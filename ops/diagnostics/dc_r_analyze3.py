"""2페이지 바닥글 글리프를 PyMuPDF(문자 단위)와 1200dpi 잉크 클러스터로 점검. 인자: PDF들."""
import sys
import fitz  # PyMuPDF
from PIL import Image

for pdf in sys.argv[1:]:
    print(f'\n######## {pdf}')
    doc = fitz.open(pdf)
    page = doc[1]
    print('page rect', page.rect, 'mediabox', page.mediabox, 'cropbox', page.cropbox)
    raw = page.get_text('rawdict')
    for b in raw['blocks']:
        for l in b.get('lines', []):
            for sp in l['spans']:
                if sp['bbox'][1] > 780:
                    print('span font=%s size=%.1f bbox=%s' % (sp['font'], sp['size'], tuple(round(v, 1) for v in sp['bbox'])))
                    for ch in sp['chars'][:14]:
                        c = ch['c']
                        print('   char U+%04X %r x0=%.1f x1=%.1f' % (ord(c), c, ch['bbox'][0], ch['bbox'][2]))
    # 1200dpi ink clusters in the footer line
    pix = page.get_pixmap(dpi=1200, clip=fitz.Rect(20, 878, 220, 894))
    im = Image.frombytes('RGB', (pix.width, pix.height), pix.samples).convert('L')
    S = 1200 / 72
    cols = [any(im.getpixel((x, y)) < 140 for y in range(im.height)) for x in range(im.width)]
    clusters, start = [], None
    for x, on in enumerate(cols + [False]):
        if on and start is None:
            start = x
        if not on and start is not None:
            clusters.append((20 + start / S, 20 + x / S)); start = None
    merged = []
    for a, b in clusters:
        if merged and a - merged[-1][1] < 0.5:
            merged[-1] = (merged[-1][0], b)
        else:
            merged.append((a, b))
    print('ink clusters in footer line (pt, gaps<0.5pt merged):', [(round(a, 1), round(b, 1)) for a, b in merged[:14]])
