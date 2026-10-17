#!/usr/bin/env bash
# 배포 중 메모리·OOM 감시(읽기 전용 SSM). 배포 워크플로와 별도 concurrency 그룹에서 병행 실행한다.
# 인자: 지속분(DURATION_MIN) ARM_CANCEL(true|false) MODE(dry|live)
set -u
DURATION_MIN="${1:-2}"; ARM_CANCEL="${2:-false}"; MODE="${3:-dry}"
INST=i-09d7cabb5a42d1135
BASE_JAR_MTIME=1792160225; BASE_JAR_SIZE=215609526   # 배포 전 app.jar (a6c409cc…)
MEM_MIN=300; SWAP_JUMP=300
PROBE='A=$(awk "/MemAvailable/{print int(\$2/1024)}" /proc/meminfo); S=$(awk "/SwapTotal/{t=\$2}/SwapFree/{f=\$2}END{print int((t-f)/1024)}" /proc/meminfo); O=$(journalctl -k --since "-2 min" --no-pager 2>/dev/null | grep -ci "out of memory\|oom-kill"); C=$(pgrep -fc designcraft-cli); P=$(pgrep -fc "pip install"); J=$(stat -c "%Y %s" /home/ec2-user/app.jar); echo "MEM=$A SWAP=$S OOM=$O CLI=$C PIP=$P JAR=$J APP=$(systemctl is-active f2r-app) WIKI=$(systemctl is-active buildit-wiki) GATE=$(cat /var/lib/feel2real/designcraft-maintenance/state)"'
PARAMS=$(mktemp); python3 -c 'import json,sys; print(json.dumps({"commands":[sys.argv[1]]}))' "$PROBE" > "$PARAMS"
sample() {
  local cid st i
  cid=$(aws ssm send-command --instance-ids "$INST" --document-name AWS-RunShellScript --parameters "file://$PARAMS" --timeout-seconds 30 --query Command.CommandId --output text 2>/dev/null) || { echo "SEND_FAIL"; return; }
  for i in 1 2 3 4 5 6 7 8; do
    st=$(aws ssm get-command-invocation --command-id "$cid" --instance-id "$INST" --query Status --output text 2>/dev/null || true)
    case "$st" in Success) aws ssm get-command-invocation --command-id "$cid" --instance-id "$INST" --query StandardOutputContent --output text | tr -d '\n'; echo " CID=$cid"; return;; Failed|Cancelled|TimedOut) echo "PROBE_$st"; return;; esac
    sleep 2
  done
  echo "PROBE_TIMEOUT"
}
cancel_deploy() {
  echo "!! STOP CRITERIA MET: cancelling in-progress deploy SSM command(s) (safe point: app.jar not yet replaced)"
  for c in $(aws ssm list-commands --instance-id "$INST" --filters key=Status,value=InProgress --query "Commands[?DocumentName=='AWS-RunShellScript' && contains(to_string(Parameters.commands), 'deploy-buildit.sh')].CommandId" --output text); do
    aws ssm cancel-command --command-id "$c" --instance-ids "$INST" && echo "cancel requested: $c"
  done
}
echo "== permission dry check (no side effects): cancel/list with a bogus id"
aws ssm cancel-command --command-id 00000000-0000-0000-0000-000000000000 --instance-ids "$INST" 2>&1 | head -2
aws ssm list-commands --instance-id "$INST" --filters key=Status,value=InProgress --query 'length(Commands)' --output text 2>&1 | head -2
echo "== monitoring ${DURATION_MIN} min, mode=$MODE, arm_cancel=$ARM_CANCEL, thresholds: MemAvailable<${MEM_MIN} x2, any OOM, swap +${SWAP_JUMP} MiB"
end=$(( $(date +%s) + DURATION_MIN*60 )); low=0; base_swap=""
while [ "$(date +%s)" -lt "$end" ]; do
  line=$(sample); echo "$(date -u +%T) $line"
  mem=$(sed -n 's/.*MEM=\([0-9]*\).*/\1/p' <<<"$line"); swap=$(sed -n 's/.*SWAP=\([0-9]*\).*/\1/p' <<<"$line"); oom=$(sed -n 's/.*OOM=\([0-9]*\).*/\1/p' <<<"$line")
  jar=$(sed -n 's/.*JAR=\([0-9]* [0-9]*\).*/\1/p' <<<"$line")
  if [ -n "$mem" ]; then
    [ -z "$base_swap" ] && base_swap=$swap
    if [ "$mem" -lt "$MEM_MIN" ]; then low=$((low+1)); else low=0; fi
    trig=0; [ "$low" -ge 2 ] && trig=1; [ "${oom:-0}" -gt 0 ] && trig=1; [ $((swap-base_swap)) -gt "$SWAP_JUMP" ] && trig=1
    if [ "$trig" = 1 ]; then
      if [ "$jar" = "$BASE_JAR_MTIME $BASE_JAR_SIZE" ]; then
        echo "!! TRIGGER (jar unchanged => safe to stop)"; [ "$ARM_CANCEL" = true ] && [ "$MODE" = live ] && cancel_deploy || echo "(cancel not armed)"
      else
        echo "!! TRIGGER after app.jar replacement: no mid-flight cancel; rely on auto-rollback, then manual rollback decision"
      fi
    fi
  fi
  sleep 6
done
echo "== monitor finished"
