import { afterEach, describe, expect, it, vi } from 'vitest'
import { extractFiles, extractionSummary, joinExtractedPages, summaryMessage, type ExtractedPage } from './ocrExtract'

afterEach(() => { vi.restoreAllMocks(); vi.resetModules() })

const page = (over: Partial<ExtractedPage>): ExtractedPage => ({ name: 'p', previewUrl: '', text: '', usedOcr: false, ok: true, ...over })

describe('ocrExtract page accounting', () => {
  it('summary counts every page including failures', () => {
    const s = extractionSummary([page({ name: '1' }), page({ name: '2', ok: false, error: 'x' }), page({ name: '3' })])
    expect(s).toEqual({ total: 3, succeeded: 2, failed: 1, failedPages: ['2'] })
  })

  it('summaryMessage never says "완료" when pages failed', () => {
    expect(summaryMessage({ total: 55, succeeded: 55, failed: 0, failedPages: [] })).toContain('전체 55쪽 모두')
    const partial = summaryMessage({ total: 55, succeeded: 8, failed: 47, failedPages: ['x 9/55쪽'] })
    expect(partial).toContain('전체 55쪽')
    expect(partial).toContain('성공 8쪽')
    expect(partial).toContain('실패 47쪽')
  })

  it('joinExtractedPages marks failed pages instead of dropping them silently', () => {
    const joined = joinExtractedPages([page({ name: 'A', text: 'ok' }), page({ name: 'B', ok: false, error: '렌더 실패' })])
    expect(joined).toContain('A')
    expect(joined).toContain('ok')
    expect(joined).toContain('[추출 실패: 렌더 실패]')
  })

  it('reads a .txt file end to end as one successful page', async () => {
    const file = { name: 'note.txt', type: 'text/plain', text: async () => 'hello world' } as unknown as File
    const pages = await extractFiles([file])
    expect(pages).toHaveLength(1)
    expect(pages[0]).toMatchObject({ text: 'hello world', ok: true })
  })

  it('extractPdfFile returns ALL pages (no silent 8-page cap) and surfaces per-page failures', async () => {
    vi.doMock('pdfjs-dist', () => ({
      GlobalWorkerOptions: {},
      getDocument: () => ({ promise: Promise.resolve({ numPages: 20, getPage: () => Promise.reject(new Error('render blew up')) }) }),
    }))
    vi.doMock('pdfjs-dist/build/pdf.worker.min.js?url', () => ({ default: 'worker' }))
    const { extractPdfFile } = await import('./ocrExtract')
    const file = { name: 'big.pdf', type: 'application/pdf', arrayBuffer: async () => new Uint8Array([1, 2, 3]).buffer } as unknown as File
    const pages = await extractPdfFile(file)
    expect(pages).toHaveLength(20)
    expect(pages.every(p => !p.ok)).toBe(true)
    expect(extractionSummary(pages)).toMatchObject({ total: 20, succeeded: 0, failed: 20 })
  })
})
