import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
// @ts-ignore - @types/node 미설치. vitest는 node 런타임이라 실제 동작한다.
import { readFileSync } from 'node:fs'
// @ts-ignore - @types/node 미설치. vitest는 node 런타임이라 실제 동작한다.
import { join } from 'node:path'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import WeeklyExamHub from './WeeklyExamHub'

declare const __dirname: string

const baseProps = {
  pastExamCount: 1, schoolPrintCount: 1, wrongCount: 0, aiWorkLogCount: 0,
  hasDigitalTwin: false, onOpenContext: () => undefined, onOpenWeeklyWorkspace: () => undefined,
  onOpenWrongReview: () => undefined, onOpenWrongMock: () => undefined,
}

const draftItem = (id: number) => ({
  id, school: 'X', grade: 'Y', year: 2026, semester: '2학기', exam: '중간고사',
  round: 5, set_type: '40', question_count: 40, status: 'DRAFT', version: 1,
  created_by: 't', created_at: '2026-09-01T00:00:00', context_hash: 'h',
})

const finalItem = (id: number) => ({ ...draftItem(id), status: 'FINAL' })

const memoryStorage = () => { const v = new Map<string, string>(); return { getItem: (k: string) => v.get(k) ?? null, setItem: (k: string, x: string) => { v.set(k, String(x)) }, removeItem: (k: string) => { v.delete(k) }, clear: () => v.clear(), key: (i: number) => [...v.keys()][i] ?? null, get length() { return v.size } } }

type Call = { url: string; init?: RequestInit }

