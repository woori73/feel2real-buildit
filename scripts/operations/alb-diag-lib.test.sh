#!/usr/bin/env bash
# alb-diag-lib.sh 단위 테스트 (given/when/then). 실행: bash scripts/operations/alb-diag-lib.test.sh
set -u
cd "$(dirname "$0")"
# shellcheck source=alb-diag-lib.sh
source ./alb-diag-lib.sh

fail=0
assert_eq() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1"; echo "  expected: $2"; echo "  actual:   $3"; fail=1; fi
}
assert_ok()   { if "${@:2}" >/dev/null 2>&1; then echo "PASS  $1"; else echo "FAIL  $1 (expected success)"; fail=1; fi; }
assert_fail() { if "${@:2}" >/dev/null 2>&1; then echo "FAIL  $1 (expected failure)"; fail=1; else echo "PASS  $1"; fi; }

# given 올바른 1시간 구간 / when 검증 / then 성공
assert_ok "window: valid 1h" diag_validate_window 2026-10-19T20:30:00Z 2026-10-19T21:30:00Z
# given 형식이 틀린 값 / when 검증 / then 실패
assert_fail "window: bad format" diag_validate_window "2026-10-19 20:30" 2026-10-19T21:30:00Z
# given 시작이 끝보다 늦음 / when 검증 / then 실패
assert_fail "window: reversed" diag_validate_window 2026-10-19T21:30:00Z 2026-10-19T20:30:00Z
# given 24시간 초과 / when 검증 / then 실패
assert_fail "window: over 24h" diag_validate_window 2026-10-18T00:00:00Z 2026-10-19T00:00:01Z
# given 존재하지 않는 날짜 / when 검증 / then 실패
assert_fail "window: impossible date" diag_validate_window 2026-02-31T00:00:00Z 2026-03-01T00:00:00Z

# given ISO 시각 / when journal 형식으로 변환 / then 'YYYY-MM-DD HH:MM:SS UTC'
assert_eq "journal time" "2026-10-19 20:30:00 UTC" "$(diag_to_journal_time 2026-10-19T20:30:00Z)"

# given 0과 양수가 섞인 합계형 데이터 / when 요약 / then 0 초과 시각만 나열
out=$(printf '2026-10-19T21:08:00Z\t0.0\n2026-10-19T21:09:00Z\t11.0\n2026-10-19T21:10:00Z\t4.0\n' | diag_summ_sum)
assert_eq "summ_sum header" "  points=3 nonzero=2 sum=15 max=11" "$(printf '%s' "$out" | head -1)"
assert_eq "summ_sum lists nonzero only" "2" "$(printf '%s\n' "$out" | grep -c '^    ')"

# given 데이터 없음 / when 합계 요약 / then 0건으로 표시
assert_eq "summ_sum empty" "  points=0 nonzero=0 sum=0 max=0" "$(printf '' | diag_summ_sum)"

# given 평균형 데이터 / when 요약 / then 최솟값·평균·최댓값과 최댓값 시각
out=$(printf 'T1\t10\nT2\t30\nT3\t20\n' | diag_summ_avg)
assert_eq "summ_avg" "  points=3 min=10 avg=20.0000 max=30 at T2" "$out"

# given 데이터 없음 / when 평균 요약 / then points=0
assert_eq "summ_avg empty" "  points=0" "$(printf '' | diag_summ_avg)"

# given 값이 None(데이터 없음) / when 요약 / then 건수에서 제외
assert_eq "summ_avg ignores None" "  points=1 min=5 avg=5.0000 max=5 at T1" "$(printf 'T1\t5\nT2\tNone\n' | diag_summ_avg)"

exit $fail
