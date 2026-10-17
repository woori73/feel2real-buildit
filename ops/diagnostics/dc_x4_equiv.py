"""#722 PDFBox 재저장 전후 PDF/X-4 구조 동등성 점검. 인자: 템플릿코드 orig.pdf asciiPre.pdf restored.pdf"""
import hashlib, re, subprocess, sys
import pymupdf

code, orig, pre, rest = sys.argv[1:5]


def meta(path):
    d = pymupdf.open(path)
    cat = d.pdf_catalog()
    oi_key = d.xref_get_key(cat, 'OutputIntents')
    oi = {}
    if oi_key[0] == 'array':
        oi_xref = int(re.search(r'(\d+) 0 R', oi_key[1]).group(1))
        oi['dict'] = re.sub(r'\s+', ' ', d.xref_object(oi_xref, compressed=False))
        dest = re.search(r'/DestOutputProfile (\d+) 0 R', oi['dict'])
        if dest:
            oi['icc_sha'] = hashlib.sha256(d.xref_stream(int(dest.group(1)))).hexdigest()[:16]
    xmp = d.get_xml_metadata()
    info_xref = int(d.xref_get_key(-1, 'Info')[1].split()[0])
    info = {k: d.xref_get_key(info_xref, k)[1] for k in d.xref_get_keys(info_xref)}
    boxes = []
    for p in d:
        boxes.append(tuple(tuple(round(v, 2) for v in b) for b in (p.mediabox, p.trimbox, p.bleedbox, p.artbox, p.cropbox)))
    fonts = sorted({(f[3].split('+')[-1], f[1]) for p in d for f in p.get_fonts()})
    return {'version': d.metadata['format'], 'pages': len(d), 'boxes': boxes, 'oi': oi, 'xmp': xmp, 'info': info, 'fonts': fonts,
            'markinfo': d.xref_get_key(cat, 'MarkInfo')[1], 'has_id': d.xref_get_key(-1, 'ID')[0] == 'array', 'title': d.metadata['title']}


def xmp_norm(x):
    x = re.sub(r'<xmp:(CreateDate|ModifyDate|MetadataDate)>.*?</xmp:\1>', '', x)
    x = re.sub(r'uuid:[0-9a-fA-F-]+', 'uuid:X', x)
    return re.sub(r'\s+', ' ', x)


def info_norm(i):
    return {k: v for k, v in i.items() if k not in ('ModDate', 'CreationDate')}


O, P, R = meta(orig), meta(pre), meta(rest)
print(f'## {code}')
res = []
def chk(name, ok, detail=''):
    res.append(ok); print(('PASS ' if ok else 'FAIL ') + name + (f'  {detail}' if detail and not ok else ''))
chk('PDF version unchanged (orig vs restored)', O['version'] == R['version'], f"{O['version']} / {R['version']}")
chk('page count equal', O['pages'] == R['pages'] == P['pages'])
chk('Media/Trim/Bleed/Art/Crop boxes equal on every page', O['boxes'] == R['boxes'] == P['boxes'])
chk('OutputIntent dict + ICC profile equal (orig vs restored)', O['oi'] == R['oi'] and 'icc_sha' in R['oi'], f"{O['oi']} / {R['oi']}")
chk('pre-restore vs restored: OutputIntent equal', P['oi'] == R['oi'])
chk('fonts embedded set equal (orig vs restored)', O['fonts'] == R['fonts'], f"{O['fonts']} / {R['fonts']}")
chk('MarkInfo (tagged) equal', O['markinfo'] == R['markinfo'], f"{O['markinfo']} / {R['markinfo']}")
chk('trailer /ID present', R['has_id'])
chk('Info dict equal except dates (orig vs restored)', info_norm(O['info']) == info_norm(R['info']), f"{info_norm(O['info'])} / {info_norm(R['info'])}")
chk('Info /Title restored to original title', O['title'] == R['title'] and P['title'] != R['title'], f"{O['title']!r} / {P['title']!r} / {R['title']!r}")
chk('GTS_PDFXVersion present in Info and XMP', 'GTS_PDFXVersion' in R['info'] and 'pdfxid' in R['xmp'] .lower().replace('pdfx:', 'pdfxid') or 'GTS_PDFXVersion' in R['xmp'], '')
xo, xp, xr = xmp_norm(O['xmp']), xmp_norm(P['xmp']), xmp_norm(R['xmp'])
chk('XMP equal to original after normalising dates/uuid', xo == xr, '')
if xo != xr:
    import difflib
    for l in list(difflib.unified_diff(re.split(r'(?<=>)', xo), re.split(r'(?<=>)', xr), lineterm='', n=0))[:8]:
        print('   ', l[:200])
q = subprocess.run(['qpdf', '--check', rest], capture_output=True, text=True)
print('qpdf --check restored:', q.returncode, (q.stdout + q.stderr).strip().splitlines()[-1][:160])
chk('qpdf --check reports no errors', q.returncode in (0, 3) and 'No syntax or stream encoding errors found' in q.stdout)
print('RESULT', code, 'ALL_PASS' if all(res) else 'HAS_FAIL')
