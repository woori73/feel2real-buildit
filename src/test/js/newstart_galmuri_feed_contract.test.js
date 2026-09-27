'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { MiniElement, dispatchClick } = require('./helpers/mini_dom');
const { memoryStorage } = require('./helpers/browser_script_harness');

const ACCOUNT_MENU_PATH = path.resolve(__dirname, '../../main/resources/static/js/account_menu.js');
const GALMURI_PATH = path.resolve(__dirname, '../../main/resources/static/js/newstart-galmuri.js');

/**
 * 갈무리 게시판(설계 §6) 화면 계약 — read-only knowledge feed.
 *
 * 핵심 불변조건: 신뢰 라벨(§4)의 색/판정을 프론트가 만들지 않는다. 서버가 내린 `tone`
 * (official/operator/case)만을 클래스로 매핑하므로, 운영자가 라벨을 바꾸면 배지도 함께 간다.
 * 그리고 자유 게시판이 아니므로 쓰기·삭제 UI가 렌더되지 않는다.
 */

if (!MiniElement.prototype.scrollIntoView) {
  MiniElement.prototype.scrollIntoView = function scrollIntoView() {};
}

const IDS = ['ns-gm-login', 'ns-gm-list', 'ns-gm-empty', 'ns-gm-more', 'ns-gm-status', 'ns-gm-detail'];

const CARD = {
  id: 3, title: '채권추심 연락 절차', summary: '연락 빈도 제한을 요청할 수 있습니다',
  category: '추심 대응', label: '[사례 참고]', tone: 'case', authority: '금융감독원',
  sourceDate: '2026-09-01', effectiveTo: null, tags: ['추심', '연락']
};
const OFFICIAL = Object.assign({}, CARD, { id: 4, label: '[공식·현재]', tone: 'official' });

function buildDocument() {
  const root = new MiniElement('root');

  function el(tag, attrs) {
    const node = new MiniElement(tag);
    Object.entries(attrs || {}).forEach(([k, v]) => node.setAttribute(k, v));
    root.appendChild(node);
    return node;
  }

  const authActions = el('div', { class: 'auth-actions', 'data-auth-actions': '' });
  authActions.hidden = true;
  const accountMenu = el('div', { class: 'account-menu', 'data-account-menu': '' });
  accountMenu.hidden = true;

  const byId = {};
  IDS.forEach((id) => {
    const node = el(id === 'ns-gm-more' ? 'button' : id === 'ns-gm-status' ? 'p' : 'div', { id });
    if (id === 'ns-gm-login' || id === 'ns-gm-more' || id === 'ns-gm-detail' || id === 'ns-gm-empty') {
      node.hidden = true;
    }
    byId[id] = node;
  });

  const document = {
    readyState: 'complete',
    head: { appendChild() {} },
    body: root,
    addEventListener() {},
    getElementById(id) { return byId[id] || null; },
    querySelector(sel) { return root.querySelector(sel); },
    querySelectorAll(sel) { return root.querySelectorAll(sel); },
    createElement(tag) { return new MiniElement(tag); }
  };
  return { document, nodes: byId, root };
}

function loadGalmuriPage({ meResponse, feedResponses, detailResponse } = {}) {
  const { document, nodes, root } = buildDocument();
  const storage = memoryStorage({});
  const calls = [];
  const fetchImpl = async (url) => {
    calls.push(String(url));
    if (String(url).startsWith('/api/auth/me')) {
      if (!meResponse) return { ok: false, status: 401, json: async () => ({}) };
      return { ok: true, status: 200, json: async () => meResponse };
    }
    if (/^\/api\/newstart\/galmuri\/\d+$/.test(String(url))) {
      return detailResponse
        ? { ok: true, status: 200, json: async () => ({ status: 'ok', errorMessage: null, item: detailResponse }) }
        : { ok: false, status: 404, json: async () => ({ status: 'error', errorMessage: '갈무리 항목을 찾을 수 없습니다.', item: null }) };
    }
    if (String(url).startsWith('/api/newstart/galmuri')) {
      const page = Number(new URL(String(url), 'https://feel2real.co.kr').searchParams.get('page') || 0);
      const items = page === 0 ? [CARD, OFFICIAL] : [];
      return { ok: true, status: 200, json: async () => ({ items, hasMore: page === 0 }) };
    }
    return Promise.reject(new Error('not mocked: ' + url));
  };

  const window = {};
  const context = vm.createContext({
    console, document, window, sessionStorage: storage, fetch: fetchImpl,
    setTimeout, clearTimeout, URL,
    location: { href: 'https://feel2real.co.kr/newstart/galmuri', pathname: '/newstart/galmuri', search: '', hash: '' }
  });
  vm.runInContext(fs.readFileSync(ACCOUNT_MENU_PATH, 'utf8'), context, { filename: ACCOUNT_MENU_PATH });
  vm.runInContext(fs.readFileSync(GALMURI_PATH, 'utf8'), context, { filename: GALMURI_PATH });
  return { nodes, root, calls };
}

const flushAll = async () => { for (let i = 0; i < 8; i += 1) await new Promise((r) => setImmediate(r)); };

