import json, sys, zipfile, copy
src, dst, text, y0 = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4])
z = zipfile.ZipFile(src); names = [i.filename for i in z.infolist()]
d = json.loads(z.read('document.json'))
item_tpl = json.load(open('item_tpl.json'))
d['styles']['paragraph'].append({"name": "ko-footer", "para": {"align": "center"}, "chars": {"fontFamily": "Noto Sans KR", "size": 7.0, "leading": {"kind": "points", "value": 10.0}}})
nid = d['nextId']
for par in d['parents']:
    for pg in par['pages']:
        item = copy.deepcopy(item_tpl); sid = nid + 1; fid = nid; nid += 2
        x0 = pg['x'] + pg['margins']['outside'] if pg['side'] == 'right' else pg['x'] + pg['margins']['inside']
        # symmetric simple: span the live area
        l = pg['x'] + (pg['margins']['inside'] if pg['side']=='right' else pg['margins']['outside'])
        r = pg['x'] + pg['width'] - (pg['margins']['outside'] if pg['side']=='right' else pg['margins']['inside'])
        item['id'] = fid
        item['path']['subpaths'][0]['anchors'] = [{"p": [l, y0]}, {"p": [r, y0]}, {"p": [r, y0 + 12]}, {"p": [l, y0 + 12]}]
        item['content']['story'] = sid
        par['items'].append(item)
        d['stories'][str(sid)] = {"id": sid, "text": text, "paras": [{"style": "ko-footer"}], "chars": [{"len": len(text.encode()), "style": "[None]"}], "frames": [fid], "direction": "leftToRight", "rev": 1}
d['nextId'] = nid
with zipfile.ZipFile(dst, 'w') as o:
    for n in names:
        data = z.read(n)
        if n == 'document.json': data = json.dumps(d, ensure_ascii=False, separators=(',', ':')).encode()
        o.writestr(zipfile.ZipInfo(n), data, compress_type=zipfile.ZIP_STORED if n == 'mimetype' else zipfile.ZIP_DEFLATED)
