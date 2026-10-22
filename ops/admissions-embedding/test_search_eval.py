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
