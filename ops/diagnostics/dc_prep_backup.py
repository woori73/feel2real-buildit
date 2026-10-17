"""운영 배포 전 백업(승인 범위: app.jar·app.jar.previous 별도 사본, Wiki app.db 온라인 백업).
새 디렉터리에만 O_EXCL로 쓰고 원본은 읽기만 한다. 기존 백업은 덮어쓰거나 삭제하지 않는다."""
import hashlib, json, os, re, shutil, sqlite3, stat, subprocess, time

out = {}


def sh(cmd, timeout=60):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout.strip()


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def copy_new(src, dst):
    """dst가 이미 있으면 실패(덮어쓰기 금지)."""
    with open(src, 'rb') as fin, os.fdopen(os.open(dst, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as fout:
        shutil.copyfileobj(fin, fout, 1 << 20)
    shutil.copystat(src, dst)


ts = time.strftime('%Y%m%d-%H%M%S', time.gmtime())
root = '/var/backups/dc-deploy-pre-bbbd9222'
os.makedirs(root, mode=0o700, exist_ok=True)
bdir = f'{root}/{ts}'
os.mkdir(bdir, 0o700)  # 이미 있으면 예외
out['backup_dir'] = bdir
out['existing_backup_dirs_kept'] = sorted(os.listdir('/var/backups/designcraft')) if os.path.isdir('/var/backups/designcraft') else []
out['pre_state'] = {p: {'sha256': sha(p), 'mtime': int(os.stat(p).st_mtime)} for p in ('/home/ec2-user/app.jar', '/home/ec2-user/app.jar.previous')}
st = os.statvfs('/var/backups')
need = sum(os.path.getsize(p) for p in out['pre_state']) + (os.path.getsize('/opt/buildit-wiki/data/app.db') if os.path.exists('/opt/buildit-wiki/data/app.db') else 0)
out['disk'] = {'free_MiB': st.f_bavail * st.f_frsize // 2**20, 'need_MiB': need // 2**20 + 1}
assert st.f_bavail * st.f_frsize > need * 2, 'not enough disk'

jars = {}
for name in ('app.jar', 'app.jar.previous'):
    src = f'/home/ec2-user/{name}'
    dst = f'{bdir}/{name}'
    copy_new(src, dst)
    jars[name] = {'src_sha256': out['pre_state'][src]['sha256'][:16], 'copy_sha256': sha(dst)[:16], 'bytes': os.path.getsize(dst), 'match': sha(dst) == out['pre_state'][src]['sha256']}
out['jar_backups'] = jars

# Wiki app.db
db = '/opt/buildit-wiki/data/app.db'
w = {'exists': os.path.exists(db), 'ls': sh(f"ls -la --time-style=long-iso /opt/buildit-wiki/data | tail -n +2 | awk '{{print $5,$6,$7,$8}}'").splitlines(),
     'file_type': sh(f"head -c 16 {db} | strings | head -1") if os.path.exists(db) else None,
     'wiki_writers': sh(f"lsof {db} 2>/dev/null | awk 'NR>1{{print $1,$2}}' | sort -u | head -3")}
hdr = open(db, 'rb').read(100) if w['exists'] else b''
w['header_wal_flag'] = (hdr[18], hdr[19]) if len(hdr) >= 20 else None  # (2,2)=WAL
wal_ok = not (w['header_wal_flag'] == (2, 2) and not os.path.exists(db + '-shm'))  # WAL인데 -shm이 없으면 열기만 해도 파일을 만들 수 있어 중단
if w['exists'] and w['file_type'] and w['file_type'].startswith('SQLite format 3') and wal_ok:
    src = sqlite3.connect(f'file:{db}?mode=ro', uri=True, timeout=30)
    w['journal_mode'] = src.execute('PRAGMA journal_mode').fetchone()[0]
    w['page_count_x_size_bytes'] = src.execute('PRAGMA page_count').fetchone()[0] * src.execute('PRAGMA page_size').fetchone()[0]
    dst_path = f'{bdir}/app.db'
    dst = sqlite3.connect(dst_path)
    src.backup(dst)           # SQLite 온라인 백업 API: 서비스 중단 없이 일관된 스냅샷
    dst.commit()
    w['backup_method'] = 'sqlite3 Connection.backup (online, consistent snapshot, source opened read-only)'
    w['copy_quick_check'] = dst.execute('PRAGMA quick_check').fetchone()[0]
    tabs = [r[0] for r in dst.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
    w['table_row_counts_copy_vs_source'] = {t: [dst.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0], src.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]] for t in tabs}
    dst.close(); src.close()
    os.chmod(dst_path, 0o600)
    w['copy_bytes'] = os.path.getsize(dst_path)
else:
    w['note'] = 'SQLite가 아니거나 파일이 없거나, WAL인데 -shm이 없어 안전을 위해 백업하지 않음'
out['wiki_db'] = w
out['post_state_unchanged'] = {p: (sha(p) == v['sha256'] and int(os.stat(p).st_mtime) == v['mtime']) for p, v in out['pre_state'].items()}
out['backup_listing'] = sh(f"ls -la {bdir} | tail -n +2 | awk '{{print $1,$5,$9}}'").splitlines()
db_host = ''
for line in open('/etc/f2r/env', errors='replace'):
    if line.startswith('DB_HOST='):
        db_host = line.strip().split('=', 1)[1].strip('"\'')
out['rds_endpoint_label'] = db_host.split('.')[0] if 'rds.amazonaws.com' in db_host else None
out['mem_after'] = sh("free -m | sed -n 2,3p")
print('RDS_ID=' + (out['rds_endpoint_label'] or ''))
print(json.dumps(out, ensure_ascii=False, indent=1))
