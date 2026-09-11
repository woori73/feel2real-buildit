"""Legacy PAST_EXAM 본문 복구 endpoint 회귀 테스트.

대상: POST /api/exam-materials/{id}/reprocess-body (admin 전용, main.py)

보장하는 계약:
  - legacy row(original_blob=정상 HTML, body_text='')에서 호출 후 같은 id 유지,
    body_text 생성, body_format='html', has_body=1, original_blob 불변.
  - 실패(없는 id/비admin/PAST_EXAM 아님/원본 없음/미지원 형식/invalid·empty HTML) 시
    기존 body_text/body_format을 훼손하지 않는다(UPDATE는 검증된 본문 확보 후에만).
  - SCHOOL_PRINT html_text 계약은 건드리지 않는다.
  - 복구 후 Final Context/MCP에서 body_included=true로 읽힌다.
"""
import io
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from fastapi import HTTPException, UploadFile

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-reprocess-body.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402
import workspace  # noqa: E402

LEGACY_HTML = (
    '<!doctype html><html><head><meta charset="utf-8"><title>2026 1학기 기말</title></head>\n'
    '<body>\n<h1>2026학년도 1학기 기말고사 영어</h1>\n'
    '<p>1. 다음 글의 빈칸에 들어갈 말로 가장 적절한 것은?</p>\n'
    '</body></html>'
).encode('utf-8')

LEGACY_MD = (
    '# 2026 1학기 기말고사 영어 기출\n\n## 1번\n다음 글의 요지로 가장 적절한 것은?\n'
).encode('utf-8')


def _request(role='ADMIN', login_id='admin-f2r'):
    return SimpleNamespace(
        state=SimpleNamespace(
            wiki_identity=workspace.WikiIdentity('1', login_id, role)))


