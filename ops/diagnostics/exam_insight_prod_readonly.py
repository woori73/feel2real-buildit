"""기출분석 배포 전 운영 설정 점검(읽기 전용). 키·토큰·비밀번호 값은 읽어도 출력하지 않는다(설정 여부 true/false만).
출력 허용 값: 아래 SAFE_VALUES의 비밀이 아닌 설정값, 서비스 상태, HTTP 상태 코드, 파일 존재/건수."""
import json, os, re, subprocess

SAFE_VALUES = {
    'APP_EXAM_INSIGHT_ENABLED', 'APP_EXAM_INSIGHT_PER_USER_DAILY_LIMIT', 'APP_EXAM_INSIGHT_GLOBAL_DAILY_LIMIT',
    'APP_EXAM_INSIGHT_MAX_OUTPUT_CHARS', 'APP_EXAM_INSIGHT_MAX_CHARS_PER_GROUP', 'APP_EXAM_INSIGHT_MAX_FILES_PER_GROUP',
    'APP_WIKI_AI_ROUTER_BASE_URL', 'OPENROUTER_BASE_URL', 'NEWSTART_OPENROUTER_MODEL',
}
PRESENCE_ONLY = {'OPENROUTER_API_KEY'}


def sh(cmd, timeout=20):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


def parse_env(path):
    out = {}
    try:
        for line in open(path, encoding='utf-8', errors='replace'):
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            line = re.sub(r'^export\s+', '', line)
            k, sep, v = line.partition('=')
            if sep:
                out[k.strip()] = v.strip().strip('"').strip("'")
    except Exception as e:  # noqa: BLE001
        out['__error__'] = type(e).__name__
    return out


def report_env(path):
    env = parse_env(path)
    res = {'file_readable': '__error__' not in env, 'safe_values': {}, 'presence': {}}
    for k in SAFE_VALUES:
        if k in env:
            res['safe_values'][k] = env[k]
    for k in PRESENCE_ONLY:
        res['presence'][k] = bool(env.get(k))
    # 이름만: 키/토큰 계열 변수 이름(값 없음)
    res['secret_like_names_count'] = sum(1 for k in env if re.search(r'KEY|TOKEN|SECRET|PASSWORD', k))
    return res


out = {}
out['f2r_app_env'] = report_env('/etc/f2r/env')
dropin = sh("systemctl show f2r-app.service -p Environment --value")
out['f2r_app_unit_env_names_of_interest'] = sorted({m for m in re.findall(r'([A-Z0-9_]+)=', dropin) if m in SAFE_VALUES | PRESENCE_ONLY})
out['f2r_app'] = {'active': sh('systemctl is-active f2r-app.service'), 'since': sh('systemctl show f2r-app.service -p ActiveEnterTimestamp --value')}

jar = '/home/ec2-user/app.jar'
out['jar'] = {'exists': os.path.exists(jar), 'mtime': sh(f"stat -c %y {jar}"), 'sha256': sh(f"sha256sum {jar} | cut -d' ' -f1", 60),
              'examinsight_class_count': sh(f"unzip -l {jar} 2>/dev/null | grep -c 'domain/examinsight/' || true", 60),
              'exam_insight_template_count': sh(f"unzip -l {jar} 2>/dev/null | grep -c 'public/exam_insight' || true", 60)}

ports = {}
for p in (8080, 8081, 8082):
    code = sh(f"curl -s -o /dev/null -m 5 -w '%{{http_code}}' http://127.0.0.1:{p}/")
    if code.startswith(('2', '3')):
        ports[str(p)] = {
            'home': code,
            'home_has_agent_hero': sh(f"curl -s -m 8 http://127.0.0.1:{p}/ | grep -c 'agent-hero' || true"),
            'exam_analysis': sh(f"curl -s -o /dev/null -m 5 -w '%{{http_code}}' http://127.0.0.1:{p}/exam-analysis"),
            'exam_insight_config_anon': sh(f"curl -s -o /dev/null -m 5 -w '%{{http_code}}' http://127.0.0.1:{p}/api/exam-insight/config"),
        }
out['app_http_get_only'] = ports

wiki = {'active': sh('systemctl is-active buildit-wiki.service'),
        'health_http': sh("curl -s -o /dev/null -m 5 -w '%{http_code}' http://127.0.0.1:8000/api/health"),
        'ai_complete_route_GET_expect_405': sh("curl -s -o /dev/null -m 5 -w '%{http_code}' http://127.0.0.1:8000/api/ai/complete")}
efiles = sh("systemctl show buildit-wiki.service -p EnvironmentFiles --value")
wiki['env_files_paths'] = [x.split(' ')[0] for x in efiles.splitlines() if x.strip()][:3]
names = []
for path in wiki['env_files_paths']:
    path = path.lstrip('-')
    for k, v in parse_env(path).items():
        if re.search(r'KEY|TOKEN', k) and k != '__error__':
            names.append({'name': k, 'set': bool(v)})
wiki['provider_key_vars(name,set only)'] = names
out['wiki_backend'] = wiki

out['nginx_timeouts'] = sh("nginx -T 2>/dev/null | grep -nE 'proxy_read_timeout|proxy_send_timeout|client_max_body_size|send_timeout' | head -12")
print(json.dumps(out, ensure_ascii=False, indent=1))
