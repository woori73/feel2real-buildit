#!/usr/bin/env bash
# 신규 호스트(f2r-app)에서만 실행하는 입시상담 읽기 전용 진단. 운영 데이터 변경 없음.
# - DB: SELECT만, READ ONLY 트랜잭션. 쓰기 SQL·DDL 없음. 비밀값·개인정보 출력 없음.
# - 출력: 상태별 건수, 대학별 일정 현황, 공식자료 출처·승인 상태(공개 자료명·URL), 검색 인덱스 연결, 앱 상태.
set -uo pipefail
printf '=== app status ===\n'
systemctl is-active f2r-app || true
systemctl show f2r-app -p ActiveState -p SubState -p ActiveEnterTimestamp 2>/dev/null || true
printf 'health: '
curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health 2>/dev/null | head -c 200 || printf 'unreachable'
printf '\n=== admissions flags (names allow-listed; secrets never printed) ===\n'
python3 - <<'PY'
import shlex
ALLOW_VALUE={'APP_ADMISSIONS_PUBLIC_API_ENABLED','APP_ADMISSIONS_EMBEDDING_ENABLED'}
ALLOW_PRESENCE={'APP_ADMISSIONS_EMBEDDING_SERVICE_URL','APP_ADMISSIONS_LLM_ENABLED','APP_ADMISSIONS_LLM_PROVIDER'}
seen={}
try:
    for line in open('/etc/f2r/env'):
        line=line.strip()
        if not line or line.startswith('#') or '=' not in line: continue
        k,v=line.split('=',1)
        k=k.replace('export ','').strip()
        if k in ALLOW_VALUE|ALLOW_PRESENCE:
            parts=shlex.split(v); seen[k]=parts[0] if parts else ''
except OSError:
    print('env file unreadable')
for k in sorted(ALLOW_VALUE): print(k+'='+(seen.get(k,'<unset: application default>')))
for k in sorted(ALLOW_PRESENCE): print(k+'='+('<set>' if seen.get(k) else '<unset>'))
PY
printf '=== admissions DB census (read only) ===\n'
python3 - <<'PY'
import os,shlex,subprocess,tempfile
values={}
for line in open('/etc/f2r/env'):
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1)
    if k in ('DB_HOST','DB_USER','DB_USERNAME','DB_PASSWORD','DB_NAME','DB_PORT'):
        parts=shlex.split(v); values[k]=parts[0] if parts else ''
