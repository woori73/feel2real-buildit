// dc_b_console.js 시나리오 테스트(가짜 서버). given / when / then.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const { dcSynthTest } = createRequire(import.meta.url)('./dc_b_console.js');

function fakeServer(opts = {}) {
  const s = { gate: opts.gate ?? { state: 'CLOSED', activeOperations: 0, liveCliProcesses: 0 }, jobs: new Map(), calls: [], nextId: 77, drainFails: opts.drainFails ?? 0, polls: 0 };
  const res = (status, body, headers = {}) => ({ status, headers: { get: (k) => headers[k.toLowerCase()] ?? null }, json: async () => body, blob: async () => ({ size: body?.__size ?? 5000 }), arrayBuffer: async () => body?.__buf ?? new TextEncoder().encode('%PDF-1.7 ....').buffer });
  const fn = async (url, init = {}) => {
    const method = init.method || 'GET'; s.calls.push(method + ' ' + url);
    if (url === '/api/admin/publishing/maintenance' && method === 'GET') return res(200, { ...s.gate });
    if (url.endsWith('/maintenance/resume')) { s.gate.state = 'OPEN'; return res(200, { ...s.gate }); }
    if (url.endsWith('/maintenance/drain')) { if (s.drainFails > 0) { s.drainFails--; return res(500, null); } s.gate.state = 'CLOSED'; return res(200, { ...s.gate }); }
    if (url === '/api/publishing/jobs' && method === 'POST') {
      if (opts.createFails) return res(500, null);
      const id = s.nextId++; s.jobs.set(id, { jobId: id, title: JSON.parse(init.body).title, status: 'QUEUED' }); return res(202, { ...s.jobs.get(id) });
    }
    let m;
    if ((m = url.match(/^\/api\/publishing\/jobs\/(\d+)$/)) && method === 'GET') {
      const j = s.jobs.get(+m[1]); if (!j) return res(404, null);
      s.polls++; if (j.status === 'QUEUED' && s.polls >= (opts.pollsUntilRunning ?? 2)) j.status = 'RUNNING';
      return res(200, { ...j });
    }
    if ((m = url.match(/^\/api\/publishing\/jobs\/(\d+)$/)) && method === 'DELETE') { s.jobs.delete(+m[1]); return res(204, null); }
    if ((m = url.match(/^\/api\/publishing\/jobs\/(\d+)\/cancel$/))) { s.jobs.get(+m[1]).status = 'CANCELLED'; return res(200, { ...s.jobs.get(+m[1]) }); }
    if (url.endsWith('/manuscript')) return res(200, { applied: true, pageCount: 4 });
    if (url.endsWith('/pages/1/png')) return res(200, { __size: 100000 }, { 'content-type': 'image/png' });
    if (url.endsWith('/export')) {
      if (opts.exportFails) return res(422, { message: '검수 게이트 실패' });
      const id = +url.match(/jobs\/(\d+)/)[1]; s.jobs.get(id).status = 'DONE'; return res(202, { artifactId: 9, format: 'pdf', standard: 'x4', fileSize: 13 });
    }
    if (url.endsWith('/artifacts')) return res(200, { artifacts: [{ artifactId: 9, format: 'pdf', fileSize: 13 }] });
    if (url.endsWith('/artifacts/9')) return res(200, { __buf: new TextEncoder().encode('%PDF-1.7 ....').buffer }, { 'content-type': 'application/pdf' });
    return res(404, null);
  };
  return { fn, s };
}
const quiet = (t) => { const orig = console.log; console.log = () => {}; t.after(() => { console.log = orig; }); };
const O = { tickMs: 1, confirmWatchdog: true, suffix: 'T' };

test('성공 경로: PDF를 확인한 뒤 내려받고, 내 작업만 삭제한 다음 drain한다', async (t) => {
  quiet(t);
  // given: 깨끗한 CLOSED 게이트의 가짜 서버
  const { fn, s } = fakeServer();
  // when
  const out = await dcSynthTest(fn, O);
  // then
  assert.equal(out.ok, true); assert.equal(out.finalClosed, true);
  const idx = (needle) => s.calls.findIndex((c) => c.includes(needle));
  assert.ok(idx('/export') < idx('/artifacts/9'), 'export 확인 전에 다운로드하지 않는다');
  assert.ok(idx('DELETE /api/publishing/jobs/77') < idx('/maintenance/drain'), '정리는 drain보다 먼저');
  assert.equal(s.calls.filter((c) => c.startsWith('DELETE')).length, 1);
  assert.equal(s.calls.filter((c) => c.includes('/cancel')).length, 0, 'DONE 작업은 취소하지 않는다');
  assert.equal(s.gate.state, 'CLOSED');
});

