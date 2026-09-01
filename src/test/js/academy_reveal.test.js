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

test('Phase 4 에서 삭제한 레거시 모션 자산·API 는 여전히 없다(Phase 5 는 별도 academy_cinematic.js)', () => {
  for (const rel of [
    '../../main/resources/static/js/academy_storefront_motion.js',
    '../../main/resources/static/js/academy_story.js',
    '../../main/resources/static/css/academy_scene.css',
    '../../main/resources/static/css/academy_story.css'
  ]) {
    assert.equal(fs.existsSync(path.resolve(__dirname, rel)), false, `${rel} 는 삭제된 상태여야 한다`);
  }
  // 죽은 모션 API 는 되살아나지 않는다.
  assert.doesNotMatch(PUBLIC_JS, /F2RStorefrontMotion|F2RStorefrontStory|wireStory|wireSceneFlow|wireStorefrontMotion|applyReveal|processProgress\b|sceneFlow/);
  assert.doesNotMatch(PUBLIC_HTML, /academy_storefront_motion|academy_story\.(js|css)|academy_scene\.css/);
  assert.doesNotMatch(SECTIONS_SRC, /data-fade|f2r-reveal|ai-process-step|ai-process-stage|data-collection-auto/);
  // Phase 5: GSAP/ScrollTrigger 는 academy_cinematic.js 한 곳만 소유한다. academy_public.js 는
  // window.F2RCinematic 만 부른다(GSAP 리터럴 없음).
  assert.doesNotMatch(PUBLIC_JS, /\bgsap\b\.|new ScrollTrigger|ScrollTrigger\.create/);
  assert.match(PUBLIC_JS, /window\.F2RCinematic/);
  assert.ok(fs.existsSync(path.resolve(__dirname, '../../main/resources/static/js/academy_cinematic.js')));
});

/* ── cinematic: AI Native Pipeline (담당 섹션 1개) ── */

const CINEMATIC_JS = read('../../main/resources/static/js/academy_cinematic.js');

test('academy_cinematic.js: #sec-digitaltwin 하나만 담당, sec-featured 는 안 건드린다', () => {
  assert.match(CINEMATIC_JS, /root\.querySelector\('#sec-digitaltwin'\)/);
  assert.doesNotMatch(CINEMATIC_JS, /cinematic-featured|function wireFeatured|seekFeatured/);
  // scrub (pin 아님)
  assert.match(CINEMATIC_JS, /scrub:/);
  assert.doesNotMatch(CINEMATIC_JS, /pin:\s*true/);
  // immediateRender:false 를 쓰지 않는다 — progress 0 = 흩어진 시작(정적 아님)
  assert.doesNotMatch(CINEMATIC_JS, /immediateRender:\s*false/);
  // 폴백 가드
  assert.match(CINEMATIC_JS, /prefers-reduced-motion: reduce/);
  assert.match(CINEMATIC_JS, /viewportWidth\(\) < 900/);
  assert.match(CINEMATIC_JS, /if \(!gsap\(\) \|\| !ST\(\)\) return false/);
  // CSR 재렌더/리사이즈 안전
  assert.match(CINEMATIC_JS, /function teardown\(\)/);
  assert.match(CINEMATIC_JS, /\.kill\(\)/);
  assert.match(CINEMATIC_JS, /js-cinematic/);
  assert.doesNotMatch(CINEMATIC_JS, /setInterval/);
});

/* ── AI Native: 하나의 파이프라인 canvas (데스크톱 sticky stage / 세로 블록 모바일) ── */

test('AI Native: 하나의 canvas 안 파이프라인 오브젝트 + 5개 세로 블록. scene 교체·pin·타이머 없음', () => {
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
  // Visual Stage — 하나의 넓은 canvas 안에 파이프라인 오브젝트가 전부 존재(자료 → BRAIN → 문제 → FINAL)
  assert.match(html, /<figure class="ai-native-visual" data-ai-visual/);
  assert.match(html, /<div class="ai-stage" data-ai-stage>/);
  assert.match(html, /<div class="ai-canvas" data-ai-canvas>/);
  // 5개 정적 scene 교체 구조는 없다
  assert.doesNotMatch(html, /class="ai-scene" data-ai-scene=/);
  assert.match(html, /class="ai-src" data-ai-src="0">시험지/);
  assert.equal((html.match(/class="ai-src" data-ai-src="/g) || []).length, 4);
  assert.match(html, /class="ai-brain" data-ai-brain><b>BUILD IT<\/b>/);
  assert.match(html, /data-ai-node="twin"/);
  assert.match(html, /data-ai-node="insight"/);
  assert.equal((html.match(/class="ai-qout" data-ai-qout="/g) || []).length, 4);
  assert.match(html, /class="ai-final" data-ai-final><b>FINAL EXAM/);
  assert.match(html, /class="ai-flow-path" data-ai-flow-path/);
  // 텍스트 블록 — 번호 리스트는 유지(모바일 세로 스토리)
  assert.equal((html.match(/class="ai-native-step" data-ai-step="/g) || []).length, 5);
  assert.match(html, /class="ai-native-step-desc">우리 학교 기출·프린트/);
  assert.doesNotMatch(html, /ai-native-visual-frames|data-active-frame|processProgress|watchScene/);

  // desktop = CSS position:sticky (JS pin 아님)
  assert.match(PUBLIC_CSS, /@media \(min-width:900px\)\{[\s\S]*\.ai-native-visual\{display:block;position:sticky/);
  // canvas 핏: 좌표계 660 고정 canvas 를 stage 폭에 맞춰 scale. IO·타이머 없음.
  assert.match(PUBLIC_JS, /function wireAiNative\(\)/);
  assert.match(PUBLIC_JS, /\[data-ai-canvas\]/);
  assert.match(PUBLIC_JS, /w \/ 660/);
  assert.doesNotMatch(PUBLIC_JS.slice(PUBLIC_JS.indexOf('function wireAiNative')), /setTimeout|setInterval|IntersectionObserver/);
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
