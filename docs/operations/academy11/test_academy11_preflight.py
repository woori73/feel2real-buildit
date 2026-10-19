import importlib.util,sqlite3,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('preflight',Path(__file__).with_name('academy11-preflight.py'));p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
class ScopeTests(unittest.TestCase):
 def setUp(self):
  self.db=sqlite3.connect(':memory:')
  self.db.executescript('CREATE TABLE academies(academy_id INTEGER);INSERT INTO academies VALUES(11),(12);CREATE TABLE lessons(id INTEGER,academy_id INTEGER);INSERT INTO lessons VALUES(101,11),(102,12);CREATE TABLE items(id INTEGER,lesson_id INTEGER);INSERT INTO items VALUES(1,101),(2,102);CREATE TABLE shared(id INTEGER,left_id INTEGER,right_id INTEGER);INSERT INTO shared VALUES(1,101,102);')
  self.edges={'lessons':[('academies',[('academy_id','academy_id')])],'items':[('lessons',[('lesson_id','id')])],'shared':[('lessons',[('left_id','id')]),('lessons',[('right_id','id')])]}
  self.seeds={'academies','lessons'}
 def tearDown(self):self.db.close()
 def test_indirect_child_scope(self):
  pred=p.scoped('items',11,self.seeds,self.edges)
  self.assertEqual(self.db.execute('SELECT id FROM items s0 WHERE '+pred).fetchall(),[(1,)])
 def test_real_academy_excluded(self):
  pred=p.scoped('lessons',11,self.seeds,self.edges)
  self.assertEqual(self.db.execute('SELECT id FROM lessons s0 WHERE '+pred).fetchall(),[(101,)])
 def test_shared_row_is_detectable(self):
  a=p.scoped('shared',11,self.seeds,self.edges);b=p.scoped('shared',12,self.seeds,self.edges)
  self.assertEqual(self.db.execute('SELECT COUNT(*) FROM shared s0 WHERE ('+a+') AND ('+b+')').fetchone()[0],1)
 def test_cycle_and_identifier_blocked(self):
  with self.assertRaises(ValueError):p.scoped('a',11,{'a'},{'a':[('a',[('id','id')])]})
  with self.assertRaises(SystemExit):p.identifier('x;DROP TABLE x')
if __name__=='__main__':unittest.main()
