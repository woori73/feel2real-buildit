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


def record(rid="r1", day="2026-12-01", group="g1"):
    return {"id": rid, "instruction": "Create one question using the question contract.",
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
                with self.assertRaises(ValueError):
                    generate(record(), f"http://127.0.0.1:{server.server_port}/v1", "truncated", "a" * 64)
            finally:
                server.shutdown(); worker.join()

    def test_given_non_json_response_when_parsed_then_not_returned_as_success(self):
        with self.assertRaises(ValueError): parse_object("인과관계 검증 완료")
        self.assertEqual(parse_object('```json\n{"question": {}}\n```'), {"question": {}})


if __name__ == "__main__":
    unittest.main()
