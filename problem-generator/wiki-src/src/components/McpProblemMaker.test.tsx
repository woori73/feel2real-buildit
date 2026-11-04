import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import McpProblemMaker from './McpProblemMaker'

const tree = [{ school: '신사중', grades: [{ grade: '3학년', exams: [] }] }]
const contextBody = { past_exams: [{ id: 1 }, { id: 2 }], school_prints: [], digital_twin: { id: 9 }, class_insights: [], student_errors: [], ai_work_logs: [], generated_questions: [], final_deliverables: [] }

const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url.includes('/schools')) return json(tree)
    if (url.includes('/workspaces/final/context')) return json(contextBody)
    return new Response('{}', { status: 404 })
  }))
})
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('MCP 문제 만들기', () => {
  it('given 학교 목록 when 화면을 열면 then 자료 현황과 빠진 핵심 자료를 보여 준다', async () => {
    // given / when
    render(<McpProblemMaker onNavigate={vi.fn()} />)
    // then
    const list = await screen.findByRole('list', { name: '자료 현황' })
    expect(within(list).getByText('기출시험지').nextSibling).toHaveTextContent('2건')
    expect(within(list).getByText('학교 프린트').nextSibling).toHaveTextContent('없음')
    expect(screen.getByText(/아직 없는 자료: 학교 프린트\./)).toBeInTheDocument()
  })

  it('given 시험 선택 when 문항 수를 바꾸면 then 지시문에 시험 인자와 batch가 반영된다', async () => {
    // given
    render(<McpProblemMaker onNavigate={vi.fn()} />)
    await screen.findByRole('list', { name: '자료 현황' })
    // when
    fireEvent.click(screen.getByRole('radio', { name: '70문항' }))
    // then
    const prompt = screen.getByLabelText('MCP 작업 지시문')
    expect(prompt).toHaveTextContent('school="신사중", grade="3학년"')
    expect(prompt).toHaveTextContent('Q61~70')
  })

  it('given 지시문 when 복사 버튼을 누르면 then 클립보드에 같은 지시문을 넣는다', async () => {
    // given
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })
    render(<McpProblemMaker onNavigate={vi.fn()} />)
    await screen.findByRole('list', { name: '자료 현황' })
    // when
    fireEvent.click(screen.getByRole('button', { name: '지시문 복사' }))
    // then
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(screen.getByLabelText('MCP 작업 지시문').textContent))
    expect(await screen.findByRole('button', { name: '복사했습니다' })).toBeInTheDocument()
  })

  it('given 결과 보기 when 강사 문제 DB를 누르면 then 해당 화면으로 이동한다', async () => {
    // given
    const onNavigate = vi.fn()
    render(<McpProblemMaker onNavigate={onNavigate} />)
    await screen.findByRole('list', { name: '자료 현황' })
    // when
    fireEvent.click(screen.getByRole('button', { name: '강사 문제 DB' }))
    // then
    expect(onNavigate).toHaveBeenCalledWith('database')
  })
})
