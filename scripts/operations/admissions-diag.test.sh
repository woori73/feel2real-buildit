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
exit $fail
