import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-test-bootstrap.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402


class EducatorInsightTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db()

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_given_lesson_context_when_saved_then_recent_record_contains_it(self):
        created = main.create_educator_insight(main.EducatorInsightCreate(
            content='학생이 근거 문장을 먼저 찾으니 정답률이 올랐다.',
            school='신구중',
            grade='중2',
            lesson_context='빈칸 추론',
        ))

        recent = main.list_educator_insights(limit=5)

        self.assertEqual(created['content'], recent[0]['content'])
        self.assertEqual('신구중', recent[0]['school'])
        self.assertEqual('빈칸 추론', recent[0]['lesson_context'])

    def test_given_blank_content_when_saved_then_rejected(self):
        with self.assertRaises(HTTPException) as raised:
            main.create_educator_insight(main.EducatorInsightCreate(content='   '))

        self.assertEqual(400, raised.exception.status_code)


if __name__ == '__main__':
    unittest.main()
