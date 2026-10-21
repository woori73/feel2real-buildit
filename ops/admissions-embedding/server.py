"""EmbeddingGemma(ONNX, CPU) 임베딩 서버. 127.0.0.1 전용, 동시 1건, 메모리 절약 설정.
POST /embed {"texts":[...], "task":"document|query"} → {"embeddings":[[768 floats, L2 정규화]]}
GET  /health → {"ok":true,"dim":768}
"""
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

import embed_core

MAX_TOKENS = int(os.getenv("EMBED_MAX_TOKENS", "512"))
MAX_BATCH = int(os.getenv("EMBED_MAX_BATCH", "8"))
MAX_CHARS = int(os.getenv("EMBED_MAX_CHARS", "4000"))
DIM = int(os.getenv("EMBED_DIM", "768"))
PORT = int(os.getenv("EMBED_PORT", "8081"))

opts = ort.SessionOptions()
opts.intra_op_num_threads = int(os.getenv("EMBED_THREADS", "1"))
opts.inter_op_num_threads = 1
opts.enable_cpu_mem_arena = False  # 상주 메모리 증가 방지
opts.enable_mem_pattern = False
session = ort.InferenceSession(os.environ["EMBED_MODEL_PATH"], opts, providers=["CPUExecutionProvider"])
INPUTS = {i.name for i in session.get_inputs()}
OUTPUTS = [o.name for o in session.get_outputs()]
if not INPUTS <= {"input_ids", "attention_mask"} or "sentence_embedding" not in OUTPUTS:
    raise SystemExit(f"unsupported model io: inputs={sorted(INPUTS)} outputs={OUTPUTS}")

tokenizer = Tokenizer.from_file(os.environ["EMBED_TOKENIZER_PATH"])
tokenizer.enable_truncation(MAX_TOKENS)
tokenizer.enable_padding(pad_id=tokenizer.token_to_id("<pad>") or 0, pad_token="<pad>")

busy = threading.BoundedSemaphore(1)


def embed(texts):
    enc = tokenizer.encode_batch(texts)
    feed = {"input_ids": np.array([e.ids for e in enc], dtype=np.int64),
            "attention_mask": np.array([e.attention_mask for e in enc], dtype=np.int64)}
    out = session.run(["sentence_embedding"], {k: v for k, v in feed.items() if k in INPUTS})[0]
    vectors = [embed_core.l2_normalize([float(x) for x in row]) for row in out]
    if any(len(v) != DIM for v in vectors):
        raise RuntimeError("dimension mismatch")
    return vectors


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"ok": True, "dim": DIM})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/embed":
            return self._send(404, {"error": "not found"})
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > 262144:
            return self._send(400, {"error": "bad length"})
        try:
            texts, task = embed_core.parse_request(self.rfile.read(length), MAX_BATCH, MAX_CHARS)
        except embed_core.BadRequest as e:
            return self._send(400, {"error": str(e)})
        if not busy.acquire(timeout=1):
            return self._send(503, {"error": "busy"})  # 동시 요청 제한: 앱은 폴백한다
        try:
            self._send(200, {"embeddings": embed(embed_core.with_prefix(texts, task))})
        except Exception as e:  # noqa: BLE001
            self._send(500, {"error": type(e).__name__})
        finally:
            busy.release()

    def log_message(self, *args):  # 본문·질문이 로그에 남지 않게 한다
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
