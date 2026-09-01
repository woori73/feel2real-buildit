import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerChromeQuestionTools } from './chromeQuestionWebMcp'

type Tool = { name: string; execute: (input: Record<string, unknown>) => Promise<unknown> }
const memoryStorage = () => { const v = new Map<string, string>(); return { getItem: (k: string) => v.get(k) ?? null, setItem: (k: string, x: string) => { v.set(k, String(x)) }, removeItem: (k: string) => { v.delete(k) }, clear: () => v.clear(), key: (i: number) => [...v.keys()][i] ?? null, get length() { return v.size } } }

const canonical = { schema_version: 'wiki-question-v1', kind: 'mc', question_type: '어법', instruction: { text: '변형된 발문입니다', html: '<p>변형된 발문입니다</p>' }, passage: { text: 'He runs fast.', html: '<p>He runs fast.</p>', status: 'complete' }, choices: [{ number: 1, text: 'run', html: 'run' }, { number: 2, text: 'runs', html: 'runs' }], conditions: [], answer: { choice_numbers: [1], accepted_answers: [], sample_answer: '' }, explanation: { text: '3인칭 단수', html: '3인칭 단수' }, scoring: { score: 3, multi: false, answer_format: '', grading_rule: '' }, source: {} }

describe('save_chrome_transformed_question — 교과서 provenance', () => {
  let tools: Tool[]
  const posts: string[] = []
  beforeEach(async () => {
    posts.length = 0
    vi.stubGlobal('localStorage', memoryStorage()); vi.stubGlobal('sessionStorage', memoryStorage())
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      if (String(url).includes('/generated-tests') && init?.method === 'POST') { posts.push(String(init.body)); return new Response('{"saved":true,"test_id":5}', { status: 200, headers: { 'Content-Type': 'application/json' } }) }
      return new Response('{}', { status: 200 })
    }))
    tools = []
    Object.defineProperty(document, 'modelContext', { configurable: true, value: { registerTool: vi.fn(async (t: Tool) => { tools.push(t) }) } })
    await registerChromeQuestionTools()
    // Workbench가 세션에 심어둔 것과 동일한 형태
    sessionStorage.setItem('buildit_chrome_workbench_context', JSON.stringify({
      exam_context: { school: '신구중', grade: '3학년', year: 2026, semester: '2학기', exam: '중간고사' },
      source_question_id: 91, source_question: canonical,
      source_question_meta: { source_kind: 'textbook', id: 91, textbook_material_id: 7, page: 32, question_number: '12', publisher: '동아(윤정미)', lesson: '5과', school: '', grade: '3학년', exam: '' },
    }))
    sessionStorage.setItem('buildit_chrome_preview_question', JSON.stringify({ source_question_id: 91, question: canonical }))
  })
  afterEach(() => { vi.unstubAllGlobals(); Reflect.deleteProperty(document, 'modelContext') })

  it('버튼 저장과 동일하게 source_page=PDF페이지, twin_pattern에 id/번호/material 독립 보존', async () => {
    const save = tools.find(t => t.name === 'save_chrome_transformed_question')!
    await save.execute({ source_question_id: 91, revision_note: 'r', digital_twin_id: 9, material_ids: [3], insight_ids: [] })
    expect(posts).toHaveLength(1)
    const body = JSON.parse(posts[0])
    expect(body.source_page).toBe('32')
    expect(body.source_page).not.toContain('textbook-question')
    expect(body.publisher).toBe('동아(윤정미)')
    const prov = JSON.parse(body.twin_pattern)
    expect(prov.source_kind).toBe('textbook')
    expect(prov.source_question_id).toBe(91)
    expect(prov.textbook_material_id).toBe(7)
    expect(prov.source_page).toBe(32)
    expect(prov.source_question_number).toBe('12')
  })
})
