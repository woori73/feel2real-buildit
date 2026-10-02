'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { parseHtml, MiniElement } = require('./helpers/mini_dom');

/**
 * 비밀번호 관리 화면의 프론트 계약.
 *
 * 서버가 ① 비밀번호 조회 API를 만들지 않고 ② 초기화 응답에 한 번만 실어 보내므로, "한 번만 표시·
 * 재조회 불가"가 화면에서 성립하려면 JS가 값을 어디에도 남기면 안 된다. 이 테스트는 그 화면 쪽
 * 규정을 고정한다:
 * - 임시 비밀번호는 초기화 POST가 성공한 뒤에만 보이는데, 그 전에는 DOM에 값이 없다.
 * - 확인을 취소하면 요청 자체가 나가지 않는다(실수 클릭으로 회원 비밀번호가 죽는 사고 방지).
 * - 값을 지우려고 서버에 다시 물어보지 않는다.
 * - 이 화면이 호출하는 경로는 목록/상세/초기화 세 가지뿐 — 비밀번호를 "가져오는" 호출이 없다.
 * 본인 변경 화면(newstart-password-set.js)은 submit 직전 입력 3개를 비워 다시 표시되지 않게 한다.
 */

const ADMIN_JS = path.resolve(__dirname, '../../main/resources/static/js/admin_newstart_members.js');
const SET_JS = path.resolve(__dirname, '../../main/resources/static/js/newstart-password-set.js');
const ADMIN_TEMPLATE = path.resolve(__dirname, '../../main/resources/templates/admin/newstart_members.html');
const SET_TEMPLATE = path.resolve(__dirname, '../../main/resources/templates/newstart/password_set.html');

const ADMIN_MARKUP = `
  <form id="nm-search-form"><input id="nm-query"></form>
  <div id="nm-msg"></div>
  <table><tbody id="nm-body"></tbody></table>
  <button id="nm-prev">이전</button><span id="nm-page-info"></span><button id="nm-next">다음</button><span id="nm-total"></span>
  <section id="nm-detail" hidden>
    <p id="nm-d-loginId"></p><p id="nm-d-nickname"></p><p id="nm-d-memberType"></p>
    <p id="nm-d-accountStatus"></p><p id="nm-d-joinedAt"></p><p id="nm-d-lastLoginAt"></p>
    <p id="nm-d-rosterLinked"></p><p id="nm-d-lastReset"></p><p id="nm-d-hint"></p>
    <button id="nm-reset-btn">비밀번호 초기화</button>
    <div id="nm-temp" hidden><code id="nm-temp-value"></code><button id="nm-temp-close">닫기</button></div>
  </section>`;

const TEMP_PASSWORD = 'kq7m2xt9pbad';

function indexById(root) {
  const byId = {};
  (function collect(node) {
    node.children.forEach((child) => {
      if (child.id) byId[child.id] = child;
      collect(child);
    });
  })(root);
  return byId;
}

function makeDocument(root, byId) {
  return {
    readyState: 'complete',
    body: root,
    addEventListener() {},
    getElementById(id) { return byId[id] || null; },
    querySelector(sel) { return root.querySelector(sel); },
    querySelectorAll(sel) { return root.querySelectorAll(sel); },
    createElement(tag) { return new MiniElement(tag); }
  };
}

