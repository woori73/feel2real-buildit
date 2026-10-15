import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadExamContext, resolveSavedContext, saveExamContext } from './wikiContext'
import { initializeWorkspace } from './workspaceStorage'
import type { SchoolNode } from './types'

const tree: SchoolNode[] = [
  { school: '신구중', grades: [
    { grade: '2학년', exams: [] },
    { grade: '3학년', exams: [] },
  ] },
  { school: '신사중', grades: [{ grade: '1학년', exams: [] }] },
]

const asLegacy = () => vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
  workspace_id: 'legacy', storage_namespace: 'legacy', legacy: true,
  user_id: '1', login_id: 'admin', role: 'ADMIN', workspace_admin: true, workspace_owner_login_id: 'admin',
}), { status: 200 })))

const asUser = (ns: string) => vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
  workspace_id: ns, storage_namespace: ns, legacy: false,
  user_id: '9', login_id: 'x', role: 'TUTOR', workspace_admin: false, workspace_owner_login_id: 'x',
}), { status: 200 })))

const memoryStorage = () => {
  const values = new Map<string, string>()
  return {
    getItem: (k: string) => values.get(k) ?? null,
    setItem: (k: string, v: string) => { values.set(k, String(v)) },
    removeItem: (k: string) => { values.delete(k) },
    clear: () => values.clear(),
    key: (i: number) => [...values.keys()][i] ?? null,
    get length() { return values.size },
  }
}

describe('wiki exam-context persistence', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('localStorage', memoryStorage())
    vi.stubGlobal('sessionStorage', memoryStorage())
  })

  it('round-trips the last selected context', async () => {
    asLegacy(); await initializeWorkspace()
    saveExamContext({ school: '신구중', grade: '3학년', year: 2026, semester: '2학기', exam: '중간고사' })
    expect(loadExamContext()).toEqual({ school: '신구중', grade: '3학년', year: 2026, semester: '2학기', exam: '중간고사' })
  })

  it('keeps each workspace context separate', async () => {
    asUser('user-101'); await initializeWorkspace()
    saveExamContext({ school: '신구중', grade: '2학년', year: 2026, semester: '1학기', exam: '전체' })
    asUser('user-202'); await initializeWorkspace()
    expect(loadExamContext()).toEqual({})
    asUser('user-101'); await initializeWorkspace()
    expect(loadExamContext().school).toBe('신구중')
  })

  it('does not persist an incomplete context', async () => {
    asLegacy(); await initializeWorkspace()
    saveExamContext({ school: '', grade: '', year: 2026, semester: '1학기', exam: '전체' })
    expect(loadExamContext()).toEqual({})
  })

  it('restores a still-valid saved context unchanged', () => {
    expect(resolveSavedContext({ school: '신구중', grade: '3학년', year: 2025, semester: '2학기', exam: '기말고사' }, tree, 2026))
      .toEqual({ school: '신구중', grade: '3학년', year: 2025, semester: '2학기', exam: '기말고사' })
  })

  it('falls back to the first school/grade only when the saved one is gone', () => {
    expect(resolveSavedContext({ school: '없어진중', grade: '9학년', exam: '중간고사' }, tree, 2026))
      .toEqual({ school: '신구중', grade: '2학년', year: 2026, semester: '1학기', exam: '중간고사' })
  })

  it('keeps the saved school but repairs an invalid grade', () => {
    expect(resolveSavedContext({ school: '신사중', grade: '3학년', exam: '전체' }, tree, 2026).grade).toBe('1학년')
  })

  it('sanitizes a corrupt exam/semester value', () => {
    const r = resolveSavedContext({ school: '신구중', grade: '2학년', semester: 'x', exam: 'garbage' } as never, tree, 2026)
    expect(r.exam).toBe('전체')
    expect(r.semester).toBe('1학기')
  })
})
