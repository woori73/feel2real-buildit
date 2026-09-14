import { describe, expect, it } from 'vitest'
import {
  assignStableIds,
  buildNumberMapping,
  linkedRangeLabel,
  moveQuestion,
  renumberQuestions,
  validateExamIntegrity,
} from './weeklyExamOps'

const makeDoc = (bodyHtml: string): Document => {
  const doc = document.implementation.createHTMLDocument('test')
  doc.body.innerHTML = bodyHtml
  return doc
}

describe('weeklyExamOps — stable question identity', () => {
  it('assigns stable questionIds once and keeps them stable on re-run', () => {
    // given: questionId 없는 두 문항
    const doc = makeDoc('<div class="qblock" data-qnum="1"><span class="qnum">1.</span></div><div class="qblock" data-qnum="2"><span class="qnum">2.</span></div>')
    // when
    const refs = assignStableIds(doc)
    const again = assignStableIds(doc)
    // then: 재실행해도 동일 id 유지
    expect(refs).toHaveLength(2)
    expect(again.map(r => r.questionId)).toEqual(refs.map(r => r.questionId))
    expect(refs[0].questionId).not.toBe(refs[1].questionId)
  })
})

describe('weeklyExamOps — move + renumber + sync mapping', () => {
  it('keeps questionId while displayNumber changes, and maps old→new numbers', () => {
    // given: Q1, Q2
    const doc = makeDoc('<div class="qblock" data-qnum="1" id="a"></div><div class="qblock" data-qnum="2" id="b"></div>')
    const refs = assignStableIds(doc)
    const a = refs[0].questionId
    // when: Q2를 위로 이동 후 재번호화
    const moved = moveQuestion(doc, refs[1].questionId, 'up')
    renumberQuestions(doc)
    // then: questionId 불변, displayNumber만 변경
    expect(moved).toBe(true)
    const after = assignStableIds(doc)
    expect(after[0].questionId).toBe(refs[1].questionId) // 원 Q2가 1번으로
    expect(after[0].displayNumber).toBe(1)
    expect(after[1].questionId).toBe(a) // 원 Q1은 2번으로, id 동일
    expect(after[1].displayNumber).toBe(2)
    const mapping = buildNumberMapping(makeDoc(doc.body.innerHTML), doc) // 문서 순서 기준 매핑 산출 가능
    expect(Object.keys(mapping).length).toBeGreaterThan(0)
  })

  it('renumberQuestions rewrites data-qnum and .qnum text in doc order', () => {
    // given
    const doc = makeDoc('<div class="qblock" data-qnum="5"><span class="qnum">5.</span></div><div class="qblock" data-qnum="9"><span class="qnum">9.</span></div>')
    // when
    renumberQuestions(doc)
    // then
    expect(doc.querySelectorAll('.qblock')[0].getAttribute('data-qnum')).toBe('1')
    expect(doc.querySelectorAll('.qblock')[1].getAttribute('data-qnum')).toBe('2')
    expect(doc.querySelector('.qnum')!.textContent).toBe('1.')
  })
})

describe('weeklyExamOps — integrity gate', () => {
  it('flags MISSING_ANSWER / MISSING_EXPLANATION / QUESTION_ANSWER_MISMATCH', () => {
    // given
    const refs = [
      { questionId: 'q-1', displayNumber: 1 },
      { questionId: 'q-2', displayNumber: 2 },
    ]
    // when: q-2 정답/해설 누락 + 정의되지 않은 정답 존재
    const issues = validateExamIntegrity(refs, { 'q-1': '③', 'q-9': '①' }, { 'q-1': '해설' })
    // then
    const codes = issues.map(issue => issue.code)
    expect(codes).toContain('MISSING_ANSWER')
    expect(codes).toContain('MISSING_EXPLANATION')
    expect(codes).toContain('QUESTION_ANSWER_MISMATCH')
  })

  it('returns no issues for a complete 1:1 paper', () => {
    // given/when
    const refs = [{ questionId: 'q-1', displayNumber: 1 }]
    const issues = validateExamIntegrity(refs, { 'q-1': '③' }, { 'q-1': '해설' })
    // then
    expect(issues).toEqual([])
  })
})

describe('weeklyExamOps — linked passage range', () => {
  it('builds compact range labels from linked question numbers', () => {
    // given/when/then
    expect(linkedRangeLabel([6, 7])).toBe('6~7')
    expect(linkedRangeLabel([10, 11, 12, 13])).toBe('10~13')
    expect(linkedRangeLabel([21])).toBe('21')
  })
})
