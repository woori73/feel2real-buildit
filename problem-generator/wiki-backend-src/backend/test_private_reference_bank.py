import json
import sys
import unittest
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import private_reference_bank as bank


def prompt(grade, qno, kind):
    marker={"questionWiki":True,"grade":grade,"questionNo":qno,"type":kind}
    return "QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST:"+json.dumps(marker,separators=(",",":"))


class PrivateReferenceCorpusTest(unittest.TestCase):
    def test_counts_schema_and_validation(self):
        expected={1:255,2:243}
        for grade,count in expected.items():
            manifest=json.loads((HERE/"private-reference"/f"grade{grade}-manifest.json").read_text(encoding="utf-8"))
            records=bank._load_grade(grade)
            self.assertEqual(count,manifest["recordCount"])
            self.assertEqual(count,len(records))
            self.assertEqual(Counter({"PASS":count}),Counter(r["validation"]["status"] for r in records))
            for r in records:
                for key in ("grade","sourceQuestionNo","type","instruction","rawText","rawLayout","provenance"):
                    self.assertTrue(r.get(key),f"variant {r.get('variantIndex')} missing {key}")

    def test_exact_source_and_type_priority(self):
        for qno in (30,31):
            refs=bank.select(prompt(2,qno,"vocabulary"))
            self.assertTrue(refs)
            self.assertTrue(all(r["grade"]==2 and qno in r["sourceQuestionNos"] for r in refs))
            if any(r["type"]=="vocabulary" for r in bank._same_question(2,qno)):
                self.assertTrue(all(r["type"]=="vocabulary" for r in refs))

    def test_fallback_never_crosses_source_or_grade(self):
        refs=bank.select(prompt(2,31,"nonexistent-type"))
        self.assertTrue(refs)
        self.assertTrue(all(r["grade"]==2 and r["sourceQuestionNo"]==31 for r in refs))
        self.assertFalse(any(r in bank.select(prompt(1,31,"nonexistent-type")) for r in refs))

    def test_type_structures(self):
        records=bank._load_grade(1)+bank._load_grade(2)
        for r in records:
            if r["type"] in ("grammar","vocabulary") and "밑줄" in r["instruction"]:
                self.assertGreaterEqual(len({x["mark"] for x in r["testedSegments"]}),3)
            if r["type"]=="blank": self.assertTrue(r["blank"]["present"])
            if r["type"]=="order": self.assertEqual(set("ABC"),set(r["blocks"]))
            if r["type"]=="insertion":
                self.assertTrue(r["givenSentence"])
                self.assertEqual(set(range(1,6)),{x["no"] for x in r["insertionPoints"]})


if __name__ == "__main__": unittest.main()
