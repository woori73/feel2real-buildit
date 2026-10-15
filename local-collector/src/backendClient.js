// Thin HTTP client for the EASY collector-agent contract (/api/admin/easy/collector-agent/**).
// This is the ONLY place the collector talks to EASY. It sends status transitions and, on
// completion, file metadata (name/size/sha256) — never file content, never any credential.
import { config } from './config.js';

function authHeaders() {
  return {
    'Authorization': `Bearer ${config.collectorToken}`,
    'Content-Type': 'application/json',
  };
}

async function request(method, path, body) {
  const res = await fetch(`${config.easyBaseUrl}${path}`, {
    method,
    headers: authHeaders(),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204) return null;
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`EASY ${method} ${path} -> ${res.status}: ${text}`);
  }
  const contentType = res.headers.get('content-type') || '';
  return contentType.includes('application/json') ? res.json() : null;
}

export const backendClient = {
  heartbeat: () => request('POST', '/api/admin/easy/collector-agent/heartbeat'),
  nextTask: () => request('GET', '/api/admin/easy/collector-agent/next-task'),
  waitingForAuth: (taskId) =>
    request('POST', `/api/admin/easy/collector-agent/tasks/${taskId}/waiting-for-auth`),
  resume: (taskId) => request('POST', `/api/admin/easy/collector-agent/tasks/${taskId}/resume`),
  collecting: (taskId) =>
    request('POST', `/api/admin/easy/collector-agent/tasks/${taskId}/collecting`),
  downloading: (taskId) =>
    request('POST', `/api/admin/easy/collector-agent/tasks/${taskId}/downloading`),
  completed: (taskId, { filename, size, sha256 }) =>
    request('POST', `/api/admin/easy/collector-agent/tasks/${taskId}/completed`,
      { filename, size, sha256 }),
  failed: (taskId, reason) =>
    request('POST', `/api/admin/easy/collector-agent/tasks/${taskId}/failed`, { reason }),
};
