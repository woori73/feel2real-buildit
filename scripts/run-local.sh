#!/usr/bin/env bash
#
# 로컬 개발용 Spring Boot 실행 스크립트.
#
# 목표:
#   git clone 후 ./scripts/run-local.sh 한 번으로 로컬 MySQL을 자동 생성/재사용하고
#   local profile로 Spring Boot를 실행한다.
#
# 운영(prd) 영향 없음:
#   이 스크립트는 SPRING_PROFILES_ACTIVE=local을 강제하고 localhost:3308/f2r_db만 사용한다.
#   prd는 EC2 systemd EnvironmentFile(/etc/f2r/env)의 SPRING_PROFILES_ACTIVE=prd와 DB_* 값으로 별도 실행된다.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${HOME}/.config/f2r/env"
COMPOSE_FILE="${ROOT}/docker-compose.main.yaml"
DB_CONTAINER="f2r_mysql_main"
DB_PORT="3308"
DB_NAME="f2r_db"
DB_USER="f2r_user"
DB_PASSWORD="f2r_password"
DB_ROOT_PASSWORD="f2r_root_password"

load_optional_env() {
  if [[ -f "${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
  fi
}

require_command() {
  local command_name="$1"
  local install_hint="$2"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "ERROR: ${command_name} 명령을 찾을 수 없습니다." >&2
    echo "       ${install_hint}" >&2
    exit 1
  fi
}

compose() {
  MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-${DB_ROOT_PASSWORD}}" \
  MYSQL_DATABASE="${MYSQL_DATABASE:-${DB_NAME}}" \
  MYSQL_USER="${MYSQL_USER:-${DB_USER}}" \
  MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD}}" \
    docker compose -f "${COMPOSE_FILE}" "$@"
}

wait_for_mysql() {
  local attempts=0
  local status

  while [[ ${attempts} -lt 90 ]]; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${DB_CONTAINER}" 2>/dev/null || true)"
    if [[ "${status}" == "healthy" ]]; then
      return 0
    fi
    if [[ "${status}" == "unhealthy" ]]; then
      echo "ERROR: ${DB_CONTAINER} healthcheck가 unhealthy입니다. docker logs ${DB_CONTAINER}로 확인하세요." >&2
      return 1
    fi
    sleep 1
    attempts=$((attempts + 1))
  done

  echo "ERROR: ${DB_CONTAINER}가 90초 안에 healthy 상태가 되지 않았습니다." >&2
  echo "       docker logs ${DB_CONTAINER}로 원인을 확인하세요." >&2
  return 1
}

start_local_db() {
  require_command docker "Docker Desktop을 설치하고 실행한 뒤 다시 시도하세요."
  if ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker daemon에 접근할 수 없습니다. Docker Desktop이 실행 중인지 확인하세요." >&2
    exit 1
  fi

  echo "[local-db] MySQL 컨테이너 준비: localhost:${DB_PORT}/${DB_NAME}"
  compose up -d
  wait_for_mysql
}

load_optional_env
start_local_db

# local 실행은 항상 로컬 DB만 바라보게 고정한다.
export SPRING_PROFILES_ACTIVE="local"
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci"
export SPRING_DATASOURCE_USERNAME="${MYSQL_USER:-${DB_USER}}"
export SPRING_DATASOURCE_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD}}"

# anthropic.enabled 기본값은 false라 API key가 없어도 부팅 가능하다.
# 키가 있으면 향후 local에서 기능을 켰을 때 사용할 수 있게 그대로 전달한다.
if [[ -n "${ANTHROPIC_API_KEY:-}" ]]; then
  export ANTHROPIC_API_KEY
fi

cd "${ROOT}"

echo "[bootRun] profile=${SPRING_PROFILES_ACTIVE}, db=localhost:${DB_PORT}/${DB_NAME}"
exec ./gradlew bootRun "$@"
