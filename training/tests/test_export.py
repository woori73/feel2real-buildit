"""T07 내보내기 도구와 트랙별 분리·원문 겹침 지표 테스트(합성 자료, GPU·DB 없음)."""
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "problem-generator/wiki-backend-src"))
from training.common import check_split, parse_cutoff  # noqa: E402
from training.export import sql_rows  # noqa: E402
from training.export.text_utils import fix_insertion_marks, overlap, split_question  # noqa: E402
from backend.eval_harness import evaluate  # noqa: E402

Q = {"type": "mc", "questionType": "t", "sourceText": "s", "stem": "Choose.", "options": ["a", "b"], "answer": [1],
     "multi": False, "score": 1, "conditions": [], "answerFormat": "", "acceptedAnswers": [], "sampleAnswer": "",
     "gradingRule": ""}


def row(rid, track, group, date, text, train=True):
    r = {"id": rid, "instruction": "i", "input": "x", "source_chunks": [{"id": "c", "text": text}],
         "metadata": {"domain": "education", "deidentified": True, "source_group": group, "exam_date": date,
                      "teacher_verified": True, "training_allowed": True, "track": track}}
    if train:
        r["output"] = {"question": Q, "evidence_refs": []}
    return r


PASS_A = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron"
PASS_B = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen"


class SplitTest(unittest.TestCase):
    def test_given_track_rules_when_naesin_is_group_then_dates_are_not_compared(self):
        # given: 내신 학습 행 날짜(12월)가 수능 평가 cutoff(9월)보다 늦다
        train = [row("t1", "naesin", "naesin:A", "2026-12-10", PASS_A), row("t2", "csat", "kice:6", "2026-06-04", PASS_B)]
        ev = [row("e1", "naesin", "naesin:B", "2026-12-10", "z " * 3 + "unique words for the holdout school passage here now", False),
              row("e2", "csat", "kice:9", "2026-09-03", "another separate passage that does not repeat any training text at all", False)]
        # when / then: 트랙별 규칙이면 통과, 날짜 하나면 실패
        check_split(train, ev, "csat=2026-09-01,naesin=group")
        with self.assertRaises(ValueError):
            check_split(train, ev, "2026-09-01")

    def test_given_same_passage_under_other_group_then_rejected(self):
        train = [row("t1", "naesin", "naesin:A", "2026-12-10", PASS_A)]
        ev = [row("e1", "naesin", "naesin:B", "2026-12-10", PASS_A, False)]
        with self.assertRaises(ValueError):
            check_split(train, ev, "naesin=group")

    def test_given_csat_eval_before_cutoff_then_rejected(self):
        train = [row("t1", "csat", "g1", "2026-06-04", PASS_A)]
        ev = [row("e1", "csat", "g2", "2026-08-01", PASS_B, False)]
        with self.assertRaises(ValueError):
            check_split(train, ev, "csat=2026-09-01")

    def test_given_unknown_track_then_rejected(self):
        with self.assertRaises(ValueError):
            check_split([row("t1", "sat", "g1", "2026-01-01", PASS_A)], [], "csat=2026-09-01")

    def test_parse_cutoff(self):
        self.assertEqual(parse_cutoff("2026-12-01"), {"*": "2026-12-01"})
        self.assertEqual(parse_cutoff("csat=2026-09-01,naesin=group"), {"csat": "2026-09-01", "naesin": "group"})
        with self.assertRaises(ValueError):
            parse_cutoff("csat=")


class TextUtilsTest(unittest.TestCase):
    def test_split_question_with_ocr_marks(self):
        n, stem, body, opts = split_question(
            "21. 다음 글의 요지로 가장 적절한 것은?\nText line one.\n01) one\n(2) two\n@ three\n(4) four\n(S) five")
        self.assertEqual(n, 21)
        self.assertEqual(stem, "다음 글의 요지로 가장 적절한 것은?")
        self.assertEqual(opts, ["one", "two", "three", "four", "five"])
        self.assertEqual(body, "Text line one.")

    def test_split_question_without_choices(self):
        self.assertIsNone(split_question("3. 빈칸에 알맞은 말을 쓰시오.\nI like it.")[3])

    def test_fix_insertion_marks_needs_exactly_five(self):
        text, ok = fix_insertion_marks("a ( @) ) b ( 2) ) c ( (3) ) d ( 4) ) e ( © ) f")
        self.assertTrue(ok)
        self.assertEqual(text, "a ( ① ) b ( ② ) c ( ③ ) d ( ④ ) e ( ⑤ ) f")
        self.assertFalse(fix_insertion_marks("a ( 2) ) b")[1])

    def test_overlap(self):
        self.assertEqual(overlap(PASS_A, PASS_A), 1.0)
        self.assertEqual(overlap(PASS_A, PASS_B), 0.0)
        self.assertIsNone(overlap("short", PASS_A))


class SqlRowsTest(unittest.TestCase):
    def test_given_migration_when_exam_blocks_then_questions_join_exam_by_variable(self):
        sql = ("INSERT INTO tp_exams (exam_code, title, note) VALUES ('X-1', 'it''s', NULL);\n"
               "SET @e1 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'X-1');\n"
               "INSERT INTO tp_questions (exam_id, prompt, points) VALUES\n(@e1, 'a\\nb', 2),\n(@e1, '(c, d)', 3);\n")
        with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f:
            f.write(sql)
        blocks = sql_rows.exam_blocks(f.name)
        self.assertEqual(blocks["X-1"]["exam"]["title"], "it's")
        self.assertIsNone(blocks["X-1"]["exam"]["note"])
        self.assertEqual([q["prompt"] for q in blocks["X-1"]["questions"]], ["a\nb", "(c, d)"])
        self.assertEqual(blocks["X-1"]["questions"][1]["points"], 3)


class OverlapMetricTest(unittest.TestCase):
    def test_given_copied_original_when_evaluate_then_copied_count(self):
        gold = [{"id": "g1", "task": "generate", "source_chunks": [{"id": "c", "text": PASS_B}],
                 "reference_question": PASS_A}]
        copied = dict(Q, stem=PASS_A, sourceText=PASS_B)
        res = evaluate(gold, [{"id": "g1", "question": copied, "evidence_refs": []}])
        self.assertEqual(res["original_question_overlap"]["copied_count"], 1)
        self.assertEqual(res["source_passage_overlap"]["mean"], 1.0)
        fresh = dict(Q, stem="a brand new stem written for this test only today", sourceText="x")
        res2 = evaluate(gold, [{"id": "g1", "question": fresh, "evidence_refs": []}])
        self.assertEqual(res2["original_question_overlap"]["copied_count"], 0)


if __name__ == "__main__":
    unittest.main()
