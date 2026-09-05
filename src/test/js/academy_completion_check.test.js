const assert = require('node:assert/strict');
const test = require('node:test');
const { loadRenderHooks } = require('./helpers/academy_render_hooks');

/**
 * Phase 4 Academy 완성도 진단 패널 — 실제 academy_builder.js 렌더/조회 함수를 실행해 검증한다.
 * 점수 산식 자체(deterministic rule set)는 AcademyCompletionCheckServiceTest.java가 서버
 * 쪽에서 검증한다 — 여기는 "서버가 준 결과를 화면에 정확히/안전하게 반영하는지"만 본다.
 */
function fakeElement() {
  return { textContent: '', hidden: false, innerHTML: '' };
}

function buildCompletionDocument() {
  const elements = new Map([
    ['builderCompletionPanel', fakeElement()],
    ['builderCompletionScore', fakeElement()],
    ['builderCompletionList', fakeElement()]
  ]);
  return {
    addEventListener() {},
    getElementById(id) { return elements.has(id) ? elements.get(id) : null; },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    elements
  };
}

test('given 완성도 결과 when 렌더 then 점수/항목이 정확히 반영되고 패널이 보인다', () => {
  const document = buildCompletionDocument();
  const hooks = loadRenderHooks('builder', { document });

  hooks.renderCompletionCheck({
    scorePercent: 62,
    items: [
      { key: 'intro', label: '학원 소개', done: true },
      { key: 'miniLectures', label: '미니 강의', done: false }
    ]
  });

  assert.equal(document.elements.get('builderCompletionScore').textContent, '62%');
  assert.equal(document.elements.get('builderCompletionPanel').hidden, false);
  const html = document.elements.get('builderCompletionList').innerHTML;
  assert.match(html, /is-done">.*✓.*학원 소개/s);
  assert.doesNotMatch(html.split('학원 소개')[1], /is-done/); // 미니 강의 항목은 완료 클래스가 없어야 한다
  assert.match(html, /△.*미니 강의/s);
});

test('given 완성도 API가 실패할 때(예: 아직 저장 전인 신규 Academy) when 조회 then 패널을 조용히 숨긴다(에러 노출 없음)', async () => {
  const document = buildCompletionDocument();
  document.elements.get('builderCompletionPanel').hidden = false; // 우선 보이는 상태로 시작
  const fetchImpl = async () => ({ ok: false });
  const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });

  await hooks.loadCompletionCheck();

  assert.equal(document.elements.get('builderCompletionPanel').hidden, true);
});

test('given 네트워크 오류 when 완성도 조회 then 예외를 던지지 않고 패널을 숨긴다', async () => {
  const document = buildCompletionDocument();
  const fetchImpl = async () => { throw new Error('network down'); };
  const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });

  await assert.doesNotReject(() => hooks.loadCompletionCheck());
  assert.equal(document.elements.get('builderCompletionPanel').hidden, true);
});

test('given 완성도 API 성공 when 조회 then fetch가 올바른 엔드포인트를 호출한다', async () => {
  const document = buildCompletionDocument();
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, init });
    return { ok: true, json: async () => ({ scorePercent: 100, items: [] }) };
  };
  const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });

  await hooks.loadCompletionCheck();

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/api/tutor/academy/ai/completion');
  assert.equal(document.elements.get('builderCompletionScore').textContent, '100%');
});
