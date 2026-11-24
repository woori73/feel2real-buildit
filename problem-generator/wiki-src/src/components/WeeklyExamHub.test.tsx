import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import WeeklyExamHub, { finalFileLabel, finalFileOpenPath, parseRoundInput, type FinalFile } from './WeeklyExamHub'

// 승인 단계 제거(2026-11-22) 이후의 허브 계약:
// - 등록한 회차는 상태(DRAFT/FINAL)·승인 버튼 없이 바로 보인다.
// - 이미 올려 둔 FINAL 완성본 파일(final_deliverables)도 같은 칸에서 연다.

const context = { school: '신구중', grade: '2학년', year: 2026, semester: '2학기', exam: '기말고사' }
const baseProps = {
  context, pastExamCount: 1, schoolPrintCount: 1, wrongCount: 0, aiWorkLogCount: 0,
  hasDigitalTwin: false, onOpenContext: () => undefined, onOpenWeeklyWorkspace: () => undefined,
  onOpenWrongReview: () => undefined,
}

const weeklyItem = (id: number, legacyStatus: string) => ({
  id, ...context, round: 5, set_type: 'STD', question_count: 40, status: 'FINAL', legacy_status: legacyStatus,
  version: 1, created_by: 't', created_at: '2026-11-01T00:00:00', context_hash: 'h',
  student_paper_name: '문제지.pdf', answer_explanation_name: '정답해설.pdf',
})

const pdfFinal = (id: number, round: number): FinalFile => ({
  id, title: `신구중2 예상문제 ${round}회 문제지`, original_name: `${round}회_문제지.pdf`,
  created_at: '2026-11-20T10:00:00', has_html: 0,
})

const mockApi = (items: unknown[]) => {
  vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL) => {
    const u = String(url)
    const body = u.includes('weekly-exams?') ? items : u.includes('workspace/me') ? { user_id: 't' } : {}
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }))
}

let opened: string[]

beforeEach(() => {
  opened = []
  vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null)
  vi.stubGlobal('open', vi.fn((url: string) => { opened.push(String(url)); return null }))
})
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks() })

describe('finalFileOpenPath', () => {
  it('given 원본만 있는 완성본(PDF) when 경로를 만들면 then /original을 연다', () => {
    // given
    const item = pdfFinal(31, 1)
    // when
    const path = finalFileOpenPath(item)
    // then
    expect(path).toBe('final-deliverables/31/original')
  })

  it('given HTML이 저장된 완성본 when 경로를 만들면 then /html을 연다', () => {
    // given
    const item: FinalFile = { ...pdfFinal(32, 2), has_html: 1 }
    // when
    const path = finalFileOpenPath(item)
    // then
    expect(path).toBe('final-deliverables/32/html')
  })

  it('given HTML도 원본도 없는 행 when 경로를 만들면 then null이다', () => {
    // given
    const item: FinalFile = { id: 33, title: '빈 자료', original_name: '', created_at: '', has_html: 0 }
    // when / then
    expect(finalFileOpenPath(item)).toBeNull()
  })
})

describe('finalFileLabel', () => {
  it('given 제목이 있으면 when 이름을 만들면 then 제목을 쓴다', () => {
    // given / when / then
    expect(finalFileLabel(pdfFinal(1, 7))).toBe('신구중2 예상문제 7회 문제지')
  })

  it('given 제목이 비어 있으면 when 이름을 만들면 then 파일 이름, 그것도 없으면 "완성본"이다', () => {
    // given
    const noTitle: FinalFile = { ...pdfFinal(2, 8), title: '  ' }
    const nothing: FinalFile = { id: 3, title: '', original_name: '', created_at: '' }
    // when / then
    expect(finalFileLabel(noTitle)).toBe('8회_문제지.pdf')
    expect(finalFileLabel(nothing)).toBe('완성본')
  })
})

