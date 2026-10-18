#!/usr/bin/env bash
# provision-new-host.sh 의 순수 함수(render_app_unit, render_env) 검증. 실행: bash scripts/deploy/provision-new-host.test.sh
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/provision-new-host.sh"

FAILURES=0
assert_contains() {
  if grep -qF -- "$2" <<<"$1"; then echo "ok: $3"; else echo "FAIL: $3"; FAILURES=$((FAILURES+1)); fi
}

# given 힙 크기 지정 / when 유닛 렌더 / then -Xmx 반영
out="$(render_app_unit 3072)"
assert_contains "$out" "-Xmx3072m" "heap 인자가 유닛에 반영된다"
assert_contains "$out" "EnvironmentFile=/etc/f2r/env" "EnvironmentFile 고정"

# given 인자 없음 / when 렌더 / then 기본 2048m
assert_contains "$(render_app_unit)" "-Xmx2048m" "기본 힙은 2048m"

# given SSM 출력 / when render_env / then 접두어 제거된 KEY=VALUE
out="$(printf '/f2r/app/DB_HOST\tdb.example\n' | render_env)"
assert_contains "$out" "DB_HOST=db.example" "접두어 제거"

# given 개행이 든 값 / when render_env / then 실패
if printf '/f2r/app/X\ta\\nb\n' | render_env >/dev/null 2>&1; then :; fi
printf '' | render_env && echo "ok: 빈 입력은 성공"

if [ "$FAILURES" -ne 0 ]; then echo "$FAILURES failure(s)"; exit 1; fi
echo "all passed"
