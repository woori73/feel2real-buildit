#!/usr/bin/env python3
"""Verify pinned Git originals, then register via existing authenticated Studio APIs.
Default is a read-only dry run. Production writes require explicit scoped approval.
No DB credentials, MCP, paid AI calls or arbitrary remote URLs are accepted.
"""
import argparse, contextlib, hashlib, json, os, pathlib, stat, subprocess, urllib.error, urllib.request, urllib.parse, uuid
PIN='09d62dee982e2e89e3653c90b1ee42f05bff4f9d'
REPO=pathlib.Path(__file__).resolve().parents[2]
PRODUCTION_ORIGIN='https://feel2real.co.kr'
PROJECT='ai-agent-part1'

def require(condition,message):
    if not condition:raise ValueError(message)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        raise ValueError('Redirect refused; credentials were not forwarded')

def target(args):
    origin=args.base_url or (PRODUCTION_ORIGIN if args.production else 'http://127.0.0.1:18090')
    parsed=urllib.parse.urlsplit(origin)
    require(not parsed.username and not parsed.password and not parsed.query and not parsed.fragment,'URL credentials/query/fragment refused')
    if args.production:
        require(origin==PRODUCTION_ORIGIN,'Fixed production HTTPS origin required')
        if args.apply:
            require(args.approval_file is not None,'Explicit user approval file required')
            a=json.loads(args.approval_file.read_text())
            require(a.get('approved') is True and a.get('action')=='studio-source-import'
                    and a.get('account')=='425805163960' and a.get('origin')==origin
                    and a.get('projectId')==PROJECT and a.get('sourceCommit')==PIN
                    and bool(a.get('approvedBy')) and bool(a.get('reference')),'Approval scope mismatch')
    else:
        require(parsed.scheme=='http' and parsed.hostname in ('127.0.0.1','localhost') and parsed.port==18090 and parsed.path in ('','/'),'Only isolated loopback port 18090 is allowed')
    return origin.rstrip('/')

def pages(call,endpoint):
    result=[]
    for offset in range(0,1000001,200):
        rows=json.loads(call(endpoint+'?limit=200&offset='+str(offset)))
        require(isinstance(rows,list),'Expected paginated list')
        result.extend(rows)
        if len(rows)<200:return result
    raise ValueError('Pagination limit reached; incomplete verification refused')

def approved(rows):
    return {r['item_key']:(r['id'],r.get('file_id'),r.get('sha256')) for r in rows if r.get('approved_id')==r['id']}

