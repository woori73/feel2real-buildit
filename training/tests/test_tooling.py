"""Synthetic-only contract tests. No GPU, model downloads, production DB or paid API."""
import json
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from training.common import check_split, messages_for, parse_object, read_jsonl, require_revision, validate_record
from training.gguf.export import conversion_commands
from training.qlora.train import ResponseCollator, encode_response, load_config
from training.v0.generate import generate

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "problem-generator/wiki-backend-src"))
from backend.eval_harness import evaluate


def question():
    return {"type": "mc", "questionType": "grammar", "sourceText": "A is larger than B.",
            "stem": "Choose the correct answer.", "options": ["A", "B"], "answer": [1],
            "multi": False, "score": 1, "conditions": [], "answerFormat": "",
            "acceptedAnswers": [], "sampleAnswer": "", "gradingRule": ""}


def record(rid="r1", day="2026-12-01", group="g1", task="solve"):
    # 기존 정답률 테스트는 기준 정답(gold)과 비교하는 풀이 과제(solve)다. 생성 과제는 아래 GenerateTaskTests.
    return {"id": rid, "task": task, "instruction": "Create one question using the question contract.",
            "input": {"profile": "synthetic example"}, "gold": {"answer": [1]},
            "output": {"question": question(), "evidence_refs": [{"id": "s1", "quote": "larger"}]},
            "source_chunks": [{"id": "s1", "text": "A is larger than B."}],
            "metadata": {"domain": "education", "deidentified": True, "teacher_verified": True,
                         "training_allowed": True, "source_group": group, "exam_date": day}}


def prediction():
    return {"id": "r1", "question": question(), "generator_model": "model-a@revision",
            "evidence_refs": [{"id": "s1", "quote": "larger than B"}],
            "usage": {"prompt_tokens": 100, "completion_tokens": 20}, "latency_seconds": 2}


class MetricsTests(unittest.TestCase):
    def test_given_valid_prediction_when_evaluated_then_observed_metrics_are_correct(self):
        result = evaluate([record()], [prediction()])
        self.assertEqual(result["accuracy"]["value"], 1)
        self.assertEqual(result["evidence_match_rate"]["value"], 1)
        self.assertIsNone(result["auto_pass_audited_error_rate"]["value"])
        self.assertIsNone(result["independent_disagreement_rate"]["value"])
        self.assertEqual(result["mean_tokens"], 120)

    def test_given_missing_prediction_when_evaluated_then_not_dropped_from_denominator(self):
        result = evaluate([record()], [])
        self.assertEqual(result["accuracy"], {"numerator": 0, "denominator": 1, "value": 0})
        self.assertEqual(result["missing_predictions"], 1)
        self.assertIsNone(result["mean_tokens"])

    def test_given_wrong_or_missing_citation_when_evaluated_then_provenance_fails(self):
        for ref in [[], [{"id": "s2", "quote": "larger"}], [{"id": "s1", "quote": ""}],
                    [{"id": "s1", "quote": "fabricated"}]]:
            with self.subTest(ref=ref):
                pred = prediction(); pred["evidence_refs"] = ref
                self.assertEqual(evaluate([record()], [pred])["evidence_match_rate"]["value"], 0)

    def test_given_invalid_contract_when_evaluated_then_form_and_answer_fail(self):
        pred = prediction(); del pred["question"]["stem"]
        result = evaluate([record()], [pred])
        self.assertEqual(result["format_compliance_rate"]["value"], 0)
        self.assertEqual(result["accuracy"]["value"], 0)

    def test_given_same_model_when_rechecked_then_not_counted_as_independent(self):
        pred = prediction(); pred["verification"] = {"model": pred["generator_model"], "answer": [2]}
        result = evaluate([record()], [pred])
        self.assertIsNone(result["independent_disagreement_rate"]["value"])
        self.assertEqual(result["excluded_nonindependent_or_invalid_verifications"], 1)

    def test_given_different_model_when_answer_differs_then_disagreement_counted(self):
        pred = prediction(); pred["verification"] = {"model": "model-b", "answer": [2]}
        self.assertEqual(evaluate([record()], [pred])["independent_disagreement_rate"]["value"], 1)

    def test_given_audited_subset_when_evaluated_then_uses_reviewed_denominator(self):
        one, two = prediction(), prediction(); two["id"] = "r2"
        one.update(auto_pass=True, audit={"selected": True, "correct": False})
        two["auto_pass"] = True
        result = evaluate([record(), record("r2")], [one, two])
        self.assertEqual(result["auto_pass_audited_error_rate"], {"numerator": 1, "denominator": 1, "value": 1})
        self.assertEqual(result["unreviewed_auto_pass_count"], 1)

    def test_given_invalid_numbers_when_measured_then_not_treated_as_zero_latency(self):
        pred = prediction(); pred["latency_seconds"] = float("nan")
        pred["usage"]["prompt_tokens"] = True
        result = evaluate([record()], [pred])
        self.assertIsNone(result["mean_latency_seconds"])
        self.assertIsNone(result["mean_tokens"])

    def test_given_unexpected_ids_when_evaluated_then_fails(self):
        pred = prediction(); pred["id"] = "unrelated"
        with self.assertRaises(ValueError): evaluate([record()], [pred])
        with self.assertRaises(ValueError): evaluate([record()], [prediction(), prediction()])

    def test_given_short_answer_when_normalized_then_only_documented_variants_match(self):
        pred = prediction(); pred["question"].update(type="sa", sampleAnswer="  Hello   world ")
        gold = record(); gold["gold"] = {"acceptedAnswers": ["Hello world"]}
        self.assertEqual(evaluate([gold], [pred])["accuracy"]["value"], 1)
        pred["question"]["sampleAnswer"] = "hello world"
        self.assertEqual(evaluate([gold], [pred])["accuracy"]["value"], 0)


