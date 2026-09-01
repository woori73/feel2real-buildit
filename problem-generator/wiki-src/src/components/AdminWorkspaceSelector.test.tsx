import { cleanup, render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminWorkspaceSelector from './AdminWorkspaceSelector'
import { initializeWorkspace } from '../lib/workspaceStorage'

describe('admin Wiki workspace selector', () => {
  beforeEach(() => sessionStorage.clear())
  afterEach(() => { cleanup(); vi.unstubAllGlobals() })

  it('shows the signed admin the active legacy workspace and private users', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/workspace/me')) return new Response(JSON.stringify({
        workspace_id: 'legacy', storage_namespace: 'legacy', legacy: true,
        user_id: '1', login_id: 'admin', role: 'ADMIN', workspace_admin: true,
        workspace_owner_login_id: null,
      }), { status: 200 })
      return new Response(JSON.stringify([
        { workspace_id: 'legacy', legacy: true, user_id: null, login_id: null },
        { workspace_id: 'private-a', legacy: false, user_id: '101', login_id: 'new-a' },
      ]), { status: 200 })
    }))
    await initializeWorkspace()
    render(<AdminWorkspaceSelector />)
    expect(screen.getByText('legacy (admin · modu 기존 자료)')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('option', { name: 'new-a' })).toBeInTheDocument())
  })

  it('does not render workspace controls for an ordinary signed user', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      workspace_id: 'private-a', storage_namespace: 'user-101', legacy: false,
      user_id: '101', login_id: 'new-a', role: 'TUTOR', workspace_admin: false,
      workspace_owner_login_id: 'new-a',
    }), { status: 200 })))
    await initializeWorkspace()
    const { container } = render(<AdminWorkspaceSelector />)
    expect(container).toBeEmptyDOMElement()
  })
})
