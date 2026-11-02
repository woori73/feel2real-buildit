#!/usr/bin/env python3
"""F2R 인스타 자동 피드 GPU 작업자.

runyour.ai GPU 인스턴스(ComfyUI 실행 중)에서 돌린다. F2R 서버에서 이미지 작업을 하나씩 가져가
ComfyUI로 만든 뒤 JPEG로 올린다. 운영 DB·자격증명은 GPU에 두지 않는다(작업자 토큰 하나만).

환경변수
  F2R_BASE_URL       예: https://feel2real.co.kr
  SNS_WORKER_TOKEN   F2R 서버의 SNS_WORKER_TOKEN과 같은 값
  COMFYUI_URL        기본 http://127.0.0.1:8188
  SNS_CKPT           기본 sd_xl_base_1.0.safetensors (ComfyUI models/checkpoints 안 파일명)
  SNS_IPADAPTER_PRESET  기본 "PLUS FACE (portraits)" (ComfyUI_IPAdapter_plus 노드)
  SNS_IPADAPTER_WEIGHT  기본 0.8

실행
  python3 worker.py                 # 계속 돌면서 30초마다 작업 확인
  python3 worker.py --once          # 작업 하나만 처리하고 끝
  python3 worker.py --exit-when-idle 30   # 30분 동안 작업이 없으면 끝(GPU 요금 절약)
"""
import argparse
import io
import json
import os
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

POLL_SECONDS = 30
COMFY_TIMEOUT_SECONDS = 600
NEGATIVE = ("text, letters, watermark, logo, signature, caption, deformed face, extra fingers, bad hands, "
            "blurry, lowres, jpeg artifacts, duplicate person, cropped head")

# SDXL 권장 해상도(약 1메가픽셀).
RESOLUTIONS = {"1:1": (1024, 1024), "4:5": (896, 1120), "16:9": (1344, 768)}


class Config:
    def __init__(self, env=None):
        env = os.environ if env is None else env
        self.base_url = (env.get("F2R_BASE_URL") or "").rstrip("/")
        self.token = env.get("SNS_WORKER_TOKEN") or ""
        self.comfy_url = (env.get("COMFYUI_URL") or "http://127.0.0.1:8188").rstrip("/")
        self.ckpt = env.get("SNS_CKPT") or "sd_xl_base_1.0.safetensors"
        self.ipadapter_preset = env.get("SNS_IPADAPTER_PRESET") or "PLUS FACE (portraits)"
        self.ipadapter_weight = float(env.get("SNS_IPADAPTER_WEIGHT") or "0.8")

    def check(self):
        missing = [n for n, v in (("F2R_BASE_URL", self.base_url), ("SNS_WORKER_TOKEN", self.token)) if not v]
        if missing:
            raise SystemExit("환경변수가 비어 있습니다: " + ", ".join(missing))


# ---------- HTTP ----------

def _request(url, method="GET", data=None, headers=None, timeout=60):
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            return res.status, res.read(), res.headers.get("Content-Type", "")
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers.get("Content-Type", "") if e.headers else ""


