#!/bin/bash
# guard_decide 단위 테스트 (given/when/then)
set -u
. "$(dirname "$0")/guard.sh"
fail=0
expect() { # 이름 기대값 실제값
  if [ "$2" = "$3" ]; then echo "ok: $1"; else echo "FAIL: $1 (기대 '$2', 실제 '$3')"; fail=1; fi
}
# given 여유 메모리 부족 / when 판단 / then 앱 상태와 무관하게 즉시 중지
expect "메모리 부족이면 즉시 중지" "stop-memory 0" "$(guard_decide 300 600 1 0 4)"
expect "메모리 부족이면 앱이 내려가 있어도 메모리 사유로 중지" "stop-memory 0" "$(guard_decide 300 600 0 2 4)"
# given 앱 정상 / when 판단 / then 유지하고 카운트 초기화
expect "앱 정상이면 유지·카운트 0" "keep 0" "$(guard_decide 5000 600 1 3 4)"
# given 앱이 잠깐 내려감(배포 재시작) / when 연속 3회 / then 아직 유지
expect "앱 다운 1회째 유지" "keep 1" "$(guard_decide 5000 600 0 0 4)"
expect "앱 다운 3회째 유지" "keep 3" "$(guard_decide 5000 600 0 2 4)"
# given 앱이 계속 내려가 있음 / when 4회째 / then 중지
expect "앱 다운 4회째 중지" "stop-app-down 4" "$(guard_decide 5000 600 0 3 4)"
# given 앱 다운 후 복구 / when 판단 / then 카운트 리셋
expect "복구되면 카운트 리셋" "keep 0" "$(guard_decide 5000 600 1 2 4)"
exit $fail
