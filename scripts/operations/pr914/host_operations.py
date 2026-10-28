"""Appended to the reviewed backup library by generate.py; fixed SSM commands only.
No DB operations. No media writes. No arbitrary shell, service or path parameter.
"""
import fcntl
import re
import zipfile

RECEIPT = BACKUP / 'deployment.json'
BUCKET = 'f2r-deploy-425805163960'
PREFIX = 'pr914-approved/'
STAGING_ROOT = '/var/tmp'


def media_unchanged(data):
    require(media_manifest(Path(data['media']['root'])) == data['media'], 'production media drift; originals not overwritten')


def environment_unchanged(data):
    for item in data['env']:
        p = Path(item['path'])
        require(p.exists() == item['exists'], 'environment presence changed')
        if item['exists']:
            require(digest(p) == item['digest'], 'environment content changed')


def ready_to_restart():
    require(os.geteuid() == 0, 'root execution required')
    require(command('systemctl', 'show', 'f2r-app.service', '-p', 'LoadState', '--value') == 'loaded', 'service unavailable')
    for key in ('RefuseManualStart', 'RefuseManualStop'):
        require(command('systemctl', 'show', 'f2r-app.service', '-p', key, '--value') == 'no', 'manual service operations refused')


def backup_or_verify():
    require(digest(JAR)['sha256'] == EXPECTED, 'live JAR is not the approved df425588 baseline')
    if not BACKUP.exists():
        create_backup()
    data = verify_restore()
    media_unchanged(data)
    environment_unchanged(data)
    ready_to_restart()
    print(json.dumps({'result': 'BASELINE_BACKUP_VERIFIED', 'jar_sha256': EXPECTED,
                      'media_files': len(data['media']['files']), 'restart_executed': False}))


def verify_permission():
    data = verify_restore()
    media_unchanged(data)
    environment_unchanged(data)
    ready_to_restart()
    print(json.dumps({'result': 'RESTORE_PREFLIGHT_OK', 'restart_executed': False,
                      'note': 'SSM restore document executed as root; full recovery drill not performed'}))


def write_receipt(data):
    fd, name = tempfile.mkstemp(prefix='.receipt-', dir=BACKUP)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(data, stream); stream.flush(); os.fsync(stream.fileno())
        os.replace(name, RECEIPT)
    finally:
        if os.path.exists(name): os.unlink(name)


def health():
    for _ in range(36):
        try:
            command('curl', '-fsS', '--max-time', '5', '-o', '/dev/null', 'http://127.0.0.1:8080/actuator/health')
            require(command('systemctl', 'is-active', 'f2r-app.service') == 'active', 'service inactive')
            return
        except (subprocess.CalledProcessError, RuntimeError):
            time.sleep(5)
    raise RuntimeError('service health failed')


def deploy():
    sha = os.environ.get('SSM_ReleaseSha', '')
    patch_sha = os.environ.get('SSM_PatchSha256', '')
    run_id = os.environ.get('SSM_ReleaseRunId', '')
    require(re.fullmatch('[1-9][0-9]{0,19}', run_id), 'invalid release run identity')
    require(re.fullmatch('[0-9a-f]{40}', sha) and re.fullmatch('[0-9a-f]{64}', patch_sha), 'invalid release identity; current SSM agent with ENV_VAR support required')
    require(digest(JAR)['sha256'] == EXPECTED, 'live JAR is not the approved baseline')
    verify_permission()
    data = check_backup()
    with tempfile.TemporaryDirectory(prefix='f2r914-', dir=STAGING_ROOT) as folder:
        patch, candidate = Path(folder)/'resources.zip', Path(folder)/'app.jar'
        command('aws', 's3', 'cp', '--only-show-errors', '--region', 'ap-northeast-2',
                's3://' + BUCKET + '/' + PREFIX + sha + '/' + patch_sha + '.zip', str(patch))
        require(digest(patch)['sha256'] == patch_sha, 'downloaded patch checksum mismatch')
        build_overlay(BACKUP/'app.jar', patch, candidate, APPROVED_FILES)
        candidate_sha = digest(candidate)['sha256']
        media_unchanged(data); environment_unchanged(data)
        require(digest(JAR)['sha256'] == EXPECTED, 'live JAR changed during staging')
        # Receipt before swap permits recovery even when restart/health fails.
        receipt = {'run_id': run_id, 'release_sha': sha, 'candidate_sha256': candidate_sha, 'baseline_sha256': EXPECTED, 'state': 'prepared'}
        write_receipt(receipt)
        atomic_restore(candidate, JAR, data['jar_metadata'])
        command('systemctl', 'restart', 'f2r-app.service')
        health(); media_unchanged(data); environment_unchanged(data)
        require(digest(JAR)['sha256'] == candidate_sha, 'deployed JAR hash mismatch')
        receipt['state'] = 'healthy'; write_receipt(receipt)
        print(json.dumps({'result': 'DEPLOY_HEALTHY', **receipt, 'media_unchanged': True}))


def rollback():
    data = verify_restore()
    ready_to_restart()
    current = digest(JAR)['sha256']
    if current != EXPECTED:
        require(RECEIPT.is_file() and not RECEIPT.is_symlink(), 'deployment receipt unavailable; refuse unknown rollback')
        receipt = json.loads(RECEIPT.read_text())
        require(receipt.get('run_id') == os.environ.get('SSM_ReleaseRunId'), 'stale recovery request; refuse newer deployment rollback')
        require(current == receipt['candidate_sha256'], 'current JAR belongs to another release; refuse overwrite')
        # Reuse verified atomic restore, but this UI-only path never edits environment files.
        atomic_restore(BACKUP/'app.jar', JAR, data['jar_metadata'])
    command('systemctl', 'restart', 'f2r-app.service')
    health()
    require(digest(JAR)['sha256'] == EXPECTED, 'rollback JAR mismatch')
    media_unchanged(data); environment_unchanged(data)
    print(json.dumps({'result': 'ROLLBACK_VERIFIED', 'jar_sha256': EXPECTED, 'media_unchanged': True}))


def fixed_main(operation):
    os.umask(0o077)
    require(os.geteuid() == 0, 'approved SSM root execution required')
    # Serialize these commands on the host as well as in Actions.
    with open('/var/lock/f2r-pr914-release.lock', 'a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        {'backup': backup_or_verify, 'verify': verify_permission, 'deploy': deploy, 'restore': rollback}[operation]()
