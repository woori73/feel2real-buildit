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
  assert.match(PUBLIC_JS, /STORY\.stage\(ai, \{[\s\S]*scenes: 5[\s\S]*sceneSelector: '\.ai-process-step'/);
  // AI Native 가 story 로 pin 되면 storefront_motion 의 1회성 processProgress 는 건너뛴다
  assert.match(PUBLIC_JS, /data-story-staged['"]\)\s*===\s*['"]true['"]/);
  assert.match(PUBLIC_JS, /if \(steps && !staged && steps\.dataset\.motionWired/);
});

test('story CSS 는 전부 html.js-story 스코프 + reduced-motion 예외 + transform/opacity 만', () => {
  // 모든 규칙이 html.js-story 아래에만
  const ruleHeads = STORY_CSS.match(/^[.#a-z][^{@]*\{/gmi) || [];
  ruleHeads.forEach((h) => {
    assert.ok(/html\.js-story/.test(h), `js-story 스코프 밖 규칙: ${h.trim()}`);
  });
  assert.match(STORY_CSS, /@media \(prefers-reduced-motion: reduce\)/);
  assert.match(STORY_CSS, /--story-exit/);
  assert.match(STORY_CSS, /\[data-story-staged\][\s\S]*\[data-scene-state="active"\]/);
  // 레이아웃을 흔드는 속성(top/left/width/height 애니메이션) 없음 — transform/opacity 중심
  assert.doesNotMatch(STORY_CSS, /transition:[^;]*\b(width|height|top|left|margin)\b/);
});

test('공개 템플릿은 academy_story 자산을 GSAP 뒤, academy_public.js 앞에 로드한다', () => {
  assert.match(PUBLIC_HTML, /\/css\/academy_story\.css/);
  const g = PUBLIC_HTML.indexOf('ScrollTrigger.min.js');
  const s = PUBLIC_HTML.indexOf('/js/academy_story.js');
  const p = PUBLIC_HTML.indexOf('/js/academy_public.js');
  assert.ok(g >= 0 && g < s && s < p, `로드 순서: GSAP(${g}) < story(${s}) < public(${p})`);
});
