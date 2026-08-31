const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * 스크롤 모션 — 단 하나의 primitive(academy_reveal.js).
 * Shopify Dawn 계열: translateY + opacity → 최종 상태 / 1회성 / 작은 stagger / reveal 후 unobserve /
 * reduced-motion → 즉시 최종 / IO 미발화 → 3s failsafe. 지나간 섹션은 건드리지 않는다.
 * GSAP·ScrollTrigger·sceneFlow·processProgress·자동 캐러셀 없음.
 */

const read = (rel) => fs.readFileSync(path.resolve(__dirname, rel), 'utf8');
const REVEAL_JS = read('../../main/resources/static/js/academy_reveal.js');
const REVEAL_CSS = read('../../main/resources/static/css/academy_reveal.css');
const PUBLIC_JS = read('../../main/resources/static/js/academy_public.js');
const PUBLIC_CSS = read('../../main/resources/static/css/academy_public.css');
const COLLECTION_JS = read('../../main/resources/static/js/academy_collection.js');
const PUBLIC_HTML = read('../../main/resources/templates/public/academy_public.html');
const SECTIONS_SRC = read('../../main/resources/static/js/academy_sections.js');

/* ── reveal primitive: 동작 ──────────────────────────────────────────────── */

function loadReveal(opts) {
  const o = opts || {};
  const timers = [];
  const observers = [];
  function el(extra) {
    const cls = new Set();
    return Object.assign({
      style: { _p: {}, setProperty(k, v) { this._p[k] = String(v); }, getPropertyValue(k) { return this._p[k] || ''; } },
      _attrs: {},
      classList: { add: (...x) => x.forEach((v) => cls.add(v)), remove: (...x) => x.forEach((v) => cls.delete(v)), contains: (v) => cls.has(v) },
      setAttribute(k, v) { this._attrs[k] = String(v); },
      getAttribute(k) { return k in this._attrs ? this._attrs[k] : null; },
      querySelectorAll() { return []; }
    }, extra || {});
  }
  const items = [el(), el(), el()];
  const group = el({ querySelectorAll: (s) => (s.indexOf('reveal-item') >= 0 ? items : []) });
  const root = {
    querySelectorAll: (s) => {
      if (s.indexOf('data-reveal') < 0) return [];
      // ':not([data-reveal-wired])' 를 흉내낸다
      if (s.indexOf('data-reveal-wired') >= 0 && group.getAttribute('data-reveal-wired') === 'true') return [];
      return [group];
    }
  };
  const win = {
    matchMedia: () => ({ matches: !!o.reduced }),
    setTimeout: (fn, ms) => { const id = timers.length + 1; timers.push({ fn, ms, id }); return id; },
    IntersectionObserver: o.noIO ? undefined : function (cb) {
      const io = { cb, observe() {}, unobserve() { this.unobserved = (this.unobserved || 0) + 1; }, fire(entries) { this.cb(entries, this); } };
      observers.push(io); return io;
    },
    document: { documentElement: { classList: { add() {} } } }
  };
  const sandbox = { window: win, globalThis: win };
  vm.createContext(sandbox);
  vm.runInContext(REVEAL_JS, sandbox);
  return { api: win.F2RReveal, group, items, root, timers, observers };
}

test('reveal: 그룹이 뷰포트에 들어오면 하위 item 에 stagger order 를 주고 is-revealed, 그리고 unobserve', () => {
  const { api, group, items, root, observers } = loadReveal();
  api.wire(root);
  assert.equal(observers.length, 1);
  observers[0].fire([{ isIntersecting: true, target: group }]);
  assert.ok(group.classList.contains('is-revealed'));
  assert.deepEqual(items.map((i) => i.style.getPropertyValue('--reveal-order')), ['0', '1', '2']);
  assert.equal(observers[0].unobserved, 1, 'reveal 후 unobserve(1회성)');
});

