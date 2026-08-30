const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

// PHASE 3-B: Horizontal Content Collection.
//   공통 구조 한 벌(Collection → head/nav → viewport → track → card) 위에
//   Program Card / Video Card 가 card *type* 으로 올라간다.
//   sec-lessons.lessonsLayout / sec-videos.videosLayout == 'horizontal' 일 때만 opt-in.

const LESSONS = [
  {
    lessonId: 9, name: '영문법 기초: 레고', tabShortName: 'F2R Starter',
    kicker: '영어의 기본 블록을 조립하는 단계',
    learnerNeed: '중등 영문법 기초 체력을 다지고 싶은 초등~중1',
    highlight: '외우지 않고 직접 조립하며 이해하는 원리 중심 영문법',
    coreDetailTitle: '블록 조립으로 영문법 원리를 직관적으로 체득'
  },
  {
    lessonId: 13, name: '압구정중, 신구중, 신사중, 언북중', tabShortName: '중학교 영어내신',
    kicker: '출제자의 눈으로 풀고 함정을 미리 파악합니다.',
    learnerNeed: '문법과 서술형에서 자꾸 감점될 때',
    highlight: '', coreDetailTitle: ''
  }
];
const VIDEOS = [
  { videoId: 8, videoUrl: '/api/academies/x/videos/8/stream', thumbnailUrl: '/api/academies/x/videos/8/thumbnail', durationSec: 17, width: 406, height: 720 },
  { videoId: 6, videoUrl: '/api/academies/x/videos/6/stream', thumbnailUrl: null, durationSec: 55, width: 640, height: 360 }
];

function fixture(builderDraft) {
  return academyFixture({
    name: '테스트영어교실',
    lessons: LESSONS,
    pageSettings: { sectionVisibility: {}, builderDraft: builderDraft || {} }
  });
}

test('레이아웃 키가 없으면 기존 레이아웃을 그대로 쓴다(회귀 방지)', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const academy = fixture({});
    primeRenderer(hooks, academy, VIDEOS);
    const lessons = hooks.renderLessons(academy);
    const videos = hooks.renderVideos(academy);
    assert.doesNotMatch(lessons, /f2r-collection/, `${kind}: lessons 기본은 컬렉션 아님`);
    assert.doesNotMatch(videos, /f2r-collection/, `${kind}: videos 기본은 컬렉션 아님`);
    // 기존 골격은 유지
    assert.match(lessons, /lesson-product-slot/, `${kind}: 기존 showcase slot`);
    assert.match(videos, /video-playlist-layout/, `${kind}: 기존 재생목록`);
  }
});

test("lessonsLayout='horizontal' 이면 sec-lessons 를 가로 컬렉션으로 렌더한다", () => {
  for (const [kind, hooks] of bothRenderers()) {
    const academy = fixture({ lessonsLayout: 'horizontal' });
    primeRenderer(hooks, academy, VIDEOS);
    const html = hooks.renderLessons(academy);
    assert.match(html, /<div class="f2r-collection"/, `${kind}: collection shell`);
    assert.match(html, /f2r-collection-track/, `${kind}: track`);
    assert.match(html, /f2r-program-card/, `${kind}: program card`);
    // 여전히 섹션 골격/제목/스케줄은 유지
    assert.match(html, /<section id="sec-lessons"/, `${kind}: section id`);
    assert.doesNotMatch(html, /lesson-product-showcase/, `${kind}: 기존 showcase 미사용`);
  }
});

test("videosLayout='horizontal' 이면 sec-videos 를 가로 Video 컬렉션으로 렌더한다", () => {
  for (const [kind, hooks] of bothRenderers()) {
    const academy = fixture({ videosLayout: 'horizontal' });
    primeRenderer(hooks, academy, VIDEOS);
    const html = hooks.renderVideos(academy);
    assert.match(html, /<div class="f2r-collection"/, `${kind}: collection shell`);
    assert.match(html, /f2r-video-card/, `${kind}: video card`);
    assert.match(html, /0:17/, `${kind}: 첫 영상 길이 배지`);
    assert.doesNotMatch(html, /video-playlist-layout/, `${kind}: 기존 재생목록 미사용`);
  }
});

test('Program Card 는 우선순위(kicker→name→learnerNeed→highlight→coreDetailTitle→CTA)를 지키고 빈 필드는 생략한다', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture({ lessonsLayout: 'horizontal' });
  primeRenderer(map.public, academy, []);
  const html = map.public.renderLessons(academy);

  const order = ['f2r-program-card-kicker', 'f2r-program-card-name', 'f2r-program-card-need',
    'f2r-program-card-highlight', 'f2r-program-card-core', 'f2r-program-card-cta'];
  const positions = order.map((m) => html.indexOf(m));
  assert.ok(positions.every((p, i) => i === 0 || (p > positions[i - 1])), '필드 DOM 순서가 우선순위와 다르다');

  // lesson 13 은 highlight/coreDetailTitle 이 비어 있다 → 그 카드에는 해당 요소가 없어야 한다
  const card13 = html.slice(html.indexOf('압구정중, 신구중'));
  const card13End = card13.indexOf('</article>');
  const card13Html = card13.slice(0, card13End);
  assert.doesNotMatch(card13Html, /f2r-program-card-highlight/, 'lesson 13 은 highlight 없음 → 요소 생략');
  assert.doesNotMatch(card13Html, /f2r-program-card-core/, 'lesson 13 은 coreDetailTitle 없음 → 요소 생략');
});

