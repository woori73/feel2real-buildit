#!/usr/bin/env bash
# f2r-app.service(Spring Boot) 무중단 배포 가드.
#
# 왜 있는가: 기능 PR을 merge/deploy한 뒤 Spring이 기동 실패하거나 기동 직후 종료돼
# feel2real.co.kr 이 접속 불가 상태가 되고, EC2에 수동 접속해 복구하는 일이 반복됐다.
# 이 파일은 그 반복을 끊기 위한 "backup → deploy → health 재시도 → 실패 시 자동 롤백"
# 로직을 순수 함수로만 담는다.
#
# 이 파일은 소싱(source)되는 용도로만 쓴다 — 최상위에서 아무 것도 실행하지 않는다.
# 그래서 두 곳에서 완전히 같은 코드를 쓸 수 있다:
#   1) .github/workflows/deploy-ec2.yml 이 "Create EC2 deploy script" 단계에서 이 파일을
#      그대로 잘라 넣어(cat) EC2에서 실제로 실행되는 deploy-buildit.sh 의 일부로 만든다.
#   2) scripts/deploy/f2r_app_guard.test.sh 가 curl/systemctl/journalctl 을 스텁으로 바꿔
#      로컬에서 CASE 1~4 를 검증한다.
# 두 사용처가 "cat" 한 줄로 완전히 같은 소스를 공유하므로, 테스트한 코드와 실제 배포되는
# 코드가 서로 다를 위험(drift)이 없다.
#
# 실제 EC2 상태 확인이 필요한 항목(예: f2r-app.service 유닛이 정확히 무엇을 하는지,
# /etc/f2r/env 실제 값, ALB 헬스체크 타임아웃)은 이 스크립트만으로는 검증할 수 없다 —
# 완료 보고에 별도로 남긴다.

set -uo pipefail

# 새 버전이 health를 통과할 때까지 몇 번, 몇 초 간격으로 재시도할지의 기본값.
# Spring Boot는 재시작 명령 직후 바로 응답하지 않는다 — 5초 x 24회 = 최대 120초 대기.
# 이 예산은 "한 PID"의 몫이다 — systemd가 Restart=on-failure로 새 PID를 띄우면 그 PID는
# 이 예산을 그대로 물려받지 않고 자기 몫을 새로 받는다(f2r_wait_for_health_pid_aware).
F2R_HEALTH_DEFAULT_ATTEMPTS=24
F2R_HEALTH_DEFAULT_SLEEP_SECS=5

# systemd가 Restart=on-failure로 몇 세대(=몇 번의 새 PID)까지 봐줄지의 기본 상한.
# 이 상한을 넘기면 "계속 재시작만 반복"으로 판단하고 더 기다리지 않는다 — systemd의
# 무한 재시작을 이 가드가 그대로 받아들이지 않는다는 뜻이다.
F2R_MAX_PID_GENERATIONS=3

# 내부에서 실제 systemctl/journalctl 을 부르는 지점을 한 곳으로 모아 둔다 — 테스트가
# 이 네 함수만 오버라이드하면 나머지 로직(재시도 횟수, PID 세대 판단, 롤백 판단)은 실제
# 코드 그대로 검증된다.
f2r__service_restart() { systemctl restart "$1"; }
f2r__service_status_dump() { systemctl status "$1" --no-pager -l || true; }
f2r__service_journal_dump() { journalctl -u "$1" -n 200 --no-pager || true; }
# 현재 MainPID. 서비스가 죽어서 아직 재시작 전이면 systemd는 빈 문자열/0 을 준다 —
# 그 경우도 "이전에 추적하던 pid와 다르다"로 자연스럽게 처리되도록 0으로 정규화한다.
f2r__service_main_pid() {
  local pid
  pid=$(systemctl show -p MainPID --value "$1" 2>/dev/null)
  echo "${pid:-0}"
}

# EC2 내부에서(127.0.0.1) health endpoint를 확인한다. curl 자체가 실패해도(연결 거부 등)
# "정상 아님"으로 취급한다 — set -e 환경에서 호출자가 죽지 않도록 항상 0/1만 반환한다.
f2r_health_ok() {
  local url="$1"
  local code
  code=$(curl -fsS -o /dev/null -w '%{http_code}' "$url" 2>/dev/null) || return 1
  [ "$code" = "200" ]
}

