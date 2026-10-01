'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { parseHtml, MiniElement } = require('./helpers/mini_dom');

const JS_PATH = path.resolve(__dirname, '../../main/resources/static/js/newstart-thanks-verify.js');
const HTML_PATH = path.resolve(__dirname, '../../main/resources/templates/newstart/thanks-guide.html');
const ADMIN_JS = path.resolve(__dirname, '../../main/resources/static/js/admin_newstart_thanks_exceptions.js');

/**
 * 감사회원 셀프 인증(회원 화면) 계약: 닉네임 1회 입력만 요구하고(추가 입력 없음), 판정은 서버 결과만 보여준다.
 * ADMIN 에게는 인증 섹션을 보여주지 않는다. 값은 textContent 로만 그린다.
 */

const MARKUP = '<section id="ns-thanks-verify"><div id="ns-thanks-verify-body"></div></section>';

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
  return { section: byId['ns-thanks-verify'], body: byId['ns-thanks-verify-body'], calls };
}
const flush = async () => { for (let i = 0; i < 15; i += 1) await new Promise((r) => setImmediate(r)); };
const click = (el) => (el._listeners.get('click') || []).forEach((l) => l({}));
const STATUS = 'GET /api/newstart/thanks-verification/status';
const VERIFY = 'POST /api/newstart/thanks-verification';

test('비로그인(401)이면 네이버 로그인만 안내하고 인증 요청을 보내지 않는다', async () => {
  const { body, calls } = load({ [STATUS]: () => ({ status: 401, json: {} }) });
  await flush();
  assert.ok(body.textContent.includes('네이버로 로그인'));
  assert.equal(body.querySelector('a').getAttribute('href'), '/oauth2/authorization/naver');
  assert.deepEqual(calls, [STATUS.replace('GET ', 'GET ')]);
});

test('ADMIN이면 인증 섹션을 숨기고 입력·인증코드·roster 요구를 하지 않는다', async () => {
  const { section, body, calls } = load({ [STATUS]: () => ({ status: 200, json: { state: 'ADMIN', message: '운영자 계정은 …' } }) });
  await flush();
  assert.equal(section.style.display, 'none');
  assert.equal(body.querySelector('input'), null, 'ADMIN에게 닉네임 입력창이 보였다');
  assert.ok(!body.textContent.includes('인증코드'));
  assert.deepEqual(calls, [STATUS]);
});

test('미인증 회원은 카페 닉네임 입력창 하나만 본다(가입일·등급변경일 등 추가 입력 없음)', async () => {
  const { body } = load({ [STATUS]: () => ({ status: 200, json: { state: 'NONE' } }) });
  await flush();
  assert.equal(body.querySelectorAll('input').length, 1);
  assert.ok(!/가입일|등급변경|인증코드|쪽지/.test(body.textContent), '닉네임 외 추가 입력/코드를 요구한다');
});

test('닉네임 1회 입력 → 서버가 APPROVED면 확인 문구와 지피지기 링크, 요청 본문은 닉네임뿐', async () => {
  const { body, calls } = load({
    [STATUS]: () => ({ status: 200, json: { state: 'NONE' } }),
    [VERIFY]: () => ({ status: 200, json: { state: 'APPROVED', message: '감사회원으로 확인되었습니다.' } })
  });
  await flush();
  body.querySelector('input').value = '카페닉';
  click(body.querySelector('button'));
  await flush();
  assert.ok(calls.includes(VERIFY + ' {"nickname":"카페닉"}'), JSON.stringify(calls));
  assert.ok(body.textContent.includes('감사회원으로 확인되었습니다.'));
  assert.equal(body.querySelector('a').getAttribute('href'), '/newstart#ns-ai');
});

test('FAILED면 입력창을 다시 보여주고(재시도), EXCEPTION이면 "운영자 확인 중"만 보여준다', async () => {
  let n = 0;
  const { body } = load({
    [STATUS]: () => ({ status: 200, json: { state: 'NONE' } }),
    [VERIFY]: () => (++n === 1
      ? { status: 200, json: { state: 'FAILED', message: '확인하지 못했습니다.' } }
      : { status: 200, json: { state: 'EXCEPTION', message: '운영자에게 전달했습니다.' } })
  });
  await flush();
  body.querySelector('input').value = '틀린닉';
  click(body.querySelector('button'));
  await flush();
  assert.ok(body.textContent.includes('확인하지 못했습니다.'));
  assert.equal(body.querySelectorAll('input').length, 1, '실패 후 재입력 창이 없다');
  body.querySelector('input').value = '다시';
  click(body.querySelector('button'));
  await flush();
  assert.ok(body.textContent.includes('운영자 확인 중'));
  assert.equal(body.querySelector('input'), null);
});

test('마크업/스크립트: thanks-guide 섹션 존재, innerHTML 미사용, 운영자 예외함 화면도 textContent만 사용', () => {
  const html = fs.readFileSync(HTML_PATH, 'utf8');
  assert.ok(html.includes('id="ns-thanks-verify"'));
  assert.ok(html.includes('/js/newstart-thanks-verify.js'));
  assert.ok(!/innerHTML/.test(fs.readFileSync(JS_PATH, 'utf8')));
  assert.ok(!/innerHTML/.test(fs.readFileSync(ADMIN_JS, 'utf8')));
});
