"""T11: unquantized base + LoRA merge -> F16 GGUF -> Q4_K_M.

No cloning, package install, serving, or deployment is performed here.
"""
import argparse
import json
import os
import shlex
import subprocess
from pathlib import Path
from training.common import fingerprint, require_revision, write_json
from training.qlora.train import TRAINING_MODELS


def conversion_commands(llama_dir, merged_dir, output):
    import sys
    llama_dir, merged_dir, output = map(Path, (llama_dir, merged_dir, output))
    return [
        [sys.executable, str(llama_dir / "convert_hf_to_gguf.py"), str(merged_dir),
         "--outfile", str(output / "model-f16.gguf"), "--outtype", "f16"],
        [str(llama_dir / "build/bin/llama-quantize"), str(output / "model-f16.gguf"),
         str(output / "model-Q4_K_M.gguf"), "Q4_K_M"],
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--adapter", required=True)
    parser.add_argument("--llama-cpp-dir", required=True)
    parser.add_argument("--llama-cpp-revision", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--allow-download", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    require_revision(args.revision)
    require_revision(args.llama_cpp_revision)
    if args.model not in TRAINING_MODELS:
        parser.error("unsupported merge model")
    adapter, llama_dir, output = (Path(v).resolve() for v in (args.adapter, args.llama_cpp_dir, args.output))
    manifest = json.loads((adapter / "run_manifest.json").read_text(encoding="utf-8"))
    if (manifest.get("status"), manifest.get("base_model"), manifest.get("revision")) != ("completed", args.model, args.revision):
        raise ValueError("adapter manifest does not match completed training base/revision")
    for path in (llama_dir / "convert_hf_to_gguf.py", llama_dir / "build/bin/llama-quantize"):
        if not path.is_file():
            raise FileNotFoundError(path)
    commands = conversion_commands(llama_dir, output / "merged", output)
    if args.dry_run:
        print(json.dumps({"executed": False, "commands": commands, "base_revision": args.revision}, ensure_ascii=False))
        return
    actual = subprocess.check_output(["git", "-C", str(llama_dir), "rev-parse", "HEAD"], text=True).strip()
    remote = subprocess.check_output(["git", "-C", str(llama_dir), "remote", "get-url", "origin"], text=True).strip()
    if actual != args.llama_cpp_revision or remote.removesuffix(".git") != "https://github.com/ggml-org/llama.cpp":
        raise ValueError("use a pinned official ggml-org/llama.cpp checkout")
    dirty = subprocess.check_output(["git", "-C", str(llama_dir), "status", "--porcelain", "--untracked-files=no"], text=True)
    if dirty.strip():
        raise ValueError("llama.cpp tracked sources modified")
    output.mkdir(parents=True, exist_ok=False)
    os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
    if not args.allow_download:
        os.environ["HF_HUB_OFFLINE"] = "1"
    import torch
    from peft import PeftModel
    from transformers import AutoModelForCausalLM, AutoTokenizer
    base = AutoModelForCausalLM.from_pretrained(args.model, revision=args.revision,
            local_files_only=not args.allow_download, trust_remote_code=False, torch_dtype=torch.float16,
            device_map={"": "cpu"})
    merged = PeftModel.from_pretrained(base, str(adapter)).merge_and_unload(safe_merge=True)
    merged.save_pretrained(output / "merged", safe_serialization=True)
    AutoTokenizer.from_pretrained(str(adapter), local_files_only=True, trust_remote_code=False).save_pretrained(output / "merged")
    del merged, base
    import gc
    gc.collect()
    for command in commands:
        print(shlex.join(command))
        subprocess.run(command, check=True)
    gguf = output / "model-Q4_K_M.gguf"
    with gguf.open("rb") as stream:
        magic = stream.read(4)
    if gguf.stat().st_size <= 4 or magic != b"GGUF":
        raise ValueError("quantizer did not produce a GGUF file")
    write_json(output / "export_manifest.json", {
        "schema_version": "f2r-export-v1", "base_model": args.model, "revision": args.revision,
        "adapter_manifest_sha256": fingerprint(adapter / "run_manifest.json"),
        "llama_cpp_revision": actual, "quantization": "Q4_K_M",
        "gguf_sha256": fingerprint(gguf), "bytes": gguf.stat().st_size, "evaluation_passed": False,
    })


if __name__ == "__main__":
    main()
