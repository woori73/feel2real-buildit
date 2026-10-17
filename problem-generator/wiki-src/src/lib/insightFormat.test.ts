import { describe, expect, it } from 'vitest'
import { formatInsightDate, insightExamLine, insightStudentLine } from './insightFormat'

describe('insightFormat',()=>{
  it('날짜는 초 없이 한 줄 문자열로 만든다',()=>{
    // given
    const iso=new Date(2026,9,16,7,25,42).toISOString()
    // when
    const text=formatInsightDate(iso)
    // then
    expect(text).toContain('2026')
    expect(text).toContain('7:25')
    expect(text).not.toContain('7:25:42')
    expect(text).not.toContain('\n')
  })
  it('해석할 수 없는 날짜는 빈 문자열',()=>{
    // given/when/then
    expect(formatInsightDate('not-a-date')).toBe('')
  })
  it('학교·학년·학기·시험은 한 번만, 빈 값은 생략한다',()=>{
    // given
    const i={created_at:'',school:'신사중',grade:'3학년',year:2026,semester:'2학기',exam:'기말고사'}
    // when
    const line=insightExamLine(i)
    // then
    expect(line).toBe('신사중 · 3학년 · 2026년 · 2학기 · 기말고사')
    expect(insightExamLine({created_at:'',school:'신사중',grade:'',exam:''})).toBe('신사중')
    expect(insightExamLine({created_at:''})).toBe('')
  })
  it('학생·수업 맥락 줄은 값이 있는 것만 만든다',()=>{
    // given/when/then
    expect(insightStudentLine({created_at:'',lesson_context:'문법'})).toBe('수업 · 문법')
    expect(insightStudentLine({created_at:''})).toBe('')
  })
})
