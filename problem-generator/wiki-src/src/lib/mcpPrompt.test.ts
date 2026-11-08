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
  it('예상문제는 위키 저장 형식 그대로 시험 값을 넣고 출제 원칙 6개(동태밀도 배분 포함)를 담는다', () => {
    // given
    const input = job({ count: 40 })
    // when
    const prompt = buildMcpPrompt(context, input)
    // then
    expect(prompt.split('\n')[0]).toBe('[BuildIt Wiki] 신사중 3학년 · 2026 2학기 기말고사 · 예상문제 시험지 40문항')
    expect(prompt).toContain('get_final_context(school="신사중", grade="3학년", year=2026, semester="2학기", exam="기말고사")로 자료를 읽는다.')
    expect(prompt).toContain('■ 출제 원칙')
    expect(prompt).toContain('1. 해당 학교 기출을 철저히 분석하고 저장한다.')
    expect(prompt).toContain('5. 시험지는 해당 학교 시험지와 똑같이 편집한다(폰트, 글씨 크기, 줄간격). 학원 로고를 넣고, 하단의 학교 저작권 문구는 뺀다.')
    expect(prompt).toContain('6. 범위 안 항목(본문·문법)마다 동태밀도 D = 기출 출제 비중 × 최근 증가량 ÷ (범위 분량 + ε)를 계산해, D가 높은 항목에 문항을 더 배정하고 계산표를 함께 보고한다.')
    expect(prompt).not.toContain('7.')
    expect(prompt.trimEnd().endsWith('세부 작업은 사용자 지시에 따른다.')).toBe(true)
    expect(prompt).not.toContain('Q1~20')
    expect(prompt).not.toContain('문항 구성')
  })

  it('직접 지정한 서답형 수는 전체 문항 수를 넘지 않는다', () => {
    // given
    const input = job({ count: 20, mix: 'custom', saCount: 50 })
    // when
    const prompt = buildMcpPrompt(context, input)
    // then
    expect(prompt).toContain('문항 구성: 객관식 0문항 · 서답형 20문항')
  })

  it('직보는 학교 프린트(학습지 원본)만 근거로 하고 FINAL을 요구하지 않는다', () => {
    // given
    const input = job({ kind: 'jikbo' })
    // when
    const prompt = buildMcpPrompt(context, input)
    // then
    expect(prompt.split('\n')[0]).toBe('[BuildIt Wiki] 신사중 3학년 · 2026 2학기 기말고사 · 직보')
    expect(prompt).toContain('get_final_context(school="신사중", grade="3학년", year=2026, semester="2학기", exam="기말고사")로 학교 프린트(학습지 원본)를 읽는다.')
    expect(prompt).toContain('■ 제작 원칙')
    expect(prompt).toContain('1. 학습지 원본만 근거로 만든다.')
    expect(prompt).toContain('2. 원본에 없는 내용, 문구, 번호는 추가하거나 바꾸지 않는다.')
    expect(prompt).not.toContain('FINAL')
    expect(prompt).not.toContain('출제 원칙')
    expect(prompt).not.toContain('동태밀도')
  })

  it('추가 요청이 있으면 마지막 줄 앞에 붙이고 공백뿐이면 넣지 않는다', () => {
    // given
    const withNote = job({ note: '  Lesson 5 본문 위주  ' })
    const blankNote = job({ note: '   ' })
    // when
    const a = buildMcpPrompt(context, withNote)
    const b = buildMcpPrompt(context, blankNote)
    // then
    expect(a).toContain('추가 요청: Lesson 5 본문 위주\n\n세부 작업은 사용자 지시에 따른다.')
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