const mockApi = (items: unknown[], calls: Call[], putImpl?: (body: unknown) => { status: number; payload: unknown; delayMs?: number }) => {
  vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
    const u = String(url)
    calls.push({ url: u, init })
    if (u.includes('weekly-exams?')) return new Response(JSON.stringify(items), { status: 200, headers: { 'Content-Type': 'application/json' } })
    if (u.includes('workspace/me')) return new Response(JSON.stringify({ user_id: 't' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    if (/weekly-exams\/\d+\/artifacts$/.test(u) && init?.method === 'PUT') {
      const body = JSON.parse(String(init.body ?? '{}'))
      if (putImpl) {
        const out = putImpl(body)
        if (out.delayMs) await new Promise(resolve => setTimeout(resolve, out.delayMs))
        return new Response(JSON.stringify(out.payload), { status: out.status, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response(JSON.stringify({ id: 1 }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    }
    if (/weekly-exams\/\d+$/.test(u) && init?.method === 'DELETE') return new Response(JSON.stringify({ id: 1 }), { status: 200 })
    return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
  }))
}

beforeEach(() => {
  vi.stubGlobal('localStorage', memoryStorage()); vi.stubGlobal('sessionStorage', memoryStorage())
  window.HTMLElement.prototype.scrollIntoView = vi.fn()
})
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

const contexts = [
  { school: '신구중', grade: '2학년', year: 2026, semester: '2학기', exam: '중간고사' },
  { school: '압구정중', grade: '2학년', year: 2026, semester: '2학기', exam: '중간고사' },
  { school: '신사중', grade: '3학년', year: 2026, semester: '2학기', exam: '중간고사' },
]

describe.each(contexts)('WeeklyExamHub 공통 관리 버튼 ($school $grade)', (context) => {
  it('DRAFT 세트에 FINAL 확정·자료 교체·삭제를 모두 노출한다', async () => {
    const calls: { url: string; init?: RequestInit }[] = []
    mockApi([draftItem(11)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    expect(screen.getByRole('button', { name: 'FINAL 확정' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '자료 교체' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument()
  })

  it('FINAL 세트에는 교체·삭제를 노출하지 않는다', async () => {
    const calls: { url: string; init?: RequestInit }[] = []
    mockApi([finalItem(12)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    expect(screen.queryByRole('button', { name: '자료 교체' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'DRAFT로 다시 열기' })).toBeInTheDocument()
  })
})

describe('WeeklyExamHub 세트 정보 수정', () => {
  const context = contexts[0]
  const adminApi = (items: unknown[], calls: { url: string; init?: RequestInit }[]) => {
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      const u = String(url)
      calls.push({ url: u, init })
      if (u.includes('weekly-exams?')) return new Response(JSON.stringify(items), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (u.includes('workspace/me')) return new Response(JSON.stringify({ user_id: 't', workspace_admin: true }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (/weekly-exams\/\d+\/metadata$/.test(u) && init?.method === 'PATCH') return new Response(JSON.stringify({ id: 21, set_type: '70', question_count: 70 }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
    }))
  }

  it('관리자는 세트 정보 수정 패널에서 round·set_type을 PATCH한다', async () => {
    const calls: { url: string; init?: RequestInit }[] = []
    adminApi([draftItem(21)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    fireEvent.click(screen.getByRole('button', { name: '세트 정보 수정' }))
    const panel = screen.getByLabelText('세트 정보 수정')
    expect(panel).toBeInTheDocument()
    const roundInput = within(panel).getByLabelText('회차') as HTMLInputElement
    expect(roundInput.value).toBe('5')
    fireEvent.change(roundInput, { target: { value: '6' } })
    fireEvent.change(within(panel).getByLabelText('세트 유형'), { target: { value: '70' } })
    fireEvent.click(within(panel).getByRole('button', { name: '세트 정보 저장' }))
    await waitFor(() => expect(calls.some(c => /weekly-exams\/21\/metadata$/.test(c.url) && c.init?.method === 'PATCH')).toBe(true))
    const patch = calls.find(c => /weekly-exams\/21\/metadata$/.test(c.url))!
    expect(JSON.parse(String(patch.init!.body))).toEqual({ round: 6, setType: '70' })
    await screen.findByText(/세트 정보를 수정했습니다/)
  })

  it('관리자가 아니면 세트 정보 수정 버튼이 없다', async () => {
    const calls: { url: string; init?: RequestInit }[] = []
    mockApi([draftItem(22)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    expect(screen.queryByRole('button', { name: '세트 정보 수정' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('세트 정보 수정')).not.toBeInTheDocument()
  })
})

describe('WeeklyExamHub 단일 교체 버튼', () => {
  const context = contexts[0]

  const openReplace = async () => {
    fireEvent.click(screen.getByRole('button', { name: '자료 교체' }))
    return waitFor(() => {
      expect(screen.getByRole('button', { name: '선택한 자료 교체' })).toBeInTheDocument()
    })
  }

  const selectFile = async (label: RegExp, file: File) => {
    const input = screen.getByLabelText(label) as HTMLInputElement
    fireEvent.change(input, { target: { files: [file] } })
  }

  const submitButton = () => screen.getByRole('button', { name: '선택한 자료 교체' }) as HTMLButtonElement

  const lastPutBody = (calls: Call[], id: number) => {
    const put = calls.filter(c => new RegExp(`weekly-exams\\/${id}\\/artifacts$`).test(c.url) && c.init?.method === 'PUT').pop()!
    return JSON.parse(String(put.init!.body))
  }

  it('아무 파일도 선택하지 않으면 실행 버튼 비활성 + 안내 표시', async () => {
    const calls: Call[] = []
    mockApi([draftItem(21)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    await openReplace()
    expect(submitButton()).toBeDisabled()
    expect(screen.getByText('파일을 하나 이상 선택하세요.')).toBeInTheDocument()
    expect(screen.getByText('교체할 파일만 선택하세요. 선택하지 않은 기존 자료는 그대로 유지됩니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '닫기' })).toBeVisible()
  })

  it('문제지만 선택하면 실행 버튼 활성 + 문제지 필드만 전송', async () => {
    const calls: Call[] = []
    mockApi([draftItem(22)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    await openReplace()
    await selectFile(/문제지 교체/, new File(['<h1>new</h1>'], 'paper.html', { type: 'text/html' }))
    const button = await waitFor(() => { const b = submitButton(); expect(b).toBeEnabled(); return b })
    expect(screen.getByText(/선택됨: paper\.html/)).toBeInTheDocument()
    fireEvent.click(button)
    await waitFor(() => expect(calls.some(c => /weekly-exams\/22\/artifacts$/.test(c.url) && c.init?.method === 'PUT')).toBe(true))
    const body = lastPutBody(calls, 22)
    expect(body.studentPaper).toContain('<h1>new</h1>')
    expect(body).not.toHaveProperty('answerExplanation')
    expect(body).not.toHaveProperty('productionVerification')
    await screen.findByText(/문제지를 교체했습니다/)
  })

  it('정답·해설만 선택해도 활성 + 해당 필드만 전송', async () => {
    const calls: Call[] = []
    mockApi([draftItem(23)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    await openReplace()
    await selectFile(/정답/, new File(['<h1>expl</h1>'], 'expl.html', { type: 'text/html' }))
    await waitFor(() => expect(submitButton()).toBeEnabled())
    fireEvent.click(submitButton())
    await waitFor(() => expect(calls.some(c => /weekly-exams\/23\/artifacts$/.test(c.url))).toBe(true))
    const body = lastPutBody(calls, 23)
    expect(body.answerExplanation).toContain('<h1>expl</h1>')
    expect(body).not.toHaveProperty('studentPaper')
    expect(body).not.toHaveProperty('productionVerification')
    const status = await screen.findByRole('status')
    expect(status.textContent).toContain('교체했습니다')
    expect(status.textContent).toContain('정답')
  })

  it('출제근거만 선택해도 활성 + 해당 필드만 전송', async () => {
    const calls: Call[] = []
    mockApi([draftItem(24)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    await openReplace()
    await selectFile(/출제근거/, new File(['# md v2'], 'proof.md', { type: 'text/markdown' }))
    await waitFor(() => expect(submitButton()).toBeEnabled())
    fireEvent.click(submitButton())
    await waitFor(() => expect(calls.some(c => /weekly-exams\/24\/artifacts$/.test(c.url))).toBe(true))
    const body = lastPutBody(calls, 24)
    expect(body.productionVerification).toContain('# md v2')
    expect(body).not.toHaveProperty('studentPaper')
    expect(body).not.toHaveProperty('answerExplanation')
    await screen.findByText(/출제근거를 교체했습니다/)
  })

  it('문제지+정답·해설 조합은 두 필드만 전송', async () => {
    const calls: Call[] = []
    mockApi([draftItem(25)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    await openReplace()
    await selectFile(/문제지 교체/, new File(['<h1>p</h1>'], 'p.html', { type: 'text/html' }))
    await selectFile(/정답/, new File(['<h1>e</h1>'], 'e.html', { type: 'text/html' }))
    await waitFor(() => expect(submitButton()).toBeEnabled())
    fireEvent.click(submitButton())
    await waitFor(() => expect(calls.some(c => /weekly-exams\/25\/artifacts$/.test(c.url))).toBe(true))
    const body = lastPutBody(calls, 25)
    expect(body.studentPaper).toContain('<h1>p</h1>')
    expect(body.answerExplanation).toContain('<h1>e</h1>')
    expect(body).not.toHaveProperty('productionVerification')
    const status = await screen.findByRole('status')
    expect(status.textContent).toContain('문제지')
    expect(status.textContent).toContain('교체했습니다')
  })

  it('세 파일 모두 선택하면 세 필드 전송', async () => {
    const calls: Call[] = []
    mockApi([draftItem(26)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    await openReplace()
    await selectFile(/문제지 교체/, new File(['<h1>p</h1>'], 'p.html', { type: 'text/html' }))
    await selectFile(/정답/, new File(['<h1>e</h1>'], 'e.html', { type: 'text/html' }))
    await selectFile(/출제근거/, new File(['# m'], 'm.md', { type: 'text/markdown' }))
    await waitFor(() => expect(submitButton()).toBeEnabled())
    fireEvent.click(submitButton())
    await waitFor(() => expect(calls.some(c => /weekly-exams\/26\/artifacts$/.test(c.url))).toBe(true))
    const body = lastPutBody(calls, 26)
    expect(body.studentPaper).toContain('<h1>p</h1>')
    expect(body.answerExplanation).toContain('<h1>e</h1>')
    expect(body.productionVerification).toContain('# m')
  })

  it('요청 중 중복 클릭 방지 + 교체 중… 표시', async () => {
    const calls: Call[] = []
    mockApi([draftItem(27)], calls, () => ({ status: 200, payload: { id: 27 }, delayMs: 80 }))
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    await openReplace()
    await selectFile(/문제지 교체/, new File(['<h1>p</h1>'], 'p.html', { type: 'text/html' }))
    const button = await waitFor(() => { const b = submitButton(); expect(b).toBeEnabled(); return b })
    fireEvent.click(button)
    fireEvent.click(button)
    await waitFor(() => expect(screen.getByRole('button', { name: '교체 중…' })).toBeDisabled())
    await waitFor(() => expect(calls.filter(c => /weekly-exams\/27\/artifacts$/.test(c.url) && c.init?.method === 'PUT')).toHaveLength(1))
  })

  it('실패 시 서버 오류 내용을 표시하고 패널을 유지한다', async () => {
    const calls: Call[] = []
    mockApi([draftItem(28)], calls, () => ({ status: 400, payload: { detail: '교체 본문이 비어 있습니다.' } }))
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    await openReplace()
    await selectFile(/문제지 교체/, new File(['<h1>p</h1>'], 'p.html', { type: 'text/html' }))
    await waitFor(() => expect(submitButton()).toBeEnabled())
    fireEvent.click(submitButton())
    await screen.findByText(/교체에 실패했습니다: 교체 본문이 비어 있습니다/)
    expect(screen.getByRole('button', { name: /선택한 자료 교체|교체 중/ })).toBeInTheDocument()
  })

  it('활성 교체 버튼은 흰 글자+흰 배경이 아니다(공통 variant 명시)', () => {
    const css: string = readFileSync(join(__dirname, '..', 'styles', 'generator.css'), 'utf-8')
    const rule = css.match(/\.artifact-actions button\.primary-action[^{]*\{[^}]*\}/s)?.[0] ?? ''
    expect(rule).toContain('#17211b')
    expect(rule).toContain('#fff')
    const disabled = css.match(/\.artifact-actions button\.primary-action:disabled[^{]*\{[^}]*\}/s)?.[0] ?? ''
    expect(disabled).toContain('#d9dfdb')
    expect(disabled).toContain('#536159')
    const danger = css.match(/button\.danger-button[^{]*\{[^}]*\}/s)?.[0] ?? ''
    expect(danger).toMatch(/#b91c1c|#fef2f2/)
  })

  it('모바일 375px에서 교체 UI가 카드 밖으로 넘치지 않는다(CSS 명시)', () => {
    const css: string = readFileSync(join(__dirname, '..', 'styles', 'generator.css'), 'utf-8')
    expect(css).toContain('@media(max-width:375px)')
    expect(css).toContain('.replace-panel')
    expect(css).toContain('.replace-file-name')
  })

  it('삭제는 확인 절차를 거쳐 한 번만 DELETE한다', async () => {
    const calls: Call[] = []
    mockApi([draftItem(31)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    fireEvent.click(screen.getByRole('button', { name: '삭제' }))
    await screen.findByText('정말 삭제할까요?')
    fireEvent.click(screen.getByRole('button', { name: '삭제 확인' }))
    await waitFor(() => expect(calls.filter(c => /weekly-exams\/31$/.test(c.url) && c.init?.method === 'DELETE')).toHaveLength(1))
  })
})

describe('WeeklyExamHub 인라인 편집 진입', () => {
  const context = contexts[0]

  it('문제지/정답해설 편집 버튼으로 WeeklyExamEditor 오버레이를 연다', async () => {
    // given: DRAFT 세트가 있는 허브
    const calls: { url: string; init?: RequestInit }[] = []
    mockApi([draftItem(41)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    // when: 문제지 편집 클릭
    fireEvent.click(screen.getByRole('button', { name: '문제지 편집' }))
    // then: 오버레이가 뜬다
    const dialog = await screen.findByRole('dialog', { name: /주간 실전 모의고사 문제지 편집/ })
    expect(dialog).toBeInTheDocument()
    // when: 취소로 닫기
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    // when: 정답해설 편집 클릭
    fireEvent.click(screen.getByRole('button', { name: '정답해설 편집' }))
    expect(await screen.findByRole('dialog', { name: /주간 실전 모의고사 정답해설 편집/ })).toBeInTheDocument()
  })
})
