#!/usr/bin/env python3
"""seed-v3.3-ops.json 검증: v3.2와의 호환, 제출서류 원문 일치, 임의 생성 없음.
사용법: python3 verify_seed_v33.py <handover-20261021 폴더> <seed-v3.3-ops.json> <seed-v3.2-ops.json>
"""
import json, os, sys
base, v33p, v32p = sys.argv[1:4]
load = lambda p: json.load(open(p, encoding='utf-8'))
v33, v32 = load(v33p), load(v32p)
seed = load(os.path.join(base, 'draft-seed-2027-v3.1-20261021.json'))
code = {u['name']: u['university_code'] for u in seed['ipsi_universities']}
dockey = {}
for d in seed['ipsi_documents']:
    k = d['document_key']; dockey[(k.split('_')[1].upper(), '수시' if k.endswith('susi') else '정시')] = k
ext = []
for f, skip in [('recruitment-2027-extracted.json', False), ('recruitment-2027-extracted-addendum-20261021.json', True),
                ('recruitment-2027-extracted-addendum2-20261021.json', False)]:
    for e in load(os.path.join(base, f)):
        if skip and e['university'] == '서강대학교' and e['admission_type'] == '정시': continue
        ext.append(e)
errors = []
def check(cond, msg):
    if not cond: errors.append(msg)
# 1) 구조 호환: 제출서류 필드를 빼면 v3.2와 완전히 같다
strip = lambda rows: [{k: v for k, v in r.items() if k != 'submission_documents'} for r in rows]
check(strip(v33['ipsi_admission_types']) == v32['ipsi_admission_types'], 'types differ from v3.2 beyond submission_documents')
for key in ('ipsi_universities', 'ipsi_documents', 'ipsi_schedules', 'ipsi_departments'):
    check(v33[key] == v32[key], key + ' differs from v3.2')
# 2) 제출서류 원문 일치: 추출의 documents == 해당 문서 전형 행의 submission_documents (순서·문자열 그대로)
checked = rows_with = 0
for e in ext:
    c = code[e['university']]; key = dockey[(c.upper(), e['admission_type'])]
    if (e['university'], e['admission_type']) == ('중앙대학교', '수시'): continue
    rows = [r for r in v33['ipsi_admission_types'] if r['source_document_key'] == key]
    docs = e.get('documents')
    has = isinstance(docs, list) and any(str(d).strip() for d in docs)
    for r in rows:
        checked += 1
        if has:
            rows_with += 1
            check(r.get('submission_documents') == [str(d) for d in docs], f'documents mismatch {key} / {r["type_name"]}')
        else:
            check('submission_documents' not in r, f'invented documents for {key} / {r["type_name"]}')
# 3) 임의 생성 없음: 시드의 모든 제출서류 문자열은 어떤 추출 documents에도 실제로 존재
allowed = {str(d) for e in ext for d in (e.get('documents') or [])}
for r in v33['ipsi_admission_types']:
    for d in r.get('submission_documents', []):
        check(d in allowed, 'document text not found in any extraction: ' + d[:40])
print('types', len(v33['ipsi_admission_types']), 'checked rows', checked, 'rows with documents', rows_with)
print('without documents:', sorted({r['source_document_key'] for r in v33['ipsi_admission_types'] if 'submission_documents' not in r}))
if errors:
    print('FAIL', len(errors)); [print(' -', x) for x in errors[:10]]; sys.exit(1)
print('PASS')
