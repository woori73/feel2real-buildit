'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const SRC = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/testprep.js'), 'utf8');

/** testprep.js를 가짜 window/document로 로드해 window.TP를 돌려준다. */
function load(opts) {
  opts = opts || {};
  const listeners = {};
  const body = { children: [], appendChild(el) { this.children.push(el); } };
  const document = {
    cookie: '',
    body,
    addEventListener(type, fn) { listeners[type] = fn; },
    getElementById(id) { return body.children.find(el => el.id === id) || null; },
    createElement() {
      const el = { id: '', className: '', _html: '', remove() { body.children = body.children.filter(c => c !== el); },
        querySelector() { return { addEventListener() {} }; }, addEventListener() {} };
      Object.defineProperty(el, 'innerHTML', { set(v) { el._html = v; }, get() { return el._html; } });
      return el;
    }
  };
  const window = { addEventListener() {}, TP_SSO_LOGIN: '/login' };
  const location = { pathname: opts.pathname || '/testprep', search: opts.search || '', protocol: 'https:', href: '' };
  const ctx = { window, document, location, fetch: async () => ({ ok: false, status: 401 }), URLSearchParams, encodeURIComponent, console };
  vm.createContext(ctx);
  vm.runInContext(SRC, ctx);
  return { TP: window.TP, document, listeners, location };
}

test('given 비회원, when /testprep 내부 경로를 검사하면, then 허용하고 외부·위험 경로는 거부한다', () => {
  // given
  const { TP } = load();
  // when / then
  assert.strictEqual(TP.safeReturn('/testprep'), '/testprep');
  assert.strictEqual(TP.safeReturn('/testprep/practice'), '/testprep/practice');
  assert.strictEqual(TP.safeReturn('/testprep/run?attempt=3'), '/testprep/run?attempt=3');
  assert.strictEqual(TP.safeReturn('https://evil.com'), null);
  assert.strictEqual(TP.safeReturn('//evil.com'), null);
  assert.strictEqual(TP.safeReturn('/other'), null);
  assert.strictEqual(TP.safeReturn('/testprep/../admin'), null);
  assert.strictEqual(TP.safeReturn('/testprep\\evil'), null);
  assert.strictEqual(TP.safeReturn(null), null);
});

test('given 비회원, when 로그인 안내를 열면, then 지정 문구와 네이버 로그인 버튼이 표시된다', () => {
  // given
  const { TP, document } = load();
  // when
  TP.openLoginModal('/testprep/practice');
  // then
  const modal = document.getElementById('tp-login-modal');
  assert.ok(modal);
  assert.match(modal.innerHTML, /TESTPREP 서비스를 이용하려면 로그인이 필요합니다\./);
  assert.match(modal.innerHTML, /네이버 로그인하고 시작하기/);
  assert.match(modal.innerHTML, /href="\/oauth2\/authorization\/naver"/);
});

test('given 안내가 이미 열려 있을 때, when 다시 열면, then 중복 생성하지 않는다', () => {
  const { TP, document } = load();
  TP.openLoginModal('/testprep/vocab');
  TP.openLoginModal('/testprep/vocab');
  assert.strictEqual(document.body.children.length, 1);
});

test('given 비회원, when 보호 화면 링크를 누르면, then 이동을 막고 로그인 안내를 띄운다', () => {
  // given
  const { TP, document, listeners } = load();
  TP.interceptProtectedLinks();
  let prevented = false;
  const a = { getAttribute: () => '/testprep/scores' };
  const event = { target: { closest: sel => (sel.includes('/testprep/') ? a : null) }, preventDefault() { prevented = true; } };
  // when
  listeners.click(event);
  // then
  assert.strictEqual(prevented, true);
  assert.ok(document.getElementById('tp-login-modal'));
});

test('given 로그인 회원, when 보호 화면 링크를 누르면, then 그대로 이동한다', () => {
  const { TP, document, listeners } = load();
  TP.user = { loginId: 'student' };
  TP.interceptProtectedLinks();
  let prevented = false;
  const a = { getAttribute: () => '/testprep/scores' };
  listeners.click({ target: { closest: () => a }, preventDefault() { prevented = true; } });
  assert.strictEqual(prevented, false);
  assert.strictEqual(document.getElementById('tp-login-modal'), null);
});
