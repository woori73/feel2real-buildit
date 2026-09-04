#!/usr/bin/env bash
# scripts/deploy/f2r_app_guard.sh 의 로컬 검증. systemctl/journalctl/curl 이 없는 환경
# (이 저장소 개발 환경 포함)에서도 CASE 1~4 를 확인할 수 있도록 세 함수만 스텁으로
# 바꾼다 — 재시도 횟수·롤백 판단 로직 자체는 실제 코드 그대로 실행된다.
#
# 실행: bash scripts/deploy/f2r_app_guard.test.sh
#
# CASE 5(동시 배포 방지)는 GitHub Actions concurrency 설정의 몫이라 이 bash 테스트로는
# 검증할 수 없다 — .github/workflows/deploy-ec2.yml 의 concurrency 블록으로 대신 확인한다.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=f2r_app_guard.sh
source "$SCRIPT_DIR/f2r_app_guard.sh"

FAILURES=0
assert_eq() {
  local expected="$1" actual="$2" message="$3"
  if [ "$expected" != "$actual" ]; then
    echo "FAIL: $message (expected [$expected], got [$actual])"
    FAILURES=$((FAILURES + 1))
  else
    echo "ok: $message"
  fi
}

# health 재시도 사이 실제로 잠들지 않는다 — 테스트가 몇 분씩 걸리면 안 된다.
sleep() { :; }

# health는 "현재 배포된 jar 내용"으로 판정한다 — 실제로 mv/cp된 파일을 그대로 반영해
# f2r_deploy_with_rollback의 순서(백업 → 교체 → 재시작 → health)를 있는 그대로 검증한다.
CURRENT_JAR_PATH=""
FORCE_ALL_UNHEALTHY=0
curl() {
  if [ "$FORCE_ALL_UNHEALTHY" = "1" ]; then return 1; fi
  local content=""
  [ -f "$CURRENT_JAR_PATH" ] && content=$(cat "$CURRENT_JAR_PATH")
  if [ "$content" = "GOOD" ]; then
    echo -n 200
    return 0
  fi
  return 1
}

RESTART_CALLS=0
f2r__service_restart() { RESTART_CALLS=$((RESTART_CALLS + 1)); }
DIAG_DUMPS=0
f2r__service_status_dump() { DIAG_DUMPS=$((DIAG_DUMPS + 1)); }
f2r__service_journal_dump() { :; }

# ── CASE 1: NEW 정상 기동 → rollback 없음 ──────────────────────────────────
case1() {
  local dir; dir=$(mktemp -d)
  echo GOOD > "$dir/current.jar"
  echo GOOD > "$dir/new.jar"
  CURRENT_JAR_PATH="$dir/current.jar"
  FORCE_ALL_UNHEALTHY=0
  RESTART_CALLS=0
  f2r_deploy_with_rollback "$dir/current.jar" "$dir/new.jar" "$dir/current.jar.previous" \
    f2r-app.service "http://127.0.0.1:8080/actuator/health" 2 0
  local status=$?
  assert_eq "0" "$status" "CASE 1: exit code is success"
  assert_eq "GOOD" "$(cat "$dir/current.jar")" "CASE 1: current jar is the new (healthy) version"
  assert_eq "GOOD" "$(cat "$dir/current.jar.previous")" "CASE 1: previous jar backed up before swap"
  assert_eq "1" "$RESTART_CALLS" "CASE 1: service restarted exactly once (no rollback restart)"
  rm -rf "$dir"
}
echo "--- CASE 1: NEW starts healthy -> no rollback ---"
case1

# ── CASE 2: NEW startup 실패, 첫 배포(백업 없음) → 롤백 불가, 사람 개입 필요 ──
case2() {
  local dir; dir=$(mktemp -d)
  # current.jar 파일 자체가 없다 = 이 EC2에서의 첫 배포.
  echo BAD > "$dir/new.jar"
  CURRENT_JAR_PATH="$dir/current.jar"
  FORCE_ALL_UNHEALTHY=0
  RESTART_CALLS=0
  DIAG_DUMPS=0
  f2r_deploy_with_rollback "$dir/current.jar" "$dir/new.jar" "$dir/current.jar.previous" \
    f2r-app.service "http://127.0.0.1:8080/actuator/health" 2 0
  local status=$?
  assert_eq "2" "$status" "CASE 2: exit code signals unrecoverable (no previous version)"
  assert_eq "0" "$([ -f "$dir/current.jar.previous" ] && echo 1 || echo 0)" \
    "CASE 2: no backup file created when there was nothing to back up"
  assert_eq "1" "$([ "$DIAG_DUMPS" -ge 1 ] && echo 1 || echo 0)" "CASE 2: diagnostics were dumped"
  rm -rf "$dir"
}
echo "--- CASE 2: NEW fails on first-ever deploy (no previous to roll back to) ---"
case2

# ── CASE 3: NEW startup 실패 → PREVIOUS 복구 성공 → production 회복 ──────
case3() {
  local dir; dir=$(mktemp -d)
  echo GOOD > "$dir/current.jar"
  echo BAD > "$dir/new.jar"
  CURRENT_JAR_PATH="$dir/current.jar"
  FORCE_ALL_UNHEALTHY=0
  RESTART_CALLS=0
  f2r_deploy_with_rollback "$dir/current.jar" "$dir/new.jar" "$dir/current.jar.previous" \
    f2r-app.service "http://127.0.0.1:8080/actuator/health" 2 0
  local status=$?
  assert_eq "1" "$status" "CASE 3: exit code marks the deploy itself as failed"
  assert_eq "GOOD" "$(cat "$dir/current.jar")" "CASE 3: production is running the restored previous jar"
  assert_eq "2" "$RESTART_CALLS" "CASE 3: service restarted twice (new attempt + rollback restart)"
  rm -rf "$dir"
}
echo "--- CASE 3: NEW fails health -> automatic rollback restores previous, healthy ---"
case3

# ── CASE 4: 롤백 자체도 실패 → 명확한 ERROR + 진단 로그 ───────────────────
case4() {
  local dir; dir=$(mktemp -d)
  echo GOOD > "$dir/current.jar"
  echo BAD > "$dir/new.jar"
  CURRENT_JAR_PATH="$dir/current.jar"
  FORCE_ALL_UNHEALTHY=1  # 새 버전도 이전 버전도 전부 unhealthy (예: DB 장애 등 외부 요인)
  RESTART_CALLS=0
  DIAG_DUMPS=0
  f2r_deploy_with_rollback "$dir/current.jar" "$dir/new.jar" "$dir/current.jar.previous" \
    f2r-app.service "http://127.0.0.1:8080/actuator/health" 2 0
  local status=$?
  assert_eq "2" "$status" "CASE 4: exit code signals rollback also failed"
  assert_eq "2" "$RESTART_CALLS" "CASE 4: attempted both the new-version restart and the rollback restart"
  assert_eq "1" "$([ "$DIAG_DUMPS" -ge 2 ] && echo 1 || echo 0)" \
    "CASE 4: diagnostics dumped for both the failed new version and the failed rollback"
  rm -rf "$dir"
}
echo "--- CASE 4: rollback itself fails -> clear ERROR + diagnostics, no silent success ---"
case4

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "All f2r_app_guard checks passed."
  exit 0
else
  echo "$FAILURES check(s) failed."
  exit 1
fi
