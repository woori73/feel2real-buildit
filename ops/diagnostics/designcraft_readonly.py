"""Fixed read-only production probe. No raw env, command lines, journal or files leave EC2."""
import json,os,stat,subprocess,re,hashlib
from pathlib import Path

def run(args):
    p=subprocess.run(args,capture_output=True,text=True,timeout=20)
    return p.returncode,p.stdout

def probe():
    _,pidtext=run(['systemctl','show','f2r-app.service','-p','MainPID','--value'])
    pid=int(pidtext.strip() or '0')
    _,user=run(['systemctl','show','f2r-app.service','-p','User','--value'])
    user=user.strip() or 'root'
    keys={'PUBLISHING_CLI_PATH','PUBLISHING_TEMPLATE_DIR','PUBLISHING_STORAGE_ROOT'}
    env={}
    if pid>0:
        for field in Path(f'/proc/{pid}/environ').read_bytes().split(b'\0'):
            key,sep,val=field.partition(b'=')
            if key.decode(errors='replace') in keys:
                env[key.decode()]=val.decode(errors='replace')
    cli=env.get('PUBLISHING_CLI_PATH','/opt/designcraft/designcraft-cli')
    templates=env.get('PUBLISHING_TEMPLATE_DIR','/opt/designcraft/templates')
    storage=env.get('PUBLISHING_STORAGE_ROOT','/tmp/publishing')
    # Command line publishing overrides only; do not print or retain other arguments.
    if pid>0:
        for a in Path(f'/proc/{pid}/cmdline').read_bytes().split(b'\0'):
            a=a.decode(errors='replace')
            for prefix,name in [('--publishing.cli-path=','cli'),('-Dpublishing.cli-path=','cli'),('--publishing.template-dir=','templates'),('--publishing.storage-root=','storage')]:
                if a.startswith(prefix):
                    if name=='cli':cli=a[len(prefix):]
                    elif name=='templates':templates=a[len(prefix):]
                    else:storage=a[len(prefix):]
    p=Path(cli)
    out={'service_running':pid>0,'service_user':user,'configuration_source':'running process env/explicit publishing args; Spring defaults if absent','cli_path':cli,'cli_exists':p.exists(),'cli_regular':p.is_file()}
    if p.exists():
        st=p.stat();out['cli_mode']=oct(stat.S_IMODE(st.st_mode));out['cli_uid']=st.st_uid;out['cli_gid']=st.st_gid
    rc,_=run(['runuser','-u',user,'--','test','-x',cli]);out['service_can_execute_cli']=rc==0
    if p.is_file() and rc==0:
        result=subprocess.run(['runuser','-u',user,'--',cli,'--version'],capture_output=True,text=True,timeout=20)
        rc,v=result.returncode,result.stdout
        out['version_error_glibc_symbols']=sorted(set(re.findall(r'GLIBC_[0-9.]+',result.stderr)))
        h=hashlib.sha256()
        with p.open('rb') as f:
            for chunk in iter(lambda:f.read(65536),b''):h.update(chunk)
        out['cli_sha256']=h.hexdigest()
        m=re.search(r'designcraft-cli\s+[0-9]+\.[0-9]+\.[0-9]+',v)
        out['cli_version']=m.group(0) if m else 'unavailable';out['cli_version_exit']=rc
    _,glibc=run(['getconf','GNU_LIBC_VERSION']);out['glibc']=glibc.strip()
    out['templates']={}
    for code in ['ko-book-46','ko-textbook-a4','ko-workbook-b5']:
        root=Path(templates)/code
        out['templates'][code]={'native_file':(root/'template.designcraft').is_file(),'noto_regular':(root/'Document Fonts/NotoSansKR-Regular.ttf').is_file(),'noto_bold':(root/'Document Fonts/NotoSansKR-Bold.ttf').is_file()}
    root=Path(storage)
    out['storage_exists']=root.is_dir()
    out['service_storage_writable']=run(['runuser','-u',user,'--','test','-w',str(root)])[0]==0 if root.exists() else None
    out['service_tmp_writable']=run(['runuser','-u',user,'--','test','-w','/tmp'])[0]==0
    _,logs=run(['journalctl','-u','f2r-app.service','--since','2026-10-14 00:00:00 UTC','--no-pager','-n','12000'])
    categories={
        'designcraft_no_such_file':r'(?i)(?:designcraft[^\n]{0,250}(?:no such file|error=2)|(?:no such file|error=2)[^\n]{0,250}designcraft)',
        'designcraft_permission_denied':r'(?i)designcraft[^\n]{0,250}(?:permission denied|error=13)',
        'designcraft_timeout':r'(?i)designcraft[^\n]{0,250}(?:timeout|시간 초과)',
        'designcraft_glibc_error':r'(?i)(?:designcraft[^\n]{0,250}GLIBC_|GLIBC_[^\n]{0,250}designcraft)',
        'designcraft_nonzero_exit':r'DesignCraft CLI 비정상 종료',
        'publishing_provision_failed':r'템플릿 프로비저닝 실패',
    }
    out['journal_matches']={k:len(re.findall(pat,logs)) for k,pat in categories.items()}
    out['journal_scope']='last 12000 service lines since 2026-10-14 UTC; raw lines withheld'
    print(json.dumps(out,ensure_ascii=False))
try:probe()
except Exception as e:
    print(json.dumps({'probe_error_class':type(e).__name__,'raw_message_withheld':True}));raise SystemExit(1)
