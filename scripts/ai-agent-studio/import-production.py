#!/usr/bin/env python3
"""Verify pinned Git originals, then register via existing authenticated Studio APIs.
Default is a read-only dry run. Apply is restricted to a loopback isolated instance.
No production credentials, paid AI calls or arbitrary remote URLs are accepted.
"""
import argparse, hashlib, json, pathlib, subprocess, urllib.request, urllib.parse, uuid
PIN='09d62dee982e2e89e3653c90b1ee42f05bff4f9d'
REPO=pathlib.Path(__file__).resolve().parents[2]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',type=pathlib.Path)
    parser.add_argument('--extract-to',type=pathlib.Path)
    parser.add_argument('--base-url',default='http://127.0.0.1:18090')
    parser.add_argument('--auth-file',type=pathlib.Path)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--receipt',type=pathlib.Path,required=True)
    args=parser.parse_args()
    catalog=json.loads((REPO/'src/main/resources/studio/production-09d62dee.json').read_text())
    if args.extract_to:
        args.extract_to.mkdir(parents=True,exist_ok=True)
        for row in catalog['files']:
            dest=args.extract_to/row['path'];dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(subprocess.check_output(['git','show',PIN+':docs/ai-agent-production/'+row['path']],cwd=REPO))
        args.source=args.extract_to
    if not args.source:parser.error('--source or --extract-to is required')
    source=args.source.resolve();actual={p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()}
    assert actual=={r['path'] for r in catalog['files']},'536개 원본 경로 불일치'
    unique={}
    for row in catalog['files']:
        file=source/row['path'];assert not file.is_symlink(),'symlink not allowed'
        b=file.read_bytes();assert len(b)==row['size'] and hashlib.sha256(b).hexdigest()==row['sha256'],row['path']
        unique.setdefault(row['sha256'],row)
    receipt={'commit':PIN,'mode':'dry-run','sourcePaths':536,'uniqueFiles':len(unique),'duplicatePaths':536-len(unique),
        'bytes':sum(r['size'] for r in catalog['files']),'verifiedSha':536,'approvedOriginals':catalog['approvedSha256'],
        'originalComparedCuts':0,'confirmedPassCuts':0,'representativeImagesCreated':0,'externalAiCalls':0}
    if args.apply:
        parsed=urllib.parse.urlsplit(args.base_url)
        assert parsed.scheme=='http' and parsed.hostname in ('127.0.0.1','localhost') and parsed.port==18090 and not parsed.username and parsed.path in ('','/'),'Only isolated loopback port 18090 is allowed'
        assert args.auth_file,'--auth-file required; never pass a token on the command line'
        cookie=json.loads(args.auth_file.read_text())['adminCookie']
        origin=args.base_url.rstrip('/')
        def call(path,data=None,ctype='application/json'):
            headers={'Cookie':cookie,'Origin':origin}
            if data is not None:headers['Content-Type']=ctype
            request=urllib.request.Request(origin+'/api/studio/'+path,data=data,headers=headers,method='POST' if data is not None else 'GET')
            # Do not use a system proxy for the explicitly isolated loopback endpoint.
            with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request,timeout=180) as response:return response.read()
        mapping={}
        for i,(sha,row) in enumerate(unique.items(),1):
            boundary='f2r-'+uuid.uuid4().hex;name=pathlib.PurePosixPath(row['path']).name.replace('"','_')
            body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode()+(source/row['path']).read_bytes()+f'\r\n--{boundary}--\r\n'.encode())
            stored=json.loads(call('files',body,'multipart/form-data; boundary='+boundary))['file']
            assert stored['sha256']==sha;mapping[sha]=stored['id']
            # Re-read persisted original bytes; a successful upload response alone is insufficient.
            actual_bytes=call('files/'+stored['id']+'/content');assert hashlib.sha256(actual_bytes).hexdigest()==sha,row['path']
            if i%50==0:print(f'verified stored originals {i}/{len(unique)}',flush=True)
        result=json.loads(call('source-import',json.dumps({'commit':PIN,'files':mapping}).encode()))
        receipt.update(mode='applied-local',storedOriginalsVerified=len(mapping),registration=result,
            sources=[{**row,'fileId':mapping[row['sha256']]} for row in catalog['files']])
    args.receipt.parent.mkdir(parents=True,exist_ok=True);args.receipt.write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in receipt.items() if k!='sources'},ensure_ascii=False))

if __name__=='__main__':main()
