#!/usr/bin/env bash
# 학원 홈 E2E 시드(로컬 전용). 새 테스트 DB에서 두 앱이 떠 있는 상태를 전제로 한다. 운영 DB·S3에 쓰지 않는다.
# 사용: MYSQL="mysql -uroot -p<로컬비밀번호> -h127.0.0.1 -P3308" BASE_URL=http://localhost:8080 bash e2e/academy-home/seed.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="${BASE_URL:-http://localhost:8080}"
PUBLIC_ORIGIN="${STUDIO_ORIGIN:-https://feel2real.co.kr}"   # app.studio.public-base-url (자료실·학원이사 쓰기 Origin)
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
$MYSQL --default-character-set=utf8mb4 < "$ROOT/e2e/unified-assistant/fixtures.sql"
$MYSQL --default-character-set=utf8mb4 < "$ROOT/e2e/academy-home/fixtures.sql"
python3 "$ROOT/e2e/academy-home/make_test_docs.py" "$WORK/docs" >/dev/null
JAR="$WORK/cookies"
curl -fsS -c "$JAR" -H 'Content-Type: application/json' -H "Origin: $BASE" -X POST "$BASE/api/auth/login" -d '{"loginId":"admin","password":"admin1234"}' >/dev/null
up() { curl -fsS -b "$JAR" -H "Origin: $PUBLIC_ORIGIN" -F "file=@$2" "$BASE/api/admin/$1/files" | python3 -c 'import sys,json;print(json.load(sys.stdin)["file"]["id"])'; }
pdf=$(up student-roster "$WORK/docs/roster-2026-test.pdf"); word=$(up student-roster "$WORK/docs/roster-2026-test.docx")
curl -fsS -b "$JAR" -H "Origin: $PUBLIC_ORIGIN" -H 'Content-Type: application/json' -X POST "$BASE/api/admin/student-roster/entries" \
  -d "{\"year\":\"2026\",\"pdfFileId\":\"$pdf\",\"wordFileId\":\"$word\"}" >/dev/null
move=$(up academy-move "$WORK/docs/이사준비-체크리스트-아주아주긴파일이름이화면에서줄바꿈되는지확인하는테스트용문서_최종_수정본_v3.pdf")
curl -fsS -b "$JAR" -H "Origin: $PUBLIC_ORIGIN" -H 'Content-Type: application/json' -X POST "$BASE/api/admin/academy-move/entries" \
  -d "{\"title\":\"중요 · 이전 계약서 확인 (테스트)\",\"content\":\"원장이 적은 테스트 설명입니다. 파일 원문이 아닙니다.\",\"category\":\"문서·계약\",\"archived\":false,\"fileId\":\"$move\"}" >/dev/null
# 기록에 붙이지 않은 업로드(저장 단계가 끝나지 않은 첨부 재현) + 글자 층 없는 PDF
up academy-move "$WORK/docs/blank-scan.pdf" >/dev/null
echo "seeded"
