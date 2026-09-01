import { beforeEach, describe, expect, it, vi } from 'vitest'
import { initializeWorkspace, storageKey } from './workspaceStorage'

describe('Wiki browser storage workspace namespace', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('preserves legacy keys for admin/modu', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      workspace_id: 'legacy', storage_namespace: 'legacy', legacy: true,
      user_id: '1', login_id: 'admin', role: 'ADMIN',
    }), { status: 200 })))
    await initializeWorkspace()
    expect(storageKey('f2r_school_prints')).toBe('f2r_school_prints')
  })

  it('namespaces every private-user key without reading legacy storage', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      workspace_id: 'private-a', storage_namespace: 'user-101', legacy: false,
      user_id: '101', login_id: 'new-a', role: 'TUTOR',
    }), { status: 200 })))
    await initializeWorkspace()
    for (const key of ['f2r_school_prints', 'f2r_school_materials', 'f2r_textbook_materials', 'f2r_wrong_review_materials']) {
      expect(storageKey(key)).toBe(`${key}::user-101`)
    }
  })
})
