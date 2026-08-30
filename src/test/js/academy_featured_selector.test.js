const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * Education Storefront V2-6 — Featured school selector.
 *
 * 학교 칩은 단순 장식 pill 이 아니라 단일 선택 selector 다. 선택하면 중앙 Featured Stage
 * 의 정보 상태(시트 상단 학교명 · "지금 보는 학교" 라인 · CTA 접두어)가 실제로 바뀐다.
 * 학교별 실데이터는 만들지 않는다 — 공통 프로세스(Featured 프로그램) 안에서 "선택된 학교"
 * 만 반영한다. 상태 전환은 요소 자신의 attribute/textContent 로만 한다(조상 attr + CSS
 * transition 은 일부 Chromium 버그 — V2-5 #86).
 */

const read = (rel) => fs.readFileSync(path.resolve(__dirname, rel), 'utf8');
const SECTIONS = read('../../main/resources/static/js/academy_sections.js');
const PUBLIC_JS = read('../../main/resources/static/js/academy_public.js');
const PUBLIC_CSS = read('../../main/resources/static/css/academy_public.css');
const PUBLIC_HTML = read('../../main/resources/templates/public/academy_public.html');
const BUILDER_HTML = read('../../main/resources/templates/tutor/academy_builder.html');

function fixture(pageSettings) {
  return academyFixture({ name: '테스트영어교실', pageSettings });
}

const SCHOOLS = {
  sectionVisibility: { 'sec-featured': true },
  builderDraft: {
    featuredTitle: '우리 학교 2학기 내신\n집중 대비',
    featuredItem1: '압구정중',
    featuredItem2: '신구중',
    featuredItem3: '신사중',
    featuredItem4: '언북중',
    featuredCtaLabel: '상담 신청하기'
  }
};

test('공개 렌더: 학교 칩이 단일 선택 버튼이고 첫 학교만 aria-pressed="true"', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-items" role="group"[^>]*data-featured-group/);
  assert.match(html, /<button type="button" class="featured-item[^"]*" data-featured-school="압구정중" aria-pressed="true"/);
  assert.match(html, /data-featured-school="신구중" aria-pressed="false"/);
  assert.match(html, /data-featured-school="언북중" aria-pressed="false"/);

  const pressedTrue = html.match(/aria-pressed="true"/g) || [];
  assert.equal(pressedTrue.length, 1, 'aria-pressed="true" 는 정확히 하나');
});

test('공개 렌더: 중앙 Featured Stage 가 선택 학교/제목을 반영하는 앵커를 갖는다', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-stage[^"]*" data-featured-stage/);
  assert.match(html, /<text class="featured-paper-school"[^>]*data-featured-stage-school>압구정중<\/text>/);
  // 캡션은 교육자가 입력한 featuredTitle 첫 줄(코드에 학교/시즌/과목 하드코딩 없음)
  assert.match(html, /<span class="featured-stage-caption">우리 학교 2학기 내신 집중 대비<\/span>/);
  // "지금 보는 학교" 라인은 live region, div(=#publicView p margin reset 회피)
  assert.match(html, /<div class="featured-selected[^"]*" aria-live="polite">지금 보는 학교 · <strong data-featured-selected>압구정중<\/strong><\/div>/);
});

test('공개 렌더: 빈 학교 항목은 칩도 무대도 만들지 않는다(빈 pill 방지)', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {
    featuredItem1: '압구정중', featuredItem2: '   ', featuredItem3: '   ', featuredItem4: '   '
  } });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);
  const chips = html.match(/<button type="button" class="featured-item/g) || [];
  assert.equal(chips.length, 1, '공백만 있는 항목은 칩을 만들지 않는다');
  assert.doesNotMatch(html, /<span class="featured-item-label"><\/span>/, '빈 라벨 칩 없음');
  assert.match(html, /data-featured-stage-school>압구정중</);
});

test('공개 렌더: 학교 draft 가 전혀 없으면 stage/selector 를 만들지 않는다', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {} });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);
  // 기본 fallback 항목("핵심 포인트 N")은 유효 라벨이라 칩은 생기지만, 하드코딩 학교/연도는 없어야
  assert.doesNotMatch(html, /압구정중|신구중|신사중|언북중/, '하드코딩 학교명 없음');
});

