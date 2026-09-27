"""LEVEL-2 이명학 EXPERT_ANALYSIS 등록: 출처 영상 2건 + 시험별 claim + 문항 DNA + 6월↔9월 비교.

멱등: 출처는 (expert, video_url), claim은 (claim_type, tag, provenance, expert_source_id),
비교는 (exam_a, exam_b, tag, verdict) 기준으로 이미 있으면 건너뛴다.
기존 OFFICIAL_SOURCE / ACTUAL_EXAM / WIKI_SYNTHESIS 행은 읽기만 하고 수정·삭제하지 않는다.

Usage:
  python scripts/seed_csat_expert_lee.py --base http://127.0.0.1:8000
"""
import argparse
import json
import sys
from pathlib import Path

import httpx

FIXTURE = Path(__file__).resolve().parent.parent / 'fixtures' / 'csat_day1' / 'dna_expert_lee.json'
EXPERT = '이명학'
# 로컬 미서명 환경에서 ADMIN으로 쓰기하기 위한 쿼리. httpx는 Client(params=...)가 URL 쿼리를 덮어쓰므로
# 요청마다 params로 넘긴다.
ROLE = {'role': 'ADMIN'}


def exam_ids(client, base):
    body = client.get(f'{base}/api/csat/exams', params=ROLE).raise_for_status().json()
    rows = body['exams'] if isinstance(body, dict) else body
    return {(row['exam_year'], row['exam_type']): row['id'] for row in rows}


def resolve_exam(keys, ids, alias):
    spec = keys[alias]
    return ids[(spec['exam_year'], spec['exam_type'])]


def ensure_sources(client, base, data, ids):
    existing = {(row['expert'], row['video_url']): row['id']
                for row in client.get(f'{base}/api/csat/expert-sources', params=ROLE).raise_for_status().json()}
    resolved = {}
    for source in data['sources']:
        key = (source['expert'], source['video_url'])
        if key in existing:
            resolved[source['key']] = existing[key]
            print(f"  source 존재 → skip: {source['key']} (id={existing[key]})")
            continue
        payload = {
            'expert': source['expert'],
            'target_exam_id': resolve_exam(data['_meta']['exams'], ids, source['target_exam']),
            'source_type': source['source_type'],
            'video_url': source['video_url'],
            'video_title': source['video_title'],
            'published_at': source['published_at'],
            'verified_note': source['verified_note'],
        }
        created = client.post(f'{base}/api/csat/expert-sources', json=payload, params=ROLE).raise_for_status().json()
        resolved[source['key']] = created['id']
        print(f"  source 생성 id={created['id']} {source['key']}")
    return resolved


def with_exam(ref, keys, ids):
    """fixture의 {'exam': 'J2027', 'question_no': 31} → API evidence 형식으로 변환."""
    spec = keys[ref['exam']]
    return {'exam_year': spec['exam_year'], 'exam_type': spec['exam_type'], 'question_no': ref['question_no']}


def expert_fields(item, sources, keys, ids):
    return {
        'provenance': 'EXPERT_ANALYSIS',
        'expert': EXPERT,
        'expert_source_id': sources[item['source']],
        'timestamp_start': item['timestamp_start'],
        'timestamp_end': item['timestamp_end'],
        'agreement_kind': item['agreement_kind'],
    }


def seed_dna(client, base, data, ids, sources):
    keys = data['_meta']['exams']
    created = skipped = 0
    for item in data['dna']:
        question = client.get(f"{base}/api/csat/exams/{resolve_exam(keys, ids, item['exam'])}/questions",
                              params={**ROLE, 'from_no': item['question_no'], 'to_no': item['question_no']}
                              ).raise_for_status().json()
        if not question:
            print(f"  !! 문항 없음 → abort: {item['exam']} {item['question_no']}번이 등록되어 있지 않습니다.")
            sys.exit(1)
        if question[0]['question_no'] != item['question_no']:
            print(f"  !! 문항 번호 불일치 → abort: {item['exam']} 요청 {item['question_no']} / 응답 {question[0]['question_no']}")
            sys.exit(1)
        question_id = question[0]['id']
        rows = client.get(f'{base}/api/csat/questions/{question_id}/dna', params=ROLE).raise_for_status().json()
        if any(r['entry_type'] == item['entry_type'] and r['tag'] == item['tag']
               and r['provenance'] == 'EXPERT_ANALYSIS' for r in rows):
            skipped += 1
            continue
        body = {
            'entry_type': item['entry_type'], 'tag': item['tag'], 'note': item['note'],
            'importance': item['importance'], 'agreement': item['agreement_kind'] == 'AGREEMENT',
            'evidence': [with_exam({'exam': item['exam'], 'question_no': item['question_no']}, keys, ids)],
            **expert_fields(item, sources, keys, ids),
        }
        response = client.post(f'{base}/api/csat/questions/{question_id}/dna', json=body, params=ROLE)
        if response.status_code == 201:
            created += 1
        else:
            print(f"  !! DNA 실패 {item['exam']} {item['question_no']} {item['tag']}: {response.status_code} {response.text[:200]}")
            sys.exit(1)
    print(f"  DNA: 생성 {created} / 건너뜀 {skipped}")


