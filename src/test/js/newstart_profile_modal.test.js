'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { MiniElement } = require('./helpers/mini_dom');

const PROFILE_JS_PATH = path.resolve(__dirname, '../../main/resources/static/js/newstart-profile.js');
const CSS_PATH = path.resolve(__dirname, '../../main/resources/static/css/newstart.css');

/**
 * 회귀: "내 프로필" 모달에서 별명을 저장하면 서버 저장은 성공하는데 모달이 계속 열려 있었다.
 *
 * Root cause — CSS 캐스케이드(#350류) 문제가 아니라 순수 JS 로직 누락이었다: production에서
 * .ns-profile-modal[hidden]{display:none !important} 규칙이 정확히 동작함을 실제 페이지에서
 * getComputedStyle로 직접 확인했다(hidden=true → display:none). 그런데 save 버튼 클릭
 * 핸들러의 성공 분기(`if (result.ok) {...}`)에는애초 closeModal() 호출 자체가 없었다 —
 * 성공 메시지만 표시하고 끝났다. 이 테스트는 그 두 가지를 분리해서 증명한다:
 * (1) 실제 스크립트 파일을 그대로 실행해 저장 성공 시 modal.hidden이 true가 되는지
 *     (JS 로직) — pre-fix 코드로 돌리면 실패해야 정상이다.
 * (2) .ns-profile-modal[hidden] 규칙이 newstart.css에 실제로 존재하는지(CSS 계약, #350
 *     회귀 재발 방지) — 별개로 항상 확인한다.
 */

function buildDom() {
  const root = new MiniElement('root');
  function el(tag, attrs) {
    const node = new MiniElement(tag);
    Object.entries(attrs || {}).forEach(([k, v]) => node.setAttribute(k, v));
    root.appendChild(node);
    return node;
  }

  const trigger = el('button', { 'data-ns-profile-trigger': '' });
  const modal = el('div', { 'data-ns-profile-modal': '' });
  modal.hidden = true;
  const closeBtn = el('button', { 'data-ns-profile-close': '' });
  modal.appendChild(closeBtn);
  const input = el('input', { 'data-ns-profile-input': '' });
  modal.appendChild(input);
  const saveBtn = el('button', { 'data-ns-profile-save': '' });
  modal.appendChild(saveBtn);
  const message = el('p', { 'data-ns-profile-message': '' });
  message.hidden = true;
  modal.appendChild(message);

  const document = {
    readyState: 'complete',
    addEventListener() {},
    querySelector(sel) { return root.querySelector(sel); },
    querySelectorAll(sel) { return root.querySelectorAll(sel); }
  };

  return { document, trigger, modal, closeBtn, input, saveBtn, message };
}

function loadProfileModal({ putResponse }) {
  const dom = buildDom();
  const fetchCalls = [];
  const fetchImpl = async (url, init) => {
    fetchCalls.push({ url, init });
    if (String(url).startsWith('/api/newstart/profile/me')) {
      return { ok: true, json: async () => ({ nickname: '기존별명' }) };
    }
    if (String(url).startsWith('/api/newstart/profile/nickname')) {
      return putResponse;
    }
    return { ok: false, json: async () => ({}) };
  };

  const context = vm.createContext({
    console,
    document: dom.document,
    fetch: fetchImpl,
    window: {}
  });
  vm.runInContext(fs.readFileSync(PROFILE_JS_PATH, 'utf8'), context, { filename: PROFILE_JS_PATH });

  return { ...dom, fetchCalls };
}

