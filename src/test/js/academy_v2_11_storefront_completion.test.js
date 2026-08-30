const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { loadSectionsModule } = require('./helpers/browser_script_harness');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

// V2-11: Eduholic Mobile Storefront Completion — 기존 primitive 확장(재설계 아님).
//   A. AI Native 5-step: 느리게 + watchScene(V2-10 data-scene 에 물려 active 면 재생, 벗어나면 초기화)
//   B. Vertical Scene tuning 강화
//   C. Featured — 저장된 featuredLessonIds 를 실제 name+핵심특징으로 렌더(데코 목업 제거)
//   D. Programs — 핵심 특징 Visual Highlight Box(강한 border + 라벨)
//   E. Horizontal Collection — 첫 진입 affordance(hint/advance), 무한 캐러셀 아님, 사용자 우선

const MOTION_SRC = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/academy_storefront_motion.js'), 'utf8');
const COLLECTION_JS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/academy_collection.js'), 'utf8');
const COLLECTION_CSS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/academy_collection.css'), 'utf8');
const SCENE_CSS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/academy_scene.css'), 'utf8');
const PUBLIC_JS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/academy_public.js'), 'utf8');

/* ── A: processProgress replay ─────────────────────────────────────────── */

function loadMotion() {
  const observers = [];
  const timers = [];
  const mutationObservers = [];
  const win = {
    matchMedia: () => ({ matches: false }),
    setTimeout: (fn, ms) => { timers.push({ fn, ms, id: timers.length + 1 }); return timers.length; },
    clearTimeout: (id) => { const t = timers.find((x) => x.id === id); if (t) t.done = true; },
    document: { documentElement: { classList: { add() {} } } },
    IntersectionObserver: function (cb) {
      const o = { cb, targets: [], disconnected: false, observe(t) { this.targets.push(t); }, disconnect() { this.disconnected = true; }, fire(e) { this.cb(e); } };
      observers.push(o); return o;
    },
    MutationObserver: function (cb) {
      const o = { cb, targets: [], disconnected: false, observe(t) { this.targets.push(t); }, disconnect() { this.disconnected = true; }, fire() { this.cb([{ type: 'attributes' }]); } };
      mutationObservers.push(o); return o;
    }
  };
  const sandbox = { window: win, globalThis: win };
  vm.createContext(sandbox);
  vm.runInContext(MOTION_SRC, sandbox);
  const runTimers = () => { let g = 0; while (timers.some((t) => !t.done && !t.ran) && g++ < 500) { const t = timers.filter((x) => !x.done && !x.ran).sort((a, b) => a.ms - b.ms)[0]; t.ran = true; t.fn(); } };
  return { M: win.F2RStorefrontMotion, observers, mutationObservers, timers, runTimers };
}

function step() { const c = new Set(); return { classList: { add: (...x) => x.forEach((v) => c.add(v)), remove: (...x) => x.forEach((v) => c.delete(v)), contains: (v) => c.has(v) }, has: (v) => c.has(v) }; }

test('A: processProgress watchScene — data-scene="active" 면 재생, enter/exit 로 벗어나면 초기화', () => {
  const { M, mutationObservers, runTimers } = loadMotion();
  const steps = [step(), step(), step()];
  const listC = new Set();
  const list = { classList: { add: (x) => listC.add(x), remove: (x) => listC.delete(x), contains: (x) => listC.has(x) }, querySelectorAll: () => steps };
  const sceneAttrs = { 'data-scene': 'enter' };
  const scene = { getAttribute: (k) => sceneAttrs[k] ?? null, setAttribute: (k, v) => { sceneAttrs[k] = String(v); } };
  M.processProgress(list, { start: 0, step: 10, linkLead: 5, watchScene: scene });
  const mo = mutationObservers.find((o) => o.targets[0] === scene);
  assert.ok(mo, 'watchScene 은 섹션의 data-scene 을 관찰하는 MutationObserver 를 만든다');

  // enter 상태에선 아직 재생 안 함
  runTimers();
  assert.ok(steps.every((s) => !s.has('is-active')), 'enter 에선 진행하지 않는다');

  // active 로 바뀌면 재생
  sceneAttrs['data-scene'] = 'active';
  mo.fire();
  runTimers();
  assert.ok(steps.every((s) => s.has('is-active')), 'active 가 되면 전 단계 진행');
  assert.ok(listC.has('is-complete'));

  // exit 로 벗어나면 초기화
  sceneAttrs['data-scene'] = 'exit';
  mo.fire();
  assert.ok(steps.every((s) => !s.has('is-active') && !s.has('is-done') && !s.has('is-linked')), '벗어나면 리셋');
  assert.ok(!listC.has('is-complete'));

  // 다시 active 가 되면 재생
  sceneAttrs['data-scene'] = 'active';
  mo.fire();
  runTimers();
  assert.ok(steps.every((s) => s.has('is-active')), '재진입(active) 시 다시 진행');
});

