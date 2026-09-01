const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * Phase 5.1 — 학교별 실제 출제자 Digital Twin 은 sec-featured 의 별도 showcase 가 아니라
 * sec-lessons 의 「중학교 영어내신」 Program Card *안에서* 보여준다.
 *
 *  - sec-featured 는 단순 Featured 밴드로 되돌린다(eyebrow → 제목 → 설명 → 정적 칩 → CTA).
 *  - programCardHtml: 카드 이름이 학교 목록(2개+)이면 이름 줄 대신 학교 셀렉터 + [data-program-twin] 패널.
 *    academy_public.js wireProgramCardTwin 이 칩 선택 시 그 학교의 실제 /api/analyzes 데이터로 채운다.
 *  - 가짜 summary 없음. builderDraft 복제 없음. lesson/product 저장 구조 미변경. 다른 Program Card 무변경.
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

const SCHOOL_LESSON = {
  lessonId: 13,
  name: '압구정중, 신구중, 신사중, 언북중',
  tabShortName: '중학교 영어내신',
  kicker: '출제자의 눈으로 풀고, 함정을 미리 파악합니다.',
  highlight: '20년 노하우로 영어 내신 만점에 도전',
  tabGuidanceLabel: '중등 1학년 - 3학년',
  mediaType: 'VIDEO',
  mediaVideoId: 6
};
const PLAIN_LESSON = {
  lessonId: 9, name: '영문법 기초: 레고', tabShortName: 'F2R Starter',
  kicker: '기본 블록 조립', highlight: '원리 중심 영문법', mediaType: 'IMAGE'
};

function lessonsFixture(extra) {
  return fixture({
    sectionVisibility: {},
    builderDraft: { lessonsLayout: 'horizontal', ...(extra || {}) }
  });
}

/* ── sec-featured: 단순 밴드로 되돌림 ─────────────────────────────────────── */

test('sec-featured 공개 렌더: eyebrow/제목/설명/정적 칩/CTA 만 — showcase/twin/셀렉터 없음', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({
    sectionVisibility: { 'sec-featured': true },
    builderDraft: {
      featuredTitle: '2학기 영어 내신 Build It',
      featuredItem1: '압구정중', featuredItem2: '신구중', featuredItem3: '신사중', featuredItem4: '언북중',
      featuredCtaLabel: '2학기 내신 상담 신청'
    }
  });
  primeRenderer(hooks, academy);
  const html = hooks.renderFeatured(academy);

  assert.match(html, /<h2 class="featured-title"[^>]*>2학기 영어 내신 Build It<\/h2>/);
  assert.match(html, /<span class="featured-chip">압구정중<\/span>/);
  assert.match(html, /<button type="button" class="btn featured-cta-btn" onclick="requestConsult\(\)">2학기 내신 상담 신청<\/button>/);
  // Phase 5 의 가로 showcase / Digital Twin 패널 / 학교 셀렉터는 sec-featured 에서 제거됨
  assert.doesNotMatch(html, /dt-showcase|dt-scene|data-dt-track|data-featured-twin|data-featured-group|data-featured-school/);
  assert.doesNotMatch(html, /featured-paper|featured-stage/);
});

test('academy_public.js: sec-featured 에서 /api/analyzes 를 부르지 않는다 — wireFeaturedShowcase 없음', () => {
  assert.doesNotMatch(PUBLIC_JS, /wireFeaturedShowcase|data-dt-showcase|seekFeatured/);
});

test('academy_public.css: 무거운 칩 상호작용 스타일(aria-pressed/포커스)이 sec-featured 에서 빠졌다', () => {
  assert.doesNotMatch(PUBLIC_CSS, /\.featured-chip\[aria-pressed/);
  assert.doesNotMatch(PUBLIC_CSS, /\.featured-twin\b|\.dt-showcase|\.dt-scene/);
  assert.match(PUBLIC_CSS, /\.featured-chip\{[^}]*border-radius:999px/);
});

/* ── sec-lessons: school-twin Program Card ──────────────────────────────── */

test('공개 렌더: 카드 이름이 학교 목록이면 이름 줄 대신 학교 셀렉터 + [data-program-twin] 패널', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = lessonsFixture();
  academy.lessons = [SCHOOL_LESSON];
  primeRenderer(hooks, academy, []);
  const html = hooks.renderLessons(academy);

  assert.match(html, /f2r-program-card--twin/);
  assert.match(html, /<div class="f2r-program-card-schools" data-program-twin-group>/);
  assert.match(html, /<button type="button" class="f2r-twin-chip" data-twin-school="압구정중" aria-pressed="true">압구정중<\/button>/);
  assert.match(html, /data-twin-school="신구중" aria-pressed="false"/);
  assert.match(html, /data-twin-school="언북중" aria-pressed="false"/);
  assert.equal((html.match(/aria-pressed="true"/g) || []).length, 1);
  assert.match(html, /<div class="f2r-program-card-twin" data-program-twin aria-live="polite">/);
  assert.match(html, /class="f2r-twin-loading">압구정중 출제 분석을 불러오는 중…/);
  // 학교 목록이 그냥 텍스트 한 줄(.f2r-program-card-name)로는 렌더되지 않는다
  assert.doesNotMatch(html, /<div class="f2r-program-card-name">압구정중, 신구중/);
  // eyebrow / kicker / highlight / grade / CTA 는 그대로
  assert.match(html, /class="f2r-program-card-eyebrow">중학교 영어내신/);
  assert.match(html, /class="f2r-program-card-highlight">20년 노하우/);
  assert.match(html, /class="f2r-program-card-cta"/);
});

