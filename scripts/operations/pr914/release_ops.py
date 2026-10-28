#!/usr/bin/env python3
"""Prepared only: fixed PR914 audit/backup/verify/restore operations. No DB access.
Run on the approved production host through the fixed SSM documents, as root.
Never prints environment contents or credentials. No media restore/delete operation.
"""
import hashlib, json, os, shutil, stat, subprocess, sys, tempfile, time
from pathlib import Path
JAR = Path('/home/ec2-user/app.jar')
BACKUP = Path('/var/backups/f2r-pr914-df425588')
EXPECTED = '18ca78cb7743fbd9e94e055fa5c6a4cd6fb5ca1011885b63d866f96eaa1dcdc0'
ENV_FILES = [Path('/etc/f2r/env'), Path('/etc/buildit-wiki-identity.env')]

def require(ok, message):
    if not ok: raise RuntimeError(message)

def command(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.DEVNULL).strip()

def digest(p):
    require(not p.is_symlink(), 'symlink rejected')
    with os.fdopen(os.open(p, os.O_RDONLY | os.O_NOFOLLOW), 'rb') as f:
        before = os.fstat(f.fileno())
        require(stat.S_ISREG(before.st_mode), 'regular file required')
        h = hashlib.sha256()
        for chunk in iter(lambda: f.read(1024 * 1024), b''): h.update(chunk)
        after = os.fstat(f.fileno())
        require((before.st_ino,before.st_size,before.st_mtime_ns)==(after.st_ino,after.st_size,after.st_mtime_ns), 'source changed during hash')
    return {'sha256':h.hexdigest(), 'bytes':after.st_size}

def runtime_media_root():
    pid = command('systemctl','show','f2r-app.service','-p','MainPID','--value')
    require(pid.isdigit() and int(pid)>1, 'running application PID unavailable')
    # Read only the named path setting; never serialize the rest of the environment.
    env = Path('/proc/'+pid+'/environ').read_bytes().split(b'\0')
    values = [v.split(b'=',1)[1].decode() for v in env if v.startswith(b'APP_AGENT_MEDIA_DIR=')]
    for arg in Path('/proc/'+pid+'/cmdline').read_bytes().split(b'\0'):
        for prefix in (b'-Dapp.agent-media.dir=',b'--app.agent-media.dir='):
            if arg.startswith(prefix): values.append(arg[len(prefix):].decode())
    values = set(v for v in values if v)
    require(len(values)==1, 'media setting absent/conflicting: verify effective Spring configuration; no guessed fallback')
    p = Path(values.pop())
    require(p.is_absolute() and p.is_dir(), 'absolute existing media directory required')
    require(pid==command('systemctl','show','f2r-app.service','-p','MainPID','--value'), 'application restarted during inspection')
    p=p.resolve()
    require(p!=BACKUP and p not in BACKUP.parents and BACKUP not in p.parents, 'media/backup directories must be separate')
    return p

def media_manifest(root):
    files={}
    for agent in ('seven','suni'):
        directory=root/agent
        require(directory.is_dir() and not directory.is_symlink(), 'both character directories must exist without symlinks')
        for p in sorted(directory.rglob('*')):
            require(not p.is_symlink(), 'media symlink requires separate review')
            if p.is_file(): files[p.relative_to(root).as_posix()]=digest(p)
        names=[n for n in files if n.startswith(agent+'/')]
        require(any(n.endswith(('.jpg','.jpeg','.png','.webp')) for n in names), 'character image missing')
        require(any(n.endswith('.mp4') for n in names), 'character MP4 missing')
    return {'root':str(root),'files':files}

def private_directory():
    require(BACKUP.is_dir() and not BACKUP.is_symlink(), 'fixed backup directory absent')
    s=BACKUP.stat()
    require(s.st_uid==0 and stat.S_IMODE(s.st_mode)==0o700, 'backup must be root-owned mode 0700')

