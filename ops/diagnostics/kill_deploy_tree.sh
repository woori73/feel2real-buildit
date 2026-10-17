#!/usr/bin/env bash
# 배포 작업에 속한 프로세스(배포 스크립트와 그 자손)만 종료한다. 운영 중인 Spring·Wiki·CLI·SSM 에이전트는 절대 대상이 아니다.
# 인자: $1=배포 스크립트 경로(/tmp/deploy-buildit.sh) $2="<app.jar mtime> <size>" 기준값 또는 SKIP(시험용)
set -u
SCRIPT="$1"; BASE="$2"
APP_PID=$(systemctl show f2r-app.service -p MainPID --value); WIKI_PID=$(systemctl show buildit-wiki.service -p MainPID --value)
if [ "$BASE" != SKIP ]; then
  J=$(stat -c '%Y %s' /home/ec2-user/app.jar)
  [ "$J" = "$BASE" ] || { echo JAR_ALREADY_REPLACED_NO_STOP; exit 0; }
fi
esc=$(printf '%s' "$SCRIPT" | sed 's/[.[\*^$/]/\\&/g')
PIDS=$(pgrep -f "^(/usr/bin/env )?(/usr/bin/|/bin/)?bash ${esc}\$")
[ -z "$PIDS" ] && { echo NO_DEPLOY_PROCESS; exit 0; }
protected() {
  case "$(tr '\0' ' ' < /proc/$1/cmdline 2>/dev/null)" in *java*|*uvicorn*|*designcraft-cli*|*amazon-ssm-agent*|*sshd*|*systemd*) return 0;; esac
  [ "$1" = "$APP_PID" ] || [ "$1" = "$WIKI_PID" ] || [ "$1" = 1 ]
}
kill_tree() {
  local p=$1 c
  protected "$p" && { echo "SKIP protected pid $p"; return; }
  for c in $(pgrep -P "$p"); do kill_tree "$c"; done
  kill -TERM "$p" 2>/dev/null && echo "TERM $p $(tr '\0' ' ' < /proc/$p/cmdline 2>/dev/null | cut -c1-80)"
}
for P in $PIDS; do kill_tree "$P"; done
echo STOPPED