class GenerateTaskTests(unittest.TestCase):
    """생성 과제: 새 문항에는 기준 정답이 없으므로 교사 검수 결과로만 정답률을 센다."""

    def test_given_generated_question_without_review_when_evaluated_then_accuracy_unknown(self):
        # given: 형식은 맞지만 교사 검수 전인 생성 문항
        gold = record(task="generate"); del gold["gold"]
        # when
        result = evaluate([gold], [prediction()])
        # then: 기준 정답 번호와 비교하지 않고, 분모에서 빼서 따로 센다
        self.assertEqual(result["accuracy"], {"numerator": 0, "denominator": 0, "value": None})
        self.assertEqual(result["unreviewed_generated_count"], 1)
        self.assertEqual(result["format_compliance_rate"]["value"], 1)

    def test_given_teacher_review_when_evaluated_then_review_decides_accuracy(self):
        # given: 교사가 정답 키를 맞다/틀리다고 검수한 생성 문항 두 개
        right, wrong = prediction(), prediction(); wrong["id"] = "r2"
        right["review"] = {"answer_key_correct": True}
        wrong["review"] = {"answer_key_correct": False}
        # when
        result = evaluate([record(task="generate"), record("r2", task="generate")], [right, wrong])
        # then
        self.assertEqual(result["accuracy"], {"numerator": 1, "denominator": 2, "value": 0.5})
        self.assertEqual(result["unreviewed_generated_count"], 0)

    def test_given_error_row_when_evaluated_then_counted_as_format_and_accuracy_failure(self):
        # given: v0가 JSON이 깨져 error 행을 남긴 경우
        failed = {"id": "r1", "question": None, "error": "invalid_json", "generator_model": "model-a"}
        # when
        result = evaluate([record(task="generate")], [failed])
        # then
        self.assertEqual(result["format_compliance_rate"]["value"], 0)
        self.assertEqual(result["accuracy"], {"numerator": 0, "denominator": 1, "value": 0})

    def test_given_generate_row_without_gold_when_validated_then_ok_but_unknown_task_fails(self):
        # given / when / then
        row = record(task="generate"); del row["gold"]
        validate_record(row, evaluation=True)
        row["task"] = "translate"
        with self.assertRaises(ValueError): validate_record(row, evaluation=True)
        solve = record(); del solve["gold"]
        with self.assertRaises(ValueError): validate_record(solve, evaluation=True)