def multipart(fields, files):
    """fields: {name: str}, files: {name: (filename, bytes, content_type)} → (body, content_type)."""
    boundary = "----f2rsns" + uuid.uuid4().hex
    out = io.BytesIO()
    for name, value in fields.items():
        out.write(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n" % (boundary, name, value)).encode("utf-8"))
    for name, (filename, data, ctype) in files.items():
        out.write(("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n"
                   % (boundary, name, filename, ctype)).encode("utf-8"))
        out.write(data)
        out.write(b"\r\n")
    out.write(("--%s--\r\n" % boundary).encode("utf-8"))
    return out.getvalue(), "multipart/form-data; boundary=" + boundary


class F2R:
    """F2R 작업자 API. 운영 서버는 쓰기 요청에 Origin을 확인하므로 기준 주소를 Origin으로 보낸다."""

    def __init__(self, cfg):
        self.cfg = cfg

    def _headers(self, extra=None):
        h = {"Authorization": "Bearer " + self.cfg.token, "Origin": self.cfg.base_url}
        h.update(extra or {})
        return h

    def next_job(self):
        status, body, _ = _request(self.cfg.base_url + "/api/sns/worker/jobs/next", "POST", b"", self._headers())
        if status == 204:
            return None
        if status != 200:
            raise RuntimeError("작업 가져오기 실패 HTTP %d: %s" % (status, body[:200].decode("utf-8", "replace")))
        return json.loads(body.decode("utf-8"))

    def reference(self, path):
        status, body, ctype = _request(self.cfg.base_url + path, "GET", None, self._headers())
        if status != 200:
            raise RuntimeError("참고 이미지 받기 실패 HTTP %d" % status)
        return body, ctype or "image/jpeg"

    def result(self, job_id, jpeg, duration_ms, model):
        body, ctype = multipart({"durationMs": str(duration_ms), "model": model},
                                {"image": ("sns-%s.jpg" % job_id, jpeg, "image/jpeg")})
        status, resp, _ = _request(self.cfg.base_url + "/api/sns/worker/jobs/%s/result" % job_id, "POST", body,
                                   self._headers({"Content-Type": ctype}), timeout=120)
        if status != 200:
            raise RuntimeError("결과 올리기 실패 HTTP %d: %s" % (status, resp[:200].decode("utf-8", "replace")))

    def fail(self, job_id, reason, duration_ms):
        data = json.dumps({"reason": reason[:300], "durationMs": duration_ms}).encode("utf-8")
        _request(self.cfg.base_url + "/api/sns/worker/jobs/%s/fail" % job_id, "POST", data,
                 self._headers({"Content-Type": "application/json"}))


# ---------- ComfyUI ----------

def build_workflow(cfg, prompt, aspect_ratio, reference_name, seed):
    """ComfyUI API 형식 워크플로. 참고 이미지가 있으면 IPAdapter(얼굴)로 인물 생김새를 맞춘다."""
    width, height = RESOLUTIONS.get(aspect_ratio, RESOLUTIONS["1:1"])
    wf = {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": cfg.ckpt}},
        "5": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["1", 1]}},
        "6": {"class_type": "CLIPTextEncode", "inputs": {"text": NEGATIVE, "clip": ["1", 1]}},
        "7": {"class_type": "EmptyLatentImage", "inputs": {"width": width, "height": height, "batch_size": 1}},
        "8": {"class_type": "KSampler", "inputs": {
            "model": ["1", 0], "positive": ["5", 0], "negative": ["6", 0], "latent_image": ["7", 0],
            "seed": seed, "steps": 30, "cfg": 6.0, "sampler_name": "dpmpp_2m", "scheduler": "karras", "denoise": 1.0}},
        "9": {"class_type": "VAEDecode", "inputs": {"samples": ["8", 0], "vae": ["1", 2]}},
        "10": {"class_type": "SaveImage", "inputs": {"images": ["9", 0], "filename_prefix": "f2r_sns"}},
    }
    if reference_name:
        wf["2"] = {"class_type": "LoadImage", "inputs": {"image": reference_name}}
        wf["3"] = {"class_type": "IPAdapterUnifiedLoader", "inputs": {"model": ["1", 0], "preset": cfg.ipadapter_preset}}
        wf["4"] = {"class_type": "IPAdapterAdvanced", "inputs": {
            "model": ["3", 0], "ipadapter": ["3", 1], "image": ["2", 0], "weight": cfg.ipadapter_weight,
            "weight_type": "linear", "combine_embeds": "concat", "start_at": 0.0, "end_at": 1.0,
            "embeds_scaling": "V only"}}
        wf["8"]["inputs"]["model"] = ["4", 0]
    return wf


