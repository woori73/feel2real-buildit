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
 * 지피지기 Agent 2단계 답변 계약 (LLM 실호출 없음 — fetch를 가짜로 대체).
 *
 * QUESTION → STEP 1(/chat: 전자책+실제 사례) 표시 → "관련된 현재 규정을 확인해서 알려드리겠습니다." →
 * STEP 2(/chat/official: 현재 공식자료) 추가. STEP 2가 실패·timeout이어도 STEP 1은 화면에 남는다.
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

const NOTICE = '관련된 현재 규정을 확인해서 알려드리겠습니다.';
const DELAY = '현재 규정 확인이 지연되고 있습니다. 위 답변은 전자책과 실제 사례를 바탕으로 먼저 안내드렸습니다.';

function load({ step1, step2 }) {
  const root = parseHtml(MARKUP);
  const byId = {};
  const collect = (node) => node.children.forEach((c) => { if (c.id) byId[c.id] = c; collect(c); });
  collect(root);
  const document = {
    readyState: 'complete', head: { appendChild() {} }, body: root, addEventListener() {},
    getElementById(id) { return byId[id] || null; },
    querySelector(sel) { return root.querySelector(sel); },
    querySelectorAll(sel) { return root.querySelectorAll(sel); },
    createElement(tag) { return new MiniElement(tag); }
  };
  const calls = [];
  const fetchImpl = (url, init) => {
    calls.push({ url: String(url), body: init && init.body ? JSON.parse(init.body) : null });
    const u = String(url);
    if (u === '/api/newstart/ai/chat') return step1(calls);
    if (u === '/api/newstart/ai/chat/official') return step2(calls);
    return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
  };
  const location = { href: 'https://feel2real.co.kr/newstart', pathname: '/newstart', search: '', hash: '' };
  const window = { matchMedia: () => ({ matches: false }), setTimeout, clearTimeout, location, addEventListener() {}, scrollIntoView() {} };
  const context = vm.createContext({
    console, document, window, sessionStorage: memoryStorage({}), fetch: fetchImpl, AbortController, URL, location
  });
  vm.runInContext(fs.readFileSync(NEWSTART_PATH, 'utf8'), context, { filename: NEWSTART_PATH });
  const submit = () => (byId['ns-ai-form']._listeners.get('submit') || []).forEach((l) => l({ preventDefault() {} }));
  return { byId, calls, submit };
}

const flush = async () => { for (let i = 0; i < 20; i += 1) await new Promise((r) => setImmediate(r)); };
const ok = (data) => Promise.resolve({ ok: true, status: 200, json: async () => data });

const STEP1 = {
  status: 'ok', stage: 'CASE', model: 'm',
  answer: '전자책에서는 기한이익상실 전에 상담을 접수한 사례가 있습니다.',
  sources: [{ kind: '운영자·전자책 자료', title: '[전자책] EBK-9-1', url: 'docs/x' },
    { kind: '부실차주 카페 사례', title: '사업자 후순위 담보대출 문의드립니다.', url: 'https://cafe.naver.com/newhopero/6037' }]
};
const STEP2 = {
  status: 'ok', stage: 'OFFICIAL', model: 'm',
  answer: '공식 안내에 따르면 현재 담보채무는 캠코가 인수합니다. 사례 당시와 다를 수 있습니다.',
  sources: [{ kind: '공식 자료', title: '[새출발기금] 담보채무 안내', url: 'https://nsf.example/1' }]
};

function texts(thread) {
  return thread.querySelectorAll('.ns-ai-text').map((n) => n.textContent);
}

test('B. STEP 1 답변이 먼저 표시되고, 그 직후 정확한 안내문, 그 다음 STEP 2 답변이 아래에 추가된다', async () => {
  const seenAtStep2 = [];
  const { byId, calls, submit } = load({
    step1: () => ok(STEP1),
    step2: () => {
      // STEP 2 요청이 시작된 시점에 화면에는 이미 STEP 1 답변과 안내문이 있어야 한다.
      seenAtStep2.push(texts(byId['ns-ai-thread']));
      return ok(STEP2);
    }
  });
  byId['ns-ai-input'].value = '후순위 담보대출에서 기한이익상실 통보를 받았는데 어떻게 해야 하나?';
  submit();
  await flush();

  assert.deepEqual(calls.map((c) => c.url), ['/api/newstart/ai/chat', '/api/newstart/ai/chat/official']);
  assert.equal(seenAtStep2.length, 1);
  const before = seenAtStep2[0];
  assert.ok(before[1].includes('전자책에서는'), 'STEP 2 시작 전 STEP 1 답변이 화면에 없다');
  assert.ok(!before.includes('답변 작성 중'), '1차 표시 후에도 "답변 작성 중"이 남았다');
  assert.equal(before[2], NOTICE, 'STEP 1 직후 정확한 안내문이 아니다');

  const all = texts(byId['ns-ai-thread']);
  assert.equal(all.length, 4, '질문 + 1차 + 안내 + 2차 = 4블록');
  assert.ok(all[1].includes('전자책에서는'));
  assert.equal(all[2], NOTICE);
  assert.ok(all[3].includes('공식 안내에 따르면'));
});

