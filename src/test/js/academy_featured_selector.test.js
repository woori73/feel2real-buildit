const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * Education Storefront — Featured "2학기 영어 내신" = 학교 칩 + School Digital Twin Horizontal Showcase.
 *
 * 학교 칩은 단일 선택 selector. 선택하면 Showcase 가 그 학교 scene 으로 이동한다. 각 scene body 는
 * 그 학교의 실제 /api/analyzes(기존 SSoT/service) 데이터로 채워진다(배선: academy_public.js
 * wireFeaturedShowcase). 데스크톱 cinematic 은 academy_cinematic.js(F2RCinematic)가 세로 스크롤에
 * 맞춰 가로 이동을 소유한다. lesson_products highlight 재사용 없음, builderDraft 복제 없음.
 */

const read = (rel) => fs.readFileSync(path.resolve(__dirname, rel), 'utf8');
const SECTIONS = read('../../main/resources/static/js/academy_sections.js');
const PUBLIC_JS = read('../../main/resources/static/js/academy_public.js');
const PUBLIC_CSS = read('../../main/resources/static/css/academy_public.css');
const CINEMATIC_CSS = read('../../main/resources/static/css/academy_cinematic.css');
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
  assert.match(html, /<button type="button" class="featured-chip" data-featured-school="압구정중" data-featured-index="0" aria-pressed="true">압구정중<\/button>/);
  assert.match(html, /data-featured-school="신구중" data-featured-index="1" aria-pressed="false"/);
  assert.match(html, /data-featured-school="언북중" data-featured-index="3" aria-pressed="false"/);
  assert.equal((html.match(/aria-pressed="true"/g) || []).length, 1, 'aria-pressed="true" 는 정확히 하나');
});

test('공개 렌더: Digital Twin Showcase 는 학교별 빈 scene + 로딩 안내(academy_public.js 가 fetch 로 채운다)', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="dt-showcase" data-reveal-item data-dt-showcase data-featured-twin data-active="0">/);
  assert.match(html, /<ol class="dt-track" data-dt-track role="list">/);
  // 4개 학교 scene
  assert.equal((html.match(/<li class="dt-scene" data-dt-scene="/g) || []).length, 4);
  assert.match(html, /<li class="dt-scene" data-dt-scene="0" data-featured-school="압구정중" data-active>/);
  assert.match(html, /<h3 class="dt-scene-school">신사중<\/h3>/);
  assert.match(html, /data-dt-body data-dt-school="언북중"[^>]*>\s*<p class="dt-scene-loading">언북중 출제 분석을 불러오는 중…<\/p>/);
  // 진행 카운터 + prev/next
  assert.match(html, /<span data-dt-current>01<\/span>.*<span data-dt-total>04<\/span>/s);
  assert.match(html, /data-dt-nav-dir="prev"[^>]*disabled/);
  assert.match(html, /data-dt-nav-dir="next"/);
  // 추상 종이 SVG / featuredLessonIds 대표수업 리스트 / lesson highlight 재사용 없음
  assert.doesNotMatch(html, /featured-paper|featured-stage|featured-programs|featured-twin-loading/);
});

test('공개 렌더: 빈 학교 항목은 칩/scene 을 만들지 않는다(빈 pill 방지)', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {
    featuredItem1: '압구정중', featuredItem2: '   ', featuredItem3: '   ', featuredItem4: '   '
  } });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);
  assert.equal((html.match(/<button type="button" class="featured-chip"/g) || []).length, 1, '공백만 있는 항목은 칩을 만들지 않는다');
  assert.equal((html.match(/<li class="dt-scene" data-dt-scene="/g) || []).length, 1, '공백만 있는 항목은 scene 을 만들지 않는다');
});

test('공개 렌더: builderDraft 기본값에는 하드코딩 학교명이 없다', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {} });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);
  assert.doesNotMatch(html, /압구정중|신구중|신사중|언북중/, '하드코딩 학교명 없음');
});

test('공개 렌더: 학교가 하나도 없으면(공백만) 빈 안내로 떨어진다', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {
    featuredItem1: '   ', featuredItem2: '   ', featuredItem3: '   ', featuredItem4: '   '
  } });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);
  assert.match(html, /class="dt-showcase dt-showcase--empty" data-featured-twin/);
  assert.doesNotMatch(html, /data-dt-track|data-dt-scene/);
});

test('빌더 렌더: 칩은 편집 앵커로 남고 selector group/showcase 는 공개 전용, 정적 안내만', () => {
  const hooks = Object.fromEntries(bothRenderers()).builder;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-chip"><span class="featured-chip-label" data-builder-draft="featuredItem1"/);
  assert.doesNotMatch(html, /data-featured-group/, '빌더 캔버스에는 선택 group 없음');
  assert.doesNotMatch(html, /<button[^>]*data-featured-school/, '빌더 캔버스에는 선택 버튼 없음');
  assert.doesNotMatch(html, /data-dt-track|data-dt-scene/, '빌더 캔버스에는 가로 track 없음');
  // 빌더는 /api/analyzes 를 fetch 하지 않는다 — 정적 안내만
  assert.match(html, /dt-showcase--builder/);
  assert.match(html, /<code>\/analyzes<\/code>/);
});