class Comfy:
    def __init__(self, cfg, sleep=time.sleep):
        self.cfg = cfg
        self.sleep = sleep

    def upload(self, data, filename):
        body, ctype = multipart({"overwrite": "true"}, {"image": (filename, data, "image/jpeg")})
        status, resp, _ = _request(self.cfg.comfy_url + "/upload/image", "POST", body, {"Content-Type": ctype})
        if status != 200:
            raise RuntimeError("ComfyUI 참고 이미지 업로드 실패 HTTP %d" % status)
        return json.loads(resp.decode("utf-8"))["name"]

    def generate(self, workflow):
        payload = json.dumps({"prompt": workflow, "client_id": uuid.uuid4().hex}).encode("utf-8")
        status, resp, _ = _request(self.cfg.comfy_url + "/prompt", "POST", payload, {"Content-Type": "application/json"})
        if status != 200:
            raise RuntimeError("ComfyUI 작업 등록 실패 HTTP %d: %s" % (status, resp[:300].decode("utf-8", "replace")))
        prompt_id = json.loads(resp.decode("utf-8"))["prompt_id"]
        deadline = time.time() + COMFY_TIMEOUT_SECONDS
        while time.time() < deadline:
            status, resp, _ = _request(self.cfg.comfy_url + "/history/" + prompt_id)
            if status == 200:
                hist = json.loads(resp.decode("utf-8")).get(prompt_id)
                if hist:
                    st = hist.get("status", {})
                    if st.get("status_str") == "error":
                        raise RuntimeError("ComfyUI 생성 오류")
                    images = hist.get("outputs", {}).get("10", {}).get("images", [])
                    if images:
                        return self.view(images[0])
            self.sleep(2)
        raise RuntimeError("ComfyUI 생성 시간 초과(%d초)" % COMFY_TIMEOUT_SECONDS)

    def view(self, image):
        q = urllib.parse.urlencode({"filename": image["filename"], "subfolder": image.get("subfolder", ""),
                                    "type": image.get("type", "output")})
        status, body, _ = _request(self.cfg.comfy_url + "/view?" + q)
        if status != 200:
            raise RuntimeError("ComfyUI 결과 이미지 받기 실패 HTTP %d" % status)
        return body


def to_jpeg(png_bytes, quality=92):
    """인스타그램은 JPEG만 받는다."""
    from PIL import Image  # ComfyUI 환경에 포함
    im = Image.open(io.BytesIO(png_bytes)).convert("RGB")
    out = io.BytesIO()
    im.save(out, "JPEG", quality=quality, optimize=True)
    return out.getvalue()


# ---------- 작업 처리 ----------

def process_one(cfg, f2r, comfy, rng=random):
    """작업 하나 처리. 반환: 처리한 작업 id 또는 None(작업 없음)."""
    job = f2r.next_job()
    if not job:
        return None
    job_id = job["jobId"]
    t0 = time.time()
    try:
        ref_name = None
        if job.get("referenceImageUrl"):
            data, _ = f2r.reference(job["referenceImageUrl"])
            ref_name = comfy.upload(data, "f2r_ref_rule%s.jpg" % job.get("ruleId"))
        wf = build_workflow(cfg, job["prompt"], job.get("aspectRatio") or "1:1", ref_name, rng.randint(1, 2**31 - 1))
        png = comfy.generate(wf)
        jpeg = to_jpeg(png)
        duration_ms = int((time.time() - t0) * 1000)
        model = "comfyui:" + cfg.ckpt + ("+ipadapter" if ref_name else "")
        f2r.result(job_id, jpeg, duration_ms, model)
        print("[sns] 작업 %s 완료 (%.1f초)" % (job_id, duration_ms / 1000.0), flush=True)
    except Exception as e:  # noqa: BLE001 — 어떤 실패든 서버에 알려 1회 재시도/실패 처리를 맡긴다.
        duration_ms = int((time.time() - t0) * 1000)
        print("[sns] 작업 %s 실패: %s" % (job_id, e), file=sys.stderr, flush=True)
        try:
            f2r.fail(job_id, str(e), duration_ms)
        except Exception:  # noqa: BLE001
            pass
    return job_id


def main(argv=None):
    ap = argparse.ArgumentParser(description="F2R 인스타 자동 피드 GPU 작업자")
    ap.add_argument("--once", action="store_true", help="작업 하나만 처리하고 끝")
    ap.add_argument("--exit-when-idle", type=int, default=0, metavar="MINUTES", help="이 시간 동안 작업이 없으면 끝")
    args = ap.parse_args(argv)
    cfg = Config()
    cfg.check()
    f2r, comfy = F2R(cfg), Comfy(cfg)
    idle_since = time.time()
    while True:
        try:
            done = process_one(cfg, f2r, comfy)
        except Exception as e:  # noqa: BLE001 — 서버 일시 오류는 다음 주기에 다시
            print("[sns] 작업 확인 오류: %s" % e, file=sys.stderr, flush=True)
            done = None
        if args.once:
            return 0
        if done:
            idle_since = time.time()
            continue
        if args.exit_when_idle and time.time() - idle_since > args.exit_when_idle * 60:
            print("[sns] %d분 동안 작업이 없어 끝냅니다." % args.exit_when_idle, flush=True)
            return 0
        time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    sys.exit(main())
