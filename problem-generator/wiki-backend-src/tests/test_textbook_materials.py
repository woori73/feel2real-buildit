import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException, UploadFile

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ.setdefault('BUILDIT_WIKI_DB_PATH', str(Path(tempfile.gettempdir()) / 'buildit-wiki-textbook-bootstrap.db'))
os.environ.setdefault('BUILDIT_WIKI_WORKSPACE', tempfile.gettempdir())
import main  # noqa: E402
import workspace  # noqa: E402


def _pdf(name: str, body: bytes = b'%PDF-1.4 fake', content_type: str = 'application/pdf') -> UploadFile:
    return UploadFile(filename=name, file=io.BytesIO(body), headers={'content-type': content_type})


def _create(**kw):
    # FastAPI Form(...) 기본값은 요청 처리 시점에만 해석된다. 함수를 직접 부르는 단위 테스트는
    # 모든 인자를 명시해야 한다(create_exam_material 테스트와 동일 패턴).
    params = dict(grade='', publisher='', lesson='', title='', tags='', author='',
                  page_total=0, page_extracted=0, content_html='')
    params.update(kw)
    params.setdefault('file', _pdf('m.pdf'))
    return main.create_textbook_material(**params)


class TextbookMaterialTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self): self.temp.cleanup()

    def test_original_blob_and_content_persist_and_are_downloadable(self):
        created = _create(
            grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='2과',
            title='2과 문제자료', tags='본문,문법', author='박정민 원장',
            page_total=55, page_extracted=55, content_html='<p>추출 본문</p>',
            file=_pdf('yunjeongmi-2.pdf', b'%PDF-1.4 ' + b'x' * 5000))
        self.assertEqual(55, created['page_total'])
        self.assertEqual(1, created['has_content'])

        listing = main.list_textbook_materials(grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='2과')
        self.assertEqual([created['id']], [row['id'] for row in listing])
        self.assertNotIn('original_blob', listing[0])

        original = main.open_textbook_material_original(created['id'])
        self.assertTrue(original.body.startswith(b'%PDF-1.4'))
        self.assertEqual(len(b'%PDF-1.4 ' + b'x' * 5000), len(original.body))
        self.assertIn('추출 본문', main.open_textbook_material_content(created['id']).body.decode('utf-8'))

    def test_partial_page_extraction_is_recorded_not_hidden(self):
        created = _create(grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='3과', title='부분추출',
            page_total=55, page_extracted=8, content_html='<p>8쪽만</p>', file=_pdf('big.pdf'))
        row = main.list_textbook_materials(grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='3과')[0]
        self.assertEqual(55, row['page_total'])
        self.assertEqual(8, row['page_extracted'])

    def test_content_update_can_backfill_missing_pages_later(self):
        created = _create(grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='4과', title='나중 보강',
            page_total=55, page_extracted=8, content_html='<p>8쪽</p>', file=_pdf('later.pdf'))
        main.update_textbook_material_content(created['id'], main.TextbookContentUpdate(
            content_html='<p>55쪽 전체</p>', page_extracted=55, page_total=55))
        row = main.list_textbook_materials(grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='4과')[0]
        self.assertEqual(55, row['page_extracted'])
        self.assertIn('55쪽 전체', main.open_textbook_material_content(created['id']).body.decode('utf-8'))

    def test_upload_without_file_is_rejected(self):
        with self.assertRaises(HTTPException) as raised:
            _create(grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='1과',
                    title='no file', file=UploadFile(filename='', file=io.BytesIO(b'')))
        self.assertEqual(400, raised.exception.status_code)

    def test_delete_removes_row_and_404s_afterwards(self):
        created = _create(grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='5과', title='삭제대상',
            file=_pdf('drop.pdf'))
        self.assertEqual(204, main.delete_textbook_material(created['id']).status_code)
        self.assertEqual([], main.list_textbook_materials(grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='5과'))
        with self.assertRaises(HTTPException) as raised:
            main.delete_textbook_material(created['id'])
        self.assertEqual(404, raised.exception.status_code)

    def test_textbook_table_is_workspace_isolation_aware(self):
        self.assertIn('textbook_materials', workspace.OWNED_TABLES)
        with main.get_db() as conn:
            columns = {row[1] for row in conn.execute('PRAGMA table_info(textbook_materials)')}
            workspace.migrate_owned_tables(conn, workspace.LEGACY_WORKSPACE_ID)
            after = {row[1] for row in conn.execute('PRAGMA table_info(textbook_materials)')}
        self.assertNotIn('workspace_id', columns)
        self.assertIn('workspace_id', after)


if __name__ == '__main__':
    unittest.main()
