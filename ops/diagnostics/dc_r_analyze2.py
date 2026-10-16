"""2페이지 머리글의 '한' 슬롯을 고해상도 렌더링·잉크 측정·ASCII·OCR로 점검한다. 인자: PDF들, PNG는 환경변수 PNGS."""
import glob, os, re, subprocess, sys
from PIL import Image


def run(*a):
    return subprocess.run(a, capture_output=True, text=True).stdout


DPI = 400
S = DPI / 72
for pdf in sys.argv[1:]:
    print(f'\n######## {pdf}')
    bbox = run('pdftotext', '-bbox-layout', '-f', '2', '-l', '2', pdf, '-')
    words = re.findall(r'<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">(.*?)</word>', bbox)
    top = sorted(words, key=lambda w: (round(float(w[1])), float(w[0])))
    edge = [w for w in top if float(w[1]) < 90 or float(w[1]) > 780]
    print('page2 words in top/bottom bands:')
    for w in edge[:14]:
        print('  ', tuple(round(float(v), 1) for v in w[:4]), w[4])
    body = [float(w[0]) for w in words if 90 <= float(w[1]) <= 780]
    print('body min xMin:', round(min(body), 1) if body else None)
    first = next((w for w in edge if '글' in w[4]), None)
    if not first:
        print('no "글" word in the page-2 top/bottom bands'); continue
    x0, y0, x1, y1 = (float(v) for v in first[:4])
    gw = y1 - y0
    print('first header word', first[4], 'x0=%.1f h=%.1f' % (x0, gw))
    subprocess.run(['pdftoppm', '-r', str(DPI), '-png', '-f', '2', '-l', '2', pdf, 'p2'], capture_output=True)
    im = Image.open(sorted(glob.glob('p2*.png'))[-1]).convert('L')
    def ink(xa, xb):
        c = im.crop((int(xa * S), int((y0 - 2) * S), int(xb * S), int((y1 + 2) * S))).point(lambda v: 255 if v < 140 else 0)
        return sum(1 for p in c.getdata() if p)
    print('ink in slot left of "글" [x0-1.1h, x0-0.05h):', ink(x0 - gw * 1.1, x0 - gw * 0.05), ' ink in "글" itself:', ink(x0, x0 + gw))
    print('ink in far-left margin [x0-3h, x0-1.2h):', ink(x0 - gw * 3, x0 - gw * 1.2))
    crop = im.crop((int((x0 - gw * 3.2) * S), int((y0 - 2) * S), int((x0 + gw * 6) * S), int((y1 + 2) * S)))
    crop.save('hdr.png')
    rows = 20
    cols = int(crop.size[0] / crop.size[1] * rows * 2)
    small = crop.resize((cols, rows))
    print('ASCII crop: from 3.2 glyphs left of "글" to 6 glyphs right')
    for r in range(rows):
        print(''.join('#' if small.getpixel((c, r)) < 170 else '.' for c in range(cols)))
    print('OCR kor header:', run('tesseract', 'hdr.png', '-', '-l', 'kor', '--psm', '7').strip())
    for f in glob.glob('p2*.png') + ['hdr.png']:
        os.remove(f)
for png in os.environ.get('PNGS', '').split():
    print(f'\n######## {png}')
    print('OCR kor (psm 6):', run('tesseract', png, '-', '-l', 'kor', '--psm', '6').strip()[:500])