# 최대 attempts 회, sleep_secs 간격으로 health_ok를 재시도한다. 한 번이라도 통과하면
# 즉시 성공 반환 — "한 번 curl해서 실패하면 바로 종료" 하지 않는다.
#
# PID를 모르는(=systemd가 관리하지 않는) 대상을 기다릴 때 쓰는 단순 버전이다.
# f2r-app.service 처럼 systemd Restart=on-failure 가 붙은 대상은 이 함수가 아니라
# f2r_wait_for_health_pid_aware 를 쓴다 — PID가 바뀌는데도 원래 카운트다운을 그대로
# 이어가면(Deploy 사고의 원인) 새 프로세스가 사실상 남은 시간만 받기 때문이다.
f2r_wait_for_health() {
  local url="$1"
  local attempts="${2:-$F2R_HEALTH_DEFAULT_ATTEMPTS}"
  local sleep_secs="${3:-$F2R_HEALTH_DEFAULT_SLEEP_SECS}"
  local i=0
  while [ "$i" -lt "$attempts" ]; do
    if f2r_health_ok "$url"; then
      return 0
    fi
    i=$((i + 1))
    sleep "$sleep_secs"
  done
  return 1
}

# PID-aware health wait. "재시작 직후 PID A가 죽고 10초 뒤 systemd가 PID B를 새로
# 띄우는데, 헬스체크는 A 기준으로 시작한 원래 카운트다운을 그대로 이어가 B는 남은
# 시간만 받고 만다"는 실제 배포 장애의 원인을 직접 막는다:
#   - 매 poll마다 현재 MainPID를 같이 확인한다.
#   - 추적하던 pid와 다르면(=systemd가 새로 띄웠다) "새 세대"로 인식하고, 그 세대는
#     attempts_per_pid 를 처음부터 다시 받는다(이전 pid가 얼마나 기다렸었는지는 새
#     pid에게 넘어가지 않는다).
#   - 세대 수는 max_generations 로 상한을 둔다 — systemd가 계속 재시작을 허용해도
#     이 함수는 무한정 따라가며 기다리지 않는다("반복 종료"는 실패로 확정한다).
#   - 한 pid가 세대 교체 없이 자기 attempts_per_pid 를 다 쓰면(=재시작도 안 되고
#     그냥 응답도 안 하는 상태) 그 자체로 실패다.
f2r_wait_for_health_pid_aware() {
  local service_name="$1" url="$2"
  local attempts_per_pid="${3:-$F2R_HEALTH_DEFAULT_ATTEMPTS}"
  local sleep_secs="${4:-$F2R_HEALTH_DEFAULT_SLEEP_SECS}"
  local max_generations="${5:-$F2R_MAX_PID_GENERATIONS}"

  local generation=1
  local tracked_pid
  tracked_pid=$(f2r__service_main_pid "$service_name")
  echo "Health wait: ${service_name} MainPID=${tracked_pid} (generation ${generation}/${max_generations}, up to ${attempts_per_pid}x${sleep_secs}s each)"

  local i=0
  while [ "$generation" -le "$max_generations" ]; do
    if [ "$i" -ge "$attempts_per_pid" ]; then
      echo "::error::${service_name} (MainPID=${tracked_pid}) did not become healthy within its window and was not restarted by systemd — giving up"
      return 1
    fi
    if f2r_health_ok "$url"; then
      echo "${service_name} is healthy (MainPID=${tracked_pid}, generation ${generation}/${max_generations})."
      return 0
    fi
    local current_pid
    current_pid=$(f2r__service_main_pid "$service_name")
    if [ "$current_pid" != "$tracked_pid" ]; then
      generation=$((generation + 1))
      if [ "$generation" -gt "$max_generations" ]; then
        break
      fi
      echo "::warning::${service_name} MainPID changed ${tracked_pid} -> ${current_pid} (systemd restarted it after a crash) — generation ${generation}/${max_generations} gets its own fresh ${attempts_per_pid}x${sleep_secs}s health window"
      tracked_pid="$current_pid"
      i=0
      continue
    fi
    i=$((i + 1))
    sleep "$sleep_secs"
  done

  echo "::error::${service_name} exited and was restarted ${max_generations} time(s) without becoming healthy — repeated exit, not a single slow startup"
  return 1
}

