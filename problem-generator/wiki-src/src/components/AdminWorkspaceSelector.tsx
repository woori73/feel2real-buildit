import { useEffect, useState } from 'react'
import { api, selectWorkspace } from '../lib/api'
import { workspaceIdentity } from '../lib/workspaceStorage'

type WorkspaceSummary = {
  workspace_id: string
  legacy: boolean
  user_id: string | null
  login_id: string | null
}

export default function AdminWorkspaceSelector() {
  const identity = workspaceIdentity()
  const [workspaces, setWorkspaces] = useState<WorkspaceSummary[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    if (!identity?.workspace_admin) return
    api('admin/workspaces')
      .then(async response => {
        if (!response.ok) throw new Error('Workspace 목록을 불러오지 못했습니다.')
        setWorkspaces(await response.json() as WorkspaceSummary[])
      })
      .catch(reason => setError(reason instanceof Error ? reason.message : 'Workspace 목록을 불러오지 못했습니다.'))
  }, [identity?.workspace_admin])

  if (!identity?.workspace_admin) return null
  const activeLabel = identity.legacy
    ? 'legacy (admin · modu 기존 자료)'
    : `${identity.workspace_owner_login_id || '사용자'} · ${identity.workspace_id}`

  const changeWorkspace = (workspaceId: string) => {
    selectWorkspace(workspaceId === 'legacy' ? '' : workspaceId)
    window.location.reload()
  }

  return <section className="admin-workspace-selector" aria-label="관리자 Workspace 선택">
    <div><b>관리 중인 Workspace</b><span>{activeLabel}</span></div>
    <label>사용자 선택
      <select value={identity.workspace_id} onChange={event => changeWorkspace(event.target.value)}>
        <option value="legacy">legacy · admin/modu</option>
        {workspaces.filter(item => !item.legacy).map(item =>
          <option key={item.workspace_id} value={item.workspace_id}>{item.login_id || item.user_id || item.workspace_id}</option>
        )}
      </select>
    </label>
    {!identity.legacy && <button type="button" onClick={() => changeWorkspace('legacy')}>선택 해제 · legacy로 복귀</button>}
    {error && <small role="alert">{error}</small>}
  </section>
}
