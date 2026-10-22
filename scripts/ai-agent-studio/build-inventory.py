#!/usr/bin/env python3
"""Enrich an isolated application's real Spring inventory with source evidence; no app/DB calls.
Usage: python3 scripts/ai-agent-studio/build-inventory.py /tmp/f2r-route-inventory.json
"""
import ast
import collections
import json
import re
import subprocess
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/ai-agent-studio'
OUT.mkdir(exist_ok=True)
routes = json.loads(Path(sys.argv[1]).read_text())
java = list((ROOT / 'src/main/java').rglob('*.java'))
by_name = collections.defaultdict(list)
texts = {}
for path in java:
    texts[path] = path.read_text()
    by_name[path.stem].append(path)

def evidence(name):
    queue = list(by_name.get(name, [])); seen = set(); tables = set(); repositories = set()
    while queue:
        path = queue.pop()
        if path in seen: continue
        seen.add(path); text = texts[path]
        if path.stem.endswith('Repository'): repositories.add(str(path.relative_to(ROOT)))
        tables.update(re.findall(r'@Table\s*\(\s*name\s*=\s*"([^"]+)"', text))
        # Imported domain types and same-domain repository generic arguments, not comments/literals.
        for ref in re.findall(r'import\s+f2r\.[\w.]+\.([A-Z]\w+);', text):
            queue.extend(by_name.get(ref, []))
        for ref in re.findall(r'(?:private\s+final|extends\s+JpaRepository<)\s*([A-Z]\w+)',text):
            queue.extend(by_name.get(ref, []))
    return {'repositorySources':sorted(repositories),'transitiveTableCandidates':sorted(tables),
            'evidenceNote':'정적 클래스 의존 그래프의 후보. 개별 호출의 실제 SQL 표나 운영 DB 건수를 주장하지 않음'}
cache = {}
for row in routes:
    name = row['controller']
    if name not in cache: cache[name]=evidence(name)
    row.update(cache[name])
# Java MVC view registration + client-side API source evidence.
web = texts[next(p for p in java if p.name == 'WebMvcConfig.java')]
views = dict(re.findall(r'view\(registry, "([^"]+)", "([^"]+)"\)',web))
for row in routes:
    controller = '\n'.join(texts[p] for p in by_name.get(row['controller'], []))
    templates = [views[row['path']]] if row['path'] in views else re.findall(r'(?:return|viewName\s*=)\s*"([\w/-]+)"', controller)
    sources = []
    for template in sorted(set(templates)):
        path = ROOT / 'src/main/resources/templates' / (template+'.html')
        if not path.exists(): continue
        html = path.read_text();scripts = re.findall(r'(?:src|th:src)="(?:@\{)?(/js/[^"}]+)',html)
        urls = set(re.findall(r'["\x27`]((?:/api/|/academycraft/api/|/problem-generator/wiki/api/)[^"\x27`\s<]*)',html))
        for script in scripts:
            js = ROOT / 'src/main/resources/static' / script.lstrip('/')
            if js.exists(): urls.update(re.findall(r'["\x27`]((?:/api/|/academycraft/api/|/problem-generator/wiki/api/)[^"\x27`\s]*)',js.read_text()))
        sources.append({'template':str(path.relative_to(ROOT)),'scripts':scripts,'clientApiLiterals':sorted(urls)})
    if sources:row['screenSources']=sources
# FastAPI route census from Python AST, including dormant modules marked as such.
python_routes=[]
backend=ROOT/'problem-generator/wiki-backend-src/backend'
main=(backend/'main.py').read_text()
for path in sorted(backend.glob('*.py')):
    tree=ast.parse(path.read_text());prefix=''
    for node in ast.walk(tree):
        if isinstance(node,ast.Call) and isinstance(node.func,ast.Name) and node.func.id=='APIRouter':
            prefix=next((k.value.value for k in node.keywords if k.arg=='prefix' and isinstance(k.value,ast.Constant)), '')
    mounted=path.name=='main.py' or f'include_router({path.stem}.router)' in main
    for node in ast.walk(tree):
        if not isinstance(node,(ast.FunctionDef,ast.AsyncFunctionDef)):continue
        for dec in node.decorator_list:
            if not isinstance(dec,ast.Call) or not isinstance(dec.func,ast.Attribute) or dec.func.attr not in {'get','post','put','patch','delete','head'}:continue
            if not dec.args or not isinstance(dec.args[0],ast.Constant):continue
            url='/problem-generator/wiki'+prefix+dec.args[0].value
            python_routes.append({'path':url,'methods':[dec.func.attr.upper()],'handler':node.name,'source':str(path.relative_to(ROOT)),
                'line':node.lineno,'mountedInMain':mounted,'database':'사용자별 SQLite workspace (legacy는 기존 ADMIN/지정 TUTOR 정책 유지)',
                'permission':'Spring signed identity + workspace.py. New MCP bypasses legacy shared MCP_API_KEY endpoint.'})
