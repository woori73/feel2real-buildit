const assert = require('node:assert/strict');
const test = require('node:test');
const Adopt = require('../../main/resources/static/js/academy_adopt.js');

/** 필요한 만큼만 흉내 낸 DOM. */
function makeDoc() {
  const mk = (tag) => ({
    tag, className: '', textContent: '', children: [], listeners: {}, hidden: false, disabled: false,
    checked: false, type: '', id: '',
    appendChild(c) { this.children.push(c); return c; },
    addEventListener(t, f) { (this.listeners[t] = this.listeners[t] || []).push(f); },
    setAttribute() {}
  });
  const doc = { createElement: mk, createTextNode: (t) => ({ text: t }), body: mk('body') };
  return doc;
}
const find = (node, id) => {
  if (node.id === id) return node;
  for (const c of node.children || []) { const f = find(c, id); if (f) return f; }
  return null;
};
const fire = (el, type) => (el.listeners[type] || []).forEach((f) => f({}));
const flush = () => new Promise((r) => setImmediate(r));
const res = (ok, body) => Promise.resolve({ ok, json: () => Promise.resolve(body) });

function setup(modeRes, adoptRes) {
  const doc = makeDoc();
  const mount = doc.createElement('div');
  const calls = [];
  const nav = [];
  const fetchFn = (url, opts) => {
    calls.push({ url, opts });
    return url.endsWith('/mode') ? modeRes : adoptRes;
  };
  const shown = Adopt.init({ document: doc, fetch: fetchFn, navigate: (u) => nav.push(u), mount });
  return { doc, mount, calls, nav, shown };
}

test('given 모드 조회가 403(스위치 꺼짐) when 초기화하면 then 버튼을 붙이지 않는다', async () => {
  // given / when
  const s = setup(res(false, {}), null);

  // then
  assert.equal(await s.shown, false);
  assert.equal(s.mount.children.length, 0);
});

test('given canAdopt=false(AI 제작 학원) when 초기화하면 then 버튼을 붙이지 않는다', async () => {
  // given / when
  const s = setup(res(true, { mode: 'ASSISTANT', canAdopt: false }), null);

  // then
  assert.equal(await s.shown, false);
  assert.equal(s.mount.children.length, 0);
});

test('given canAdopt=true when 초기화하면 then "AI로 이어서 만들기" 버튼이 붙고 아직 전환 요청은 없다', async () => {
  // given / when
  const s = setup(res(true, { mode: 'CLASSIC', canAdopt: true }), null);

  // then
  assert.equal(await s.shown, true);
  assert.equal(s.mount.children.length, 1);
  assert.equal(s.mount.children[0].textContent, 'AI로 이어서 만들기');
  assert.equal(s.calls.filter((c) => c.url.endsWith('/adopt')).length, 0);
});

test('given 확인 창 when 확인 체크 없이 전환 버튼을 누르면 then 요청을 보내지 않는다', async () => {
  // given
  const s = setup(res(true, { mode: 'CLASSIC', canAdopt: true }), res(true, { adopted: true }));
  await s.shown;
  fire(s.mount.children[0], 'click');
  const ok = find(s.doc.body, 'adoptOk');
  assert.equal(ok.disabled, true);

  // when
  fire(ok, 'click');
  await flush();

  // then
  assert.equal(s.calls.filter((c) => c.url.endsWith('/adopt')).length, 0);
  assert.deepEqual(s.nav, []);
});

test('given 확인 체크 when 전환 버튼을 누르면 then confirm:true로 POST하고 성공하면 /builder로 이동한다', async () => {
  // given
  const s = setup(res(true, { mode: 'CLASSIC', canAdopt: true }), res(true, { adopted: true, versionId: 5 }));
  await s.shown;
  fire(s.mount.children[0], 'click');
  const check = find(s.doc.body, 'adoptCheck');
  const ok = find(s.doc.body, 'adoptOk');
  check.checked = true; fire(check, 'change');
  assert.equal(ok.disabled, false);

  // when
  fire(ok, 'click');
  await flush();

  // then
  const post = s.calls.find((c) => c.url.endsWith('/adopt'));
  assert.equal(post.opts.method, 'POST');
  assert.deepEqual(JSON.parse(post.opts.body), { confirm: true });
  assert.deepEqual(s.nav, ['/builder']);
});

test('given 서버가 전환을 거절 when 확인하면 then 이동하지 않고 홈페이지는 그대로라고 안내한다', async () => {
  // given
  const s = setup(res(true, { mode: 'CLASSIC', canAdopt: true }), res(false, { message: '전환할 수 없어요.' }));
  await s.shown;
  fire(s.mount.children[0], 'click');
  const check = find(s.doc.body, 'adoptCheck');
  check.checked = true; fire(check, 'change');

  // when
  fire(find(s.doc.body, 'adoptOk'), 'click');
  await flush();

  // then
  assert.deepEqual(s.nav, []);
  assert.match(find(s.doc.body, 'adoptMsg').textContent, /전환할 수 없어요\. 홈페이지는 그대로예요/);
});

test('given 취소 when 취소를 누르면 then 창이 닫히고 요청이 없다', async () => {
  // given
  const s = setup(res(true, { mode: 'CLASSIC', canAdopt: true }), null);
  await s.shown;
  fire(s.mount.children[0], 'click');
  const modal = find(s.doc.body, 'adoptCancel');

  // when
  fire(modal, 'click');

  // then
  assert.equal(s.calls.filter((c) => c.url.endsWith('/adopt')).length, 0);
});
