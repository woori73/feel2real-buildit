#!/usr/bin/env bash
# admissions-diag.sh 정적 안전 검사: 쓰기 SQL이 없고 읽기 전용 트랜잭션을 쓰며 구 계정을 참조하지 않는다.
set -euo pipefail
S="$(dirname "$0")/admissions-diag.sh"
fail=0
check() { if ! eval "$2"; then echo "FAIL: $1"; fail=1; else echo "ok: $1"; fi; }
# given: 진단 스크립트 / when: SQL 영역을 검사 / then: 쓰기 키워드 없음
SQL_ONLY=$(sed -n '/^SQL=r"""/,/^"""/p' "$S")
check "문법 오류 없음(bash -n)" 'bash -n "$S"'
check "쓰기 SQL 없음" '! printf "%s" "$SQL_ONLY" | grep -Eiq "\b(insert|update|delete|replace|drop|alter|truncate|create|grant|load data|into outfile)\b"'
check "READ ONLY 트랜잭션 사용" 'printf "%s" "$SQL_ONLY" | grep -q "START TRANSACTION READ ONLY"'
check "세션 READ ONLY 지정" 'grep -q "SET SESSION TRANSACTION READ ONLY" "$S"'
check "구 AWS 계정 ID 없음" '! grep -q "067573916809" "$S"'
check "비밀값 출력 없음(password 출력 금지)" '! grep -Eq "print\(.*password|echo .*PASSWORD" "$S"'
# given: 관리자 화면 시드 적재 실패 / when: 앱 로그 구간을 검사 / then: 적재 성공·실패 줄(admissions seed)을 포함
check "시드 적재 로그 포함" 'grep -q "index/reload|admissions seed" "$S"'
# given: SSM 출력은 앞부분만 남는다 / when: 구간 순서를 검사 / then: 로그 구간이 긴 DB 집계보다 먼저 나온다
check "로그 구간이 DB 집계보다 앞" '[ "$(grep -n "seed log" "$S" | head -1 | cut -d: -f1)" -lt "$(grep -n "DB census" "$S" | head -1 | cut -d: -f1)" ]'
exit $fail