class ReprocessBodyTest(unittest.TestCase):
    CTX = dict(school='신구중', grade='2학년', semester='2학기', exam='중간고사')

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()
        self.identity_token = workspace.set_current_identity(workspace.WikiIdentity('7', 'modu', 'TUTOR'))

    def tearDown(self):
        workspace.reset_current_identity(self.identity_token)
        self.temp_dir.cleanup()

    def _insert_legacy(self, name='past.html', blob=LEGACY_HTML, material_type='PAST_EXAM',
                       body_text='', body_format='', year=2026):
        with main.get_db() as conn:
            cursor = conn.execute(
                '''INSERT INTO exam_material_sets
                    (core_type, material_type, school, grade, year, semester, exam, title,
                     original_name, original_type, original_blob, json_text, html_text,
                     body_text, body_format, uploaded_by, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)''',
                ('school_print', material_type, self.CTX['school'], self.CTX['grade'], year,
                 self.CTX['semester'], self.CTX['exam'], 'legacy 업로드',
                 name, 'text/html', blob, '{}', '', body_text, body_format, 'modu',
                 '2026-09-09T09:23:12'))
            return cursor.lastrowid

    def _row(self, material_id):
        with main.get_db() as conn:
            return dict(conn.execute(
                'SELECT * FROM exam_material_sets WHERE id=?', (material_id,)).fetchone())

    def _count(self):
        with main.get_db() as conn:
            return conn.execute('SELECT COUNT(*) FROM exam_material_sets').fetchone()[0]

    # ---- 성공 ---------------------------------------------------------------

    def test_legacy_html_row_is_reprocessed_in_place(self):
        material_id = self._insert_legacy()
        before = self._row(material_id)
        count_before = self._count()

        result = main.reprocess_exam_material_body(material_id, _request())

        self.assertEqual(material_id, result['id'])
        self.assertEqual('PAST_EXAM', result['material_type'])
        self.assertEqual('html', result['body_format'])
        self.assertEqual(1, result['has_body'])
        self.assertGreater(result['body_chars'], 0)

        after = self._row(material_id)
        self.assertEqual(count_before, self._count())  # 새 row/새 id 없음
        self.assertIn('빈칸에 들어갈 말로 가장 적절한 것은?', after['body_text'])
        self.assertEqual('html', after['body_format'])
        # 불변 필드: 원본과 메타데이터를 건드리지 않는다.
        self.assertEqual(bytes(before['original_blob']), bytes(after['original_blob']))
        self.assertEqual(before['original_name'], after['original_name'])
        self.assertEqual(before['original_type'], after['original_type'])
        self.assertEqual(before['material_type'], after['material_type'])
        self.assertEqual(before['core_type'], after['core_type'])
        self.assertEqual(before['uploaded_by'], after['uploaded_by'])

    def test_legacy_md_row_is_reprocessed_as_markdown(self):
        material_id = self._insert_legacy(name='past.md', blob=LEGACY_MD)
        result = main.reprocess_exam_material_body(material_id, _request())
        self.assertEqual('markdown', result['body_format'])
        self.assertEqual(1, result['has_body'])
        self.assertIn('## 1번', self._row(material_id)['body_text'])

    def test_reprocessed_body_reaches_final_context(self):
        material_id = self._insert_legacy()
        main.reprocess_exam_material_body(material_id, _request())
        ctx = main.get_final_workspace_context(year=2026, **self.CTX)
        item = next(i for i in ctx['past_exams'] if i['id'] == material_id)
        self.assertTrue(item['has_body'])
        self.assertTrue(item['body_included'])
        self.assertEqual('html', item['body_format'])
        self.assertIn('빈칸에 들어갈 말로 가장 적절한 것은?', item['body_text'])

    def test_route_is_registered_as_admin_post(self):
        paths = {(r.path, tuple(sorted(r.methods))) for r in main.app.routes if hasattr(r, 'methods')}
        self.assertIn(('/api/exam-materials/{material_id}/reprocess-body', ('POST',)), paths)

    # ---- 실패: 기존 데이터 불변 ------------------------------------------------

    def test_missing_id_returns_404(self):
        with self.assertRaises(HTTPException) as raised:
            main.reprocess_exam_material_body(9999, _request())
        self.assertEqual(404, raised.exception.status_code)

    def test_non_admin_is_rejected_with_403_and_keeps_row(self):
        material_id = self._insert_legacy()
        with self.assertRaises(HTTPException) as raised:
            main.reprocess_exam_material_body(
                material_id, _request(role='TUTOR', login_id='modu'))
        self.assertEqual(403, raised.exception.status_code)
        self.assertEqual('', self._row(material_id)['body_text'])

    def test_non_past_exam_is_rejected(self):
        import json
        created = main.create_exam_material(
            'school_print', self.CTX['school'], self.CTX['grade'], 2026,
            self.CTX['semester'], self.CTX['exam'], '5과 프린트', None,
            None, None, json.dumps({'items': [1]}, ensure_ascii=False),
            '<h1>5과 본문</h1><p>학교 프린트 원문</p>', 'SCHOOL_PRINT')
        with self.assertRaises(HTTPException) as raised:
            main.reprocess_exam_material_body(created['id'], _request())
        self.assertEqual(400, raised.exception.status_code)
        # SCHOOL_PRINT html_text 계약 불변.
        with main.get_db() as conn:
            row = conn.execute(
                'SELECT html_text, body_text FROM exam_material_sets WHERE id=?',
                (created['id'],)).fetchone()
        self.assertIn('학교 프린트 원문', row['html_text'])
        self.assertEqual('', row['body_text'])

    def test_empty_blob_is_rejected(self):
        material_id = self._insert_legacy(blob=b'')
        with self.assertRaises(HTTPException) as raised:
            main.reprocess_exam_material_body(material_id, _request())
        self.assertEqual(400, raised.exception.status_code)
        self.assertEqual('', self._row(material_id)['body_text'])

    def test_unsupported_extension_is_rejected_without_touching_body(self):
        material_id = self._insert_legacy(
            name='exam.pdf', blob=b'%PDF-1.7\n1 0 obj', body_text='기존본문', body_format='text')
        with self.assertRaises(HTTPException) as raised:
            main.reprocess_exam_material_body(material_id, _request())
        self.assertEqual(400, raised.exception.status_code)
        after = self._row(material_id)
        self.assertEqual('기존본문', after['body_text'])
        self.assertEqual('text', after['body_format'])

    def test_invalid_html_is_rejected_without_touching_body(self):
        material_id = self._insert_legacy(
            name='past.html', blob='그냥 텍스트'.encode('utf-8'),
            body_text='기존본문', body_format='html')
        with self.assertRaises(HTTPException) as raised:
            main.reprocess_exam_material_body(material_id, _request())
        self.assertEqual(400, raised.exception.status_code)
        self.assertEqual('기존본문', self._row(material_id)['body_text'])

    def test_sanitizer_empty_html_is_rejected_without_touching_body(self):
        empty_html = ('<!doctype html><html><head><script>var a=1;</script>'
                      '<style>p{}</style></head><body></body></html>').encode('utf-8')
        material_id = self._insert_legacy(
            name='past.html', blob=empty_html, body_text='기존본문', body_format='html')
        with self.assertRaises(HTTPException) as raised:
            main.reprocess_exam_material_body(material_id, _request())
        self.assertEqual(400, raised.exception.status_code)
        self.assertEqual('기존본문', self._row(material_id)['body_text'])

    def test_reprocess_matches_fresh_upload_body(self):
        """같은 원본이면 신규 업로드와 reprocess가 동일한 body를 만든다."""
        created = main.create_exam_material(
            'school_print', self.CTX['school'], self.CTX['grade'], 2026,
            self.CTX['semester'], self.CTX['exam'], '신규 업로드', None,
            UploadFile(filename='past.html', file=io.BytesIO(LEGACY_HTML),
                       headers={'content-type': 'text/html'}),
            None, '', '', 'PAST_EXAM')
        fresh_body = main.open_exam_material_body(created['id']).body
        legacy_id = self._insert_legacy()
        main.reprocess_exam_material_body(legacy_id, _request())
        reprocessed_body = main.open_exam_material_body(legacy_id).body
        self.assertEqual(fresh_body, reprocessed_body)


if __name__ == '__main__':
    unittest.main()