test('공개 Program Card 는 상담 CTA 가 실제 동작(requestConsult)이고, 빌더는 정적이다', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture({ lessonsLayout: 'horizontal' });

  primeRenderer(map.public, academy, []);
  const pub = map.public.renderLessons(academy);
  assert.match(pub, /<button type="button" class="f2r-program-card-cta" onclick="requestConsult\(\)">/, 'public CTA 는 버튼 + requestConsult');
  assert.doesNotMatch(pub, /data-collection-static/, 'public 은 정적 모드 아님');
  assert.match(pub, /data-collection-scroll="prev"/, 'public 은 좌우 네비게이션 화살표');

  primeRenderer(map.builder, academy, []);
  const bld = map.builder.renderLessons(academy);
  assert.match(bld, /data-collection-static/, 'builder 캔버스는 정적 wrap 모드');
  assert.doesNotMatch(bld, /onclick="requestConsult/, 'builder CTA 는 동작 없음');
  assert.doesNotMatch(bld, /data-collection-scroll/, 'builder 는 화살표 없음');
});

test('공개 Video Card 는 클릭 재생 앵커(업로드 스트림)를 갖고 방향에 맞는 비율 클래스를 붙인다', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture({ videosLayout: 'horizontal' });

  primeRenderer(map.public, academy, VIDEOS);
  const pub = map.public.renderVideos(academy);
  assert.match(pub, /data-collection-play/, 'public video card 는 재생 트리거');
  assert.match(pub, /data-video-src="\/api\/academies\/x\/videos\/8\/stream"/, '업로드 스트림 URL');
  assert.match(pub, /f2r-video-card is-landscape/, '가로(640x360) 영상은 is-landscape');
  // 세로 영상(406x720)은 landscape 클래스가 없다
  const portraitCard = pub.slice(pub.indexOf('videos/8/stream'));
  assert.doesNotMatch(portraitCard.slice(0, portraitCard.indexOf('</article>')), /is-landscape/, '세로 영상은 landscape 아님');

  primeRenderer(map.builder, academy, VIDEOS);
  const bld = map.builder.renderVideos(academy);
  assert.doesNotMatch(bld, /data-collection-play/, 'builder video card 는 재생 트리거 없음(정적)');
});

test('공개 렌더는 빌더 편집 앵커를 얹지 않는다(누수 방지)', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture({ lessonsLayout: 'horizontal', videosLayout: 'horizontal' });
  primeRenderer(map.public, academy, VIDEOS);
  const html = map.public.renderLessons(academy) + map.public.renderVideos(academy);
  assert.doesNotMatch(html, /data-builder-draft/, 'public 컬렉션에 편집 앵커 누수');
  assert.doesNotMatch(html, /contenteditable/, 'public 컬렉션에 contenteditable 누수');
});

test('academy_collection.css / academy_collection.js 는 FontAwesome 없이 자체 자산만 쓴다', () => {
  const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/academy_collection.css'), 'utf8');
  const js = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/academy_collection.js'), 'utf8');
  for (const [name, src] of [['css', css], ['js', js]]) {
    assert.doesNotMatch(src, /fa-solid|font-awesome/, `${name}: FontAwesome 참조`);
  }
  // 가로 밀림 방지 장치가 CSS 에 있어야 한다
  assert.match(css, /\.f2r-collection-viewport\s*\{[^}]*overflow:\s*hidden/, 'viewport overflow:hidden');
  assert.match(css, /overscroll-behavior-x:\s*contain/, 'track overscroll-behavior-x:contain');
  assert.match(css, /scroll-snap-type:\s*x/, 'track scroll-snap-type:x');
  // 화살표 이동의 부드러움은 CSS 가 담당(JS behavior:'smooth' 옵션은 일부 Chromium 에서 무시됨)
  assert.match(css, /\.f2r-collection-track\s*\{[^}]*scroll-behavior:\s*smooth/, 'track scroll-behavior:smooth');
  assert.match(css, /prefers-reduced-motion[\s\S]{0,400}\.f2r-collection-track\s*\{\s*scroll-behavior:\s*auto/, 'reduced-motion → scroll-behavior:auto');
  assert.doesNotMatch(js, /behavior:\s*['"]smooth['"]/, "JS 는 behavior:'smooth' 옵션에 의존하지 않는다");
});

test('공개 템플릿은 academy_collection 자산을 로드하고, 배선 모듈은 자동 캐러셀을 만들지 않는다', () => {
  const tpl = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/public/academy_public.html'), 'utf8');
  assert.match(tpl, /\/css\/academy_collection\.css/, 'collection CSS 링크');
  assert.match(tpl, /\/js\/academy_collection\.js/, 'collection JS 스크립트');
  assert.ok(
    tpl.indexOf('/js/academy_collection.js') < tpl.indexOf('/js/academy_public.js'),
    'academy_collection.js 가 academy_public.js 보다 먼저 로드되어야 한다');

  const js = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/academy_collection.js'), 'utf8');
  assert.doesNotMatch(js, /setInterval/, '자동 캐러셀(setInterval) 금지');
  assert.doesNotMatch(js, /requestAnimationFrame\([^)]*\bsyncArrows[^)]*\)[\s\S]{0,60}requestAnimationFrame/, '자기 예약 rAF 루프 금지');
  assert.match(js, /track\.scrollBy\(\s*dir/, '화살표는 native scrollBy(x, y) 레거시 시그니처로 이동');
});
