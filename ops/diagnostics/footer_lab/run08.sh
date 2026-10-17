#!/usr/bin/env bash
# CLI 0.8 마스터 페이지 한글 바닥글 프레임 실험(러너 전용). 인자: CLI 경로, 템플릿 디렉터리
set -u
CLI="$1"; T="$2"; H="$(cd "$(dirname "$0")" && pwd)"
W=$(mktemp -d); cp "$T/template.designcraft" "$W/job.designcraft"; cp -r "$T/Document Fonts" "$W/"; cp "$T/sample-manuscript.md" "$W/m.md"; cp "$H"/addfooter.py "$H"/item_tpl.json "$H"/chk.py "$W/"
cd "$W"
TITLE=$(python3 -c "import json,zipfile;print(json.loads(zipfile.ZipFile('job.designcraft').read('document.json'))['title'])")
for cand in 'U0018' 'hash'; do
  if [ "$cand" = hash ]; then TXT="$TITLE  Page #"; else TXT=$(python3 -c "print('$TITLE  Page '+chr(0x18))"); fi
  python3 addfooter.py job.designcraft ft.designcraft "$TXT" 805 || exit 1
  "$CLI" run --in ft.designcraft --cmd 'file.place={"path":"m.md","autoflow":true}' --cmd 'edit.selectAll' --cmd 'style.paragraph.apply={"name":"ko-body"}' --pdf-options '{"standard":"x4","marks":{"crop":true,"bleed":true,"pageInfo":true}}' --export ft.pdf >/dev/null 2>&1
  echo "== master-frame footer ($cand), pageInfo mark ON"; python3 chk.py ft.pdf | head -9
  pdffonts -f 2 -l 2 ft.pdf 2>/dev/null | head -6
done