# 실패 시 EC2에 다시 접속하지 않아도 GitHub Actions 로그에서 원인을 볼 수 있게 진단 정보를
# 남긴다. secret이 담길 수 있는 EnvironmentFile 내용 자체는 절대 출력하지 않는다 —
# systemctl status/journalctl 표준 출력만 남긴다.
f2r_dump_diagnostics() {
  local service_name="$1"
  echo "----- systemctl status ${service_name} -----"
  f2r__service_status_dump "$service_name"
  # SSM StandardOutputContent는 24000자로 잘린다 — 200줄 전체 덤프보다 근본 원인 줄을
  # 먼저 출력한다 (2026-10-06: Provider ID 누락이 200줄 뒤에 잘려서 안 보였다).
  echo "----- ${service_name} root-cause lines first (Caused by/APPLICATION FAILED/Provider ID/Unknown provider) -----"
  journalctl -u "$service_name" -n 400 --no-pager 2>/dev/null \
    | grep -a -E "Caused by|APPLICATION FAILED|Provider ID|Unknown provider" | tail -n 30 || true
  echo "----- journalctl -u ${service_name} (last 200 lines) -----"
  f2r__service_journal_dump "$service_name"
}

# 핵심 함수: 새 jar를 배포하고, health가 안 되면 자동으로 이전 jar로 되돌린다.
#
#   current_jar   현재 운영 중인 jar 경로 (예: /home/ec2-user/app.jar)
#   new_jar       새로 받은 jar 경로 (예: /tmp/app-new.jar)
#   backup_jar    직전 정상 jar를 보관할 경로 (예: /home/ec2-user/app.jar.previous)
#   service_name  systemd 서비스명 (예: f2r-app.service)
#   health_url    내부 health endpoint (예: http://127.0.0.1:8080/actuator/health)
#   attempts/sleep_secs  PID 세대당 health 재시도 파라미터 (생략 시 기본값)
#   max_generations      한 시도(NEW 또는 rollback)당 몇 세대(=몇 번의 새 PID)까지
#                         봐줄지 (생략 시 기본값) — systemd가 계속 재시작해도 이 상한을
#                         넘으면 "반복 종료"로 확정하고 기다림을 끝낸다.
#
# NEW 시도와 rollback 시도는 완전히 독립된 f2r_wait_for_health_pid_aware 호출이다 —
# rollback도 자기 자신의 MainPID 추적과 자기 자신의 attempts×generations 예산을 처음부터
# 새로 받는다. NEW가 재시작을 몇 번 썼는지는 rollback 예산에 전혀 영향을 주지 않는다.
#
# 이 함수는 service_name 으로 넘어온 서비스만 restart/health-check 한다 — 별도 서비스인
# buildit-wiki는 이 함수 안에서 절대 건드리지 않는다(호출자가 f2r-app.service 하나만
# 넘기면 그 서비스 하나만 대상이 된다).
#
# 반환값(exit code)으로 다섯 가지 결과를 구분한다 — 호출자가 그대로 워크플로 성공/실패에
# 반영한다:
#   0  NEW 정상 기동 (rollback 없음)                                → CASE 1
#   1  NEW 실패 → PREVIOUS로 롤백 성공, production 복구됨            → CASE 2/3 (배포 자체는 실패로 표시)
#   2  NEW 실패 → 롤백도 실패(원인: backup jar 없음/롤백 후도 unhealthy) → CASE 4, 즉시 사람 개입 필요
f2r_deploy_with_rollback() {
  local current_jar="$1" new_jar="$2" backup_jar="$3" service_name="$4" health_url="$5"
  local attempts="${6:-$F2R_HEALTH_DEFAULT_ATTEMPTS}"
  local sleep_secs="${7:-$F2R_HEALTH_DEFAULT_SLEEP_SECS}"
  local max_generations="${8:-$F2R_MAX_PID_GENERATIONS}"

  local had_previous=0
  if [ -f "$current_jar" ]; then
    cp -f "$current_jar" "$backup_jar"
    had_previous=1
  else
    echo "::warning::${current_jar} not found — first deploy, no previous version to roll back to"
  fi

  mv -f "$new_jar" "$current_jar"
  echo "Restarting ${service_name} with the new jar..."
  f2r__service_restart "$service_name"

  if f2r_wait_for_health_pid_aware "$service_name" "$health_url" "$attempts" "$sleep_secs" "$max_generations"; then
    return 0
  fi

  echo "::error::${service_name} failed its health check on the NEW version — rolling back"
  f2r_dump_diagnostics "$service_name"

  if [ "$had_previous" -ne 1 ]; then
    echo "::error::No previous jar backup available — cannot roll back automatically. Manual EC2 recovery required."
    return 2
  fi

  cp -f "$backup_jar" "$current_jar"
  echo "Restarting ${service_name} with the previous jar..."
  f2r__service_restart "$service_name"

  if f2r_wait_for_health_pid_aware "$service_name" "$health_url" "$attempts" "$sleep_secs" "$max_generations"; then
    echo "::error::Deploy FAILED — automatically rolled back to the previous version, which is healthy. Production is restored."
    return 1
  fi

  echo "::error::ROLLBACK ALSO FAILED — previous version is also unhealthy after restart. Production may be down. Manual EC2 recovery required."
  f2r_dump_diagnostics "$service_name"
  return 2
}

