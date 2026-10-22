#!/bin/bash
# 앱 보호 guard. 임베딩 서버가 앱 메모리를 잠식하거나 앱이 계속 죽어 있으면 임베딩부터 중지한다(재시작은 사람이 한다).
# - 여유 메모리가 임계값 미만이면 즉시 중지.
# - 앱이 내려가 있어도 배포 같은 계획된 재시작(수십 초)은 견딘다: 연속 LIMIT회(30초 간격 → 약 2분) 내려가 있을 때만 중지.

guard_decide() { # AVAIL_MB MIN_MB APP_ACTIVE(1|0) COUNT_IN LIMIT → "ACTION NEW_COUNT"
  local avail=$1 min=$2 app=$3 count=$4 limit=$5
  if [ "$avail" -lt "$min" ]; then echo "stop-memory 0"; return; fi
  if [ "$app" = 1 ]; then echo "keep 0"; return; fi
  count=$((count + 1))
  if [ "$count" -ge "$limit" ]; then echo "stop-app-down $count"; else echo "keep $count"; fi
}

guard_main() {
  . /opt/f2r-embed/guard.conf
  local limit=${APP_DOWN_LIMIT:-4} cf=/run/f2r-embed-guard.count
  systemctl is-active --quiet f2r-embed || { rm -f "$cf"; return 0; }
  local avail app=0 count=0
  avail=$(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo)
  systemctl is-active --quiet f2r-app && app=1
  [ -f "$cf" ] && count=$(cat "$cf" 2>/dev/null || echo 0)
  local out action
  out=$(guard_decide "$avail" "$MIN_AVAIL_MB" "$app" "$count" "$limit")
  action=${out% *}; echo "${out#* }" > "$cf"
  case "$action" in
    stop-*) systemctl stop f2r-embed; rm -f "$cf"; logger -t f2r-embed-guard "stopped embedding: $action MemAvailable=${avail}MB" ;;
  esac
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then guard_main; fi
