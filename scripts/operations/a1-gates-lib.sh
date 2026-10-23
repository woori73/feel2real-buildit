#!/usr/bin/env bash
# A1(블루·그린) 배포 사전 게이트 판정 함수. 조회 결과(숫자)만 받아 PASS/FAIL을 돌려준다. 부작용 없음.
# a1-precheck.sh 와 이후 배포 스크립트가 같은 기준을 쓰도록 한 곳에 둔다.

A1_MIN_AVAILABLE_MB=${A1_MIN_AVAILABLE_MB:-2500}   # 두 번째 JVM 기동 전 최소 가용 메모리
A1_MAX_DB_CONN_AFTER=${A1_MAX_DB_CONN_AFTER:-40}   # 중복 구간 포함 예상 DB 연결 상한(RDS max 60의 2/3)

# a1_mem_gate <available_mb> [min_mb] → "PASS|FAIL available=<n> min=<m>" 출력, FAIL이면 종료코드 1
a1_mem_gate() {
  local avail="$1" min="${2:-$A1_MIN_AVAILABLE_MB}"
  [[ "$avail" =~ ^[0-9]+$ && "$min" =~ ^[0-9]+$ ]] || { echo "FAIL invalid-input available=$avail min=$min"; return 1; }
  if [ "$avail" -ge "$min" ]; then echo "PASS available=$avail min=$min"; else echo "FAIL available=$avail min=$min"; return 1; fi
}

# a1_conn_gate <connected_now> <pool_max_per_app> [limit] → 중복 구간 예상 연결(현재 + 앱 풀 최대)이 limit 이하인지
a1_conn_gate() {
  local now="$1" pool="$2" limit="${3:-$A1_MAX_DB_CONN_AFTER}"
  [[ "$now" =~ ^[0-9]+$ && "$pool" =~ ^[0-9]+$ && "$limit" =~ ^[0-9]+$ ]] || { echo "FAIL invalid-input"; return 1; }
  local est=$((now + pool))
  if [ "$est" -le "$limit" ]; then echo "PASS estimated=$est limit=$limit"; else echo "FAIL estimated=$est limit=$limit"; return 1; fi
}
