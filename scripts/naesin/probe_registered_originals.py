"""Authenticated GET-only original reread; login is the only POST. No contents in logs."""
import hashlib
import http.cookiejar
import json
import os
import re
import sys
import urllib.request
import urllib.error

BASE = 'https://feel2real.co.kr'
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs): return None

def main():
    login, password = os.environ.get('LOGIN_ID'), os.environ.get('LOGIN_PASSWORD')
    if not login or not password: raise ValueError('Existing operator credentials are unavailable')
    opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    body = json.dumps({'loginId': login, 'password': password}).encode()
    with opener.open(urllib.request.Request(BASE+'/api/auth/login', data=body, headers={'Origin':BASE,'Content-Type':'application/json'}), timeout=30) as response:
        print('login_http='+str(response.status))
    def get(path):
        with opener.open(BASE+'/api/naesin-production/'+path, timeout=120) as response: return json.load(response)
    summary={}
    for division in ('MIDDLE','HIGH'):
        result={'projects':0,'files':0,'verified':0,'failed':0,'failure_types':{}}
        for project in get('projects?division='+division):
            pid=project['id']
            if not re.fullmatch(r'naesin-[a-f0-9-]{36}',pid): raise ValueError('Unexpected project namespace')
            result['projects']+=1;offset=0;seen=set()
            while True:
                state=get('state?division='+division+'&projectId='+pid+'&offset='+str(offset))
                for file in state['files']:
                    fid=file['id']
                    if fid in seen: continue
                    if not re.fullmatch(r'[a-f0-9-]{36}',fid): raise ValueError('Invalid file identifier')
                    seen.add(fid);result['files']+=1
                    sha=hashlib.sha256();size=0
                    try:
                        path='files/'+fid+'/download?division='+division+'&projectId='+pid
                        with opener.open(BASE+'/api/naesin-production/'+path, timeout=120) as response:
                            status=response.status
                            for chunk in iter(lambda:response.read(1024*1024),b''):
                                size+=len(chunk);sha.update(chunk)
                        if status!=200 or size!=file['size_bytes'] or sha.hexdigest()!=file['sha256']: raise ValueError('Byte verification mismatch')
                        result['verified']+=1
                    except Exception as error:
                        kind=type(error).__name__+(':'+str(error.code) if isinstance(error,urllib.error.HTTPError) else '')
                        result['failed']+=1;result['failure_types'][kind]=result['failure_types'].get(kind,0)+1
                offset=state['nextOffset']
                if offset<0: break
        summary[division]=result
    print(json.dumps({'original_reread':summary,'data_writes':0,'publications':0},ensure_ascii=False))
    return 1 if any(x['failed'] for x in summary.values()) else 0
if __name__=='__main__':
    try: sys.exit(main())
    except Exception as error:
        print('probe_error_type='+type(error).__name__);sys.exit(1)
