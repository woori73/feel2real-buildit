"""Canonical validation for generated questions before persistence."""
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError


class _StrictModel(BaseModel):
    model_config = ConfigDict(strict=True, extra='forbid', populate_by_name=True)


class MultipleChoiceQuestion(_StrictModel):
    question_type: str = Field(alias='questionType')
    source_text: str = Field(alias='sourceText')
    stem: str
    options: list[str]
    answer: list[int]
    multi: bool
    score: int


class ShortAnswerQuestion(_StrictModel):
    question_type: str = Field(alias='questionType')
    source_text: str = Field(alias='sourceText')
    stem: str
    conditions: list[str]
    answer_format: str = Field(alias='answerFormat')
    accepted_answers: list[str] = Field(alias='acceptedAnswers')
    sample_answer: str = Field(alias='sampleAnswer')
    grading_rule: str = Field(alias='gradingRule')
    score: int


class NewQuestionResponse(_StrictModel):
    exam_title: str = Field(alias='examTitle')
    mc_questions: list[MultipleChoiceQuestion] = Field(alias='mcQuestions')
    sa_questions: list[ShortAnswerQuestion] = Field(alias='saQuestions')


class QuestionEvidence(_StrictModel):
    """문항 근거: 기출(학교·연도·학기·시험·문항번호, 자료 id), 학교 프린트(자료 id·쪽), 바꾼 부분."""
    past_exam: str = ''
    school_print: str = ''
    changed: str = ''


VERIFY_STATUSES = ('일치', '불일치', '복수정답 의심', '해설 근거 부족')


class QuestionVerify(_StrictModel):
    """독립 검증: 정답을 가리고 다시 푼 답, 상태, 쟁점."""
    independent_answer: str = ''
    status: Literal['', '일치', '불일치', '복수정답 의심', '해설 근거 부족'] = ''
    note: str = ''


class QuestionTeacher(_StrictModel):
    """교사 조치와 수정 전·후."""
    action: str = ''


class TransformQuestion(_StrictModel):
    type: Literal['mc', 'sa']
    question_type: str = Field(alias='questionType')
    source_text: str = Field(alias='sourceText')
    stem: str
    options: list[str]
    answer: list[int]
    multi: bool
    score: int
    conditions: list[str]
    answer_format: str = Field(alias='answerFormat')
    accepted_answers: list[str] = Field(alias='acceptedAnswers')
    sample_answer: str = Field(alias='sampleAnswer')
    grading_rule: str = Field(alias='gradingRule')
    canonical: dict | None = None
    source_question_number: str = Field(default='', alias='sourceQuestionNumber')
    # 문항 단위 근거·검증 기록(선택). 없으면 None으로 저장되고 기존 문항과 호환된다.
    evidence: QuestionEvidence | None = None
    verify: QuestionVerify | None = None
    teacher: QuestionTeacher | None = None


class TransformResponse(_StrictModel):
    exam_title: str = Field(alias='examTitle')
    questions: list[TransformQuestion]


ALIASES = {
    'exam_title': 'examTitle', 'mc_questions': 'mcQuestions', 'sa_questions': 'saQuestions',
    'question_type': 'questionType', 'source_text': 'sourceText', 'answer_format': 'answerFormat',
    'accepted_answers': 'acceptedAnswers', 'sample_answer': 'sampleAnswer', 'grading_rule': 'gradingRule',
}


def normalize_aliases(value: Any) -> Any:
    """Normalize only documented field aliases; never guess or coerce values."""
    if isinstance(value, list):
        return [normalize_aliases(item) for item in value]
    if not isinstance(value, dict):
        return value
    normalized = {}
    for key, item in value.items():
        canonical = ALIASES.get(key, key)
        if canonical in normalized and canonical != key:
            raise ValueError(f'duplicate alias for {canonical}')
        normalized[canonical] = normalize_aliases(item)
    return normalized


def validate_new(value: dict) -> dict:
    return NewQuestionResponse.model_validate(normalize_aliases(value)).model_dump(by_alias=True)


def validate_transform(value: dict) -> dict:
    return TransformResponse.model_validate(normalize_aliases(value)).model_dump(by_alias=True)


def validation_fields(exc: ValidationError) -> str:
    """Return bounded field/type diagnostics without response content."""
    return '; '.join(f"{'.'.join(map(str, error['loc']))}: {error['type']}" for error in exc.errors()[:5])
