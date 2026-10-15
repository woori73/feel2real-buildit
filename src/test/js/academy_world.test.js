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

// ── Bildi visual asset 구조 (Phase 5B.1a) ───────────────────────────────────
test('bildiAssetUrl: state → /img/bildi/bildi-<state>.svg, run/wait alias, 미정의 → null', () => {
  assert.equal(world.bildiAssetUrl('idle'), '/img/bildi/bildi-idle.svg');
  assert.equal(world.bildiAssetUrl('wings_open'), '/img/bildi/bildi-wings-open.svg');
  assert.equal(world.bildiAssetUrl('fly'), '/img/bildi/bildi-fly.svg');
  assert.equal(world.bildiAssetUrl('run'), '/img/bildi/bildi-fly.svg', 'run → fly asset alias');
  assert.equal(world.bildiAssetUrl('wait'), '/img/bildi/bildi-idle.svg', 'wait → idle asset alias');
  assert.equal(world.bildiAssetUrl('bogus'), null);
});

test('bildiAssetUrl: assetBase / assets override (아트 패스에서 WebP 교체용)', () => {
  assert.equal(world.bildiAssetUrl('fly', { assetBase: '/cdn/bildi/' }), '/cdn/bildi/bildi-fly.svg');
  assert.equal(
    world.bildiAssetUrl('fly', { assets: { fly: '/img/bildi/bildi-fly.webp' } }),
    '/img/bildi/bildi-fly.webp');
});

test('8개 필수 state에 모두 asset 파일명이 있다', () => {
  ['idle', 'wings_open', 'fly', 'land', 'point', 'talk', 'think', 'happy'].forEach((s) => {
    assert.ok(world.BILDI_STATE_ASSETS[s], `${s} asset 파일명 누락`);
  });
});

test('createBildi: .bildi > .bildi-inner > .bildi-art > (img.bildi-sprite + .bildi-fallback) 구조', () => {
  const b = world.createBildi(host());
  assert.equal((b.el.className || '').indexOf('bildi'), 0);
  const inner = b.el.children[0];
  assert.equal(inner.className, 'bildi-inner', '위치 transform 과 분리된 애니메이션 레이어');
  const art = inner.children[0];
  assert.equal(art.className, 'bildi-art');
  const sprite = art.children[0];
  const fb = art.children[1];
  assert.equal(sprite.tagName, 'IMG');
  assert.equal(sprite.className, 'bildi-sprite');
  assert.equal(sprite.getAttribute('alt'), '');
  assert.equal(fb.className, 'bildi-fallback');
  assert.match(fb.innerHTML, /^<svg/, 'fallback 은 인라인 BILDI_SVG');
  assert.equal(b.sprite, sprite);
});

test('academy_world.css: 상태/반응 애니메이션은 .bildi-inner 에만 걸린다 (.bildi 위치 transform 보호)', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const css = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/static/css/academy_world.css'), 'utf8');
  // .bildi[data-state=...] { animation: ... } 처럼 .bildi 자체에 animation 을 걸면 안 된다
  assert.doesNotMatch(css, /\.bildi\[data-state="[a-z_]+"\]\s*\{[^}]*animation:/,
    '.bildi 자체에 상태 애니메이션을 걸면 keyframe transform 이 이동을 삼킨다');
  assert.doesNotMatch(css, /\.bildi\[data-react="[a-z]+"\]\s*\{[^}]*animation:/,
    '.bildi 자체에 반응 애니메이션 금지');
  assert.doesNotMatch(css, /\.bildi\[data-state="fly"\]\[data-facing="up"\]\s*\{\s*transform:/,
    '.bildi 에 위치 override transform 을 걸면 안 된다');
  assert.match(css, /\.bildi\[data-state="idle"\] \.bildi-inner\s*\{[^}]*bildiBreathe/);
  assert.match(css, /\.bildi\[data-state="fly"\] \.bildi-inner\s*\{[^}]*bildiBob/);
});

test('setState → sprite.src 가 해당 state asset 으로 바뀐다', () => {
  const b = world.createBildi(host());
  assert.equal(b.sprite.getAttribute('src'), '/img/bildi/bildi-idle.svg');
  b.setState('fly');
  assert.equal(b.sprite.getAttribute('src'), '/img/bildi/bildi-fly.svg');
  b.dispatch('arrive'); // fly → land
  assert.equal(b.getState(), 'land');
  assert.equal(b.sprite.getAttribute('src'), '/img/bildi/bildi-land.svg');
});

test('sprite load → bildi-has-sprite(스프라이트 표시), error → 제거(fallback SVG 표시)', () => {
  const b = world.createBildi(host());
  b.setState('fly');
  assert.equal(b.hasSprite(), false, '로드 전에는 fallback');
  // 이미지 로드 성공 시뮬레이션
  (b.sprite._listeners.load || []).forEach((fn) => fn());
  assert.equal(b.hasSprite(), true, '로드되면 sprite 표시');
  // 이후 상태에서 이미지 로드 실패 시뮬레이션
  b.setState('point');
  (b.sprite._listeners.error || []).forEach((fn) => fn());
  assert.equal(b.hasSprite(), false, '로드 실패하면 fallback SVG 로 폴백');
});

