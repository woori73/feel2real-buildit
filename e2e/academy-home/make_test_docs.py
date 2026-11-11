"""학원 홈 E2E용 합성 자료를 만든다(실존 인물·운영 자료 아님). 사용: python3 make_test_docs.py <출력폴더>"""
import sys, os
import docx
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
d = docx.Document()
d.add_paragraph('11월 진행표 (2026년)')
rows = [["11월 진행표 (2026년)", "", "", "", "", "", ""],
        ["월", "화", "수", "목", "금", "토", "일"],
        ["2\n테스트가1\n테스트나1", "3\n신규가1", "4\n테스트가2", "5\n동명나1", "6\n테스트마1", "7", "8"],
        ["9\n테스트나2", "10", "11\n테스트가3\n신규가2", "12", "13", "14", "15"],
        ["16", "17", "18", "19", "20", "21", "22"],
        ["23", "24", "25", "26", "27", "28", "29"]]
t = d.add_table(rows=len(rows), cols=7)
for r, row in enumerate(rows):
    for c, val in enumerate(row):
        t.cell(r, c).text = val
d.save(os.path.join(out, 'roster-2026-test.docx'))
c = canvas.Canvas(os.path.join(out, 'roster-2026-test.pdf'))
c.drawString(72, 720, 'Test roster preview page 1'); c.showPage(); c.drawString(72, 720, 'Test roster preview page 2'); c.save()
pdfmetrics.registerFont(UnicodeCIDFont('HYSMyeongJo-Medium'))
name = '이사준비-체크리스트-아주아주긴파일이름이화면에서줄바꿈되는지확인하는테스트용문서_최종_수정본_v3.pdf'
c = canvas.Canvas(os.path.join(out, name))
c.setFont('HYSMyeongJo-Medium', 12)
c.drawString(72, 760, '학원이사 체크리스트 (테스트 자료) 1쪽'); c.drawString(72, 740, '계약서 원본 확인 - 담당 원장 - 기한 11월 20일'); c.showPage()
c.setFont('HYSMyeongJo-Medium', 12)
c.drawString(72, 760, '2쪽: 간판 시안 비교 - 담당 디자이너 - 기한 11월 25일'); c.save()
c = canvas.Canvas(os.path.join(out, 'blank-scan.pdf')); c.showPage(); c.save()
print(out)