host=values.get('DB_HOST'); user=values.get('DB_USER') or values.get('DB_USERNAME'); password=values.get('DB_PASSWORD')
if not all((host,user,password)): raise SystemExit('DB credential fields unavailable; no values printed')
client=next((p for p in ('/usr/bin/mysql','/usr/bin/mariadb') if os.path.isfile(p)),None)
if not client: raise SystemExit('MySQL client unavailable')
def quote(v): return '"'+v.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n')+'"'
SQL=r"""
SET SESSION TRANSACTION READ ONLY;
START TRANSACTION READ ONLY;
SELECT 'tables present' AS section, GROUP_CONCAT(table_name ORDER BY table_name) AS value FROM information_schema.tables
 WHERE table_schema=DATABASE() AND table_name LIKE 'admissions\\_%';
SELECT 'flyway V1078' AS section, COUNT(*) AS value FROM flyway_schema_history WHERE version='1078' AND success=1;
SELECT 'materials by year/status' AS section, academic_year, status, COUNT(*) AS n FROM admissions_official_materials GROUP BY academic_year, status ORDER BY academic_year, status;
SELECT 'departments by year/status' AS section, academic_year, status, COUNT(*) AS n FROM admissions_departments GROUP BY academic_year, status ORDER BY academic_year, status;
SELECT 'types by year/status' AS section, academic_year, status, COUNT(*) AS n FROM admissions_types GROUP BY academic_year, status ORDER BY academic_year, status;
SELECT 'types with 제출서류 in details (count only)' AS section, academic_year, SUM(details LIKE '%제출서류:%') AS with_docs, SUM(details IS NULL OR details NOT LIKE '%제출서류:%') AS without_docs FROM admissions_types GROUP BY academic_year;
SELECT 'schedules by year/status' AS section, academic_year, status, COUNT(*) AS n FROM admissions_schedules GROUP BY academic_year, status ORDER BY academic_year, status;
SELECT 'knowledge by year/status' AS section, academic_year, status, COUNT(*) AS n FROM admissions_knowledge_items GROUP BY academic_year, status ORDER BY academic_year, status;
SELECT 'schedules by university/year/track/status (track from title)' AS section, u.name AS university, s.academic_year, CASE WHEN s.title LIKE '%정시%' THEN '정시' WHEN s.title LIKE '%수시%' THEN '수시' ELSE '구분없음' END AS track, s.status, COUNT(*) AS n
 FROM admissions_schedules s LEFT JOIN admissions_universities u ON u.university_id=s.university_id GROUP BY u.name, s.academic_year, track, s.status ORDER BY u.name, s.academic_year, track, s.status;
SELECT 'schedules answerable now (ACTIVE row + ACTIVE source of same year)' AS section, s.academic_year, COUNT(*) AS n, COUNT(DISTINCT s.university_id) AS universities
 FROM admissions_schedules s JOIN admissions_official_materials m ON m.material_id=s.source_id
 WHERE s.status='ACTIVE' AND m.status='ACTIVE' AND m.academic_year=s.academic_year GROUP BY s.academic_year ORDER BY s.academic_year;
SELECT 'schedules ACTIVE but source not usable' AS section, COUNT(*) AS n FROM admissions_schedules s LEFT JOIN admissions_official_materials m ON m.material_id=s.source_id
 WHERE s.status='ACTIVE' AND (m.material_id IS NULL OR m.status<>'ACTIVE' OR m.academic_year<>s.academic_year);
SELECT 'universities' AS section, COUNT(*) AS n FROM admissions_universities;
SELECT 'official materials (public titles/URLs)' AS section, m.material_id, u.name AS university, m.academic_year, m.status, m.title, m.issuer, m.document_url
 FROM admissions_official_materials m LEFT JOIN admissions_universities u ON u.university_id=m.university_id ORDER BY m.academic_year, u.name, m.material_id LIMIT 300;
SELECT 'embeddings by model' AS section, model_version, COUNT(*) AS n FROM admissions_knowledge_embeddings GROUP BY model_version;
SELECT 'embedding rows by model/dim/bytes' AS section, model_version, dimension, LENGTH(vector) AS bytes, COUNT(*) AS n FROM admissions_knowledge_embeddings GROUP BY model_version, dimension, LENGTH(vector);
SELECT 'knowledge by kind/year/status' AS section, SUBSTRING_INDEX(dedup_key,':',1) AS kind, academic_year, status, COUNT(*) AS n FROM admissions_knowledge_items GROUP BY SUBSTRING_INDEX(dedup_key,':',1), academic_year, status;
SELECT 'ACTIVE knowledge without embedding (any model)' AS section, COUNT(*) AS n FROM admissions_knowledge_items k
 WHERE k.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM admissions_knowledge_embeddings e WHERE e.knowledge_id=k.knowledge_id);
SELECT 'open conflicts' AS section, COUNT(*) AS n FROM admissions_conflicts WHERE status='OPEN';
SELECT 'counseling sessions (count only)' AS section, COUNT(*) AS n FROM admissions_counseling_sessions;
ROLLBACK;
"""
f=tempfile.NamedTemporaryFile(mode='w',prefix='admissions-diag-',delete=False)
try:
    os.chmod(f.name,0o600)
    cfg='[client]\nhost='+quote(host)+'\nuser='+quote(user)+'\npassword='+quote(password)+'\nconnect-timeout=10\ndefault-character-set=utf8mb4\n'
    if values.get('DB_NAME'): cfg+='database='+quote(values['DB_NAME'])+'\n'
    if values.get('DB_PORT'): cfg+='port='+values['DB_PORT']+'\n'
    f.write(cfg); f.close()
    # 문장별로 실행해 한 섹션(예: 테이블 부재) 실패가 나머지를 막지 않게 한다.
    stmts=[s.strip() for s in SQL.split(';\n') if s.strip()]
    for s in stmts:
        subprocess.run([client,'--defaults-extra-file='+f.name,'--batch','--table','-e','SET SESSION TRANSACTION READ ONLY; '+s+';'],check=False)
finally:
    f.close(); os.unlink(f.name)
PY
printf '=== embedding server (read only) ===\n'
printf 'f2r-embed: '; systemctl is-active f2r-embed 2>/dev/null || true
printf 'health: '; curl -fsS --max-time 3 http://127.0.0.1:8081/health 2>/dev/null || printf 'unreachable'
printf '\n'
python3 - <<'PY'
import json,math,time,urllib.request
try:
    t=time.time()
    req=urllib.request.Request('http://127.0.0.1:8081/embed',json.dumps({'texts':['서울대학교 수시 전형'],'task':'query'}).encode(),{'Content-Type':'application/json'})
    v=json.load(urllib.request.urlopen(req,timeout=10))['embeddings'][0]
    print('embed probe: dim=%d norm=%.4f elapsed=%.2fs' % (len(v), math.sqrt(sum(x*x for x in v)), time.time()-t))
except Exception as e:
    print('embed probe failed:', type(e).__name__)
PY
printf '=== admissions index/reindex log (app journal since last start; message text only) ===\n'
SINCE=$(systemctl show f2r-app -p ActiveEnterTimestamp --value 2>/dev/null)
journalctl -u f2r-app --since "${SINCE:-1 hour ago}" --no-pager 2>/dev/null \
  | grep -E '입시 벡터 인덱스 로드|admissions knowledge (build|reindex)|AdmissionsEmbedding|admissions embedding|index/reload' \
  | sed -E 's/^.*java\[[0-9]+\]: //' | cut -c1-260 | tail -40
printf '(end of log section)\n'
