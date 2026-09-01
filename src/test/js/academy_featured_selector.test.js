const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * 「2학기 영어 내신」(sec-featured) — 학교별 독립 카드 횡스크롤.
 *
 *  - sec-featured: eyebrow → 제목 → 설명 → **학교별 카드 트랙**(각 카드 = 그 학교의 실제
 *    /api/analyzes 출제자 Digital Twin) → 단일 CTA. 배선 wireFeaturedSchoolCards — 전부
 *    #sec-featured 안에서만. 가짜 분석 없음, builderDraft 복제 없음.
 *  - 대표수업(sec-lessons)의 programCardHtml 은 학교 셀렉터/Digital Twin 을 만들지 않는다.
 *    카드 이름은 그냥 이름 줄. schoolTokensFrom 없음.
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

/* ── sec-featured: 학교별 카드 횡스크롤 ─────────────────────────────────── */

test('공개 렌더: 학교마다 독립 카드(data-featured-card + data-school) + 트랙 + 카운터/dot', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<div class="featured-schoolcards" role="list" data-featured-track>/);
  assert.equal((html.match(/class="featured-schoolcard" role="listitem" data-featured-card data-school="/g) || []).length, 4);
  assert.match(html, /data-featured-card data-school="압구정중"[\s\S]*?<b class="featured-schoolcard-name">압구정중<\/b>/);
  assert.match(html, /data-school="언북중"/);
  assert.match(html, /출제자 Digital Twin/);
  // 카드 본문은 빈 상태(academy_public.js 가 fetch 로 채운다)
  assert.match(html, /data-featured-card-body[^>]*>\s*<p class="featured-schoolcard-loading">압구정중 출제 분석/);
  assert.match(html, /data-featured-card-more href="\/analyzes"/);
  // 카운터 + dot
  assert.match(html, /data-featured-count>1 \/ 4</);
  assert.equal((html.match(/class="featured-schoolcards-dot[^"]*" data-featured-dot="/g) || []).length, 4);
  // 옛 칩/단일패널/showcase 흔적 없음
  assert.doesNotMatch(html, /featured-chip"|data-featured-group|data-featured-school=|data-featured-twin|dt-showcase|dt-scene/);
});

test('공개 렌더: 빈 학교 항목은 카드를 만들지 않는다', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {
    featuredItem1: '압구정중', featuredItem2: '   ', featuredItem3: '   ', featuredItem4: '   '
  } });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);
  assert.equal((html.match(/class="featured-schoolcard" role="listitem" data-featured-card/g) || []).length, 1);
  assert.match(html, /data-featured-count>1 \/ 1</);
});

test('빌더 렌더: 학교명은 편집 앵커, 트랙 이벤트 훅(data-featured-track/card)은 공개 전용, 본문은 정적 안내', () => {
  const hooks = Object.fromEntries(bothRenderers()).builder;
  const academy = fixture(SCHOOLS);
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<span class="featured-chip-label" data-builder-draft="featuredItem1"/);
  assert.match(html, /featured-schoolcard--builder/);
  assert.doesNotMatch(html, /data-featured-track|data-featured-card |data-featured-count|data-featured-dot/);
  assert.match(html, /<code>\/analyzes<\/code>/);
});