test('controller.assetUrlFor / hasSprite 노출', () => {
  const b = world.createBildi(host());
  assert.equal(b.assetUrlFor('happy'), '/img/bildi/bildi-happy.svg');
  assert.equal(typeof b.hasSprite(), 'boolean');
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

// ── flightPosition: scroll progress → waypoint 보간 위치 (Phase 5B.1b) ──────
const SAFE = { x0: 20, y0: 60, x1: 620, y1: 460 }; // 600×400 usable
const SIZE = { w: 84, h: 84 };
const WPS = [
  { x: 0.14, y: 0.24 }, { x: 0.74, y: 0.46 }, { x: 0.22, y: 0.58 }, { x: 0.72, y: 0.28 }
];

test('flightPosition: progress 0 → 첫 waypoint, progress 1 → 마지막 waypoint (safe area 안)', () => {
  const start = world.flightPosition({ progress: 0, waypoints: WPS, safe: SAFE, size: SIZE, arc: 0 });
  const end = world.flightPosition({ progress: 1, waypoints: WPS, safe: SAFE, size: SIZE, arc: 0 });
  // 첫 waypoint x ≈ x0 + 0.14*(600-84)
  assert.ok(Math.abs(start.x - (20 + 0.14 * (600 - 84))) < 2, 'start x @ wp0');
  assert.ok(Math.abs(end.x - (20 + 0.72 * (600 - 84))) < 2, 'end x @ wp3');
  // safe area 안
  [start, end].forEach((p) => {
    assert.ok(p.x >= SAFE.x0 && p.x <= SAFE.x1 - SIZE.w, 'x in safe');
    assert.ok(p.y >= SAFE.y0 && p.y <= SAFE.y1 - SIZE.h, 'y in safe');
  });
});

test('flightPosition: 스크롤에 따라 X/Y가 실제로 이동한다 (같은 좌표 반복 = 실패)', () => {
  const samples = [0, 0.12, 0.25, 0.4, 0.55, 0.7, 0.85, 1].map((p) =>
    world.flightPosition({ progress: p, waypoints: WPS, safe: SAFE, size: SIZE }));
  // 인접 샘플이 전부 동일하면 "고정 chatbot" — 실패로 간주
  let moved = 0;
  for (let i = 1; i < samples.length; i += 1) {
    if (samples[i].x !== samples[i - 1].x || samples[i].y !== samples[i - 1].y) moved += 1;
  }
  assert.ok(moved >= samples.length - 2, `대부분의 구간에서 위치가 바뀌어야 한다 (moved=${moved})`);
  // 시작과 끝은 확실히 다르다
  assert.notDeepEqual(
    { x: samples[0].x, y: samples[0].y },
    { x: samples[samples.length - 1].x, y: samples[samples.length - 1].y });
  // X 범위가 실제로 넓게 쓰인다 (좌↔우 교차 waypoint)
  const xs = samples.map((s) => s.x);
  assert.ok(Math.max(...xs) - Math.min(...xs) > 200, 'X가 좌우로 크게 움직인다');
});

test('flightPosition: segment 경계(frac≈0)에서 moving=false, 중간(frac≈0.5)에서 moving=true', () => {
  const atWp = world.flightPosition({ progress: 0, waypoints: WPS, safe: SAFE, size: SIZE });
  const mid = world.flightPosition({ progress: 1 / (WPS.length - 1) / 2, waypoints: WPS, safe: SAFE, size: SIZE });
  assert.equal(atWp.moving, false, 'waypoint 위에서는 착지 상태');
  assert.equal(mid.moving, true, '구간 중간에서는 비행 상태');
  assert.equal(atWp.segIndex, 0);
});

test('flightPosition: arc 는 비행 중 위로 솟는다(직선 대비 y가 작아진다)', () => {
  const straight = world.flightPosition({ progress: 0.16, waypoints: WPS, safe: SAFE, size: SIZE, arc: 0 });
  const arced = world.flightPosition({ progress: 0.16, waypoints: WPS, safe: SAFE, size: SIZE, arc: 0.15 });
  assert.ok(arced.y < straight.y, 'arc 가 있으면 같은 progress에서 더 위(작은 y)');
});

test('flightPosition: size 가 safe area 를 넘어도 좌표는 safe 안에 clamp 된다', () => {
  const p = world.flightPosition({
    progress: 1, waypoints: [{ x: 2, y: 2 }], count: 1,
    safe: { x0: 0, y0: 0, x1: 100, y1: 100 }, size: { w: 60, h: 60 }
  });
  assert.ok(p.x >= 0 && p.x <= 40);
  assert.ok(p.y >= 0 && p.y <= 40);
});

test('flightPosition: waypoints 없거나 progress NaN 이어도 throw 안 함', () => {
  const a = world.flightPosition({});
  assert.equal(typeof a.x, 'number');
  const b = world.flightPosition({ progress: NaN, waypoints: [], safe: SAFE, size: SIZE });
  assert.equal(typeof b.y, 'number');
});
