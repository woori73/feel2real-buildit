import tempfile
import unittest
from pathlib import Path

from training.newstart.build_dataset import PRIVATE, prepare, write_rows
from training.newstart.counsel_eval import evaluate


def sample(i, training=False):
    group = f"case-{i % 100}" if training else "heldout"
    key = f"{'train' if training else 'eval'}-{i}"
    return dict(id=key, input=f"{key} 서류를 확인할 수 있나요?", instruction="제공 자료로 안내하세요.",
                output="자료에 없습니다. 보증재단에 문의하세요.",
                source_chunks=[dict(id="s1", group=group, month="2026-09", text="제도 안내"),
                               dict(id="s2", group="other-" + group, month="2026-09", text="다른 사례는 3일")],
                metadata=dict(domain="newstart", deidentified=True, source_group=group, category="자료없음",
                              gold_basis="archived_case", human_checked=False, training_allowed=False),
                gold=dict(facts=[], cite=[], abstain=True))


class NewstartTests(unittest.TestCase):
    def test_irrelevant_source_supports_neither_citation_nor_deadline(self):
        row = sample(1)
        def score(answer):
            return evaluate([row], [dict(id=row["id"], answer=answer)])
        self.assertEqual(score(row["output"])["citation_match"], 1)
        result = score("자료에 없지만 재단이 3일 안에 처리합니다. [s2]")
        self.assertEqual(result["abstain_correct"], 0)
        self.assertEqual(result["fabricated_amounts_deadlines"], 1)
        self.assertEqual(result["citation_match"], 0)

    def test_trial_checks_review_and_disjoint_cases_before_export(self):
        with tempfile.TemporaryDirectory() as d:
            train, gold, output = (Path(d) / name for name in ("train", "gold", "out"))
            rows = [sample(i, training=True) for i in range(400)]
            write_rows(train, rows)
            write_rows(gold, [sample(i) for i in range(150)])
            self.assertEqual(prepare(gold, train, "trial")["distinct_cases"], 100)
            with self.assertRaisesRegex(ValueError, "review"):
                prepare(gold, train, "trial", output)
            self.assertFalse(output.exists())
            prepare(gold, train, "trial", output, "synthetic unit test")
            self.assertEqual(len(output.read_text().splitlines()), 400)
            with self.assertRaisesRegex(ValueError, "800"):
                prepare(gold, train, "main")
            rows[0]["source_chunks"][1]["group"] = "heldout"
            write_rows(train, rows)
            with self.assertRaisesRegex(ValueError, "overlap"):
                prepare(gold, train, "trial")

    def test_given_contact_or_account_when_scanned_then_exclude(self):
        for example in ("010-0000-0000", "sample@example.com", "123-456-789012", "가상로 12"):
            self.assertRegex(example, PRIVATE)
        self.assertNotRegex("일률적으로 70%를 적용할 수 없습니다.", PRIVATE)