test('빌더 렌더: 칩은 편집 앵커로 남고 selector group/버튼은 공개 전용', () => {
  const hooks = Object.fromEntries(bothRenderers()).builder;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-item f2r-reveal"><span class="featured-item-label" data-builder-draft="featuredItem1"/);
  assert.doesNotMatch(html, /data-featured-group/, '빌더 캔버스에는 선택 group 없음');
  assert.doesNotMatch(html, /<button[^>]*data-featured-school/, '빌더 캔버스에는 선택 버튼 없음');
  // 빌더도 무대는 정적으로 보여준다(editor preview == public 방향)
  assert.match(html, /data-featured-stage-school>압구정중</);
});

test('academy_public.js: wireFeaturedSelector 가 render()에 배선되고 안전한 상태 전환만 쓴다', () => {
  assert.match(PUBLIC_JS, /function wireFeaturedSelector\(\)/);
  assert.match(PUBLIC_JS, /wireContentCollections\(\);\s*\n\s*wireFeaturedSelector\(\);/);
  // 선택 시 요소 자신의 textContent 를 갈아끼운다(조상 attr + CSS transition 회피)
  assert.match(PUBLIC_JS, /\[data-featured-stage-school\]/);
  assert.match(PUBLIC_JS, /\[data-featured-selected\]/);
  assert.match(PUBLIC_JS, /aria-pressed['"], b === btn \? 'true' : 'false'/);
  // WAAPI 페이드 + reduced-motion 가드
  assert.match(PUBLIC_JS, /stage\.animate\(\[\{ opacity: 0\.35 \}/);
  assert.match(PUBLIC_JS, /prefers-reduced-motion: reduce/);
  // 키보드: 화살표로 이동
  assert.match(PUBLIC_JS, /ArrowRight' \|\| e\.key === 'ArrowDown'/);
  // 재렌더 가드
  assert.match(PUBLIC_JS, /group\.dataset\.selectorWired === 'true'/);
});

test('academy_public.css: 선택 상태/포커스/무대 스타일이 정의된다', () => {
  assert.match(PUBLIC_CSS, /\.featured-item\{[^}]*cursor:pointer/);
  assert.match(PUBLIC_CSS, /\.featured-item:focus-visible\{[^}]*outline/);
  assert.match(PUBLIC_CSS, /\.featured-item\[aria-pressed="true"\]\{[^}]*background:var\(--ink\)/);
  assert.match(PUBLIC_CSS, /\.featured-item\[aria-pressed="true"\] \.featured-item-label\{[^}]*color:#fff/);
  assert.match(PUBLIC_CSS, /\.featured-stage\{[^}]*display:flex/);
  assert.match(PUBLIC_CSS, /\.featured-paper-band\{fill:var\(--ultramarine\)/);
  assert.match(PUBLIC_CSS, /\.featured-selected strong\{color:var\(--ultramarine\)/);
});

test('캐시버스팅: 변경된 storefront 자산 버전이 함께 올라간다', () => {
  // V2-10: Vertical Scene Flow — academy_storefront_motion.js + academy_sections.js
  //        + academy_public.js + 신규 academy_scene.css 변경
  assert.match(PUBLIC_HTML, /academy_storefront_motion\.js\?v=20260831-4/);
  assert.match(PUBLIC_HTML, /academy_scene\.css\?v=20260831-4/);
  assert.match(PUBLIC_HTML, /academy_sections\.js\?v=20260831-4/);
  assert.match(PUBLIC_HTML, /academy_public\.js\?v=20260831-4/);
  assert.match(PUBLIC_HTML, /academy_public\.css\?v=20260830-4/);
  assert.match(BUILDER_HTML, /academy_sections\.js\?v=20260831-4/);
});

test('sections.js featured 영역에 학교/연도/시즌 하드코딩이 없다', () => {
  const featured = SECTIONS.slice(
    SECTIONS.indexOf('sec-featured : Featured'),
    SECTIONS.indexOf('sec-digitaltwin : AI Native'));
  assert.doesNotMatch(featured, /압구정|신구중|신사중|언북중|202\d|2학기|1학기|내신/,
    '코드에 학교/연도/시즌/과목 문자열이 있으면 안 된다 — 전부 builderDraft 입력값');
});
