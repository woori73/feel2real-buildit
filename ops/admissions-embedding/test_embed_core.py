import unittest
import embed_core as c


class EmbedCoreTest(unittest.TestCase):
    def test_given_valid_body_when_parse_then_returns_texts_and_default_task(self):
        # given
        body = '{"texts":["가나다","라마바"]}'
        # when
        texts, task = c.parse_request(body, 8, 100)
        # then
        self.assertEqual(texts, ["가나다", "라마바"])
        self.assertEqual(task, "document")

    def test_given_long_text_when_parse_then_truncated_to_max_chars(self):
        texts, _ = c.parse_request('{"texts":["' + "가" * 50 + '"]}', 8, 10)
        self.assertEqual(len(texts[0]), 10)

    def test_given_bad_bodies_when_parse_then_bad_request(self):
        for body in ["not json", "[]", '{"texts":[]}', '{"texts":[" "]}', '{"texts":[1]}',
                     '{"texts":["a"],"task":"x"}', '{"texts":["a","b","c"]}']:
            with self.subTest(body=body):
                with self.assertRaises(c.BadRequest):
                    c.parse_request(body, 2, 100)

    def test_given_task_when_prefix_then_matches_model_prompt(self):
        self.assertEqual(c.with_prefix(["q"], "query"), ["task: search result | query: q"])
        self.assertEqual(c.with_prefix(["d"], "document"), ["title: none | text: d"])

    def test_given_vector_when_normalize_then_unit_length(self):
        v = c.l2_normalize([3.0, 4.0])
        self.assertAlmostEqual(sum(x * x for x in v), 1.0)

    def test_given_zero_vector_when_normalize_then_error(self):
        with self.assertRaises(ValueError):
            c.l2_normalize([0.0, 0.0])

    def test_given_vectors_when_cosine_then_expected(self):
        self.assertAlmostEqual(c.cosine([1, 0], [1, 0]), 1.0)
        self.assertAlmostEqual(c.cosine([1, 0], [0, 1]), 0.0)

    def test_given_separable_sims_when_suggest_then_midpoint_else_none(self):
        self.assertEqual(c.suggest_floor([0.6, 0.7], [0.2, 0.3]), 0.45)
        self.assertIsNone(c.suggest_floor([0.3, 0.7], [0.2, 0.4]))


if __name__ == "__main__":
    unittest.main()