def check_backup():
    private_directory()
    p=BACKUP/'manifest.json'
    require(p.is_file() and not p.is_symlink(), 'complete backup manifest absent')
    data=json.loads(p.read_text())
    require(data['baseline_sha256']==EXPECTED, 'wrong backup baseline')
    require(digest(BACKUP/'app.jar')['sha256']==EXPECTED, 'backup JAR hash mismatch')
    for name,record in data['media']['files'].items():
        rel=Path(name)
        require(not rel.is_absolute() and '..' not in rel.parts and rel.parts[0] in ('seven','suni'), 'invalid backup media path')
        require(digest(BACKUP/'media'/rel)==record, 'backup media hash mismatch')
    for item in data['env']:
        require(item['path'] in [str(p) for p in ENV_FILES], 'invalid env path')
        if item['exists']: require(digest(BACKUP/'env'/Path(item['path']).name)==item['digest'], 'backup environment hash mismatch')
    return data

def create_backup():
    require(digest(JAR)['sha256']==EXPECTED, 'live JAR differs from verified df425588')
    require(not BACKUP.exists() and not BACKUP.is_symlink(), 'backup path already exists: use verify; never overwrite')
    root=runtime_media_root(); media=media_manifest(root)
    require(command('systemctl','is-active','f2r-app.service')=='active','baseline service must be active')
    command('curl','-fsS','--max-time','10','-o','/dev/null','http://127.0.0.1:8080/actuator/health')
    BACKUP.mkdir(mode=0o700, parents=False)
    data={'baseline_commit':'df425588c97d7f1fb668e23452e638bc9cb14fd4','baseline_sha256':EXPECTED,'media':media,'env':[]}
    js=JAR.stat();data['jar_metadata']={'uid':js.st_uid,'gid':js.st_gid,'mode':stat.S_IMODE(js.st_mode)}
    shutil.copyfile(JAR,BACKUP/'app.jar');os.chmod(BACKUP/'app.jar',0o600)
    require(digest(BACKUP/'app.jar')['sha256']==EXPECTED,'copied JAR hash mismatch')
    for name,record in media['files'].items():
        target=BACKUP/'media'/name;target.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        shutil.copyfile(root/name,target);os.chmod(target,0o600)
        require(digest(target)==record,'copied media hash mismatch')
    (BACKUP/'env').mkdir(mode=0o700)
    for p in ENV_FILES:
        item={'path':str(p),'exists':p.exists()}
        if item['exists']:
            d=digest(p);s=p.stat();target=BACKUP/'env'/p.name
            shutil.copyfile(p,target);os.chmod(target,0o600)
            require(digest(target)==d and digest(p)==d,'environment changed during backup')
            item.update(digest=d,uid=s.st_uid,gid=s.st_gid,mode=stat.S_IMODE(s.st_mode))
        data['env'].append(item)
    require(digest(JAR)['sha256']==EXPECTED,'live JAR changed during backup')
    require(media_manifest(root)==media,'media changed during backup; incomplete backup retained for review')
    # The complete manifest is written last. A failed/partial backup cannot be restored.
    with open(BACKUP/'manifest.json','x') as f:
        os.chmod(BACKUP/'manifest.json',0o600);json.dump(data,f,ensure_ascii=False,indent=2);f.flush();os.fsync(f.fileno())
    for p in BACKUP.rglob('*'):
        if p.is_file():
            with open(p,'rb') as f: os.fsync(f.fileno())
    check_backup()
    print(json.dumps({'result':'BACKUP_VERIFIED','path':str(BACKUP),'jar_sha256':EXPECTED,'media_files':len(media['files']),'manifest_sha256':digest(BACKUP/'manifest.json')['sha256']}))

