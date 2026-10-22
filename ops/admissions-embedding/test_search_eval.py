import unittest

import search_eval


class TopRankingTest(unittest.TestCase):
    def test_given_items_when_ranking_then_highest_dot_product_first(self):
        # given
        items = [
            {"id": 1, "kind": "type", "title": "A", "vec": (1.0, 0.0)},
            {"id": 2, "kind": "type", "title": "B", "vec": (0.0, 1.0)},
            {"id": 3, "kind": "schedule", "title": "C", "vec": (0.6, 0.8)},
        ]
        # when
        ranked = search_eval.top(items, (0.0, 1.0), k=2)
        # then
        self.assertEqual([it["id"] for _, it in ranked], [2, 3])
        self.assertAlmostEqual(ranked[0][0], 1.0)

    def test_given_quote_chars_when_quoting_then_escaped_for_mysql_cfg(self):
        # given / when
        quoted = search_eval.quote('pa"ss\\word')
        # then
        self.assertEqual(quoted, '"pa\\"ss\\\\word"')


if __name__ == "__main__":
    unittest.main()


class GoldenQuestionSetTest(unittest.TestCase):
    REQUIRED = [
        "연대 교과전형 수능최저 있어?",
        "고대 2027 수시 지역균형 전형은?",
        "2025학년도 서울대 수시 지역균형 수능최저",
        "카이스트 수시 모집요강 알려주세요",
        "중앙대 수시 일정과 모집요강",
        "연세대학교 2027학년도 학생부교과 추천형 지원자격 알려주세요",
        "성균관대 2027학년도 수시 원서접수 일정 언제예요",
    ]

    def test_given_golden_file_when_loading_then_all_cases_are_valid_and_cover_required_questions(self):
        # given / when
        cases = search_eval.load_cases()
        questions = [c["question"] for c in cases]
        # then
        for q in self.REQUIRED:
            self.assertIn(q, questions)
        self.assertEqual({c["category"] for c in cases}, {"relevant", "irrelevant", "no_evidence"})

    def test_given_ranked_titles_when_checking_hit_then_all_required_substrings_must_be_in_one_title(self):
        # given
        ranked = [(0.7, {"title": "연세대학교 2027학년도 학생부교과전형[추천형]"}), (0.6, {"title": "서울대학교 지역균형"})]
        # when / then
        self.assertTrue(search_eval.case_hit(ranked, {"expected_title_contains": ["연세대학교", "추천형"]}))
        self.assertFalse(search_eval.case_hit(ranked, {"expected_title_contains": ["연세대학교", "지역균형"]}))
        self.assertIsNone(search_eval.case_hit(ranked, {"expected_title_contains": []}))
