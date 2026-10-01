'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { parseHtml, MiniElement } = require('./helpers/mini_dom');

const JS_PATH = path.resolve(__dirname, '../../main/resources/static/js/newstart-thanks-claim.js');
const HTML_PATH = path.resolve(__dirname, '../../main/resources/templates/newstart/thanks-guide.html');

/**
 * 감사회원 본인확인(회원 화면) 계약: 어떤 화면 경로도 권한을 직접 부여하지 않는다 — 요청(PENDING)만 만들고,
 * 서버 응답이 APPROVED일 때만 "확인되었습니다"와 지피지기 링크를 보여준다. 값은 textContent로만 그린다.
 */

const MARKUP = '<section id="ns-thanks-claim"><div id="ns-thanks-claim-body"></div></section>';

function load(handlers) {
  const root = parseHtml(MARKUP);
  const byId = {};
  const collect = (n) => n.children.forEach((c) => { if (c.id) byId[c.id] = c; collect(c); });
  collect(root);
  const document = {
    getElementById: (id) => byId[id] || null,
    createElement: (tag) => { const el = new MiniElement(tag); el.style = {}; return el; }
  };
  const calls = [];
  const fetchImpl = (url, init) => {
    const method = (init && init.method) || 'GET';
    calls.push(method + ' ' + url + (init && init.body ? ' ' + init.body : ''));
    const h = handlers[method + ' ' + url];
    const res = h ? h(init) : { status: 404, json: {} };
    return Promise.resolve({ status: res.status, json: async () => res.json });
  };
  const context = vm.createContext({ console, document, fetch: fetchImpl, window: {} });
  vm.runInContext(fs.readFileSync(JS_PATH, 'utf8'), context, { filename: JS_PATH });
  return { body: byId['ns-thanks-claim-body'], calls };
}
const flush = async () => { for (let i = 0; i < 15; i += 1) await new Promise((r) => setImmediate(r)); };
const click = (el) => (el._listeners.get('click') || []).forEach((l) => l({}));
const buttons = (body) => body.querySelectorAll('button');

test('비로그인(401)이면 네이버 로그인만 안내하고 어떤 부여 요청도 보내지 않는다', async () => {
  const { body, calls } = load({ 'GET /api/newstart/thanks-claim/status': () => ({ status: 401, json: {} }) });
  await flush();
  assert.ok(body.textContent.includes('네이버로 로그인'));
  assert.equal(body.querySelector('a').getAttribute('href'), '/oauth2/authorization/naver');
  assert.deepEqual(calls, ['GET /api/newstart/thanks-claim/status']);
});

test('PENDING이면 인증코드와 "운영자 확인 대기"를 보여주고, APPROVED 문구는 보여주지 않는다', async () => {
  const { body } = load({
    'GET /api/newstart/thanks-claim/status': () => ({ status: 200, json: {
      state: 'PENDING', claimId: 5, rosterNickname: '카페닉', maskedId: 'ssgw****', verifyCode: 'ABCD2345' } })
  });
  await flush();
  assert.ok(body.textContent.includes('운영자 확인 대기 중'));
  assert.ok(body.textContent.includes('ABCD2345'));
  assert.ok(!body.textContent.includes('감사회원으로 확인되었습니다'));
});

test('APPROVED일 때만 확인 문구와 지피지기 링크가 나온다', async () => {
  const { body } = load({
    'GET /api/newstart/thanks-claim/status': () => ({ status: 200, json: { state: 'APPROVED', message: '감사회원으로 확인되었습니다.' } })
  });
  await flush();
  assert.ok(body.textContent.includes('감사회원으로 확인되었습니다.'));
  assert.equal(body.querySelector('a').getAttribute('href'), '/newstart#ns-ai');
});

test('닉네임 입력 → 후보 선택 → 요청(POST)만 보내며, 선택 전에는 요청하지 않는다', async () => {
  const { body, calls } = load({
    'GET /api/newstart/thanks-claim/status': () => ({ status: 200, json: { state: 'NONE' } }),
    'POST /api/newstart/thanks-claim/candidates': () => ({ status: 200, json: { status: 'ok', candidates: [{ rosterId: 11, nickname: '카페닉', maskedId: 'ssgw****' }] } }),
    'POST /api/newstart/thanks-claim': () => ({ status: 200, json: { status: 'ok', claim: { state: 'PENDING', verifyCode: 'ZZZZ2222', rosterNickname: '카페닉', maskedId: 'ssgw****' } } })
  });
  await flush();
  body.querySelector('input').value = '카페닉';
  click(buttons(body)[0]);                       // 후보 찾기
  await flush();
  assert.ok(calls.some((c) => c.startsWith('POST /api/newstart/thanks-claim/candidates')));
  assert.ok(!calls.some((c) => c === 'POST /api/newstart/thanks-claim' || c.startsWith('POST /api/newstart/thanks-claim {')),
    '후보를 고르기 전에 요청이 전송됐다');
  const pick = buttons(body).find((b) => b.textContent.includes('이 닉네임이 맞습니다'));
  click(pick);
  await flush();
  assert.ok(calls.some((c) => c === 'POST /api/newstart/thanks-claim {"rosterId":11}'));
  assert.ok(body.textContent.includes('ZZZZ2222'));
});

test('화면 마크업: thanks-guide에 본인확인 섹션이 있고, 스크립트는 innerHTML을 쓰지 않는다', () => {
  const html = fs.readFileSync(HTML_PATH, 'utf8');
  assert.ok(html.includes('id="ns-thanks-claim"'));
  assert.ok(html.includes('/js/newstart-thanks-claim.js'));
  const js = fs.readFileSync(JS_PATH, 'utf8');
  assert.ok(!/innerHTML/.test(js), '본인확인 스크립트가 innerHTML을 사용한다');
});
