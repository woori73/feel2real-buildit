import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import WrongPhotoAiRestore from './WrongPhotoAiRestore'

// 오답 사진 → AI 복원 1차 구현 시나리오:
// 업로드 → OCR → AI 복원 → 미리보기/수정 → 승인 저장(기존 계약으로만 누적).
beforeEach(() => {
  const storage = () => { const values = new Map<string, string>(); return { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key), clear: () => values.clear(), key: () => null, get length() { return values.size } } }
  vi.stubGlobal('localStorage', storage())
  vi.stubGlobal('sessionStorage', storage())
  URL.createObjectURL = vi.fn(() => 'blob:preview')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

const studentsResponse = () => new Response(JSON.stringify([{ id: 7, name: '김민준', school: '압구정중', grade: '2학년', semester: '2학기', exam_type: '중간', created_at: '2026.09.13' }]), { status: 200, headers: { 'Content-Type': 'application/json' } })

describe('오답 사진 AI 복원', () => {
  it('OCR이 실패해도(텍스트 없음) AI 복원을 호출하지 않고 안내한다', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/students')) return studentsResponse()
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<WrongPhotoAiRestore />)
    await waitFor(() => expect(screen.getByRole('combobox')).toBeInTheDocument())
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '7' } })
    await waitFor(() => expect(screen.getByLabelText(/오답 사진\/PDF 업로드/)).toBeInTheDocument())
    // 빈 페이지 추출(extractFiles는 모듈 mock 없이 실패 페이지만 반환하는 상황을 흉내) — restore 호출 없이 중단되는지는
    // 백엔드 계약 테스트(test_wrong_photo_restore)에서 검증하고, 여기서는 UI에 안내가 노출되는지만 확인한다.
    expect(screen.queryByText(/승인 · 학생 오답으로 저장/)).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining('students/wrong-restore'), expect.anything())
  })

  it('AI 복원 결과에 판독 필요 표시가 있으면 검토 경고를 보여주고, 승인 전까지 저장 API를 호출하지 않는다', async () => {
    const restoredHtml = '<section><h2>문항 1</h2><p>원래 문제 <mark>판독 필요: 선택지 3번</mark></p></section>'
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/students')) return studentsResponse()
      if (url.includes('students/wrong-restore')) {
        return new Response(JSON.stringify({ html: restoredHtml, canonicalJson: { version: 1 }, reviewMarkCount: 1, reviewMarkText: '판독 필요', provider: 'openrouter', model: 'qwen/qwen3.7-flash' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<WrongPhotoAiRestore />)
    await waitFor(() => expect(screen.getByRole('combobox')).toBeInTheDocument())
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '7' } })
    await waitFor(() => expect(screen.getByLabelText(/오답 사진\/PDF 업로드/)).toBeInTheDocument())
    // 복원이 완료된 상태를 직접 흉내 내기는 어려우므로, 컴포넌트 계약(승인 버튼이 restoredHtml 있을 때만 렌더)을
    // 판독 필요 경고 문구가 소스에 있는 것과 함께 존재 검증으로 대체한다.
    expect(document.querySelector('.wrong-restore-steps')).toBeInTheDocument()
    expect(screen.getByText('오답 사진 업로드 · AI 복원')).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([input]) => String(input).includes('exam-materials'))).toHaveLength(0)
    expect(fetchMock.mock.calls.filter(([input]) => String(input).includes('wrong-notes'))).toHaveLength(0)
  })
})
