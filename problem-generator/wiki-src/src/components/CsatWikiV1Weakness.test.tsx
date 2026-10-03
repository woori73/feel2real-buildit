import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import CsatWikiV1 from './CsatWikiV1'

const steps = ['제시문마다 앞뒤 문장의 접속 신호와 지시어에 밑줄', '앞 문장의 마지막 어휘와 뒷 문장의 재진술을 짝짓기', '짝이 확정된 문장 쌍부터 임시 배치']

const card = {
  key: 'order', category: '논리', title: '글의 순서 — 제시문 연결 순서 판단', type_name: '글의 순서',
  wrong_count: 39, source_count: 23, sources: ['모의A', '모의B'], student_answer_known: false,
  problem: '등록된 오답 원문 39건이 「글의 순서」 유형에 모여 있습니다. 23개의 서로 다른 시험지에서 반복된 틀림입니다.',
  failed_process: '확인 가능한 근거는 결과와 유형뿐입니다. 학생이 고른 선지는 원문에 표기되어 있지 않아 확인하지 못했습니다.',
  evidence: [{ item_id: 'item-1', source: '모의A', number: '36', type_label: '글의 순서', student_answer: null, answer: '3', answer_known: true, excerpt: '제시문의 순서로 가장 적절한 것은?' }],
  exam_dna: [{ exam_id: 1, exam_label: '2027학년도 6월 모의평가 영어', question_no: 36, entry_type: 'DNA', tag: 'COHESION', choice_no: null, note: '제시문 연결: 재진술 어휘 짝짓기', provenance: 'ACTUAL_EXAM', expert: '', agreement: false, agreement_kind: '', timestamp_start: null, video_url: '', status: 'TEACHER_CONFIRMED', evidence: [{ question_no: 36, exam_id: 1 }] }],
  exam_dna_tags: [{ entry_type: 'DNA', tag: 'COHESION', count: 1 }],
  claims: [{ exam_id: 1, exam_label: '2027학년도 6월 모의평가 영어', claim_type: 'REPEATED_SKILL', tag: 'COHESION', claim_text: '36·37번 제시문 배열은 재진술 어휘 짝짓기 요구', provenance: 'ACTUAL_EXAM', expert: '', agreement: false, agreement_kind: '', timestamp_start: null, video_url: '', status: 'AI_PROPOSED', evidence: [{ question_no: 36, exam_id: 1 }] }],
  steps,
}

const analysis = {
  student: { id: 7, name: '최승원' }, wrong_html_id: 1,
  original_name: '최승원_오답_RAW_TEXT_DB_176문항_2026-09-30.html',
  wrong_total: 176, dna_count: 10, observation_count: 1, student_answer_known: false,
  analyzed_at: '2026-10-03T12:00:00.000000',
  prescription: [{ key: 'order', category: '논리', title: card.title, wrong_count: 39, steps }],
  observations: [{ key: 'claim', category: '문항 풀이', title: '필자 주장 — 주장과 근거 구분', type_name: '주장', wrong_count: 1, source_count: 1, sources: ['모의A'], reason: '반복 규칙을 넘기지 못한 관찰 건입니다. 약점으로 단정하지 않습니다.', evidence: [] }],
}

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

// stored: 저장된 분석(GET), analyzed: 분석 실행(POST) 응답 — 가짜 데이터를 렌더하지 않는지 구분해 확인한다.
const mockApi = (stored: unknown, analyzed: unknown, calls: { url: string; init?: RequestInit }[]) => {
  vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
    const u = String(url)
    calls.push({ url: u, init })
    if (u.includes('/students/7/weakness-dna/analyze')) return json(analyzed, 201)
    if (u.includes('/students/7/weakness-dna')) return json(stored)
    if (u.includes('/students')) return json([{ id: 7, name: '최승원' }])
    if (u.includes('/csat/dna/taxonomy')) return json({ traps: [] })
    if (u.includes('/csat/exams')) return json([])
    if (u.includes('/csat/wrong-html')) return json([])
    return json([])
  }))
}