class DataTests(unittest.TestCase):
    def test_given_padded_batch_when_collated_then_only_padding_labels_are_ignored(self):
        fake_torch = SimpleNamespace(tensor=lambda values, dtype: values, long="long")
        features = [{"input_ids": [1, 2, 0], "attention_mask": [1, 1, 1], "labels": [-100, 2, 0]},
                    {"input_ids": [1, 3], "attention_mask": [1, 1], "labels": [-100, 3]}]
        with patch.dict(sys.modules, {"torch": fake_torch}):
            batch = ResponseCollator(0)(features)
        self.assertEqual(batch["labels"], [[-100, 2, 0], [-100, 3, -100]])
        self.assertEqual(batch["attention_mask"], [[1, 1, 1], [1, 1, 0]])

    def test_given_clean_temporal_split_when_checked_then_passes(self):
        check_split([record(day="2026-11-01")], [record("r2", group="g2")], "2026-12-01")

    def test_given_future_or_shared_sources_when_checked_then_fails(self):
        for train in [record(day="2026-12-01", group="g2"), record(day="2026-11-01")]:
            with self.subTest(train=train["metadata"]):
                with self.assertRaises(ValueError):
                    check_split([train], [record("r2")], "2026-12-01")

    def test_given_unapproved_or_non_education_training_when_checked_then_fails(self):
        for key, value in [("domain", "counselling"), ("deidentified", False),
                           ("teacher_verified", False), ("training_allowed", False)]:
            row = record(); row["metadata"][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): validate_record(row, training=True)

    def test_given_duplicate_jsonl_ids_when_loaded_then_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "data.jsonl"
            path.write_text(json.dumps(record()) + "\n" + json.dumps(record()) + "\n")
            with self.assertRaises(ValueError): read_jsonl(path)

    def test_given_gold_when_prompt_built_then_answer_not_leaked(self):
        row = record(); row["output"] = "SECRET_GOLD_ANSWER"; row["gold"] = {"answer": "SECRET_GOLD_ANSWER"}
        self.assertNotIn("SECRET_GOLD_ANSWER", json.dumps(messages_for(row)))

    def test_given_floating_revision_when_validated_then_rejected(self):
        with self.assertRaises(ValueError): require_revision("main")
        self.assertEqual(require_revision("a" * 40), "a" * 40)

    def test_given_template_when_encoded_then_prompt_is_masked_and_answer_is_preserved(self):
        class Tokenizer:
            def apply_chat_template(self, messages, tokenize, add_generation_prompt):
                text = "".join(m["role"] + ":" + m["content"] + "|" for m in messages)
                if add_generation_prompt: text += "assistant:"
                return list(text.encode())
        encoded = encode_response(Tokenizer(), record(), 10000)
        start = next(i for i, v in enumerate(encoded["labels"]) if v != -100)
        self.assertTrue(all(v == -100 for v in encoded["labels"][:start]))
        self.assertEqual(encoded["labels"][start:], encoded["input_ids"][start:])
        self.assertIsNone(encode_response(Tokenizer(), record(), 1))

    def test_given_prefix_mismatch_when_encoded_then_fails_instead_of_mislabeling(self):
        class Tokenizer:
            def apply_chat_template(self, messages, tokenize, add_generation_prompt):
                return [1, 2] if add_generation_prompt else [3, 4, 5]
        with self.assertRaises(ValueError): encode_response(Tokenizer(), record(), 100)

    def test_given_train_cli_dry_run_when_executed_then_gpu_is_not_loaded(self):
        with tempfile.TemporaryDirectory() as folder:
            train, holdout = Path(folder) / "train.jsonl", Path(folder) / "eval.jsonl"
            train.write_text(json.dumps(record(day="2026-11-01")) + "\n")
            holdout.write_text(json.dumps(record("r2", group="g2")) + "\n")
            result = subprocess.run([sys.executable, "-m", "training.qlora.train", "--train", str(train),
                     "--eval", str(holdout), "--cutoff", "2026-12-01", "--revision", "a" * 40,
                     "--output", str(Path(folder) / "out"), "--dry-run"], capture_output=True, text=True, cwd=ROOT)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse(json.loads(result.stdout)["executed"])
            self.assertFalse((Path(folder) / "out").exists())

    def test_given_conversion_paths_with_spaces_when_built_then_no_shell_interpolation(self):
        commands = conversion_commands("/tmp/llama checkout", "/tmp/merged model", "/tmp/output model")
        self.assertEqual(commands[0][2], "/tmp/merged model")
        self.assertEqual(commands[1][-1], "Q4_K_M")
        # 원본 가중치(bf16) 정밀도를 그대로 유지해 변환한다
        self.assertEqual(commands[0][commands[0].index("--outtype") + 1], "bf16")
        self.assertTrue(commands[1][1].endswith("model-bf16.gguf"))

    def test_given_adapter_manifest_when_export_dry_run_then_no_merge_is_executed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); adapter = root / "adapter"; adapter.mkdir()
            llama = root / "llama"; (llama / "build/bin").mkdir(parents=True)
            (llama / "convert_hf_to_gguf.py").write_text("raise AssertionError('must not run')")
            (llama / "build/bin/llama-quantize").write_text("not executable")
            (adapter / "run_manifest.json").write_text(json.dumps({"status": "completed",
                "base_model": "Qwen/Qwen2.5-7B-Instruct", "revision": "a" * 40}))
            command = [sys.executable, "-m", "training.gguf.export", "--model", "Qwen/Qwen2.5-7B-Instruct",
                       "--revision", "a" * 40, "--adapter", str(adapter), "--llama-cpp-dir", str(llama),
                       "--llama-cpp-revision", "b" * 40, "--output", str(root / "out"), "--dry-run"]
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse(json.loads(result.stdout)["executed"])
            self.assertFalse((root / "out").exists())

    def test_given_wrong_base_manifest_when_export_planned_then_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "run_manifest.json").write_text(json.dumps({"status": "completed",
                "base_model": "different-model", "revision": "a" * 40}))
            result = subprocess.run([sys.executable, "-m", "training.gguf.export", "--model", "Qwen/Qwen2.5-7B-Instruct",
                "--revision", "a" * 40, "--adapter", str(root), "--llama-cpp-dir", str(root),
                "--llama-cpp-revision", "b" * 40, "--output", str(root / "out"), "--dry-run"],
                cwd=ROOT, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("adapter manifest does not match", result.stderr)

    def test_given_eval_cli_when_executed_then_json_has_hashes_and_counts(self):
        with tempfile.TemporaryDirectory() as folder:
            gold, pred, out = [Path(folder) / n for n in ["gold.jsonl", "pred.jsonl", "result.json"]]
            gold.write_text(json.dumps(record()) + "\n"); pred.write_text(json.dumps(prediction()) + "\n")
            result = subprocess.run([sys.executable, "-m", "training.eval.run", "--gold", str(gold),
                     "--predictions", str(pred), "--output", str(out), "--model-version", "synthetic",
                     "--retrieval-mode", "hybrid"], capture_output=True, text=True, cwd=ROOT)
            self.assertEqual(result.returncode, 0, result.stderr)
            data = json.loads(out.read_text())
            self.assertEqual(data["accuracy"]["denominator"], 1)
            self.assertEqual(len(data["gold_sha256"]), 64)

    def test_given_empty_predictions_when_cli_evaluated_then_all_missing_counted(self):
        with tempfile.TemporaryDirectory() as folder:
            gold, pred, out = [Path(folder) / n for n in ["gold.jsonl", "pred.jsonl", "result.json"]]
            gold.write_text(json.dumps(record()) + "\n"); pred.write_text("")
            result = subprocess.run([sys.executable, "-m", "training.eval.run", "--gold", str(gold),
                     "--predictions", str(pred), "--output", str(out), "--model-version", "synthetic",
                     "--retrieval-mode", "hybrid"], capture_output=True, text=True, cwd=ROOT)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(out.read_text())["missing_predictions"], 1)

    def test_given_remote_endpoint_without_opt_in_when_cli_runs_then_rejected_before_data_load(self):
        result = subprocess.run([sys.executable, "-m", "training.v0.generate", "--input", "nonexistent",
                 "--output", "nonexistent", "--model", "fixture", "--model-revision", "a" * 40,
                 "--endpoint", "https://example.com/v1"], capture_output=True, text=True, cwd=ROOT)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("--allow-remote", result.stderr)


