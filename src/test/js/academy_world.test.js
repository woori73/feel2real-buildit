const assert = require('node:assert/strict');
const test = require('node:test');
const world = require('../../main/resources/static/js/academy_world.js');

/**
 * Academy World Engine (Phase 5B.1) — Bildi Sidekick 공용 엔진.
 * 순수 로직(scene 매핑·상태머신·후보) + DOM 헬퍼(mini fake DOM으로) 검증.
 */

// ── mini fake DOM ────────────────────────────────────────────────────────────
function fakeEl(tag) {
  const el = {
    tagName: (tag || 'div').toUpperCase(),
    children: [], parentNode: null, hidden: false, innerHTML: '',
    _attrs: {}, style: { _p: {}, setProperty(k, v) { this._p[k] = v; } },
    _listeners: {},
    classList: {
      _s: new Set(),
      add(n) { this._s.add(n); }, remove(n) { this._s.delete(n); },
      toggle(n, f) { const v = f === undefined ? !this._s.has(n) : f; if (v) this._s.add(n); else this._s.delete(n); return v; },
      contains(n) { return this._s.has(n); }
    },
    setAttribute(k, v) { this._attrs[k] = String(v); },
    getAttribute(k) { return this._attrs[k] == null ? null : this._attrs[k]; },
    removeAttribute(k) { delete this._attrs[k]; },
    appendChild(c) { c.parentNode = el; el.children.push(c); return c; },
    insertBefore(c, ref) { c.parentNode = el; el.children.unshift(c); return c; },
    removeChild(c) { el.children = el.children.filter((x) => x !== c); c.parentNode = null; return c; },
    addEventListener(type, fn) { (el._listeners[type] = el._listeners[type] || []).push(fn); },
    removeEventListener(type, fn) { el._listeners[type] = (el._listeners[type] || []).filter((f) => f !== fn); },
    getBoundingClientRect() { return el._rect || { left: 0, top: 0, width: 100, height: 100, right: 100, bottom: 100 }; },
    get firstChild() { return el.children[0] || null; }
  };
  return el;
}
function fakeDoc() {
  const doc = { createElement: (t) => { const e = fakeEl(t); e.ownerDocument = doc; return e; } };
  return doc;
}
function host() {
  const doc = fakeDoc();
  const h = fakeEl('div'); h.ownerDocument = doc;
  return h;
}

// ── Environment ─────────────────────────────────────────────────────────────
test('sceneForProgress: 스크롤 진행도 구간별 scene(맑은 아침→…→별)', () => {
  assert.equal(world.sceneForProgress(0), 'morning');
  assert.equal(world.sceneForProgress(0.2), 'day');
  assert.equal(world.sceneForProgress(0.38), 'clouds');
  assert.equal(world.sceneForProgress(0.5), 'rain');
  assert.equal(world.sceneForProgress(0.63), 'sunset');
  assert.equal(world.sceneForProgress(0.8), 'night');
  assert.equal(world.sceneForProgress(0.95), 'stars');
  assert.equal(world.sceneForProgress(1), 'stars');
  assert.equal(world.sceneForProgress(-3), 'morning', 'clamp 하한');
  assert.equal(world.sceneForProgress(9), 'stars', 'clamp 상한');
});

test('createSky: setScene/setProgress가 data-scene 속성과 --world-progress 를 갱신한다', () => {
  const h = host();
  const sky = world.createSky(h);
  assert.ok(sky.el, 'sky 요소가 host 앞에 삽입됨');
  assert.equal(h.children[0], sky.el);
  sky.setProgress(0.5);
  assert.equal(sky.el.getAttribute('data-scene'), 'rain');
  assert.equal(sky.getScene(), 'rain');
  sky.setProgress(0.95);
  assert.equal(sky.el.getAttribute('data-scene'), 'stars');
  sky.destroy();
  assert.equal(h.children.length, 0);
});

test('createSky({scenes}): 소비자가 자신의 registry를 넘길 수 있다(확장 가능)', () => {
  const sky = world.createSky(host(), {
    scenes: [{ id: 'a', from: 0, to: 0.5 }, { id: 'b', from: 0.5, to: 1.01 }]
  });
  sky.setProgress(0.2);
  assert.equal(sky.getScene(), 'a');
  sky.setProgress(0.7);
  assert.equal(sky.getScene(), 'b');
});

test('reactionForScene: 날씨별 Bildi 반응 hint (§7)', () => {
  assert.equal(world.reactionForScene('rain'), 'shake');
  assert.equal(world.reactionForScene('night'), 'gaze');
  assert.equal(world.reactionForScene('stars'), 'gaze');
  assert.equal(world.reactionForScene('space'), 'float');
  assert.equal(world.reactionForScene('day'), null);
});

// ── Bildi 상태머신 ──────────────────────────────────────────────────────────
test('nextBildiState: signature 전이 (idle→wings_open→fly→land→idle)', () => {
  const S = world.BILDI_STATES;
  assert.equal(world.nextBildiState(S.IDLE, 'scrollStart'), S.WINGS_OPEN);
  assert.equal(world.nextBildiState(S.WINGS_OPEN, 'move'), S.FLY);
  assert.equal(world.nextBildiState(S.FLY, 'arrive'), S.LAND);
  assert.equal(world.nextBildiState(S.FLY, 'scrollStop'), S.LAND);
  assert.equal(world.nextBildiState(S.LAND, 'settle'), S.IDLE);
});

