import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import WeeklyExamHub from './WeeklyExamHub'

const baseProps = {
  pastExamCount: 1, schoolPrintCount: 1, wrongCount: 0, aiWorkLogCount: 0,
  hasDigitalTwin: false, onOpenContext: () => undefined, onOpenWeeklyWorkspace: () => undefined,
  onOpenWrongReview: () => undefined,
}

const draftItem = (id: number) => ({
  id, school: 'X', grade: 'Y', year: 2026, semester: '2학기', exam: '중간고사',
  round: 5, set_type: '40', question_count: 40, status: 'DRAFT', version: 1,
  created_by: 't', created_at: '2026-09-01T00:00:00', context_hash: 'h',
})

const finalItem = (id: number) => ({ ...draftItem(id), status: 'FINAL' })

const memoryStorage = () => { const v = new Map<string, string>(); return { getItem: (k: string) => v.get(k) ?? null, setItem: (k: string, x: string) => { v.set(k, String(x)) }, removeItem: (k: string) => { v.delete(k) }, clear: () => v.clear(), key: (i: number) => [...v.keys()][i] ?? null, get length() { return v.size } } }

const mockApi = (items: unknown[], calls: { url: string; init?: RequestInit }[]) => {
  vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
    const u = String(url)
    calls.push({ url: u, init })
    if (u.includes('weekly-exams?')) return new Response(JSON.stringify(items), { status: 200, headers: { 'Content-Type': 'application/json' } })
    if (u.includes('workspace/me')) return new Response(JSON.stringify({ user_id: 't' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    if (/weekly-exams\/\d+\/artifacts$/.test(u) && init?.method === 'PUT') return new Response(JSON.stringify({ id: 1 }), { status: 200, headers: { 'Content-Type': 'application/json' } })
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

describe('WeeklyExamHub 파일별 교체', () => {
  const context = contexts[0]
  it('문제지 교체는 studentPaper만 PUT한다', async () => {
    const calls: { url: string; init?: RequestInit }[] = []
    mockApi([draftItem(21)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    fireEvent.click(screen.getByRole('button', { name: '자료 교체' }))
    const file = new File(['<h1>new</h1>'], 'paper.html', { type: 'text/html' })
    const input = screen.getByLabelText(/문제지 교체/) as HTMLInputElement
    fireEvent.change(input, { target: { files: [file] } })
    const saveButton = await waitFor(() => {
      const button = screen.getByRole('button', { name: '문제지 교체 저장' })
      expect(button).toBeEnabled()
      return button
    })
    fireEvent.click(saveButton)
    await waitFor(() => expect(calls.some(c => /weekly-exams\/21\/artifacts$/.test(c.url) && c.init?.method === 'PUT')).toBe(true))
    const put = calls.find(c => /weekly-exams\/21\/artifacts$/.test(c.url))!
    const body = JSON.parse(String(put.init!.body))
    expect(body.studentPaper).toContain('<h1>new</h1>')
    expect(body).not.toHaveProperty('answerExplanation')
    expect(body).not.toHaveProperty('productionVerification')
    await screen.findByText(/선택한 자료를 교체했습니다/)
  })

  it('삭제는 확인 절차를 거쳐 한 번만 DELETE한다', async () => {
    const calls: { url: string; init?: RequestInit }[] = []
    mockApi([draftItem(31)], calls)
    render(<WeeklyExamHub context={context} {...baseProps} />)
    await screen.findByText('5회 · 40')
    fireEvent.click(screen.getByRole('button', { name: '삭제' }))
    await screen.findByText('정말 삭제할까요?')
    fireEvent.click(screen.getByRole('button', { name: '삭제 확인' }))
    await waitFor(() => expect(calls.filter(c => /weekly-exams\/31$/.test(c.url) && c.init?.method === 'DELETE')).toHaveLength(1))
  })
})
