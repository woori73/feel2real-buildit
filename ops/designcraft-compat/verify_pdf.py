import json,subprocess
from pathlib import Path
out=Path('compat-output');rows=[]
for p in sorted(out.glob('smoke-*/result.pdf'))+sorted(out.glob('smoke-*/legacy.pdf')):
    text=subprocess.check_output(['pdftotext',str(p),'-'],text=True)
    compact=''.join(text.split())
    for phrase in (['AI 승인 후 수정한 문장입니다.','교재','20000'] if p.name=='legacy.pdf' else ['한글 조판 검증','첫 번째 문장입니다.','두 번째 문장입니다.']):
        assert ''.join(phrase.split()) in compact,(str(p),'text mismatch')
    fonts=subprocess.check_output(['pdffonts',str(p)],text=True)
    assert 'NotoSansKR' in fonts,(str(p),'Korean font missing')
    (p.parent/(p.stem+'-pdffonts.txt')).write_text(fonts)
    # Save a PDF-rendered page as well as native preview for direct visual inspection.
    subprocess.run(['pdftoppm','-f','1','-singlefile','-scale-to','1200','-png',str(p),str(p.parent/(p.stem+'-render'))],check=True)
    rows.append({'file':str(p),'text_and_korean_font':'PASS'})
(out/'pdf-verification.json').write_text(json.dumps(rows,indent=2))
print('New-build PDFs reopened: Korean text/font and legacy table text PASS')
