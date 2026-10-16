#!/usr/bin/env bash
# CLI 0.8 바닥글(pageInfo 마크) 실험실: 러너에서만 실행, 운영 서버/AWS 무관.
set -u
CLI="$1"; T="$2"
W=$(mktemp -d); cp "$T/template.designcraft" "$W/job.designcraft"; cp -r "$T/Document Fonts" "$W/"; cp "$T/sample-manuscript.md" "$W/m.md"
cd "$W"
echo "== version"; "$CLI" --version
echo "== describe file.exportPdf (params)"; "$CLI" describe file.exportPdf | python3 -c "import json,sys;print(json.load(sys.stdin)['params'])"
run() { # name, pdf-options JSON
  "$CLI" run --in job.designcraft --cmd 'file.place={"path":"m.md","autoflow":true}' --cmd 'edit.selectAll' --cmd 'style.paragraph.apply={"name":"ko-body"}' --pdf-options "$2" --export "$1.pdf" >/dev/null 2>&1
  echo "== variant $1: $2"
  python3 - "$1.pdf" <<'PY'
import sys, pymupdf
d = pymupdf.open(sys.argv[1]); p = d[1]; H = p.rect.height
seen = False
for b in p.get_text('rawdict')['blocks']:
    for l in b.get('lines', []):
        for s in l['spans']:
            if s['bbox'][1] > H - 40 or s['bbox'][1] < 40:
                seen = True
                print('  span', s['font'], [hex(ord(c['c'])) for c in s['chars'][:2]], repr(''.join(c['c'] for c in s['chars']))[:60])
print('  pages', len(d), 'page_rect', p.rect, 'footer_text_found', seen)
PY
}
run default '{"standard":"x4","marks":{"crop":true,"bleed":true,"pageInfo":true}}'
run ascii_title '{"standard":"x4","title":"Korean Textbook Template (A4)","marks":{"crop":true,"bleed":true,"pageInfo":true}}'
run no_pageinfo '{"standard":"x4","marks":{"crop":true,"bleed":true}}'
