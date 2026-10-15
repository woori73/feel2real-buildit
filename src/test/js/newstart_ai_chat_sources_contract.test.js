'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { parseHtml, MiniElement } = require('./helpers/mini_dom');
const { memoryStorage } = require('./helpers/browser_script_harness');

const NEWSTART_PATH = path.resolve(__dirname, '../../main/resources/static/js/newstart.js');

/**
 * 지피지기 AI 답변의 근거/출처 렌더 계약(newstart.js bindAiChat → appendSources).
 *
 * 2026-09-29 요구사항 3가지:
 * 1. 백엔드가 내려주는 `sources[{kind,title,url}]`를 답변 아래 실제로 보여준다(이전엔 폐기했다).
 * 2. 내부 표기·provider·model·debug 정보는 화면에 나오지 않는다. kind는 사용자 언어로 바꿔 쓴다.
 * 3. 링크는 http/https만 만들고, 역링크 회피(rel=noopener noreferrer)와 새 탭 규칙을 지킨다.
 *    프론트 abort 상한(300s)은 서버 체인 상한(NVIDIA 180s + Qwen fallback 90s = 270s)보다 크다.
 */

if (!MiniElement.prototype.scrollIntoView) {
  MiniElement.prototype.scrollIntoView = function scrollIntoView() {};
}

const MARKUP = `
  <div id="ns-ai">
    <form id="ns-ai-form">
      <textarea id="ns-ai-input"></textarea>
      <button type="submit" id="ns-ai-send">질문</button>
    </form>
    <div id="ns-ai-thread"></div>
  </div>`;

function loadHome({ chatResponse } = {}) {
  const root = parseHtml(MARKUP);
  const byId = {};
  const collect = (node) => {
    node.children.forEach((child) => {
      if (child.id) byId[child.id] = child;
      collect(child);
    });
  };
  collect(root);

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

  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init: init || {} });
    if (String(url) === '/api/newstart/debt-status/me') {
      return { ok: false, status: 401, json: async () => ({}) };
    }
    if (String(url) === '/api/newstart/ai/chat') {
      return chatResponse || { ok: false, status: 500, json: async () => ({}) };
    }
    return { ok: false, status: 404, json: async () => ({}) };
  };

  const location = { href: 'https://feel2real.co.kr/newstart', pathname: '/newstart', search: '', hash: '' };
  const window = {
    matchMedia: () => ({ matches: false }), setTimeout, clearTimeout,
    location, addEventListener() {}, scrollIntoView() {}
  };
  const context = vm.createContext({
    console, document, window, sessionStorage: memoryStorage({}), fetch: fetchImpl,
    AbortController, URL, location
  });
  vm.runInContext(fs.readFileSync(NEWSTART_PATH, 'utf8'), context, { filename: NEWSTART_PATH });

  const submit = () => {
    const listeners = byId['ns-ai-form']._listeners.get('submit') || [];
    assert.ok(listeners.length >= 1, 'AI 질문 폼에 submit 리스너가 붙지 않았다');
    listeners.forEach((listener) => listener({ preventDefault() {} }));
  };
  return { byId, root, calls, submit, window };
}

const flush = async () => { for (let i = 0; i < 10; i += 1) await new Promise((r) => setImmediate(r)); };

const OK_CHAT = {
  ok: true, status: 200,
  json: async () => ({
    status: 'ok',
    answer: '채권자 변동 통지를 받으면 서면 이의제기 절차를 먼저 확인하세요.',
    model: 'nvidia/nemotron',
    elapsedMs: 41200,
    sources: [
      { kind: '공식 자료', title: '개인신용회생 절차 안내', url: 'https://www.ccrs.or.kr/guide' },
      { kind: '실증 사례(DB)', title: '변동 통지 후 14일 내 이의제기 사례', url: 'https://cafe.daum.net/newhopero/1' },
      { kind: '운영자 정리', title: '내부 정리 노트', url: 'internal://note/12' }
    ]
  })
};