function findAll(node, predicate, out = []) {
  node.children.forEach((child) => {
    if (predicate(child)) out.push(child);
    findAll(child, predicate, out);
  });
  return out;
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

function fire(el, type) {
  const handlers = (el._listeners && el._listeners.get(type)) || [];
  handlers.slice().forEach((h) => h({ preventDefault() {}, target: el }));
}

function loadAdmin({ confirmAnswer = true } = {}) {
  const root = parseHtml(ADMIN_MARKUP);
  const byId = indexById(root);
  const document = makeDocument(root, byId);
  const calls = [];

  const fetchAuth = async (url, init) => {
    const route = String(url).split('?')[0];
    calls.push({ url: String(url), method: (init && init.method) || 'GET' });
    if (route === '/api/admin/newstart/members') {
      return { ok: true, status: 200, json: async () => ({
        query: '', page: 0, size: 20, totalElements: 1, totalPages: 1,
        members: [{
          userId: 77, loginId: 'ns-member-01', cafeNickname: '새출발러',
          memberType: 'THANKS_MEMBER', accountStatus: 'ACTIVE',
          joinedAt: '2026-09-01T10:00:00', lastLoginAt: null
        }]
      }) };
    }
    if (route.endsWith('/password-reset')) {
      return { ok: true, status: 200, json: async () => ({
        userId: 77, loginId: 'ns-member-01', cafeNickname: '새출발러',
        temporaryPassword: TEMP_PASSWORD, resetAt: '2026-10-02T11:00:00'
      }) };
    }
    if (/^\/api\/admin\/newstart\/members\/\d+$/.test(route)) {
      return { ok: true, status: 200, json: async () => ({
        userId: 77, loginId: 'ns-member-01', cafeNickname: '새출발러',
        memberType: 'THANKS_MEMBER', accountStatus: 'RESET_REQUIRED',
        joinedAt: '2026-09-01T10:00:00', lastLoginAt: null, passwordResetRequired: true,
        lastResetAt: '2026-10-02T11:00:00', lastResetBy: 'admin-f2r', thanksRosterLinked: true
      }) };
    }
    return { ok: false, status: 404, json: async () => ({}) };
  };

  const context = vm.createContext({
    console, document, window: { fetchAuth, confirm: () => confirmAnswer }
  });
  vm.runInContext(fs.readFileSync(ADMIN_JS, 'utf8'), context, { filename: ADMIN_JS });
  return { ...byId, calls, root };
}

/** 목록 1행의 [상세]를 눌러 state.userId를 확정한다 — 초기화 버튼의 전제 조건. */
async function openDetail(dom) {
  const [button] = findAll(dom['nm-body'], (n) => n.tagName === 'BUTTON' && n.textContent === '상세');
  assert.ok(button, '목록에 [상세] 버튼이 렌더되지 않았다');
  fire(button, 'click');
  await flush();
  return button;
}

test('given 화면을 열 때 when 부팅 then 목록·상세·초기화 세 경로만 쓰이고 비밀번호를 읽는 호출은 없다', async () => {
  const dom = loadAdmin();
  await flush();

  const known = [
    /^\/api\/admin\/newstart\/members\?/,
    /^\/api\/admin\/newstart\/members\/\d+$/,
    /^\/api\/admin\/newstart\/members\/\d+\/password-reset$/
  ];
  dom.calls.forEach((call) => assert.ok(
    known.some((re) => re.test(call.url)),
    `예상치 못한 경로를 호출했다: ${call.url}`
  ));
  assert.ok(dom.calls.some((c) => c.method === 'GET' && /\?/.test(c.url)), '부팅 시 목록을 조회하지 않는다');
  assert.ok(!dom.calls.some((c) => c.method === 'POST'), '화면을 여는 것만으로 초기화가 실행되면 안 된다');
  assert.equal(dom['nm-temp-value'].textContent, '');
});

test('given 회원을 선택하고 초기화를 확인했을 때 when POST 성공 then 그 자리에서만 임시 값이 보인다', async () => {
  const dom = loadAdmin();
  await flush();
  await openDetail(dom);
  assert.equal(dom['nm-detail'].hidden, false);
  assert.equal(dom['nm-temp-value'].textContent, '', '초기화 전인데 화면에 값이 있다');

  fire(dom['nm-reset-btn'], 'click');
  await flush();

  assert.ok(dom.calls.some((c) => c.method === 'POST' && c.url.endsWith('/77/password-reset')));
  assert.equal(dom['nm-temp'].hidden, false);
  assert.equal(dom['nm-temp-value'].textContent, TEMP_PASSWORD);
  assert.equal(dom['nm-msg'].textContent, '초기화했습니다. 표시된 임시 비밀번호를 회원에게 전달하세요.');
});

test('given 회원을 선택하지 않았을 때 when 초기화 버튼을 눌러도 then 요청이 나가지 않는다', async () => {
  const dom = loadAdmin();
  await flush();
  const before = dom.calls.length;

  fire(dom['nm-reset-btn'], 'click');
  await flush();

  assert.equal(dom.calls.length, before, '대상 없는 초기화 요청이 나갔다');
  assert.equal(dom['nm-msg'].textContent, '먼저 회원 목록에서 [상세]를 눌러 대상을 선택해 주세요.');
});

test('given 확인 대화상자를 취소했을 때 when 초기화 then POST가 없다 — 실수로 회원 비밀번호를 죽일 수 없다', async () => {
  const dom = loadAdmin({ confirmAnswer: false });
  await flush();
  await openDetail(dom);

  fire(dom['nm-reset-btn'], 'click');
  await flush();

  assert.ok(!dom.calls.some((c) => c.method === 'POST'));
  assert.equal(dom['nm-temp'].hidden, true);
  assert.equal(dom['nm-temp-value'].textContent, '');
});

test('given 임시 값이 표시된 뒤 when X로 닫으면 then DOM에서 값이 사라지고 재조회하지 않는다', async () => {
  const dom = loadAdmin();
  await flush();
  await openDetail(dom);
  fire(dom['nm-reset-btn'], 'click');
  await flush();
  assert.equal(dom['nm-temp-value'].textContent, TEMP_PASSWORD);

  const callsBefore = dom.calls.length;
  fire(dom['nm-temp-close'], 'click');
  await flush();

  assert.equal(dom['nm-temp-value'].textContent, '', '표시를 지웠는데 값이 DOM에 남아 있다');
  assert.equal(dom['nm-temp'].hidden, true);
  assert.equal(dom.calls.length, callsBefore, '값을 지우려고 서버에 다시 물어본다(재조회)');
  assert.equal(dom['nm-msg'].textContent, '임시 비밀번호 표시를 지웠습니다. 같은 값을 다시 볼 수 없습니다.');
});

test('given 임시 값이 표시된 뒤 when 검색을 다시 제출하면 then 상세 패널과 표시가 함께 닫힌다', async () => {
  const dom = loadAdmin();
  await flush();
  await openDetail(dom);
  fire(dom['nm-reset-btn'], 'click');
  await flush();

  fire(dom['nm-search-form'], 'submit');
  await flush();

  assert.equal(dom['nm-detail'].hidden, true);
  assert.equal(dom['nm-temp'].hidden, true);
  assert.equal(dom['nm-temp-value'].textContent, '');
});

test('admin 회원 화면 마크업: 상세 패널과 임시 표시는 처음부터 hidden, 초기화 버튼은 상세 안에만 있다', () => {
  const html = fs.readFileSync(ADMIN_TEMPLATE, 'utf8');
  assert.match(html, /<section class="nm-detail" id="nm-detail" hidden>/);
  assert.match(html, /<div class="nm-temp" id="nm-temp" hidden>/);
  const detailStart = html.indexOf('id="nm-detail"');
  const resetStart = html.indexOf('id="nm-reset-btn"');
  assert.ok(detailStart > -1 && resetStart > detailStart,
    '초기화 버튼이 상세 패널 밖에 있으면 대상을 고르기 전에 클릭할 수 있다');
});

// ── 회원 본인 [비밀번호 변경 / 새 비밀번호 설정] ──────────────────────────────

function loadSet({ forced = true, response } = {}) {
  const root = parseHtml(`
    <form id="ns-pw-form">
      <input id="ns-pw-current" type="password">
      <input id="ns-pw-new" type="password" minlength="8">
      <input id="ns-pw-confirm" type="password" minlength="8">
      <button type="submit" data-ns-pw-submit data-change-required="${forced ? 'true' : 'false'}">저장</button>
    </form>
    <p id="ns-pw-msg" hidden></p>`);
  const byId = indexById(root);
  const document = makeDocument(root, byId);
  const calls = [];
  const location = { href: '/newstart/password/set' };

  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init: init || {} });
    return response || { ok: true, status: 200, text: async () => '{"changed":true}' };
  };
  const context = vm.createContext({ console, document, fetch: fetchImpl, window: { location } });
  vm.runInContext(fs.readFileSync(SET_JS, 'utf8'), context, { filename: SET_JS });
  return { ...byId, calls, location };
}

