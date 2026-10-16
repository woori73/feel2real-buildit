"""job 1 versions 소실 원인 단서(읽기 전용): 서비스 기동 이력 + 해당 시간대 ERROR/rollback 로그(마스킹)."""
import json, re, subprocess


def sh(cmd, timeout=90):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout.strip().splitlines()


def mask(s):
    s = re.sub(r'[\w.+-]+@[\w-]+\.[\w.-]+', '<email>', s)
    s = re.sub(r'\b\d{1,3}(\.\d{1,3}){3}\b', '<ip>', s)
    s = re.sub(r'(?i)(password|secret|token|key|authorization|cookie)(["\'=:\s]+)\S+', r'\1\2<masked>', s)
    s = re.sub(r'[A-Za-z0-9+/_=-]{32,}', '<longtoken>', s)
    return s[:260]


J = "journalctl -u f2r-app.service --since '2026-10-14' --no-pager -o short-iso 2>/dev/null"
out = {
    'starts_stops': [mask(x) for x in sh(J + " | grep -E 'systemd\\[1\\]: (Started|Stopped|Stopping)' | tail -20")],
    'error_lines_by_day': sh(J + " | grep ' ERROR ' | cut -c1-10 | sort | uniq -c"),
    'rollback_hits': [mask(x) for x in sh(J + " | grep -iE 'rollback|UnexpectedRollback' | head -10")],
    'publishing_error_or_warn': [mask(x) for x in sh(J + " | grep -E ' (ERROR|WARN) ' | grep -iE 'publishing|designcraft|PublishingJob|PublishingEdit|템플릿|원고' | head -25")],
}
print(json.dumps(out, ensure_ascii=False, indent=1))
