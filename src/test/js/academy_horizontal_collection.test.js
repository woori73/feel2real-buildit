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
    coreDetailTitle: '블록 조립으로 영문법 원리를 직관적으로 체득',
    gradeLabel: '초등 고학년 - 중등 1학년', tabGuidanceLabel: '개념이 부족하다면',
    mediaType: 'IMAGE'
  },
  {
    lessonId: 13, name: '압구정중, 신구중, 신사중, 언북중', tabShortName: '중학교 영어내신',
    kicker: '출제자의 눈으로 풀고 함정을 미리 파악합니다.',
    learnerNeed: '문법과 서술형에서 자꾸 감점될 때',
    highlight: '', coreDetailTitle: '', tabGuidanceLabel: '중등 1학년 - 3학년',
    mediaType: 'VIDEO', mediaVideoId: 6
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
    assert.match(html, /<div class="f2r-collection f2r-collection--programs"/, `${kind}: programs 변형 컬렉션 shell`);
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
    assert.match(html, /<div class="f2r-collection f2r-collection--videos"/, `${kind}: videos 변형 컬렉션 shell`);
    assert.match(html, /f2r-phone-card/, `${kind}: phone card`);
    assert.match(html, /f2r-phone-frame/, `${kind}: phone frame`);
    assert.match(html, /0:17/, `${kind}: 첫 영상 길이 배지`);
    assert.doesNotMatch(html, /video-playlist-layout/, `${kind}: 기존 재생목록 미사용`);
  }
});

test('Program Card 는 V2 우선순위(eyebrow→name→kicker→need→highlight→grade→CTA)를 지키고 빈 필드는 생략한다', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture({ lessonsLayout: 'horizontal' });
  primeRenderer(map.public, academy, []);
  const html = map.public.renderLessons(academy);

  // 첫 카드(lesson 9) 영역만: track 시작 ~ 두 번째 카드(lesson 13) 전까지.
  const c9 = html.slice(html.indexOf('f2r-collection-track'), html.indexOf('압구정중, 신구중'));
  const order = ['f2r-program-card-eyebrow', 'f2r-program-card-name', 'f2r-program-card-kicker',
    'f2r-program-card-need', 'f2r-program-card-highlight', 'f2r-program-card-grade', 'f2r-program-card-cta'];
  const positions = order.map((m) => c9.indexOf(m));
  assert.ok(positions.every((p, i) => p >= 0 && (i === 0 || p > positions[i - 1])), '필드 DOM 순서가 V2 우선순위와 다르다: ' + JSON.stringify(positions));
  // media(VISUAL STAGE)는 body 앞에 온다
  assert.ok(html.indexOf('f2r-program-card-media') < html.indexOf('f2r-program-card-body'), 'media 는 카드 본문보다 앞');

  // lesson 13 은 highlight 가 비어 있다 → 그 카드에는 해당 요소가 없어야 한다. coreDetailTitle 은 V2 카드에서 아예 안 쓴다.
  const card13 = html.slice(html.indexOf('압구정중, 신구중'));
  const card13Html = card13.slice(0, card13.indexOf('</article>'));
  assert.doesNotMatch(card13Html, /f2r-program-card-highlight/, 'lesson 13 은 highlight 없음 → 요소 생략');
  assert.doesNotMatch(html, /f2r-program-card-core/, 'coreDetailTitle 은 V2 Program Card 에서 렌더하지 않는다');
});

