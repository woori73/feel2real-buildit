#!/usr/bin/env bash
# 신규 AWS 계정(425805163960) ALB·CloudWatch 읽기 전용 진단용 보조 함수.
# migration-infra-build.yml step=alb-diag 에서 source 한다. 조회(describe/get/list)만 수행한다.

# 진단 구간(UTC ISO8601 'YYYY-MM-DDTHH:MM:SSZ')을 검증한다. 형식 오류·역전·24시간 초과는 실패.
diag_validate_window() {
  local s="$1" e="$2" re='^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$'
  [[ "$s" =~ $re && "$e" =~ $re ]] || { echo "invalid-window-format" >&2; return 1; }
  local se ee
  se=$(date -u -d "$s" +%s 2>/dev/null) || { echo "invalid-start" >&2; return 1; }
  ee=$(date -u -d "$e" +%s 2>/dev/null) || { echo "invalid-end" >&2; return 1; }
  [ "$ee" -gt "$se" ] || { echo "window-not-increasing" >&2; return 1; }
  [ $((ee - se)) -le 86400 ] || { echo "window-over-24h" >&2; return 1; }
}

# 'YYYY-MM-DDTHH:MM:SSZ' → journalctl 용 'YYYY-MM-DD HH:MM:SS UTC'
diag_to_journal_time() {
  local t="${1/T/ }"
  printf '%s UTC' "${t%Z}"
}

# 입력: "<timestamp>\t<value>" 줄들(합계형 지표). 출력: 건수·0 초과 건수·합·최댓값·0 초과 시각(최대 20개)
diag_summ_sum() {
  awk -F'\t' 'NF>=2 && $2!="None" { n++; if ($2+0>0) { nz++; sum+=$2; if ($2+0>mx) mx=$2+0; if (nz<=20) l=l "\n    " $1 "  " $2 } }
    END { printf "  points=%d nonzero=%d sum=%s max=%s%s\n", n, nz, sum+0, mx+0, l }'
}

# 입력: "<timestamp>\t<value>" 줄들(평균·최소·최대형 지표). 출력: 건수·최솟값·평균·최댓값(발생 시각)
diag_summ_avg() {
  awk -F'\t' 'NF>=2 && $2!="None" { n++; v=$2+0; s+=v; if (n==1||v<mn) mn=v; if (n==1||v>mx) { mx=v; mt=$1 } }
    END { if (n==0) { print "  points=0"; exit } printf "  points=%d min=%s avg=%.4f max=%s at %s\n", n, mn, s/n, mx, mt }'
}
