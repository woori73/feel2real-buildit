"""T10: response-only QLoRA. --dry-run validates data without model/GPU access."""
import argparse
import importlib.metadata
import json
import os
from pathlib import Path
from training.common import check_split, fingerprint, messages_for, read_jsonl, require_revision, write_json


TRAINING_MODELS = {"Qwen/Qwen2.5-7B-Instruct"}


def load_config(path):
    config = json.loads(Path(path).read_text(encoding="utf-8"))
    required = {"model", "max_seq_length", "lora_r", "lora_alpha", "lora_dropout",
                "learning_rate", "num_train_epochs", "gradient_accumulation_steps", "seed"}
    if set(config) != required or config["model"] not in TRAINING_MODELS:
        raise ValueError("unsupported model or configuration keys")
    for key in ("max_seq_length", "lora_r", "lora_alpha", "gradient_accumulation_steps"):
        if type(config[key]) is not int or config[key] <= 0:
            raise ValueError("positive integer required: " + key)
    if type(config["seed"]) is not int:
        raise ValueError("integer seed required")
    for key in ("learning_rate", "num_train_epochs"):
        if not isinstance(config[key], (int, float)) or not 0 < config[key] < 100:
            raise ValueError("invalid " + key)
    if not isinstance(config["lora_dropout"], (int, float)) or not 0 <= config["lora_dropout"] < 1:
        raise ValueError("invalid lora_dropout")
    return config


def encode_response(tokenizer, row, max_length):
    messages = messages_for(row)
    output = row["output"]
    if not isinstance(output, str):
        output = json.dumps(output, ensure_ascii=False)
    if not output.strip():
        raise ValueError("empty training response")
    prompt = tokenizer.apply_chat_template(messages, tokenize=True, add_generation_prompt=True)
    full = tokenizer.apply_chat_template(messages + [{"role": "assistant", "content": output}],
                                         tokenize=True, add_generation_prompt=False)
    if full[:len(prompt)] != prompt or len(full) <= len(prompt):
        raise ValueError("chat template prefix mismatch; response mask cannot be established")
    if len(full) > max_length:
        return None  # Never silently truncate a gold answer or train on prompt-only rows.
    return {"input_ids": full, "attention_mask": [1] * len(full),
            "labels": [-100] * len(prompt) + full[len(prompt):]}


class ResponseCollator:
    def __init__(self, pad_token_id):
        self.pad_token_id = pad_token_id

    def __call__(self, features):
        import torch
        width = max(len(f["input_ids"]) for f in features)
        return {key: torch.tensor([f[key] + [pad] * (width - len(f[key])) for f in features], dtype=torch.long)
                for key, pad in [("input_ids", self.pad_token_id), ("attention_mask", 0), ("labels", -100)]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--train", required=True)
    parser.add_argument("--eval", required=True, help="Untouched holdout: only leakage checks, never used by Trainer")
    parser.add_argument("--cutoff", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--config", default=str(Path(__file__).with_name("config.json")))
    parser.add_argument("--allow-download", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    require_revision(args.revision)
    config = load_config(args.config)
    train, holdout = read_jsonl(args.train), read_jsonl(args.eval)
    check_split(train, holdout, args.cutoff)
    manifest = {"schema_version": "f2r-train-v1", "base_model": config["model"],
                "revision": args.revision, "config": config, "train_rows": len(train),
                "holdout_rows": len(holdout), "cutoff": args.cutoff,
                "train_sha256": fingerprint(args.train), "holdout_sha256": fingerprint(args.eval)}
    if args.dry_run:
        print(json.dumps({**manifest, "executed": False}, ensure_ascii=False))
        return
    os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
    if not args.allow_download:
        os.environ["HF_HUB_OFFLINE"] = "1"
    import torch
    from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
    from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig, Trainer, TrainingArguments, set_seed
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA GPU required; no simulated training fallback")
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=False)
    set_seed(config["seed"])
    dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16
    kwargs = {"revision": args.revision, "local_files_only": not args.allow_download, "trust_remote_code": False}
    tokenizer = AutoTokenizer.from_pretrained(config["model"], **kwargs)
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token
    if tokenizer.pad_token_id is None:
        raise ValueError("tokenizer has no padding or EOS token")
    features = [encode_response(tokenizer, row, config["max_seq_length"]) for row in train]
    skipped = sum(f is None for f in features)
    features = [f for f in features if f is not None]
    if not features:
        raise ValueError("no trainable rows within max_seq_length")
    model = AutoModelForCausalLM.from_pretrained(
        config["model"], **kwargs, device_map={"": 0}, torch_dtype=dtype,
        quantization_config=BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4",
                                              bnb_4bit_use_double_quant=True, bnb_4bit_compute_dtype=dtype))
    model.config.use_cache = False
    model = prepare_model_for_kbit_training(model)
    model = get_peft_model(model, LoraConfig(r=config["lora_r"], lora_alpha=config["lora_alpha"],
                           lora_dropout=config["lora_dropout"], bias="none", task_type="CAUSAL_LM",
                           target_modules="all-linear"))
    arguments = TrainingArguments(
        output_dir=str(output), per_device_train_batch_size=1,
        gradient_accumulation_steps=config["gradient_accumulation_steps"],
        num_train_epochs=config["num_train_epochs"], learning_rate=config["learning_rate"],
        bf16=dtype == torch.bfloat16, fp16=dtype == torch.float16,
        gradient_checkpointing=True, optim="paged_adamw_8bit", logging_steps=10,
        save_steps=50, save_total_limit=2, report_to=[], seed=config["seed"])
    manifest.update({"used_rows": len(features), "skipped_overlength_rows": skipped,
                     "packages": {name: importlib.metadata.version(name) for name in
                                  ["torch", "transformers", "peft", "accelerate", "bitsandbytes"]},
                     "gpu": torch.cuda.get_device_name(0)})
    write_json(output / "run_manifest.json", {**manifest, "status": "started"})
    trainer = Trainer(model=model, args=arguments, train_dataset=features,
                      data_collator=ResponseCollator(tokenizer.pad_token_id), processing_class=tokenizer)
    trainer.train()
    adapter = output / "adapter"
    model.save_pretrained(adapter, safe_serialization=True)
    tokenizer.save_pretrained(adapter)
    write_json(adapter / "run_manifest.json", {**manifest, "status": "completed"})
    write_json(output / "run_manifest.json", {**manifest, "status": "completed", "adapter": "adapter"})


if __name__ == "__main__":
    main()