def seed_claims(client, base, data, ids, sources):
    keys = data['_meta']['exams']
    existing = {}
    for exam_id in set(ids.values()):
        agg = client.get(f'{base}/api/csat/exams/{exam_id}/dna', params=ROLE).raise_for_status().json()
        existing[exam_id] = {(c['claim_type'], c['tag'], c['provenance'], c.get('expert_source_id')) for c in agg['claims']}
    created = skipped = 0
    for item in data['claims']:
        exam_id = resolve_exam(keys, ids, item['exam'])
        body = {
            'claim_type': item['claim_type'], 'tag': item['tag'], 'claim_text': item['claim_text'],
            'importance': item['importance'], 'agreement': item['agreement_kind'] == 'AGREEMENT',
            'evidence': [with_exam(dict(ref, exam=item['exam']), keys, ids) for ref in item['evidence']],
            **expert_fields(item, sources, keys, ids),
        }
        if (body['claim_type'], body['tag'], 'EXPERT_ANALYSIS', body['expert_source_id']) in existing[exam_id]:
            skipped += 1
            continue
        response = client.post(f'{base}/api/csat/exams/{exam_id}/dna-claims', json=body, params=ROLE)
        if response.status_code == 201:
            created += 1
        else:
            print(f"  !! claim 실패 {item['exam']} {item['claim_type']}/{item['tag']}: {response.status_code} {response.text[:200]}")
            sys.exit(1)
    print(f"  claims: 생성 {created} / 건너뜀 {skipped}")


def seed_comparisons(client, base, data, ids, sources):
    keys = data['_meta']['exams']
    existing = {(row['exam_a_id'], row['exam_b_id'], row['tag'], row['verdict']): row['id']
                for row in client.get(f'{base}/api/csat/dna-comparisons', params={
                    **ROLE, 'exam_a_id': resolve_exam(keys, ids, 'J2027'),
                    'exam_b_id': resolve_exam(keys, ids, 'S2027')}).raise_for_status().json()}
    created = skipped = 0
    for item in data['comparisons']:
        exam_a = resolve_exam(keys, ids, item['exam_a'])
        exam_b = resolve_exam(keys, ids, item['exam_b'])
        if (exam_a, exam_b, item['tag'], item['verdict']) in existing:
            skipped += 1
            continue
        body = {
            'exam_a_id': exam_a, 'exam_b_id': exam_b, 'tag': item['tag'], 'verdict': item['verdict'],
            'claim_text': item['claim_text'], 'agreement': item['agreement_kind'] == 'AGREEMENT',
            'evidence': [with_exam(ref, keys, ids) for ref in item['evidence']],
            **expert_fields(item, sources, keys, ids),
        }
        response = client.post(f'{base}/api/csat/dna-comparisons', json=body, params=ROLE)
        if response.status_code in (200, 201):
            created += 1
        else:
            print(f"  !! comparison 실패 {item['tag']}/{item['verdict']}: {response.status_code} {response.text[:200]}")
            sys.exit(1)
    print(f"  comparisons: 생성 {created} / 건너뜀 {skipped}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:8000')
    args = parser.parse_args()
    data = json.loads(FIXTURE.read_text(encoding='utf-8'))
    with httpx.Client(timeout=120) as client:
        ids = exam_ids(client, args.base)
        print(f"[sources]")
        sources = ensure_sources(client, args.base, data, ids)
        print(f"[dna]")
        seed_dna(client, args.base, data, ids, sources)
        print(f"[claims]")
        seed_claims(client, args.base, data, ids, sources)
        print(f"[comparisons]")
        seed_comparisons(client, args.base, data, ids, sources)
    sources_list = httpx.get(f'{args.base}/api/csat/expert-sources', params=ROLE, timeout=60).raise_for_status().json()
    print('[검증] 출처별 연결 건수 (fixture期望 dna={} claims={} comparisons={})'.format(
        len(data['dna']), len(data['claims']), len(data['comparisons'])))
    for row in sources_list:
        print(f"  {row['expert']} · {row['video_url']}: dna={row['dna_count']} claims={row['claim_count']} "
              f"comparisons={row['comparison_count']}")
    print('EXPERT_ANALYSIS 등록 완료')


if __name__ == '__main__':
    main()
