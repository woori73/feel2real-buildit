const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const FLOW_JS = fs.readFileSync(
  path.join(__dirname, '../../main/resources/static/js/academy_image_edit_flow.js'), 'utf8');
const CHAT_JS = fs.readFileSync(
  path.join(__dirname, '../../main/resources/static/js/academy_builder_ai_chat.js'), 'utf8');

function loadFlow(fetchImpl) {
  const win = { fetch: fetchImpl };
  const ctx = vm.createContext({
    window: win, fetch: fetchImpl, console, document: {}, CustomEvent: function () {},
    confirm: () => true,
  });
  vm.runInContext(FLOW_JS, ctx);
  return win.ImageEditFlow;
}

const json = (body, ok = true, status = 200) => Promise.resolve({
  ok, status, json: () => Promise.resolve(body),
});

test('publishApproved: given approved image / when publish / then posts current revision to working-copy publish', async () => {
  // given
  const calls = [];
  const flow = loadFlow((url, opts) => {
    calls.push({ url, opts });
    if (url.endsWith('/working-copy-revision')) return json({ revision: 7 });
    return json({ revision: 8 });
  });
  // when
  await flow.publishApproved();
  // then
  assert.equal(calls[1].url, '/api/tutor/academy/working-copy/publish');
  assert.deepEqual(JSON.parse(calls[1].opts.body), { revision: 7, force: false });
});

test('publishApproved: given publish conflict / when publish / then surfaces server message', async () => {
  // given
  const flow = loadFlow((url) => url.endsWith('/working-copy-revision')
    ? json({ revision: 1 }) : json({ message: '공개 홈페이지가 다른 곳에서 수정되었습니다.' }, false, 409));
  // when / then
  await assert.rejects(flow.publishApproved(), /다른 곳에서 수정/);
});

test('approveImage(reject): given reject / when approve false / then no revision lookup and posts approved=false', async () => {
  // given
  const calls = [];
  const flow = loadFlow((url, opts) => { calls.push(url); return json({ status: 'REJECTED' }); });
  // when
  await flow.approveImage(3, false, '취소');
  // then
  assert.deepEqual(calls, ['/api/tutor/academy/images/flow/approve']);
});

test('chat wiring: image confirm buttons attach to the real AI message class', () => {
  // given / when / then — 실제 메시지 클래스는 aichat-msg-ai (chat-message.ai 는 존재하지 않는다)
  assert.ok(CHAT_JS.includes("querySelectorAll('.aichat-msg-ai')"));
  assert.ok(!CHAT_JS.includes('.chat-message.ai'));
});
