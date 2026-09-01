const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * Phase 5.2 — 학교별 실제 출제자 Digital Twin 은 「2학기 영어 내신」 섹션(sec-featured)에만 있다.
 *
 *  - sec-featured: eyebrow → 제목 → 설명 → 학교 칩(단일 선택 버튼) → .featured-twin 패널 → CTA.
 *    칩 선택 시 그 학교의 실제 /api/analyzes 데이터로 패널이 바뀐다(wireFeaturedSchoolTwin).
 *  - 대표수업(sec-lessons)의 programCardHtml 은 학교 셀렉터/Digital Twin 패널을 만들지 않는다
 *    (Phase 5.1 회귀 제거). 카드 이름은 그냥 이름 줄로 렌더.
 *  - 가짜 summary 없음, /api/analyzes 실데이터만, builderDraft 복제 없음.
 */

const read = (rel) => fs.readFileSync(path.resolve(__dirname, rel), 'utf8');
const SECTIONS = read('../../main/resources/static/js/academy_sections.js');
const PUBLIC_JS = read('../../main/resources/static/js/academy_public.js');
const PUBLIC_CSS = read('../../main/resources/static/css/academy_public.css');
const COLLECTION_CSS = read('../../main/resources/static/css/academy_collection.css');
const PUBLIC_HTML = read('../../main/resources/templates/public/academy_public.html');
const BUILDER_HTML = read('../../main/resources/templates/tutor/academy_builder.html');

function fixture(pageSettings) {
  return academyFixture({ name: '테스트영어교실', pageSettings });
}

const SCHOOLS = {
  sectionVisibility: { 'sec-featured': true },
  builderDraft: {
    featuredTitle: '2학기 영어 내신 Build It',
    featuredItem1: '압구정중', featuredItem2: '신구중', featuredItem3: '신사중', featuredItem4: '언북중',
    featuredCtaLabel: '2학기 내신 상담 신청'
  }
};

/* ── sec-featured: 학교 칩 셀렉터 + Digital Twin 패널 ────────────────────── */

test('공개 렌더: 학교 칩이 단일 선택 버튼이고 첫 학교만 aria-pressed="true"', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-chips" role="group"[^>]*data-featured-group/);
  assert.match(html, /<button type="button" class="featured-chip" data-featured-school="압구정중" aria-pressed="true">압구정중<\/button>/);
  assert.match(html, /data-featured-school="신구중" aria-pressed="false"/);
  assert.match(html, /data-featured-school="언북중" aria-pressed="false"/);
  assert.equal((html.match(/aria-pressed="true"/g) || []).length, 1);
});

test('공개 렌더: Digital Twin 패널은 비어 있는 컨테이너 + 로딩 안내(academy_public.js 가 fetch 로 채운다)', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-twin" data-reveal-item data-featured-twin aria-live="polite">/);
  assert.match(html, /class="featured-twin-loading">압구정중 출제 분석/);
  // Phase 5 의 가로 showcase / Phase 5.1 의 program-card-twin 흔적 없음
  assert.doesNotMatch(html, /dt-showcase|dt-scene|data-dt-track|f2r-program-card-schools|data-program-twin/);
});

test('공개 렌더: 빈 학교 항목은 칩을 만들지 않는다(빈 pill 방지)', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {
    featuredItem1: '압구정중', featuredItem2: '   ', featuredItem3: '   ', featuredItem4: '   '
  } });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);
  assert.equal((html.match(/<button type="button" class="featured-chip"/g) || []).length, 1);
});

test('빌더 렌더: 칩은 편집 앵커, selector group/버튼은 공개 전용, 패널은 정적 안내', () => {
  const hooks = Object.fromEntries(bothRenderers()).builder;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-chip"><span class="featured-chip-label" data-builder-draft="featuredItem1"/);
  assert.doesNotMatch(html, /data-featured-group|<button[^>]*data-featured-school/);
  assert.match(html, /featured-twin--builder/);
  assert.match(html, /<code>\/analyzes<\/code>/);
});

