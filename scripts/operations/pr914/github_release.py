"""Pinned PR914 merge + explicit CI dispatch using the workflow's ephemeral GITHUB_TOKEN."""
import hashlib, json, os, subprocess, sys, time, urllib.error, urllib.request, zipfile
from pathlib import Path
REPO='woori73/feel2real-buildit'
APPROVED=json.loads(Path(__file__).with_name('approved.json').read_text())


def require(ok, reason):
    if not ok: raise RuntimeError(reason)


def api(path, method='GET', data=None):
    url='https://api.github.com/'+path.lstrip('/')
    request=urllib.request.Request(url, data=None if data is None else json.dumps(data).encode(), method=method,
        headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json','Content-Type':'application/json','X-GitHub-Api-Version':'2022-11-28'})
    try:
        with urllib.request.urlopen(request,timeout=30) as response:
            raw=response.read();return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raise RuntimeError('GitHub API '+method+' failed with HTTP '+str(e.code)+'; check repository policy/permission (response body suppressed)') from None


def repo(path, **kwargs): return api('repos/'+REPO+'/'+path, **kwargs)
def git(*args): return subprocess.check_output(['git',*args],stderr=subprocess.DEVNULL)


def verify_pr(pr):
    require(pr['head']['sha']==APPROVED['approved_head'], 'PR914 changed since approval')
    require(pr['base']['ref']=='main' and pr['head']['repo']['full_name']==REPO, 'unexpected PR repository/base')
    checks=repo('commits/'+pr['head']['sha']+'/check-runs?per_page=100')['check_runs']
    for name in ('build','mysql-integration-test','chromium-local'):
        found=sorted([c for c in checks if c['name']==name and c['app']['slug']=='github-actions'],key=lambda c:c['id'],reverse=True)
        require(found and found[0]['conclusion']=='success' and found[0]['status']=='completed','required latest PR CI is not successful: '+name)


def merge():
    require(os.environ.get('GITHUB_REF')=='refs/heads/main','release workflow must run from main')
    pr=repo('pulls/914');verify_pr(pr)
    if pr.get('merged'):
        sha=pr['merge_commit_sha']
        require(repo('branches/main')['commit']['sha']==sha, 'main advanced after PR914; separate review required')
    else:
        require(pr['state']=='open' and pr.get('mergeable') is True, 'PR914 not mergeable; no bypass')
        require(repo('branches/main')['commit']['sha']==os.environ['GITHUB_SHA'], 'main changed since dispatch')
        if pr['draft']:
            response=api('graphql',method='POST',data={'query':'mutation($id:ID!){markPullRequestReadyForReview(input:{pullRequestId:$id}){pullRequest{id}}}', 'variables':{'id':pr['node_id']}})
            require(not response.get('errors'),'Ready transition rejected')
        result=repo('pulls/914/merge',method='PUT',data={'sha':APPROVED['approved_head'],'merge_method':'merge','commit_title':'Merge approved PR #914 compact assistant'})
        require(result.get('merged'),'merge rejected; no bypass')
        sha=result['sha']
    require(repo('branches/main')['commit']['sha']==sha,'main changed; refuse different SHA deployment')
    # GITHUB_TOKEN-created pushes do not start push CI. Explicit workflow_dispatch is necessary.
    repo('actions/workflows/build-check.yml/dispatches',method='POST',data={'ref':'main'})
    deadline=time.monotonic()+1800
    while time.monotonic()<deadline:
        require(repo('branches/main')['commit']['sha']==sha,'main changed while CI ran')
        runs=repo('actions/workflows/build-check.yml/runs?head_sha='+sha+'&event=workflow_dispatch&per_page=30')['workflow_runs']
        if runs:
            run=max(runs,key=lambda r:r['id'])
            if run['status']=='completed':
                require(run['conclusion']=='success','merged SHA CI failed; no production change')
                with open(os.environ['GITHUB_OUTPUT'],'a') as f: f.write('release_sha='+sha+'\n')
                Path('release-evidence').mkdir(exist_ok=True)
                Path('release-evidence/merge-ci.json').write_text(json.dumps({'release_sha':sha,'ci_run_id':run['id'],'ci_url':run['html_url']},indent=2))
                return
        time.sleep(10)
    raise RuntimeError('merged SHA CI timeout; no production change')


def package():
    sha=os.environ['RELEASE_SHA']
    require(git('rev-parse','HEAD').decode().strip()==sha,'checkout differs from merged release SHA')
    require(repo('branches/main')['commit']['sha']==sha,'main advanced before deployment')
    changed=git('diff','--name-only',APPROVED['baseline_commit'],sha,'--','src/main').decode().splitlines()
    allowed={'src/main/resources/'+n.removeprefix('BOOT-INF/classes/') for n in APPROVED['files']}
    require(set(changed)==allowed,'release contains backend/media/config changes outside PR914 approval')
    # Also match the runtime resource tree exactly to the approved PR head.
    require(not git('diff',APPROVED['approved_head'],sha,'--','src/main').strip(),'runtime tree differs from approved PR914')
    with zipfile.ZipFile('resources.zip','w',zipfile.ZIP_DEFLATED) as output:
        for entry, expected in sorted(APPROVED['files'].items()):
            data=Path('src/main/resources/'+entry.removeprefix('BOOT-INF/classes/')).read_bytes()
            require(hashlib.sha256(data).hexdigest()==expected,'approved resource hash mismatch')
            info=zipfile.ZipInfo(entry,date_time=(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            output.writestr(info,data)
    checksum=hashlib.sha256(Path('resources.zip').read_bytes()).hexdigest()
    with open(os.environ['GITHUB_OUTPUT'],'a') as f:f.write('patch_sha256='+checksum+'\n')

if __name__=='__main__':
    try: {'merge':merge,'package':package}[sys.argv[1]]()
    except Exception as e:
        print(str(e) if isinstance(e,RuntimeError) else type(e).__name__);sys.exit(1)
