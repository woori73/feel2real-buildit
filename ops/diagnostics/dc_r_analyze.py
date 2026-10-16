"""러너에서 PDF 머리글을 고해상도 렌더링·OCR·ASCII로 점검한다. 인자: PDF 경로들."""
import re, subprocess, sys
from PIL import Image


def run(*a):
    return subprocess.run(a, capture_output=True, text=True).stdout


for pdf in sys.argv[1:]:
    print(f'\n######## {pdf}')
    print(run('pdffonts', pdf)[:900])
    bbox = run('pdftotext', '-bbox-layout', '-f', '1', '-l', '1', pdf, '-')
    page = re.search(r'<page width="([\d.]+)" height="([\d.]+)"', bbox)
    print('page size pt:', page.groups() if page else None)
    words = re.findall(r'<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">(.*?)</word>', bbox)
    top = sorted([w for w in words if float(w[1]) < 90], key=lambda w: (round(float(w[1])), float(w[0])))
    print('top-band words (x0,y0,x1,y1,text):')
    for w in top[:14]:
        print('  ', w[:4], w[4])
    for p in (1, 2):
        txt = run('pdftotext', '-f', str(p), '-l', str(p), '-layout', pdf, '-').splitlines()[:3]
        print(f'page{p} first lines:', [t.strip()[:60] for t in txt])
    subprocess.run(['pdftoppm', '-r', '300', '-png', '-f', '1', '-l', '1', pdf, 'hi'], capture_output=True)
    import glob
    f = sorted(glob.glob('hi*.png'))[-1]
    im = Image.open(f).convert('L')
    W, H = im.size
    band = im.crop((0, 0, W, int(H * 0.10)))
    bb = band.point(lambda v: 255 if v < 160 else 0).getbbox()
    print('hi-res size', (W, H), 'header dark bbox', bb)
    if bb:
        x0, y0, x1, y1 = bb
        hgt = y1 - y0
        # 헤더 줄 하나의 높이 기준 왼쪽 4글자 영역을 ASCII로
        line = band.crop((x0, y0, min(x1, x0 + int(hgt * 5)), y1))
        rows = 18
        cols = int(line.size[0] * rows / line.size[1] * 2)
        small = line.resize((max(cols, 1), rows))
        print('ASCII (header, leftmost ~4 glyphs):')
        for r in range(rows):
            print(''.join('#' if small.getpixel((c, r)) < 150 else '.' for c in range(small.size[0])))
        band.crop((max(0, x0 - 10), max(0, y0 - 10), x1 + 10, y1 + 10)).save('hdr.png')
        print('OCR kor:', run('tesseract', 'hdr.png', '-', '-l', 'kor', '--psm', '7').strip())
    for fl in glob.glob('hi*.png') + glob.glob('hdr.png'):
        import os; os.remove(fl)