def verify_restore():
    data=check_backup()
    require(os.geteuid()==0,'root execution required')
    for p in [JAR]+ENV_FILES:
        require(not p.is_symlink(),'restore target symlink rejected')
        require(p.parent.resolve()==p.parent,'restore parent symlink rejected')
        require(os.access(p.parent,os.W_OK),'restore target parent not writable')
    require(command('systemctl','show','f2r-app.service','-p','LoadState','--value')=='loaded','application unit unavailable')
    return data

def atomic_restore(source,target,metadata):
    fd,name=tempfile.mkstemp(prefix='.f2r914-restore-',dir=target.parent)
    try:
        with os.fdopen(fd,'wb') as f, open(source,'rb') as src:
            shutil.copyfileobj(src,f);f.flush();os.fchown(f.fileno(),metadata['uid'],metadata['gid']);os.fchmod(f.fileno(),metadata['mode']);os.fsync(f.fileno())
        require(digest(Path(name))==digest(source),'restore staging hash mismatch')
        os.replace(name,target)
    finally:
        if os.path.exists(name):os.unlink(name)

def restore():
    data=verify_restore()
    # No media writes, deletes, database commands, or paid API calls in this operation.
    changed_env=[]
    for item in data['env']:
        p=Path(item['path'])
        same=item['exists'] and p.exists() and digest(p)==item['digest']
        if not same and (item['exists'] or p.exists()): changed_env.append(item)
    for item in changed_env:
        p=Path(item['path'])
        if item['exists']: atomic_restore(BACKUP/'env'/p.name,p,item)
        else: p.unlink() # only the two fixed environment paths, restoring recorded absence
    atomic_restore(BACKUP/'app.jar',JAR,data['jar_metadata'])
    require(digest(JAR)['sha256']==EXPECTED,'restored JAR hash mismatch')
    command('systemctl','restart','f2r-app.service')
    if any(item['path']=='/etc/buildit-wiki-identity.env' for item in changed_env):
        command('systemctl','restart','buildit-wiki.service')
    healthy=False
    for _ in range(36):
        try:
            command('curl','-fsS','--max-time','5','-o','/dev/null','http://127.0.0.1:8080/actuator/health');healthy=True;break
        except subprocess.CalledProcessError:time.sleep(5)
    require(healthy,'restore health failed: manual recovery required; no success claimed')
    require(media_manifest(Path(data['media']['root']))==data['media'],'media drift detected: JAR restored but originals require investigation; media not overwritten')
    print(json.dumps({'result':'JAR_RESTORED_HEALTH_OK','jar_sha256':EXPECTED,'media_unchanged':True,'ui_and_external_health':'must verify separately'}))

if __name__=='__main__':
    os.umask(0o077)
    action=sys.argv[1] if len(sys.argv)==2 else os.environ.get('F2R_FIXED_OPERATION','audit')
    try:
        require(os.geteuid()==0,'approved root/SSM execution required')
        if action=='audit':
            root=runtime_media_root();report=media_manifest(root)
            report['jar']=digest(JAR);report['previous_jar']=digest(JAR.with_name('app.jar.previous')) if JAR.with_name('app.jar.previous').exists() else None
            report['fixed_backup_exists']=BACKUP.exists()
            report['fixed_backup_verified']=False
            if BACKUP.exists():check_backup();report['fixed_backup_verified']=True
            print(json.dumps(report,ensure_ascii=False,indent=2))
        elif action=='backup':create_backup()
        elif action=='verify':
            d=verify_restore();require(media_manifest(Path(d['media']['root']))==d['media'],'media differs from backup')
            print(json.dumps({'result':'BACKUP_AND_ROOT_RESTORE_PREFLIGHT_OK','restart_executed':False,'media_unchanged':True}))
        elif action=='restore':restore()
        else:raise RuntimeError('operation not allowed')
    except Exception as e:
        # No traceback, command output, environment content, or credential values in logs.
        print(json.dumps({'result':'FAIL','reason':str(e) if isinstance(e,RuntimeError) else type(e).__name__}))
        sys.exit(1)
