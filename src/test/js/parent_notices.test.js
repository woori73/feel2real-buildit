const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/parent_notices.js'), 'utf8');
const wait = () => new Promise(resolve => setImmediate(resolve));

async function setup(key, search) {
  const ids = new Map();
  const posted = [];
  const shared = [];
  const clipboard = [];
  const node = () => ({
    value: '', textContent: '', hidden: true, checked: false,
    children: [], events: {},
    addEventListener(event, fn) { this.events[event] = fn; },
    append(...nodes) { this.children.push(...nodes); },
    appendChild(child) { this.append(child); },
    replaceChildren() { this.children = []; },
    focus() {}, select() {}
  });
  const get = id => {
    if (!ids.has(id)) ids.set(id, node());
    return ids.get(id);
  };
  get('pnKind').value = 'NOTICE';
  const head = node();
  const document = {
    head, getElementById: get, createElement: node,
    addEventListener(event, fn) { this[event] = fn; }
  };
  vm.runInNewContext(source, {
    window: { Kakao: { init(key) { assert.equal(key, 'public-js-key'); },
      Share: { sendDefault(message) { shared.push(message); } } } },
    document,
    location: { search: search || '' },
    URLSearchParams,
    navigator: { clipboard: { async writeText(value) { clipboard.push(value); } },
      async share(value) { shared.push(value); } },
    crypto: { randomUUID: () => 'd1c86876-1c4b-4cec-96ac-2a3f4a23fbd4' },
    fetchAuth: async (_, opts) => {
      if (opts.method === 'POST') posted.push(JSON.parse(opts.body));
      return { ok: true, json: async () => ({ javascriptKey: key, drafts: [{
        id: '6f1c2b3a-0000-4000-8000-000000000001', kind: 'NOTICE', source: 'MCP', title: '예시 공지', body: '12월은 정상 수업입니다.',
        createdAt: '2026-11-18T00:00:00Z'
      }] }) };
    }
  });
  document.DOMContentLoaded();
  await wait();
  return { get, head, posted, shared, clipboard };
}

test('greeting draft from MCP becomes editable and creates an idempotent dashboard save', async () => {
  const { get, posted } = await setup('');
  const draftButton = get('pnDrafts').children[0].children[0];
  draftButton.events.click();
  assert.equal(get('pnPreview').textContent, '12월은 정상 수업입니다.');
  for (let i = 0; i < 2; i++) await get('pnForm').events.submit({ preventDefault() {} });
  assert.deepEqual(posted[0], posted[1]);
  assert.equal(posted[0].body, '12월은 정상 수업입니다.');
});

test('sharing a promotion requires owner confirmation and the Kakao picker gets only the agreed text', async () => {
  const { get, shared, head } = await setup('public-js-key');
  assert.match(head.children[0].integrity, /^sha384-[A-Za-z0-9+/]{64}$/);
  head.children[0].onload();
  get('pnKind').value = 'PROMOTION';
  get('pnKind').events.change();
  get('pnBody').value = 'F2R 겨울특강 안내';
  get('pnBody').events.input();
  get('pnKakao').events.click();
  assert.equal(shared.length, 0);
  get('pnConsent').checked = true;
  get('pnConsent').events.change();
  get('pnKakao').events.click();
  assert.equal(shared[0].text, '(광고) F2R 겨울특강 안내');
  assert.equal(shared[0].link.webUrl, 'https://feel2real.co.kr/');
  assert.equal(Object.hasOwn(shared[0], 'receiver_uuids'), false);
});

test('unconfigured SDK still permits system sharing and a draft-only MCP prompt', async () => {
  const { get, head, clipboard, shared } = await setup('');
  assert.equal(head.children.length, 0);
  assert.equal(get('pnKakao').hidden, true);
  get('pnBrief').value = '월요일 휴강 안내';
  get('pnCopyPrompt').events.click();
  await wait();
  assert.match(clipboard[0], /save_parent_notice_draft/);
  get('pnBody').value = '월요일 휴강입니다.';
  get('pnBody').events.input();
  await get('pnNative').events.click();
  assert.equal(shared[0].text, '월요일 휴강입니다.');
});

test('AI가 준 ?draft= 링크로 열면 그 초안이 바로 편집 칸에 뜨고 공유 안내가 나온다(보내기는 사람이)', async () => {
  // given & when
  const { get, shared } = await setup('', '?draft=6f1c2b3a-0000-4000-8000-000000000001');

  // then
  assert.equal(get('pnBody').value, '12월은 정상 수업입니다.');
  assert.match(get('pnStatus').textContent, /공유 버튼/);
  assert.equal(shared.length, 0);
});

test('?draft= 값이 이상하거나 없는 초안이면 아무것도 띄우지 않는다', async () => {
  // given & when
  const bad = await setup('', '?draft=../../x');
  const missing = await setup('', '?draft=00000000-0000-4000-8000-000000000000');

  // then
  assert.equal(bad.get('pnBody').value, '');
  assert.equal(missing.get('pnBody').value, '');
});
