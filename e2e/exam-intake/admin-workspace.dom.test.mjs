// DOM behaviour tests only; this adapter does not test native layout or PDF rendering.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
const { parseHTML } = await import(process.env.F2R_DOM_MODULE ? pathToFileURL(process.env.F2R_DOM_MODULE).href : 'linkedom');
const root = path.resolve(import.meta.dirname, '../..');
const html = fs.readFileSync(path.join(root, 'src/main/resources/templates/admin/exam_requests.html'), 'utf8');
const source = fs.readFileSync(path.join(root, 'src/main/resources/static/js/exam_admin.js'), 'utf8');
const pause = () => new Promise(resolve => setTimeout(resolve, 5));
const settle = async () => { for (let n = 0; n < 6; n++) await pause(); };
function request(id, state = 'RECEIVED', kind = 'APPLICATION') {
  return { id, state, kind, version: 0, createdAt: '2026-11-11T01:00:00Z', updatedAt: '2026-11-11T01:00:00Z', details: { name: `합성학생${id}`, school: '검증중학교', grade: '중3', contactMethod: 'EMAIL', contact: 'fixture@example.invalid', requirements: '교과서 7~8과, 관계대명사와 분사 중심', scope: '7~8과', termsAccepted: 'true', optionalConsent: 'true' }, offer: { durationDays: 14, deliveriesPerWeek: 2, totalDeliveries: 4, totalQuestions: 160, priceWon: 20000 } };
}
const cleanDocument = { id: 'doc1', name: '학교프린트.png', size: 100, scanState: 'CLEAN', hidden: false };
async function setup(options = {}) {
  const { document, window } = parseHTML(html);
  // Fill only browser APIs absent from linkedom. No application functions are replaced.
  Object.defineProperty(window.HTMLSelectElement.prototype, 'value', { configurable: true, get() { return [...this.options].find(o => o.hasAttribute('selected'))?.getAttribute('value') ?? this.options[0]?.getAttribute('value') ?? ''; }, set(value) { for (const o of this.options) o.toggleAttribute('selected', o.getAttribute('value') === String(value)); } });
  Object.defineProperty(window.HTMLInputElement.prototype, 'checked', { configurable: true, get() { return this.hasAttribute('checked'); }, set(value) { this.toggleAttribute('checked', Boolean(value)); } });
  for (const form of document.querySelectorAll('form')) {
    Object.defineProperty(form, 'elements', { get() { return new Proxy({}, { get: (_, key) => form.querySelector(`[name="${String(key)}"]`) }); } });
    form.reset = () => { for (const e of form.querySelectorAll('input, textarea, select')) { if (e.type === 'checkbox') e.checked = false; else if (e.tagName !== 'SELECT') e.value = ''; } };
  }
  const dialog = document.getElementById('documentViewer');
  Object.defineProperty(dialog, 'open', { get() { return this.hasAttribute('open'); } });
  dialog.showModal = () => dialog.setAttribute('open', '');
  dialog.close = () => { dialog.removeAttribute('open'); dialog.dispatchEvent(new window.Event('close')); };
  for (const e of document.querySelectorAll('input, button, h2')) e.focus = () => {};
  class BrowserFormData extends FormData { constructor(form) { super(); if (form) for (const e of form.querySelectorAll('[name]')) if (!e.disabled && (e.type !== 'checkbox' || e.checked)) this.set(e.name, e.type === 'checkbox' ? 'on' : e.value || ''); } }
  let entries = structuredClone(options.rows || [request('a'), request('b', 'CONFIRMATION_REQUESTED'), request('c', 'PRODUCTION')]);
  const details = new Map(entries.map(r => [r.id, { request: r, documents: [], deliveries: [], actions: [], refundEstimate: { available: true, remaining: 4, estimatedWon: 20000 } }]));
  if (options.details) for (const [id, value] of Object.entries(options.details)) Object.assign(details.get(id), structuredClone(value));
  const calls = [], revoked = [];
  class BrowserURL extends URL { static createObjectURL() { return 'blob:http://fixture.invalid/preview'; } static revokeObjectURL(value) { revoked.push(value); } }
  const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  const fetchMock = async (url, init = {}) => {
    calls.push({ url, ...init, data: init.body instanceof BrowserFormData ? init.body : init.body ? JSON.parse(init.body) : null });
    assert.equal(init.credentials, 'same-origin'); assert.equal(init.headers['X-F2R-Exam'], '1');
    if (options.override) { const result = await options.override(url, init); if (result) return result; }
    if (url.endsWith('/config')) return json({ summary: '2주 · 매주 2회 · 총 4회 · 20,000원' });
    if (url.includes('/requests?page=')) return json(new URL(url, 'http://fixture.invalid').searchParams.get('page') === '0' ? entries : options.secondPage || []);
    if (url.endsWith('/events')) return options.eventsFail ? json({ message: '알림 조회 실패' }, 500) : json(options.events || []);
    if (url.endsWith('/retention')) return json(options.retention || []);
    const m = url.match(/\/requests\/([^/]+)(.*)$/);
    if (m) {
      const [, id, suffix] = m; const value = details.get(id);
      if (!value) return json({ message: '없는 접수' }, 404);
      if (!suffix) { if (options.delays?.[id]) await new Promise(r => setTimeout(r, options.delays[id])); return json(value); }
      if (suffix.includes('/documents/') && init.method === undefined) return new Response(Buffer.from('fixture'), { headers: { 'Content-Type': 'image/png' } });
      const data = init.body ? JSON.parse(init.body) : {};
      if (data.version !== value.request.version) return json({ message: '다른 변경이 있습니다.' }, 409);
      if (suffix === '/state') { value.request.state = data.state; value.request.version++; }
      if (suffix === '/deliveries') {
        if (!data.accessibleAndReviewed || data.ordinal !== value.deliveries.length + 1) return json({ message: '전달 검수와 회차 순서를 확인해 주세요.' }, 409);
        value.deliveries.push({ ordinal_number: data.ordinal, note: data.note, delivered_at: '2026-11-11T02:00:00Z' });
        value.request.version++; value.request.state = data.ordinal === value.request.offer.totalDeliveries ? 'DELIVERED' : 'PRODUCTION';
      }
      if (suffix === '/details') { value.request.details = { ...value.request.details, ...data.details }; value.request.version++; }
      return json(value.request);
    }
    return json({});
  };
  const history = { replaceState(_s, _t, value) { history.url = String(value); } };
  window.innerWidth = options.width || 1440; window.confirm = () => options.confirm !== false;
  const sandbox = { document, window, location: { href: options.location || 'http://fixture.invalid/admin/hub/exam-requests' }, history, URL: BrowserURL, fetch: fetchMock, FormData: BrowserFormData, File, Blob, Intl, console, setTimeout, clearTimeout };
  vm.runInNewContext(source, sandbox, { filename: 'exam_admin.js' }); await settle();
  const $ = id => document.getElementById(id);
  const click = async element => { assert.ok(element, 'click target exists'); assert.ok(!element.disabled, 'click target enabled'); element.dispatchEvent(new window.Event('click', { bubbles: true })); await settle(); };
  const open = id => click(document.querySelector(`[data-request-id="${id}"]`));
  const submit = async form => { for (const e of [...form.querySelectorAll('input,select,textarea')].filter(e => e.required || e.hasAttribute('required'))) { if (e.type === 'checkbox' ? !e.checked : !e.value) return false; } form.dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true })); await settle(); return true; };
  return { $, document, window, calls, details, open, click, submit, history, revoked, json };
}
test('list shows names, schools and current-page work counts; filtering and searching use real fields', async () => {
  const h = await setup(); assert.equal(h.$('countNew').textContent, '1'); assert.equal(h.$('countPayment').textContent, '1'); assert.match(h.$('requests').textContent, /합성학생a/); assert.match(h.$('summaryScope').textContent, /1페이지 3건/);
  h.$('requestSearch').value = 'fixture@example.invalid'; h.$('requestSearch').dispatchEvent(new h.window.Event('input')); assert.equal(h.document.querySelectorAll('.request-row').length, 3);
  await h.click(h.document.querySelector('[data-filter="payment"]')); assert.equal(h.document.querySelectorAll('.request-row').length, 1); assert.match(h.$('requests').textContent, /합성학생b/);
});
test('selecting an intake renders readable details and opaque deep link without exposing raw JSON', async () => {
  const h = await setup(); await h.open('a'); assert.equal(h.$('detailTitle').textContent, '합성학생a'); assert.match(h.$('details').textContent, /학교\/학원검증중학교/); assert.match(h.$('details').textContent, /관계대명사와 분사/); assert.match(h.history.url, /request=a/); assert.match(h.$('stateActions').textContent, /입금 안내/);
});
test('free inquiries have no paid four-round workflow or deposit confirmation', async () => {
  const r = request('free', 'RECEIVED', 'INQUIRY'); r.details.inquiryType = 'FREE_SAMPLE';
  const h = await setup({ rows: [r] }); await h.open('free'); assert.equal(h.$('detailKind').textContent, '클라우드리뷰 무료신청'); assert.equal(h.$('deliveries').children.length, 0); assert.equal(h.$('deliveryForm').hidden, true); assert.match(h.$('stateActions').textContent, /문의 처리 완료/);
});
test('actual deposit confirmation requires explicit checkbox and sends the selected version', async () => {
  const h = await setup(); await h.open('b'); const form = h.$('stateActions').querySelector('form'); assert.ok(form); assert.equal(await h.submit(form), false); assert.equal(h.calls.filter(c => c.url.endsWith('/state')).length, 0);
  form.querySelector('input').checked = true; assert.equal(await h.submit(form), true); const call = h.calls.find(c => c.url.endsWith('/state')); assert.deepEqual(call.data, { version: 0, state: 'CONFIRMED' }); assert.match(h.$('stateActions').textContent, /제작 시작/); assert.equal(h.$('countReady').textContent, '1');
});
test('round delivery is sequential, requires review and updates delivery history and stage', async () => {
  const r = request('paid', 'PRODUCTION'); const h = await setup({ rows: [r], details: { paid: { deliveries: [{ ordinal_number: 1, note: '1회 전달' }, { ordinal_number: 2, note: '2회 전달' }] } } }); await h.open('paid'); const f = h.$('deliveryForm'); assert.equal(f.elements.ordinal.value, '3'); f.elements.note.value = '이메일로 3회차 문제지·해설지 전달'; assert.equal(await h.submit(f), false); f.elements.accessibleAndReviewed.checked = true; await h.submit(f);
  assert.equal(h.calls.find(c => c.url.endsWith('/deliveries')).data.ordinal, 3); assert.equal(h.$('deliveryForm').elements.ordinal.value, '4'); assert.match(h.$('deliveries').textContent, /3회차.*전달 완료/s); assert.match(h.$('progress').textContent, /3\/4회/);
});
test('final round changes to delivered and removes the delivery form', async () => {
  const r = request('final', 'PRODUCTION'); const h = await setup({ rows: [r], details: { final: { deliveries: [1,2,3].map(i => ({ ordinal_number: i, note: `${i}회 전달` })) } } }); await h.open('final'); const f = h.$('deliveryForm'); f.elements.note.value = '최종 문제지·해설지 전달'; f.elements.accessibleAndReviewed.checked = true; await h.submit(f); assert.equal(h.$('deliveryForm').hidden, true); assert.equal(h.$('detailState').textContent, '전달 완료'); assert.match(h.$('stateActions').textContent, /처리 완료/);
});
test('failed list fetch is distinct from an empty list; event failure does not hide requests', async () => {
  const h = await setup({ override: (url) => url.includes('/requests?page=') ? new Response(JSON.stringify({ message: '조회 오류' }), { status: 500 }) : null }); assert.equal(h.$('listError').hidden, false); assert.match(h.$('listError').textContent, /0건을 의미하지/); assert.doesNotMatch(h.$('requests').textContent, /접수 내역이 없습니다/); assert.equal(h.$('countNew').textContent, '—');
  const healthy = await setup({ eventsFail: true }); assert.equal(healthy.document.querySelectorAll('.request-row').length, 3); assert.equal(healthy.$('eventCount').textContent, '조회 실패');
});
test('stale detail response cannot replace the latest selected student', async () => {
  const h = await setup({ delays: { a: 150 } }); const a = h.document.querySelector('[data-request-id="a"]'); a.dispatchEvent(new h.window.Event('click')); await h.open('b'); await new Promise(r => setTimeout(r, 180)); assert.equal(h.$('detailTitle').textContent, '합성학생b'); assert.equal(h.$('stateActions').querySelector('form') !== null, true);
});
test('file preview is available only for clean visible documents and blob is revoked on close', async () => {
  const h = await setup({ details: { a: { documents: [cleanDocument, { ...cleanDocument, id: 'pending', name: '검사대기.pdf', scanState: 'PENDING' }] } } }); await h.open('a'); const cards = h.document.querySelectorAll('.document-card'); assert.match(cards[0].textContent, /자료 보기/); assert.doesNotMatch(cards[1].textContent, /자료 보기|다운로드/); await h.click(cards[0].querySelector('button')); assert.equal(h.$('documentViewer').open, true); assert.equal(h.$('viewerBody').querySelector('img').alt, '학교프린트.png'); await h.click(h.$('closeViewer')); assert.equal(h.revoked.length, 1);
});
test('untrusted requirements are text, not executable markup', async () => {
  const r = request('xss'); r.details.requirements = '<img src=x onerror="window.pwned=true">'; const h = await setup({ rows: [r] }); await h.open('xss'); assert.match(h.$('details').textContent, /<img/); assert.equal(h.$('details').querySelector('img'), null); assert.equal(h.window.pwned, undefined);
});
test('version conflicts leave the intake stage unchanged and show recovery guidance', async () => {
  const h = await setup({ override: (url) => url.endsWith('/state') ? new Response(JSON.stringify({ message: '다른 변경이 있습니다.' }), { status: 409 }) : null }); await h.open('a'); await h.click(h.$('stateActions').querySelector('button')); assert.equal(h.$('detailState').textContent, '신규 접수'); assert.match(h.$('notice').textContent, /다른 변경/); assert.equal(h.$('notice').dataset.error, 'true');
});
test('deep link directly opens the requested intake independently of the list page', async () => {
  const h = await setup({ location: 'http://fixture.invalid/admin/hub/exam-requests?request=b' }); assert.equal(h.$('detailTitle').textContent, '합성학생b');
});
test('pagination clears details and retains truthful page-scoped counts', async () => {
  const h = await setup({ rows: Array.from({ length: 50 }, (_, i) => request(String(i))), secondPage: [request('page2')] }); await h.open('0'); await h.click(h.$('next')); assert.equal(h.$('requestDetail').hidden, true); assert.match(h.$('summaryScope').textContent, /2페이지 1건/); assert.equal(h.$('countNew').textContent, '1'); assert.equal(h.$('next').disabled, true); assert.equal(h.$('previous').disabled, false);
});
test('approved amendments populate current fields and post the selected reviewed action', async () => {
  const h = await setup({ details: { a: { actions: [{ id: 'approved', kind: 'UPDATE_DETAILS', state: 'APPROVED', body: '학교 수정' }] } } }); await h.open('a'); const f = h.$('amendForm'); assert.equal(f.hidden, false); f.elements.school.value = '수정중학교'; await h.submit(f); const call = h.calls.find(c => c.url.endsWith('/details')); assert.equal(call.data.actionId, 'approved'); assert.equal(call.data.details.school, '수정중학교'); assert.match(h.$('details').textContent, /수정중학교/);
});
test('privacy erasure remains explicit and cancelled confirmation does not mutate data', async () => {
  const h = await setup({ rows: [request('done', 'COMPLETE')], confirm: false }); await h.open('done'); assert.equal(h.$('earlyErasureSection').hidden, false); const f = h.$('earlyErasure'); f.elements.purposeComplete.checked = true; f.elements.noLegalHold.checked = true; await h.submit(f); assert.equal(h.calls.filter(c => c.url.endsWith('/erase-learning-data')).length, 0);
});

