"""입시상담 평가 답변 생성(GPU). 학습 전 베이스 모델과 학습 후(베이스 + LoRA 어댑터)를 같은 조건으로 돌린다.

python -m training.admissions.generate_hf --input eval.jsonl --output pred_base.jsonl --model Qwen/Qwen2.5-7B-Instruct --revision <commit>
python -m training.admissions.generate_hf ... --adapter outputs/trial/adapter --output pred_trial.jsonl

- 탐욕 디코딩(temperature 0)으로 같은 입력이면 같은 답이 나오게 한다.
- 출력: JSONL {id, answer, latency_seconds, finish_reason}. 채점은 counsel_eval.py가 한다.
- 가짜 답을 만들지 않는다. GPU가 없으면 멈춘다.
"""
import argparse
import json
import os
import time
from pathlib import Path

from training.common import messages_for, read_jsonl, require_revision, validate_record


def batches(rows, size):
    for start in range(0, len(rows), size):
        yield rows[start:start + size]


def prompt_texts(tokenizer, rows):
    return [tokenizer.apply_chat_template(messages_for(r), tokenize=False, add_generation_prompt=True) for r in rows]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--adapter", help="학습한 LoRA 어댑터 폴더(없으면 베이스 모델)")
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--max-new-tokens", type=int, default=768)
    parser.add_argument("--allow-download", action="store_true")
    args = parser.parse_args()
    require_revision(args.revision)
    rows = read_jsonl(args.input)
    for row in rows:
        validate_record(row, evaluation=True)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
    if not args.allow_download:
        os.environ["HF_HUB_OFFLINE"] = "1"
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA GPU required")
    kwargs = {"revision": args.revision, "local_files_only": not args.allow_download, "trust_remote_code": False}
    tokenizer = AutoTokenizer.from_pretrained(args.model, **kwargs)
    tokenizer.padding_side = "left"
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token
    dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16
    model = AutoModelForCausalLM.from_pretrained(args.model, **kwargs, torch_dtype=dtype, device_map={"": 0})
    if args.adapter:
        from peft import PeftModel
        model = PeftModel.from_pretrained(model, args.adapter)
    model.eval()
    with output.open("x", encoding="utf-8") as handle:
        for chunk in batches(rows, args.batch_size):
            encoded = tokenizer(prompt_texts(tokenizer, chunk), return_tensors="pt", padding=True).to(model.device)
            start = time.perf_counter()
            with torch.no_grad():
                generated = model.generate(**encoded, max_new_tokens=args.max_new_tokens, do_sample=False,
                                           pad_token_id=tokenizer.pad_token_id)
            per_item = (time.perf_counter() - start) / len(chunk)
            width = encoded["input_ids"].shape[1]
            for row, ids in zip(chunk, generated):
                new = ids[width:]
                finished = bool((new == tokenizer.eos_token_id).any()) or len(new) < args.max_new_tokens
                answer = tokenizer.decode(new, skip_special_tokens=True).strip()
                handle.write(json.dumps({"id": row["id"], "answer": answer, "latency_seconds": round(per_item, 3),
                                         "finish_reason": "stop" if finished else "length",
                                         "model": args.model, "revision": args.revision,
                                         "adapter": args.adapter}, ensure_ascii=False) + "\n")
            handle.flush()


if __name__ == "__main__":
    main()
