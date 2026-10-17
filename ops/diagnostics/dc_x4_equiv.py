"""#722 PDFBox 재저장 전후 PDF/X-4 구조 동등성(같은 PDF에 적용). 인자: 코드 orig.pdf pre.pdf restored_same.pdf"""
import hashlib, re, subprocess, sys
import pymupdf

code, orig, pre, rest = sys.argv[1:5]


def meta(path):
    d = pymupdf.open(path)
    cat = d.pdf_catalog()
    oi_key = d.xref_get_key(cat, 'OutputIntents')
    oi = {}
    if oi_key[0] == 'array':
        x = int(re.search(r'(\d+) 0 R', oi_key[1]).group(1))
        raw = re.sub(r'\s+', ' ', d.xref_object(x, compressed=False))
        dest = re.search(r'/DestOutputProfile (\d+) 0 R', raw)
        oi['dict'] = re.sub(r'\d+ 0 R', 'N 0 R', raw)
        if dest:
            oi['icc_sha'] = hashlib.sha256(d.xref_stream(int(dest.group(1)))).hexdigest()[:16]
    info_x = int(d.xref_get_key(-1, 'Info')[1].split()[0])
    info = {k: d.xref_get_key(info_x, k)[1] for k in d.xref_get_keys(info_x)}
    boxes = [tuple(tuple(round(v, 2) for v in b) for b in (p.mediabox, p.trimbox, p.bleedbox, p.artbox, p.cropbox)) for p in d]
    fonts = sorted({(f[3].split('+')[-1], f[1]) for p in d for f in p.get_fonts()})
    texts = [p.get_text() for p in d]
    return {'format': d.metadata['format'], 'pages': len(d), 'boxes': boxes, 'oi': oi, 'xmp': d.get_xml_metadata(), 'info': info, 'fonts': fonts,
            'markinfo': d.xref_get_key(cat, 'MarkInfo')[1], 'id': d.xref_get_key(-1, 'ID')[1], 'title': d.metadata['title'], 'texts': texts,
            'struct': d.xref_get_key(cat, 'StructTreeRoot')[0], 'lang': d.xref_get_key(cat, 'Lang')[1]}


def strip_title(x):
    return re.sub(r'(<dc:title>.*?<rdf:li[^>]*>).*?(</rdf:li>)', r'\1T\2', re.sub(r'\s+', ' ', x), flags=re.S)


def nodate(i):
    return {k: v for k, v in i.items() if k not in ('ModDate', 'CreationDate', 'Title')}


O, P, R = meta(orig), meta(pre), meta(rest)
print(f'## {code}   (A=pre-restore engine PDF with ASCII title, B=same PDF after PDFBox restore)')
res = []
def chk(name, ok, detail=''):
    res.append(ok); print(('PASS ' if ok else 'FAIL ') + name + (f'  | {detail}' if not ok and detail else ''))
chk('PDF version / page count', P['format'] == R['format'] and P['pages'] == R['pages'], f"{P['format']}/{R['format']}")
chk('Media/Trim/Bleed/Art/Crop boxes identical', P['boxes'] == R['boxes'])
chk('OutputIntent dict identical (object numbers normalised)', P['oi'].get('dict') == R['oi'].get('dict'), f"{P['oi']} / {R['oi']}")
chk('ICC profile bytes identical', P['oi'].get('icc_sha') == R['oi'].get('icc_sha') and R['oi'].get('icc_sha'), f"{P['oi'].get('icc_sha')} / {R['oi'].get('icc_sha')}")
chk('embedded fonts identical', P['fonts'] == R['fonts'])
chk('MarkInfo / StructTreeRoot / Lang identical (tagged PDF)', (P['markinfo'], P['struct'], P['lang']) == (R['markinfo'], R['struct'], R['lang']), f"{(P['markinfo'], P['struct'], P['lang'])} / {(R['markinfo'], R['struct'], R['lang'])}")
chk('trailer /ID identical', P['id'] == R['id'], f"{P['id']} / {R['id']}")
chk('Info dict identical except Title and dates', nodate(P['info']) == nodate(R['info']), f"{nodate(P['info'])} / {nodate(R['info'])}")
chk('Info /Title: A=ASCII mark title, B=original document title', P['title'] != R['title'] and R['title'] == O['title'], f"{P['title']!r} {R['title']!r} orig={O['title']!r}")
chk('XMP identical except dc:title text', strip_title(P['xmp']) == strip_title(R['xmp']))
chk('XMP dc:title == original document title', O['title'] in R['xmp'])
chk('page text (body + ASCII footer) identical', P['texts'] == R['texts'])
chk('GTS_PDFXVersion kept (Info and XMP)', 'GTS_PDFXVersion' in R['info'] and 'PDF/X-4' in R['xmp'] and 'PDF/X-4' in P['xmp'])
q = subprocess.run(['qpdf', '--check', rest], capture_output=True, text=True)
chk('qpdf --check: no syntax/stream errors', 'No syntax or stream encoding errors found' in q.stdout, (q.stdout + q.stderr)[-200:])
raw = lambda p: open(p, 'rb').read().decode('latin-1')
kw = ['GTS_PDFX', 'NotoSansKR', '/N 4', '/DeviceRGB']
chk('app gate keywords (raw bytes) same as engine PDF', [k in raw(pre) for k in kw] == [k in raw(rest) for k in kw], str([(k, k in raw(pre), k in raw(rest)) for k in kw]))
print(f"note: engine run-to-run noise (orig vs pre): ICC {O['oi'].get('icc_sha')} vs {P['oi'].get('icc_sha')}, /ID {O['id'] == P['id']}")
print('RESULT', code, 'ALL_PASS' if all(res) else 'HAS_FAIL')