test('reveal: 지나간 콘텐츠를 다시 숨기지 않는다 — is-revealed 를 제거하는 코드가 없다', () => {
  assert.doesNotMatch(REVEAL_JS, /classList\.remove\(['"]is-revealed/);
  assert.doesNotMatch(REVEAL_JS, /'enter'|'active'|'exit'|data-scene/, 'enter/active/exit 상태 없음');
  assert.doesNotMatch(REVEAL_JS, /addEventListener\(['"]scroll['"]/, 'scroll 이벤트 없음');
});

test('reveal: reduced-motion 이면 IO 없이 즉시 is-revealed', () => {
  const { api, group, root, observers } = loadReveal({ reduced: true });
  api.wire(root);
  assert.equal(observers.length, 0);
  assert.ok(group.classList.contains('is-revealed'));
});

test('reveal: IO 미지원이면 즉시 is-revealed', () => {
  const { api, group, root } = loadReveal({ noIO: true });
  api.wire(root);
  assert.ok(group.classList.contains('is-revealed'));
});

test('reveal: 3s failsafe 로 아직 안 나타난 그룹을 강제로 노출', () => {
  const { api, root, group, timers } = loadReveal();
  api.wire(root);
  const failsafe = timers.find((t) => t.ms === 3000);
  assert.ok(failsafe, '3s failsafe 타이머');
  failsafe.fn();
  assert.ok(group.classList.contains('is-revealed'));
});

test('reveal: 재렌더 가드 — 이미 배선한 그룹은 건너뛴다', () => {
  const { api, root, observers } = loadReveal();
  api.wire(root);
  api.wire(root);
  assert.equal(observers.length, 1, '두 번째 wire 는 이미 배선한 그룹을 건너뛴다');
});

/* ── CSS 계약 ────────────────────────────────────────────────────────────── */

test('academy_reveal.css: html.js-reveal 스코프에서만 초기 숨김(모듈 미로드 시 콘텐츠 정적)', () => {
  assert.match(REVEAL_CSS, /html\.js-reveal \[data-reveal-item\]\s*\{[^}]*opacity:\s*0/);
  assert.doesNotMatch(REVEAL_CSS, /^\s*\[data-reveal-item\]\s*\{[^}]*opacity:\s*0/m);
  assert.match(REVEAL_CSS, /html\.js-reveal \[data-reveal\]\.is-revealed \[data-reveal-item\]/);
  assert.match(REVEAL_CSS, /@media \(prefers-reduced-motion: reduce\)/);
});

test('academy_public.css: reveal / hover 토큰이 :root 에 있다(빌더 캔버스도 이 파일을 로드)', () => {
  assert.match(PUBLIC_CSS, /--reveal-duration:\s*550ms/);
  assert.match(PUBLIC_CSS, /--reveal-ease:\s*cubic-bezier\(0, 0, \.3, 1\)/);
  assert.match(PUBLIC_CSS, /--reveal-stagger:\s*80ms/);
  assert.match(PUBLIC_CSS, /--hover-lift:\s*-6px/);
  // 세로 scroll-snap 없음
  assert.doesNotMatch(PUBLIC_CSS, /scroll-snap-type:\s*y/);
});

/* ── 삭제된 레거시 모션 ──────────────────────────────────────────────────── */

test('레거시 모션 자산·API 는 완전히 제거됐다', () => {
  for (const rel of [
    '../../main/resources/static/js/academy_storefront_motion.js',
    '../../main/resources/static/js/academy_story.js',
    '../../main/resources/static/css/academy_scene.css',
    '../../main/resources/static/css/academy_story.css'
  ]) {
    assert.equal(fs.existsSync(path.resolve(__dirname, rel)), false, `${rel} 는 삭제됐어야 한다`);
  }
  assert.doesNotMatch(PUBLIC_JS, /F2RStorefrontMotion|F2RStorefrontStory|wireStory|wireSceneFlow|wireStorefrontMotion|applyReveal|gsap|ScrollTrigger|processProgress|sceneFlow/);
  assert.doesNotMatch(PUBLIC_HTML, /gsap|ScrollTrigger|academy_storefront_motion|academy_story|academy_scene/);
  assert.doesNotMatch(SECTIONS_SRC, /data-fade|f2r-reveal|ai-process-step|ai-process-stage|data-collection-auto/);
});

/* ── AI Native: 유일한 cinematic 섹션 (sticky 데스크톱 / 세로 블록 모바일) ── */

test('AI Native: sticky Visual Stage + 5 세로 블록. GSAP/pin/scrub/타이머 없음', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = academyFixture({
    pageSettings: {
      sectionVisibility: { 'sec-digitaltwin': true },
      builderDraft: {
        aiProcessStep1: '학교 자료', aiProcessStep2: '출제자 Digital Twin', aiProcessStep3: '수업 Insight',
        aiProcessStep4: '학교별 문제 제작', aiProcessStep5: 'FINAL'
      }
    }
  });
  primeRenderer(map.public, academy);
  const html = map.public.renderAiProcess(academy);
  assert.match(html, /<figure class="ai-native-visual" data-ai-visual data-active="0"/);
  assert.match(html, /<ol class="ai-native-visual-frames">/);
  assert.equal((html.match(/class="ai-native-visual-frame" data-frame="/g) || []).length, 5);
  assert.equal((html.match(/class="ai-native-step" data-ai-step="/g) || []).length, 5);
  assert.match(html, /class="ai-native-step-desc">우리 학교 기출·프린트/);
  assert.doesNotMatch(html, /data-active-frame|ai-stage-frame|processProgress|watchScene/);

  // desktop = CSS position:sticky (JS pin 아님)
  assert.match(PUBLIC_CSS, /@media \(min-width:900px\)\{[\s\S]*\.ai-native-visual\{display:block;position:sticky/);
  // active 단계 트래커: IO 기반, 타이머/자동 sequence 없음
  assert.match(PUBLIC_JS, /function wireAiNative\(\)/);
  assert.match(PUBLIC_JS, /rootMargin: '-48% 0px -48% 0px'/);
  assert.doesNotMatch(PUBLIC_JS.slice(PUBLIC_JS.indexOf('function wireAiNative')), /setTimeout|setInterval/);
});

test('AI Native: 빌더 canvas 는 sticky 대신 세로 블록으로 정적 프리뷰 + 단계 이름 편집 앵커', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = academyFixture({ pageSettings: { sectionVisibility: { 'sec-digitaltwin': true }, builderDraft: { aiProcessStep1: '학교 자료' } } });
  primeRenderer(map.builder, academy);
  const html = map.builder.renderAiProcess(academy);
  assert.match(html, /data-builder-draft="aiProcessStep1"/);
  assert.match(PUBLIC_CSS, /@container \(max-width:900px\)\{[\s\S]*\.builder-public-preview \.ai-native-visual\{display:none/);
});

/* ── Collections: 자동 이동 없음 ─────────────────────────────────────────── */

test('collection: 자동 이동(autoplay/auto-advance) 코드가 없다', () => {
  assert.doesNotMatch(COLLECTION_JS, /initAuto|runAuto|autoScrollTo|data-collection-auto|setInterval/);
  assert.match(COLLECTION_JS, /track\.scrollBy\(/, '화살표 네비게이션은 유지');
  // renderCollectionShell 이 data-collection-auto 를 안 붙인다
  assert.doesNotMatch(SECTIONS_SRC, /data-collection-auto/);
});

test('collection.css: 데스크톱에서 Programs·Actual Class 는 grid(carousel 아님), 모바일은 네이티브 swipe', () => {
  const css = read('../../main/resources/static/css/academy_collection.css');
  assert.match(css, /@media \(min-width: 900px\)[\s\S]*\.f2r-collection--programs \.f2r-collection-track\s*\{[\s\S]*display: grid/);
  assert.match(css, /\.f2r-collection--videos \.f2r-collection-track\s*\{[\s\S]*display: grid/);
  // hover 생명력(데스크톱만)
  assert.match(css, /@media \(hover: hover\) and \(pointer: fine\)/);
  assert.match(css, /\.f2r-program-card:hover[\s\S]*transform: translateY\(var\(--hover-lift/);
});
