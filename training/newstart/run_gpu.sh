#!/usr/bin/env bash
set -euo pipefail
mode=${1:-}
case "$mode" in trial|main) ;; *) echo "usage: run_gpu.sh trial|main" >&2; exit 2;; esac
gold=${NEWSTART_EVAL:?set path to the reviewed evaluation JSONL}
input=${NEWSTART_APPROVED:?set path to the training JSONL for this mode}
config=training/admissions/qlora_config.json
model=Qwen/Qwen2.5-7B-Instruct
revision=a09a35458c702b33eeacc393d103063234e8bc28
# Isolate runs when the dataset or training configuration changes.
tokens=${NEWSTART_MAX_NEW_TOKENS:-768}
[[ "$tokens" =~ ^[1-9][0-9]*$ ]] || { echo "token limit must be positive" >&2; exit 2; }
shared_key=$({ sha256sum "$gold" "$config" | cut -d' ' -f1; echo "$tokens"; } | sha256sum | cut -d' ' -f1)
root="${NEWSTART_OUTPUT:-training/outputs/newstart}/$revision/$shared_key"
mkdir -p "$root"
exec 9>"$root/.lock"
flock 9
work=$(mktemp -d "$root/.work.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
python3 -m training.newstart.build_dataset --mode "$mode" --eval "$gold" --train "$input" \
    --output "$work/train.jsonl" --review-reference "${NEWSTART_REVIEW_REFERENCE:-}"
train_key=$(sha256sum "$work/train.jsonl" | cut -d' ' -f1)
stage="$root/$mode-$train_key"
mkdir -p "$stage"
predict_score() {
    local dest=$1 name=$2
    shift 2
    if [[ ! -f "$dest/predictions.jsonl" ]]; then
        python3 -m training.admissions.generate_hf --input "$gold" --output "$work/predictions.jsonl" \
            --model "$model" --revision "$revision" --batch-size 4 --max-new-tokens "$tokens" "$@"
        mv "$work/predictions.jsonl" "$dest/predictions.jsonl"
    fi
    python3 -m training.newstart.counsel_eval --gold "$gold" --predictions "$dest/predictions.jsonl" \
        --model-version "$model@$revision/$name" --output "$work/score.json"
    mv "$work/score.json" "$dest/score.json"
}
if [[ "$mode" == trial ]]; then
    python3 -c 'from huggingface_hub import snapshot_download; import sys; snapshot_download(sys.argv[1], revision=sys.argv[2])' "$model" "$revision"
    mkdir -p "$root/base"
    predict_score "$root/base" base
elif [[ ! -s "$root/trial.completed" ]]; then
    echo "Run trial and inspect its result on this evaluation set before main." >&2
    exit 2
fi
if [[ ! -f "$stage/model/adapter/run_manifest.json" ]]; then
    python3 -m training.qlora.train --train "$work/train.jsonl" --eval "$gold" --cutoff 2026-11-22 \
        --revision "$revision" --output "$work/model" --config "$config"
    mv "$work/model" "$stage/model"
fi
predict_score "$stage" "$mode" --adapter "$stage/model/adapter"
if [[ "$mode" == trial ]]; then
    printf '%s\n' "$stage/score.json" > "$work/trial.completed"
    mv "$work/trial.completed" "$root/trial.completed"
fi
echo "$stage/score.json"
