const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SRC = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_story.js'), 'utf8');
const PUBLIC_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_public.js'), 'utf8');
const STORY_CSS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_story.css'), 'utf8');
const PUBLIC_HTML = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/public/academy_public.html'), 'utf8');

/** GSAP/ScrollTrigger 없는 스텁 window 에서 로드 → no-op 이어야 한다. */
function loadStory(extra) {
  const el = () => ({
    _attrs: {}, style: { setProperty(k, v) { this[k] = v; } },
    setAttribute(k, v) { this._attrs[k] = String(v); },
    removeAttribute(k) { delete this._attrs[k]; },
    getAttribute(k) { return this._attrs[k] == null ? null : this._attrs[k]; },
    querySelectorAll() { return []; }
  });
  const htmlEl = { classList: { _s: new Set(), add(c) { this._s.add(c); }, contains(c) { return this._s.has(c); } } };
  const win = Object.assign({
    document: { documentElement: htmlEl },
    matchMedia: () => ({ matches: false }),
    innerWidth: 1280
  }, extra || {});
  const ctx = vm.createContext({ window: win, globalThis: win });
  vm.runInContext(SRC, ctx, { filename: 'academy_story.js' });
  return { api: win.F2RStorefrontStory, htmlEl, el };
}

test('F2RStorefrontStory 를 노출하고 stage/exit/refresh 를 갖는다', () => {
  const { api } = loadStory();
  assert.ok(api, 'F2RStorefrontStory');
  ['stage', 'exit', 'refresh'].forEach((m) => assert.equal(typeof api[m], 'function', m));
});

test('GSAP 미로드 시 stage()는 no-op 이고 js-story 도 안 붙는다 (정적 폴백)', () => {
  const { api, htmlEl, el } = loadStory();
  const section = el();
  api.stage(section, { scenes: 5, id: 'x' });
  assert.equal(htmlEl.classList.contains('js-story'), false, 'js-story 미부착');
  assert.equal(section.getAttribute('data-story-staged'), null, 'pin 안 됨');
  // 정적 폴백: 마지막 장면 상태
  assert.equal(section.getAttribute('data-story-scene'), '4', '정적 폴백 = 마지막 장면');
});

test('reduced-motion / 모바일 폭에서도 stage()는 pin 하지 않는다', () => {
  const reduced = loadStory({ gsap: {}, ScrollTrigger: {}, matchMedia: () => ({ matches: true }) });
  reduced.api.stage(reduced.el(), { scenes: 3, id: 'r' });
  assert.equal(reduced.htmlEl.classList.contains('js-story'), false, 'reduced-motion → no-op');

  const mobile = loadStory({ gsap: {}, ScrollTrigger: {}, innerWidth: 480 });
  mobile.api.stage(mobile.el(), { scenes: 3, id: 'm' });
  assert.equal(mobile.htmlEl.classList.contains('js-story'), false, '모바일 폭 → no-op');
});

