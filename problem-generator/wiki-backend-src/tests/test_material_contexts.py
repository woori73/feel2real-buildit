import io
import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import UploadFile

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-material-contexts.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()
import main  # noqa: E402


class MaterialContextsTest(unittest.TestCase):
    """F2R 자료실·제작 공장이 쓰는 '자료가 있는 시험 Context 목록'(GET /api/material-contexts)."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _print(self, title, material_type='', exam='기말', year=2026):
        return main.create_exam_material(
            'school_print', '신구중', '2학년', year, '2학기', exam, title, None,
            UploadFile(filename='print.pdf', file=io.BytesIO(b'%PDF-1.4 print'), headers={'content-type': 'application/pdf'}),
            None, '', '', material_type)

    def test_given_materials_and_finals_when_list_then_one_row_per_exam_with_counts_by_kind(self):
        # given
        self._print('학교 프린트 1')
        self._print('학교 프린트 2')
        self._print('2025 기말 기출', 'PAST_EXAM')
        self._print('중간 프린트', exam='중간')
        main.create_final_deliverable('final', '신구중', '2학년', 2026, '2학기', '기말', '예상문제 FINAL', None, None, '',
                                      '<html><body>F</body></html>')

        # when
        rows = main.list_material_contexts()

        # then
        self.assertEqual(2, len(rows))
        final_exam = next(r for r in rows if r['exam'] == '기말')
        self.assertEqual((2, 1, 1, 0), (final_exam['school_print'], final_exam['past_exam'], final_exam['final'], final_exam['weekly']))
        self.assertEqual(('신구중', '2학년', 2026, '2학기'), (final_exam['school'], final_exam['grade'], final_exam['year'], final_exam['semester']))
        self.assertTrue(final_exam['updated_at'])

    def test_given_only_student_wrong_note_when_list_then_not_counted(self):
        # given
        with main.get_db() as conn:
            conn.execute("""INSERT INTO exam_material_sets (core_type, material_type, school, grade, year, semester, exam,
                student_id, title, original_name, original_type, original_blob, json_text, html_text, body_text, body_format,
                uploaded_by, created_at) VALUES ('student_wrong_note','STUDENT_WRONG_NOTE','신구중','2학년',2026,'2학기','기말',
                1,'오답','a.png','image/png',x'00','{}','','','','t','2026-11-23')""")

        # when & then
        self.assertEqual([], main.list_material_contexts())

    def test_given_empty_db_when_list_then_empty(self):
        # given & when & then
        self.assertEqual([], main.list_material_contexts())


if __name__ == '__main__':
    unittest.main()
