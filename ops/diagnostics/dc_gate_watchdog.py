"""DesignCraft gate 서버 측 데드맨 스위치 (resume 창을 서버가 스스로 닫는 안전장치).

게이트에는 만료 시간이 없다(코드 확인). 그래서 브라우저가 죽어도 닫히도록, 서버에서 독립 프로세스로 감시한다.
- 상태 파일(`state`)이 OPEN으로 바뀐 순간부터 TTL을 센다. resume이 persist(OPEN)을 쓰므로 관측 가능하다.
- CLOSED로 돌아오면(정상 drain) 즉시 종료한다. 한 번도 OPEN이 안 되면 START_WAIT 후 종료한다.
- 가드(TTL·메모리<300MiB·스왑 급증·OOM·Spring/Wiki 이상) 위반 시 f2r-app.service를 재시작한다.
  재시작이 인증 없이 가능한 유일한 차단 수단이다: Spring은 시작할 때마다 gate를 CLOSED로 시작한다.
- DB·사용자 데이터·Wiki·설정은 건드리지 않는다. 비밀·본문은 출력하지 않는다.
"""
import argparse, json, os, re, subprocess, sys, time

DEFAULTS = {
    'state_file': '/var/lib/feel2real/designcraft-maintenance/state',
    'ttl_s': 480,            # OPEN 관측 후 최대 허용 시간
    'start_wait_s': 900,     # OPEN을 기다리는 최대 시간(없으면 종료)
    'drain_grace_s': 120,    # DRAINING에서 CLOSED가 되기를 기다리는 시간
    'poll_s': 3,
    'mem_min_mib': 300,
    'swap_growth_mib': 64,
    'swapout_pages': 16384,  # 약 64MiB
    'health_fail_limit': 2,  # 연속 실패 횟수
}


def evaluate(obs, cfg, st):
    """순수 함수: 한 번의 관측으로 다음 행동을 결정한다. st는 호출 사이에 유지되는 dict(변경됨).
    반환: ('WAIT'|'DONE'|'TRIGGER', 사유)"""
    now, state = obs['now'], obs['state_file']
    if state is None:  # 읽을 수 없음
        return ('TRIGGER', 'state_unreadable') if st.get('opened_at') is not None else ('WAIT', 'state_unreadable_before_open')
    if state == 'FAULT':
        return ('DONE', 'fault_already_blocked')
    if st.get('opened_at') is None:
        if state == 'OPEN':
            st['opened_at'] = now
            st['baseline_swap'], st['baseline_swapout'], st['baseline_oom'] = obs['swap_used_mib'], obs['swapout_pages'], obs['oom_count']
        elif now - st['started_at'] > cfg['start_wait_s']:
            return ('DONE', 'never_opened')
        else:
            return ('WAIT', 'waiting_for_open')
    if state == 'CLOSED':
        return ('DONE', 'closed_normally')
    # 이 아래는 OPEN 또는 DRAINING (열린 뒤)
    if obs['mem_avail_mib'] < cfg['mem_min_mib']:
        return ('TRIGGER', 'mem_below_min')
    if obs['swap_used_mib'] - st['baseline_swap'] > cfg['swap_growth_mib']:
        return ('TRIGGER', 'swap_growth')
    if obs['swapout_pages'] - st['baseline_swapout'] > cfg['swapout_pages']:
        return ('TRIGGER', 'swap_out_burst')
    if obs['oom_count'] > st['baseline_oom']:
        return ('TRIGGER', 'oom_detected')
    for key, label in (('spring_ok', 'spring'), ('wiki_ok', 'wiki')):
        fails = st.setdefault('fails_' + key, 0)
        st['fails_' + key] = 0 if obs[key] else fails + 1
        if st['fails_' + key] >= cfg['health_fail_limit']:
            return ('TRIGGER', f'{label}_unhealthy')
    if state == 'DRAINING':
        st.setdefault('draining_since', now)
        if now - st['draining_since'] > cfg['drain_grace_s']:
            return ('TRIGGER', 'drain_stuck')
    else:
        st.pop('draining_since', None)
        if now - st['opened_at'] > cfg['ttl_s']:
            return ('TRIGGER', 'ttl_expired')
    return ('WAIT', 'monitoring')


# ── 얇은 런타임 계층(서버에서만 사용) ─────────────────────────────────────
def _sh(cmd, timeout=15):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception:  # noqa: BLE001
        return ''


