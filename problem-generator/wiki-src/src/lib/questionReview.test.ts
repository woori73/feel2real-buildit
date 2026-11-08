import { describe, expect, it } from 'vitest'
import { reviewLine } from './questionReview'

const base = { q_kind: 'mc' as const, answer: [2], accepted_answers: [] as string[] }

describe('reviewLine', () => {
  it('given 근거·검증·교사 조치가 있는 문항 when 한 줄로 만들면 then 모두 순서대로 담는다', () => {
    // given
    const q = { ...base,
      evidence: { past_exam: '2025 2학기 기말 18번', school_print: '학교 프린트 3쪽', changed: '' },
      verify: { independent_answer: '④', status: '복수정답 의심' as const, note: '④도 정답 가능' },
      teacher: { action: '선택지 수정 후 재검증' } }
    // when
    const line = reviewLine(q)
    // then
    expect(line).toBe('검수 필요(복수정답 의심) · 근거: 2025 2학기 기말 18번 / 학교 프린트 3쪽 · 제작 정답 ② · 독립 풀이 ④ · 쟁점: ④도 정답 가능 · 교사 조치: 선택지 수정 후 재검증')
  })

  it('given 검증이 일치한 문항 when 한 줄로 만들면 then 검증 일치로 표시한다', () => {
    // given
    const q = { ...base, verify: { independent_answer: '②', status: '일치' as const, note: '' } }
    // when / then
    expect(reviewLine(q)).toBe('검증 일치 · 제작 정답 ② · 독립 풀이 ②')
  })

  it('given 기록이 없는 기존 문항 when 한 줄로 만들면 then null을 돌려준다', () => {
    // given / when / then
    expect(reviewLine({ ...base, evidence: null, verify: null, teacher: null })).toBeNull()
  })
})
