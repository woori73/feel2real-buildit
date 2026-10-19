import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import GrammarBank from './GrammarBank'
afterEach(() => { cleanup(); vi.unstubAllGlobals() })
it('combines grade/difficulty/type and updates recommended grade without changing original grade', async () => {
  const calls: { url: string; init?: RequestInit }[] = []
  const item = { questionId: 459, schoolLevel: 'M3', recommendedGrade: 'H1', difficulty: 'HARD', tags: [{ tagId: 7, nameKo: '분사·분사구문', primary: true }], reviewStatus: 'NEEDS_REVIEW', rightsStatus: 'UNKNOWN', stemExcerpt: 'The weather being fine, we went out.' }
  const detail = { ...item, questionType: 'MULTIPLE_CHOICE', questionText: item.stemExcerpt, choices: ['being', 'is'], correctAnswer: '1', variants: [], externalRef: 'WG-0459', reviewMetadata: { concept_code: 'absolute', original_ids: ['source-459'], choice_explanations: [{ label: '2', text: '정동사 오답 근거', is_correct: false }] } }
  vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
    const u = String(url); calls.push({ url: u, init }); let body: unknown = []
    if (u.endsWith('/stats')) body = { total: 1, needsReview: 1, approved: 0, rejected: 0, bySchoolLevel: [], byGrammarTag: [] }
    else if (u.endsWith('/tags')) body = [{ tagId: 7, nameKo: '분사·분사구문', sortOrder: 7 }]
    else if (u.includes('/questions?')) body = { content: [item], totalElements: 1 }
    else if (u.endsWith('/questions/459/profile')) body = { ...detail, recommendedGrade: 'H2' }
    else if (u.endsWith('/questions/459')) body = detail
    return new Response(JSON.stringify(body), { status: 200 })
  }))
  render(<GrammarBank />); fireEvent.click(screen.getByRole('button', { name: '문제 검색' }))
  await screen.findByRole('button', { name: '상세·원본' })
  fireEvent.change(screen.getByLabelText('권장 학습 학년'), { target: { value: 'H1' } })
  fireEvent.change(screen.getByLabelText('난이도'), { target: { value: 'HARD' } })
  fireEvent.change(screen.getByLabelText('문법 유형'), { target: { value: '7' } })
  await waitFor(() => expect(calls.some(c => ['recommendedGrade=H1', 'difficulty=HARD', 'tagIds=7'].every(p => c.url.includes(p)))).toBe(true))
  fireEvent.click(screen.getByRole('button', { name: '상세·원본' })); await screen.findByText(/기존 학년: 중3/)
  expect(screen.getByText(/정동사 오답 근거/)).toBeInTheDocument(); expect(screen.getByText('source-459')).toBeInTheDocument()
  fireEvent.change(screen.getAllByLabelText('권장 학습 학년')[1], { target: { value: 'H2' } })
  fireEvent.click(screen.getByRole('button', { name: '권장 학년 저장' }))
  await waitFor(() => expect(calls.some(c => c.init?.method === 'PATCH')).toBe(true))
  expect(JSON.parse(String(calls.find(c => c.init?.method === 'PATCH')!.init?.body))).toEqual({ recommendedGrade: 'H2' })
  expect(await screen.findByText(/기존 학년은 보존되며/)).toBeInTheDocument()
})
it('keeps selections across pages and applies the review queue filter', async () => {
  const urls: string[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL) => {
    const u = String(url); urls.push(u); let body: unknown = []
    if (u.endsWith('/stats')) body = { total: 51, needsReview: 51, approved: 0, rejected: 0, bySchoolLevel: [], byGrammarTag: [] }
    else if (u.includes('/questions?')) {
      const second = new URL('http://local' + u).searchParams.get('page') === '1'
      body = { totalElements: 51, content: [{ questionId: second ? 51 : 1, schoolLevel: 'M1', recommendedGrade: 'M2', difficulty: 'EASY', tags: [], reviewStatus: 'NEEDS_REVIEW', rightsStatus: 'UNKNOWN', stemExcerpt: 'Example.' }] }
    }
    return new Response(JSON.stringify(body), { status: 200 })
  }))
  render(<GrammarBank />); fireEvent.click(screen.getByRole('button', { name: '자동 분류 검수' }))
  fireEvent.click(await screen.findByLabelText('1 선택'))
  fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))
  await screen.findByLabelText('51 선택')
  expect(screen.getByText(/선택 1개/)).toBeInTheDocument()
  expect(urls.some(u => u.includes('page=1') && u.includes('reviewStatus=NEEDS_REVIEW'))).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: '이전 페이지' }))
  expect(await screen.findByLabelText('1 선택')).toBeChecked()
})