test('A: processProgress watchScene — 6초 안에 data-scene 이 안 붙으면 최종 상태로 고정(콘텐츠 흐림 방지)', () => {
  const { M, timers, runTimers } = loadMotion();
  const steps = [step(), step(), step()];
  const listC = new Set();
  const list = { classList: { add: (x) => listC.add(x), remove: (x) => listC.delete(x), contains: (x) => listC.has(x) }, querySelectorAll: () => steps };
  const scene = { getAttribute: () => null, setAttribute() {} }; // sceneFlow 미배선
  M.processProgress(list, { start: 0, step: 10, watchScene: scene });
  const failsafe = timers.find((t) => t.ms === 6000);
  assert.ok(failsafe, '6초 페일세이프 타이머');
  failsafe.fn();
  assert.ok(steps.every((s) => s.has('is-active') && s.has('is-done')), '페일세이프로 전부 최종 상태');
  assert.ok(listC.has('is-complete'));
});

test('A: processProgress 는 setInterval(무한 반복)을 쓰지 않는다', () => {
  assert.doesNotMatch(MOTION_SRC, /setInterval/);
});

test('A: public.js 는 AI 5-step 을 느리게(~2.3s) + watchScene(data-scene) 으로 배선한다', () => {
  assert.match(PUBLIC_JS, /processProgress\(steps,\s*\{[^}]*watchScene:\s*aiSection/);
  assert.match(PUBLIC_JS, /processProgress\(steps,\s*\{[^}]*step:\s*4\d\d/); // step >= 400ms
});

/* ── B: sceneFlow 6s 페일세이프 ────────────────────────────────────────── */

test('B: sceneFlow — 6초 안에 IO 가 active 를 못 만들면 전부 active 로 고정(콘텐츠 흐림 방지)', () => {
  const { M, timers } = loadMotion();
  const attrs1 = {}; const attrs2 = {};
  const mk = (a) => ({ getBoundingClientRect: () => ({ top: 900, bottom: 1600 }), getAttribute: (k) => a[k] ?? null, setAttribute: (k, v) => { a[k] = String(v); }, removeAttribute: (k) => { delete a[k]; } });
  const s1 = mk(attrs1); const s2 = mk(attrs2);
  M.sceneFlow([s1, s2]);
  assert.equal(s1.getAttribute('data-scene'), 'enter'); // 초기: 아래 → enter
  // IO 콜백이 한 번도 안 온다. 6초 타이머만 실행.
  const failsafe = timers.find((t) => t.ms === 6000);
  assert.ok(failsafe, '6초 페일세이프 타이머');
  failsafe.fn();
  assert.equal(s1.getAttribute('data-scene'), 'active');
  assert.equal(s2.getAttribute('data-scene'), 'active');
});

/* ── B: Vertical Scene tuning 강화 ────────────────────────────────────── */

test('B: academy_scene.css — 전환 체감 강화(translateY↑, exit opacity↓), 계약(enter/active/exit)은 유지', () => {
  const c = SCENE_CSS.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\s+/g, '');
  const enterY = Number((c.match(/data-scene="enter"\]>\*\{transform:translateY\((\d+)px\)/) || [])[1]);
  const exitOp = Number((c.match(/data-scene="exit"\]\{opacity:(\.?\d+)/) || [])[1]);
  assert.ok(enterY >= 34, 'enter translateY 가 V2-10(26px)보다 커야 한다: ' + enterY);
  assert.ok(exitOp <= 0.2, 'exit opacity 가 V2-10(.28)보다 낮아야 한다: ' + exitOp);
  assert.match(c, /data-scene="active"\]>\*\{transform:none/);
  assert.match(c, /@media\(prefers-reduced-motion:reduce\)/);
});

/* ── C: Featured — 저장된 featuredLessonIds 실제 렌더 ──────────────────── */

test('C: Featured 는 featuredLessonIds 로 고른 대표 수업을 실제 name + 핵심특징으로 낸다', () => {
  const s = loadSectionsModule();
  const view = s.toSectionView({
    name: '테스트영어',
    lessons: [
      { lessonId: 9, name: '영문법 기초: 레고', highlight: '원리 중심 영문법' },
      { lessonId: 13, name: '압구정중, 신구중', tabShortName: '중학교 영어내신', highlight: '출제자의 눈으로 함정 파악' }
    ],
    pageSettings: { sectionVisibility: { 'sec-featured': true }, builderDraft: { featuredLessonIds: '9,13', featuredItem1: '압구정중' } }
  });
  for (const [kind, ctxName] of [['public', 'createPublicContext'], ['builder', 'createBuilderContext']]) {
    const ctx = s[ctxName]({ slug: () => 'x', showcase: () => ({ render: () => '' }), videos: () => [] });
    const html = s.renderFeatured(view, ctx);
    assert.match(html, /class="featured-programs/, kind);
    assert.match(html, /featured-program-name">영문법 기초: 레고</, kind);
    assert.match(html, /featured-program-highlight">원리 중심 영문법</, kind);
    assert.match(html, /featured-program-highlight">출제자의 눈으로 함정 파악</, kind);
  }
});

test('C: featuredLessonIds 가 없으면 Featured 에 프로그램 블록을 만들지 않는다(빈 데코 금지)', () => {
  const s = loadSectionsModule();
  const view = s.toSectionView({
    name: 'x', lessons: [{ lessonId: 9, name: 'A', highlight: 'h' }],
    pageSettings: { sectionVisibility: { 'sec-featured': true }, builderDraft: { featuredItem1: '학교' } }
  });
  const ctx = s.createPublicContext({ slug: () => 'x', showcase: () => ({ render: () => '' }), videos: () => [] });
  assert.doesNotMatch(s.renderFeatured(view, ctx), /featured-programs/);
});

/* ── D: Programs 핵심 특징 Visual Highlight Box ───────────────────────── */

test('D: .f2r-program-card-highlight 는 강한 border + "핵심 특징" 라벨 + 강조 타이포', () => {
  const c = COLLECTION_CSS.replace(/\s+/g, '');
  const rule = c.slice(c.indexOf('.f2r-program-card-highlight{'), c.indexOf('.f2r-program-card-highlight{') + 260);
  assert.match(rule, /border:1\.?5?pxsolidvar\(--ultramarine\)/, '카드 테두리(--line)와 구분되는 강한 border');
  assert.match(rule, /font-weight:8\d\d/);
  assert.match(c, /\.f2r-program-card-highlight::before\{content:"핵심특징"/);
  // gradient/neon 금지
  assert.doesNotMatch(rule, /gradient|0 0 0|box-shadow:0[^;]*rgba\([^)]*\.[3-9]/);
});

/* ── E: Horizontal Collection affordance ──────────────────────────────── */

test('E: renderCollectionShell — variant 별 data-collection-auto (programs=advance, videos/lectures=hint), static 모드엔 없음', () => {
  const s = loadSectionsModule();
  const shell = (o) => s.renderCollectionShell(Object.assign({ cards: ['<div>a</div>', '<div>b</div>'] }, o));
  assert.match(shell({ variant: 'programs' }), /data-collection-auto="advance"/);
  assert.match(shell({ variant: 'videos' }), /data-collection-auto="hint"/);
  assert.match(shell({ variant: 'lectures' }), /data-collection-auto="hint"/);
  assert.doesNotMatch(shell({ variant: 'programs', staticMode: true }), /data-collection-auto/);
  assert.doesNotMatch(shell({}), /data-collection-auto/); // variant 없으면 없음
});

test('E: academy_collection.js auto — IO 1회, 사용자 입력 즉시 중단, reduced-motion 스킵, 무한 반복 없음', () => {
  assert.match(COLLECTION_JS, /function runAuto\(/);
  assert.match(COLLECTION_JS, /function initAuto\(/);
  assert.match(COLLECTION_JS, /prefers-reduced-motion: reduce/);
  assert.doesNotMatch(COLLECTION_JS, /setInterval/);
  // 사용자 직접 입력에서 중단(스크롤 이벤트 아님)
  assert.match(COLLECTION_JS, /\[['"]pointerdown['"], ['"]touchstart['"], ['"]wheel['"], ['"]keydown['"], ['"]focusin['"]\]/);
  // 레거시 scrollBy(dx, 0) — behavior:'smooth' 옵션에 의존하지 않는다(CSS scroll-behavior 담당)
  assert.doesNotMatch(COLLECTION_JS, /behavior:\s*['"]smooth['"]/);
  assert.match(COLLECTION_JS, /track\.scrollBy\(/);
  // IO 는 1회성(첫 진입 후 disconnect)
  assert.match(COLLECTION_JS, /io\.disconnect\(\);\s*\n\s*runAuto\(collection\)/);
});

/* ── E: academy_collection.js auto — 동작(behavioral) ──────────────────── */

function loadCollection(opts) {
  const o = opts || {};
  const timers = [];
  const ios = [];
  function el(tag, extra) {
    const listeners = {};
    const cls = new Set();
    return Object.assign({
      tagName: tag, className: '', dataset: {}, _attrs: {},
      children: [], style: {},
      classList: {
        add: (...x) => x.forEach((v) => cls.add(v)),
        remove: (...x) => x.forEach((v) => cls.delete(v)),
        contains: (v) => cls.has(v),
        toggle: (v, on) => { const want = on == null ? !cls.has(v) : !!on; if (want) cls.add(v); else cls.delete(v); return want; }
      },
      getAttribute(k) { return k in this._attrs ? this._attrs[k] : null; },
      setAttribute(k, v) { this._attrs[k] = String(v); },
      hasAttribute(k) { return k in this._attrs; },
      addEventListener(t, fn) { (listeners[t] = listeners[t] || []).push(fn); },
      removeEventListener(t, fn) { if (listeners[t]) listeners[t] = listeners[t].filter((f) => f !== fn); },
      _fire(t) { (listeners[t] || []).slice().forEach((fn) => fn({ type: t })); },
      _listeners: listeners
    }, extra || {});
  }
  const cardCount = o.cards == null ? 4 : o.cards;
  const cardEls = [];
  for (let i = 0; i < cardCount; i += 1) cardEls.push(el('div', { className: 'f2r-collection-card', offsetWidth: 300, offsetLeft: i * 320 }));
  const track = el('div', {
    scrollLeft: 0, clientWidth: 360, scrollWidth: 320 * cardCount,
    scrollBy(dx) { this.scrollLeft = Math.max(0, this.scrollLeft + dx); },
    querySelectorAll(sel) { return sel.indexOf('f2r-collection-card') >= 0 ? cardEls : []; },
    querySelector() { return null; }
  });
  track.dataset = {};
  const collection = el('div', {
    className: 'f2r-collection f2r-collection--' + (o.variant || 'videos'),
    querySelector(sel) { return sel.indexOf('collection-track') >= 0 ? track : null; }
  });
  collection._attrs['data-collection-auto'] = o.mode || 'hint';
  const root = {
    querySelectorAll(sel) {
      if (sel.indexOf('f2r-collection') >= 0) return [collection];
      return [];
    }
  };
  const win = {
    matchMedia: () => ({ matches: !!o.reduced }),
    setTimeout: (fn, ms) => { const id = timers.length + 1; timers.push({ fn, ms, id, done: false }); return id; },
    clearTimeout: (id) => { const t = timers.find((x) => x.id === id); if (t) t.done = true; },
    addEventListener() {}, removeEventListener() {},
    requestAnimationFrame: (fn) => { fn(); return 1; },
    IntersectionObserver: o.noIO ? undefined : function (cb) {
      const io = { cb, disconnected: false, observe() {}, disconnect() { this.disconnected = true; }, fire(e) { this.cb(e); } };
      ios.push(io); return io;
    },
    document: {
      documentElement: { classList: { add() {} } },
      addEventListener() {}, removeEventListener() {},
      querySelectorAll: root.querySelectorAll
    }
  };
  const sandbox = { window: win, globalThis: win };
  vm.createContext(sandbox);
  vm.runInContext(COLLECTION_JS, sandbox);
  const advance = (upToMs) => {
    let g = 0;
    while (g++ < 999) {
      const due = timers.filter((t) => !t.done && !t.ran && (upToMs == null || t.ms <= upToMs)).sort((a, b) => a.ms - b.ms)[0];
      if (!due) break;
      due.ran = true; due.fn();
    }
  };
  return { C: win.F2RAcademyCollection, root, collection, track, cardEls, timers, ios, advance };
}

test('E(동작): hint 모드 — 진입하면 살짝 밀었다(peek) 원위치로 복귀하고 끝난다(1회)', () => {
  const { C, root, track, ios, advance } = loadCollection({ mode: 'hint' });
  C.wire(root);
  assert.equal(track.dataset.autoReady, 'true', 'initAuto 가 IO 를 배선한다');
  assert.equal(ios.length, 1);
  ios[0].fire([{ isIntersecting: true, intersectionRatio: 0.7 }]);
  assert.ok(track.scrollLeft > 0, 'peek — 오른쪽으로 살짝 이동');
  const peeked = track.scrollLeft;
  advance();
  assert.equal(track.scrollLeft, 0, 'peek 후 원위치 복귀');
  assert.ok(peeked < track.scrollWidth - track.clientWidth, 'peek 은 한 카드 미만의 작은 양');
});

test('E(동작): advance 모드 — 카드를 하나씩 정지-이동으로 넘겨보고 처음으로 복귀(무한 아님)', () => {
  const { C, root, track, ios, advance, timers } = loadCollection({ mode: 'advance', cards: 4 });
  C.wire(root);
  ios[0].fire([{ isIntersecting: true, intersectionRatio: 0.9 }]);
  advance();
  assert.equal(track.scrollLeft, 0, '끝나면 처음으로 복귀');
  assert.ok(timers.length > 3, '단계별 타이머가 여러 개(정지-이동)');
  assert.ok(timers.every((t) => t.done || t.ran), '모든 타이머 소진 — 재예약(무한) 없음');
});

test('E(동작): 사용자 입력(pointerdown)이 오면 이후 자동 이동을 즉시 중단한다', () => {
  const { C, root, track, ios, advance } = loadCollection({ mode: 'advance', cards: 4 });
  C.wire(root);
  ios[0].fire([{ isIntersecting: true, intersectionRatio: 0.9 }]);
  track._fire('pointerdown');
  const at = track.scrollLeft;
  advance();
  assert.equal(track.scrollLeft, at, '취소 이후 자동 스크롤이 track 을 더 건드리지 않는다');
});

test('E(동작): prefers-reduced-motion 이면 auto 를 아예 배선하지 않는다', () => {
  const { C, root, track, ios } = loadCollection({ mode: 'hint', reduced: true });
  C.wire(root);
  assert.notEqual(track.dataset.autoReady, 'true');
  assert.equal(ios.length, 0);
});

test('E(동작): IntersectionObserver 미지원이면 auto 를 건너뛴다(콘텐츠는 그대로)', () => {
  const { C, root, track } = loadCollection({ mode: 'hint', noIO: true });
  C.wire(root);
  assert.notEqual(track.dataset.autoReady, 'true');
});

test('E: 공개 sec-videos / sec-lessons 렌더에 data-collection-auto 가 붙는다 (빌더는 정적)', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = academyFixture({ name: '테스트', lessons: [{ lessonId: 1, name: 'A' }], pageSettings: { sectionVisibility: {}, builderDraft: { lessonsLayout: 'horizontal', videosLayout: 'horizontal' } } });
  primeRenderer(map.public, academy, [{ videoId: 1, videoUrl: '/v', thumbnailUrl: '/t', width: 400, height: 700 }]);
  primeRenderer(map.builder, academy, [{ videoId: 1, thumbnailUrl: '/t', width: 400, height: 700 }]);
  assert.match(map.public.renderLessons(academy), /data-collection-auto="advance"/);
  assert.match(map.public.renderVideos(academy), /data-collection-auto="hint"/);
  assert.doesNotMatch(map.builder.renderLessons(academy), /data-collection-auto/);
  assert.doesNotMatch(map.builder.renderVideos(academy), /data-collection-auto/);
});
