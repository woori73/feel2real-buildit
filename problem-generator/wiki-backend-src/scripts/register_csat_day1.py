"""DAY-1 SOURCE 등록: 평가원 3개 시험(2027 6모 · 2027 9모 · 2025 수능) Exam + PDF 원본 + 문제/정답.

멱등: 이미 등록된 Exam/Resource/Question은 건너뛴다.
Usage:
  python scripts/register_csat_day1.py --base http://127.0.0.1:8009 --sources "C:/Users/sxxra/csat-sources"
"""
import argparse
import json
import sys
from pathlib import Path

import httpx

FIXTURES = Path(__file__).resolve().parent.parent / 'fixtures' / 'csat_day1'

EXAMS = [
    {
        'key': 'june', 'payload': 'payload_june.json',
        'exam': {'exam_year': 2027, 'exam_type': 'JUNE_MOCK', 'subject': 'ENGLISH',
                 'exam_date': '2026-06-04', 'label': '2027학년도 6월 모의평가 영어'},
        'resources': [('QUESTION_PAPER', 'june/2027학년도 6월 수능 모의 평가 영어_문제지.pdf'),
                      ('ANSWER', 'june/2027학년도 6월 수능 모의 평가 영어_정답 및 해설.pdf')],
    },
    {
        'key': 'sept', 'payload': 'payload_sept.json',
        'exam': {'exam_year': 2027, 'exam_type': 'SEPTEMBER_MOCK', 'subject': 'ENGLISH',
                 'exam_date': '2026-09-02', 'label': '2027학년도 9월 모의평가 영어'},
        'resources': [('QUESTION_PAPER', 'sept/2026년_9월_고3_한국교육과정평가원 모의평가_문제지.pdf'),
                      ('ANSWER', 'sept/2026년_9월_고3_한국교육과정평가원 모의평가_정답 및 해설지.pdf')],
    },
    {
        'key': 'nov2025csat', 'payload': 'payload_nov2025csat.json',
        'exam': {'exam_year': 2025, 'exam_type': 'CSAT', 'subject': 'ENGLISH',
                 'exam_date': '2024-11-14', 'label': '2025학년도 대학수학능력시험 3교시 영어 (홀수형)'},
        'resources': [('QUESTION_PAPER', 'nov/3교시_영어영역_홀수형.pdf'),
                      ('ANSWER', 'nov/3교시_영어영역_정답표.pdf'),
                      ('ANALYSIS', 'nov/3교시_영어영역_해설지.pdf')],
    },
]


def ensure_exam(client, base, spec):
    body = client.get(f'{base}/api/csat/exams').raise_for_status().json()
    exams = body['exams'] if isinstance(body, dict) else body
    found = next((e for e in exams if e['exam_year'] == spec['exam']['exam_year']
                  and e['exam_type'] == spec['exam']['exam_type'] and e['subject'] == spec['exam']['subject']), None)
    if found:
        print(f"  exam 존재 → skip 생성 (id={found['id']})")
        return found
    created = client.post(f'{base}/api/csat/exams', json=spec['exam']).raise_for_status().json()
    print(f"  exam 생성 id={created['id']}")
    return created


def ensure_resources(client, base, exam_id, sources_dir, spec):
    detail = client.get(f'{base}/api/csat/exams/{exam_id}').raise_for_status().json()
    have = {(r['resource_type'], r['original_name']) for r in detail.get('resources', [])}
    for resource_type, relative in spec['resources']:
        path = Path(sources_dir) / relative
        if not path.exists():
            print(f'  !! 원본 PDF 없음 → skip: {path}')
            continue
        if (resource_type, path.name) in have:
            print(f'  resource 존재 → skip: {resource_type} {path.name}')
            continue
        with path.open('rb') as handle:
            response = client.post(f'{base}/api/csat/exams/{exam_id}/resources',
                                   data={'resource_type': resource_type},
                                   files={'file': (path.name, handle, 'application/pdf')})
        print(f'  resource {response.status_code}: {resource_type} {path.name}')


def ensure_questions(client, base, exam_id, spec):
    existing = client.get(f'{base}/api/csat/exams/{exam_id}/questions').raise_for_status().json(); existing = existing['questions'] if isinstance(existing, dict) else existing
    if existing:
        print(f'  questions 이미 등록됨({len(existing)}) → skip')
        return
    payload = json.loads((FIXTURES / spec['payload']).read_text(encoding='utf-8'))
    questions = []
    for item in payload['questions']:
        item = dict(item)
        item['footnote'] = item.pop('vocabulary', '')
        questions.append(item)
    response = client.post(f'{base}/api/csat/exams/{exam_id}/questions',
                           json={'questions': questions, 'source_filename': f"evaluated:{spec['key']}"})
    if response.status_code == 201:
        print(f"  questions 등록 {len(questions)}문항 (20~24, 29~42)")
    else:
        print(f'  questions 실패 {response.status_code}: {response.text[:300]}')
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:8009')
    parser.add_argument('--sources', default='C:/Users/sxxra/csat-sources')
    args = parser.parse_args()
    with httpx.Client(timeout=120) as client:
        for spec in EXAMS:
            print(f"[{spec['key']}]")
            exam = ensure_exam(client, args.base, spec)
            ensure_resources(client, args.base, exam['id'], args.sources, spec)
            ensure_questions(client, args.base, exam['id'], spec)
    print('DAY-1 SOURCE 등록 완료')


if __name__ == '__main__':
    main()
