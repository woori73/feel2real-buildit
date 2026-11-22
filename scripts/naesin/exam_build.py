"""검수를 통과한 문항 JSON으로 A4 문제지와 정답·해설지 HTML을 만든다.

    python scripts/naesin/exam_build.py exam.json --out-dir 결과폴더

입력은 exam_check.py와 같은 파일이다(내신위키 저장 계약 문항 + examTitle 등).
문제지는 exam_paper.py가 감독 승인본(신구중2 11회)과 같은 편집으로 조판하고,
정답·해설지는 problem-generator/wiki-backend-src/backend/weekly_paper.py를 그대로 쓴다.
출제 검수(exam_check)에서 고칠 것이 나오면 시험지를 만들지 않는다.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import exam_check
import exam_paper

MARKS = exam_check.MARKS
BACKEND_DIR = Path(__file__).resolve().parents[2] / 'problem-generator' / 'wiki-backend-src' / 'backend'


def load_renderer():
    """정답·해설지를 그리는 weekly_paper 모듈을 찾는다. 같은 폴더에 복사본이 있으면 그것을, 없으면 저장소의 것을 쓴다."""
    try:
        import weekly_paper
    except ImportError:
        sys.path.insert(0, str(BACKEND_DIR))
        import weekly_paper
    return weekly_paper


def answer_label(question: dict) -> str:
    """빠른 정답 칸에 넣을 글: 객관식은 ③ 또는 ②, ④, 서답형은 정답 글."""
    if question.get('type') == 'mc':
        return ', '.join(MARKS[a - 1] for a in sorted(question.get('answer') or []))
    accepted = [a for a in question.get('acceptedAnswers') or [] if a.strip()]
    return ' / '.join(accepted) if accepted else (question.get('sampleAnswer') or '')


def to_explanation_row(question: dict, no: int) -> dict:
    """정답·해설지 한 줄. 해설은 verify.note, 근거는 evidence에서 가져온다."""
    verify = question.get('verify') or {}
    evidence = question.get('evidence') or {}
    meta = ['유형: %s' % question.get('questionType', ''), '배점: %s점' % question.get('score', '')]
    for label, key in (('기출 근거', 'past_exam'), ('범위 근거', 'school_print')):
        if evidence.get(key):
            meta.append('%s: %s' % (label, evidence[key]))
    if verify.get('status'):
        meta.append('독립 검증: %s' % verify['status'])
    return {'no': no, 'answer': answer_label(question), 'expl': verify.get('note') or '', 'meta': ' · '.join(meta)}


def summarize(questions: list[dict]) -> dict:
    """해설지 맨 위 요약표. 독립 검증이 '일치'인 문항만 VERIFIED로 센다."""
    verified = sum(1 for q in questions if (q.get('verify') or {}).get('status') == '일치')
    return {
        'total': len(questions),
        'mc': sum(1 for q in questions if q.get('type') == 'mc'),
        'sa': sum(1 for q in questions if q.get('type') == 'sa'),
        'multi': sum(1 for q in questions if q.get('multi')),
        'verified': verified,
        'review': len(questions) - verified,
    }


def build(exam: dict, renderer=None, asset_dir: Path | None = None) -> tuple[str, str]:
    """(문제지 HTML, 정답·해설지 HTML)을 돌려준다. 문제지는 exam_paper, 정답·해설지는 weekly_paper가 그린다."""
    renderer = renderer or load_renderer()
    questions = exam.get('questions') or []
    title = exam.get('examTitle') or '내신 예상문제'
    paper = exam_paper.render_paper(exam, asset_dir)
    rows = [to_explanation_row(q, i + 1) for i, q in enumerate(questions)]
    explanation = renderer.render_answer_explanation(
        title=title + ' 정답 및 해설', subtitle='교사용', summary=summarize(questions),
        quick_answers=[row['answer'] for row in rows], rows=rows)
    return paper, explanation


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description='내신 예상문제 시험지 조립')
    parser.add_argument('exam_json', help='검수할 문항 JSON 파일')
    parser.add_argument('--out-dir', help='결과 폴더(기본: 문항 파일과 같은 폴더)')
    parser.add_argument('--asset-dir', help='그림 폴더([[img:…]] 파일을 저장소 static에서 못 찾을 때 파일 이름으로 찾는 곳)')
    args = parser.parse_args(argv)
    source = Path(args.exam_json)
    exam = json.loads(source.read_text(encoding='utf-8'))
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    result = exam_check.check_exam(exam)
    print(exam_check.format_report(exam, result))
    if any(result.values()):
        print('시험지를 만들지 않았습니다. 위 항목을 고친 뒤 다시 실행하세요.')
        return 1
    out_dir = Path(args.out_dir) if args.out_dir else source.parent
    out_dir.mkdir(parents=True, exist_ok=True)
    paper, explanation = build(exam, asset_dir=Path(args.asset_dir) if args.asset_dir else None)
    missing = paper.count('class="fig missing"')
    if missing:
        print('주의: 그림 %d개를 찾지 못했습니다(문제지에 빨간 점선 칸으로 표시). --asset-dir을 확인하세요.' % missing)
    paper_path = out_dir / (source.stem + '_문제지.html')
    explanation_path = out_dir / (source.stem + '_정답해설.html')
    paper_path.write_text(paper, encoding='utf-8')
    explanation_path.write_text(explanation, encoding='utf-8')
    print('문제지: %s\n정답·해설: %s' % (paper_path, explanation_path))
    return 0


if __name__ == '__main__':
    sys.exit(main())
