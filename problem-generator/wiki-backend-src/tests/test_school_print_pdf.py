import io
import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException, UploadFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
os.environ.setdefault('BUILDIT_WIKI_DB_PATH', str(Path(tempfile.gettempdir()) / 'school-print-pdf.db'))
import main
import workspace


class SchoolPrintPdfTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db_path = str(Path(self.tmp.name) / 'owner.db')
        self.scope = workspace.set_current_workspace(workspace.WikiWorkspace('print-pdf', self.db_path, False))
        self.identity = workspace.set_current_identity(workspace.WikiIdentity('1', 'teacher', 'TUTOR'))
        main.init_db()
        main.migrate_schema()

    def tearDown(self):
        workspace.reset_current_identity(self.identity)
        workspace.reset_current_workspace(self.scope)
        self.tmp.cleanup()

    def upload(self, name, data):
        return main.create_exam_material(
            'school_print', '압구정중', '2학년', 2026, '2학기', '기말고사', name, None,
            UploadFile(filename=name, file=io.BytesIO(data)), None, '', '', 'SCHOOL_PRINT')

    def test_given_pdf_only_when_saved_then_no_html_json_or_mime_is_required(self):
        original = b'%PDF-1.7\n%\xe2\xe3\xcf\xd3\noriginal\n%%EOF\n'
        row = self.upload('학교프린트.PDF', original)
        self.assertEqual('SCHOOL_PRINT', row['material_type'])
        response = main.open_exam_material_original(row['id'])
        self.assertEqual(original, response.body)
        self.assertEqual('application/pdf', response.headers['content-type'])
        self.assertTrue(response.headers['content-disposition'].startswith('inline'))
        with main.get_db() as conn:
            saved = conn.execute('SELECT html_text,json_text FROM exam_material_sets WHERE id=?', (row['id'],)).fetchone()
            self.assertEqual('', saved['html_text'])

    def test_given_fake_pdf_when_uploading_then_reject_before_saving(self):
        with self.assertRaises(HTTPException) as raised:
            self.upload('fake.pdf', b'<html>not a PDF</html>')
        self.assertEqual(400, raised.exception.status_code)
        with main.get_db() as conn:
            self.assertEqual(0, conn.execute('SELECT COUNT(*) FROM exam_material_sets').fetchone()[0])

    def test_given_htm_when_uploaded_then_preserve_html_compatibility(self):
        original = b'<!doctype html><html><body>print</body></html>'
        row = self.upload('print.htm', original)
        self.assertEqual(original, main.open_exam_material_original(row['id']).body)


if __name__ == '__main__':
    unittest.main()