base=subprocess.check_output(['git','merge-base','HEAD','origin/main'],cwd=ROOT,text=True).strip()
report={'baseMain':base,'routes':routes,'wikiBackendRoutes':python_routes,
        'limitations':['운영 DB·서비스 접속 검증 아님','정적 의존 그래프는 실제 endpoint별 SQL 실행 추적과 다름','정적 UI·JS API literal에는 조합식·동적 경로가 일부 생략될 수 있음','등록된 조회 정책 외 경로는 URL 목록으로만 제공하며 실행 거부']}
(OUT/'service-inventory.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
lines=['# 실제 서비스·페이지·API 조사', '',f'기준 main: `{base}`. 격리 앱의 Spring 런타임 매핑 {len(routes)}개. Wiki Python 선언 {len(python_routes)}개.',
'','SSR 본문과 JS가 조회하는 데이터는 다릅니다. `service-inventory.json`의 `screenSources`는 템플릿·스크립트·API literal, `repositorySources`는 기존 DB 접근 코드 근거입니다. `transitiveTableCandidates`는 정적 후보이며 각 API가 모든 후보 테이블을 읽는다는 뜻이 아닙니다.',
'','MCP 실행 가능 경로는 `readable=true`입니다. 원래 서비스 권한은 매 호출 다시 검사합니다. 민감 자료는 OAuth 서비스 동의 범위와 기존 객체 권한을 모두 충족해야 합니다.',
'','| 서비스/도메인 | 매핑 | 조회 어댑터 |','|---|---:|---:|']
for service, count in sorted(collections.Counter(r['service'] for r in routes).items()):
    lines.append(f'| {service} | {count} | {sum(bool(r["readable"]) for r in routes if r["service"]==service)} |')
lines+=['','## 전체 실제 페이지·API 목록','','`PUBLIC`/`PUBLIC_METADATA_ONLY`/`ROLE_ADMIN`/`PROJECT_GRANT`/`DATA_DEPENDENT`/`MEMBER_SERVICE_POLICY`/`SERVER_POLICY`는 접근 분류입니다. 세부 권한과 tenant 판정의 정본은 기존 서버 검사입니다. 공개 shell이 비공개 데이터 접근 권한을 뜻하지 않습니다.','','| 메서드 | 실제 URL 패턴 | 서비스 | 접근 분류 | MCP 읽기 | 코드 |','|---|---|---|---|---|---|']
for r in routes:
    url='https://feel2real.co.kr'+r['path'];path=r.get('source',r['controller'])
    lines.append(f'| {",".join(r["methods"])} | `{url.replace("|","&#124;")}` | {r["service"]} | {r.get("visibility","UPSTREAM_POLICY")} | {"허용" if r["readable"] else "미허용"} | `{r["controller"]}.{r.get("handler", "proxy")}` |')
lines+=['','## 별도 백엔드와 보존 경계','','- AcademyCraft: 기존 EC2의 `127.0.0.1:8082`로 서명한 사용자·역할·소유 학원을 전달합니다. 외부 백엔드 소스와 DB는 이 저장소에 없어 실제 연결은 별도 환경에서 검증해야 합니다. `AcademyCraftGateway.allowedPath`에서 확인된 서비스 계약에만 어댑터를 제공합니다.',
'- Wiki: 기존 `127.0.0.1:8000`, `backend/main.py`·`workspace.py`, 사용자별 SQLite. 전용 MCP 요청은 기존 workspace만 `mode=ro`로 조회합니다. 공유 관리자 API key를 재사용하지 않습니다.',
'- Spring: 기존 RDS MySQL·JPA/JDBC. 제작 스튜디오와 OAuth는 추가 테이블만 사용하며 운영 DB에 적용하지 않았습니다.',
'- 파일: 기존 FileStorage의 local/S3 구현 재사용. 실제 storage_key는 MCP 응답에서 제거합니다. 신규 버킷·서버·임베딩 서비스가 없습니다.',
'','## 읽기 도구에서 제외한 동작','','쓰기 메서드, OAuth·로그인, 토큰/수집 인증 설정, 작업 할당(next-task), 새 workspace/working-copy 초기화, AI 생성·실시간 외부 API 호출, 파일 렌더/출력 생성, SSE 이벤트 구독은 실행하지 않습니다. 기존 다운로드가 조회 이력을 쓰는 Wiki 경로는 원본 view/preview로 대체합니다. 실제 기존 서비스 코드는 보존합니다.']
(OUT/'SERVICE_INVENTORY.md').write_text('\n'.join(lines)+'\n')
print(f'Wrote {len(routes)} Spring/proxy rows and {len(python_routes)} Python declarations.')
