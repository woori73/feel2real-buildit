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
F2R_HEALTH_DEFAULT_ATTEMPTS=24
F2R_HEALTH_DEFAULT_SLEEP_SECS=5

# 내부에서 실제 systemctl/journalctl 을 부르는 지점을 한 곳으로 모아 둔다 — 테스트가
# 이 세 함수만 오버라이드하면 나머지 로직(재시도 횟수, 롤백 판단)은 실제 코드 그대로 검증된다.
f2r__service_restart() { systemctl restart "$1"; }
f2r__service_status_dump() { systemctl status "$1" --no-pager -l || true; }
f2r__service_journal_dump() { journalctl -u "$1" -n 200 --no-pager || true; }

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

# 실패 시 EC2에 다시 접속하지 않아도 GitHub Actions 로그에서 원인을 볼 수 있게 진단 정보를
# 남긴다. secret이 담길 수 있는 EnvironmentFile 내용 자체는 절대 출력하지 않는다 —
# systemctl status/journalctl 표준 출력만 남긴다.
f2r_dump_diagnostics() {
  local service_name="$1"
  echo "----- systemctl status ${service_name} -----"
  f2r__service_status_dump "$service_name"
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
#   attempts/sleep_secs  health 재시도 파라미터 (생략 시 기본값)
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

  if f2r_wait_for_health "$health_url" "$attempts" "$sleep_secs"; then
    echo "${service_name} is healthy on the new version."
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

  if f2r_wait_for_health "$health_url" "$attempts" "$sleep_secs"; then
    echo "::error::Deploy FAILED — automatically rolled back to the previous version, which is healthy. Production is restored."
    return 1
  fi

  echo "::error::ROLLBACK ALSO FAILED — previous version is also unhealthy after restart. Production may be down. Manual EC2 recovery required."
  f2r_dump_diagnostics "$service_name"
  return 2
}
