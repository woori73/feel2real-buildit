const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * Education Storefront — Featured "2학기 영어 내신" = 학교 칩 + 학교별 Digital Twin 패널.
 *
 * 학교 칩은 단일 선택 selector. 선택하면 아래 [data-featured-twin] 패널이 그 학교의 실제
 * /api/analyzes(기존 SSoT/service) 데이터로 바뀐다(배선: academy_public.js wireFeaturedSchoolTwin).
 * lesson_products 의 공감성장영어 highlight 를 학교별 정보로 재사용하지 않고, builderDraft 에도
 * Digital Twin 데이터를 복제하지 않는다.
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

  assert.match(html, /<div class="featured-chips" role="group"[^>]*data-featured-group/);
  assert.match(html, /<button type="button" class="featured-chip" data-featured-school="압구정중" aria-pressed="true">압구정중<\/button>/);
  assert.match(html, /data-featured-school="신구중" aria-pressed="false"/);
  assert.match(html, /data-featured-school="언북중" aria-pressed="false"/);
  assert.equal((html.match(/aria-pressed="true"/g) || []).length, 1, 'aria-pressed="true" 는 정확히 하나');
});

test('공개 렌더: Digital Twin 패널은 비어 있는 컨테이너 + 로딩 안내(academy_public.js 가 fetch 로 채운다)', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-twin" data-reveal-item data-featured-twin aria-live="polite">/);
  assert.match(html, /class="featured-twin-loading">압구정중 출제 분석/);
  // 추상 종이 SVG / featuredLessonIds 대표수업 리스트 / lesson highlight 재사용 없음
  assert.doesNotMatch(html, /featured-paper|featured-stage|featured-programs/);
});

test('공개 렌더: 빈 학교 항목은 칩을 만들지 않는다(빈 pill 방지)', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {
    featuredItem1: '압구정중', featuredItem2: '   ', featuredItem3: '   ', featuredItem4: '   '
  } });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);
  assert.equal((html.match(/<button type="button" class="featured-chip"/g) || []).length, 1, '공백만 있는 항목은 칩을 만들지 않는다');
  assert.doesNotMatch(html, /class="featured-chip"[^>]*><\/button>/, '빈 라벨 칩 없음');
});

test('공개 렌더: 학교 draft 가 전혀 없으면 하드코딩 학교명이 없다', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {} });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);
  assert.doesNotMatch(html, /압구정중|신구중|신사중|언북중/, '하드코딩 학교명 없음');
});

test('빌더 렌더: 칩은 편집 앵커로 남고 selector group/버튼은 공개 전용, 패널은 정적 안내', () => {
  const hooks = Object.fromEntries(bothRenderers()).builder;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-chip"><span class="featured-chip-label" data-builder-draft="featuredItem1"/);
  assert.doesNotMatch(html, /data-featured-group/, '빌더 캔버스에는 선택 group 없음');
  assert.doesNotMatch(html, /<button[^>]*data-featured-school/, '빌더 캔버스에는 선택 버튼 없음');
  // 빌더는 /api/analyzes 를 fetch 하지 않는다 — 정적 안내만
  assert.match(html, /featured-twin--builder/);
  assert.match(html, /<code>\/analyzes<\/code>/);
});

test('academy_public.js: wireFeaturedSchoolTwin 이 render()에 배선되고 /api/analyzes 에서 데이터를 가져온다', () => {
  assert.match(PUBLIC_JS, /function wireFeaturedSchoolTwin\(\)/);
  assert.match(PUBLIC_JS, /wireContentCollections\(\);\s*\n\s*wireAiNative\(\);\s*\n\s*wireFeaturedSchoolTwin\(\);/);
  // 학교별 데이터는 /api/analyzes(기존 service) 에서 — builderDraft 복제 없음, lesson highlight 재사용 없음
  assert.match(PUBLIC_JS, /fetch\('\/api\/analyzes\?schoolName=' \+ encodeURIComponent\(school\)\)/);
  assert.doesNotMatch(PUBLIC_JS, /featuredLessonIds|selectedFeaturedLessons/, 'featuredLessonIds 재사용 없음');
  // 칩 선택은 섹션 전체를 흐리게 하지 않는다 — 패널만 WAAPI 마이크로 전환
  assert.match(PUBLIC_JS, /panel\.animate\(/);
  assert.match(PUBLIC_JS, /prefers-reduced-motion: reduce/);
  // 키보드 화살표 이동, 재렌더 가드
  assert.match(PUBLIC_JS, /ArrowRight' \|\| e\.key === 'ArrowDown'/);
  assert.match(PUBLIC_JS, /group\.dataset\.twinWired === 'true'/);
  // 영어 과목만 필터(방어적)
  assert.match(PUBLIC_JS, /nameEn === 'english'/);
});

test('academy_public.css: 칩 선택 상태/포커스 + Digital Twin 패널 스타일이 정의된다', () => {
  assert.match(PUBLIC_CSS, /\.featured-chip\{[^}]*cursor:pointer/);
  assert.match(PUBLIC_CSS, /\.featured-chip:focus-visible\{[^}]*outline/);
  assert.match(PUBLIC_CSS, /\.featured-chip\[aria-pressed="true"\]\{[^}]*background:var\(--ink\)/);
  assert.match(PUBLIC_CSS, /\.featured-twin\{[^}]*border:1px solid var\(--line\)/);
  assert.match(PUBLIC_CSS, /\.featured-twin-school\{[^}]*font-weight:900/);
  assert.doesNotMatch(PUBLIC_CSS, /featured-paper|\.featured-stage\b/, '추상 종이 SVG / 구 Stage 스타일 제거');
});

test('캐시버스팅: 변경된 storefront 자산 버전이 함께 올라간다', () => {
  assert.match(PUBLIC_HTML, /academy_reveal\.(js|css)\?v=20260831-7/);
  assert.match(PUBLIC_HTML, /academy_collection\.(js|css)\?v=20260831-7/);
  assert.match(PUBLIC_HTML, /academy_sections\.js\?v=20260831-7/);
  assert.match(PUBLIC_HTML, /academy_public\.js\?v=20260831-7/);
  assert.match(PUBLIC_HTML, /academy_public\.css\?v=20260831-7/);
  assert.match(BUILDER_HTML, /academy_sections\.js\?v=20260831-7/);
  // 삭제된 자산이 템플릿에 남아 있지 않다
  assert.doesNotMatch(PUBLIC_HTML, /academy_storefront_motion|academy_story|academy_scene|gsap|ScrollTrigger/);
});

test('sections.js featured 영역에 학교/연도/시즌 하드코딩이 없다', () => {
  const featured = SECTIONS.slice(
    SECTIONS.indexOf('sec-featured : Featured'),
    SECTIONS.indexOf('sec-digitaltwin : AI Native'));
  assert.doesNotMatch(featured, /압구정|신구중|신사중|언북중|202\d|2학기|1학기|내신/,
    '코드에 학교/연도/시즌/과목 문자열이 있으면 안 된다 — 전부 builderDraft 입력값');
});
