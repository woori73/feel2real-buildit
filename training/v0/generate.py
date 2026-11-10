"""T09: generate from a local llama-server. Never substitute a fake response."""
import argparse
import json
import os
import time
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, urlopen, build_opener, HTTPRedirectHandler
from training.common import messages_for, parse_object, read_jsonl, validate_record


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("model endpoint redirect refused")


def generate(row, endpoint, model, revision, max_tokens=2048, timeout=180, api_key=None):
    if not row.get("source_chunks"):
        return {"id": row["id"], "question": None, "evidence_refs": [], "abstained": True,
                "reason": "no_sources", "generator_model": model, "model_revision": revision}
    payload = {"model": model, "messages": messages_for(row), "max_tokens": max_tokens,
               "temperature": 0, "stream": False, "response_format": {"type": "json_object"}}
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = "Bearer " + api_key
    request = Request(endpoint.rstrip("/") + "/chat/completions",
                      data=json.dumps(payload, ensure_ascii=False).encode(), headers=headers)
    start = time.perf_counter()
    with build_opener(NoRedirect()).open(request, timeout=timeout) as response:
        result = json.load(response)
    elapsed = time.perf_counter() - start
    choice = result["choices"][0]
    # 응답은 받았지만 잘렸거나 JSON이 깨진 경우는 형식 실패로 기록하고 다음 문항으로 넘어간다
    # (평가 하니스가 error 행을 형식 실패로 센다). 전송 실패(HTTP·시간 초과)는 그대로 예외로 멈춘다.
    def failed(reason):
        return {"id": row["id"], "question": None, "evidence_refs": [], "error": reason,
                "generator_model": model, "model_revision": revision,
                "usage": result.get("usage", {}), "latency_seconds": elapsed}
    if choice.get("finish_reason") != "stop":
        return failed("truncated")
    try:
        parsed = parse_object(choice["message"]["content"])
    except (ValueError, TypeError):
        return failed("invalid_json")
    if "question" in parsed and parsed["question"] is None:
        return {"id": row["id"], "question": None, "evidence_refs": [], "abstained": True,
                "reason": "model_abstained", "generator_model": model, "model_revision": revision,
                "usage": result.get("usage", {}), "latency_seconds": elapsed}
    if not isinstance(parsed.get("question"), dict):
        return failed("missing_question")
    return {"id": row["id"], "question": parsed["question"],
            "evidence_refs": parsed.get("evidence_refs", []), "generator_model": model,
            "model_revision": revision, "served_model": result.get("model"),
            "usage": result.get("usage", {}), "latency_seconds": elapsed}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--endpoint", default="http://127.0.0.1:8080/v1")
    parser.add_argument("--model", required=True)
    parser.add_argument("--model-revision", required=True, help="GGUF sha256 or pinned model commit")
    parser.add_argument("--max-tokens", type=int, default=2048)
    parser.add_argument("--timeout", type=float, default=180)
    parser.add_argument("--allow-remote", action="store_true", help="Explicitly allow an approved remote endpoint")
    args = parser.parse_args()
    target = urlsplit(args.endpoint)
    if target.scheme not in {"http", "https"} or not target.hostname or target.username or target.password:
        parser.error("valid HTTP endpoint without embedded credentials required")
    if not args.allow_remote and target.hostname not in {"localhost", "127.0.0.1", "::1"}:
        parser.error("remote endpoint requires --allow-remote")
    if target.query or target.fragment or args.max_tokens <= 0 or args.timeout <= 0:
        parser.error("invalid endpoint or generation limits")
    rows = read_jsonl(args.input)
    for row in rows:
        validate_record(row)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation prevents accidental overwrite and partial append/resume ambiguity.
    with output.open("x", encoding="utf-8") as stream:
        for row in rows:
            result = generate(row, args.endpoint, args.model, args.model_revision,
                              args.max_tokens, args.timeout, os.environ.get("F2R_MODEL_API_KEY"))
            stream.write(json.dumps(result, ensure_ascii=False, allow_nan=False) + "\n")
            stream.flush()


if __name__ == "__main__":
    main()
