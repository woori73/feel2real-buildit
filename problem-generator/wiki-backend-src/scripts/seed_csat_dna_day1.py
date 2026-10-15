"""DAY-1 출제 DNA 시드: 문항별 DNA/TRAP + 시험 CLAIM + 6월↔9월 비교를 API로 적재.

멱등: dup(409)은 조용히 건너뛴다. register_csat_day1.py 실행 후 사용.
Usage: python scripts/seed_csat_dna_day1.py --base http://127.0.0.1:8009
"""
import argparse
import json
from pathlib import Path

import httpx

FIXTURES = Path(__file__).resolve().parent.parent / 'fixtures' / 'csat_day1'


def exam_map(client, base):
    body = client.get(f'{base}/api/csat/exams').raise_for_status().json()
    exams = body['exams'] if isinstance(body, dict) else body
    return {(e['exam_year'], e['exam_type']): e['id'] for e in exams}


def question_map(client, base, exam_id):
    body = client.get(f'{base}/api/csat/exams/{exam_id}/questions').raise_for_status().json()
    questions = body['questions'] if isinstance(body, dict) else body
    return {q['question_no']: q['id'] for q in questions}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:8009')
    args = parser.parse_args()
    data = json.loads((FIXTURES / 'dna_day1.json').read_text(encoding='utf-8'))
    keys = data['_meta']['exams']
    with httpx.Client(timeout=60) as client:
        exams = exam_map(client, args.base)
        qids = {tag: question_map(client, args.base, exams[(spec['exam_year'], spec['exam_type'])]) for tag, spec in keys.items()}

        created, skipped = 0, 0
        for item in data['dna']:
            spec = keys[item['exam']]
            evidence = item.pop('evidence', None) or [{'exam_year': spec['exam_year'], 'exam_type': spec['exam_type'], 'question_no': item['question_no']}]
            body = {k: v for k, v in item.items() if k != 'exam'}
            body['evidence'] = evidence
            question_id = qids[item['exam']][item['question_no']]
            response = client.post(f'{args.base}/api/csat/questions/{question_id}/dna', json=body)
            if response.status_code == 201:
                created += 1
            elif response.status_code == 409:
                # 기존 행에 evidence가 비어 있으면(이전 시드 버그) 보강
                rows = client.get(f'{args.base}/api/csat/questions/{question_id}/dna').raise_for_status().json()
                rows = rows['dna'] if isinstance(rows, dict) else rows
                for row in rows:
                    if (row['entry_type'] == body['entry_type'] and row['tag'] == body['tag']
                            and (row.get('choice_no') or 0) == (body.get('choice_no') or 0) and not row.get('evidence')):
                        client.put(f"{args.base}/api/csat/dna/{row['id']}", json={'evidence': evidence}).raise_for_status()
                skipped += 1
            else:
                print('DNA 실패', item['exam'], item['question_no'], response.status_code, response.text[:200])
        print(f'DNA/TRAP entries: created {created}, skipped(dup) {skipped}')

        created, skipped = 0, 0
        # dna-claims에는 UNIQUE 제약이 없어 POST이 중복 행을 만든다 → (claim_type, tag) 선검사
        existing = {}
        for eid in set(exams.values()):
            agg = client.get(f'{args.base}/api/csat/exams/{eid}/dna').raise_for_status().json()
            existing[eid] = {(c['claim_type'], c['tag']) for c in agg['claims']}
        for claim in data['claims']:
            spec = keys[claim['exam']]
            exam_id = exams[(spec['exam_year'], spec['exam_type'])]
            if (claim['claim_type'], claim['tag']) in existing[exam_id]:
                skipped += 1
                continue
            body = {k: v for k, v in claim.items() if k != 'exam'}
            response = client.post(f'{args.base}/api/csat/exams/{exam_id}/dna-claims', json=body)
            if response.status_code == 201:
                created += 1
                existing[exam_id].add((claim['claim_type'], claim['tag']))
            else:
                print('CLAIM 실패', claim['exam'], claim['claim_type'], response.status_code, response.text[:200])
        print(f'CLAIMS: created {created}, skipped {skipped}')

        created, skipped = 0, 0
        for comp in data['comparisons']:
            a, b = keys[comp['exam_a']], keys[comp['exam_b']]
            body = {'exam_a_id': exams[(a['exam_year'], a['exam_type'])], 'exam_b_id': exams[(b['exam_year'], b['exam_type'])],
                    'tag': comp['tag'], 'verdict': comp['verdict'], 'claim_text': comp['claim_text'],
                    'evidence': comp['evidence'], 'provenance': comp['provenance']}
            response = client.post(f'{args.base}/api/csat/dna-comparisons', json=body)
            if response.status_code in (200, 201):
                created += 1
            elif response.status_code == 409:
                skipped += 1
            else:
                print('COMPARISON 실패', comp['tag'], response.status_code, response.text[:200])
        print(f'COMPARISONS: created {created}, skipped {skipped}')
    print('DAY-1 DNA 시드 완료')


if __name__ == '__main__':
    main()
