import os, sys, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import shotlist as sl


def script(lines, lang="ko"):
    return {"title": "t", "lang": lang,
            "characters": [{"id": "a"}, {"id": "b"}],
            "scenes": [{"setting": "s", "lines": lines}]}


class ShotlistTest(unittest.TestCase):
    def test_given_short_line_when_build_then_one_shot(self):
        # given
        s = script([{"speaker": "a", "text": "안녕하세요"}])
        # when
        shots = sl.build_shotlist(s)
        # then
        self.assertEqual(len(shots), 1)
        self.assertEqual(shots[0]["shot_id"], "S01L01P1")

    def test_given_long_line_when_build_then_split_under_limit(self):
        s = script([{"speaker": "a", "text": "가" * 130}])  # 약 21.7초
        shots = sl.build_shotlist(s)
        self.assertGreater(len(shots), 1)
        self.assertTrue(all(x["seconds"] <= sl.MAX_SHOT_SECONDS for x in shots))

    def test_given_unknown_speaker_when_validate_then_error(self):
        with self.assertRaises(sl.ScriptError):
            sl.validate_script(script([{"speaker": "zzz", "text": "x"}]))

    def test_given_duplicate_character_ids_when_validate_then_error(self):
        s = script([{"speaker": "a", "text": "x"}])
        s["characters"].append({"id": "a"})
        with self.assertRaises(sl.ScriptError):
            sl.validate_script(s)

    def test_given_unsupported_language_when_estimate_then_error(self):
        with self.assertRaises(sl.ScriptError):
            sl.estimate_seconds("hi", "jp")

    def test_given_sample_file_when_build_then_total_seconds_positive(self):
        path = os.path.join(os.path.dirname(__file__), "..", "samples", "grammar_lecture_sample.json")
        shots = sl.build_shotlist(sl.load_script(path))
        self.assertGreater(sl.total_seconds(shots), 0)


if __name__ == "__main__":
    unittest.main()
