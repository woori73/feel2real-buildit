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

    def test_given_25_saved_insights_when_listed_with_limit_100_then_all_returned_newest_first(self):
        """STEP2 화면의 기록 건수는 조회 결과와 같아야 한다. 기존 20 cap은 21건째부터
        숫자와 실제 목록이 어긋나게 만드는 원인이었다."""
        for n in range(25):
            main.create_educator_insight(main.EducatorInsightCreate(
                content=f'수업 기록 {n}', school='신구중', grade='중2',
                year=2026, semester='2학기', exam='중간고사'))

        rows = main.list_educator_insights(limit=100, school='신구중', grade='중2',
                                          year=2026, semester='2학기', exam='중간고사')

        self.assertEqual(25, len(rows))
        self.assertEqual('수업 기록 24', rows[0]['content'])

    def test_given_two_exam_scopes_when_listed_then_only_matching_scope_returned(self):
        main.create_educator_insight(main.EducatorInsightCreate(
            content='신구중 중간고사 기록', school='신구중', grade='중2',
            year=2026, semester='2학기', exam='중간고사'))
        main.create_educator_insight(main.EducatorInsightCreate(
            content='압구정중 기말고사 기록', school='압구정중', grade='중2',
            year=2026, semester='2학기', exam='기말고사'))

        rows = main.list_educator_insights(limit=100, school='신구중', grade='중2',
                                          year=2026, semester='2학기', exam='중간고사')

        self.assertEqual(['신구중 중간고사 기록'], [row['content'] for row in rows])


if __name__ == '__main__':
    unittest.main()
