#!/usr/bin/env bash
# 입시상담 학습 한 번에 돌리기(GPU 서버에서). 순서: 데이터 → 학습 전 평가 → 시험 학습(200쌍) → 평가 → (좋아지면) 본 학습 → 평가.
#
#   bash training/admissions/run_gpu.sh trial   # 학습 전 + 시험 학습까지 (RTX A5000 기준 약 1~1.5시간)
#   bash training/admissions/run_gpu.sh main    # 본 학습(804쌍, 2회 반복) + 평가 (약 1.5~2시간)
#
# 준비: 이 저장소와 buildit-admissions(승인 입시자료, 개인정보 없음)를 같은 폴더에 받는다.
#   pip install -r training/requirements.txt
# 끝나면 training/outputs/admissions_v2/ 전체(어댑터·예측·점수)를 내려받고 서버를 삭제한다.
set -euo pipefail
MODE="${1:-trial}"
MODEL="Qwen/Qwen2.5-7B-Instruct"
REV="${F2R_BASE_REV:-a09a35458c702b33eeacc393d103063234e8bc28}"   # 2026-11-22 확인한 Hugging Face commit (Apache-2.0)
SEED_DIR="${F2R_ADMISSIONS_SEED:-../buildit-admissions/data/seed}"
DATA=training/data/admissions
OUT="${F2R_OUT:-training/outputs/admissions_v2}"   # v2: 2028 중심 평가(eval_2028) 추가·학습 데이터 2028 비중 확대(2026-11-22)
CONFIG=training/admissions/qlora_config.json
mkdir -p "$OUT"
nvidia-smi --query-gpu=name,memory.total --format=csv | tee "$OUT/gpu_${MODE}.txt"
date -u +"start %Y-%m-%dT%H:%M:%SZ" | tee -a "$OUT/gpu_${MODE}.txt"

python3 -m training.admissions.build_dataset --seed-dir "$SEED_DIR" --out "$DATA"
python3 -m unittest discover -s training/tests

# 다시 돌릴 때는 이미 끝난 단계(어댑터 폴더·예측 파일이 있는 단계)를 건너뛴다. 다시 만들려면 그 파일·폴더를 지우고 돌린다.
train() {  # $1 학습 데이터, $2 출력 폴더
  if [ -d "$2/adapter" ]; then echo "skip train: $2/adapter 있음"; return; fi
  rm -rf "$2"   # 중간에 멈춘 폴더는 지우고 다시 학습
  python3 -m training.qlora.train --train "$1" --eval "$DATA/eval.jsonl" --cutoff 2026-11-22 \
    --revision "$REV" --output "$2" --config "$CONFIG"
}

generate() {  # $1 예측 파일, $2 어댑터 폴더(없으면 베이스), $3 평가 파일(기본 eval.jsonl)
  local gold="${3:-$DATA/eval.jsonl}"
  if [ -s "$1" ] && [ "$(wc -l < "$1")" -ge "$(wc -l < "$gold")" ]; then echo "skip generate: $1 있음"; return; fi
  rm -f "$1"   # 중간에 멈춘 예측 파일은 지우고 다시 생성
  python3 -m training.admissions.generate_hf --input "$gold" --output "$1" \
    --model "$MODEL" --revision "$REV" ${2:+--adapter "$2"}
}

score() {  # $1 예측 파일, $2 모델 이름, $3 평가 파일(기본 eval.jsonl)
  python3 -m training.admissions.counsel_eval --gold "${3:-$DATA/eval.jsonl}" --predictions "$1" \
    --output "${1%.jsonl}.score.json" --model-version "$2" --show-failures reform
}

both() {  # $1 이름(base·trial·main), $2 어댑터 폴더(없으면 빈 값), $3 모델 이름 — 기존 171문항과 2028 중심 67문항을 함께 잰다
  generate "$OUT/pred_$1.jsonl" "$2"
  score "$OUT/pred_$1.jsonl" "$3"
  generate "$OUT/pred_$1_2028.jsonl" "$2" "$DATA/eval_2028.jsonl"
  score "$OUT/pred_$1_2028.jsonl" "$3 / 2028 중심" "$DATA/eval_2028.jsonl"
}

if [ "$MODE" = trial ]; then
  # 모델 받기는 이 한 번만(이후 단계는 오프라인).
  python3 -c "from huggingface_hub import snapshot_download as s; s('$MODEL', revision='$REV')"
  both base "" "$MODEL@${REV:0:8} (학습 전)"
  train "$DATA/train_trial.jsonl" "$OUT/trial"
  both trial "$OUT/trial/adapter" "$MODEL@${REV:0:8} + QLoRA 시험(200쌍)"
elif [ "$MODE" = main ]; then
  train "$DATA/train.jsonl" "$OUT/main"
  both main "$OUT/main/adapter" "$MODEL@${REV:0:8} + QLoRA 본(학습 데이터 전체)"
else
  echo "usage: run_gpu.sh trial|main" >&2; exit 2
fi
date -u +"end %Y-%m-%dT%H:%M:%SZ" | tee -a "$OUT/gpu_${MODE}.txt"