test('공개 렌더: 이름이 학교 목록이 아닌 Program Card 는 변경 없음(이름 줄 그대로)', () => {
  const hooks = Object.fromEntries(bothRenderers()).public;
  const academy = lessonsFixture();
  academy.lessons = [PLAIN_LESSON, SCHOOL_LESSON];
  primeRenderer(hooks, academy, []);
  const html = hooks.renderLessons(academy);
  assert.match(html, /<div class="f2r-program-card-name">영문법 기초: 레고<\/div>/);
  // 정확히 한 장만 twin 카드
  assert.equal((html.match(/f2r-program-card--twin/g) || []).length, 1);
  assert.equal((html.match(/data-program-twin-group/g) || []).length, 1);
});

test('빌더 렌더: twin 카드는 정적 칩 + 안내만(공개 전용 fetch)', () => {
  const hooks = Object.fromEntries(bothRenderers()).builder;
  const academy = lessonsFixture();
  academy.lessons = [SCHOOL_LESSON];
  primeRenderer(hooks, academy, []);
  const html = hooks.renderLessons(academy);
  assert.match(html, /f2r-program-card-schools--builder/);
  assert.match(html, /<span class="f2r-twin-chip">압구정중<\/span>/);
  assert.doesNotMatch(html, /data-program-twin-group|data-twin-school|aria-pressed/);
  assert.match(html, /<code>\/analyzes<\/code>/);
});

test('academy_public.js: wireProgramCardTwin 이 render()에 배선되고 /api/analyzes 실데이터를 쓴다', () => {
  assert.match(PUBLIC_JS, /function wireProgramCardTwin\(\)/);
  assert.match(PUBLIC_JS, /wireAiNative\(\);\s*\n\s*wireProgramCardTwin\(\);/);
  assert.match(PUBLIC_JS, /root\.querySelectorAll\('#sec-lessons \[data-program-twin-group\]'\)/);
  assert.match(PUBLIC_JS, /fetch\('\/api\/analyzes\?schoolName=' \+ encodeURIComponent\(school\)\)/);
  assert.match(PUBLIC_JS, /nameEn === 'english'/);
  // 가짜 summary 없음 — 실제 필드(grade/semester/examType/detailUrl)만
  assert.match(PUBLIC_JS, /escapeHtml\(a\.grade\) \+ '학년 · ' \+ escapeHtml\(a\.semester\)/);
  assert.match(PUBLIC_JS, /list\[0\] && list\[0\]\.detailUrl/);
  assert.doesNotMatch(PUBLIC_JS, /featuredLessonIds|selectedFeaturedLessons/);
  // 재렌더 가드 + 키보드
  assert.match(PUBLIC_JS, /group\.dataset\.twinWired === 'true'/);
  assert.match(PUBLIC_JS, /ArrowRight' \|\| e\.key === 'ArrowDown'/);
  assert.match(PUBLIC_JS, /prefers-reduced-motion: reduce/);
});

test('academy_sections.js: schoolTokensFrom 은 "중/고/…" 로 끝나는 토큰 2개+ 만 학교로 본다', () => {
  const map = Object.fromEntries(bothRenderers());
  const mod = map.public;   // hooks 는 렌더 함수만 노출하므로 정규식으로 계약 확인
  assert.match(SECTIONS, /function schoolTokensFrom\(name\)/);
  assert.match(SECTIONS, /\(초\|중\|고\|초등학교\|중학교\|고등학교\)\$/);
  assert.match(SECTIONS, /schools\.length >= 2 && typeof ctx\.programCardSchools === 'function'/);
  void mod;
});

test('CSS: school-twin 칩/패널 스타일이 academy_collection.css(빌더도 로드)에 정의된다', () => {
  assert.match(COLLECTION_CSS, /\.f2r-twin-chip\s*\{[\s\S]*cursor:\s*pointer/);
  assert.match(COLLECTION_CSS, /\.f2r-twin-chip\[aria-pressed="true"\]\s*\{[^}]*background:\s*var\(--ink\)/);
  assert.match(COLLECTION_CSS, /\.f2r-program-card-twin\s*\{[\s\S]*border:\s*1px solid #c9cfda/);
});

test('캐시버스팅: 변경된 storefront 자산 버전이 함께 올라간다', () => {
  assert.match(PUBLIC_HTML, /academy_public\.(js|css)\?v=20260901-2/);
  assert.match(PUBLIC_HTML, /academy_collection\.css\?v=20260901-2/);
  assert.match(PUBLIC_HTML, /academy_cinematic\.(js|css)\?v=20260901-2/);
  assert.match(PUBLIC_HTML, /academy_sections\.js\?v=20260901-2/);
  assert.match(BUILDER_HTML, /academy_sections\.js\?v=20260901-2/);
});

test('sections.js featured 영역에 학교/연도/시즌 하드코딩이 없다', () => {
  const featured = SECTIONS.slice(
    SECTIONS.indexOf('sec-featured : Featured'),
    SECTIONS.indexOf('sec-digitaltwin : AI Native'));
  assert.doesNotMatch(featured, /압구정|신구중|신사중|언북중|202\d|2학기|1학기|내신/,
    '코드에 학교/연도/시즌/과목 문자열이 있으면 안 된다 — 전부 builderDraft/lesson 입력값');
});
