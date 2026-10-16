"""배포 후 읽기 전용 점검(GET·파일 존재/건수만). 비밀값·사용자 데이터는 출력하지 않는다."""
import json, os, re, subprocess, time


def sh(cmd, timeout=40):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


def get(path, port=8080):
    return sh(f"curl -s -m 10 http://127.0.0.1:{port}{path}")


def code(path, port=8080):
    return sh(f"curl -s -o /dev/null -m 10 -w '%{{http_code}}' http://127.0.0.1:{port}{path}")


out = {'checked_at_utc': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime())}
jar = '/home/ec2-user/app.jar'
out['spring'] = {'active': sh('systemctl is-active f2r-app.service'), 'since': sh('systemctl show f2r-app.service -p ActiveEnterTimestamp --value'),
                 'health': sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 120"), 'n_restarts': sh('systemctl show f2r-app.service -p NRestarts --value')}
out['wiki'] = {'active': sh('systemctl is-active buildit-wiki.service'), 'since': sh('systemctl show buildit-wiki.service -p ActiveEnterTimestamp --value'),
               'health_http': code('/api/health', 8000)}
out['jar'] = {'mtime': sh(f"stat -c %y {jar}"), 'sha256': sh(f"sha256sum {jar} | cut -d' ' -f1", 90),
              'examinsight_class_count': sh(f"unzip -l {jar} 2>/dev/null | grep -c 'domain/examinsight/' || true", 90),
              'previous_jar_exists': os.path.exists('/home/ec2-user/app.jar.previous'),
              'previous_jar_sha256': sh("sha256sum /home/ec2-user/app.jar.previous | cut -d' ' -f1", 90)}
home = get('/')
out['home'] = {'http': code('/'), 'agent_hero': home.count('agent-hero'), 'digital_english_agent_text': home.count('디지털영어학원 Agent'),
               'your_ai_agent_text': home.count('Your AI Agent for English Academy Services'),
               'old_wiki_label_count': home.count('내신위키'), 'new_wiki_label_count': home.count('모의고사위키'),
               'bi_email_count': home.count('woorinaesin@naver.com'), 'old_email_count_in_home': home.count('admin@feel2real.co.kr'),
               'exam_analysis_link': home.count('href="/exam-analysis"')}
out['exam_analysis'] = {'page_anon_http': code('/exam-analysis'), 'config_anon_http(expect 401)': code('/api/exam-insight/config'),
                        'analyze_anon_http(expect 401/403)': sh("curl -s -o /dev/null -m 10 -w '%{http_code}' -X POST http://127.0.0.1:8080/api/exam-insight/analyze")}
js = get('/js/assistant_home.js')
out['builder_assets'] = {'assistant_home_js_http': code('/js/assistant_home.js'), 'has_reset_handler': js.count('ahReset'), 'has_redo': js.count('ahRedo'), 'has_save': js.count('ahSave')}
out['exam_insight_assets'] = {'js_http': code('/js/exam_insight.js'), 'css_http': code('/css/exam_insight.css')}
out['wiki_page_http'] = {'/wiki': code('/wiki'), '/problem-generator': code('/problem-generator'), '/exam': code('/exam')}
env = {}
try:
    for line in open('/etc/f2r/env', encoding='utf-8', errors='replace'):
        k, s, v = re.sub(r'^\s*export\s+', '', line.strip()).partition('=')
        if s: env[k.strip()] = v.strip().strip('"').strip("'")
except Exception:  # noqa: BLE001
    pass
out['exam_insight_env_overrides'] = sorted(k for k in env if k.startswith('APP_EXAM_INSIGHT_'))
out['designcraft'] = {'cli_processes': sh("pgrep -fc designcraft-cli || true"),
                      'page_anon_http': code('/designcraft'),
                      'cli_version_run_as_ec2_user(expected broken, unchanged)': sh("runuser -u ec2-user -- /opt/designcraft/designcraft-cli --version 2>&1 | head -c 160"),
                      'cli_sha256': sh("sha256sum /opt/designcraft/designcraft-cli 2>/dev/null | cut -d' ' -f1", 60)}
out['log_since_restart_error_counts'] = {'ERROR_lines': sh("journalctl -u f2r-app.service --since '-15 min' --no-pager -o cat 2>/dev/null | grep -c ' ERROR ' || true", 60),
                                         'oom_kernel': sh("journalctl -k --since '-30 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true", 30)}
print(json.dumps(out, ensure_ascii=False, indent=1))