test('Program Card 는 lesson_products 에 등록된 대표 미디어를 표시한다 (item 16)', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture({ lessonsLayout: 'horizontal' });

  primeRenderer(map.public, academy, []);
  const pub = map.public.renderLessons(academy);
  // IMAGE lesson → 공개 hero-image 경로
  assert.match(pub, /<img src="\/api\/academies\/[^"]+\/lessons\/9\/hero-image" alt="" loading="lazy">/, 'IMAGE 대표 미디어');
  // VIDEO lesson(13, mediaVideoId 6) → 클릭 재생 트리거 + 썸네일
  assert.match(pub, /class="f2r-program-card-media f2r-program-card-media-video" data-collection-play data-video-src="\/api\/academies\/[^"]+\/videos\/6\/stream"/, 'VIDEO 대표 미디어 재생 트리거');
  assert.match(pub, /\/api\/academies\/[^"]+\/videos\/6\/thumbnail/, 'VIDEO 썸네일');

  // 빌더 캔버스: 이미지는 소유자 전용 경로, 재생 트리거 없음(정적)
  primeRenderer(map.builder, academy, [{ videoId: 6, thumbnailUrl: '/api/academies/x/videos/6/thumbnail' }]);
  const bld = map.builder.renderLessons(academy);
  assert.match(bld, /<img src="\/api\/tutor\/academy\/lessons\/9\/hero-image"/, '빌더 IMAGE 는 소유자 경로');
  assert.doesNotMatch(bld, /f2r-program-card-media[^"]*" data-collection-play/, '빌더 미디어는 재생 트리거 없음');
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

test('Phone Card 는 프레임 + 클릭 재생 스크린을 갖고, 가로 영상만 letterbox(is-landscape) 한다', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture({ videosLayout: 'horizontal' });

  primeRenderer(map.public, academy, VIDEOS);
  const pub = map.public.renderVideos(academy);
  // 스크린이 재생 트리거(button.f2r-phone-screen[data-collection-play])
  assert.match(pub, /<button type="button" class="f2r-phone-screen" data-collection-play data-video-src="\/api\/academies\/x\/videos\/8\/stream"/, 'phone-screen 이 재생 트리거');
  assert.match(pub, /class="f2r-phone-frame"/, 'phone 프레임');
  assert.match(pub, /class="f2r-phone-cam"/, 'phone 카메라 장식');
  // 세로 영상(8: 406x720)은 letterbox 아님, 가로 영상(6: 640x360)은 is-landscape
  const card8 = pub.slice(pub.lastIndexOf('f2r-phone-card', pub.indexOf('videos/8/stream')), pub.indexOf('videos/8/stream'));
  assert.doesNotMatch(card8, /is-landscape/, '세로 영상 phone card 는 letterbox 아님');
  const card6 = pub.slice(pub.lastIndexOf('f2r-phone-card', pub.indexOf('videos/6/stream')), pub.indexOf('videos/6/stream'));
  assert.match(card6, /is-landscape/, '가로 영상 phone card 는 letterbox(is-landscape)');

  primeRenderer(map.builder, academy, VIDEOS);
  const bld = map.builder.renderVideos(academy);
  assert.doesNotMatch(bld, /data-collection-play/, 'builder phone card 는 재생 트리거 없음(정적)');
  assert.match(bld, /class="f2r-phone-frame"/, 'builder 도 프레임은 표시(정적)');
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

// ── V2-2: Horizontal Experience Engine 고도화 ────────────────────────────────

test('컬렉션 shell 은 진행 카운터(NN / MM)를 갖고 total 이 카드 수와 일치한다', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture({ lessonsLayout: 'horizontal' });   // LESSONS = 2개
  primeRenderer(map.public, academy, []);
  const pub = map.public.renderLessons(academy);
  assert.match(pub, /<span class="f2r-collection-progress"[^>]*>/, 'progress 카운터');
  assert.match(pub, /<span data-collection-current>01<\/span>/, 'current 기본 01');
  assert.match(pub, /<span data-collection-total>02<\/span>/, 'total = 카드 수(2)');

  // 빌더 캔버스(정적)에는 카운터가 없다 — 스크롤이 없어 의미 없음
  primeRenderer(map.builder, academy, []);
  assert.doesNotMatch(map.builder.renderLessons(academy), /f2r-collection-progress/, 'static 모드는 카운터 없음');
});

test('academy_collection.js 는 ResizeObserver + 선두 카드 추적 + is-dimmed 로 shelf 상태를 갱신한다', () => {
  const js = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/academy_collection.js'), 'utf8');
  assert.match(js, /new global\.ResizeObserver/, 'ResizeObserver 로 stride 재계산');
  assert.match(js, /data-collection-current/, '진행 카운터 갱신');
  assert.match(js, /classList\.toggle\('is-dimmed'/, '가장자리 카드 감광');
  assert.match(js, /classList\.add\('js-collection'\)/, 'JS 게이트 클래스');
  // 매 scroll 마다 rAF 1회로 배칭(자기 예약 루프 아님)
  assert.match(js, /if \(scheduled\) return;\s*\n\s*scheduled = true;/, 'scroll 핸들러 rAF 배칭');
});

test('academy_collection.css 의 active/dim 축소는 js-collection 스코프 + reduced-motion 예외를 갖는다', () => {
  const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/academy_collection.css'), 'utf8');
  assert.match(css, /html\.js-collection \.f2r-collection-card\.is-dimmed > \*\s*\{[^}]*transform:\s*scale/, 'is-dimmed 축소는 js-collection 스코프');
  assert.match(css, /prefers-reduced-motion[\s\S]*html\.js-collection \.f2r-collection-card[\s\S]{0,120}transform:\s*none/, 'reduced-motion → 축소 제거');
  // JS 미로드 시 카드가 축소된 채 남지 않는다(스코프 밖은 규칙 없음)
  assert.doesNotMatch(css, /\n\.f2r-collection-card\.is-dimmed > \*\s*\{/, 'is-dimmed 축소가 무스코프로 새지 않는다');
});
