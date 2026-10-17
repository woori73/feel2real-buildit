const test = require('node:test');
const assert = require('node:assert/strict');
const { createTracker, qualifies } = require('../../main/resources/static/js/site_events.js');

/** 가짜 브라우저 환경: 타이머를 수동으로 흘리고, IntersectionObserver 콜백을 직접 호출한다. */
function makeEnv({ page = 'home', pathname = '/', top = true, sections = ['wiki', 'faq'] } = {}) {
  const listeners = { doc: {}, win: {} };
  const timers = new Map();
  let nextTimer = 1;
  const sent = [];
  const elements = sections.map((id) => ({ getAttribute: (n) => (n === 'data-track-section' ? id : null) }));
  const doc = {
    hidden: false,
    body: { getAttribute: (n) => (n === 'data-track-page' ? page : null) },
    querySelectorAll: () => elements,
    addEventListener: (t, fn) => { listeners.doc[t] = fn; },
  };
  const win = {
    location: { pathname },
    innerHeight: 800,
    crypto: { randomUUID: () => '11111111-1111-4111-8111-111111111111' },
    setTimeout: (fn, ms) => { const id = nextTimer++; timers.set(id, { fn, ms }); return id; },
    clearTimeout: (id) => { timers.delete(id); },
    addEventListener: (t, fn) => { listeners.win[t] = fn; },
  };
  win.top = top ? win : {};
  let observerCb = null;
  const unobserved = [];
  class IO { constructor(cb) { observerCb = cb; } observe() {} unobserve(el) { unobserved.push(el); } }
  const env = { win, doc, send: (url, json) => sent.push({ url, body: JSON.parse(json) }), IntersectionObserver: IO };
  const advance = (ms) => {
    for (const [id, t] of [...timers]) if (t.ms <= ms) { timers.delete(id); t.fn(); }
  };
  const entry = (el, ratio, height = 100) => ({
    target: el, isIntersecting: ratio > 0, intersectionRatio: ratio, intersectionRect: { height },
  });
  return { env, elements, listeners, sent, timers, advance, unobserved, fire: (entries) => observerCb(entries), entry, doc, win };
}

test('given 50% 이상 보이는 섹션 when 1초 유지되면 then 노출 이벤트를 1번 보낸다', () => {
  // given
  const h = makeEnv();
  const t = createTracker(h.env);
  // when
  h.fire([h.entry(h.elements[0], 0.6)]);
  h.advance(1000);
  t.flush();
  // then
  assert.equal(h.sent.length, 1);
  assert.deepEqual(h.sent[0].body.events, [{ type: 'SECTION_VIEW', target: 'wiki', seq: 0 }]);
  assert.equal(h.sent[0].body.page, 'home');
  assert.equal(h.sent[0].body.viewId, '11111111-1111-4111-8111-111111111111');
});

test('given 빠르게 스쳐 지나간 섹션(1초 미만) when 화면에서 벗어나면 then 노출을 보내지 않는다', () => {
  const h = makeEnv();
  const t = createTracker(h.env);
  h.fire([h.entry(h.elements[0], 0.9)]);
  h.fire([h.entry(h.elements[0], 0)]); // 1초가 되기 전에 사라짐
  h.advance(5000);
  t.flush();
  assert.equal(h.sent.length, 0);
});

test('given 50% 미만만 보이는 섹션 when 오래 머물러도 then 노출로 세지 않는다', () => {
  const h = makeEnv();
  const t = createTracker(h.env);
  h.fire([h.entry(h.elements[0], 0.2, 100)]);
  h.advance(5000);
  t.flush();
  assert.equal(h.sent.length, 0);
});

test('given 화면 높이의 50% 이상을 차지하는 매우 긴 섹션 when 비율은 낮아도 then 노출로 센다', () => {
  assert.equal(qualifies({ isIntersecting: true, intersectionRatio: 0.2, intersectionRect: { height: 450 } }, 800), true);
  assert.equal(qualifies({ isIntersecting: true, intersectionRatio: 0.2, intersectionRect: { height: 300 } }, 800), false);
  assert.equal(qualifies({ isIntersecting: false, intersectionRatio: 0, intersectionRect: { height: 0 } }, 800), false);
});