# ─────────────────────────────────────────────────────────────────────────────
# env 파일 백업·자동 복원 (jar 롤백은 위 f2r_deploy_with_rollback 이 그대로 담당한다)
#
# 왜 있는가: jar 롤백만으로는 배포 도중 바뀐 /etc/f2r/env(키 동기화, HWPX/ASSIST 스위치,
# OPENROUTER 동기화)가 되돌려지지 않는다. 배포 시작 전에 env를 스냅샷하고, 배포가 실패했을 때만
# "배포 도중 실제로 바뀐 파일"을 스냅샷 상태로 되돌린 뒤 서비스를 재시작·헬스 확인한다.
# 비밀값은 절대 출력하지 않는다(파일 이름·상태 단어만 출력, 내용·해시는 출력 안 함).
# ─────────────────────────────────────────────────────────────────────────────

F2R_ENV_BACKUP_KEEP=5
F2R_ENV_RESTORED_FILES=()

f2r__sha256() { sha256sum "$1" 2>/dev/null | cut -d' ' -f1; }
f2r__meta() { stat -c '%u:%g %a' "$1" 2>/dev/null; }

# f2r_env_snapshot <backup_dir> <file>...
# 각 파일을 소유자·권한까지 보존해 복사하고 sha256으로 복사본을 검증한다. 존재하지 않는 파일은
# "absent"로 기록한다(= 배포 중 새로 생기면 복원 때 제거). 하나라도 실패하면 1 — 호출자는 배포를 중단한다.
f2r_env_snapshot() {
  local dir="$1"; shift
  local manifest="$dir/MANIFEST.tsv" f name src_sha bak_sha meta
  install -d -m 700 "$dir" || { echo "::error::env backup: cannot create backup dir"; return 1; }
  : > "$manifest" && chmod 600 "$manifest" || { echo "::error::env backup: cannot create manifest"; return 1; }
  for f in "$@"; do
    name=$(basename "$f")
    if [ ! -e "$f" ]; then
      printf '%s\tabsent\t-\t-\t-\n' "$f" >> "$manifest" || return 1
      echo "env backup: ${name} absent (recorded)"
      continue
    fi
    if [ ! -f "$f" ] || [ ! -r "$f" ]; then
      echo "::error::env backup: ${name} is not a readable regular file"
      return 1
    fi
    cp -p "$f" "$dir/$name" || { echo "::error::env backup: copy of ${name} failed"; return 1; }
    src_sha=$(f2r__sha256 "$f"); bak_sha=$(f2r__sha256 "$dir/$name")
    if [ -z "$src_sha" ] || [ "$src_sha" != "$bak_sha" ]; then
      echo "::error::env backup: ${name} copy failed verification (changed during backup or unreadable)"
      return 1
    fi
    meta=$(f2r__meta "$f") || { echo "::error::env backup: stat of ${name} failed"; return 1; }
    printf '%s\tpresent\t%s\t%s\t%s\n' "$f" "${meta% *}" "${meta#* }" "$src_sha" >> "$manifest" || return 1
    echo "env backup: ${name} saved and verified"
  done
  return 0
}

