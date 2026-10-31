import importlib.util,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
HERE=Path(__file__).parent

def module(name,file):
 s=importlib.util.spec_from_file_location(name,HERE/file);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
host=module('exam_preflight','exam-intake-preflight.py')
migration=module('exam_prepare','prepare-exam-intake-migration.py')

class PreflightTest(unittest.TestCase):
 def test_query_is_readonly_and_existing_tables_are_reported(self):
  raw='history\t1096\t12345\t1\ncolumn\texam_intake_request\tid\tvarchar(36)\tNO\nindex\texam_intake_request\tPRIMARY\t0\t1\tid\n'
  def database(_,runner):
   self.assertIn('START TRANSACTION READ ONLY',host.host.SQL)
   self.assertNotRegex(host.host.SQL,r'\b(?:INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|TRUNCATE)\b')
   runner(['mysql'],input=host.host.SQL)
  with patch.object(host.host,'database',side_effect=database),patch.object(host.host,'run',return_value=raw):
   r=host.schema('not-read')
  self.assertEqual(r['presentTables'],['exam_intake_request'])
 def test_failed_read_is_never_empty_schema(self):
  with patch.object(host.host,'database',return_value={'status':'unavailable'}):
   self.assertEqual(host.schema('not-read'),{'status':'unavailable'})
 def test_secrets_and_malformed_rows_never_reach_report(self):
  def database(_,runner):runner(['mysql'])
  with patch.object(host.host,'database',side_effect=database),patch.object(host.host,'run',return_value='password\tPRIVATE_VALUE'):
   r=host.schema('not-read')
  self.assertNotIn('PRIVATE_VALUE',json.dumps(r));self.assertNotEqual(r['status'],'ok')
 def test_prepare_requires_observed_empty_schema_and_matching_history(self):
  n=max(int(p.name.split('__')[0][1:]) for p in (migration.ROOT/'src/main/resources/db/migration').glob('V*__*.sql'))
  good={'readOnly':True,'schema':{'status':'ok','history':[{'version':n,'success':True}],'presentTables':[],'columns':[],'indexes':[]}}
  with tempfile.TemporaryDirectory() as d:
   for bad in [{}, {'readOnly':True,'schema':{'status':'unavailable'}}, {**good,'schema':{**good['schema'],'presentTables':['exam_intake_request']}}, {**good,'schema':{**good['schema'],'history':[{'version':n+1,'success':True}]}}]:
    with self.assertRaises(ValueError):migration.prepare(bad,Path(d))
   f=migration.prepare(good,Path(d));self.assertEqual(f.name,f'V{n+1}__exam_intake.sql');self.assertEqual(f.read_text().count('CREATE TABLE '),8)
   with self.assertRaises(ValueError):migration.prepare(good,Path(d))
if __name__=='__main__':unittest.main()