test('given 이미 노출을 보낸 섹션 when 다시 보이면 then 같은 방문에서 중복으로 보내지 않는다', () => {
  const h = makeEnv();
  const t = createTracker(h.env);
  h.fire([h.entry(h.elements[0], 1)]);
  h.advance(1000);
  h.fire([h.entry(h.elements[0], 0)]);
  h.fire([h.entry(h.elements[0], 1)]);
  h.advance(1000);
  t.flush();
  const all = h.sent.flatMap((s) => s.body.events).filter((e) => e.type === 'SECTION_VIEW');
  assert.equal(all.length, 1);
  assert.equal(h.unobserved.length, 1);
});

test('given 탭이 숨겨진 동안 when 타이머가 지나도 then 노출로 세지 않고, 다시 보이면 새로 1초를 센다', () => {
  const h = makeEnv();
  const t = createTracker(h.env);
  h.fire([h.entry(h.elements[0], 1)]);
  h.doc.hidden = true;
  h.listeners.doc.visibilitychange();
  h.advance(5000);
  assert.equal(h.sent.flatMap((s) => s.body.events).length, 0);
  h.doc.hidden = false;
  h.listeners.doc.visibilitychange();
  h.advance(1000);
  t.flush();
  assert.equal(h.sent.flatMap((s) => s.body.events).length, 1);
});

test('given 허용 속성 버튼 when 클릭하면 then 클릭·이동·시작 이벤트를 보내고 이동은 즉시 전송한다', () => {
  const h = makeEnv();
  createTracker(h.env);
  const attrs = { 'data-track-click': 'wiki_kakao', 'data-track-flow': 'exam_request', 'data-track-move': 'builder' };
  const el = { getAttribute: (n) => attrs[n] || null };
  h.listeners.doc.click({ target: { closest: () => el } });
  const events = h.sent.flatMap((s) => s.body.events);
  assert.deepEqual(events.map((e) => e.type).sort(), ['CLICK', 'FLOW_START', 'SERVICE_MOVE']);
});

test('given 같은 버튼을 7번 클릭 when 보내면 then 5회까지만 보내고 seq는 0부터 증가한다', () => {
  const h = makeEnv();
  const t = createTracker(h.env);
  const el = { getAttribute: (n) => (n === 'data-track-click' ? 'nav_contact' : null) };
  for (let i = 0; i < 7; i++) h.listeners.doc.click({ target: { closest: () => el } });
  t.flush();
  const events = h.sent.flatMap((s) => s.body.events);
  assert.equal(events.length, 5);
  assert.deepEqual(events.map((e) => e.seq), [0, 1, 2, 3, 4]);
});

test('given 한 번에 20개를 넘게 쌓이면 when 보내면 then 요청당 최대 20개로 나눠 보낸다', () => {
  const h = makeEnv();
  const t = createTracker(h.env);
  const targets = ['a','b','c','d','e','f','g','h','i','j','k','l','m'];
  targets.forEach((x) => { // 서로 다른 버튼 13개 × 2종 이벤트 = 26개
    const attrs = { 'data-track-click': x, 'data-track-flow': x };
    h.listeners.doc.click({ target: { closest: () => ({ getAttribute: (n) => attrs[n] || null }) } });
  });
  t.flush();
  assert.ok(h.sent.every((s) => s.body.events.length <= 20));
  assert.equal(h.sent.flatMap((s) => s.body.events).length, 26);
});

test('given 관리자 화면·iframe 미리보기·추적 페이지 속성 없음 when 초기화하면 then 아무것도 하지 않는다', () => {
  assert.equal(createTracker(makeEnv({ pathname: '/admin/analytics' }).env).enabled, false);
  assert.equal(createTracker(makeEnv({ top: false }).env).enabled, false);
  assert.equal(createTracker(makeEnv({ page: null }).env).enabled, false);
});

test('given 전송 본문 when 확인하면 then 허용된 필드(page·viewId·events)만 있고 URL·사용자 식별값·입력값이 없다', () => {
  const h = makeEnv();
  const t = createTracker(h.env);
  h.fire([h.entry(h.elements[0], 1)]);
  h.advance(1000);
  t.flush();
  const body = h.sent[0].body;
  assert.deepEqual(Object.keys(body).sort(), ['events', 'page', 'viewId']);
  assert.deepEqual(Object.keys(body.events[0]).sort(), ['seq', 'target', 'type']);
});
