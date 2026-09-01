import { describe, expect, it } from 'vitest'
import { buildTransformProvenance, questionOrigin, stampCanonicalSource } from './questionSource'
import type { StoredQuestion } from './types'
import type { WikiQuestionV1 } from './wikiQuestion'

const base: StoredQuestion = {
  id: 91, twin_id: null, school: '', grade: '3학년', exam: '', mode: 'textbook', q_kind: 'mc', question_type: '어법',
  stem: 's', source_text: 'p', options: ['a', 'b'], answer: [1], multi: 0, score: 3, conditions: [], answer_format: '',
  accepted_answers: [], sample_answer: '', grading_rule: '', exam_title: '', exam_group_id: '', favorite: 0, created_at: 'now',
  source_kind: 'textbook', page: 32, question_number: '12', textbook_material_id: 7, publisher: '동아(윤정미)', lesson: '5과',
}
const ctx = { school: '신구중', grade: '3학년', year: 2026, semester: '2학기', exam: '중간고사' }
const canonical = { schema_version: 'wiki-question-v1', kind: 'mc', question_type: '어법', instruction: { text: 's', html: '<p>s</p>' }, passage: { text: 'p', html: '<p>p</p>', status: 'complete' }, choices: [], conditions: [], answer: { choice_numbers: [1], accepted_answers: [], sample_answer: '' }, explanation: { text: '', html: '' }, scoring: { score: 3, multi: false, answer_format: '', grading_rule: '' }, source: {} } as unknown as WikiQuestionV1

describe('교과서 transform provenance — PDF 페이지와 Question ID를 섞지 않는다', () => {
  it('buildTransformProvenance: source_page 는 PDF 페이지, id/번호/material은 독립 필드', () => {
    const { provenance, source_page, source_material } = buildTransformProvenance(base, ctx, { revision_note: 'x' })
    expect(source_page).toBe('32')                       // generated_tests.source_page = PDF 페이지
    expect(source_page).not.toContain('textbook-question')
    expect(source_material).not.toContain('textbook-question')
    expect(provenance.source_kind).toBe('textbook')
    expect(provenance.source_question_id).toBe(91)       // textbook_questions.id
    expect(provenance.textbook_material_id).toBe(7)      // 원본 PDF
    expect(provenance.source_page).toBe(32)              // PDF 페이지 (number)
    expect(provenance.source_question_number).toBe('12')
  })

  it('teacher Source 는 기존 question:{id} 규칙 유지', () => {
    const teacher = { ...base, source_kind: 'teacher' as const, school: '신구중', source_question_number: '7', page: undefined, textbook_material_id: undefined }
    const { provenance, source_page } = buildTransformProvenance(teacher, ctx, {})
    expect(source_page).toBe('question:91')
    expect(provenance.source_kind).toBe('teacher')
    expect(provenance.source_page).toBeNull()
    expect(provenance.textbook_material_id).toBeNull()
    expect(provenance.source_question_number).toBe('7')
  })

  it('stampCanonicalSource: canonical.source 에도 페이지/번호/kind를 독립 각인', () => {
    const stamped = stampCanonicalSource(canonical, base, ctx)
    expect(stamped.source.source_kind).toBe('textbook')
    expect(stamped.source.source_page).toBe('32')
    expect(stamped.source.source_question_number).toBe('12')
    expect(stamped.source.source_material_id).toBe(7)
    expect(stamped.source.publisher).toBe('동아(윤정미)')
  })

  it('questionOrigin: 사람이 읽는 출처 라인', () => {
    expect(questionOrigin(base)).toBe('교과서 DB · 동아(윤정미) · 5과 · p.32 · 12번 · QID 91')
    expect(questionOrigin({ ...base, source_kind: 'teacher', school: '신구중' })).toBe('강사 문제 DB · 신구중3학년 · QID 91')
  })

  it('page 가 없는 교과서 문항은 source_page 가 빈 문자열(잘못된 값 안 만듦)', () => {
    const { provenance, source_page } = buildTransformProvenance({ ...base, page: undefined }, ctx, {})
    expect(source_page).toBe('')
    expect(provenance.source_page).toBeNull()
  })
})
