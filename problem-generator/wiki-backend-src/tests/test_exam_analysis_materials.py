"""기출 분석(EXAM_ANALYSIS) 자료형 계약 테스트.

  - HTML·PDF·HWPX·DOCX만 받는다. HTML은 정제 본문이 FINAL Context(exam_analyses[])에 실린다.
  - PDF 등은 원본만 보관하고 Context에는 original_api_path만 노출한다.
  - 학교 프린트 목록(school_prints)에 섞이지 않는다.
  - 크기 상한은 150MB(기출시험지 30MB보다 넉넉함).
"""
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
os.environ.setdefault('BUILDIT_WIKI_DB_PATH', str(Path(tempfile.gettempdir()) / 'buildit-wiki-exam-analysis.db'))
os.environ.setdefault('BUILDIT_WIKI_WORKSPACE', tempfile.gettempdir())

import main  # noqa: E402
import workspace  # noqa: E402

ANALYSIS_HTML = (
    '<!doctype html><html><head><style>body{color:red}</style><script>alert(1)</script></head>'
    '<body><h1>2026 1학기 기말 기출 분석</h1><p>빈칸추론 4문항, 서답형 조건영작 비중 높음</p></body></html>'
).encode('utf-8')


def _docx_bytes() -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        archive.writestr('[Content_Types].xml', '<Types/>')
        archive.writestr('word/document.xml', '<w:document/>')
    return buffer.getvalue()


class ExamAnalysisMaterialTest(unittest.TestCase):
    CTX = dict(school='신구중', grade='2학년', semester='1학기', exam='기말고사')

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()
        self.identity_token = workspace.set_current_identity(workspace.WikiIdentity('7', 'modu', 'TUTOR'))

    def tearDown(self):
        workspace.reset_current_identity(self.identity_token)
        self.temp_dir.cleanup()

    def _create(self, name, data, content_type='application/octet-stream'):
        return main.create_exam_material(
            'school_print', self.CTX['school'], self.CTX['grade'], 2026,
            self.CTX['semester'], self.CTX['exam'], f'{name} 업로드', None,
            UploadFile(filename=name, file=io.BytesIO(data), headers={'content-type': content_type}),
            None, '', '', 'EXAM_ANALYSIS')

    def test_html_analysis_body_reaches_final_context_and_not_school_prints(self):
        # given
        created = self._create('analysis.html', ANALYSIS_HTML, 'text/html')
        # when
        context = main.get_final_workspace_context(year=2026, **self.CTX)
        # then
        self.assertEqual('EXAM_ANALYSIS', created['material_type'])
        item = context['exam_analyses'][0]
        self.assertEqual('exam_analysis', item['type'])
        self.assertEqual('html', item['body_format'])
        self.assertIn('빈칸추론 4문항', item['body_text'])
        self.assertNotIn('alert(1)', item['body_text'])
        self.assertEqual([], [i for i in context['school_prints'] if i['material_type'] == 'EXAM_ANALYSIS'])

    def test_pdf_and_docx_analysis_keep_original_without_body(self):
        # given
        self._create('analysis.pdf', b'%PDF-1.7 analysis', 'application/pdf')
        self._create('analysis.docx', _docx_bytes())
        # when
        items = main.get_final_workspace_context(year=2026, **self.CTX)['exam_analyses']
        # then
        self.assertEqual(2, len(items))
        for item in items:
            self.assertFalse(item['has_body'])
            self.assertTrue(item['original_api_path'].endswith('/original'))

    def test_unsupported_extension_is_rejected(self):
        # given / when
        with self.assertRaises(HTTPException) as error:
            self._create('analysis.md', b'# memo', 'text/markdown')
        # then
        self.assertIn('HTML, PDF, HWPX, DOCX', error.exception.detail)

    def test_fake_pdf_signature_is_rejected(self):
        # given / when / then
        with self.assertRaises(HTTPException):
            self._create('analysis.pdf', b'not a pdf', 'application/pdf')

    def test_size_limit_is_larger_than_past_exam_limit(self):
        # given
        just_over_past_exam = b'%PDF-' + b'0' * (main._PAST_EXAM_MAX_BYTES + 10)
        # when
        created = self._create('big.pdf', just_over_past_exam, 'application/pdf')
        # then
        self.assertEqual(150 * 1024 * 1024, main._EXAM_ANALYSIS_MAX_BYTES)
        self.assertEqual('EXAM_ANALYSIS', created['material_type'])

    def test_over_size_limit_is_rejected(self):
        # given
        original = main._EXAM_ANALYSIS_MAX_BYTES
        main._EXAM_ANALYSIS_MAX_BYTES = 10
        try:
            # when / then
            with self.assertRaises(HTTPException) as error:
                self._create('analysis.pdf', b'%PDF-1.7 0123456789', 'application/pdf')
            self.assertIn('150MB', error.exception.detail)
        finally:
            main._EXAM_ANALYSIS_MAX_BYTES = original

    def test_list_endpoint_filters_exam_analysis(self):
        # given
        self._create('analysis.html', ANALYSIS_HTML, 'text/html')
        # when
        rows = main.list_exam_materials(year=2026, material_type='EXAM_ANALYSIS', all_years=True, **self.CTX)
        # then
        self.assertEqual(['EXAM_ANALYSIS'], [row['material_type'] for row in rows])


if __name__ == '__main__':
    unittest.main()