test('nextBildiState: 정의되지 않은 event는 현재 상태 유지', () => {
  assert.equal(world.nextBildiState(world.BILDI_STATES.IDLE, 'bogus'), world.BILDI_STATES.IDLE);
  assert.equal(world.nextBildiState('nonsense', 'move'), 'nonsense');
});

test('10개 상태가 모두 정의되어 있다(§4)', () => {
  const names = Object.keys(world.BILDI_STATES);
  ['IDLE', 'RUN', 'WINGS_OPEN', 'FLY', 'LAND', 'POINT', 'TALK', 'THINK', 'HAPPY', 'WAIT']
    .forEach((n) => assert.ok(names.indexOf(n) >= 0, `${n} 누락`));
});

test('BILDI_SVG는 실제 캐릭터 벡터다 — emoji/원형 placeholder가 아니고 스카프·고글·날개 파트가 있다', () => {
  const svg = world.BILDI_SVG;
  assert.match(svg, /^<svg/);
  assert.match(svg, /bildi-wing/);
  assert.match(svg, /bildi-scarf/);
  assert.match(svg, /bildi-goggles/);
  assert.match(svg, /bildi-eye/);
  assert.doesNotMatch(svg, /🐣|🐤|&#x1F/, 'emoji를 캐릭터로 쓰지 않는다');
});

test('createBildi: setState/dispatch가 data-state를, react가 data-react를 갱신한다', () => {
  const h = host();
  const b = world.createBildi(h);
  assert.ok(b.el && h.children.indexOf(b.el) >= 0);
  assert.equal(b.el.getAttribute('data-state'), 'idle');
  b.dispatch('scrollStart');
  assert.equal(b.getState(), 'wings_open');
  b.setState('fly');
  assert.equal(b.el.getAttribute('data-state'), 'fly');
  b.face('up');
  assert.equal(b.el.getAttribute('data-facing'), 'up');
  b.react('shake');
  assert.equal(b.el.getAttribute('data-react'), 'shake');
  b.destroy();
  assert.equal(h.children.length, 0);
});

test('createBildi.moveToRect: host 좌표계로 변환해 transform을 건다(전체 화면 밖으로 안 나감)', () => {
  const b = world.createBildi(host());
  b.moveToRect(
    { left: 300, top: 220, width: 400, height: 300 },
    { left: 100, top: 100, width: 800, height: 600 },
    { x: 10, y: 20 }
  );
  const t = b.el.style._p ? b.el.style : b.el;
  assert.match(b.el.style.transform || '', /translate3d\(210px,140px,0\)/);
});

// ── Dialogue ────────────────────────────────────────────────────────────────
test('createDialogue: say가 본문 innerHTML을 채우고 표시, hide가 숨긴다', () => {
  const d = world.createDialogue(host());
  assert.equal(d.el.hidden, true, '초기엔 숨김');
  d.say('<b>안녕하세요</b>');
  assert.equal(d.el.hidden, false);
  assert.match(d.body.innerHTML, /안녕하세요/);
  assert.equal(d.isVisible(), true);
  d.hide();
  assert.equal(d.isVisible(), false);
});

// ── Scroll Story ────────────────────────────────────────────────────────────
test('createScrollStory: scroll 이벤트를 rAF로 coalesce해 onUpdate(progress/direction)를 부른다', () => {
  const updates = [];
  const scrollEl = fakeEl('div');
  scrollEl.scrollTop = 0; scrollEl.scrollHeight = 2000; scrollEl.clientHeight = 1000;
  let rafCb = null;
  const win = {
    requestAnimationFrame(fn) { rafCb = fn; return 1; },
    performance: { now: () => Date.now() }
  };
  const story = world.createScrollStory({
    scrollEl, window: win,
    sections: () => [],
    onUpdate: (s) => updates.push(s)
  });
  assert.equal(updates.length, 1, '초기 1회 compute');
  scrollEl.scrollTop = 500;
  (scrollEl._listeners.scroll || []).forEach((fn) => fn());
  assert.ok(rafCb, 'scroll이 rAF를 예약');
  rafCb();
  const last = updates[updates.length - 1];
  assert.equal(last.progress, 0.5);
  assert.equal(last.direction, 'down');
  story.detach();
  // detach 후 scroll은 더 이상 rAF를 예약하지 않는다
  rafCb = null;
  scrollEl.scrollTop = 900;
  (scrollEl._listeners.scroll || []).forEach((fn) => fn());
  assert.equal(rafCb, null, 'detach 후 scroll 무시');
});

test('reducedOrLowPower / prefersReducedMotion: navigator/matchMedia 없어도 throw 안 함', () => {
  assert.equal(typeof world.prefersReducedMotion(), 'boolean');
  assert.equal(typeof world.isLowPowerHint(), 'boolean');
  assert.equal(typeof world.reducedOrLowPower(), 'boolean');
});
