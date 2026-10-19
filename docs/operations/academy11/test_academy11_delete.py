import copy,importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('executor',Path(__file__).with_name('academy11-delete.py'));d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)
class Guards(unittest.TestCase):
 def setUp(self):self.good={'account':'425805163960','target':11,'delete':{t:{'count':n,'keys':[[i+1] for i in range(n)],'rows_sha256':'a'*64} for t,n in d.EXPECTED.items()},'preserve':{'users':'unchanged'}}
 def test_exact_manifest_accepted(self):d.checked_manifest(self.good);d.validate_snapshot(self.good,copy.deepcopy(self.good))
 def test_row_count_mismatch_rejected(self):
  self.good['delete']['academies']['count']=2
  with self.assertRaises(SystemExit):d.checked_manifest(self.good)
 def test_pk_mismatch_rejected(self):
  altered=copy.deepcopy(self.good);altered['delete']['academies']['keys']=[[12]]
  with self.assertRaises(SystemExit):d.validate_snapshot(altered,self.good)
 def test_protected_change_rejected(self):
  altered=copy.deepcopy(self.good);altered['preserve']['users']='changed'
  with self.assertRaises(SystemExit):d.validate_snapshot(altered,self.good)
if __name__=='__main__':unittest.main()
