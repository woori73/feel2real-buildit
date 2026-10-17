"""DesignCraft Job 1 작업 폴더 보존 복사(승인됨). 원본은 읽기만 한다(수정·삭제·DB 변경·프로세스 종료 없음).
대상은 영속 디스크의 root 전용(700) 신규 경로이며, 이미 있으면 아무것도 덮어쓰지 않고 중단한다.
출력에는 개수·크기·체크섬만 싣고 파일 내용·문서 본문은 싣지 않는다."""
import hashlib, json, os, shutil, stat, subprocess, sys, time

SRC = '/tmp/publishing/jobs/1'
ROOT = '/var/backups'
DST = ROOT + '/dc-job1-preserve-20261017'
out = {}


def sh(cmd):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=60)
    return (p.stdout or '').strip()


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for c in iter(lambda: f.read(1 << 20), b''):
            h.update(c)
    return h.hexdigest()


def listing(base):
    r = {}
    for root, _, files in os.walk(base):
        for f in files:
            p = os.path.join(root, f)
            r[os.path.relpath(p, base)] = (os.path.getsize(p), sha(p))
    return r


# 1) 사전 확인: 영속 디스크 여부·여유 공간·권한
out['target_fs'] = sh(f"df -PT {ROOT} | tail -1 | awk '{{print $2, $4/1024 \" MB-free\", $6}}'")
out['target_fs_is_tmpfs'] = 'tmpfs' in out['target_fs']
out['root_dir_mode'] = sh(f"stat -c '%a %U:%G' {ROOT}")
out['src_exists'] = os.path.isdir(SRC)
src_before = listing(SRC) if out['src_exists'] else {}
need_kb = int(sh(f'du -sk {SRC} | cut -f1') or 0)
free_kb = int(sh(f"df -Pk {ROOT} | tail -1 | awk '{{print $4}}'") or 0)
out['need_kb'] = need_kb; out['free_kb'] = free_kb
out['src_files_before'] = len(src_before); out['src_bytes_before'] = sum(v[0] for v in src_before.values())
if out['target_fs_is_tmpfs'] or not src_before or free_kb < need_kb * 20 or os.path.exists(DST):
    out['aborted'] = 'precondition failed (tmpfs / source missing / low space / destination exists)'
    print(json.dumps(out, indent=1, ensure_ascii=False)); sys.exit(0)

# 2) 복사(원본 읽기 전용). 대상은 700, 파일은 root 전용.
os.umask(0o077)
os.makedirs(DST, mode=0o700)
shutil.copytree(SRC, DST + '/1', copy_function=shutil.copy2)
for root, dirs, files in os.walk(DST):
    os.chmod(root, 0o700)
    for f in files:
        os.chmod(os.path.join(root, f), 0o600)
os.chown(DST, 0, 0)

# 3) 검증: 개수·크기·체크섬 비교 + 원본 불변 확인
dst = listing(DST + '/1')
src_after = listing(SRC)
out['copied_files'] = len(dst); out['copied_bytes'] = sum(v[0] for v in dst.values())
out['names_match'] = sorted(dst) == sorted(src_before)
out['sizes_and_sha256_match'] = dst == src_before
out['source_unchanged_after_copy'] = src_after == src_before
out['per_file'] = {k: {'bytes': v[0], 'sha16': v[1][:16]} for k, v in sorted(dst.items())}
manifest = {k: {'bytes': v[0], 'sha256': v[1]} for k, v in sorted(dst.items())}
with open(DST + '/MANIFEST.json', 'w') as f:
    json.dump({'source': SRC, 'created_utc': time.strftime('%FT%TZ', time.gmtime()), 'files': manifest}, f, indent=1)
os.chmod(DST + '/MANIFEST.json', 0o600)
out['backup_path'] = DST + '/1'
out['dir_mode_owner'] = sh(f"stat -c '%a %U:%G' {DST} {DST}/1 {DST}/MANIFEST.json")
out['db_touched'] = False
print(json.dumps(out, indent=1, ensure_ascii=False))
