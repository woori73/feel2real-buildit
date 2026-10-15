import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import WrongPhotoAiRestore from './WrongPhotoAiRestore'

// 오답 사진 → AI 복원 → 승인 저장 → 즉시 조회 시나리오.
// OCR 브라우저 의존(tesseract/pdfjs)은 모듈 경계에서 가짜 페이지로 대체한다.
vi.mock('../lib/ocrExtract', () => ({
  acceptedFileTypes: 'image/jpeg,image/png,application/pdf',
  maxUploadFiles: 12,
  joinExtractedPages: (pages: { name: string; text: string }[]) => pages.map(page => `──── ${page.name} ────\n${page.text}`).join('\n\n'),
  extractFiles: async () => [{ name: 'photo-1.jpg', previewUrl: 'blob:preview-1', text: '1. Choose the correct word.', usedOcr: true, ok: true }],
}))

const student = { id: 7, name: '최승원', school: '세화고', grade: '3학년', semester: '2학기', exam_type: '기말', created_at: '2026.09.13' }
const restoredHtml = '<section><h2>문항 1</h2><p>Choose: <u>She</u> goes.</p></section>'
const savedNote = { id: 101, student_id: 7, student_name: '최승원', student_school: '세화고', student_grade: '3학년', exam_label: '2026 2학기 기말고사', title: '최승원 오답 복원 문제', question_type: 'AI 복원', wrong_text: '문항 1 Choose: She goes.', answer_text: '', source_text: '1. Choose the correct word.', created_at: '2026-09-13T12:00:00' }
const savedMaterial = { id: 55, title: '최승원 오답 복원 문제', original_name: 'photo-1.jpg', original_type: 'image/jpeg', created_at: '2026-09-13T12:00:00' }

beforeEach(() => {
  const storage = () => { const values = new Map<string, string>(); return { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key), clear: () => values.clear(), key: () => null, get length() { return values.size } } }
  vi.stubGlobal('localStorage', storage())
  vi.stubGlobal('sessionStorage', storage())
  URL.createObjectURL = vi.fn(() => 'blob:preview')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks() })

const jsonResponse = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

describe('오답 사진 AI 복원', () => {
  it('OCR이 실패해도(텍스트 없음) AI 복원을 호출하지 않고 안내한다', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/students')) return jsonResponse([student])
      return jsonResponse([])
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<WrongPhotoAiRestore />)
    await waitFor(() => expect(screen.getByRole('combobox')).toBeInTheDocument())
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '7' } })
    await waitFor(() => expect(screen.getByLabelText(/오답 사진\/PDF 업로드/)).toBeInTheDocument())
    expect(screen.queryByText(/승인 · 학생 오답으로 저장/)).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining('students/wrong-restore'), expect.anything())
  })

  it('AI 복원 결과에 판독 필요 표시가 있으면 검토 경고를 보여주고, 승인 전까지 저장 API를 호출하지 않는다', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/students')) return jsonResponse([student])
      if (url.includes('students/wrong-restore')) {
        return jsonResponse({ html: restoredHtml + '<mark>판독 필요: 선택지 3번</mark>', canonicalJson: { version: 1 }, reviewMarkCount: 1, reviewMarkText: '판독 필요', provider: 'openrouter', model: 'qwen/qwen3.7-flash' })
      }
      return jsonResponse([])
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<WrongPhotoAiRestore />)
    await waitFor(() => expect(screen.getByRole('combobox')).toBeInTheDocument())
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '7' } })
    await waitFor(() => expect(screen.getByLabelText(/오답 사진\/PDF 업로드/)).toBeInTheDocument())
    fireEvent.change(document.querySelector('label.wrong-upload-btn input[type="file"]')!, { target: { files: [new File(['x'], 'photo-1.jpg', { type: 'image/jpeg' })] } })
    await waitFor(() => expect(screen.getByText(/판독이 불확실한 부분입니다/)).toBeInTheDocument())
    // 승인 전이므로 저장(POST)은 호출하지 않는다. 조회(GET)는 학생 선택 시 정상적으로 일어난다.
    const postCalls = fetchMock.mock.calls.filter((call: unknown[]) => String(call[0]).includes('exam-materials') && (call[1] as RequestInit | undefined)?.method === 'POST')
    expect(postCalls).toHaveLength(0)
    const saveCalls = fetchMock.mock.calls.filter((call: unknown[]) => String(call[0]).includes('wrong-notes') && (call[1] as RequestInit | undefined)?.method === 'POST')
    expect(saveCalls).toHaveLength(0)
  })

  it('승인 저장 성공 후 저장된 오답 자료가 즉시 표시되고(원본 보관 포함) 조회 API를 다시 호출한다', async () => {
    let savedNotes: unknown[] = []
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/students')) return jsonResponse([student])
      if (url.includes('students/wrong-restore')) {
        return jsonResponse({ html: restoredHtml, canonicalJson: { version: 2 }, reviewMarkCount: 0, reviewMarkText: '판독 필요', provider: 'openrouter', model: 'qwen/qwen3.7-flash' })
      }
      if (url.endsWith('/exam-materials') && init?.method === 'POST') return jsonResponse({ id: 55 }, 201)
      if (url.endsWith('/wrong-notes') && init?.method === 'POST') {
        savedNotes = [savedNote]
        return jsonResponse(savedNote, 201)
      }
      if (url.includes('/wrong-notes') && (!init?.method || init.method === 'GET')) return jsonResponse(savedNotes)
      if (url.includes('/wrong-materials')) return jsonResponse([savedMaterial])
      return jsonResponse({})
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<WrongPhotoAiRestore />)
    await waitFor(() => expect(screen.getByRole('combobox')).toBeInTheDocument())
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '7' } })
    await waitFor(() => expect(screen.getByLabelText(/오답 사진\/PDF 업로드/)).toBeInTheDocument())

    // 업로드 → AI 복원 → 승인 저장
    fireEvent.change(document.querySelector('label.wrong-upload-btn input[type="file"]')!, { target: { files: [new File(['x'], 'photo-1.jpg', { type: 'image/jpeg' })] } })
    await waitFor(() => expect(screen.getByRole('button', { name: '승인 · 학생 오답으로 저장' })).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: '승인 · 학생 오답으로 저장' }))

    // then: 저장 성공 안내가 "저장 완료" 시점에 노출되고, 저장된 목록이 즉시 렌더된다.
    await waitFor(() => expect(screen.getByText(/승인 저장 완료 — 최승원 학생 오답으로 누적했습니다/)).toBeInTheDocument())
    await waitFor(() => expect(screen.getByText('저장된 오답 자료 · 최승원')).toBeInTheDocument())
    await waitFor(() => expect(screen.getByText('방금 저장')).toBeInTheDocument())
    expect(screen.getByText('2026 2학기 기말고사')).toBeInTheDocument()
    expect(screen.getByText('AI 복원 · 최승원 오답 복원 문제')).toBeInTheDocument()
    // 보관 원본(사진)도 즉시 확인 가능
    await waitFor(() => expect(screen.getByText(/보관 원본 사진\/PDF 1건/)).toBeInTheDocument())
    // wrong-notes/wrong-materials 조회가 저장 후 다시 호출됐다(즉시 조회 = 서버 재조회).
    const getsAfterSave = fetchMock.mock.calls.filter(([input, init]) => String(input).includes('/wrong-notes') && (init as RequestInit | undefined)?.method !== 'POST')
    expect(getsAfterSave.length).toBeGreaterThanOrEqual(2)
  })
})
