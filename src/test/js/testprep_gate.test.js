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
  const body = { children: [], appendChild(el) { this.children.push(el); },
    hasAttribute(name) { return !!opts.app && name === 'data-tp-app'; } };
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

test('given 일반 회원과 전 세트 이용자, when 목록의 시작 단추를 그리면, then 일반 회원에게는 첫 세트만 Start이고 나머지는 재원생 전용 표시다', () => {
  // given
  const { TP } = load();
  const exam = { examCode: 'TP-TOEIC-RC-01' };
  // when & then — 잠김 판정
  assert.strictEqual(TP.isLockedSet(0, false), false);
  assert.strictEqual(TP.isLockedSet(1, false), true);
  assert.strictEqual(TP.isLockedSet(3, true), false);
  // when & then — 단추
  assert.match(TP.startButtonHtml(exam, 0, false), /<button class="btn btn-primary" data-start="TP-TOEIC-RC-01">Start<\/button>/);
  const locked = TP.startButtonHtml(exam, 1, false);
  assert.match(locked, /btn-locked/);
  assert.match(locked, /재원생 전용/);
  assert.doesNotMatch(locked, /data-start/);
  assert.match(TP.startButtonHtml(exam, 1, true), /data-start="TP-TOEIC-RC-01"/);
});

test('given 시험 종류, when 위 메뉴 선택 항목을 고르면, then TOEIC·어휘·SAT는 각 목록, 그 밖(iBT)은 iBT 모의고사이고 선택 표시는 한 항목에만 붙는다', () => {
  // given
  const { TP } = load();
  const make = href => { const cls = new Set(href === '/testprep/practice' ? ['active'] : []);
    return { getAttribute: () => href, classList: { add: c => cls.add(c), remove: c => cls.delete(c), has: c => cls.has(c) } }; };
  const links = ['/testprep', '/testprep/practice', '/testprep/vocab', '/testprep/sat', '/testprep/toeic'].map(make);
  const doc = { querySelectorAll: () => links };
  // when & then
  assert.strictEqual(TP.navHref('TOEIC'), '/testprep/toeic');
  assert.strictEqual(TP.navHref('VOCAB'), '/testprep/vocab');
  assert.strictEqual(TP.navHref('SAT'), '/testprep/sat');
  assert.strictEqual(TP.navHref('PRACTICE'), '/testprep/practice');
  assert.strictEqual(TP.navHref(undefined), '/testprep/practice');
  TP.markNav(doc, TP.navHref('TOEIC'));
  assert.deepStrictEqual(links.map(a => a.classList.has('active')), [false, false, false, false, true]);
});

test('given 재원생 앱에서 연 TOEIC·SAT 화면(data-tp-app) when 주소를 만들면 then 응시 API·응시·해설·돌아갈 곳이 모두 앱 안으로 간다', () => {
  // given
  const { TP } = load({ app: true, pathname: '/naesintest/app/tp-run' });
  // when / then
  assert.strictEqual(TP.isApp(), true);
  assert.strictEqual(TP.attemptsApi(), '/api/naesintest/app/attempts');
  assert.strictEqual(TP.runUrl('TOEIC', 5), '/naesintest/app/tp-run?attempt=5');
  assert.strictEqual(TP.runUrl('SAT', 5), '/naesintest/app/sat-run?attempt=5');
  assert.strictEqual(TP.reviewUrl(5), '/naesintest/app/tp-review?attempt=5');
  assert.strictEqual(TP.homeUrl('/testprep/toeic'), '/naesintest/app');
});

test('given 일반 TestPrep 화면 when 주소를 만들면 then 기존 주소 그대로(회원 API·/testprep 화면)', () => {
  // given
  const { TP } = load();
  // when / then
  assert.strictEqual(TP.isApp(), false);
  assert.strictEqual(TP.attemptsApi(), '/api/testprep/attempts');
  assert.strictEqual(TP.runUrl('TOEIC', 5), '/testprep/run?attempt=5');
  assert.strictEqual(TP.runUrl('SAT', 5), '/testprep/sat/run?attempt=5');
  assert.strictEqual(TP.reviewUrl(5), '/testprep/review?attempt=5');
  assert.strictEqual(TP.homeUrl('/testprep/toeic'), '/testprep/toeic');
});
