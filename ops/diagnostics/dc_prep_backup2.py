"""추가 백업(승인 범위): /etc/f2r/env, /etc/buildit-wiki-identity.env, Wiki 워크스페이스 DB, app.db.workspaces.
새 디렉터리(0700)에만 O_EXCL로 쓰고, 값은 출력하지 않는다(해시·크기·무결성만). 실패하면 exit 2 → 배포 중단."""
import glob, hashlib, json, os, shutil, sqlite3, subprocess, sys, time

R = {'ok': True, 'problems': []}


def fail(msg):
    R['ok'] = False
    R['problems'].append(msg)


def sh(cmd, timeout=60):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout.strip()


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for c in iter(lambda: f.read(1 << 20), b''):
            h.update(c)
    return h.hexdigest()


def copy_new(src, dst):
    with open(src, 'rb') as fi, os.fdopen(os.open(dst, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as fo:
        shutil.copyfileobj(fi, fo, 1 << 20)
    os.chmod(dst, 0o600)


def is_sqlite(p):
    try:
        with open(p, 'rb') as f:
            return f.read(16) == b'SQLite format 3\x00'
    except OSError:
        return False


def sqlite_backup(src_path, dst_path):
    with open(src_path, 'rb') as f:
        hdr = f.read(100)
    wal = (hdr[18], hdr[19]) == (2, 2)
    if wal and not os.path.exists(src_path + '-shm'):
        return {'skipped': 'WAL without -shm; refusing to open (could create files)'}
    src = sqlite3.connect(f'file:{src_path}?mode=ro', uri=True, timeout=30)
    dst = sqlite3.connect(dst_path)
    src.backup(dst)
    dst.commit()
    qc = dst.execute('PRAGMA quick_check').fetchone()[0]
    tabs = [r[0] for r in dst.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
    rows = {t: (dst.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0], src.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]) for t in tabs}
    dst.close(); src.close()
    os.chmod(dst_path, 0o600)
    return {'quick_check': qc, 'tables': len(tabs), 'row_mismatch_tables': [t for t, (a, b) in rows.items() if a != b], 'mode': 'wal' if wal else 'delete/other'}


root = '/var/backups/dc-deploy-pre-bbbd9222'
assert os.path.isdir(root) and (os.stat(root).st_mode & 0o777) == 0o700, 'backup root missing or not 0700'
ts = time.strftime('%Y%m%d-%H%M%S', time.gmtime())
bdir = f'{root}/{ts}-env-wiki'
os.mkdir(bdir, 0o700)
R['backup_dir'] = bdir
R['existing_backup_subdirs'] = sorted(os.listdir(root))
manifest = {}

# 1) env files (values never printed)
envdir = f'{bdir}/etc'
os.mkdir(envdir, 0o700)
R['env_files'] = {}
for p in ('/etc/f2r/env', '/etc/buildit-wiki-identity.env'):
    if not os.path.exists(p):
        R['env_files'][p] = 'MISSING'; fail(f'{p} missing'); continue
    st = os.stat(p)
    dst = f'{envdir}/{os.path.basename(p)}'
    copy_new(p, dst)
    s1, s2 = sha(p), sha(dst)
    lines = sum(1 for _ in open(p, errors='replace'))
    R['env_files'][p] = {'bytes': st.st_size, 'lines': lines, 'owner_mode': f'{st.st_uid}:{st.st_gid} {oct(st.st_mode & 0o777)}', 'sha256_16': s1[:16], 'copy_match': s1 == s2, 'copy_mode': oct(os.stat(dst).st_mode & 0o777)}
    manifest[dst] = s2
    if s1 != s2:
        fail(f'{p} copy hash mismatch')

# 2) app.db.workspaces: identify the real thing first
w = '/opt/buildit-wiki/data/app.db.workspaces'
info = {'exists': os.path.exists(w), 'is_dir': os.path.isdir(w), 'is_file': os.path.isfile(w)}
if info['is_file']:
    info['sqlite'] = is_sqlite(w)
    info['head_text'] = sh(f"head -c 80 {w} | strings | head -2")[:120] if not info['sqlite'] else 'SQLite'
    info['bytes'] = os.path.getsize(w)
R['app.db.workspaces'] = info
data_out = f'{bdir}/wiki-data'
os.mkdir(data_out, 0o700)
if info['is_file']:
    dst = f'{data_out}/app.db.workspaces'
    if info.get('sqlite'):
        info['backup'] = sqlite_backup(w, dst)
        if info['backup'].get('skipped') or info['backup'].get('quick_check') != 'ok':
            fail('app.db.workspaces sqlite backup problem')
    else:
        copy_new(w, dst)
        info['copy_match'] = sha(w) == sha(dst)
        if not info['copy_match']:
            fail('app.db.workspaces copy mismatch')
    if os.path.exists(dst):
        manifest[dst] = sha(dst)
elif info['is_dir']:
    fail('app.db.workspaces is a directory: needs a decision (not copied)')

# 3) workspace DBs
ws = sorted(glob.glob('/opt/buildit-wiki/data/app.workspace-*.db'))
res = {}
for p in ws:
    dst = f'{data_out}/{os.path.basename(p)}'
    if not is_sqlite(p):
        copy_new(p, dst)
        res[os.path.basename(p)] = {'non_sqlite_copy_match': sha(p) == sha(dst)}
        if sha(p) != sha(dst):
            fail(f'{p} copy mismatch')
    else:
        r = sqlite_backup(p, dst)
        res[os.path.basename(p)] = r
        if r.get('skipped') or r.get('quick_check') != 'ok':
            fail(f'{p} backup problem: {r}')
    if os.path.exists(dst):
        manifest[dst] = sha(dst)
R['workspace_dbs'] = {'count_source': len(ws), 'count_backed_up': sum(1 for v in res.values() if v.get('quick_check') == 'ok' or v.get('non_sqlite_copy_match')),
                      'all_quick_check_ok': all(v.get('quick_check') in (None, 'ok') for v in res.values()),
                      'row_mismatch_dbs(writes during backup)': {k: v['row_mismatch_tables'] for k, v in res.items() if v.get('row_mismatch_tables')},
                      'sqlite_modes': sorted({v.get('mode') for v in res.values() if v.get('mode')})}
if R['workspace_dbs']['count_source'] != R['workspace_dbs']['count_backed_up']:
    fail('workspace db count mismatch')
R['workspace_dbs_total_bytes_copy'] = sum(os.path.getsize(f'{data_out}/{k}') for k in res if os.path.exists(f'{data_out}/{k}'))

# 4) manifest + permission audit + re-verify every copy
json.dump(manifest, open(f'{bdir}/MANIFEST.sha256.json', 'w'), indent=1)
os.chmod(f'{bdir}/MANIFEST.sha256.json', 0o600)
bad = [p for p, h in manifest.items() if sha(p) != h]
if bad:
    fail(f'post-write verification failed: {bad}')
perm = {}
for dirpath, dirs, files in os.walk(bdir):
    for n in dirs + files:
        m = os.stat(os.path.join(dirpath, n)).st_mode & 0o777
        perm[oct(m)] = perm.get(oct(m), 0) + 1
R['permission_histogram_under_new_backup_dir'] = perm
R['root_dir_perm'] = oct(os.stat(root).st_mode & 0o777)
R['files_in_manifest'] = len(manifest)
R['disk_free_MiB_after'] = os.statvfs('/var/backups').f_bavail * os.statvfs('/var/backups').f_frsize // 2**20
R['mem_after'] = sh('free -m | sed -n 2,3p')
print(json.dumps(R, ensure_ascii=False, indent=1))
sys.exit(0 if R['ok'] else 2)