test('사전조건이 깨끗하지 않으면 resume하지 않는다', async (t) => {
  quiet(t);
  const { fn, s } = fakeServer({ gate: { state: 'CLOSED', activeOperations: 1, liveCliProcesses: 0 } });
  const out = await dcSynthTest(fn, O);
  assert.equal(out.ok, false);
  assert.ok(!s.calls.some((c) => c.includes('/resume')));
  assert.ok(!s.calls.some((c) => c.includes('/drain')), 'resume하지 않았으므로 drain도 하지 않는다');
});

test('감시기 가동 확인이 없으면 시작하지 않는다', async (t) => {
  quiet(t);
  const { fn, s } = fakeServer();
  const out = await dcSynthTest(fn, { ...O, confirmWatchdog: false });
  assert.equal(out.ok, false); assert.equal(s.calls.length, 0);
});

test('export가 422여도 재시도하지 않고 내 작업을 정리한 뒤 drain한다', async (t) => {
  quiet(t);
  const { fn, s } = fakeServer({ exportFails: true });
  const out = await dcSynthTest(fn, O);
  assert.equal(out.ok, false); assert.equal(out.finalClosed, true);
  assert.equal(s.calls.filter((c) => c.endsWith('/export')).length, 1, 'export 재시도 금지');
  assert.equal(s.calls.filter((c) => c.includes('/cancel')).length, 1, 'RUNNING 상태였으므로 취소 후 삭제');
  assert.equal(s.calls.filter((c) => c.startsWith('DELETE')).length, 1);
  assert.equal(s.gate.state, 'CLOSED');
});

test('작업 생성이 실패해도 drain으로 CLOSED를 복구한다(삭제할 작업 없음)', async (t) => {
  quiet(t);
  const { fn, s } = fakeServer({ createFails: true });
  const out = await dcSynthTest(fn, O);
  assert.equal(out.ok, false); assert.equal(out.finalClosed, true);
  assert.equal(s.calls.filter((c) => c.startsWith('DELETE')).length, 0);
  assert.equal(s.calls.filter((c) => c.includes('/jobs') && c.startsWith('POST')).length, 1, '생성 재시도 금지');
});

test('RUNNING이 되지 않아 전체 시간이 초과되면 취소·삭제 후 drain한다', async (t) => {
  quiet(t);
  const { fn, s } = fakeServer({ pollsUntilRunning: 10 ** 9 });
  const out = await dcSynthTest(fn, { ...O, totalMs: 30 });
  assert.equal(out.ok, false); assert.equal(out.finalClosed, true);
  assert.equal(s.calls.filter((c) => c.includes('/cancel')).length, 1);
  assert.equal(s.gate.state, 'CLOSED');
});

test('첫 drain이 실패해도 재시도해 CLOSED를 복구한다', async (t) => {
  quiet(t);
  const { fn, s } = fakeServer({ drainFails: 1 });
  const out = await dcSynthTest(fn, O);
  assert.equal(out.finalClosed, true);
  assert.equal(s.calls.filter((c) => c.endsWith('/drain')).length, 2);
});

test('정리 시점에 조회한 작업의 제목이 내 것이 아니면 아무것도 삭제하지 않는다', async (t) => {
  quiet(t);
  // given: 다운로드까지 끝난 뒤(정리 단계)부터 같은 ID의 조회 결과 제목이 달라지는 서버
  const { fn, s } = fakeServer();
  const wrapped = async (url, init) => {
    const r = await fn(url, init);
    const downloaded = s.calls.some((c) => c.endsWith('/artifacts/9'));
    if (downloaded && /^\/api\/publishing\/jobs\/77$/.test(url) && (init?.method || 'GET') === 'GET') {
      return { ...r, json: async () => ({ jobId: 77, title: '남의 작업', status: 'RUNNING' }) };
    }
    return r;
  };
  // when
  const out = await dcSynthTest(wrapped, O);
  // then: DELETE·cancel 호출이 없고, drain은 수행된다
  assert.equal(s.calls.filter((c) => c.startsWith('DELETE')).length, 0);
  assert.equal(s.calls.filter((c) => c.includes('/cancel')).length, 0);
  assert.equal(out.finalClosed, true);
  assert.ok(out.steps.some(([k]) => k === 'cleanup skipped'));
});
