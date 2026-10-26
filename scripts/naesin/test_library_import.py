import argparse
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import import_library as importer
import library_inventory as inventory

class LibraryImportTest(unittest.TestCase):
    def item(self,name):
        return {"kind":"file","id":name,"file_id":name,"library_file_id":"lib-"+name,"name":name,"path":"/"+name,"size_bytes":6}
    def test_middle_and_high_are_explicit_and_shared_ambiguous_material_is_held(self):
        self.assertEqual("MIDDLE",inventory.classify(self.item("신구중3_4회_정답.html"))["division"])
        self.assertEqual("HIGH",inventory.classify(self.item("2026_고2_학력평가.hwp"))["division"])
        self.assertEqual("고등공통",inventory.classify(self.item("수능하프_88회.hwp"))["grade"])
        self.assertEqual("UNCLASSIFIED",inventory.classify(self.item("영어문법_공통자료.json"))["division"])
        self.assertEqual("UNCLASSIFIED",inventory.label("중1 고1 공통문법")[0])
    def test_hash_duplicates_keep_aliases_and_do_not_cross_school_divisions(self):
        with tempfile.TemporaryDirectory() as tmp:
            for n in ["신구중3_4회_문제지.txt","신구중3_4회_정답.txt","고1_4회_문제지.txt"]:Path(tmp,n).write_bytes(b"source")
            items=[self.item(n) for n in ["신구중3_4회_문제지.txt","신구중3_4회_정답.txt","고1_4회_문제지.txt"]]
            result=inventory.build(items,tmp)
            self.assertEqual(1,result["summary"]["MIDDLE"]["duplicate_excluded"])
            self.assertEqual(0,result["summary"]["HIGH"]["duplicate_excluded"])
            self.assertFalse(any(r["approved"] for r in result["records"]))
            self.assertEqual(0,result["production_registered"])
    def test_materialized_identity_wins_over_colliding_canonical_filename(self):
        import os
        with tempfile.TemporaryDirectory() as tmp:
            canonical=Path(tmp,"고1_원본.txt");canonical.write_bytes(b"wrong!")
            actual=Path(tmp,"retry/고1_원본.txt");actual.parent.mkdir();actual.write_bytes(b"source")
            item=self.item(canonical.name);os.setxattr(actual,"user.library-file-id",item["library_file_id"].encode())
            r=inventory.build([item],tmp)["records"][0]
            self.assertEqual("retry/고1_원본.txt",r["local_relative_path"])
            self.assertEqual(importer.sha_file(actual),r["sha256"])
    def test_partial_import_retry_adds_missing_source_to_new_handoff_without_losing_old_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            a=Path(tmp,"고1_학습지_a.txt");b=Path(tmp,"고1_학습지_b.txt");a.write_bytes(b"first!");b.write_bytes(b"second")
            manifest=inventory.build([self.item(a.name),self.item(b.name)],tmp)
            m=Path(tmp,"manifest.json");m.write_text(json.dumps(manifest))
            args=argparse.Namespace(manifest=str(m),originals=tmp,base_url="http://127.0.0.1:8080",execute=True,production_authorized=False,deployment_sha=None,token_file=None,journal=str(Path(tmp,"journal.json")))
            class FakeApi:
                def __init__(self):self.originals={};self.handoffs={};self.fail_once=True
                def verify(self,file_id,division,project,expected):
                    if self.originals[file_id]!=expected:raise AssertionError("Bad reread")
                def request(self,path,division,project=None,payload=None,raw=None,query=None):
                    if path=="projects":return {"id":"isolated-project"}
                    if path=="raw-files":
                        if raw.name==a.name and self.fail_once:self.fail_once=False;raise OSError("Injected failure")
                        fid=raw.name;sha=importer.sha_file(raw);self.originals[fid]=sha
                        return {"file":{"id":fid,"sha256":sha},"revision":{"id":"original-"+fid},"duplicate":False}
                    if path=="state":return {"revisions":[]}
                    if path=="work":
                        rid="handoff-"+str(len(self.handoffs));self.handoffs[rid]=payload;return {"id":rid}
                    if path.startswith("revisions/"):return {"revision":{"id":path.split("/")[1]}}
                    raise AssertionError("Unexpected endpoint: "+path)
            api=FakeApi()
            with patch.object(importer,"Api",return_value=api),patch.dict("os.environ",{"F2R_IMPORT_BEARER_TOKEN":"isolated-test"}):
                self.assertEqual(1,importer.run(args)["summary"]["HIGH"]["failed"])
                old=copy.deepcopy(api.handoffs)
                self.assertEqual(0,importer.run(args)["summary"]["HIGH"]["failed"])
                self.assertEqual(2,len(api.originals));self.assertEqual(2,len(api.handoffs))
                self.assertEqual(old["handoff-0"],api.handoffs["handoff-0"])
                self.assertEqual(2,len(api.handoffs["handoff-1"]["sourceFileIds"]))
                importer.run(args);self.assertEqual(2,len(api.handoffs))
    def test_production_is_default_dry_run_and_requires_explicit_verified_deployment_reference(self):
        self.assertTrue(importer.target_guard("https://feel2real.co.kr",False,False,None))
        for url in ["https://untrusted.example","https://feel2real.co.kr@untrusted.example","https://feel2real.co.kr?x=1"]:
            with self.assertRaises(ValueError):importer.target_guard(url,False,False,None)
        with self.assertRaises(ValueError):importer.target_guard("https://feel2real.co.kr",True,False,None)
        self.assertTrue(importer.target_guard("https://feel2real.co.kr",True,True,"a"*40))
    def test_dry_run_has_zero_network_writes_and_rejects_changed_original(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp,"고1_원본.txt");p.write_bytes(b"source")
            manifest=inventory.build([self.item(p.name)],tmp)
            m=Path(tmp,"manifest.json");m.write_text(json.dumps(manifest))
            args=argparse.Namespace(manifest=str(m),originals=tmp,base_url="https://feel2real.co.kr",execute=False,production_authorized=False,deployment_sha=None)
            with patch.object(importer,"Api",side_effect=AssertionError("No network allowed")):
                self.assertEqual(0,importer.run(args)["network_writes"])
                p.write_bytes(b"change")
                with self.assertRaises(ValueError):importer.run(args)

if __name__=="__main__":unittest.main()
