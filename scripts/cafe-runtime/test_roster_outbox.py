import json
from pathlib import Path
import tempfile
import unittest
from roster_outbox import consume

class OutboxTest(unittest.TestCase):
    def test_success_archives_and_failure_remains_retryable(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); inbox=root/'in';inbox.mkdir();archive=root/'archive'
            (inbox/'good.json').write_text(json.dumps({'member_tag':'test1234abcd'}))
            (inbox/'bad.json').write_text('{')
            result=consume(inbox,archive,root/'status.json',lambda _:42)
            self.assertEqual((result['synced'],result['review']),(1,1))
            self.assertFalse((inbox/'good.json').exists())
            self.assertTrue((inbox/'bad.json').exists())
            self.assertEqual(json.loads((archive/'test1234abcd.json').read_text())['roster_id'],42)

    def test_symlink_not_read(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);inbox=root/'in';inbox.mkdir()
            secret=root/'private';secret.write_text('{}')
            (inbox/'link.json').symlink_to(secret)
            calls=[]
            result=consume(inbox,root/'archive',root/'status',lambda x:calls.append(x))
            self.assertEqual(result['review'],1)
            self.assertEqual(calls,[])
            self.assertTrue(secret.exists())