def _meminfo():
    d = {}
    for line in open('/proc/meminfo'):
        k, _, v = line.partition(':')
        d[k] = int(v.split()[0]) // 1024
    return d


def _vm(key):
    for line in open('/proc/vmstat'):
        if line.startswith(key + ' '):
            return int(line.split()[1])
    return 0


def observe(cfg, since_epoch):
    try:
        state = open(cfg['state_file'], encoding='ascii').read().strip() or None
    except OSError:
        state = None
    m = _meminfo()
    oom = _sh(f"journalctl -k --since '@{int(since_epoch)}' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true", 20)
    spring = _sh("curl -s -m 5 http://127.0.0.1:8080/actuator/health | head -c 100")
    wiki = _sh("curl -s -o /dev/null -m 5 -w '%{http_code}' http://127.0.0.1:8000/api/health")
    cli = _sh("ps -eo args | grep -iE 'designcraft-cli|verapdf' | grep -v grep | grep -v dc_gate_watchdog | wc -l")
    return {'now': time.time(), 'state_file': state, 'mem_avail_mib': m['MemAvailable'], 'swap_used_mib': m['SwapTotal'] - m['SwapFree'],
            'swapout_pages': _vm('pswpout'), 'oom_count': int(oom or 0), 'spring_ok': '"status":"UP"' in spring, 'wiki_ok': wiki == '200',
            'cli_procs': int(cli or 0)}


def log(event, **kw):
    print(json.dumps({'t': time.strftime('%H:%M:%S', time.gmtime()), 'event': event, **kw}, ensure_ascii=False), flush=True)


def trigger(reason, cfg, dry_run):
    log('TRIGGER', reason=reason, action='systemctl restart f2r-app.service', dry_run=dry_run)
    if dry_run:
        return
    subprocess.run(['systemctl', 'restart', 'f2r-app.service'], timeout=120)
    deadline = time.time() + 240
    while time.time() < deadline:
        time.sleep(5)
        ok = '"status":"UP"' in _sh("curl -s -m 5 http://127.0.0.1:8080/actuator/health | head -c 100")
        try:
            state = open(cfg['state_file'], encoding='ascii').read().strip()
        except OSError:
            state = None
        if ok and state == 'CLOSED':
            log('RECOVERED', spring='UP', gate_state_file='CLOSED')
            return
    log('RECOVERY_NOT_CONFIRMED', note='수동 확인 필요')


def main(argv=None):
    ap = argparse.ArgumentParser()
    for k, v in DEFAULTS.items():
        ap.add_argument('--' + k.replace('_', '-'), type=type(v), default=v)
    ap.add_argument('--dry-run', action='store_true', help='재시작 명령을 실행하지 않고 로그만 남긴다')
    a = ap.parse_args(argv)
    cfg = {k: getattr(a, k) for k in DEFAULTS}
    st = {'started_at': time.time()}
    log('START', cfg=cfg, dry_run=a.dry_run)
    stats = {'min_mem_mib': 10 ** 9, 'max_swap_mib': 0, 'max_cli_procs': 0}
    while True:
        obs = observe(cfg, st['started_at'])
        if st.get('opened_at') is not None or obs['state_file'] == 'OPEN':
            stats['min_mem_mib'] = min(stats['min_mem_mib'], obs['mem_avail_mib'])
            stats['max_swap_mib'] = max(stats['max_swap_mib'], obs['swap_used_mib'])
            stats['max_cli_procs'] = max(stats['max_cli_procs'], obs.get('cli_procs', 0))
        action, why = evaluate(obs, cfg, st)
        if action == 'WAIT':
            if why == 'monitoring' and int(obs['now']) % 15 < cfg['poll_s']:
                log('HEARTBEAT', state=obs['state_file'], mem=obs['mem_avail_mib'], swap=obs['swap_used_mib'], opened_for_s=int(obs['now'] - st['opened_at']))
            time.sleep(cfg['poll_s'])
            continue
        if action == 'DONE':
            log('DONE', reason=why, state=obs['state_file'], opened_for_s=(int(obs['now'] - st['opened_at']) if st.get('opened_at') else None), **stats)
            return 0
        log('STATS_AT_TRIGGER', **stats)
        trigger(why, cfg, a.dry_run)
        return 2


if __name__ == '__main__':
    sys.exit(main())
