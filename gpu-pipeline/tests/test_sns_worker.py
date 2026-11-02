"""sns_worker 단위·흐름 테스트(가짜 F2R·ComfyUI 서버). 실행: python3 -m unittest discover -s gpu-pipeline/tests"""
import io
import json
import os
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "sns_worker"))
import worker  # noqa: E402

try:
    from PIL import Image
    HAVE_PIL = True
except ImportError:  # pragma: no cover
    HAVE_PIL = False


def png_bytes():
    out = io.BytesIO()
    Image.new("RGB", (8, 8), (10, 20, 30)).save(out, "PNG")
    return out.getvalue()


class BuildWorkflowTest(unittest.TestCase):
    def test_given_reference_when_build_then_ipadapter_feeds_sampler(self):
        # given
        cfg = worker.Config({"F2R_BASE_URL": "https://x", "SNS_WORKER_TOKEN": "t"})
        # when
        wf = worker.build_workflow(cfg, "a man", "4:5", "ref.jpg", 7)
        # then
        self.assertEqual(wf["8"]["inputs"]["model"], ["4", 0])
        self.assertEqual(wf["2"]["inputs"]["image"], "ref.jpg")
        self.assertEqual(wf["3"]["inputs"]["preset"], "PLUS FACE (portraits)")
        self.assertEqual((wf["7"]["inputs"]["width"], wf["7"]["inputs"]["height"]), (896, 1120))
        self.assertEqual(wf["8"]["inputs"]["seed"], 7)

    def test_given_no_reference_when_build_then_plain_sdxl(self):
        # given
        cfg = worker.Config({"F2R_BASE_URL": "https://x", "SNS_WORKER_TOKEN": "t"})
        # when
        wf = worker.build_workflow(cfg, "city", "unknown", None, 1)
        # then
        self.assertNotIn("4", wf)
        self.assertEqual(wf["8"]["inputs"]["model"], ["1", 0])
        self.assertEqual(wf["7"]["inputs"]["width"], 1024)

    def test_given_fields_and_file_when_multipart_then_both_encoded(self):
        # given / when
        body, ctype = worker.multipart({"durationMs": "12"}, {"image": ("a.jpg", b"\xff\xd8\xff", "image/jpeg")})
        # then
        self.assertTrue(ctype.startswith("multipart/form-data; boundary="))
        self.assertIn(b'name="durationMs"\r\n\r\n12', body)
        self.assertIn(b'filename="a.jpg"', body)

    def test_given_missing_env_when_check_then_exit(self):
        # given
        cfg = worker.Config({})
        # when / then
        with self.assertRaises(SystemExit):
            cfg.check()


@unittest.skipUnless(HAVE_PIL, "Pillow 필요")
class ProcessOneFlowTest(unittest.TestCase):
    """가짜 서버 하나로 F2R 작업자 API와 ComfyUI API를 함께 흉내 낸다."""

    def setUp(self):
        state = self.state = {"job_given": False, "result": None, "fail": None, "auth": [], "origin": [], "prompt": None,
                              "comfy_error": False}

        class H(BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def _send(self, code, body=b"", ctype="application/json"):
                self.send_response(code)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self):
                if self.path.startswith("/api/sns/worker/jobs/5/reference"):
                    state["auth"].append(self.headers.get("Authorization"))
                    return self._send(200, b"\xff\xd8\xffREF", "image/jpeg")
                if self.path.startswith("/history/"):
                    if state["comfy_error"]:
                        return self._send(200, json.dumps({"p1": {"status": {"status_str": "error"}, "outputs": {}}}).encode())
                    return self._send(200, json.dumps({"p1": {"status": {"status_str": "success"},
                                                              "outputs": {"10": {"images": [{"filename": "o.png", "subfolder": "", "type": "output"}]}}}}).encode())
                if self.path.startswith("/view"):
                    return self._send(200, png_bytes(), "image/png")
                self._send(404)

            def do_POST(self):
                length = int(self.headers.get("Content-Length") or 0)
                body = self.rfile.read(length)
                if self.path == "/api/sns/worker/jobs/next":
                    state["auth"].append(self.headers.get("Authorization"))
                    state["origin"].append(self.headers.get("Origin"))
                    if state["job_given"]:
                        return self._send(204)
                    state["job_given"] = True
                    return self._send(200, json.dumps({"jobId": 5, "ruleId": 1, "attempt": 1, "prompt": "a man, photo",
                                                       "aspectRatio": "1:1", "referenceImageUrl": "/api/sns/worker/jobs/5/reference"}).encode())
                if self.path == "/upload/image":
                    return self._send(200, json.dumps({"name": "f2r_ref_rule1.jpg"}).encode())
                if self.path == "/prompt":
                    state["prompt"] = json.loads(body.decode())
                    return self._send(200, json.dumps({"prompt_id": "p1"}).encode())
                if self.path == "/api/sns/worker/jobs/5/result":
                    state["result"] = body
                    return self._send(200, b'{"message":"ok"}')
                if self.path == "/api/sns/worker/jobs/5/fail":
                    state["fail"] = json.loads(body.decode())
                    return self._send(200, b'{"message":"ok"}')
                self._send(404)

        self.server = HTTPServer(("127.0.0.1", 0), H)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        base = "http://127.0.0.1:%d" % self.server.server_port
        self.cfg = worker.Config({"F2R_BASE_URL": base, "SNS_WORKER_TOKEN": "secret", "COMFYUI_URL": base})

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def test_given_job_when_process_then_uploads_jpeg_result_with_token(self):
        # given
        f2r, comfy = worker.F2R(self.cfg), worker.Comfy(self.cfg, sleep=lambda s: None)
        # when
        done = worker.process_one(self.cfg, f2r, comfy)
        # then
        self.assertEqual(done, 5)
        self.assertIsNone(self.state["fail"])
        self.assertIn(b"\xff\xd8\xff", self.state["result"])  # JPEG로 바꿔 올림
        self.assertIn(b'name="durationMs"', self.state["result"])
        self.assertTrue(all(a == "Bearer secret" for a in self.state["auth"]))
        self.assertEqual(self.state["origin"][0], self.cfg.base_url)
        self.assertEqual(self.state["prompt"]["prompt"]["2"]["inputs"]["image"], "f2r_ref_rule1.jpg")

    def test_given_no_job_when_process_then_none(self):
        # given
        self.state["job_given"] = True
        # when
        done = worker.process_one(self.cfg, worker.F2R(self.cfg), worker.Comfy(self.cfg, sleep=lambda s: None))
        # then
        self.assertIsNone(done)

    def test_given_comfy_error_when_process_then_reports_fail(self):
        # given
        self.state["comfy_error"] = True
        # when
        worker.process_one(self.cfg, worker.F2R(self.cfg), worker.Comfy(self.cfg, sleep=lambda s: None))
        # then
        self.assertIsNone(self.state["result"])
        self.assertIn("ComfyUI", self.state["fail"]["reason"])


if __name__ == "__main__":
    unittest.main()
