"""내신위키 자료 원본(PDF·이미지)을 쪽 그림 + 글로 바꾼다 (2026-11-23).

get_final_context는 PDF·이미지 기출·학교 프린트에 original_api_path(로그인 필요한 웹 주소)만 준다.
클라우드·학원 PC의 Claude 세션이 그 원본을 읽을 수 있게, MCP 도구 get_material_pages가 이 모듈로
원본을 JPEG 쪽 그림과 (있으면) 쪽 글로 바꿔 돌려준다. 저장·수정은 하지 않는다(읽기 전용).

PDF 렌더링은 pypdfium2(Apache-2.0/BSD-3)를 쓴다 — PyMuPDF는 AGPL이라 쓰지 않는다.
"""
import io
from pathlib import Path

MAX_PAGES_PER_CALL = 4
# 긴 쪽 기준 픽셀. A4 시험지의 작은 글씨·밑줄·기호(ⓐ, ㉠)가 읽히고 한 번에 4쪽을 보낼 수 있는 크기.
TARGET_LONG_SIDE = 1600
JPEG_QUALITY = 72
PAGE_TEXT_LIMIT = 6000

PDF_SUFFIXES = ('.pdf',)
IMAGE_SUFFIXES = ('.jpg', '.jpeg', '.png', '.webp', '.gif')
TEXT_SUFFIXES = ('.html', '.htm', '.md', '.markdown', '.txt')


def kind_of(original_name: str, original_type: str, blob: bytes) -> str:
    """원본 종류: pdf·image·text·other."""
    suffix = Path(original_name or '').suffix.lower()
    mime = (original_type or '').lower()
    if suffix in PDF_SUFFIXES or mime == 'application/pdf' or (blob or b'')[:5] == b'%PDF-':
        return 'pdf'
    if suffix in IMAGE_SUFFIXES or mime.startswith('image/'):
        return 'image'
    if suffix in TEXT_SUFFIXES or mime.startswith('text/'):
        return 'text'
    return 'other'


def page_window(total: int, start_page: int, page_count: int) -> tuple[int, int]:
    """1부터 세는 쪽 번호 구간 [first, last]. 범위를 벗어나면 ValueError."""
    if total < 1:
        raise ValueError('쪽이 없는 자료입니다.')
    first = start_page if start_page and start_page > 0 else 1
    if first > total:
        raise ValueError(f'start_page {first}가 전체 쪽 수 {total}보다 큽니다.')
    count = max(1, min(page_count or MAX_PAGES_PER_CALL, MAX_PAGES_PER_CALL))
    return first, min(total, first + count - 1)


def _jpeg(pil_image) -> bytes:
    image = pil_image.convert('RGB')
    width, height = image.size
    scale = TARGET_LONG_SIDE / max(width, height)
    if scale < 1:
        image = image.resize((max(1, int(width * scale)), max(1, int(height * scale))))
    out = io.BytesIO()
    image.save(out, format='JPEG', quality=JPEG_QUALITY, optimize=True)
    return out.getvalue()


def render(blob: bytes, original_name: str, original_type: str, start_page: int = 1,
           page_count: int = MAX_PAGES_PER_CALL) -> dict:
    """원본을 쪽 그림으로. 돌려주는 값: {kind, total_pages, first_page, last_page, pages[{page, text, jpeg}], next_start_page}.
    text 자료는 pages 없이 text만, 그 밖의 형식(HWP 등)은 ValueError."""
    kind = kind_of(original_name, original_type, blob)
    if kind == 'text':
        text = blob.decode('utf-8', errors='replace')
        return {'kind': 'text', 'total_pages': 1, 'first_page': 1, 'last_page': 1, 'pages': [],
                'text': text[:200000], 'text_truncated': len(text) > 200000, 'next_start_page': None}
    if kind == 'image':
        from PIL import Image
        with Image.open(io.BytesIO(blob)) as img:
            jpeg = _jpeg(img)
        return {'kind': 'image', 'total_pages': 1, 'first_page': 1, 'last_page': 1,
                'pages': [{'page': 1, 'text': '', 'jpeg': jpeg}], 'next_start_page': None}
    if kind != 'pdf':
        raise ValueError(f'이 형식({Path(original_name or "").suffix or original_type})은 쪽 그림으로 바꿀 수 없습니다. PDF·이미지·HTML·MD·TXT만 됩니다.')
    import pypdfium2 as pdfium
    pdf = pdfium.PdfDocument(blob)
    try:
        total = len(pdf)
        first, last = page_window(total, start_page, page_count)
        pages = []
        for no in range(first, last + 1):
            page = pdf[no - 1]
            try:
                width, height = page.get_size()
                scale = TARGET_LONG_SIDE / max(width, height, 1)
                bitmap = page.render(scale=scale)
                jpeg = _jpeg(bitmap.to_pil())
                textpage = page.get_textpage()
                try:
                    text = (textpage.get_text_range() or '').strip()
                finally:
                    textpage.close()
            finally:
                page.close()
            pages.append({'page': no, 'text': text[:PAGE_TEXT_LIMIT], 'jpeg': jpeg})
        return {'kind': 'pdf', 'total_pages': total, 'first_page': first, 'last_page': last, 'pages': pages,
                'next_start_page': last + 1 if last < total else None}
    finally:
        pdf.close()
