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
REQUIRE_ENV_FILE=""
curl() {
  if [ "$FORCE_ALL_UNHEALTHY" = "1" ]; then return 1; fi
  # ENV 케이스용: 이 파일 내용이 GOODENV 가 아니면 서비스는 unhealthy (잘못된 env로는 기동 못 함).
  if [ -n "$REQUIRE_ENV_FILE" ] && [ "$(cat "$REQUIRE_ENV_FILE" 2>/dev/null)" != "GOODENV" ]; then return 1; fi
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
# CASE 1~4 는 PID가 안 바뀌는 세상을 가정한다 — 항상 같은 값.
f2r__service_main_pid() { echo "1000"; }

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

# ── CASE 5: 배포 사고 재현 — PID A가 죽고 systemd가 PID B를 새로 띄우면, B는 A의
# 남은 카운트다운이 아니라 자기 몫의 새 health window를 받아야 성공한다 ─────────
# (old f2r_wait_for_health 였다면: 총 attempts=3 을 A/B 합쳐서 나눠 쓰다가 소진되어
#  실패했을 시나리오 — B가 등장하자마자 healthy인데도 "A 기준 카운트다운 소진"으로
#  실패 처리되는 게 실제 배포 사고였다.)
#
# NOTE on implementation: curl/f2r__service_main_pid run inside f2r_health_ok via
# command substitution ($(...)), which is a SUBSHELL — plain shell variable mutations
# made inside these stub functions would NOT be visible to the caller once the
# subshell exits. State here is tracked in files instead, since file writes are real
# OS-level side effects that survive subshell boundaries.
case5_pid_replacement_gets_its_own_health_window() {
  local state_dir; state_dir=$(mktemp -d)
  echo 0 > "$state_dir/calls"
  echo A > "$state_dir/pid"
  curl() {
    local n; n=$(($(cat "$state_dir/calls") + 1)); echo "$n" > "$state_dir/calls"
    if [ "$n" -eq 3 ]; then
      echo B > "$state_dir/pid"  # A가 3번째 poll 시점에 죽고 systemd가 B를 새로 띄웠다고 가정
    fi
    local pid; pid=$(cat "$state_dir/pid")
    if [ "$pid" = "B" ] && [ "$n" -ge 4 ]; then
      echo -n 200
      return 0
    fi
    return 1
  }
  f2r__service_main_pid() { cat "$state_dir/pid"; }
  RESTART_CALLS=0
  # attempts_per_pid=3, max_generations=2 — old(non-pid-aware) 로직이었다면 총 3회
  # 만에 실패했을 예산. pid-aware 로직은 B에게 3회를 새로 준다.
  f2r_wait_for_health_pid_aware "f2r-app.service" "http://127.0.0.1:8080/actuator/health" 3 0 2
  local status=$?
  assert_eq "0" "$status" "CASE 5: replacement PID becomes healthy using its own fresh window"
  assert_eq "4" "$(cat "$state_dir/calls")" "CASE 5: succeeded on generation 2's first check (call 4) — old non-pid-aware logic would have exhausted its 3-call total budget on generation 1 and failed before ever reaching this call"
  rm -rf "$state_dir"
}
echo "--- CASE 5: PID replaced mid-wait -> new PID gets its own health window (not old PID's leftover countdown) ---"
case5_pid_replacement_gets_its_own_health_window

# ── CASE 6: PID이 계속 바뀌기만 하고(=계속 재시작만 되고) 한 번도 healthy가 안 되면,
# systemd가 계속 재시작을 허용해도 이 가드는 max_generations 에서 확정 실패해야 한다
# (= systemd의 무한 재시작을 그대로 받아들이지 않는다) ────────────────────────
case6_bounded_generations_no_infinite_restart_acceptance() {
  local state_dir; state_dir=$(mktemp -d)
  echo 0 > "$state_dir/pid_counter"
  curl() { return 1; }  # 무엇을 하든 절대 healthy가 안 됨
  f2r__service_main_pid() {
    local n; n=$(($(cat "$state_dir/pid_counter") + 1)); echo "$n" > "$state_dir/pid_counter"
    echo "PID-${n}"  # poll마다 다른 pid = 매번 "새로 재시작됨"으로 관찰됨
  }
  f2r_wait_for_health_pid_aware "f2r-app.service" "http://127.0.0.1:8080/actuator/health" 2 0 3
  local status=$?
  assert_eq "1" "$status" "CASE 6: repeated-exit crash loop fails instead of waiting forever"
  rm -rf "$state_dir"
}
echo "--- CASE 6: PID keeps changing without ever healthy -> bounded failure, not infinite wait ---"
case6_bounded_generations_no_infinite_restart_acceptance

# CASE 5/6 가 남겨 둔 curl/f2r__service_main_pid 오버라이드를 CASE 1~4 기본값으로 되돌린다
# (이 파일 안에서 순서를 바꿔도 서로 오염되지 않게).
REQUIRE_ENV_FILE=""
curl() {
  if [ "$FORCE_ALL_UNHEALTHY" = "1" ]; then return 1; fi
  # ENV 케이스용: 이 파일 내용이 GOODENV 가 아니면 서비스는 unhealthy (잘못된 env로는 기동 못 함).
  if [ -n "$REQUIRE_ENV_FILE" ] && [ "$(cat "$REQUIRE_ENV_FILE" 2>/dev/null)" != "GOODENV" ]; then return 1; fi
  local content=""
  [ -f "$CURRENT_JAR_PATH" ] && content=$(cat "$CURRENT_JAR_PATH")
  if [ "$content" = "GOOD" ]; then
    echo -n 200
    return 0
  fi
  return 1
}
f2r__service_main_pid() { echo "1000"; }

# ═══ ENV 백업/복원 케이스 (E1~E10) ═══════════════════════════════════════════
# 서비스 재시작이 일어난 "그 시점의 env 내용"을 EVENTS 에 남겨 순서(복원 → 재시작 → 헬스)를 검증한다.
SECRET_MARK="SECRETVALUE-9f3a"
EVENTS_FILE=$(mktemp)
f2r__service_restart() { RESTART_CALLS=$((RESTART_CALLS + 1)); echo "restart:$1:env=$(cat "$ENV_UNDER_TEST" 2>/dev/null | head -c 40)" >> "$EVENTS_FILE"; }
if [ "$(id -u)" = "0" ]; then ORIG_OWNER="12345:12345"; else ORIG_OWNER="$(id -u):$(id -g)"; fi

# given: env 두 개(+선택적으로 identity 부재)를 가진 가짜 서버 디렉터리
env_fixture() {
  ENV_DIR=$(mktemp -d)
  ENV_UNDER_TEST="$ENV_DIR/env"; IDENT="$ENV_DIR/identity.env"
  printf 'GOODENV' > "$ENV_UNDER_TEST"
  printf 'WIKI_IDENTITY_SECRET=%s\n' "$SECRET_MARK" > "$IDENT"
  chmod 600 "$ENV_UNDER_TEST" "$IDENT"; chown "$ORIG_OWNER" "$ENV_UNDER_TEST" "$IDENT"
  BK="$ENV_DIR/backup"; : > "$EVENTS_FILE"
  RESTART_CALLS=0; FORCE_ALL_UNHEALTHY=0; REQUIRE_ENV_FILE=""
}
# 서비스 매핑(복원된 파일 → 재시작할 서비스)은 운영 경로 이름(/etc/f2r/env 등)으로 판단한다.
# 그래서 E4/E6/E7 은 임시 경로로 복원 자체(내용·소유자·권한)를 검증하고, E5/E8 은 복원 결과를
# 스텁으로 주입해 재시작 순서·서비스 매핑만 검증한다.

e1_snapshot_saves_verified_copies() {
  env_fixture; rm -f "$IDENT"   # identity 는 부재 상태
  # when
  f2r_env_snapshot "$BK" "$ENV_UNDER_TEST" "$IDENT" >/dev/null; local rc=$?
  # then
  assert_eq "0" "$rc" "E1: snapshot succeeds"
  assert_eq "700" "$(stat -c %a "$BK")" "E1: backup dir is root-only (0700)"
  assert_eq "$(sha256sum < "$ENV_UNDER_TEST")" "$(sha256sum < "$BK/env")" "E1: backup content identical"
  assert_eq "1" "$(grep -c -P "identity.env\tabsent" "$BK/MANIFEST.tsv")" "E1: absent file recorded as absent"
  assert_eq "$ORIG_OWNER 600" "$(stat -c '%u:%g %a' "$BK/env")" "E1: backup keeps owner and mode"
  rm -rf "$ENV_DIR"
}
echo "--- E1: env snapshot saves verified copies, records absent files ---"
e1_snapshot_saves_verified_copies

e2_snapshot_failure_is_not_success() {
  env_fixture
  # given: 읽을 수 없는/일반 파일이 아닌 대상 (디렉터리)
  mkdir "$ENV_DIR/notafile"
  # when
  local out; out=$(f2r_env_snapshot "$BK" "$ENV_UNDER_TEST" "$ENV_DIR/notafile" 2>&1); local rc=$?
  # then
  assert_eq "1" "$rc" "E2: snapshot of a non-regular file fails (deploy must abort)"
  assert_eq "1" "$(echo "$out" | grep -c 'not a readable regular file')" "E2: failure is reported"
  # given: 백업 위치를 만들 수 없음 (경로 중간이 일반 파일)
  : > "$ENV_DIR/blocker"
  f2r_env_snapshot "$ENV_DIR/blocker/sub" "$ENV_UNDER_TEST" >/dev/null 2>&1; rc=$?
  assert_eq "1" "$rc" "E2: cannot create backup dir -> failure"
  rm -rf "$ENV_DIR"
}
echo "--- E2: backup failure is never treated as success ---"
e2_snapshot_failure_is_not_success

e3_normal_deploy_does_not_touch_unchanged_env() {
  env_fixture
  f2r_env_snapshot "$BK" "$ENV_UNDER_TEST" "$IDENT" >/dev/null
  local ino_before; ino_before=$(stat -c %i "$ENV_UNDER_TEST")
  # when: env 변경 없이(정상 배포 가정) 복원 판정
  f2r_env_restore_changed "$BK" >/dev/null; local rc=$?
  # then
  assert_eq "0" "$rc" "E3: restore check succeeds"
  assert_eq "0" "${#F2R_ENV_RESTORED_FILES[@]}" "E3: nothing restored when env is unchanged"
  assert_eq "$ino_before" "$(stat -c %i "$ENV_UNDER_TEST")" "E3: unchanged env file was not overwritten (same inode)"
  : > "$EVENTS_FILE"; RESTART_CALLS=0
  f2r_env_rollback_after_failed_deploy "$BK" f2r-app.service "http://x/health" 1 0 1 >/dev/null; rc=$?
  assert_eq "0" "$rc" "E3: rollback helper is a no-op success when env unchanged"
  assert_eq "0" "$RESTART_CALLS" "E3: no extra service restart when env unchanged"
  rm -rf "$ENV_DIR"
}
echo "--- E3: unchanged env is not overwritten, no extra restart ---"
e3_normal_deploy_does_not_touch_unchanged_env

e4_failed_deploy_restores_env_then_restarts_then_checks_health() {
  env_fixture
  # given: 현재 jar 정상, 새 jar 비정상, 배포 도중 env 가 키 동기화로 바뀜 (+ 소유자/권한도 변경됨)
  echo GOOD > "$ENV_DIR/current.jar"; echo BAD > "$ENV_DIR/new.jar"; CURRENT_JAR_PATH="$ENV_DIR/current.jar"
  REQUIRE_ENV_FILE=""
  f2r_env_snapshot "$BK" "$ENV_UNDER_TEST" "$IDENT" >/dev/null
  local before_sha; before_sha=$(sha256sum < "$ENV_UNDER_TEST")
  printf 'GOODENV\nMETA_KEY=%s\n' "$SECRET_MARK" > "$ENV_UNDER_TEST"; chmod 644 "$ENV_UNDER_TEST"
  # when: 배포 실패(jar 롤백 rc=1) 후 env 롤백
  f2r_deploy_with_rollback "$ENV_DIR/current.jar" "$ENV_DIR/new.jar" "$ENV_DIR/current.jar.previous" f2r-app.service "http://x/health" 2 0 >/dev/null 2>&1
  local jar_rc=$?
  : > "$EVENTS_FILE"; RESTART_CALLS=0; REQUIRE_ENV_FILE="$ENV_UNDER_TEST"
  local out; out=$(f2r_env_rollback_after_failed_deploy "$BK" f2r-app.service "http://x/health" 2 0 1 2>&1); local rc=$?
  # then
  assert_eq "1" "$jar_rc" "E4: jar rollback behaviour preserved (rc=1, previous jar restored)"
  assert_eq "GOOD" "$(cat "$ENV_DIR/current.jar")" "E4: jar rolled back as before"
  assert_eq "$before_sha" "$(sha256sum < "$ENV_UNDER_TEST")" "E4: env content restored byte-identical"
  assert_eq "$ORIG_OWNER 600" "$(stat -c '%u:%g %a' "$ENV_UNDER_TEST")" "E4: env owner and mode restored"
  assert_eq "0" "$([ -e "$ENV_UNDER_TEST".f2r-restore.* ] 2>/dev/null && echo 1 || echo 0)" "E4: no temp file left behind"
  assert_eq "0" "$(echo "$out" | grep -c "$SECRET_MARK")" "E4: no secret value in log output"
  rm -rf "$ENV_DIR"
}
echo "--- E4: failed deploy -> jar rollback kept, env restored byte/owner/mode-identical ---"
e4_failed_deploy_restores_env_then_restarts_then_checks_health

e5_order_restore_then_restart_then_health() {
  env_fixture
  : > "$EVENTS_FILE"; RESTART_CALLS=0
  echo GOOD > "$ENV_DIR/current.jar"; CURRENT_JAR_PATH="$ENV_DIR/current.jar"
  f2r_env_restore_changed() {
    echo "restore" >> "$EVENTS_FILE"
    printf 'GOODENV' > "$ENV_UNDER_TEST"      # 복원 결과 = 정상 env
    F2R_ENV_RESTORED_FILES=(/etc/f2r/env)
    return 0
  }
  REQUIRE_ENV_FILE="$ENV_UNDER_TEST"
  printf 'BADENV' > "$ENV_UNDER_TEST"           # 복원 전에는 unhealthy (롤백 후에도 실패하던 상황 = rc 2 재현)
  # when
  f2r_env_rollback_after_failed_deploy "$BK" f2r-app.service "http://x/health" 2 0 1 >/dev/null; local rc=$?
  # then
  assert_eq "0" "$rc" "E5: becomes healthy after env restore (env was the cause of the failed rollback)"
  assert_eq "restore" "$(sed -n 1p "$EVENTS_FILE")" "E5: step 1 is env restore"
  assert_eq "restart:f2r-app.service:env=GOODENV" "$(sed -n 2p "$EVENTS_FILE")" "E5: step 2 restart happens AFTER restore (sees restored env)"
  assert_eq "1" "$RESTART_CALLS" "E5: exactly one restart"
  # given: 복원은 됐지만 그래도 unhealthy
  FORCE_ALL_UNHEALTHY=1; DIAG_DUMPS=0
  f2r_env_rollback_after_failed_deploy "$BK" f2r-app.service "http://x/health" 2 0 1 >/dev/null 2>&1; rc=$?
  assert_eq "2" "$rc" "E5: still unhealthy after env restore -> failure (rc 2), not success"
  assert_eq "1" "$([ "$DIAG_DUMPS" -ge 1 ] && echo 1 || echo 0)" "E5: diagnostics dumped"
  unset -f f2r_env_restore_changed
  source "$SCRIPT_DIR/f2r_app_guard.sh"        # 스텁 제거: 실제 함수 재로드 (스텁 오버라이드 복구는 아래에서 다시)
  restub_services
  rm -rf "$ENV_DIR"
}
# source 로 가드를 다시 읽으면 서비스 스텁이 덮이므로 복구 함수를 둔다.
restub_services() {
  f2r__service_restart() { RESTART_CALLS=$((RESTART_CALLS + 1)); echo "restart:$1:env=$(cat "$ENV_UNDER_TEST" 2>/dev/null | head -c 40)" >> "$EVENTS_FILE"; }
  f2r__service_status_dump() { DIAG_DUMPS=$((DIAG_DUMPS + 1)); }
  f2r__service_journal_dump() { :; }
  f2r__service_main_pid() { echo "1000"; }
}
echo "--- E5: order is restore -> restart -> health; unhealthy after restore is a failure ---"
e5_order_restore_then_restart_then_health

e6_restore_failure_is_reported_and_blocks_restart() {
  env_fixture
  f2r_env_snapshot "$BK" "$ENV_UNDER_TEST" "$IDENT" >/dev/null
  printf 'CHANGED' > "$ENV_UNDER_TEST"
  : > "$EVENTS_FILE"; RESTART_CALLS=0
  # given: 백업 사본이 손상됨
  printf 'corrupted' > "$BK/env"
  # when
  local out; out=$(f2r_env_rollback_after_failed_deploy "$BK" f2r-app.service "http://x/health" 1 0 1 2>&1); local rc=$?
  # then
  assert_eq "2" "$rc" "E6: corrupted backup -> restore failure reported as rc 2"
  assert_eq "1" "$(echo "$out" | grep -c 'ENV RESTORE FAILED')" "E6: explicit ENV RESTORE FAILED error"
  assert_eq "0" "$RESTART_CALLS" "E6: service NOT restarted after a failed restore"
  assert_eq "CHANGED" "$(cat "$ENV_UNDER_TEST")" "E6: live env left as-is (no half-written file)"
  # given: 매니페스트 없음
  rm -f "$BK/MANIFEST.tsv"
  f2r_env_rollback_after_failed_deploy "$BK" f2r-app.service "http://x/health" 1 0 1 >/dev/null 2>&1; rc=$?
  assert_eq "2" "$rc" "E6: missing manifest -> failure, not success"
  rm -rf "$ENV_DIR"
}
echo "--- E6: restore failure (corrupt backup / missing manifest) is never success, no restart ---"
e6_restore_failure_is_reported_and_blocks_restart

e7_file_created_by_deploy_is_removed_and_service_mapping() {
  env_fixture; rm -f "$IDENT"
  f2r_env_snapshot "$BK" "$ENV_UNDER_TEST" "$IDENT" >/dev/null
  # given: 배포가 identity env 를 새로 만들고 env 도 바꿈
  printf 'WIKI_IDENTITY_SECRET=%s\n' "$SECRET_MARK" > "$IDENT"; printf 'X' > "$ENV_UNDER_TEST"
  # when
  f2r_env_restore_changed "$BK" > "$ENV_DIR/restore.log" 2>&1; local rc=$?
  local out; out=$(cat "$ENV_DIR/restore.log")
  # then
  assert_eq "0" "$rc" "E7: restore succeeds"
  assert_eq "0" "$([ -e "$IDENT" ] && echo 1 || echo 0)" "E7: file that did not exist before the deploy is removed"
  assert_eq "2" "${#F2R_ENV_RESTORED_FILES[@]}" "E7: both changed files reported restored"
  assert_eq "0" "$(echo "$out" | grep -c "$SECRET_MARK")" "E7: no secret value in log output"
  rm -rf "$ENV_DIR"
}
echo "--- E7: file created during deploy is removed on restore ---"
e7_file_created_by_deploy_is_removed_and_service_mapping

e8_service_mapping_restarts_only_affected_services() {
  env_fixture
  : > "$EVENTS_FILE"; RESTART_CALLS=0
  # given: identity env 만 복원된 상황
  f2r_env_restore_changed() { F2R_ENV_RESTORED_FILES=(/etc/buildit-wiki-identity.env); return 0; }
  # when
  f2r_env_rollback_after_failed_deploy "$BK" f2r-app.service "http://x/health" 1 0 1 >/dev/null; local rc=$?
  # then
  assert_eq "0" "$rc" "E8: success"
  assert_eq "1" "$(grep -c '^restart:buildit-wiki.service' "$EVENTS_FILE")" "E8: only buildit-wiki restarted"
  assert_eq "0" "$(grep -c '^restart:f2r-app.service' "$EVENTS_FILE")" "E8: Spring not restarted when /etc/f2r/env is unchanged"
  unset -f f2r_env_restore_changed; source "$SCRIPT_DIR/f2r_app_guard.sh"; restub_services
  rm -rf "$ENV_DIR"
}
echo "--- E8: only the services whose env was restored are restarted ---"
e8_service_mapping_restarts_only_affected_services

e9_prune_keeps_latest_n() {
  local root; root=$(mktemp -d)
  mkdir "$root/20260101-000000-1" "$root/20260102-000000-1" "$root/20260103-000000-1" "$root/20260104-000000-1"
  f2r_env_prune_backups "$root" 2
  assert_eq "2" "$(find "$root" -mindepth 1 -maxdepth 1 -type d | wc -l)" "E9: only the latest N backups kept"
  assert_eq "1" "$([ -d "$root/20260104-000000-1" ] && echo 1 || echo 0)" "E9: newest backup kept"
  assert_eq "0" "$([ -d "$root/20260101-000000-1" ] && echo 1 || echo 0)" "E9: oldest backup pruned"
  f2r_env_prune_backups "$root/does-not-exist" 2; assert_eq "0" "$?" "E9: missing root is harmless"
  rm -rf "$root"
}
echo "--- E9: old env backups are pruned (secret copies do not accumulate) ---"
e9_prune_keeps_latest_n

# E10: 워크플로 계약 — 실제 배포 스크립트 안에서 호출 순서가 지켜지는지 정적 확인.
e10_workflow_contract() {
  local wf="$SCRIPT_DIR/../../.github/workflows/deploy-ec2.yml"
  local snap guard ident rollback
  guard=$(grep -n 'cat scripts/deploy/f2r_app_guard.sh >> /tmp/deploy-buildit.sh' "$wf" | head -1 | cut -d: -f1)
  snap=$(grep -n 'f2r_env_snapshot "\$F2R_ENV_BACKUP_DIR"' "$wf" | head -1 | cut -d: -f1)
  ident=$(grep -n "install -m 600 -o root -g root /dev/null /etc/buildit-wiki-identity.env" "$wf" | head -1 | cut -d: -f1)
  rollback=$(grep -n 'f2r_env_rollback_after_failed_deploy "\$F2R_ENV_BACKUP_DIR"' "$wf" | head -1 | cut -d: -f1)
  assert_eq "1" "$([ -n "$guard" ] && [ -n "$snap" ] && [ "$guard" -lt "$snap" ] && echo 1 || echo 0)" "E10: guard functions are defined before the snapshot call"
  assert_eq "1" "$([ -n "$snap" ] && [ -n "$ident" ] && [ "$snap" -lt "$ident" ] && echo 1 || echo 0)" "E10: snapshot runs before any env modification (identity rewrite)"
  assert_eq "1" "$([ -n "$rollback" ] && echo 1 || echo 0)" "E10: failure path calls the env rollback"
  assert_eq "1" "$(grep -c 'ABORT env_backup_failed' "$wf")" "E10: backup failure aborts the deploy"
  local first_edit; first_edit=$(grep -n "grep -vE '\^META_VISION_API_KEY=' /etc/f2r/env" "$wf" | head -1 | cut -d: -f1)
  assert_eq "1" "$([ "$snap" -lt "$first_edit" ] && echo 1 || echo 0)" "E10: snapshot runs before the key syncs"
}
echo "--- E10: workflow call order contract ---"
e10_workflow_contract

# ── GAP: 서비스 공백 측정 함수(읽기 전용) ───────────────────────────────────
gap_cases() {
  local unit="f2r-app.service"
  # given Stopping 후 41초 뒤 Tomcat started → when 계산 → then 41
  f2r__journal_unix() {
    cat <<'JOURNAL'
1760918400.100000 host systemd[1]: Started f2r-app.service - old
1760918431.000000 host systemd[1]: Stopping f2r-app.service - f2r BuildIt Spring Boot app...
1760918432.000000 host systemd[1]: Stopped f2r-app.service - f2r BuildIt Spring Boot app.
1760918432.500000 host systemd[1]: Started f2r-app.service - f2r BuildIt Spring Boot app.
1760918472.000000 host java[114913]: 2026 INFO TomcatWebServer: Tomcat started on port 8080 (http)
JOURNAL
  }
  assert_eq "41" "$(f2r_service_gap_seconds "$unit")" "GAP: Stopping -> Tomcat started seconds"
  assert_eq "[DEPLOY] service_gap_seconds=41 (journald: Stopping f2r-app.service -> Tomcat started)" \
    "$(f2r_report_service_gap "$unit")" "GAP: report line"

  # given 롤백으로 재시작이 두 번 → when 계산 → then 마지막 재시작 기준(12초)
  f2r__journal_unix() {
    cat <<'JOURNAL'
1760918431.000000 host systemd[1]: Stopping f2r-app.service - x
1760918472.000000 host java[1]: Tomcat started on port 8080 (http)
1760918500.000000 host systemd[1]: Stopping f2r-app.service - x
1760918512.000000 host java[2]: Tomcat started on port 8080 (http)
JOURNAL
  }
  assert_eq "12" "$(f2r_service_gap_seconds "$unit")" "GAP: uses the last restart cycle"

  # given Stopping만 있고 Tomcat started가 없음(기동 실패) → when → then 계산 불가, 리포트는 unavailable
  f2r__journal_unix() { echo "1760918431.000000 host systemd[1]: Stopping f2r-app.service - x"; }
  f2r_service_gap_seconds "$unit" >/dev/null
  assert_eq "1" "$?" "GAP: no Tomcat started line -> not computable (rc 1)"
  assert_eq "1" "$(f2r_report_service_gap "$unit" | grep -c 'service_gap_seconds=unavailable')" "GAP: unavailable is reported"

  # given 빈 journal → when 리포트 → then 항상 성공(0) — 배포 판정에 영향 없음
  f2r__journal_unix() { :; }
  f2r_report_service_gap "$unit" >/dev/null
  assert_eq "0" "$?" "GAP: report never fails the deploy (rc 0)"

  # given Tomcat started가 Stopping보다 앞선 줄만 있음 → then 계산 불가
  f2r__journal_unix() {
    cat <<'JOURNAL'
1760918400.000000 host java[1]: Tomcat started on port 8080 (http)
1760918431.000000 host systemd[1]: Stopping f2r-app.service - x
JOURNAL
  }
  f2r_service_gap_seconds "$unit" >/dev/null
  assert_eq "1" "$?" "GAP: Tomcat started before Stopping is ignored"
}
echo "--- GAP: service gap measurement (read-only) ---"
gap_cases

# ── E11: 공백 측정 호출 위치 계약 ─────────────────────────────────────────
e11_workflow_contract() {
  local wf="$SCRIPT_DIR/../../.github/workflows/deploy-ec2.yml"
  local gate report
  gate=$(grep -n 'echo "\[DEPLOY\] spring health OK"' "$wf" | head -1 | cut -d: -f1)
  report=$(grep -n 'f2r_report_service_gap f2r-app.service' "$wf" | head -1 | cut -d: -f1)
  assert_eq "1" "$([ -n "$gate" ] && [ -n "$report" ] && [ "$gate" -lt "$report" ] && echo 1 || echo 0)" "E11: gap report runs after the health gate passes"
  assert_eq "1" "$(grep -c 'f2r_report_service_gap f2r-app.service || true' "$wf")" "E11: gap report is non-fatal (|| true)"
}
echo "--- E11: gap report call-order contract ---"
e11_workflow_contract

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "All f2r_app_guard checks passed."
  exit 0
else
  echo "$FAILURES check(s) failed."
  exit 1
fi
