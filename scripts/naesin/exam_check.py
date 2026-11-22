"""내신 예상문제 출제 검수: 문항 수·배점 합계, 정답 번호 쏠림, 지문 중복, 양식.

입력은 내신위키 저장 계약(question_contract.TransformQuestion)과 같은 문항 JSON이다.
    {"examTitle": "...", "expectedCount": 40, "totalScore": 100, "questions": [ {...}, ... ]}
검수를 통과한 같은 파일을 save_generated_questions에 그대로 넘길 수 있다.

조판(A4·밑줄·빈칸) 검수는 problem-generator/wiki-backend-src/backend/weekly_validate.py가 맡는다.
이 파일은 그 앞 단계인 "출제 내용" 검수만 한다. 각 함수는 문제 목록(빈 목록이면 통과)을 돌려준다.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from difflib import SequenceMatcher
from pathlib import Path

CHOICE_COUNT = 5
MARKS = '①②③④⑤'
# 정답 한 번호의 허용 비율. 5지선다의 기대값은 20%다.
MAX_SHARE = 0.30
MIN_SHARE = 0.10
# 비율 검사는 문항이 적으면 우연히 걸리므로 이 수 이상일 때만 한다.
MIN_ITEMS_FOR_SHARE = 20
# 같은 정답 번호가 연달아 나올 수 있는 최대 횟수.
MAX_RUN = 2
# 지문이 이 비율 이상 같으면 중복으로 본다.
SIMILAR_RATIO = 0.90
# 이보다 짧은 지문(단어 한두 개)은 중복 비교에서 뺀다.
MIN_PASSAGE_CHARS = 40
REQUIRED_FIELDS = ('type', 'questionType', 'sourceText', 'stem', 'options', 'answer', 'multi', 'score',
                   'conditions', 'answerFormat', 'acceptedAnswers', 'sampleAnswer', 'gradingRule')
SCORE_IN_STEM = re.compile(r'^\s*[\[(]\s*\d+(\.\d+)?\s*점\s*[\])]')


def _plain(text: str) -> str:
    """태그와 공백 차이를 없앤 비교용 글."""
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', '', text or '')).strip().lower()


def _label(index: int) -> str:
    return '%d번' % (index + 1)


def _single_answers(questions: list[dict]) -> list[tuple[int, int]]:
    """정답이 하나인 객관식의 (문항 순서, 정답 번호)."""
    return [(i, q['answer'][0]) for i, q in enumerate(questions)
            if q.get('type') == 'mc' and not q.get('multi') and len(q.get('answer') or []) == 1]


def check_count(questions: list[dict], expected_count: int | None) -> list[str]:
    """문항 수가 주문한 수와 같은지 본다."""
    if expected_count is None or len(questions) == expected_count:
        return []
    return ['문항 수 %d개: 주문은 %d개' % (len(questions), expected_count)]


def check_scores(questions: list[dict], total_score: int | None) -> list[str]:
    """배점이 모두 양수이고 합계가 만점과 같은지 본다."""
    issues = [_label(i) + ': 배점이 0 이하이거나 숫자가 아님' for i, q in enumerate(questions)
              if not isinstance(q.get('score'), int) or q['score'] <= 0]
    if issues or total_score is None:
        return issues
    total = sum(q['score'] for q in questions)
    if total != total_score:
        issues.append('배점 합계 %d점: 만점은 %d점' % (total, total_score))
    return issues


def check_answer_balance(questions: list[dict]) -> list[str]:
    """정답 번호가 한쪽으로 몰리거나 같은 번호가 길게 이어지는지 본다."""
    answers = _single_answers(questions)
    issues = []
    if len(answers) >= MIN_ITEMS_FOR_SHARE:
        counts = Counter(a for _, a in answers)
        for no in range(1, CHOICE_COUNT + 1):
            share = counts.get(no, 0) / len(answers)
            if share > MAX_SHARE:
                issues.append('정답 %s가 %d/%d개(%.0f%%): 너무 많음'
                              % (MARKS[no - 1], counts[no], len(answers), share * 100))
            elif share < MIN_SHARE:
                issues.append('정답 %s가 %d/%d개(%.0f%%): 너무 적음'
                              % (MARKS[no - 1], counts.get(no, 0), len(answers), share * 100))
    run_start = 0
    for pos in range(1, len(answers) + 1):
        if pos < len(answers) and answers[pos][1] == answers[run_start][1]:
            continue
        if pos - run_start > MAX_RUN:
            issues.append('%s~%s: 정답 %s가 %d번 연속' % (
                _label(answers[run_start][0]), _label(answers[pos - 1][0]),
                MARKS[answers[run_start][1] - 1], pos - run_start))
        run_start = pos
    return issues


def check_passage_duplicates(questions: list[dict]) -> list[str]:
    """서로 다른 문항이 같은 지문과 같은 발문을 쓰는지, 지문만 거의 같은지 본다.

    같은 지문에 다른 발문을 붙이는 것(공통 지문)은 정상이므로 알림만 하지 않는다.
    """
    issues = []
    items = [(i, _plain(q.get('sourceText')), _plain(q.get('stem'))) for i, q in enumerate(questions)]
    items = [it for it in items if len(it[1]) >= MIN_PASSAGE_CHARS]
    for a in range(len(items)):
        for b in range(a + 1, len(items)):
            (i, text_a, stem_a), (j, text_b, stem_b) = items[a], items[b]
            if text_a == text_b:
                if stem_a == stem_b:
                    issues.append('%s·%s: 지문과 발문이 모두 같음' % (_label(i), _label(j)))
                continue
            if SequenceMatcher(None, text_a, text_b).ratio() >= SIMILAR_RATIO and stem_a == stem_b:
                issues.append('%s·%s: 거의 같은 지문에 같은 발문' % (_label(i), _label(j)))
    return issues


def check_format(questions: list[dict]) -> list[str]:
    """문항마다 저장 계약 필드, 선택지 5개, 정답 범위, 발문 표기를 본다."""
    issues = []
    for i, q in enumerate(questions):
        name = _label(i)
        missing = [f for f in REQUIRED_FIELDS if f not in q]
        if missing:
            issues.append('%s: 빠진 항목 %s' % (name, ', '.join(missing)))
            continue
        if not _plain(q['stem']):
            issues.append(name + ': 발문이 비어 있음')
        if SCORE_IN_STEM.search(q['stem']):
            issues.append(name + ': 발문 앞에 점수 표기(제작원칙 1-1)')
        if q['type'] == 'mc':
            options = [_plain(o) for o in q['options']]
            if len(options) != CHOICE_COUNT:
                issues.append('%s: 선택지 %d개(5개여야 함)' % (name, len(options)))
            if any(not o for o in options):
                issues.append(name + ': 빈 선택지')
            if len(set(options)) != len(options):
                issues.append(name + ': 같은 선택지가 두 번')
            answer = q['answer']
            if not answer or any(not isinstance(a, int) or not 1 <= a <= CHOICE_COUNT for a in answer):
                issues.append(name + ': 정답 번호가 없거나 1~5 밖')
            elif len(set(answer)) != len(answer):
                issues.append(name + ': 정답 번호가 겹침')
            elif bool(q['multi']) != (len(answer) > 1):
                issues.append(name + ': 정답 개수와 "모두 고르기" 표시가 다름')
        elif q['type'] == 'sa':
            if not [a for a in q['acceptedAnswers'] if _plain(a)] and not _plain(q['sampleAnswer']):
                issues.append(name + ': 서답형 정답이 없음')
        else:
            issues.append('%s: 문항 종류 %r(mc 또는 sa여야 함)' % (name, q['type']))
    return issues


def check_exam(exam: dict) -> dict[str, list[str]]:
    """검수 전체를 돌려 구역별 문제 목록을 돌려준다. 모두 비면 통과."""
    questions = exam.get('questions') or []
    return {
        '문항 수': check_count(questions, exam.get('expectedCount')),
        '배점': check_scores(questions, exam.get('totalScore')),
        '정답 번호': check_answer_balance(questions),
        '지문 중복': check_passage_duplicates(questions),
        '양식': check_format(questions),
    }


def format_report(exam: dict, result: dict[str, list[str]]) -> str:
    """사람이 읽는 검수 결과."""
    questions = exam.get('questions') or []
    counts = Counter(a for _, a in _single_answers(questions))
    lines = ['검수: %s (%d문항, %d점)' % (
        exam.get('examTitle') or '제목 없음', len(questions),
        sum(q.get('score') or 0 for q in questions if isinstance(q.get('score'), int)))]
    lines.append('정답 분포: ' + '  '.join('%s %d' % (MARKS[n - 1], counts.get(n, 0))
                                        for n in range(1, CHOICE_COUNT + 1)))
    for section, issues in result.items():
        lines.append('[%s] %s' % ('통과' if not issues else '고칠 것 %d' % len(issues), section))
        lines.extend('  - ' + issue for issue in issues)
    lines.append('결과: ' + ('통과' if not any(result.values()) else '고친 뒤 다시 검수'))
    return '\n'.join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description='내신 예상문제 출제 검수')
    parser.add_argument('exam_json', help='문항 JSON 파일')
    parser.add_argument('--count', type=int, help='주문한 문항 수(파일의 expectedCount보다 우선)')
    parser.add_argument('--total', type=int, help='만점(파일의 totalScore보다 우선)')
    parser.add_argument('--json', action='store_true', help='결과를 JSON으로 출력')
    args = parser.parse_args(argv)
    exam = json.loads(Path(args.exam_json).read_text(encoding='utf-8'))
    if args.count is not None:
        exam['expectedCount'] = args.count
    if args.total is not None:
        exam['totalScore'] = args.total
    result = check_exam(exam)
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2) if args.json else format_report(exam, result))
    return 1 if any(result.values()) else 0


if __name__ == '__main__':
    sys.exit(main())