function fill(dom, current, next, confirmValue) {
  dom['ns-pw-current'].value = current;
  dom['ns-pw-new'].value = next;
  dom['ns-pw-confirm'].value = confirmValue;
}

test('given 강제로 진입한 회원 when 저장 then 본문으로 보낸 직후 입력란 3개를 비우고 홈으로 돌아간다', async () => {
  const dom = loadSet({ forced: true });
  fill(dom, TEMP_PASSWORD, 'newpass123', 'newpass123');
  fire(dom['ns-pw-form'], 'submit');
  await flush();

  assert.equal(dom.calls.length, 1);
  assert.equal(dom.calls[0].url, '/api/newstart/me/password');
  assert.deepEqual(JSON.parse(dom.calls[0].init.body), {
    currentPassword: TEMP_PASSWORD, newPassword: 'newpass123', confirmPassword: 'newpass123'
  });
  assert.equal(dom['ns-pw-current'].value, '');
  assert.equal(dom['ns-pw-new'].value, '');
  assert.equal(dom['ns-pw-confirm'].value, '');
  assert.equal(dom.location.href, '/newstart');
});

test('given 자발적 변경 when 저장 성공 then 이동하지 않고 안내만 보인다', async () => {
  const dom = loadSet({ forced: false });
  fill(dom, 'oldpass123', 'newpass123', 'newpass123');
  fire(dom['ns-pw-form'], 'submit');
  await flush();

  assert.equal(dom.location.href, '/newstart/password/set');
  assert.equal(dom['ns-pw-msg'].textContent, '비밀번호가 변경되었습니다.');
  assert.equal(dom['ns-pw-msg'].hidden, false);
});

