"""Only enumerated counters / exception class names; never raw log lines."""
from pathlib import Path
import json,re
out=Path('e2e/designcraft-final/artifacts');out.mkdir(exist_ok=True)
s=Path('.qa-app.log').read_text(errors='replace') if Path('.qa-app.log').exists() else ''
counts={
 'muse_http_200':len(re.findall(r'newstart meta-muse-spark call status=200',s)),
 'cli_nonzero':len(re.findall(r'DesignCraft CLI 비정상 종료',s)),
 'startup_failure':s.count('APPLICATION FAILED TO START'),
}
classes=sorted(set(re.findall(r'(?:Caused by: )([a-zA-Z0-9_.$]+(?:Exception|Error))\b',s)))
v={'counters':counts,'exception_classes':classes}
(out/'server-metrics.json').write_text(json.dumps(v,indent=2))
print(json.dumps(v))