test('email notifications remain primary; saved secrets are never refilled and test sends require a click', async () => {
  const settings = { version: 7, email: 'fixture@example.invalid', phone: '', naverAccount: 'fixture@example.invalid', sender: '', emailEnabled: true, smsEnabled: false, emailStatus: 'READY', smsStatus: 'DISABLED' };
  const h = await setup({ location: 'http://fixture.invalid/admin/hub/exam-requests#notifications', override: (url) => url.endsWith('/notifications') ? new Response(JSON.stringify(settings)) : null });
  assert.equal(h.$('notifications').open, true); assert.equal(h.$('notificationForm').elements.email.value, settings.email); assert.equal(h.$('notificationForm').elements.smsEnabled.checked, false); assert.equal(h.$('notificationForm').elements.naverPassword.value, ''); assert.equal(h.calls.filter(c => c.url.endsWith('/notifications/test')).length, 0);
  await h.submit(h.$('notificationForm')); const call = h.calls.find(c => c.url.endsWith('/notifications') && c.method === 'PUT'); assert.equal(call.data.version, 7); assert.equal(call.data.smsEnabled, false); assert.equal(call.data.solapiSecret, '');
});
test('notification settings load failure cannot silently overwrite saved settings', async () => {
  const h = await setup({ override: (url) => url.endsWith('/notifications') ? new Response(JSON.stringify({ message: '연결 오류' }), { status: 500 }) : null });
  await h.submit(h.$('notificationForm')); assert.equal(h.calls.filter(c => c.url.endsWith('/notifications') && c.method === 'PUT').length, 0); assert.match(h.$('notificationSettingsStatus').textContent, /조회 실패/);
});