test('given 서버가 거절할 때 when 저장 then 이유를 화면에 보여주고 값을 되살리지 않는다', async () => {
  const dom = loadSet({
    forced: false,
    response: { ok: false, status: 400, text: async () => '현재 비밀번호가 올바르지 않습니다.' }
  });
  fill(dom, 'wrongpass123', 'newpass123', 'newpass123');
  fire(dom['ns-pw-form'], 'submit');
  await flush();

  assert.equal(dom['ns-pw-msg'].textContent, '현재 비밀번호가 올바르지 않습니다.');
  assert.equal(dom['ns-pw-msg'].getAttribute('data-error'), 'true');
  assert.equal(dom['ns-pw-current'].value, '');
});

test('given 최소 길이·확인 불일치 when 저장 then 요청을 보내지 않는다(서버와 같은 8자)', async () => {
  const dom = loadSet({ forced: false });
  fill(dom, 'oldpass123', 'short12', 'short12');
  fire(dom['ns-pw-form'], 'submit');
  await flush();
  assert.equal(dom.calls.length, 0);
  assert.equal(dom['ns-pw-msg'].textContent, '새 비밀번호는 8자 이상으로 입력해 주세요.');

  fill(dom, 'oldpass123', 'newpass123', 'newpass124');
  fire(dom['ns-pw-form'], 'submit');
  await flush();
  assert.equal(dom.calls.length, 0);
  assert.equal(dom['ns-pw-msg'].textContent, '새 비밀번호가 서로 일치하지 않습니다.');
});

test('본인 화면 마크업: 대상 회원 식별자 필드가 없고 최소 길이 속성은 서버 정책을 따른다', () => {
  const html = fs.readFileSync(SET_TEMPLATE, 'utf8');
  assert.doesNotMatch(html, /name="userId"|id="ns-pw-userId/);
  assert.match(html, /th:attr="minlength=\$\{minPasswordLength\}"/);
  assert.match(html, /id="ns-pw-current"[^>]*type="password"/);
});
