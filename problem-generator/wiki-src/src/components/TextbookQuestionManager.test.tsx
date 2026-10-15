import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import TextbookQuestionManager from './TextbookQuestionManager'

const material = { id: 7, title: '5과 문제자료', publisher: '동아(윤정미)', lesson: '5과', grade: '중2 (2022 개정)', original_name: 'y.pdf', page_total: 55 }

const memoryStorage = () => { const v = new Map<string, string>(); return { getItem: (k: string) => v.get(k) ?? null, setItem: (k: string, x: string) => { v.set(k, String(x)) }, removeItem: (k: string) => { v.delete(k) }, clear: () => v.clear(), key: (i: number) => [...v.keys()][i] ?? null, get length() { return v.size } } }

beforeEach(() => {
  vi.stubGlobal('localStorage', memoryStorage()); vi.stubGlobal('sessionStorage', memoryStorage())
  window.HTMLElement.prototype.scrollIntoView = vi.fn()
})
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('TextbookQuestionManager', () => {
  it('한 PDF를 여러 문항으로 분리해 Preview한 뒤 replace 저장하고 전체/성공/검수/실패를 보고한다', async () => {
    const content = `<p>12. 다음 밑줄 친 부분 중 어법상 틀린 것은?</p><p>He run fast every morning.</p><p>① run</p><p>② fast</p><p>③ every</p><p>정답: 1</p>
      <p>13. 빈칸에 알맞은 말을 쓰시오.</p><p>She ______ to school.</p><p>정답: goes</p>`
    const postBodies: string[] = []
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      const u = String(url)
      if (u.includes('/textbook-materials/7/questions') && init?.method === 'POST') {
        postBodies.push(String(init.body))
        return new Response(JSON.stringify({ total: 2, ok: 1, review: 1, failed: 0, questions: [] }), { status: 201, headers: { 'Content-Type': 'application/json' } })
      }
      if (u.includes('/textbook-materials/7/questions')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (u.includes('/textbook-materials/7/content')) return new Response(content, { status: 200, headers: { 'Content-Type': 'text/html' } })
      return new Response('[]', { status: 200 })
    }))
    render(<TextbookQuestionManager material={material} onClose={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: '추출 내용에서 문항 분리' }))
    await screen.findByText(/분리된 문항 2개/)
    expect(screen.getByText(/2개 문항으로 분리했습니다/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /교과서 문항 DB에 저장/ }))
    await waitFor(() => expect(postBodies.length).toBe(1))
    const body = JSON.parse(postBodies[0])
    expect(body.replace).toBe(true)
    expect(body.items).toHaveLength(2)
    expect(body.items[0].question.schema_version).toBe('wiki-question-v1')
    expect(await screen.findByText(/저장 완료 — 전체 2 · 정상 1 · 검수 1 · 실패 0/)).toBeInTheDocument()
  })

  it('저장된 교과서 문항을 목록에서 확인하고 확인 후 삭제한다', async () => {
    const saved = [{
      id: 91, textbook_material_id: 7, source_kind: 'textbook', page: 32, question_number: '12', q_kind: 'mc',
      question_type: '어법', stem: '어법상 틀린 것은?', source_text: 'He run.', options: ['run', 'runs'], answer: [1],
      multi: 0, score: 3, conditions: [], answer_format: '', accepted_answers: [], sample_answer: '', grading_rule: '3인칭 단수',
      exam_title: '', exam_group_id: '', favorite: 0, created_at: 'now', extraction_status: 'review', publisher: '동아(윤정미)', lesson: '5과',
      canonical: { schema_version: 'wiki-question-v1', kind: 'mc', question_type: '어법', instruction: { text: '어법상 틀린 것은?', html: '<p>어법상 틀린 것은?</p>' }, passage: { text: 'He run.', html: '<p>He run.</p>', status: 'complete' }, choices: [{ number: 1, text: 'run', html: 'run' }, { number: 2, text: 'runs', html: 'runs' }], conditions: [], answer: { choice_numbers: [1], accepted_answers: [], sample_answer: '' }, explanation: { text: '3인칭 단수', html: '3인칭 단수' }, scoring: { score: 3, multi: false, answer_format: '', grading_rule: '' }, source: {} },
    }]
    let deleted = false
    vi.stubGlobal('confirm', vi.fn(() => true))
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      const u = String(url)
      if (u.includes('/textbook-questions/91') && init?.method === 'DELETE') { deleted = true; return new Response(null, { status: 204 }) }
      if (u.includes('/textbook-materials/7/questions')) return new Response(JSON.stringify(deleted ? [] : saved), { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200 })
    }))
    render(<TextbookQuestionManager material={material} onClose={vi.fn()} />)
    expect(await screen.findByText(/저장된 교과서 문항 1개/)).toBeInTheDocument()
    expect(screen.getByText(/p.32 · 12번 · 어법/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '삭제' }))
    await waitFor(() => expect(deleted).toBe(true))
    expect(await screen.findByText('아직 분리된 문항이 없습니다.')).toBeInTheDocument()
  })
})
