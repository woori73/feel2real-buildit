import io
import os
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

from fastapi import HTTPException, UploadFile

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-past-exams.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402
import workspace  # noqa: E402


class PastExamMaterialsTest(unittest.TestCase):
    CTX = dict(school='신구중', grade='3학년', semester='2학기', exam='중간고사')

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self):
        self.temp_dir.cleanup()

    def upload(self, year: int, name: str = 'exam.pdf'):
        identity_token = workspace.set_current_identity(workspace.WikiIdentity('7', 'modu', 'TUTOR'))
        try:
            return main.create_exam_material(
                'school_print', self.CTX['school'], self.CTX['grade'], year,
                self.CTX['semester'], self.CTX['exam'], f'{year} 기출', None,
                UploadFile(filename=name, file=io.BytesIO(b'%PDF-1.7\nexam'),
                           headers={'content-type': 'application/pdf'}),
                None, '', '', 'PAST_EXAM')
        finally:
            workspace.reset_current_identity(identity_token)

    def test_actual_past_exam_keeps_provenance_and_is_not_a_school_print(self):
        created = self.upload(2025)
        self.assertEqual('PAST_EXAM', created['material_type'])
        self.assertEqual('modu', created['uploaded_by'])

        final_context = main.get_final_workspace_context(year=2026, **self.CTX)
        self.assertEqual([], final_context['school_prints'])
        item = final_context['past_exams'][0]
        self.assertEqual(2025, item['exam_year'])
        self.assertEqual('PAST_EXAM', item['material_type'])
        self.assertEqual('modu', item['provenance']['uploaded_by'])
        self.assertEqual(f"/api/exam-materials/{created['id']}/original", item['original_api_path'])

    def test_multiple_exam_years_are_accumulated_without_overwrite(self):
        ids = [self.upload(year)['id'] for year in (2023, 2024, 2025)]
        rows = main.list_exam_materials(year=2025, material_type='PAST_EXAM', all_years=True, **self.CTX)
        self.assertEqual([2025, 2024, 2023], [row['year'] for row in rows])
        self.assertEqual(3, len(set(ids)))

    def test_past_exam_rejects_non_educational_upload_extension(self):
        with self.assertRaises(HTTPException) as raised:
            main.create_exam_material(
                'school_print', self.CTX['school'], self.CTX['grade'], 2025,
                self.CTX['semester'], self.CTX['exam'], '예상문제', None,
                UploadFile(filename='generated.docx', file=io.BytesIO(b'not-an-exam')),
                None, '', '', 'PAST_EXAM')
        self.assertEqual(400, raised.exception.status_code)
        self.assertIn('PDF, HWP, HWPX', raised.exception.detail)

    def test_hwpx_requires_the_official_container_shape(self):
        invalid = io.BytesIO()
        with zipfile.ZipFile(invalid, 'w') as archive:
            archive.writestr('random.txt', 'not hwpx')
        with self.assertRaises(HTTPException):
            main.create_exam_material(
                'school_print', self.CTX['school'], self.CTX['grade'], 2025,
                self.CTX['semester'], self.CTX['exam'], '가짜 HWPX', None,
                UploadFile(filename='exam.hwpx', file=io.BytesIO(invalid.getvalue())),
                None, '', '', 'PAST_EXAM')


if __name__ == '__main__':
    unittest.main()
