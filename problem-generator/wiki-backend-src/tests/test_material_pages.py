"""MCP get_material_pages — PDF·이미지 원본을 쪽 그림·글로 돌려준다(2026-11-23)."""
import asyncio
import io
import json
import os
import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ.setdefault('BUILDIT_WIKI_DB_PATH', str(Path(tempfile.gettempdir()) / 'buildit-wiki-v1-bootstrap.db'))
os.environ.setdefault('BUILDIT_WIKI_WORKSPACE', tempfile.gettempdir())
import main  # noqa: E402
import material_pages  # noqa: E402
import mcp_server  # noqa: E402
from PIL import Image  # noqa: E402


def scanned_pdf(pages: int) -> bytes:
    """글 없이 그림만 있는 PDF(스캔본과 같다)."""
    images = [Image.new('RGB', (600, 850), (255, 255, 255 - i * 20)) for i in range(pages)]
    out = io.BytesIO()
    images[0].save(out, format='PDF', save_all=True, append_images=images[1:])
    return out.getvalue()


def text_pdf(text: str) -> bytes:
    """글이 든 한 쪽 PDF(직접 만든 최소 구조)."""
    stream = f'BT /F1 18 Tf 72 720 Td ({text}) Tj ET'.encode()
    objects = [
        b'<< /Type /Catalog /Pages 2 0 R >>',
        b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
        b'<< /Length %d >>\nstream\n' % len(stream) + stream + b'\nendstream',
        b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    ]
    out = io.BytesIO()
    out.write(b'%PDF-1.4\n')
    offsets = []
    for i, body in enumerate(objects, 1):
        offsets.append(out.tell())
        out.write(b'%d 0 obj\n' % i + body + b'\nendobj\n')
    xref = out.tell()
    out.write(b'xref\n0 %d\n0000000000 65535 f \n' % (len(objects) + 1))
    for off in offsets:
        out.write(b'%010d 00000 n \n' % off)
    out.write(b'trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n' % (len(objects) + 1, xref))
    return out.getvalue()


class MaterialPagesRenderTest(unittest.TestCase):

    def test_scannedPdf_firstWindow_returnsJpegsAndNextPage(self):
        # given: 6쪽 스캔본 PDF
        blob = scanned_pdf(6)
        # when: 첫 4쪽을 요청한다
        result = material_pages.render(blob, 'exam.pdf', 'application/pdf')
        # then: 1~4쪽 JPEG, 다음은 5쪽부터
        self.assertEqual(result['kind'], 'pdf')
        self.assertEqual(result['total_pages'], 6)
        self.assertEqual([p['page'] for p in result['pages']], [1, 2, 3, 4])
        self.assertTrue(all(p['jpeg'][:2] == b'\xff\xd8' for p in result['pages']))
        self.assertEqual(result['next_start_page'], 5)

    def test_scannedPdf_lastWindow_hasNoNextPage(self):
        # given: 6쪽 스캔본 PDF
        blob = scanned_pdf(6)
        # when: 5쪽부터 4쪽을 요청한다
        result = material_pages.render(blob, 'exam.pdf', 'application/pdf', start_page=5, page_count=4)
        # then: 남은 5·6쪽만, 다음 쪽은 없다
        self.assertEqual([p['page'] for p in result['pages']], [5, 6])
        self.assertIsNone(result['next_start_page'])

    def test_render_limitsLongSide(self):
        # given: 한 쪽 PDF
        blob = scanned_pdf(1)
        # when
        jpeg = material_pages.render(blob, 'a.pdf', '')['pages'][0]['jpeg']
        # then: 긴 쪽이 정한 크기를 넘지 않는다
        with Image.open(io.BytesIO(jpeg)) as img:
            self.assertLessEqual(max(img.size), material_pages.TARGET_LONG_SIDE)

    def test_textPdf_returnsPageText(self):
        # given: 글이 든 PDF(MIME·확장자 없이 %PDF- 머리만으로도 알아본다)
        blob = text_pdf('Lesson 5 The City As a Canvas')
        # when
        result = material_pages.render(blob, 'noext', 'application/octet-stream')
        # then: 쪽 글이 함께 온다
        self.assertIn('The City As a Canvas', result['pages'][0]['text'])

    def test_image_returnsOnePage(self):
        # given: PNG 원본
        out = io.BytesIO()
        Image.new('RGBA', (3000, 2000), (0, 0, 0, 255)).save(out, format='PNG')
        # when
        result = material_pages.render(out.getvalue(), 'photo.png', 'image/png')
        # then: JPEG 한 장으로 줄여서
        self.assertEqual(result['kind'], 'image')
        self.assertEqual(len(result['pages']), 1)
        with Image.open(io.BytesIO(result['pages'][0]['jpeg'])) as img:
            self.assertEqual(max(img.size), material_pages.TARGET_LONG_SIDE)

    def test_html_returnsTextOnly(self):
        # given: HTML 원본
        # when
        result = material_pages.render('<p>안녕</p>'.encode(), 'print.html', 'text/html')
        # then: 그림 없이 글
        self.assertEqual(result['kind'], 'text')
        self.assertEqual(result['pages'], [])
        self.assertIn('안녕', result['text'])

    def test_hwp_isRejected(self):
        # given: HWP 원본
        # when / then
        with self.assertRaises(ValueError):
            material_pages.render(b'HWP Document File', 'exam.hwp', 'application/x-hwp')

    def test_startPageBeyondTotal_isRejected(self):
        # given: 2쪽 PDF
        # when / then: 3쪽부터는 없다
        with self.assertRaises(ValueError):
            material_pages.render(scanned_pdf(2), 'a.pdf', 'application/pdf', start_page=3)

    def test_pageCount_isCappedAtFour(self):
        # given / when: 10쪽을 달라고 해도
        first, last = material_pages.page_window(20, 1, 10)
        # then: 4쪽까지만
        self.assertEqual((first, last), (1, 4))