test('academy_public.js: wireFeaturedSchoolCards 가 render()에 배선되고 #sec-featured 에서만 /api/analyzes 를 쓴다', () => {
  assert.match(PUBLIC_JS, /function wireFeaturedSchoolCards\(\)/);
  assert.match(PUBLIC_JS, /wireAiNative\(\);\s*\n\s*wireFeaturedSchoolCards\(\);/);
  assert.match(PUBLIC_JS, /root\.querySelector\('#sec-featured \[data-featured-cards\]'\)/);
  assert.match(PUBLIC_JS, /fetch\('\/api\/analyzes\?schoolName=' \+ encodeURIComponent\(school\)\)/);
  assert.match(PUBLIC_JS, /nameEn === 'english'/);
  // 학년 → 학기 → 중간/기말 별로 그 출제자 Digital Twin(분석 상세) 로 들어가는 실링크 복구
  assert.match(PUBLIC_JS, /a\.grade[^;]*a\.semester[^;]*a\.examType/);
  assert.match(PUBLIC_JS, /featured-schoolcard-rowlink" href="' \+ href/);
  assert.match(PUBLIC_JS, /a\.detailUrl \? escapeAttr\(a\.detailUrl\)/);
  // #sec-lessons 를 fallback 셀렉터로 쓰지 않는다
  assert.doesNotMatch(PUBLIC_JS, /wireFeaturedSchoolTwin|wireProgramCardTwin|featuredLessonIds|selectedFeaturedLessons/);
  assert.doesNotMatch(PUBLIC_JS, /#sec-lessons \[data-featured|#sec-lessons[^']*analyzes/);
  // 재렌더 가드
  assert.match(PUBLIC_JS, /wrap\.dataset\.cardsWired === 'true'/);
});

test('academy_public.css: 학교 카드 트랙 — 대표수업과 동일한 swipe/scroll-snap + dot + 디바이스 프레임', () => {
  assert.match(PUBLIC_CSS, /\.featured-schoolcards\{[^}]*overflow-x:auto/);
  // 대표수업(.f2r-collection)과 동일: proximity snap + snap-align:start + overscroll contain
  assert.match(PUBLIC_CSS, /\.featured-schoolcards\{[^}]*scroll-snap-type:x proximity/);
  assert.match(PUBLIC_CSS, /\.featured-schoolcards\{[^}]*overscroll-behavior-x:contain/);
  assert.match(PUBLIC_CSS, /\.featured-schoolcard\{[^}]*scroll-snap-align:start/);
  // 카드 하나 중심 + 다음 카드 일부 peek (78vw)
  assert.match(PUBLIC_CSS, /\.featured-schoolcard\{flex:0 0 78vw/);
  // 공통 디바이스형 프레임
  assert.match(PUBLIC_CSS, /\.featured-schoolcard\{[^}]*border:3px solid var\(--device-rim\)/);
  assert.match(PUBLIC_CSS, /\.featured-schoolcards-dot\.is-on\{/);
  // 학년·학기·중간/기말 별 Digital Twin(분석 상세) 링크
  assert.match(PUBLIC_CSS, /\.featured-schoolcard-rowlink\{/);
  assert.match(PUBLIC_CSS, /@media \(min-width:900px\)\{[\s\S]*\.featured-schoolcard\{flex-basis:300px/);
});

/* ── 대표수업(sec-lessons): 회귀 제거 유지 ─────────────────────────────── */

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

test('academy_sections.js: schoolTokensFrom / programCardSchools / f2r-program-card--twin 흔적 없음. 학교 카드는 sec-featured 안에서만', () => {
  assert.doesNotMatch(SECTIONS, /schoolTokensFrom|programCardSchools|f2r-program-card--twin|data-program-twin-group/);
  // featuredSchoolCards 는 renderFeatured 에서만 호출된다(대표수업 렌더 경로엔 없음)
  const lessonsSlice = SECTIONS.slice(SECTIONS.indexOf('function renderLessons'), SECTIONS.indexOf('function renderVideos') + 1 || SECTIONS.length);
  assert.doesNotMatch(lessonsSlice, /featuredSchoolCards|data-featured-card|data-school=/);
});

test('academy_collection.css: school-twin(.f2r-twin-*/.f2r-program-card-schools) 스타일 제거됨', () => {
  assert.doesNotMatch(COLLECTION_CSS, /^\.f2r-twin-chip\s*\{/m);
  assert.doesNotMatch(COLLECTION_CSS, /^\.f2r-program-card-twin\s*\{/m);
});

/* ── 캐시버스팅 / 하드코딩 ─────────────────────────────────────────────── */

test('캐시버스팅: 변경된 storefront 자산 버전이 함께 올라간다', () => {
  assert.match(PUBLIC_HTML, /academy_scene\.(js|css)\?v=20260901-5/);
  assert.match(PUBLIC_HTML, /academy_cinematic\.js\?v=20260901-7/);
  // 모바일 카드 디자인 시스템 + Scene 설명 + Digital Twin 복구로 함께 올라간 자산.
  assert.match(PUBLIC_HTML, /academy_public\.js\?v=20260901-8/);
  assert.match(PUBLIC_HTML, /academy_public\.css\?v=20260901-8/);
  assert.match(PUBLIC_HTML, /academy_cinematic\.css\?v=20260901-8/);
  assert.match(PUBLIC_HTML, /academy_collection\.css\?v=20260901-8/);
  assert.match(PUBLIC_HTML, /academy_sections\.js\?v=20260901-8/);
  assert.match(BUILDER_HTML, /academy_sections\.js\?v=20260901-8/);
});

test('sections.js featured 영역에 학교/연도/시즌 하드코딩이 없다', () => {
  const featured = SECTIONS.slice(
    SECTIONS.indexOf('sec-featured : Featured'),
    SECTIONS.indexOf('sec-digitaltwin : AI Native'));
  assert.doesNotMatch(featured, /압구정|신구중|신사중|언북|2학기|내신|\b202\d\b/);
});