# f2r_env_restore_changed <backup_dir>
# 스냅샷과 내용·소유자·권한이 모두 같은 파일은 건드리지 않는다. 다른 파일만 임시 파일 → 검증 →
# mv(원자적)로 복원하고 다시 검증한다. 복원된 파일은 F2R_ENV_RESTORED_FILES 에 담는다.
# 하나라도 실패하면 1 (성공으로 취급하지 않는다).
f2r_env_restore_changed() {
  local dir="$1" manifest="$1/MANIFEST.tsv" f state own mode sha name tmp rc=0
  F2R_ENV_RESTORED_FILES=()
  if [ ! -r "$manifest" ]; then
    echo "::error::env restore: backup manifest missing/unreadable"
    return 1
  fi
  while IFS=$'\t' read -r f state own mode sha; do
    [ -n "$f" ] || continue
    name=$(basename "$f")
    if [ "$state" = absent ]; then
      if [ -e "$f" ]; then
        if rm -f -- "$f" && [ ! -e "$f" ]; then
          echo "env restore: ${name} did not exist before the deploy — removed"
          F2R_ENV_RESTORED_FILES+=("$f")
        else
          echo "::error::env restore: could not remove ${name}"; rc=1
        fi
      else
        echo "env restore: ${name} unchanged (still absent)"
      fi
      continue
    fi
    if [ -f "$f" ] && [ "$(f2r__sha256 "$f")" = "$sha" ] && [ "$(f2r__meta "$f")" = "$own $mode" ]; then
      echo "env restore: ${name} unchanged — not touched"
      continue
    fi
    if [ ! -f "$dir/$name" ] || [ "$(f2r__sha256 "$dir/$name")" != "$sha" ]; then
      echo "::error::env restore: backup of ${name} missing or corrupted — NOT restoring"
      rc=1; continue
    fi
    tmp="${f}.f2r-restore.$$"
    if cp "$dir/$name" "$tmp" && chown "$own" "$tmp" && chmod "$mode" "$tmp" \
       && [ "$(f2r__sha256 "$tmp")" = "$sha" ] && mv -f "$tmp" "$f" \
       && [ "$(f2r__sha256 "$f")" = "$sha" ] && [ "$(f2r__meta "$f")" = "$own $mode" ]; then
      echo "env restore: ${name} restored to the pre-deploy state (content, owner, mode verified)"
      F2R_ENV_RESTORED_FILES+=("$f")
    else
      rm -f -- "$tmp"
      echo "::error::env restore: ${name} restore FAILED"
      rc=1
    fi
  done < "$manifest"
  return $rc
}