class GetMaterialPagesToolTest(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _insert(self, name, mime, blob):
        with main.get_db() as conn:
            cur = conn.execute(
                'INSERT INTO exam_material_sets (core_type,material_type,school,grade,year,semester,exam,title,'
                'original_name,original_type,original_blob,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
                ('school_print', 'PAST_EXAM', '덕명중', '2학년', 2026, '1학기', '기말고사', '1학기 기말 기출',
                 name, mime, blob, datetime.now().isoformat()))
            conn.commit()
            return cur.lastrowid

    def test_tool_returnsHeadThenImages(self):
        # given: 위키에 올린 5쪽 PDF 기출
        material_id = self._insert('final.pdf', 'application/pdf', scanned_pdf(5))
        # when: MCP 도구로 1~2쪽을 읽는다
        blocks = mcp_server.get_material_pages(material_id, 1, 2)
        # then: 첫 블록은 자료 정보 글, 뒤에 쪽 그림 2장
        head = json.loads(blocks[0])
        self.assertEqual(head['id'], material_id)
        self.assertEqual(head['school'], '덕명중')
        self.assertEqual(head['total_pages'], 5)
        self.assertEqual(head['next_start_page'], 3)
        self.assertEqual(len(blocks), 3)
        self.assertTrue(all(b.to_image_content().mime_type == 'image/jpeg' for b in blocks[1:]))

    def test_tool_unknownId_raises(self):
        # given: 빈 위키
        # when / then
        with self.assertRaises(ValueError):
            mcp_server.get_material_pages(999, 1, 4)

    def test_tool_isListedWithImageContent(self):
        # given: MCP 서버
        # when: 도구를 부르면
        material_id = self._insert('final.pdf', 'application/pdf', scanned_pdf(1))
        tools = asyncio.new_event_loop().run_until_complete(mcp_server.mcp.list_tools())
        result = asyncio.new_event_loop().run_until_complete(
            mcp_server.mcp.call_tool('get_material_pages', {'material_id': material_id}))
        # then: 목록에 있고, 결과에 그림 블록이 담긴다
        self.assertIn('get_material_pages', [t.name for t in tools])
        blocks = result[0] if isinstance(result, tuple) else getattr(result, 'content', result)
        self.assertIn('image', [getattr(b, 'type', None) for b in blocks])


if __name__ == '__main__':
    unittest.main()