class HttpTests(unittest.TestCase):
    def test_given_no_sources_when_generated_then_abstains_without_network(self):
        row = record(); row["source_chunks"] = []
        with patch("training.v0.generate.build_opener", side_effect=AssertionError("network used")):
            result = generate(row, "invalid", "fixture", "a" * 64)
        self.assertTrue(result["abstained"])
        self.assertNotIn("latency_seconds", result)

    def test_given_local_chat_server_when_generated_then_protocol_and_usage_are_recorded(self):
        observed = []
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                observed.append(json.loads(self.rfile.read(int(self.headers["Content-Length"]))))
                response = {"model": "fixture", "usage": {"prompt_tokens": 11, "completion_tokens": 7},
                            "choices": [{"finish_reason": "length" if observed[-1]["model"] == "truncated" else "stop", "message": {"content": json.dumps(
                                {"question": question(), "evidence_refs": [{"id": "s1", "quote": "larger"}]})}}]}
                body = json.dumps(response).encode()
                self.send_response(200); self.send_header("Content-Length", str(len(body)))
                self.end_headers(); self.wfile.write(body)
            def log_message(self, *args): pass
        with HTTPServer(("127.0.0.1", 0), Handler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True); worker.start()
            try:
                result = generate(record(), f"http://127.0.0.1:{server.server_port}/v1", "fixture", "a" * 64)
                self.assertEqual(result["usage"]["completion_tokens"], 7)
                self.assertEqual(result["question"]["answer"], [1])
                self.assertEqual(observed[0]["temperature"], 0)
                self.assertNotIn("gold", observed[0])
                truncated = generate(record(), f"http://127.0.0.1:{server.server_port}/v1", "truncated", "a" * 64)
                self.assertEqual(truncated["error"], "truncated")
                self.assertIsNone(truncated["question"])
            finally:
                server.shutdown(); worker.join()

    def test_given_broken_json_then_good_response_when_generated_then_error_row_and_next_succeeds(self):
        # given: 첫 응답은 JSON이 깨지고, 두 번째 응답은 정상인 로컬 서버
        replies = ["인과관계 검증 완료", json.dumps({"question": question(), "evidence_refs": []})]
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                self.rfile.read(int(self.headers["Content-Length"]))
                body = json.dumps({"model": "fixture", "usage": {"prompt_tokens": 5, "completion_tokens": 3},
                                   "choices": [{"finish_reason": "stop", "message": {"content": replies.pop(0)}}]}).encode()
                self.send_response(200); self.send_header("Content-Length", str(len(body)))
                self.end_headers(); self.wfile.write(body)
            def log_message(self, *args): pass
        with HTTPServer(("127.0.0.1", 0), Handler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True); worker.start()
            try:
                url = f"http://127.0.0.1:{server.server_port}/v1"
                # when
                first = generate(record(), url, "fixture", "a" * 64)
                second = generate(record("r2"), url, "fixture", "a" * 64)
            finally:
                server.shutdown(); worker.join()
        # then: 깨진 응답은 멈추지 않고 형식 실패 행으로 남고, 다음 문항은 정상 생성된다
        self.assertEqual(first["error"], "invalid_json")
        self.assertEqual(first["usage"]["completion_tokens"], 3)
        self.assertEqual(second["question"]["answer"], [1])
        self.assertNotIn("error", second)

    def test_given_non_json_response_when_parsed_then_not_returned_as_success(self):
        with self.assertRaises(ValueError): parse_object("인과관계 검증 완료")
        self.assertEqual(parse_object('```json\n{"question": {}}\n```'), {"question": {}})


if __name__ == "__main__":
    unittest.main()
