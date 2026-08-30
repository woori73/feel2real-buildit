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
  // wireStory: hero exit + AI Native 5-scene stage (V2-11: layerSelector 제거)
  assert.match(PUBLIC_JS, /function wireStory\(\)/);
  assert.match(PUBLIC_JS, /STORY\.exit\(hero/);
  assert.match(PUBLIC_JS, /STORY\.stage\(ai, \{[\s\S]*scenes: 5[\s\S]*sceneSelector: '\.ai-process-step'/);
  assert.doesNotMatch(PUBLIC_JS, /layerSelector/, 'V2-11: 추상 레이어 오버레이 제거');
  // AI Native 가 story 로 pin 되면 storefront_motion 의 1회성 processProgress 는 건너뛴다
  assert.match(PUBLIC_JS, /data-story-staged['"]\)\s*===\s*['"]true['"]/);
  assert.match(PUBLIC_JS, /if \(steps && !staged && steps\.dataset\.motionWired/);
  // V2-11: 모바일 진행은 Visual Stage([data-ai-stage])도 함께 바꾼다
  assert.match(PUBLIC_JS, /stage: root\.querySelector\('#sec-digitaltwin \[data-ai-stage\]'\)/);
});

test('story CSS 는 동작 규칙을 html.js-story 로 게이트한다 (미로드 시 콘텐츠 안 숨김)', () => {
  // "무언가를 숨기거나 옮기는" 규칙은 전부 html.js-story 스코프 아래에만.
  const ruleHeads = STORY_CSS.match(/(?:^|\n)([.#][^{@]*)\{/g) || [];
  ruleHeads.forEach((h) => {
    const head = h.replace(/[\s\n]+/g, ' ').trim();
    if (/html\.js-story/.test(head)) return;
    assert.fail(`js-story 스코프 밖 동작 규칙: ${head}`);
  });
  // V2-11: Visual Stage 는 기본으로 보인다(academy_public.css). story.css 는 pin 일 때
  // 활성 프레임을 스크롤 장면으로만 정한다 — display:none 게이트 없음.
  assert.doesNotMatch(STORY_CSS, /\.ai-process-stage \{ display: none/, 'stage 를 숨기지 않는다');
  assert.match(STORY_CSS, /html\.js-story #sec-digitaltwin\[data-story-staged\]\[data-story-scene="0"\] \.ai-stage-frame\[data-frame="0"\]/);
  assert.match(STORY_CSS, /@media \(prefers-reduced-motion: reduce\)/);
  assert.match(STORY_CSS, /--story-exit/);
  assert.match(STORY_CSS, /\[data-story-staged\][\s\S]*\[data-scene-state="active"\]/);
  // 레이아웃을 흔드는 속성(top/left/width 애니메이션) 없음 — transform/opacity 중심
  assert.doesNotMatch(STORY_CSS, /transition:[^;]*\b(width|top|left)\b/);
});

test('장면 상태는 조상 attr 이 아니라 각 대상의 data-scene-state 로 준다 (Chromium transition 버그 회피)', () => {
  assert.match(SRC, /function paintScenes\(section, els, layers, idx\)/);
  assert.match(SRC, /els\[i\]\.setAttribute\('data-scene-state'/);
  // 단계 목록 강조는 각 .ai-process-step 자신의 data-scene-state 로
  assert.match(STORY_CSS, /\.ai-process-step\[data-scene-state="active"\]/);
});

test('AI Native Visual Stage 는 이미지 자산 없이 단계별 아이콘 + 이름을 가진다 (모바일에서도 표시)', () => {
  const sections = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/static/js/academy_sections.js'), 'utf8');
  assert.match(sections, /function aiProcessStageHtml\(steps\)/);
  assert.match(sections, /class="ai-process-stage" data-ai-stage aria-hidden="true"/);
  assert.match(sections, /\$\{aiProcessStageHtml\(aiProcessSteps\(view\)\)\}\s*\n\s*<ol class="ai-process-steps">/, 'stage 는 단계 목록 앞에');
  const start = sections.indexOf('const AI_STAGE_GLYPHS');
  const body = sections.slice(start, sections.indexOf('function renderAiProcess', start));
  assert.doesNotMatch(body, /<img|url\(|\.png|\.jpe?g/, 'stage 에 이미지 자산 없음 (SVG 도형만)');
  assert.match(body, /data-frame="\$\{i\}"/, '프레임 인덱스');
  assert.match(body, /class="ai-stage-frame-name"><b>\$\{i \+ 1\}<\/b>/, '단계 번호 + 이름');
  // 모바일에서 stage 를 display:none 으로 제거하지 않는다
  const publicCss = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/academy_public.css'), 'utf8');
  assert.doesNotMatch(publicCss, /\.ai-process-stage\{[^}]*display:none/);
  assert.match(publicCss, /\.ai-process-stage\[data-active-frame="4"\] \.ai-stage-frame\[data-frame="4"\]/);
});

test('공개 템플릿은 academy_story 자산을 GSAP 뒤, academy_public.js 앞에 로드한다', () => {
  assert.match(PUBLIC_HTML, /\/css\/academy_story\.css/);
  const g = PUBLIC_HTML.indexOf('ScrollTrigger.min.js');
  const s = PUBLIC_HTML.indexOf('/js/academy_story.js');
  const p = PUBLIC_HTML.indexOf('/js/academy_public.js');
  assert.ok(g >= 0 && g < s && s < p, `로드 순서: GSAP(${g}) < story(${s}) < public(${p})`);
});
