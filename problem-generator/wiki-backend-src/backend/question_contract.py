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
