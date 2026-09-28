'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { MiniElement, dispatchClick } = require('./helpers/mini_dom');
const { memoryStorage } = require('./helpers/browser_script_harness');

const ACCOUNT_MENU_PATH = path.resolve(__dirname, '../../main/resources/static/js/account_menu.js');
const REQUESTS_PATH = path.resolve(__dirname, '../../main/resources/static/js/newstart-requests.js');

/**
 * 질문요청 게시판(설계 §5) 화면 계약.
 *
 * 보는 것 3가지:
 * 1. 인증 판정 소스는 채팅과 동일하게 헤더(account_menu.js)를 재사용한다 — 요청 목록은
 *    로그인 회원에게만 열리고(§7 READ=회원), 비로그인에는 목록 자체가 fetch되지 않는다.
 * 2. 상태·종류 라벨은 서버 enum 8종/2종과 1:1로 대응한다(새 상태가 화면에서 "?"로 보이지 않게).
 * 3. 쓰기 거절(THANKS_MEMBER 미부여 403)은 UI가 임의 문장을 지어내지 않고 서버 errorMessage를
 *    그대로 보여준다 — "숨김으로 처리"가 아니라 근거를 보여주는 쪽을 택했기 때문이다.
 */

// newstart-requests.js는 상세 렌더 끝에 scrollIntoView를 부르고, 폼 제출 성공 시 form.reset()을
// 부른다. MiniElement는 레이아웃/폼 API를 흉내내지 않으므로 최소한만 붙인다.
if (!MiniElement.prototype.scrollIntoView) {
  MiniElement.prototype.scrollIntoView = function scrollIntoView() {};
}

const LIST_IDS = ['ns-req-login', 'ns-req-list', 'ns-req-empty', 'ns-req-more', 'ns-req-status',
  'ns-req-detail', 'ns-req-new', 'ns-req-write', 'ns-req-kind', 'ns-req-title',
  'ns-req-improve-fields', 'ns-req-location', 'ns-req-step', 'ns-req-problem', 'ns-req-proposal',
  'ns-req-submit', 'ns-req-write-err'];