test('academy_public.js 는 render()에서 applyReveal → wireStory → wireStorefrontMotion 순으로 배선한다', () => {
  assert.match(PUBLIC_JS, /applyReveal\(\);\s*\n\s*wireStory\(\);\s*\n\s*wireStorefrontMotion\(\);/);
  // wireStory: hero exit + AI Native 5-scene stage
  assert.match(PUBLIC_JS, /function wireStory\(\)/);
  assert.match(PUBLIC_JS, /STORY\.exit\(hero/);
  assert.match(PUBLIC_JS, /STORY\.stage\(ai, \{[\s\S]*scenes: 5[\s\S]*sceneSelector: '\.ai-process-step'[\s\S]*layerSelector: '\.ai-stage-layer'/);
  // AI Native 가 story 로 pin 되면 storefront_motion 의 1회성 processProgress 는 건너뛴다
  assert.match(PUBLIC_JS, /data-story-staged['"]\)\s*===\s*['"]true['"]/);
  assert.match(PUBLIC_JS, /if \(steps && !staged && steps\.dataset\.motionWired/);
});

test('story CSS 는 동작 규칙을 html.js-story 로 게이트한다 (미로드 시 콘텐츠 안 숨김)', () => {
  // "무언가를 숨기거나 옮기는" 규칙은 전부 html.js-story 스코프 아래에만.
  // 예외: 순수 SVG 도형 프리젠테이션(.ai-doc-* / .ai-mark / .ai-node 등), stage 기본 숨김
  // (.ai-process-stage{display:none} — js-story 가 다시 켠다), 모바일 안전 @media.
  const ruleHeads = STORY_CSS.match(/(?:^|\n)([.#][^{@]*)\{/g) || [];
  ruleHeads.forEach((h) => {
    const head = h.replace(/[\s\n]+/g, ' ').trim();
    if (/html\.js-story/.test(head)) return;
    // 예외: 순수 SVG 도형/골격 프리젠테이션 + stage 기본 숨김(js-story 가 다시 켠다).
    if (/^\.ai-(doc-|mark|scan|node|link|merge|block|badge|check|stage-)/.test(head)) return;
    if (/^\.ai-process-stage \{/.test(head)) return;
    assert.fail(`js-story 스코프 밖 동작 규칙: ${head}`);
  });
  assert.match(STORY_CSS, /\.ai-process-stage \{ display: none; \}/, 'stage 는 기본 숨김');
  assert.match(STORY_CSS, /html\.js-story #sec-digitaltwin\[data-story-staged\] \.ai-process-stage \{ display: block/, 'js-story + staged 에서만 stage 노출');
  assert.match(STORY_CSS, /@media \(prefers-reduced-motion: reduce\)/);
  assert.match(STORY_CSS, /--story-exit/);
  assert.match(STORY_CSS, /\[data-story-staged\][\s\S]*\[data-scene-state="active"\]/);
  // 레이아웃을 흔드는 속성(top/left/height 애니메이션) 없음 — transform/opacity 중심
  assert.doesNotMatch(STORY_CSS, /transition:[^;]*\b(width|height|top|left)\b/);
});

test('장면 상태는 조상 attr 이 아니라 각 대상의 data-scene-state 로 준다 (Chromium transition 버그 회피)', () => {
  // academy_story.js: paintScenes 가 sceneSelector + layerSelector 요소에 직접 data-scene-state 부착
  assert.match(SRC, /function paintScenes\(section, els, layers, idx\)/);
  assert.match(SRC, /layers\[j\]\.setAttribute\('data-scene-state', 'past'\)/);
  assert.match(SRC, /layers\[j\]\.setAttribute\('data-scene-state', 'active'\)/);
  assert.match(SRC, /layers\[j\]\.removeAttribute\('data-scene-state'\)/);
  // CSS: 레이어 opacity 는 레이어 자신의 data-scene-state 로 (조상 [data-story-scene] 아님)
  assert.match(STORY_CSS, /\.ai-stage-layer\[data-scene-state="active"\] \{\s*opacity: 1/);
  assert.doesNotMatch(STORY_CSS, /\[data-story-scene="\d"\] \.ai-stage-layer\[data-scene=/,
    '조상 [data-story-scene] + transition 되는 레이어 규칙이 남아 있으면 안 된다');
});

test('AI Native stage 는 이미지 자산 없이 SVG 도형만 쓰고 5장면 오버레이를 갖는다', () => {
  const sections = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/static/js/academy_sections.js'), 'utf8');
  assert.match(sections, /function aiProcessStageHtml\(\)/);
  assert.match(sections, /class="ai-process-stage" data-ai-stage aria-hidden="true"/);
  assert.match(sections, /\$\{aiProcessStageHtml\(\)\}\s*\n\s*<ol class="ai-process-steps">/, 'stage 는 단계 목록 앞에');
  const start = sections.indexOf('function aiProcessStageHtml');
  const body = sections.slice(start, sections.indexOf('function renderAiProcess', start));
  ['data-scene="2"', 'data-scene="3"', 'data-scene="4"', 'data-scene="5"'].forEach((s) =>
    assert.match(body, new RegExp(s), `장면 레이어 ${s}`));
  assert.doesNotMatch(body, /<img|url\(|\.png|\.jpe?g/, 'stage 에 이미지 자산 없음 (SVG 도형만)');
  assert.match(body, /<svg class="ai-stage-doc"/, 'SVG 문서 도형');
});

test('공개 템플릿은 academy_story 자산을 GSAP 뒤, academy_public.js 앞에 로드한다', () => {
  assert.match(PUBLIC_HTML, /\/css\/academy_story\.css/);
  const g = PUBLIC_HTML.indexOf('ScrollTrigger.min.js');
  const s = PUBLIC_HTML.indexOf('/js/academy_story.js');
  const p = PUBLIC_HTML.indexOf('/js/academy_public.js');
  assert.ok(g >= 0 && g < s && s < p, `로드 순서: GSAP(${g}) < story(${s}) < public(${p})`);
});