test('academy_public.js: wireFeaturedShowcase 가 render()에 배선되고 /api/analyzes 에서 데이터를 가져온다', () => {
  assert.match(PUBLIC_JS, /function wireFeaturedShowcase\(\)/);
  assert.match(PUBLIC_JS, /wireContentCollections\(\);\s*\n\s*wireAiNative\(\);\s*\n\s*wireFeaturedShowcase\(\);/);
  // 학교별 데이터는 /api/analyzes(기존 service) 에서 — builderDraft 복제 없음, lesson highlight 재사용 없음
  assert.match(PUBLIC_JS, /fetch\('\/api\/analyzes\?schoolName=' \+ encodeURIComponent\(school\)\)/);
  assert.doesNotMatch(PUBLIC_JS, /featuredLessonIds|selectedFeaturedLessons/, 'featuredLessonIds 재사용 없음');
  // scene body 만 짧게 마이크로 전환 — 섹션 전체를 흐리게 하지 않는다
  assert.match(PUBLIC_JS, /body\.animate\(/);
  assert.match(PUBLIC_JS, /prefers-reduced-motion: reduce/);
  // 키보드 화살표 이동, 재렌더 가드
  assert.match(PUBLIC_JS, /ArrowRight' \|\| e\.key === 'ArrowDown'/);
  assert.match(PUBLIC_JS, /showcase\.dataset\.showcaseWired === 'true'/);
  // 영어 과목만 필터(방어적)
  assert.match(PUBLIC_JS, /nameEn === 'english'/);
});

test('cinematic 은 academy_cinematic.js 한 곳만 — academy_public.js 는 GSAP 을 직접 참조하지 않는다', () => {
  assert.match(PUBLIC_JS, /function wireCinematic\(\)/);
  assert.match(PUBLIC_JS, /window\.F2RCinematic/);
  // GSAP API 를 직접 호출하지 않는다(주석 언급은 허용). 모든 timeline/ScrollTrigger 는 academy_cinematic.js.
  assert.doesNotMatch(PUBLIC_JS, /gsap\.(to|from|set|timeline|registerPlugin)|ScrollTrigger\.(create|getById|refresh)/,
    'academy_public.js 안에 GSAP API 호출 없음');
});

test('academy CSS: 칩 선택 상태/포커스 + Digital Twin scene 카드 스타일이 정의된다', () => {
  assert.match(PUBLIC_CSS, /\.featured-chip\{[^}]*cursor:pointer/);
  assert.match(PUBLIC_CSS, /\.featured-chip:focus-visible\{[^}]*outline/);
  assert.match(PUBLIC_CSS, /\.featured-chip\[aria-pressed="true"\]\{[^}]*background:var\(--ink\)/);
  assert.match(CINEMATIC_CSS, /\.dt-scene-card\{[\s\S]*border:1px solid var\(--line\)/);
  assert.match(CINEMATIC_CSS, /\.dt-scene-school\{[^}]*font-weight:900/);
  assert.match(CINEMATIC_CSS, /html\.js-cinematic #sec-featured \.featured-inner\{[\s\S]*position:sticky/);
  assert.doesNotMatch(PUBLIC_CSS, /featured-paper|\.featured-stage\b/, '추상 종이 SVG / 구 Stage 스타일 제거');
});

test('캐시버스팅: 변경된 storefront 자산 버전이 함께 올라간다', () => {
  assert.match(PUBLIC_HTML, /academy_reveal\.(js|css)\?v=20260901-1/);
  assert.match(PUBLIC_HTML, /academy_collection\.(js|css)\?v=20260901-1/);
  assert.match(PUBLIC_HTML, /academy_cinematic\.(js|css)\?v=20260901-1/);
  assert.match(PUBLIC_HTML, /academy_sections\.js\?v=20260901-1/);
  assert.match(PUBLIC_HTML, /academy_public\.js\?v=20260901-1/);
  assert.match(PUBLIC_HTML, /academy_public\.css\?v=20260901-1/);
  assert.match(BUILDER_HTML, /academy_sections\.js\?v=20260901-1/);
  // Phase 4 에서 삭제된 자산은 여전히 템플릿에 없다(Phase 5 는 academy_cinematic.js 로 GSAP 재도입)
  assert.doesNotMatch(PUBLIC_HTML, /academy_storefront_motion|academy_story\.js|academy_story\.css|academy_scene/);
  // GSAP / ScrollTrigger 는 cinematic 2섹션 전용으로만 로드된다
  assert.match(PUBLIC_HTML, /gsap\.min\.js/);
  assert.match(PUBLIC_HTML, /ScrollTrigger\.min\.js/);
});

test('sections.js featured 영역에 학교/연도/시즌 하드코딩이 없다', () => {
  const featured = SECTIONS.slice(
    SECTIONS.indexOf('sec-featured : Featured'),
    SECTIONS.indexOf('sec-digitaltwin : AI Native'));
  assert.doesNotMatch(featured, /압구정|신구중|신사중|언북중|202\d|2학기|1학기|내신/,
    '코드에 학교/연도/시즌/과목 문자열이 있으면 안 된다 — 전부 builderDraft 입력값');
});
