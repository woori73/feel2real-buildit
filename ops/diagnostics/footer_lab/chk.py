import sys, pymupdf
d=pymupdf.open(sys.argv[1]); H=d[0].rect.height
print('pages',len(d),'rect',d[0].rect)
for i,p in enumerate(d):
    for b in p.get_text('rawdict')['blocks']:
        for l in b.get('lines',[]):
            for s in l['spans']:
                if s['bbox'][1]>H-100:
                    print(i+1, s['font'], round(s['bbox'][0]),round(s['bbox'][1]), [hex(ord(c['c'])) for c in s['chars'][:2]], repr(''.join(c['c'] for c in s['chars']))[:70])
