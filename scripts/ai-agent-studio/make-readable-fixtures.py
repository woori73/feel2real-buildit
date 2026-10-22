#!/usr/bin/env python3
"""Synthetic local UI fixtures only; never substitute these for the actual EP01 files."""
import json
import uuid
import zipfile
from pathlib import Path
assets = Path('/tmp/f2r-qa-assets')
assert (assets/'qa-synthetic.png').is_file(), 'Run make-qa-fixtures.py first'
manifest = {'episode': 1, 'qaRun': str(uuid.uuid4()), 'cuts': [
    {'cutNumber': n, 'title': f'합성 QA · {n:02}컷',
     'content': '실제 EP01 원본이 아닙니다. 화면·권한 검증용 등록 자료입니다.',
     'dialogue': f'검증 대사 {n}', 'direction': '화면 열람 검증', 'file': 'qa-synthetic.png'}
    for n in range(1, 69)]}
with zipfile.ZipFile('/tmp/f2r-stage1-synthetic.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('manifest.json', json.dumps(manifest, ensure_ascii=False))
    archive.write(assets/'qa-synthetic.png', 'qa-synthetic.png')
with zipfile.ZipFile('/tmp/f2r-stage1-unsafe.zip', 'w') as archive:
    archive.writestr('../blocked.md', 'never preview')
    archive.writestr('document.md', '# 합성 QA 문서\n\n**실제 작품 자료 아님**')
