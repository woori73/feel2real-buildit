"""T05 검증 파이프라인 — PLAN 4-5.

문항 하나: 형식(question_contract) → 근거 대조(인용 id·문장이 실제 청크에 있는지, 지문이 원문에서 왔는지)
→ 독립 재풀이(생성 모델과 다른 모델) → 판정(verify.status). 시험 하나: 배포 게이트 + 샘플 오딧 선정.

DB·모델을 직접 부르지 않는다. 재풀이 모델은 solver(callable)로 받는다(T04 모델 라우터가 연결되면
라우터 호출을 넘긴다). 결과는 save_generated_test의 evidence·verify 칸과 T08 평가 행(verification,
auto_pass, audit)에 그대로 넣을 수 있는 모양으로 돌려준다.
"""
import random
import re
import unicodedata
from dataclasses import dataclass, field

try:  # 패키지(backend.verify_pipeline)·스크립트 경로 둘 다 지원
    from .question_contract import validate_transform
except ImportError:  # pragma: no cover
    from question_contract import validate_transform

STATUS_MATCH, STATUS_MISMATCH, STATUS_MULTI, STATUS_WEAK = '일치', '불일치', '복수정답 의심', '해설 근거 부족'
PASSAGE_MIN_RATIO = 0.9  # 지문 문장 중 원문에서 찾아야 하는 최소 비율(내신: 지문은 원문 그대로)


def _norm(text):
    return ' '.join(unicodedata.normalize('NFKC', text or '').split())


def _sentences(text):
    return [s for s in re.split(r'(?<=[.!?])\s+', _norm(text)) if len(s) >= 20]


@dataclass
class QuestionCheck:
    contract_ok: bool
    contract_error: str = ''
    evidence_ok: bool = False
    evidence_issues: list = field(default_factory=list)
    passage_ratio: float | None = None
    independent_answer: list | str | None = None
    verifier_model: str = ''
    status: str = ''
    note: str = ''

    @property
    def auto_pass(self):
        return self.contract_ok and self.evidence_ok and self.status == STATUS_MATCH

    def verify_field(self):
        """save_generated_test의 문항 verify 칸."""
        ans = self.independent_answer
        shown = ','.join(map(str, ans)) if isinstance(ans, list) else (ans or '')
        return {'independent_answer': shown, 'status': self.status, 'note': self.note}

    def eval_fields(self):
        """T08 평가 행에 붙일 verification·auto_pass."""
        out = {'auto_pass': self.auto_pass}
        if self.independent_answer is not None and self.verifier_model:
            out['verification'] = {'model': self.verifier_model, 'answer': self.independent_answer}
        return out


def check_contract(question):
    try:
        validate_transform({'examTitle': 'verify', 'questions': [question]})
        return True, ''
    except (ValueError, TypeError) as exc:
        return False, str(exc).splitlines()[0][:200]


def check_evidence(question, evidence_refs, chunks, passage_must_match=True):
    """인용이 실제 청크에 있는지, 문항 지문 문장이 근거 청크에서 왔는지 문자열로 확인한다(의미 검증 아님)."""
    texts = {c['id']: _norm(c['text']) for c in chunks or []}
    issues = []
    if not evidence_refs:
        issues.append('근거 인용 없음')
    for ref in evidence_refs or []:
        rid, quote = ref.get('id'), _norm(ref.get('quote', ''))
        if rid not in texts:
            issues.append(f'없는 청크 id: {rid}')
        elif not quote or quote not in texts[rid]:
            issues.append(f'청크 {rid}에 없는 인용: {quote[:60]}')
    ratio = None
    sents = _sentences(question.get('sourceText', ''))
    if sents and texts:
        joined = ' '.join(texts.values())
        ratio = sum(s in joined for s in sents) / len(sents)
        if passage_must_match and ratio < PASSAGE_MIN_RATIO:
            issues.append(f'지문 문장 중 원문에 있는 비율 {ratio:.0%}')
    return not issues, issues, ratio


def _key(question):
    if question.get('type') == 'mc':
        return sorted(question.get('answer') or [])
    return _norm(question.get('sampleAnswer', ''))


def blind(question):
    """정답·예시답을 가린 문항(재풀이 모델에 보낼 것)."""
    q = dict(question)
    q['answer'], q['sampleAnswer'], q['acceptedAnswers'] = [], '', []
    return q


def independent_solve(question, solver, generator_model):
    """solver(blind question) → {'model': 정규 모델 ID, 'answer': [번호] 또는 서답 문자열}.
    생성 모델과 같은 모델이면 독립 검증으로 치지 않는다."""
    result = solver(blind(question))
    model = (result or {}).get('model', '')
    if not model or model == generator_model:
        return None, model, '재풀이 모델이 생성 모델과 같거나 비어 있어 독립 검증 아님'
    answer = result.get('answer')
    if question.get('type') == 'mc':
        answer = sorted(int(a) for a in (answer or []))
        key = _key(question)
        if not question.get('multi') and len(answer) > 1:
            return answer, model, STATUS_MULTI
        return answer, model, STATUS_MATCH if answer == key else STATUS_MISMATCH
    accepted = {_norm(a) for a in question.get('acceptedAnswers') or []} | {_key(question)}
    return answer, model, STATUS_MATCH if _norm(answer) in accepted else STATUS_MISMATCH


def verify_question(question, evidence_refs, chunks, *, solver=None, generator_model='', explanation='',
                    passage_must_match=True):
    ok, err = check_contract(question)
    check = QuestionCheck(contract_ok=ok, contract_error=err)
    if not ok:
        check.status, check.note = STATUS_WEAK, '형식 검사 실패: ' + err
        return check
    check.evidence_ok, check.evidence_issues, check.passage_ratio = check_evidence(
        question, evidence_refs, chunks, passage_must_match)
    if solver is None:
        check.note = '재풀이 전(검수 대기)'
    else:
        answer, model, status = independent_solve(question, solver, generator_model)
        check.independent_answer, check.verifier_model = answer, model
        if answer is None:
            check.note = status
        else:
            check.status = status
    if check.status == STATUS_MATCH and not check.evidence_ok:
        check.status, check.note = STATUS_WEAK, '; '.join(check.evidence_issues)
    if check.status == STATUS_MATCH and explanation and question.get('type') == 'mc':
        marks = '①②③④⑤'
        head = re.search(r'정답\s*([①-⑤](?:\s*[,·와과및]?\s*[①-⑤])*)', explanation)
        named = {marks.index(c) + 1 for c in re.findall('[①-⑤]', head.group(1))} if head else set()
        if named and named != set(question['answer']):
            check.status, check.note = STATUS_MISMATCH, '해설의 정답 번호와 정답 키가 다름'
    return check


def release_gate(checks):
    """모든 문항이 자동 통과여야 '배포 가능'. 하나라도 아니면 '검수 대기'(고객 화면에 내보내지 않음)."""
    held = [i for i, c in enumerate(checks) if not c.auto_pass]
    return {'status': '배포 가능' if checks and not held else '검수 대기', 'held_indexes': held,
            'auto_pass_count': len(checks) - len(held), 'total': len(checks)}


def select_audit(question_ids, rate=0.1, seed=None):
    """자동 통과 문항 중 rate 비율(최소 1개)을 무작위로 골라 교사 검수 목록에 올린다."""
    ids = list(question_ids)
    if not ids:
        return []
    k = max(1, round(len(ids) * rate))
    return sorted(random.Random(seed).sample(ids, k))