function click(el) {
  const handlers = el._listeners && el._listeners.get('click');
  (handlers || []).forEach((h) => h({ preventDefault() {}, target: el }));
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

test('given 별명 저장이 성공(PUT 200) when 저장 버튼을 클릭 then 모달이 닫힌다(hidden=true)', async () => {
  const dom = loadProfileModal({
    putResponse: { ok: true, json: async () => ({ nickname: '새출발러73' }) }
  });
  click(dom.trigger); // 열기
  await flush();
  dom.modal.hidden = false; // 열림 상태 흉내(실제 openModal이 이미 false로 만들지만 명시)
  dom.input.value = '새출발러73';

  click(dom.saveBtn);
  await flush();

  assert.equal(dom.modal.hidden, true, '저장 성공 후에도 모달이 열려 있다(회귀)');
  assert.equal(dom.input.value, '새출발러73', '서버가 확정한 값으로 입력이 갱신되지 않음');
});

test('given 별명 저장이 실패(PUT 400) when 저장 버튼을 클릭 then 모달은 열린 채로 남고 오류 메시지를 보여준다', async () => {
  const dom = loadProfileModal({
    putResponse: { ok: false, json: async () => ({ errorMessage: '별명은 2~20자로 입력해 주세요.' }) }
  });
  click(dom.trigger);
  await flush();
  dom.modal.hidden = false;
  dom.input.value = '가';

  click(dom.saveBtn);
  await flush();

  assert.equal(dom.modal.hidden, false, '저장 실패인데 모달이 닫혔다 — 사용자가 오류를 볼 수 없다');
  assert.equal(dom.message.hidden, false, '오류 메시지 영역이 숨겨져 있다');
  assert.equal(dom.message.textContent, '별명은 2~20자로 입력해 주세요.');
});

test('given 저장 요청 자체가 실패(네트워크 오류) when 저장 버튼을 클릭 then 모달은 닫히지 않고 저장 버튼이 다시 활성화된다(중복 클릭 방지 해제)', async () => {
  const dom = buildDom();
  const fetchImpl = async (url) => {
    if (String(url).startsWith('/api/newstart/profile/me')) {
      return { ok: true, json: async () => ({ nickname: '별명' }) };
    }
    throw new Error('network down');
  };
  const context = vm.createContext({ console, document: dom.document, fetch: fetchImpl, window: {} });
  vm.runInContext(fs.readFileSync(PROFILE_JS_PATH, 'utf8'), context, { filename: PROFILE_JS_PATH });

  click(dom.trigger);
  await flush();
  dom.modal.hidden = false;
  click(dom.saveBtn);

  assert.equal(dom.saveBtn.disabled, true, '요청 진행 중에는 중복 클릭 방지를 위해 disabled여야 한다');
  await flush();

  assert.equal(dom.modal.hidden, false, '네트워크 오류인데 모달이 닫혔다');
  assert.equal(dom.saveBtn.disabled, false, '요청 종료 후 저장 버튼이 다시 활성화되지 않음');
});

test('given X 닫기 버튼 when 클릭 then 모달이 닫힌다(기존 동작 보존)', async () => {
  const dom = loadProfileModal({ putResponse: { ok: true, json: async () => ({ nickname: 'x' }) } });
  click(dom.trigger);
  await flush();
  dom.modal.hidden = false;

  click(dom.closeBtn);

  assert.equal(dom.modal.hidden, true);
});

test('.ns-profile-modal[hidden]는 newstart.css에서 display:none !important를 선언한다(#350류 CSS 캐스케이드 회귀 방지)', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
  const rules = [];
  const re = /([^{}]+)\{([^{}]*)\}/g;
  let m;
  while ((m = re.exec(css))) rules.push({ selectors: m[1].trim(), body: m[2].trim() });

  const base = rules.find((r) => r.selectors.split(',').map((s) => s.trim()).includes('.ns-profile-modal'));
  assert.ok(base, '.ns-profile-modal 기본 규칙을 찾지 못함');
  const baseDisplay = /display\s*:\s*([^;]+);/i.exec(base.body)?.[1]?.trim();
  assert.ok(baseDisplay && baseDisplay !== 'none', '.ns-profile-modal는 display:flex 등으로 항상 보이는 것을 전제로 한다');

  const hiddenRule = rules.find((r) => r.selectors.split(',').map((s) => s.trim()).includes('.ns-profile-modal[hidden]'));
  assert.ok(hiddenRule, '.ns-profile-modal[hidden] override가 없다 — hidden 속성이 display:flex를 이기지 못한다');
  const hiddenDisplay = /display\s*:\s*([^;]+);/i.exec(hiddenRule.body)?.[1]?.trim();
  assert.equal(hiddenDisplay, 'none !important');
});
