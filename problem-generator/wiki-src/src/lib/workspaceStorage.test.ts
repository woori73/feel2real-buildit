import { beforeEach, describe, expect, it, vi } from 'vitest'
import { initializeWorkspace, storageKey } from './workspaceStorage'
import { api, selectWorkspace } from './api'

describe('Wiki browser storage workspace namespace', () => {
  beforeEach(() => { vi.restoreAllMocks(); sessionStorage.clear() })

  it('preserves legacy keys for admin/modu', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      workspace_id: 'legacy', storage_namespace: 'legacy', legacy: true,
      user_id: '1', login_id: 'admin', role: 'ADMIN',
      workspace_admin: true, workspace_owner_login_id: 'admin',
    }), { status: 200 })))
    await initializeWorkspace()
    expect(storageKey('f2r_school_prints')).toBe('f2r_school_prints')
  })

  it('namespaces every private-user key without reading legacy storage', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      workspace_id: 'private-a', storage_namespace: 'user-101', legacy: false,
      user_id: '101', login_id: 'new-a', role: 'TUTOR',
      workspace_admin: false, workspace_owner_login_id: 'new-a',
    }), { status: 200 })))
    await initializeWorkspace()
    for (const key of ['f2r_school_prints', 'f2r_school_materials', 'f2r_textbook_materials', 'f2r_wrong_review_materials']) {
      expect(storageKey(key)).toBe(`${key}::user-101`)
    }
  })

  it('adds an explicit workspace header only after admin selection and clears back to legacy', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    selectWorkspace('private-a')
    await api('students')
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('X-BuildIt-Wiki-Workspace')).toBe('private-a')
    selectWorkspace('')
    await api('students')
    expect(new Headers(fetchMock.mock.calls[1][1].headers).has('X-BuildIt-Wiki-Workspace')).toBe(false)
  })

  it('drops a stale admin selection when the newly signed user is denied', async () => {
    selectWorkspace('private-a')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('{"detail":"admin workspace access required"}', { status: 403 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        workspace_id: 'private-b', storage_namespace: 'user-202', legacy: false,
        user_id: '202', login_id: 'new-b', role: 'TUTOR', workspace_admin: false,
        workspace_owner_login_id: 'new-b',
      }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const identity = await initializeWorkspace()
    expect(identity.workspace_id).toBe('private-b')
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('X-BuildIt-Wiki-Workspace')).toBe('private-a')
    expect(new Headers(fetchMock.mock.calls[1][1].headers).has('X-BuildIt-Wiki-Workspace')).toBe(false)
  })
})
