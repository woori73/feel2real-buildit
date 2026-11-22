#!/usr/bin/env bash
# 입시상담 학습 한 번에 돌리기(GPU 서버에서). 순서: 데이터 → 학습 전 평가 → 시험 학습(200쌍) → 평가 → (좋아지면) 본 학습 → 평가.
#
#   bash training/admissions/run_gpu.sh trial   # 학습 전 + 시험 학습까지 (RTX A5000 기준 약 1~1.5시간)
#   bash training/admissions/run_gpu.sh main    # 본 학습(1,104쌍, 2회 반복) + 평가 (약 1.5~2시간)
#
# 준비: 이 저장소와 buildit-admissions(승인 입시자료, 개인정보 없음)를 같은 폴더에 받는다.
#   pip install -r training/requirements.txt
# 끝나면 training/outputs/admissions/ 전체(어댑터·예측·점수)를 내려받고 서버를 삭제한다.
set -euo pipefail
MODE="${1:-trial}"
MODEL="Qwen/Qwen2.5-7B-Instruct"
REV="${F2R_BASE_REV:-a09a35458c702b33eeacc393d103063234e8bc28}"   # 2026-11-22 확인한 Hugging Face commit (Apache-2.0)
SEED_DIR="${F2R_ADMISSIONS_SEED:-../buildit-admissions/data/seed}"
DATA=training/data/admissions
OUT=training/outputs/admissions
CONFIG=training/admissions/qlora_config.json
mkdir -p "$OUT"
nvidia-smi --query-gpu=name,memory.total --format=csv | tee "$OUT/gpu_${MODE}.txt"
date -u +"start %Y-%m-%dT%H:%M:%SZ" | tee -a "$OUT/gpu_${MODE}.txt"

python3 -m training.admissions.build_dataset --seed-dir "$SEED_DIR" --out "$DATA"
python3 -m unittest discover -s training/tests

score() {  # $1 예측 파일, $2 모델 이름
  python3 -m training.admissions.counsel_eval --gold "$DATA/eval.jsonl" --predictions "$1" \
    --output "${1%.jsonl}.score.json" --model-version "$2"
}

if [ "$MODE" = trial ]; then
  # 모델 받기는 이 한 번만(이후 단계는 오프라인).
  python3 -c "from huggingface_hub import snapshot_download as s; s('$MODEL', revision='$REV')"
  [ -f "$OUT/pred_base.jsonl" ] || python3 -m training.admissions.generate_hf --input "$DATA/eval.jsonl" \
    --output "$OUT/pred_base.jsonl" --model "$MODEL" --revision "$REV"
  score "$OUT/pred_base.jsonl" "$MODEL@${REV:0:8} (학습 전)"
  python3 -m training.qlora.train --train "$DATA/train_trial.jsonl" --eval "$DATA/eval.jsonl" --cutoff 2026-11-22 \
    --revision "$REV" --output "$OUT/trial" --config "$CONFIG"
  python3 -m training.admissions.generate_hf --input "$DATA/eval.jsonl" --output "$OUT/pred_trial.jsonl" \
    --model "$MODEL" --revision "$REV" --adapter "$OUT/trial/adapter"
  score "$OUT/pred_trial.jsonl" "$MODEL@${REV:0:8} + QLoRA 시험(200쌍)"
elif [ "$MODE" = main ]; then
  python3 -m training.qlora.train --train "$DATA/train.jsonl" --eval "$DATA/eval.jsonl" --cutoff 2026-11-22 \
    --revision "$REV" --output "$OUT/main" --config "$CONFIG"
  python3 -m training.admissions.generate_hf --input "$DATA/eval.jsonl" --output "$OUT/pred_main.jsonl" \
    --model "$MODEL" --revision "$REV" --adapter "$OUT/main/adapter"
  score "$OUT/pred_main.jsonl" "$MODEL@${REV:0:8} + QLoRA 본(1,104쌍)"
else
  echo "usage: run_gpu.sh trial|main" >&2; exit 2
fi
date -u +"end %Y-%m-%dT%H:%M:%SZ" | tee -a "$OUT/gpu_${MODE}.txt"