@contextlib.contextmanager
def report_failure(receipt,save):
    try:yield
    except Exception as error:
        receipt.update(failedStage=receipt.get('stage','preflight'),stage='failed',errorType=type(error).__name__,
                       mode='failed-production' if 'production' in receipt['mode'] else 'failed-local')
        save()
        raise

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',type=pathlib.Path)
    parser.add_argument('--extract-to',type=pathlib.Path)
    parser.add_argument('--base-url')
    parser.add_argument('--auth-file',type=pathlib.Path)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--production',action='store_true')
    parser.add_argument('--approval-file',type=pathlib.Path)
    parser.add_argument('--initialize-project',action='store_true')
    parser.add_argument('--receipt',type=pathlib.Path,required=True)
    args=parser.parse_args()
    origin=target(args) # Reject unapproved production apply before all network calls/writes.
    catalog=json.loads((REPO/'src/main/resources/studio/production-09d62dee.json').read_text())
    if args.extract_to:
        args.extract_to.mkdir(parents=True,exist_ok=True)
        for row in catalog['files']:
            dest=args.extract_to/row['path'];dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(subprocess.check_output(['git','show',PIN+':docs/ai-agent-production/'+row['path']],cwd=REPO))
        args.source=args.extract_to
    if not args.source:parser.error('--source or --extract-to is required')
    source=args.source.resolve();actual={p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()}
    require(actual=={r['path'] for r in catalog['files']},'536개 원본 경로 불일치')
    unique={}
    for row in catalog['files']:
        file=source/row['path'];require(not file.is_symlink() and file.resolve().is_relative_to(source),'symlink not allowed')
        b=file.read_bytes();require(len(b)==row['size'] and hashlib.sha256(b).hexdigest()==row['sha256'],row['path'])
        unique.setdefault(row['sha256'],row)
    receipt={'commit':PIN,'mode':'dry-run-production' if args.production else 'dry-run','sourcePaths':536,'uniqueFiles':len(unique),'duplicatePaths':536-len(unique),
        'bytes':sum(r['size'] for r in catalog['files']),'verifiedSha':536,'approvedOriginals':catalog['approvedSha256'],
        'originalComparedCuts':0,'confirmedPassCuts':0,'representativeImagesCreated':0,'externalAiCalls':0}
    def save():
        args.receipt.parent.mkdir(parents=True,exist_ok=True)
        args.receipt.write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n')
    save()
    with report_failure(receipt,save):
        if args.apply:
            require(args.auth_file is not None,'--auth-file required; never pass a token on the command line')
            if args.production:
                require(stat.S_IMODE(args.auth_file.stat().st_mode)&0o077==0 and args.auth_file.stat().st_uid==os.getuid(),'Auth file requires owner-only access')
            cookie=json.loads(args.auth_file.read_text())['adminCookie']
            require(bool(cookie) and '\r' not in cookie and '\n' not in cookie,'Invalid session cookie')
            handlers=[NoRedirect()]
            if not args.production:handlers.append(urllib.request.ProxyHandler({}))
            opener=urllib.request.build_opener(*handlers) # Production keeps proxy/verified TLS.
            def call(path,data=None,ctype='application/json'):
                require(not path.startswith('/') and '..' not in path and '://' not in path,'Invalid API path')
                headers={'Cookie':cookie,'Origin':origin}
                if data is not None:headers['Content-Type']=ctype
                path+=('&' if '?' in path else '?')+'projectId='+PROJECT
                request=urllib.request.Request(origin+'/api/studio/'+path,data=data,headers=headers,method='POST' if data is not None else 'GET')
                try:
                    with opener.open(request,timeout=180) as response:return response.read()
                except urllib.error.HTTPError as e:raise ValueError('Studio API HTTP '+str(e.code)) from None
            overview=json.loads(call('project'))
            require(overview.get('projectId')==PROJECT and overview.get('canManage') is True and not overview.get('archived'),'Active ADMIN project required')
            if not overview.get('initialized'):
                require(args.initialize_project,'Project absent; explicit --initialize-project required')
                call('project',b'{}')
            before=pages(call,'revisions');canon_before=approved(before)
            character_keys={'character-approved-'+sha[:12] for sha in catalog['approvedSha256']}
            require(all(r.get('approved_id') for r in before if r['item_key'] in character_keys),'Existing character approval was withdrawn; automatic reapproval refused')
            existing={r['sha256']:r for r in pages(call,'files')}
            receipt.update(mode='applying-production' if args.production else 'applying-local',stage='uploads')
            save()
            mapping={}
            for i,(sha,row) in enumerate(unique.items(),1):
                boundary='f2r-'+uuid.uuid4().hex;name=pathlib.PurePosixPath(row['path']).name.replace('"','_').replace('\r','_').replace('\n','_')
                body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode()+(source/row['path']).read_bytes()+f'\r\n--{boundary}--\r\n'.encode())
                stored=existing.get(sha)
                if stored is None:stored=json.loads(call('files',body,'multipart/form-data; boundary='+boundary))['file']
                require(stored['sha256']==sha,'Stored metadata SHA mismatch');mapping[sha]=stored['id']
                # Re-read persisted original bytes; a successful upload response alone is insufficient.
                actual_bytes=call('files/'+stored['id']+'/content');require(len(actual_bytes)==row['size'] and hashlib.sha256(actual_bytes).hexdigest()==sha,row['path'])
                receipt.update(storedOriginalsVerified=len(mapping),fileMapping=dict(mapping));save()
                if i%50==0:print(f'verified stored originals {i}/{len(unique)}',flush=True)
            receipt['stage']='registration';save()
            result=json.loads(call('source-import',json.dumps({'commit':PIN,'files':mapping}).encode()))
            receipt.update(registration=result,stage='verification');save()
            status=json.loads(call('source-import'));canon_after=approved(pages(call,'revisions'))
            require(status.get('commit')==PIN and status.get('registeredSourcePaths')==536,'Persisted source registration incomplete')
            require(all(canon_after.get(k)==v for k,v in canon_before.items()),'Existing canonical approval changed; manual investigation required')
            require({v[2] for k,v in canon_after.items() if k in character_keys}==set(catalog['approvedSha256']),'Five canonical SHA values not observed')
            receipt.update(mode='applied-production' if args.production else 'applied-local',stage='complete',existingApprovalsPreserved=True,registrationVerified=status,
                sources=[{**row,'fileId':mapping[row['sha256']]} for row in catalog['files']])
    save()
    print(json.dumps({k:v for k,v in receipt.items() if k not in ('sources','fileMapping')},ensure_ascii=False))

if __name__=='__main__':
    try:main()
    except Exception as e:
        # Don't print HTTP response bodies, cookies, credentials or headers.
        print('Import failed ('+type(e).__name__+'). Receipt retains last verified stage; no automatic deletion/rollback.')
        raise SystemExit(2)
