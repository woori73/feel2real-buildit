import { describe, expect, it } from 'vitest'
import { buildMcpPrompt, mcpEndpointUrl, splitBatches, summarizeFinalContext, type McpJob } from './mcpPrompt'

const context = { school: '신사중', grade: '3학년', year: 2026, semester: '2학기', exam: '기말고사' }
const job = (over: Partial<McpJob> = {}): McpJob => ({ kind: 'expected', count: 40, mix: 'school', saCount: 0, note: '', ...over })

describe('splitBatches', () => {
  it('70문항은 20문항씩 나누고 나머지를 마지막에 둔다', () => {
    // given
    const count = 70
    // when
    const batches = splitBatches(count)
    // then
    expect(batches).toEqual(['Q1~20', 'Q21~40', 'Q41~60', 'Q61~70'])
  })

  it('0 이하 문항 수는 빈 목록을 돌려준다', () => {
    // given / when
    const batches = splitBatches(0)
    // then
    expect(batches).toEqual([])
  })
})

describe('mcpEndpointUrl', () => {
  it('사이트 주소 끝의 슬래시와 상관없이 MCP 주소를 만든다', () => {
    // given
    const origin = 'https://feel2real.co.kr/'
    // when
    const url = mcpEndpointUrl(origin)
    // then
    expect(url).toBe('https://feel2real.co.kr/problem-generator/wiki/api/mcp')
  })
})

describe('buildMcpPrompt', () => {
  it('예상문제는 시험 Context 인자와 batch 순서, 저장 요청을 담는다', () => {
    // given
    const input = job({ count: 40 })
    // when
    const prompt = buildMcpPrompt(context, input)
    // then
    expect(prompt).toContain('get_final_context(school="신사중", grade="3학년", year=2026, semester="2학기", exam="기말고사")')
    expect(prompt).toContain('Q1~20 → Q21~40')
    expect(prompt).toContain('save_generated_questions(school="신사중", grade="3학년", exam="기말고사"')
    expect(prompt).toContain('학교 기출의 객관식·서답형 비율')
  })

  it('직접 지정한 서답형 수는 전체 문항 수를 넘지 않는다', () => {
    // given
    const input = job({ count: 20, mix: 'custom', saCount: 50 })
    // when
    const prompt = buildMcpPrompt(context, input)
    // then
    expect(prompt).toContain('객관식 0문항 · 서답형 20문항')
  })

  it('직보는 get_jikbo_context와 save_jikbo를 쓰고 batch를 만들지 않는다', () => {
    // given
    const input = job({ kind: 'jikbo' })
    // when
    const prompt = buildMcpPrompt(context, input)
    // then
    expect(prompt).toContain('get_jikbo_context(')
    expect(prompt).toContain('save_jikbo(')
    expect(prompt).not.toContain('Q1~20')
  })

  it('추가 요청이 있으면 지시문 끝에 붙이고 공백뿐이면 넣지 않는다', () => {
    // given
    const withNote = job({ note: '  Lesson 5 본문 위주  ' })
    const blankNote = job({ note: '   ' })
    // when
    const a = buildMcpPrompt(context, withNote)
    const b = buildMcpPrompt(context, blankNote)
    // then
    expect(a).toContain('추가 요청: Lesson 5 본문 위주')
    expect(b).not.toContain('추가 요청')
  })
})

describe('summarizeFinalContext', () => {
  it('목록 길이와 Digital Twin 유무를 자료 수로 바꾼다', () => {
    // given
    const body = { past_exams: [{}, {}], school_prints: [], digital_twin: { id: 1 }, class_insights: [{}] }
    // when
    const rows = summarizeFinalContext(body)
    // then
    const count = (key: string) => rows.find(row => row.key === key)?.count
    expect(count('past_exams')).toBe(2)
    expect(count('school_prints')).toBe(0)
    expect(count('digital_twin')).toBe(1)
    expect(count('class_insights')).toBe(1)
    expect(count('final_deliverables')).toBe(0)
  })

  it('응답이 없으면 모든 자료 수를 0으로 둔다', () => {
    // given / when
    const rows = summarizeFinalContext(null)
    // then
    expect(rows.every(row => row.count === 0)).toBe(true)
  })
})
