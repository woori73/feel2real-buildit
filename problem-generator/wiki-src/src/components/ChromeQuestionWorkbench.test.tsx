import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import ChromeQuestionWorkbench from './ChromeQuestionWorkbench'

const memoryStorage = () => { const v = new Map<string, string>(); return { getItem: (k: string) => v.get(k) ?? null, setItem: (k: string, x: string) => { v.set(k, String(x)) }, removeItem: (k: string) => { v.delete(k) }, clear: () => v.clear(), key: (i: number) => [...v.keys()][i] ?? null, get length() { return v.size } } }

const canon = (over: Record<string, unknown> = {}) => ({
  schema_version: 'wiki-question-v1', kind: 'mc', question_type: '어법',
  instruction: { text: '어법상 틀린 것은?', html: '<p>어법상 틀린 것은?</p>' },
  passage: { text: 'He run fast.', html: '<p>He run fast.</p>', status: 'complete' },
  choices: [{ number: 1, text: 'run', html: 'run' }, { number: 2, text: 'fast', html: 'fast' }],
  conditions: [], answer: { choice_numbers: [1], accepted_answers: [], sample_answer: '' },
  explanation: { text: '3인칭 단수', html: '3인칭 단수' }, scoring: { score: 3, multi: false, answer_format: '', grading_rule: '' },
  source: {}, ...over,
})
const teacherRow = { id: 55, twin_id: null, school: '신구중', grade: '3학년', exam: '중간고사', mode: 'new', q_kind: 'mc', question_type: '어법', stem: '강사 문제 발문', source_text: 'teacher passage', options: ['a', 'b'], answer: [1], multi: 0, score: 2, conditions: [], answer_format: '', accepted_answers: [], sample_answer: '', grading_rule: '', exam_title: '', exam_group_id: '', favorite: 0, created_at: 'now', canonical: canon({ instruction: { text: '강사 문제 발문', html: '<p>강사 문제 발문</p>' } }) }
const textbookRow = { id: 91, twin_id: null, school: '', grade: '3학년', exam: '', mode: 'textbook', source_kind: 'textbook', textbook_material_id: 7, page: 32, question_number: '12', publisher: '동아(윤정미)', lesson: '5과', q_kind: 'mc', question_type: '어법', stem: '어법상 틀린 것은?', source_text: 'He run fast.', options: ['run', 'fast'], answer: [1], multi: 0, score: 3, conditions: [], answer_format: '', accepted_answers: [], sample_answer: '', grading_rule: '3인칭 단수', exam_title: '', exam_group_id: '', favorite: 0, created_at: 'now', extraction_status: 'ok', canonical: canon() }

beforeEach(() => {
  vi.stubGlobal('localStorage', memoryStorage()); vi.stubGlobal('sessionStorage', memoryStorage())
  sessionStorage.setItem('buildit_chrome_context', JSON.stringify({ school: '신구중', grade: '3학년', year: 2026, semester: '2학기', exam: '중간고사' }))
  window.HTMLElement.prototype.scrollIntoView = vi.fn()
})
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('ChromeQuestionWorkbench — 교과서 DB Source 통합', () => {
  const stubFetch = (posts: string[]) => vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
    const u = String(url)
    if (u.includes('/generated-tests') && init?.method === 'POST') { posts.push(String(init.body)); return new Response('{"saved":true,"test_id":1}', { status: 200, headers: { 'Content-Type': 'application/json' } }) }
    if (u.includes('/workspaces/final/context')) return new Response(JSON.stringify({ exam_context: { school: '신구중', grade: '3학년', year: 2026, semester: '2학기', exam: '중간고사' }, digital_twin: { id: 9 }, class_insights: [], school_prints: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    if (u.includes('/textbook-questions')) return new Response(JSON.stringify([textbookRow]), { status: 200, headers: { 'Content-Type': 'application/json' } })
    if (u.includes('/questions')) return new Response(JSON.stringify([teacherRow]), { status: 200, headers: { 'Content-Type': 'application/json' } })
    return new Response('[]', { status: 200 })
  }))

  it('Source 선택으로 강사 문제 DB와 교과서 DB를 한 Workbench에서 전환하고 출처를 표시한다', async () => {
    stubFetch([])
    render(<ChromeQuestionWorkbench onNavigate={vi.fn()} />)
    for (const label of ['강사 문제 DB', '교과서 DB', '전체']) expect(await screen.findByRole('button', { name: label })).toBeInTheDocument()
    expect(await screen.findByText(/강사 문제 DB · 신구중3학년 · QID 55/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '교과서 DB' }))
    expect(await screen.findByText(/교과서 DB · 동아\(윤정미\) · 5과 · p.32 · 12번 · QID 91/)).toBeInTheDocument()
  })

  it('교과서 문항 변형본을 저장하면 원본을 덮지 않고 textbook provenance가 붙은 파생 row로 간다', async () => {
    const posts: string[] = []
    stubFetch(posts)
    render(<ChromeQuestionWorkbench onNavigate={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: '교과서 DB' }))
    await screen.findByText(/교과서 DB · 동아/)

    const paste = screen.getByRole('textbox', { name: /변형 결과 입력/ }) || screen.getByPlaceholderText(/"type"/)
    fireEvent.change(paste, { target: { value: JSON.stringify({ ...canon({ instruction: { text: '신구중3 스타일로 변형된 발문', html: '<p>신구중3 스타일로 변형된 발문</p>' } }) }) } })
    fireEvent.click(screen.getByRole('button', { name: 'Preview' }))
    await screen.findByRole('button', { name: '변형본 보기' })
    fireEvent.click(screen.getByRole('button', { name: '변형본 저장' }))

    await waitFor(() => expect(posts.length).toBe(1))
    const body = JSON.parse(posts[0])
    // generated_tests.source_page = 실제 PDF 페이지 ("32"), textbook-question id를 섞지 않는다
    expect(body.source_page).toBe('32')
    expect(body.source_material).toContain('교과서')
    expect(body.source_material).not.toContain('textbook-question')
    expect(body.publisher).toBe('동아(윤정미)')
    // twin_pattern provenance = PDF 페이지 / 교과서 Question ID / material id / 문항번호를 각각 독립 보존
    const prov = JSON.parse(body.twin_pattern)
    expect(prov.source_kind).toBe('textbook')
    expect(prov.source_question_id).toBe(91)   // textbook_questions.id
    expect(prov.textbook_material_id).toBe(7)  // 원본 PDF
    expect(prov.source_page).toBe(32)          // PDF 페이지 (number)
    expect(prov.source_question_number).toBe('12')
  })
})