test('academy_public.js: wireFeaturedSchoolTwin 이 render()에 배선되고 sec-featured 에서 /api/analyzes 를 쓴다', () => {
  assert.match(PUBLIC_JS, /function wireFeaturedSchoolTwin\(\)/);
  assert.match(PUBLIC_JS, /wireAiNative\(\);\s*\n\s*wireFeaturedSchoolTwin\(\);/);
  assert.match(PUBLIC_JS, /root\.querySelector\('#sec-featured \[data-featured-group\]'\)/);
  assert.match(PUBLIC_JS, /root\.querySelector\('#sec-featured \[data-featured-twin\]'\)/);
  assert.match(PUBLIC_JS, /fetch\('\/api\/analyzes\?schoolName=' \+ encodeURIComponent\(school\)\)/);
  assert.match(PUBLIC_JS, /nameEn === 'english'/);
  assert.match(PUBLIC_JS, /escapeHtml\(a\.grade\) \+ '학년 ' \+ escapeHtml\(a\.semester\)/);
  assert.doesNotMatch(PUBLIC_JS, /wireProgramCardTwin|featuredLessonIds|selectedFeaturedLessons/);
  // 재렌더 가드 + 키보드 + reduced
  assert.match(PUBLIC_JS, /group\.dataset\.twinWired === 'true'/);
  assert.match(PUBLIC_JS, /ArrowRight' \|\| e\.key === 'ArrowDown'/);
  assert.match(PUBLIC_JS, /prefers-reduced-motion: reduce/);
});

test('academy_public.css: 학교 칩 상호작용(hover/포커스/aria-pressed) + .featured-twin 패널 스타일', () => {
  assert.match(PUBLIC_CSS, /\.featured-chip\{[^}]*cursor:pointer/);
  assert.match(PUBLIC_CSS, /\.featured-chip:focus-visible\{[^}]*outline/);
  assert.match(PUBLIC_CSS, /\.featured-chip\[aria-pressed="true"\]\{[^}]*background:var\(--ink\)/);
  assert.match(PUBLIC_CSS, /\.featured-twin\{[^}]*border:1px solid var\(--line\)/);
  assert.match(PUBLIC_CSS, /\.featured-twin-school\{[^}]*font-weight:900/);
});

/* ── 대표수업(sec-lessons): Phase 5.1 회귀 제거 ─────────────────────────── */

test('programCardHtml: 카드 이름은 그냥 이름 줄로 렌더 — 학교 셀렉터/Twin 패널 없음', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: {}, builderDraft: { lessonsLayout: 'horizontal' } });
  academy.lessons = [
    { lessonId: 13, name: '압구정중, 신구중, 신사중, 언북중', tabShortName: '중학교 영어내신', kicker: 'k', highlight: 'h', mediaType: 'VIDEO', mediaVideoId: 6 },
    { lessonId: 9, name: '영문법 기초: 레고', tabShortName: 'F2R Starter', highlight: 'h2', mediaType: 'IMAGE' }
  ];
  primeRenderer(hooks, academy, []);
  const html = hooks.renderLessons(academy);
  assert.match(html, /<div class="f2r-program-card-name">압구정중, 신구중, 신사중, 언북중<\/div>/);
  assert.match(html, /<div class="f2r-program-card-name">영문법 기초: 레고<\/div>/);
  assert.doesNotMatch(html, /f2r-program-card--twin|f2r-program-card-schools|data-program-twin|f2r-twin-/);
});

test('academy_sections.js: schoolTokensFrom / programCardSchools / f2r-program-card--twin 흔적 없음', () => {
  assert.doesNotMatch(SECTIONS, /schoolTokensFrom|programCardSchools|f2r-program-card--twin|data-program-twin-group/);
});

test('academy_collection.css: school-twin(.f2r-twin-*/.f2r-program-card-schools) 스타일 제거됨', () => {
  assert.doesNotMatch(COLLECTION_CSS, /^\.f2r-twin-chip\s*\{/m);
  assert.doesNotMatch(COLLECTION_CSS, /^\.f2r-program-card-twin\s*\{/m);
});

/* ── 캐시버스팅 / 하드코딩 ─────────────────────────────────────────────── */

test('캐시버스팅: 변경된 storefront 자산 버전이 함께 올라간다', () => {
  assert.match(PUBLIC_HTML, /academy_public\.(js|css)\?v=20260901-3/);
  assert.match(PUBLIC_HTML, /academy_collection\.css\?v=20260901-3/);
  assert.match(PUBLIC_HTML, /academy_cinematic\.(js|css)\?v=20260901-3/);
  assert.match(PUBLIC_HTML, /academy_sections\.js\?v=20260901-3/);
  assert.match(BUILDER_HTML, /academy_sections\.js\?v=20260901-3/);
});

test('sections.js featured 영역에 학교/연도/시즌 하드코딩이 없다', () => {
  const featured = SECTIONS.slice(
    SECTIONS.indexOf('sec-featured : Featured'),
    SECTIONS.indexOf('sec-digitaltwin : AI Native'));
  assert.doesNotMatch(featured, /압구정|신구중|신사중|언북중|202\d|2학기|1학기|내신/,
    '코드에 학교/연도/시즌/과목 문자열이 있으면 안 된다 — 전부 builderDraft 입력값');
});
