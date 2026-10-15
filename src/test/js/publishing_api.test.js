const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const SRC = fs.readFileSync(
  path.join(__dirname, '../../main/resources/static/js/publishing_api.js'), 'utf8');

/** routes: { 'METHOD /path': (opts) => {status, body} } — 실제 백엔드 DTO 모양으로 응답한다. */
function loadApi(routes) {
  const calls = [];
  const fetchImpl = (url, opts) => {
    const method = (opts && opts.method) || 'GET';
    const key = method + ' ' + url.replace('/api/publishing', '');
    calls.push({ key, opts });
    const handler = routes[key];
    if (!handler) return Promise.resolve(res(404, ''));
    const r = handler(opts);
    return Promise.resolve(res(r.status || 200, r.body === undefined ? '' : r.body));
  };
  const win = {};
  const ctx = vm.createContext({
    window: win, fetch: fetchImpl, console, FormData: class { constructor() { this.f = {}; } append(k, v) { this.f[k] = v; } },
    URL: { createObjectURL: () => 'blob:x' },
  });
  vm.runInContext(SRC, ctx);
  return { api: win.PublishingApi, calls };
}

/* vm 컨텍스트의 객체는 프로토타입이 달라 deepEqual이 실패한다 — JSON으로 평탄화해 비교한다. */
const plain = (x) => JSON.parse(JSON.stringify(x));

function res(status, body) {
  const text = typeof body === 'string' ? body : JSON.stringify(body);
  return { ok: status < 400, status, url: 'u', text: () => Promise.resolve(text),
    json: () => Promise.resolve(JSON.parse(text)), blob: () => Promise.resolve({}) };
}

test('constants: given backend /templates and /styles / when constants / then maps code→id, size→mm, ids→labels', async () => {
  // given
  const { api } = loadApi({
    'GET /templates': () => ({ body: { templates: [{ code: 'ko-book-46', name: '46판 단행본', size: '188×257mm' }] } }),
    'GET /styles': () => ({ body: { paragraph: ['ko-body', 'x-unknown'], character: ['ko-char-emph'] } }),
  });
  // when
  const c = await api.constants();
  // then
  assert.deepEqual(plain(c.TEMPLATES[0]), { id: 'ko-book-46', name: '46판 단행본', size: '188×257mm', mm: [188, 257] });
  assert.deepEqual(plain(c.PARAGRAPH_STYLES), [{ id: 'ko-body', label: '본문' }, { id: 'x-unknown', label: 'x-unknown' }]);
  assert.equal(c.CHAR_STYLES[0].label, '강조');
});

test('getProject: given ready job / when getProject / then merges pageCount from /info; queued job skips /info', async () => {
  // given
  const { api, calls } = loadApi({
    'GET /jobs/1': () => ({ body: { jobId: 1, title: 't', template: 'ko-textbook-a4', status: 'RUNNING' } }),
    'GET /jobs/1/info': () => ({ body: { jobId: 1, pageCount: 4 } }),
    'GET /jobs/2': () => ({ body: { jobId: 2, status: 'QUEUED' } }),
  });
  // when
  const ready = await api.getProject(1);
  const queued = await api.getProject(2);
  // then
  assert.equal(ready.pageCount, 4);
  assert.equal(queued.pageCount, 0);
  assert.ok(!calls.some((c) => c.key === 'GET /jobs/2/info'));
});

test('saveNow: given commands response {applied,snapshotId} / when saveNow / then returns {saved:true,snapshotId}', async () => {
  // given
  const { api, calls } = loadApi({
    'POST /jobs/1/commands': () => ({ body: { jobId: 1, applied: true, snapshotId: 9, touchedPages: [] } }),
  });
  // when
  const r = await api.saveNow(1);
  // then
  assert.deepEqual(plain(r), { saved: true, snapshotId: 9 });
  assert.deepEqual(JSON.parse(calls[0].opts.body), { commands: [], label: '수동 저장' });
});

test('http errors: given 422 without body / when startExport / then message explains the gate, status kept', async () => {
  // given
  const { api } = loadApi({ 'POST /jobs/1/export': () => ({ status: 422, body: '' }) });
  // when / then
  await assert.rejects(api.startExport(1, 'pdf', 'x4'), (e) => e.status === 422 && /검수/.test(e.message));
});

test('http errors: given server message / when request fails / then server message wins', async () => {
  // given
  const { api } = loadApi({ 'POST /jobs/1/export': () => ({ status: 422, body: { message: 'NotoSansKR 폰트가 임베드되지 않았습니다.' } }) });
  // when / then
  await assert.rejects(api.startExport(1, 'pdf', 'x4'), /NotoSansKR/);
});

test('restoreVersion: given empty 200 body / when restore / then {restored:true} without JSON parse error', async () => {
  // given
  const { api } = loadApi({ 'POST /jobs/1/versions/3/restore': () => ({ status: 200, body: '' }) });
  // when / then
  assert.deepEqual(plain(await api.restoreVersion(1, 3)), { restored: true });
});

test('importManuscript: given file / when import / then multipart POST to /manuscript and returns server result', async () => {
  // given
  const { api, calls } = loadApi({
    'POST /jobs/1/manuscript': () => ({ body: { applied: true, snapshotId: 5, pageCount: 20, fileName: 'a.md' } }),
  });
  // when
  const r = await api.importManuscript(1, { name: 'a.md' });
  // then
  assert.equal(r.pageCount, 20);
  assert.equal(calls[0].opts.body.f.manuscript.name, 'a.md');
});

test('estimateManuscript: given markdown / when estimate / then counts headings, quotes and body paragraphs', () => {
  // given
  const { api } = loadApi({});
  const md = '# T\n\n## A\n\n> q\n\nbody one\n\nbody two\n';
  // when
  const e = api.estimateManuscript('a.md', md, md.length);
  // then
  assert.deepEqual(plain(e.rows.map((r) => [r.target, r.count])),
    [['ko-heading1', 1], ['ko-heading2', 1], ['ko-quote', 1], ['ko-body', 2]]);
  assert.equal(e.binary, false);
  assert.ok(e.estPages >= 1);
});

test('estimateManuscript: given docx / when estimate / then binary estimate by size', () => {
  // given
  const { api } = loadApi({});
  // when
  const e = api.estimateManuscript('a.docx', '', 30000);
  // then
  assert.equal(e.binary, true);
  assert.equal(e.estPages, 10);
});