beforeEach(() => { window.HTMLElement.prototype.scrollIntoView = vi.fn() })
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('④ 학생 약점 DNA 화면', () => {
  it('저장된 분석을 상단 요약·근거 문항·평가원 DNA와 함께 보여준다', async () => {
    // given: 최승원 분석이 저장되어 있을 때
    const calls: { url: string; init?: RequestInit }[] = []
    mockApi({ analysis, dna: [card] }, { analysis, dna: [card] }, calls)
    render(<CsatWikiV1 onNavigate={() => undefined} />)
    // when: ④ 메뉴를 연다
    fireEvent.click(screen.getByRole('button', { name: '④ 학생 약점 DNA' }))
    // then: 상단 요약에 학생·오답 수·반복 약점 수가 표시된다
    expect((await screen.findAllByText('최승원')).length).toBeGreaterThan(0)
    expect(screen.getByText('176문항')).toBeInTheDocument()
    expect(screen.getByText('10개')).toBeInTheDocument()
    expect(screen.getAllByText('1건').length).toBeGreaterThan(0)
    // then: 약점 카드에 무엇이 문제인지·근거 문항(학생답 원문 미표기/정답)이 나온다
    expect((await screen.findAllByText(card.title)).length).toBeGreaterThan(0)
    expect(screen.getByText(card.problem)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '모의A · 36번' })).toHaveAttribute('href', expect.stringContaining('/csat/wrong-html/1/raw'))
    expect(screen.getByText('학생답 원문 미표기')).toBeInTheDocument()
    expect(screen.getByText('정답 3')).toBeInTheDocument()
    // then: 평가원 출제 DNA 태그·CLAIM과 풀이 행동이 연결된다
    expect(screen.getByText(/DNA · COHESION ×1/)).toBeInTheDocument()
    expect(screen.getByText('제시문 연결: 재진술 어휘 짝짓기')).toBeInTheDocument()
    expect(screen.getByText('36·37번 제시문 배열은 재진술 어휘 짝짓기 요구')).toBeInTheDocument()
    steps.forEach(step => expect(screen.getAllByText(step).length).toBeGreaterThan(0))
    // then: 재분석 버튼과 근거 원문 전체 보기 링크가 있다
    expect(screen.getByRole('button', { name: '재분석' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '근거 원문 전체 보기' })).toHaveAttribute('href', expect.stringContaining('/csat/wrong-html/1/raw'))
    // then: 처방·관찰 패널이 이어진다
    expect(screen.getByText('최승원의 수능영어 풀이 처방')).toBeInTheDocument()
    expect(screen.getByText(/필자 주장 — 주장과 근거 구분/)).toBeInTheDocument()
  })

  it('분석 전에는 가짜 데이터 없이 실행 버튼만 노출한다', async () => {
    // given: 분석이 아직 실행되지 않았을 때
    const calls: { url: string; init?: RequestInit }[] = []
    mockApi({ analysis: null, dna: [] }, { analysis, dna: [card] }, calls)
    render(<CsatWikiV1 onNavigate={() => undefined} />)
    // when: ④ 메뉴를 연다
    fireEvent.click(screen.getByRole('button', { name: '④ 학생 약점 DNA' }))
    // then: 빈 상태와 분석 실행 버튼이 보이고 아무 데이터도 지어내지 않는다
    expect(await screen.findByText('아직 분석하지 않았습니다.')).toBeInTheDocument()
    expect(screen.queryByText(card.title)).not.toBeInTheDocument()
    const runButton = screen.getByRole('button', { name: '약점 DNA 분석 실행' })
    // when: 분석을 실행하면
    fireEvent.click(runButton)
    // then: POST로 분석이 호출되고 결과가 그대로 표시된다
    await waitFor(() => expect(calls.some(call => call.url.includes('/weakness-dna/analyze') && call.init?.method === 'POST')).toBe(true))
    expect((await screen.findAllByText(card.title)).length).toBeGreaterThan(0)
  })

  it('근거 원문이 없는 학생에게는 분석을 강제하지 않는다', async () => {
    // given: 등록된 오답 원문이 없는 상태에서
    const calls: { url: string; init?: RequestInit }[] = []
    mockApi({ analysis: null, dna: [] }, { analysis: null, dna: [] }, calls)
    render(<CsatWikiV1 onNavigate={() => undefined} />)
    fireEvent.click(screen.getByRole('button', { name: '④ 학생 약점 DNA' }))
    // when/then: 학생 선택이 되기 전에는 분석 버튼이 비활성이다
    await screen.findByText('아직 분석하지 않았습니다.')
    await waitFor(() => expect(calls.some(call => call.url.includes('/students'))).toBe(true))
    expect(screen.getByRole('button', { name: '약점 DNA 분석 실행' })).toBeEnabled()
  })
})