function buildDocument() {
  const root = new MiniElement('root');

  function el(tag, attrs) {
    const node = new MiniElement(tag);
    Object.entries(attrs || {}).forEach(([k, v]) => node.setAttribute(k, v));
    root.appendChild(node);
    return node;
  }

  // 헤더: fragments/_newstart_auth.html의 authControls와 동일한 구조(account_menu.js가 요구).
  const authActions = el('div', { class: 'auth-actions', 'data-auth-actions': '' });
  authActions.hidden = true;
  const accountMenu = el('div', { class: 'account-menu', 'data-account-menu': '' });
  accountMenu.hidden = true;

  const byId = {};
  LIST_IDS.forEach((id) => {
    const tag = id === 'ns-req-write' ? 'details'
      : id === 'ns-req-kind' ? 'select'
        : id === 'ns-req-title' || id === 'ns-req-location' || id === 'ns-req-step' ? 'input'
          : id === 'ns-req-problem' || id === 'ns-req-proposal' ? 'textarea'
            : id === 'ns-req-submit' ? 'button' : 'div';
    const node = el(tag, { id });
    if (id === 'ns-req-login' || id === 'ns-req-new' || id === 'ns-req-detail'
      || id === 'ns-req-more' || id === 'ns-req-improve-fields' || id === 'ns-req-write-err') {
      node.hidden = true;
    }
    byId[id] = node;
  });

  // 실제 questions.html과 같은 감싼(form) + kind 탭 3개.
  const form = el('form', { id: 'ns-req-form' });
  form.reset = function reset() { this._textParts = []; };
  byId['ns-req-form'] = form;
  // 템플릿의 정적 "ns-req-actions"(등록 버튼 감싼) — 취소 버튼 렌더 검증이 이 노드에
  // 섞여 통과하지 않도록 같은 클래스로 미리 심어둔다.
  el('div', { class: 'ns-req-actions' }).appendChild(el('button', { type: 'submit', id: 'ns-req-static-submit' }));
  ['all', 'QUESTION', 'SITE_IMPROVEMENT'].forEach((kind) => {
    el('button', { class: 'ns-req-tab', type: 'button', 'data-ns-req-kind': kind === 'all' ? '' : kind });
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

  return { document, nodes: byId, root };
}

const RECEIVED = {
  id: 7, kind: 'QUESTION', title: '개인회생 순서가 궁금해요', screenLocation: null, aToZStep: null,
  problem: '어디부터 시작하나요?', proposal: null, status: 'RECEIVED', answer: null,
  answeredBy: null, resolvedAt: null, githubRef: null, resolvedVersion: null,
  authorId: 1, author: '공용닉네임', createdAt: '2026-09-27T09:00:00', statusLogs: []
};
const ADMIN_ANSWERED = Object.assign({}, RECEIVED, {
  id: 8, status: 'ANSWERED', answer: '공식 경로를 확인해 답변드립니다.',
  statusLogs: [{ fromStatus: 'RECEIVED', toStatus: 'ANSWERED', actor: '운영자',
    createdAt: '2026-09-27T10:30:00', reason: null }]
});

function loadRequestsPage({ meResponse, listResponse, detailResponses = {}, writeResponse, reviewResponse } = {}) {
  const { document, nodes, root } = buildDocument();
  const storage = memoryStorage({});
  const calls = [];
  const fetchImpl = async (url, options) => {
    calls.push({ url: String(url), method: (options && options.method) || 'GET', body: options && options.body });
    if (String(url).startsWith('/api/auth/me')) {
      if (!meResponse) return { ok: false, status: 401, json: async () => ({}) };
      return { ok: true, status: 200, json: async () => meResponse };
    }
    if (String(url).startsWith('/api/admin/newstart/requests/')) {
      return reviewResponse || { ok: true, status: 200, json: async () => ({ status: 'ok', request: ADMIN_ANSWERED }) };
    }
    if (/^\/api\/newstart\/requests\/\d+$/.test(String(url))) {
      if ((options && options.method) === 'DELETE') {
        return { ok: true, status: 200, json: async () => ({ status: 'ok', errorMessage: null, request: null }) };
      }
      const id = Number(String(url).split('/').pop());
      const row = detailResponses[id] || null;
      return row
        ? { ok: true, status: 200, json: async () => ({ status: 'ok', errorMessage: null, request: row }) }
        : { ok: false, status: 404, json: async () => ({ status: 'error', errorMessage: '요청을 찾을 수 없습니다.', request: null }) };
    }
    if (String(url).startsWith('/api/newstart/requests')) {
      if (options && options.method === 'POST') {
        return writeResponse || { ok: true, status: 200, json: async () => ({ status: 'ok', errorMessage: null, request: RECEIVED }) };
      }
      const payload = listResponse === undefined
        ? { requests: [RECEIVED, ADMIN_ANSWERED], hasMore: false }
        : listResponse;
      return { ok: true, status: 200, json: async () => payload };
    }
    return Promise.reject(new Error('not mocked: ' + url));
  };

  const window = {};
  const context = vm.createContext({
    console, document, window, sessionStorage: storage, fetch: fetchImpl,
    setTimeout, clearTimeout,
    location: { href: 'https://feel2real.co.kr/newstart/questions', pathname: '/newstart/questions', search: '', hash: '' }
  });
  vm.runInContext(fs.readFileSync(ACCOUNT_MENU_PATH, 'utf8'), context, { filename: ACCOUNT_MENU_PATH });
  vm.runInContext(fs.readFileSync(REQUESTS_PATH, 'utf8'), context, { filename: REQUESTS_PATH });

  return { nodes, root, calls, window };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));
const flushAll = async () => { for (let i = 0; i < 8; i += 1) await flush(); };

test('given 비로그인 방문자 when /newstart/questions then 로그인 안내가 보이고 요청 목록 fetch는 발생하지 않는다(§7 READ=회원)', async () => {
  const { nodes, calls } = loadRequestsPage({});
  await flushAll();

  assert.equal(nodes['ns-req-login'].hidden, false, '비로그인인데 로그인 안내가 숨겨졌다');
  assert.equal(nodes['ns-req-list'].hidden, true, '비로그인인데 요청 목록이 보인다');
  assert.equal(nodes['ns-req-new'].hidden, true, '비로그인인데 작성 버튼이 보인다');
  assert.equal(calls.some((c) => c.url.startsWith('/api/newstart/requests')), false,
    '비로그인 상태에서 요청 목록을 불러왔다 — 서버 401에 화면을 맡긴 채 무의미한 호출');
});

test('given 로그인 회원 when 페이지를 열면 then 계정 헤더와 같은 판정 소스를 쓰고 목록이 한국어 라벨로 렌더된다', async () => {
  const { nodes, calls } = loadRequestsPage({ meResponse: { userId: 1, loginId: 'naver-1', role: 'STUDENT' } });
  await flushAll();

  assert.equal(nodes['ns-req-login'].hidden, true);
  assert.equal(nodes['ns-req-new'].hidden, false);
  assert.ok(calls.some((c) => c.url === '/api/newstart/requests?page=0&size=20'),
    '목록 첫 페이지 URL이 기대와 다르다: ' + JSON.stringify(calls.map((c) => c.url)));

  const text = nodes['ns-req-list'].textContent;
  assert.ok(text.includes('[질문·조사 요청]'), 'kind 라벨 미노출: ' + text);
  assert.ok(text.includes('접수'), 'status 라벨 미노출: ' + text);
  assert.ok(text.includes('답변 완료'), 'ANSWERED 라벨 미노출: ' + text);
  assert.ok(text.includes('공용닉네임'), '작성자 표기 미노출: ' + text);
});

test('given 서버 enum when 화면 라벨을 조회하면 then 8 상태/2 종류가 전부 대응한다(미정의 상태 = 영어 원문 노출 방지)', () => {
  const { window } = loadRequestsPage({ meResponse: { userId: 1, loginId: 'naver-1', role: 'STUDENT' } });
  const labels = window.F2RNewStartRequests.STATUS_LABEL;
  const kinds = window.F2RNewStartRequests.KIND_LABEL;

  ['RECEIVED', 'REVIEWING', 'ANSWERED', 'ACCEPTED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'DUPLICATE']
    .forEach((code) => assert.ok(labels[code], 'STATUS_LABEL 누락: ' + code));
  assert.deepEqual(Object.keys(kinds).sort(), ['QUESTION', 'SITE_IMPROVEMENT']);
  // 설계 §5의 4개 톤(대기/진행/종료/ 닫힘)이 모두 매핑돼야 배지 색이 깨지지 않는다.
  const tones = window.F2RNewStartRequests.STATUS_TONE;
  ['is-open', 'is-done', 'is-closed'].forEach((tone) => {
    assert.ok(Object.values(tones).includes(tone), 'STATUS_TONE 누락: ' + tone);
  });
});

test('given THANKS_MEMBER 미부여 회원 when 요청 등록(403) then 서버 안내 문구를 그대로 보여주고 목록은 그대로 유지된다', async () => {
  const { nodes, calls } = loadRequestsPage({
    meResponse: { userId: 1, loginId: 'naver-1', role: 'STUDENT' },
    writeResponse: {
      ok: false, status: 403,
      json: async () => ({ status: 'error', errorMessage: '감사회원 전용 기능입니다. 감사회원 승인을 운영자에게 요청해 주세요.' })
    }
  });
  await flushAll();

  nodes['ns-req-kind'].value = 'QUESTION';
  nodes['ns-req-title'].value = '질문 제목';
  nodes['ns-req-problem'].value = '내용입니다';
  const listeners = nodes['ns-req-form']._listeners.get('submit') || [];
  assert.ok(listeners.length >= 1, '폼에 submit 리스너가 붙지 않았다');
  listeners.forEach((listener) => listener({ preventDefault() {} }));
  await flushAll();

  const posted = calls.find((c) => c.method === 'POST' && c.url === '/api/newstart/requests');
  assert.ok(posted, '작성 POST가 발생하지 않았다: ' + JSON.stringify(calls.map((c) => c.url)));
  const payload = JSON.parse(posted.body);
  assert.equal(payload.kind, 'QUESTION');
  assert.equal(payload.screenLocation, '', 'QUESTION인데 개선 요청 전용 칼럼이 전송됐다');

  assert.equal(nodes['ns-req-write-err'].hidden, false, '403 안내가 사용자에게 보이지 않는다');
  assert.equal(nodes['ns-req-write-err'].textContent,
    '감사회원 전용 기능입니다. 감사회원 승인을 운영자에게 요청해 주세요.');
  // 거절로 화면이 초기화되지 않는다 — 이미 열린 목록/상세는 계속 열람 가능해야 한다(§7 READ=회원).
  assert.ok(nodes['ns-req-list'].textContent.includes('개인회생 순서가 궁금해요'),
    '403 이후 목록이 비었다 — 읽기 권한까지 잃은 화면이 됐다');
});

test('given ADMIN이 상세 열람 when 검토 패널로 저장 then /api/admin 경로로 POST하고 반영 정보를 다시 렌더한다', async () => {
  const detailResponses = { 7: RECEIVED, 8: ADMIN_ANSWERED };
  const { nodes, root, calls } = loadRequestsPage({
    meResponse: { userId: 9, loginId: 'admin', role: 'ADMIN' }, detailResponses
  });
  await flushAll();

  const openButton = root.querySelectorAll('.ns-req-open')[0];
  assert.ok(openButton, '목록에 상세 열기 버튼이 없다');
  dispatchClick(openButton, root);
  await flushAll();

  assert.equal(nodes['ns-req-detail'].hidden, false, '상세가 열리지 않았다');
  assert.ok(root.querySelectorAll('.ns-req-admin').length === 1, 'ADMIN에게 운영자 검토 패널이 렌더되지 않았다');
  // ADMIN에게는 본인 작성 여부와 무관하게 취소 버튼도 함께 보인다.
  assert.ok(root.querySelectorAll('.ns-req-withdraw').length === 1, 'ADMIN에게 취소 버튼이 렌더되지 않았다');

  const select = nodes['ns-req-detail'].querySelectorAll('select')[0];
  assert.ok(select, '검토 상태 셀렉트가 없다');
  select.value = 'ANSWERED';
  const saveButton = nodes['ns-req-detail'].querySelectorAll('.ns-btn-primary')[0];
  dispatchClick(saveButton, root);
  await flushAll();

  const review = calls.find((c) => c.method === 'POST' && c.url === '/api/admin/newstart/requests/7/review');
  assert.ok(review, '검토 POST가 /api/admin 경로로 가지 않았다: ' + JSON.stringify(calls.map((c) => c.url)));
  assert.equal(JSON.parse(review.body).status, 'ANSWERED');
  assert.ok(nodes['ns-req-detail'].textContent.includes('검토 이력'), '이력 렌더 실패');
  assert.ok(nodes['ns-req-detail'].textContent.includes('운영자'), '검토자 표기 실패');
});

test('given 일반 회원이 타인의 상세 when 열람 then 취소 버튼은 서버가 결정하는 권한(UI 표시는 근거일 뿐)이라 보이지 않는다', async () => {
  const { nodes, root } = loadRequestsPage({
    meResponse: { userId: 2, loginId: 'member2', role: 'STUDENT' },
    listResponse: { requests: [RECEIVED], hasMore: false },
    detailResponses: { 7: RECEIVED }
  });
  await flushAll();

  dispatchClick(root.querySelectorAll('.ns-req-open')[0], root);
  await flushAll();

  assert.equal(nodes['ns-req-detail'].hidden, false);
  assert.equal(root.querySelectorAll('.ns-req-withdraw').length, 0,
    '본인 작성자(authorId=1)가 아닌 회원에게 취소 버튼이 보인다');
  assert.equal(root.querySelectorAll('.ns-req-admin').length, 0,
    '일반 회원에게 운영자 검토 패널이 보인다');
});