# f2r_env_rollback_after_failed_deploy <backup_dir> <service> <health_url> [attempts sleep max_gen]
# 배포 실패 후 호출: env 복원 → (복원된 파일이 있을 때만) 서비스 재시작 → 헬스 확인 순서.
#   0  env 변경 없음 또는 복원·재시작·헬스 모두 성공
#   2  env 복원 실패 또는 복원 후 헬스 실패 — 사람 개입 필요
# Wiki identity env가 복원되면 buildit-wiki 만 재시작한다(실패해도 Spring 판정에는 영향 없음).
f2r_env_rollback_after_failed_deploy() {
  local dir="$1" service="$2" url="$3"
  local attempts="${4:-$F2R_HEALTH_DEFAULT_ATTEMPTS}" sleep_secs="${5:-$F2R_HEALTH_DEFAULT_SLEEP_SECS}"
  local max_generations="${6:-$F2R_MAX_PID_GENERATIONS}"
  local spring=0 wiki=0 f

  if ! f2r_env_restore_changed "$dir"; then
    echo "::error::ENV RESTORE FAILED — env files may be in a mixed state. Manual recovery from ${dir} required."
    return 2
  fi
  if [ "${#F2R_ENV_RESTORED_FILES[@]}" -eq 0 ]; then
    echo "env files unchanged by this deploy — no env restore needed"
    return 0
  fi
  for f in "${F2R_ENV_RESTORED_FILES[@]}"; do
    case "$f" in
      /etc/f2r/env) spring=1 ;;
      /etc/buildit-wiki-identity.env) wiki=1 ;;
    esac
  done
  if [ "$wiki" -eq 1 ]; then
    f2r__service_restart buildit-wiki.service || echo "::warning::buildit-wiki restart after env restore failed (non-fatal)"
  fi
  if [ "$spring" -eq 1 ]; then
    echo "Restarting ${service} with the restored env..."
    f2r__service_restart "$service"
    if ! f2r_wait_for_health_pid_aware "$service" "$url" "$attempts" "$sleep_secs" "$max_generations"; then
      echo "::error::${service} unhealthy even after env restore — Production may be down. Manual EC2 recovery required."
      f2r_dump_diagnostics "$service"
      return 2
    fi
    echo "${service} healthy after env restore."
  fi
  return 0
}

# 성공한 배포 뒤 오래된 env 백업 디렉터리를 정리한다(비밀 사본이 쌓이지 않게). 최신 N개 유지.
f2r_env_prune_backups() {
  local root="$1" keep="${2:-$F2R_ENV_BACKUP_KEEP}" d
  [ -d "$root" ] || return 0
  find "$root" -mindepth 1 -maxdepth 1 -type d | sort | head -n "-${keep}" | while IFS= read -r d; do
    rm -rf -- "$d"
  done
  return 0
}

# ── 서비스 공백 측정(읽기 전용) ───────────────────────────────────────────────
# 배포 한 번에서 "종료 신호 → 새 프로세스가 포트를 열 때까지"가 실제로 몇 초였는지 journald에서
# 계산해 [DEPLOY] 로그로 남긴다. 판정(성공/실패)·재시작·롤백에는 어떤 영향도 주지 않는다 —
# 호출 지점은 health gate 통과 이후이며, 이 함수는 항상 0을 반환한다.
#
# 측정 기준: 마지막 "Stopping <unit>" 줄 시각 → 그 뒤 첫 "Tomcat started on port" 줄 시각.
# Stopping은 graceful 대기 시작 시점이므로 종료 구간(진행 중 요청 대기)도 포함한 상한값이다.
# 롤백이 일어났다면 마지막 재시작 기준이다.
f2r__journal_unix() {
  journalctl -u "$1" --since "${F2R_GAP_JOURNAL_WINDOW:-1 hour ago}" -o short-unix --no-pager 2>/dev/null
}

# stdout: 정수 초. 계산할 수 없으면(줄이 없음/순서 어긋남) 아무것도 출력하지 않고 1 반환.
f2r_service_gap_seconds() {
  local unit="$1"
  f2r__journal_unix "$unit" | awk -v u="$unit" '
    index($0, "Stopping " u) { stop = $1; up = "" }
    stop != "" && up == "" && index($0, "Tomcat started on port") { up = $1 }
    END { if (stop != "" && up != "" && up + 0 >= stop + 0) { printf "%.0f\n", up - stop } else { exit 1 } }
  '
}

f2r_report_service_gap() {
  local unit="$1" secs
  if secs=$(f2r_service_gap_seconds "$unit") && [ -n "$secs" ]; then
    echo "[DEPLOY] service_gap_seconds=${secs} (journald: Stopping ${unit} -> Tomcat started)"
  else
    echo "[DEPLOY] service_gap_seconds=unavailable (journald에서 Stopping/Tomcat started 줄을 찾지 못함)"
  fi
  return 0
}