test('given 비로그인 방문자 when /newstart/galmuri then 로그인 안내가 보이고 feed fetch는 발생하지 않는다', async () => {
  const { nodes, calls } = loadGalmuriPage({});
  await flushAll();

  assert.equal(nodes['ns-gm-login'].hidden, false);
  assert.equal(nodes['ns-gm-list'].hidden, true, '비로그인인데 갈무리 목록이 보인다');
  assert.equal(calls.some((u) => u.startsWith('/api/newstart/galmuri')), false,
    '비로그인 상태에서 feed를 불렀다(서버 401에 화면을 맡긴 채 무의미한 호출)');
});

test('given 로그인 회원 when 열람 then 서버 tone대로 라벨 배지가 갈리고 원문 요청이 없다(§6 read-only)', async () => {
  const { nodes, root, calls } = loadGalmuriPage({ meResponse: { userId: 1, loginId: 'naver-1', role: 'STUDENT' } });
  await flushAll();

  assert.ok(calls.some((u) => u === '/api/newstart/galmuri?page=0&size=20'),
    '첫 페이지 URL이 기대와 다르다: ' + JSON.stringify(calls));
  const items = root.querySelectorAll('.ns-gm-item');
  assert.equal(items.length, 2);
  const caseBadge = items[0].querySelector('.ns-gm-label');
  const officialBadge = items[1].querySelector('.ns-gm-label');
  assert.equal(caseBadge.textContent, '[사례 참고]');
  assert.equal(caseBadge.classList.contains('is-case'), true, 'case tone이 is-case로 매핑되지 않았다');
  assert.equal(officialBadge.classList.contains('is-official'), true, 'official tone이 is-official로 매핑되지 않았다');
  // 프론트가 자체 판정하지 않으므로 tone 매핑 외 클래스 조합은 만들지 않는다.
  assert.equal(officialBadge.classList.contains('is-case'), false);
  // 쓰기·삭제 UI가 없다 — 갈무리는 자유 게시판이 아니다.
  assert.equal(root.querySelectorAll('form').length, 0, '갈무리 화면에 작성 폼이 있다(§6 위반)');
  assert.equal(nodes['ns-gm-list'].querySelectorAll('textarea').length, 0);
});

test('given 상세 열기 when 본문 GET then 원출처 링크는 rel="noopener noreferrer"로 새 탭에 열린다', async () => {
  const { nodes, root } = loadGalmuriPage({
    meResponse: { userId: 1, loginId: 'naver-1', role: 'STUDENT' },
    detailResponse: Object.assign({}, CARD, {
      content: '추심 연락은 1일 수회 제한을 요청할 수 있습니다.', effectiveFrom: '2026-01-01',
      sourceUrl: 'https://www.fss.or.kr'
    })
  });
  await flushAll();

  dispatchClick(root.querySelectorAll('.ns-gm-open')[0], root);
  await flushAll();

  assert.equal(nodes['ns-gm-detail'].hidden, false);
  assert.ok(nodes['ns-gm-detail'].textContent.includes('추심 연락은 1일 수회 제한을 요청할 수 있습니다.'),
    '본문이 렌더되지 않았다');
  const link = nodes['ns-gm-detail'].querySelector('a');
  assert.ok(link, '원출처 링크가 없다');
  assert.equal(link.getAttribute('href'), 'https://www.fss.or.kr');
  assert.equal(link.getAttribute('rel'), 'noopener noreferrer', '역링크 회피 속성이 없다');
  assert.equal(link.getAttribute('target'), '_blank');
});

test('given 미승인·만료 항목 id when 상세 then 서버 404 안내문을 상태 영역에 보여주고 상세는 열지 않는다', async () => {
  const { nodes, root } = loadGalmuriPage({
    meResponse: { userId: 1, loginId: 'naver-1', role: 'STUDENT' },
    detailResponse: null
  });
  await flushAll();

  dispatchClick(root.querySelectorAll('.ns-gm-open')[0], root);
  await flushAll();

  assert.equal(nodes['ns-gm-detail'].hidden, true, '404 항목의 상세 패널이 열렸다');
  assert.equal(nodes['ns-gm-status'].textContent, '갈무리 항목을 찾을 수 없습니다.');
});

test('given hasMore when 더 보기 then 다음 페이지가 이어붙고 hasMore가 꺼지면 버튼이 숨는다', async () => {
  const { nodes, root, calls } = loadGalmuriPage({ meResponse: { userId: 1, loginId: 'naver-1', role: 'STUDENT' } });
  await flushAll();

  assert.equal(nodes['ns-gm-more'].hidden, false);
  dispatchClick(nodes['ns-gm-more'], root);
  await flushAll();

  assert.ok(calls.some((u) => u === '/api/newstart/galmuri?page=1&size=20'),
    '두 번째 페이지가 요청되지 않았다: ' + JSON.stringify(calls));
  assert.equal(root.querySelectorAll('.ns-gm-item').length, 2, '기존 항목이 덮어써졌다(append 아님)');
  assert.equal(nodes['ns-gm-more'].hidden, true, 'hasMore=false인데 더 보기 버튼이 남았다');
});
