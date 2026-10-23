#!/usr/bin/env bash
# studio-postdeploy-diag.sh 로그 정규화 단위 테스트 (given/when/then)
set -u
cd "$(dirname "$0")"
# shellcheck source=studio-postdeploy-diag.sh
source ./studio-postdeploy-diag.sh
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1"; echo "  expected: $2"; echo "  actual:   $3"; fail=1; fi; }

# given Spring 형식 WARN 한 줄 / when 정규화 / then 수준·로거·메시지만 남고 숫자는 #
out=$(printf '2026-10-23T02:17:40.123Z  WARN 90488 --- [app] [main] o.h.engine.jdbc.Foo : took 1234 ms\n' | normalize_log)
check "warn line" "WARN o.h.engine.jdbc.Foo : took # ms" "$out"
# given 이메일·토큰이 든 ERROR / when 정규화 / then 가림
out=$(printf '2026-10-23T02:17:40.123Z ERROR 90488 --- [app] [http-1] c.x.Svc : user a.b@c.com TOKEN=abc123 failed\n' | normalize_log)
check "masking" "ERROR c.x.Svc : user <email> TOKEN=<masked> failed" "$out"
# given INFO 줄 / when 정규화 / then 출력 없음
out=$(printf '2026-10-23T02:17:40.123Z  INFO 90488 --- [app] [main] c.x.Svc : started\n' | normalize_log)
check "info ignored" "" "$out"
exit $fail