test('C. STEP 2 요청은 원 질문과 1차 답변을 비교 입력으로 보내고, 2차 답변에는 공식 자료 근거만 붙는다', async () => {
  const { byId, calls, submit } = load({ step1: () => ok(STEP1), step2: () => ok(STEP2) });
  byId['ns-ai-input'].value = '질문입니다';
  submit();
  await flush();

  assert.equal(calls[1].body.message, '질문입니다');
  assert.equal(calls[1].body.step1Answer, STEP1.answer);
  const sources = byId['ns-ai-thread'].querySelectorAll('.ns-ai-sources');
  assert.equal(sources.length, 2, '1차·2차 각각 자기 근거 블록');
  const kinds2 = sources[1].querySelectorAll('.ns-ai-source-kind').map((n) => n.textContent);
  assert.deepEqual(kinds2, ['공식 안내']);
  const kinds1 = sources[0].querySelectorAll('.ns-ai-source-kind').map((n) => n.textContent);
  assert.ok(!kinds1.includes('공식 안내'), '1차 답변 근거에 공식 자료가 섞였다');
});

test('D. STEP 2가 STEP 1을 overwrite하지 않는다 — 1차 블록 텍스트·근거는 STEP 2 후에도 동일', async () => {
  const { byId, submit } = load({ step1: () => ok(STEP1), step2: () => ok(STEP2) });
  byId['ns-ai-input'].value = '질문';
  submit();
  await flush();
  const step1Block = byId['ns-ai-thread'].querySelectorAll('.ns-ai-text')[1];
  assert.equal(step1Block.querySelector('p') ? step1Block.querySelector('p').textContent : step1Block.textContent.split('이 답변의 근거')[0].trim(),
    STEP1.answer);
  assert.ok(step1Block.textContent.includes('사업자 후순위 담보대출 문의드립니다.'));
});

test('E. STEP 2가 HTTP 오류(timeout 400)여도 STEP 1은 유지되고 지연 안내만 추가된다(전체 실패 아님)', async () => {
  const { byId, submit } = load({
    step1: () => ok(STEP1),
    step2: () => Promise.resolve({ ok: false, status: 400, json: async () => ({ status: 'error', errorMessage: 'AI 서비스 연결에 실패했습니다.' }) })
  });
  byId['ns-ai-input'].value = '질문';
  submit();
  await flush();

  const all = texts(byId['ns-ai-thread']);
  assert.ok(all[1].includes('전자책에서는'), 'STEP 2 실패로 STEP 1이 사라졌다');
  assert.equal(all[2], NOTICE);
  assert.equal(all[3], DELAY);
  assert.equal(byId['ns-ai-thread'].querySelectorAll('.ns-ai-error').length, 0, 'STEP 2 지연을 오류 블록으로 처리했다');
});

test('E2. STEP 2 네트워크 예외/abort여도 STEP 1 유지 + 지연 안내, 입력창은 다시 사용 가능', async () => {
  const { byId, submit } = load({ step1: () => ok(STEP1), step2: () => Promise.reject(new Error('aborted')) });
  byId['ns-ai-input'].value = '질문';
  submit();
  await flush();

  const all = texts(byId['ns-ai-thread']);
  assert.ok(all[1].includes('전자책에서는'));
  assert.equal(all[3], DELAY);
});

test('STEP 1 자체가 실패하면 STEP 2를 요청하지 않는다(1차 없이 공식만 나오지 않는다)', async () => {
  const { byId, calls, submit } = load({
    step1: () => Promise.resolve({ ok: false, status: 400, json: async () => ({ status: 'error', errorMessage: '답변을 불러오지 못했습니다.' }) }),
    step2: () => ok(STEP2)
  });
  byId['ns-ai-input'].value = '질문';
  submit();
  await flush();

  assert.deepEqual(calls.map((c) => c.url), ['/api/newstart/ai/chat']);
  assert.ok(byId['ns-ai-thread'].textContent.includes('답변을 불러오지 못했습니다.'));
});