test('given 지피지기 답변 when 성공 then 답변 블록 아래에 "이 답변의 근거" 목록이 렌더된다(출처 폐기 회귀 방지)', async () => {
  const { byId, submit } = loadHome({ chatResponse: OK_CHAT });
  byId['ns-ai-input'].value = '채권자 변동 통지를 받았어요';
  submit();
  await flush();

  const thread = byId['ns-ai-thread'];
  const sources = thread.querySelector('.ns-ai-sources');
  assert.ok(sources, '근거 블록이 렌더되지 않았다 — sources가 화면에 닿지 않는다');
  assert.equal(sources.querySelector('.ns-ai-who').textContent, '이 답변의 근거');
  // mini_dom은 후손 셀렉터를 지원하지 않는다 — ul을 잡은 뒤 li를 센다.
  assert.equal(sources.querySelector('.ns-ai-source-list').querySelectorAll('li').length, 3);
  assert.ok(thread.textContent.includes('채권자 변동 통지를 받으면'), '답변 본문이 먼저 렌더되지 않았다');
});

test('given 출처 kind when 렌더 then 내부 표기("(DB)")는 사용자 언어로 바꾸고, 링크는 http/https만 만든다', async () => {
  const { byId, submit } = loadHome({ chatResponse: OK_CHAT });
  byId['ns-ai-input'].value = '질문';
  submit();
  await flush();

  const items = byId['ns-ai-thread'].querySelector('.ns-ai-source-list').querySelectorAll('li');
  assert.equal(items[0].querySelector('.ns-ai-source-kind').textContent, '공식 안내',
    '서버 kind를 그대로 쓰면 내부 표기가 회원 화면에 남는다');
  assert.equal(items[1].querySelector('.ns-ai-source-kind').textContent, '실증 사례');
  assert.equal(items[2].querySelector('.ns-ai-source-kind').textContent, '운영자 정리');

  const httpLink = items[0].querySelector('a');
  assert.equal(httpLink.getAttribute('href'), 'https://www.ccrs.or.kr/guide');
  assert.equal(httpLink.getAttribute('target'), '_blank');
  assert.equal(httpLink.getAttribute('rel'), 'noopener noreferrer', '역링크 회피 속성이 없다');
  assert.equal(items[2].querySelector('a'), null, 'http/https가 아닌 url을 링크로 만들었다');
});

test('given 답변 응답 when 렌더 then provider·model·debug 값이 화면 어디에도 나타나지 않는다', async () => {
  const { byId, submit } = loadHome({ chatResponse: OK_CHAT });
  byId['ns-ai-input'].value = '질문';
  submit();
  await flush();

  const text = byId['ns-ai-thread'].textContent;
  assert.doesNotMatch(text, /nvidia|nemotron|openrouter|qwen/i, 'LLM provider/model이 회원 화면에 노출됐다');
  assert.doesNotMatch(text, /elapsedMs|41200/, '내부 지연 수치가 노출됐다');
});

test('given 서버 실패(400 공통 문구) when 질문 then 안내 문구만 보여주고 근거 블록 만들지 않는다', async () => {
  const { byId, submit } = loadHome({
    chatResponse: { ok: false, status: 400, json: async () => ({ status: 'error', errorMessage: '답변을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.' }) }
  });
  byId['ns-ai-input'].value = '질문';
  submit();
  await flush();

  const thread = byId['ns-ai-thread'];
  assert.ok(thread.textContent.includes('답변을 불러오지 못했습니다.'));
  assert.equal(thread.querySelector('.ns-ai-sources'), null, '실패 응답에 근거 블록이 붙었다');
});

test('given 프론트 abort 상한 when 서버 체인과 비교 then 300s로, 서버 worst case(180+90=270s)보다 크다', () => {
  const js = fs.readFileSync(NEWSTART_PATH, 'utf8');
  assert.match(js, /}, 300000\);/, 'abort 상한이 300000ms가 아니다 — 답변 생성 중 프론트가 먼저 끊을 수 있다');
  assert.doesNotMatch(js, /}, 120000\);/, '120s abort로 되돌아가면 270s 서버 체인을 자른다');
});
