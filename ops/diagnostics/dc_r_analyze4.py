"""바닥글 첫 글리프(42.6~45.4pt)가 실제 '한'인지 .notdef 상자인지 1200dpi ASCII/OCR로 판별. 인자: PDF."""
import subprocess, sys
import pymupdf
from PIL import Image

pdf = sys.argv[1]
doc = pymupdf.open(pdf)
page = doc[1]
S = 1200 / 72


def glyph(x0, x1, tag):
    clip = pymupdf.Rect(x0 - 0.3, 880.6, x1 + 0.3, 891.0)
    pix = page.get_pixmap(dpi=1200, clip=clip)
    im = Image.frombytes('RGB', (pix.width, pix.height), pix.samples).convert('L')
    bb = im.point(lambda v: 255 if v < 140 else 0).getbbox()
    print(f'-- {tag}: crop px {im.size}, ink bbox {bb}')
    im = im.crop(bb)
    rows = 26
    cols = max(1, int(im.width / im.height * rows * 2))
    small = im.resize((cols, rows))
    for r in range(rows):
        print(''.join('#' if small.getpixel((c, r)) < 170 else '.' for c in range(cols)))
    big = Image.new('L', (im.width + 80, im.height + 80), 255)
    big.paste(im, (40, 40))
    big.save('g.png')
    print('OCR kor psm10:', repr(subprocess.run(['tesseract', 'g.png', '-', '-l', 'kor', '--psm', '10'], capture_output=True, text=True).stdout.strip()))


glyph(42.6, 45.4, 'FIRST glyph (slot of U+0000)')
glyph(46.5, 49.3, 'SECOND glyph (U+AE00 글)')
glyph(51.6, 54.5, 'THIRD glyph (U+AD50 교)')
