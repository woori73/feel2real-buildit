#!/usr/bin/env python3
"""Muse 인계 자료(시드 v3.1 + 추출 JSON 3종)를 AdmissionsDataLoader.loadSeed 형식(v3.2)으로 변환한다.

입력(같은 폴더의 상위 디렉터리 handover-20261021/):
  draft-seed-2027-v3.1-20261021.json                  대학 10 · 공식자료 20
  recruitment-2027-extracted.json                      추출 13건
  recruitment-2027-extracted-addendum-20261021.json    추출 3건 (서강대 정시 부분본은 addendum2가 대체)
  recruitment-2027-extracted-addendum2-20261021.json   추출 5건
출력: seed-v3.2-ops.json  (대학 10 · 공식자료 20 · 전형 116 · 일정 72, 학과 0)
쓰기·네트워크 없음. 사용법: python3 convert_seed.py [입력폴더] [출력파일]
"""
import json, os, sys

base = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
out_path = sys.argv[2] if len(sys.argv) > 2 else 'seed-v3.2-ops.json'
load = lambda f: json.load(open(os.path.join(base, f), encoding='utf-8'))

seed = load('draft-seed-2027-v3.1-20261021.json')
code = {u['name']: u['university_code'] for u in seed['ipsi_universities']}
dockey = {}
for d in seed['ipsi_documents']:
    k = d['document_key']
    dockey[(k.split('_')[1].upper(), '수시' if k.endswith('susi') else '정시')] = k

extractions = []
for f, skip_sogang_jungsi in [('recruitment-2027-extracted.json', False),
                              ('recruitment-2027-extracted-addendum-20261021.json', True),
                              ('recruitment-2027-extracted-addendum2-20261021.json', False)]:
    for e in load(f):
        if skip_sogang_jungsi and e['university'] == '서강대학교' and e['admission_type'] == '정시':
            continue  # addendum2 완전본이 대체
        extractions.append(e)

ITEM = {'application': '원서접수', 'docs_deadline': '서류 제출', 'interview': '면접·실기 고사',
        'announcement': '합격자 발표', 'registration': '합격자 등록'}
types, scheds = [], []
for e in extractions:
    u, t = e['university'], e['admission_type']
    c = code[u]
    key = dockey[(c.upper(), t)]
    if (u, t) == ('중앙대학교', '수시'):
        continue  # 공식 원본 미확보 — 내용 행을 만들지 않는다(자료 행만 등록, 사설 미러 표시)
    overall = e.get('sat_minimum') or ''
    for m in e.get('methods', []):
        mn = m.get('minimum')
        mstd = {'O': '적용', 'X': '미적용'}.get(mn, mn) if mn else None
        types.append({'university_code': c, 'academic_year': 2027, 'type_name': m['type'], 'recruitment_count': '',
                      'eligibility': '', 'student_record_method': m.get('ratio', ''),
                      'min_academic_standard': (f"{mstd} (대학 전체 기준: {overall})" if mstd else overall),
                      'source_document_key': key})
    for k, v in (e.get('schedule') or {}).items():
        if v:
            scheds.append({'university_code': c, 'academic_year': 2027, 'item': ITEM.get(k, k), 'type_name': t,
                           'date': v, 'source_document_key': key})

# --- v3.2 정정 (인계 문서 §2·§6 주의사항 반영) ---
for d in seed['ipsi_documents']:
    k = d['document_key']
    if k == 'doc_korea_2027_susi': d['document_name'] = '고려대학교(세종캠퍼스) 2027학년도 수시모집요강'
    if k == 'doc_sogang_2027_susi': d['document_name'] = '서강대학교 2027학년도 수시 논술가이드북 (모집요강 전체 아님)'
    if k == 'doc_cau_2027_susi': d['document_name'] = '중앙대학교 2027학년도 수시모집요강 (사설 미러본 · 공식 원본 미확보)'
    if k == 'doc_khu_2027_jungsi': d['source_url'] = 'https://iphak.khu.ac.kr'  # 설명 문구가 섞인 값은 링크가 될 수 없다
for t in types:
    if t['source_document_key'] == 'doc_khu_2027_jungsi' and '확인필요' in t['min_academic_standard']:
        t['min_academic_standard'] = '확인되지 않음 (원문 전형요약표 p.12 대조 필요 — 최저학력기준 값을 근거로 답하지 않는다)'
    if t['source_document_key'] == 'doc_korea_2027_susi':
        t['type_name'] = '[세종캠퍼스] ' + t['type_name']
for s in scheds:
    if s['source_document_key'] == 'doc_korea_2027_susi':
        s['type_name'] = '수시(세종캠퍼스)'

out = {'ipsi_universities': seed['ipsi_universities'], 'ipsi_documents': seed['ipsi_documents'],
       'ipsi_departments': [], 'ipsi_admission_types': types, 'ipsi_schedules': scheds}
json.dump(out, open(out_path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('universities', len(out['ipsi_universities']), 'documents', len(out['ipsi_documents']),
      'types', len(types), 'schedules', len(scheds))