describe('WeeklyExamHub 승인 없는 회차별 자료', () => {
  it('given 주간 회차는 없고 완성본 파일만 있을 때 when 화면을 열면 then 파일 목록과 원본 열기가 보인다', async () => {
    // given: 신구중2 기말처럼 final_deliverables에만 올라가 있는 경우
    mockApi([])
    const finals = [pdfFinal(12, 2), pdfFinal(11, 1)]
    // when
    render(<WeeklyExamHub {...baseProps} finals={finals} />)
    // then
    const table = await screen.findByRole('table', { name: '완성본 파일 목록' })
    expect(within(table).getByText('신구중2 예상문제 1회 문제지')).toBeInTheDocument()
    expect(within(table).getByText('신구중2 예상문제 2회 문제지')).toBeInTheDocument()
    expect(screen.getByText('완성본 파일 2건')).toBeInTheDocument()
    expect(screen.queryByText('준비 전')).not.toBeInTheDocument()
    // when: 원본 열기
    fireEvent.click(within(table).getAllByRole('button', { name: '원본 열기' })[0])
    // then
    expect(opened).toEqual(['/problem-generator/wiki/api/final-deliverables/12/original'])
  })

  it('given HTML이 있는 완성본 when 화면을 열면 then HTML 보기와 원본 열기를 모두 준다', async () => {
    // given
    mockApi([])
    const finals: FinalFile[] = [{ ...pdfFinal(21, 3), has_html: 1 }]
    // when
    render(<WeeklyExamHub {...baseProps} finals={finals} />)
    const table = await screen.findByRole('table', { name: '완성본 파일 목록' })
    fireEvent.click(within(table).getByRole('button', { name: 'HTML 보기' }))
    fireEvent.click(within(table).getByRole('button', { name: '원본 열기' }))
    // then
    expect(opened).toEqual([
      '/problem-generator/wiki/api/final-deliverables/21/html',
      '/problem-generator/wiki/api/final-deliverables/21/original',
    ])
  })

  it('given 아무 자료도 없을 때 when 화면을 열면 then "준비 전"이고 완성본 파일 칸은 없다', async () => {
    // given
    mockApi([])
    // when
    render(<WeeklyExamHub {...baseProps} />)
    // then
    expect(await screen.findByText('준비 전')).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: '완성본 파일 목록' })).not.toBeInTheDocument()
  })

  it.each(['REVIEW', 'CHANGES_REQUESTED', 'FINAL'])(
    'given 저장 상태가 %s인 회차 when 화면을 열면 then 상태·승인 표시 없이 자료가 바로 보인다',
    async (legacy) => {
      // given
      mockApi([weeklyItem(41, legacy)])
      // when
      const { container } = render(<WeeklyExamHub {...baseProps} />)
      // then: 회차 자료가 보이고
      const table = await screen.findByRole('table', { name: '5회 자료' })
      expect(within(table).getAllByRole('button', { name: '보기' }).length).toBe(2)
      expect(screen.getByText('5회 · 40문항')).toBeInTheDocument()
      // then: 승인 관련 버튼·문구는 없다
      for (const name of ['FINAL 확정', '승인', '반려', '수정 요청', '게시', 'DRAFT로 다시 열기']) {
        expect(screen.queryByRole('button', { name })).not.toBeInTheDocument()
      }
      expect(container.textContent).not.toMatch(/DRAFT|REVIEW|최근 FINAL|검토 중|승인/)
    },
  )

  it('given 등록 폼 when 열면 then 버튼 이름에 DRAFT가 없다', async () => {
    // given
    mockApi([])
    render(<WeeklyExamHub {...baseProps} />)
    // when
    fireEvent.click(screen.getByRole('button', { name: '시험 자료 등록' }))
    // then
    expect(screen.getByRole('button', { name: '등록하기' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'DRAFT 등록' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('주간 실전 모의고사 등록')).toBeInTheDocument()
  })
})

describe('parseRoundInput — 회차 칸 자유 이름(2026-11-24)', () => {
  it('given 숫자 또는 「3회」 when 등록 값 then 그 회차, 이름 없음', () => {
    // given / when / then
    expect(parseRoundInput('3', 8)).toEqual({ round: 3, roundLabel: '' })
    expect(parseRoundInput(' 3회 ', 8)).toEqual({ round: 3, roundLabel: '' })
  })
  it('given 「1-7회」 when 등록 값 then 이름으로 쓰고 회차 숫자는 다음 회차', () => {
    // given / when / then
    expect(parseRoundInput('1-7회', 8)).toEqual({ round: 8, roundLabel: '1-7회' })
  })
  it('given 빈 칸 when 등록 값 then 다음 회차', () => {
    // given / when / then
    expect(parseRoundInput('  ', 8)).toEqual({ round: 8, roundLabel: '' })
  })
})
