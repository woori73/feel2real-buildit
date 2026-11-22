#!/usr/bin/env bash
set -euo pipefail
mode="${1:-}"
case "$mode" in trial|main) ;; *) echo "usage: run_gpu.sh trial|main" >&2; exit 2;; esac
data=training/data/newstart
out=training/outputs/newstart
model=Qwen/Qwen2.5-7B-Instruct
revision=a09a35458c702b33eeacc393d103063234e8bc28
python3 -m training.newstart.build_dataset --approved "${NEWSTART_APPROVED:?set path to reviewed training JSONL}"
mkdir -p "$out"
predict_score() {
  local stage="$1"
  shift
  python3 -m training.admissions.generate_hf --input "$data/eval.jsonl" --output "$out/$stage.jsonl" --model "$model" --revision "$revision" "$@"
  python3 -m training.newstart.counsel_eval --gold "$data/eval.jsonl" --predictions "$out/$stage.jsonl" --model-version "$model@$revision/$stage" --output "$out/$stage.score.json"
}
if [[ "$mode" == trial ]]; then
  python3 -c 'from huggingface_hub import snapshot_download; import sys; snapshot_download(sys.argv[1],revision=sys.argv[2])' "$model" "$revision"
  predict_score base
  train="$data/train_trial.jsonl"
else
  test -s "$out/trial.score.json"
  python3 -c 'import json,sys; from training.common import fingerprint; assert json.load(open(sys.argv[1]))["gold_sha256"]==fingerprint(sys.argv[2])' "$out/trial.score.json" "$data/eval.jsonl"
  train="$data/train.jsonl"
fi
python3 -m training.qlora.train --train "$train" --eval "$data/eval.jsonl" --cutoff 2026-11-22 --revision "$revision" --output "$out/$mode" --config training/admissions/qlora_config.json
predict_score "$mode" --adapter "$out/$mode/adapter"
