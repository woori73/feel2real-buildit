'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { MiniElement } = require('./helpers/mini_dom');
const { memoryStorage } = require('./helpers/browser_script_harness');

const ACCOUNT_MENU_PATH = path.resolve(__dirname, '../../main/resources/static/js/account_menu.js');
const NEWSTART_CHAT_PATH = path.resolve(__dirname, '../../main/resources/static/js/newstart-chat.js');

/**
 * 회귀: NAVER 로그인 직후 /newstart 헤더는 로그인 상태(계정 아이콘)를 보여주는데
 * /newstart/chat은 "회원 채팅은 로그인 회원만 사용할 수 있습니다" 로그인 필요 화면을 보여줬다.
 *
 * Root cause — 헤더(account_menu.js)와 채팅(newstart-chat.js)이 같은 /api/auth/me 계약을
 * 쓰면서도 서로 다른 판정 전략을 썼다: 헤더는 sessionStorage 캐시를 우선 신뢰하고
 * (loadCurrentUser의 `if (cached) return cached;`), 채팅은 캐시를 무시한 채 자신만의
 * fetch('/api/auth/me')를 매번 새로 했다. 그 결과 헤더가 캐시로 "로그인됨"을 보여주는
 * 바로 그 순간에도, 채팅의 독립된 fresh 호출은 다른 결과를 반환할 수 있었다.
 *
 * 수정: newstart-chat.js의 loadCurrentUser()가 자체 fetch를 버리고, 이미 이 페이지에
 * favicon fragment로 로드되는 account_menu.js가 노출하는 window.refreshAccountArea()를
 * 그대로 재사용한다 — 헤더와 채팅이 정확히 같은 함수 호출로 같은 답을 얻도록 판정 소스를
 * 하나로 통일했다.
 *
 * 이 테스트는 실제 두 스크립트 파일을 그대로 실행해(우회 DOM 아님) 다음을 증명한다:
 * (1) 헤더가 sessionStorage 캐시로 로그인 상태를 보여주는 것과 같은 조건에서, 채팅도
 *     "로그인 필요" 화면이 아니라 실제 채팅 UI를 보여준다 — 이때 /api/auth/me로의 실제
 *     fetch는 전혀 발생하지 않는다(캐시를 신뢰하기 때문에 이 sameSourceOfTruth를 확인할
 *     수 있다).
 * (2) 캐시가 비어 있어도 쿠키 기반 /api/auth/me가 정상 로그인 사용자를 반환하면 채팅도
 *     정상 인증 UI를 보여준다(첫 로그인 직후 캐시가 없는 정상 경로가 계속 동작함을 보장).
 * (3) 캐시도 없고 /api/auth/me도 미인증이면 채팅은 그대로 로그인 필요 화면을 보여준다
 *     (실제 비로그인 방문자에게 채팅이 새어나가면 안 됨).
 */

function buildDocument() {
  const root = new MiniElement('root');

  function el(tag, attrs) {
    const node = new MiniElement(tag);
    Object.entries(attrs || {}).forEach(([k, v]) => node.setAttribute(k, v));
    root.appendChild(node);
    return node;
  }

  // 헤더: fragments/_newstart_auth.html의 authControls와 동일한 구조.
  const authActions = el('div', { class: 'auth-actions', 'data-auth-actions': '' });
  authActions.hidden = true;
  const accountMenu = el('div', { class: 'account-menu', 'data-account-menu': '' });
  accountMenu.hidden = true;

  // 채팅 본문: newstart/chat.html과 동일한 id들.
  const ids = [
    'ns-chat-list', 'ns-chat-more', 'ns-chat-empty', 'ns-chat-form', 'ns-chat-input',
    'ns-chat-send', 'ns-chat-login', 'ns-chat-login-btn', 'ns-chat-login-alert', 'ns-chat-status'
  ];
  const byId = {};
  ids.forEach((id) => {
    const tag = id === 'ns-chat-form' ? 'form' : id === 'ns-chat-input' ? 'textarea' : 'div';
    const node = el(tag, { id });
    if (id === 'ns-chat-login') node.hidden = true;
    if (id === 'ns-chat-form') node.hidden = true;
    byId[id] = node;
  });

  const document = {
    readyState: 'complete',
    head: { appendChild() {} },
    body: root,
    addEventListener() {},
    getElementById(id) { return byId[id] || root.querySelectorAll('#' + id)[0] || null; },
    querySelector(sel) { return root.querySelector(sel); },
    querySelectorAll(sel) { return root.querySelectorAll(sel); },
    createElement(tag) { return new MiniElement(tag); }
  };

  return { document, authActions, accountMenu, chat: byId };
}

