#!/usr/bin/env bash
# a1-gates-lib.sh 단위 테스트 (given/when/then). 실행: bash scripts/operations/a1-gates-lib.test.sh
set -u
cd "$(dirname "$0")"
# shellcheck source=a1-gates-lib.sh
source ./a1-gates-lib.sh

fail=0
assert_eq() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1"; echo "  expected: $2"; echo "  actual:   $3"; fail=1; fi; }
assert_fail() { if "${@:2}" >/dev/null 2>&1; then echo "FAIL  $1 (expected failure)"; fail=1; else echo "PASS  $1"; fi; }

# given 가용 5500MB / when 메모리 게이트 / then PASS
assert_eq "mem pass" "PASS available=5500 min=2500" "$(a1_mem_gate 5500)"
# given 가용 2499MB / when 게이트 / then FAIL(종료코드 1)
assert_fail "mem fail below min" a1_mem_gate 2499
assert_eq "mem fail text" "FAIL available=2499 min=2500" "$(a1_mem_gate 2499)"
# given 가용 2500MB(경계) / when 게이트 / then PASS
assert_eq "mem boundary" "PASS available=2500 min=2500" "$(a1_mem_gate 2500)"
# given 숫자가 아닌 입력 / when 게이트 / then FAIL
assert_fail "mem invalid" a1_mem_gate abc
# given 연결 18 + 풀 10 / when 연결 게이트 / then PASS(예상 28 ≤ 40)
assert_eq "conn pass" "PASS estimated=28 limit=40" "$(a1_conn_gate 18 10)"
# given 연결 35 + 풀 10 / when 게이트 / then FAIL(예상 45 > 40)
assert_fail "conn fail" a1_conn_gate 35 10
# given 상한을 인자로 지정 / when 게이트 / then 그 값으로 판정
assert_eq "conn custom limit" "PASS estimated=45 limit=50" "$(a1_conn_gate 35 10 50)"

exit $fail
