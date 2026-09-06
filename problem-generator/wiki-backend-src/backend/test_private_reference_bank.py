import json
import sys
import unittest
from unittest.mock import patch
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import private_reference_bank as bank


def records_for_grade(grade):
    manifest=bank._load_manifest(grade)
    records=[]
    for shard in manifest.get("shards") or []:
        q=41 if str(shard["sourceQuestionNo"])=="41-42" else int(shard["sourceQuestionNo"])
        records.extend(bank._load_question(grade,q))
    return tuple(records)


def prompt(grade, qno, kind):
    marker={"questionWiki":True,"grade":grade,"questionNo":qno,"type":kind}
    return "QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST:"+json.dumps(marker,separators=(",",":"))


class PrivateReferenceCorpusTest(unittest.TestCase):
    def test_counts_schema_and_validation(self):
        expected={1:255,2:243}
        for grade,count in expected.items():
            manifest=json.loads((HERE/"private-reference"/f"grade{grade}-manifest.json").read_text(encoding="utf-8"))
            records=records_for_grade(grade)
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
        records=records_for_grade(1)+records_for_grade(2)
        for r in records:
            if r["type"] in ("grammar","vocabulary") and "밑줄" in r["instruction"]:
                self.assertEqual(set("①②③④⑤"),{x["mark"] for x in r["testedSegments"]})
                self.assertTrue(all(x.get("page") and x.get("wordBoxes") and x.get("underlineRects") for x in r["testedSegments"]))
            if r["type"]=="blank":
                self.assertTrue(r["blank"]["present"])
                self.assertTrue(r["blank"]["rects"] or r["blank"]["representations"])
            if r["type"]=="order":
                self.assertEqual(set("ABC"),set(r["blocks"]))
                self.assertEqual([1,2,3,4,5],[x["no"] for x in r["choices"]])
            if r["type"]=="insertion":
                self.assertTrue(r["givenSentence"])
                self.assertEqual([1,2,3,4,5],[x["no"] for x in r["insertionPoints"]])
            self.assertTrue((r.get("answer") or {}).get("raw"))

    def test_exact_shard_resolution_and_no_q30_read_for_q31(self):
        expected={(2,30):"g2-q30.json.gz",(2,31):"g2-q31.json.gz",
                  (1,30):"g1-q30.json.gz",(1,31):"g1-q31.json.gz",
                  (2,41):"g2-q41-42.json.gz",(2,42):"g2-q41-42.json.gz"}
        for key,name in expected.items(): self.assertEqual(name,bank.resolve_shard(*key).name)
        original=bank.gzip.open
        bank._load_question.cache_clear(); bank._same_question.cache_clear()
        with patch.object(bank.gzip,"open",wraps=original) as opened:
            bank.select(prompt(2,31,"vocabulary"))
            paths=[Path(call.args[0]).name for call in opened.call_args_list]
        self.assertEqual(["g2-q31.json.gz"],paths)


if __name__ == "__main__": unittest.main()