/** account_menu.js와 newstart-chat.js를 같은 vm context에서 순서대로 실행한다(실제 페이지의
 *  <head> defer 로드 순서와 동일 — account_menu.js가 먼저 실행되어 window.refreshAccountArea를
 *  정의한 뒤에야 newstart-chat.js가 그걸 호출할 수 있다). */
function loadNewStartChatPage({ cachedUser, meResponse } = {}) {
  const { document, chat } = buildDocument();
  const storage = memoryStorage(cachedUser ? { currentUser: cachedUser } : {});
  const fetchCalls = [];
  const fetchImpl = async (url) => {
    fetchCalls.push(url);
    if (String(url).startsWith('/api/auth/me')) {
      if (!meResponse) return { ok: false, status: 401, json: async () => ({}) };
      return { ok: true, status: 200, json: async () => meResponse };
    }
    return Promise.reject(new Error('not mocked: ' + url));
  };

  class FakeWebSocket {
    constructor() { this.readyState = 0; }
    close() {}
  }
  FakeWebSocket.OPEN = 1;
  FakeWebSocket.CONNECTING = 0;

  const window = {};
  const context = vm.createContext({
    console,
    document,
    window,
    sessionStorage: storage,
    fetch: fetchImpl,
    WebSocket: FakeWebSocket,
    setTimeout,
    clearTimeout,
    location: { href: 'https://feel2real.co.kr/newstart/chat', pathname: '/newstart/chat', search: '', hash: '', protocol: 'https:', host: 'feel2real.co.kr' }
  });

  vm.runInContext(fs.readFileSync(ACCOUNT_MENU_PATH, 'utf8'), context, { filename: ACCOUNT_MENU_PATH });
  vm.runInContext(fs.readFileSync(NEWSTART_CHAT_PATH, 'utf8'), context, { filename: NEWSTART_CHAT_PATH });

  return { chat, storage, fetchCalls, window };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

test('given 헤더가 sessionStorage 캐시로 로그인 상태를 보여주는 상황 when /newstart/chat을 열면 then 채팅도 로그인 필요 화면이 아니라 인증 UI를 보여준다(fetch 재검증 없이 같은 캐시를 신뢰)', async () => {
  const { chat, fetchCalls } = loadNewStartChatPage({
    cachedUser: { userId: 42, loginId: 'naver-42', role: 'STUDENT' }
  });
  await flush();

  assert.equal(chat['ns-chat-login'].hidden, true, '캐시된 로그인 사용자인데도 로그인 필요 화면이 보인다(회귀)');
  assert.equal(chat['ns-chat-form'].hidden, false, '캐시된 로그인 사용자인데 채팅 입력 폼이 안 보인다');
  assert.equal(fetchCalls.some((u) => String(u).startsWith('/api/auth/me')), false,
    '캐시가 있는데도 /api/auth/me를 다시 불렀다 — 헤더와 다른 판정 소스를 쓰고 있다는 뜻');
});

test('given sessionStorage 캐시는 없지만 쿠키 기반 /api/auth/me가 정상 로그인 사용자를 반환 when /newstart/chat을 열면 then 채팅이 정상 인증 UI를 보여준다(최초 로그인 직후 경로)', async () => {
  const { chat, fetchCalls } = loadNewStartChatPage({
    meResponse: { userId: 7, loginId: 'naver-7', role: 'STUDENT' }
  });
  await flush();

  assert.equal(chat['ns-chat-login'].hidden, true, '쿠키 기반 인증 성공인데 로그인 필요 화면이 보인다');
  assert.equal(chat['ns-chat-form'].hidden, false);
  // account_menu.js 자신의 초기화(refreshAccountArea)도 같은 페이지에서 독립적으로 한 번
  // /api/auth/me를 부르므로(실제 프로덕션과 동일한 동시 호출) 최소 1회 이상이면 된다 —
  // 여기서 확인할 불변조건은 "0번(즉, chat.js가 더 이상 자체 fetch를 안 함에도 캐시 미스 시
  // 여전히 쿠키 기반 인증이 동작한다)"이 아니라는 것뿐이다.
  assert.ok(fetchCalls.filter((u) => String(u).startsWith('/api/auth/me')).length >= 1,
    '캐시가 없으면 /api/auth/me를 최소 한 번은 불러야 한다');
});

test('given 캐시도 없고 /api/auth/me도 미인증(401) when /newstart/chat을 열면 then 로그인 필요 화면을 그대로 보여준다(비로그인 방문자 보호)', async () => {
  const { chat } = loadNewStartChatPage({});
  await flush();

  assert.equal(chat['ns-chat-login'].hidden, false, '비로그인인데 로그인 필요 화면이 숨겨졌다(정보 노출 위험)');
  assert.equal(chat['ns-chat-form'].hidden, true, '비로그인인데 채팅 입력 폼이 보인다');
});
