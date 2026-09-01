import { api } from './api'

export type WorkspaceIdentity = {
  workspace_id: string
  storage_namespace: string
  legacy: boolean
  user_id: string
  login_id: string
  role: string
}

let current: WorkspaceIdentity | null = null

export async function initializeWorkspace(): Promise<WorkspaceIdentity> {
  const response = await api('workspace/me')
  if (!response.ok) throw new Error('Wiki 사용자 저장공간을 확인하지 못했습니다.')
  current = await response.json() as WorkspaceIdentity
  return current
}

export function storageKey(base: string): string {
  // admin/modu keep the exact historical keys so Production data is neither copied nor lost.
  if (!current || current.legacy) return base
  return `${base}::${current.storage_namespace}`
}

export function workspaceIdentity(): WorkspaceIdentity | null {
  return current
}
