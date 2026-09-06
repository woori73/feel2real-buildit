const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { loadSectionsModule } = require('./helpers/browser_script_harness');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

// V2-8: Common Video Playback — Media Viewport 계약.
//   두 영역(Actual Class Phone Card / 대표 수업 Program Card)의 재생 확대·crop 버그는
//   같은 원인이었다: academy_collection.js onPlayClick 이 트리거를 replaceWith 로 치환해
//   크기·비율·overflow 를 소유하던 컨테이너를 없애고, .f2r-video-card-player 가
//   position:absolute + width/height:auto 라 대체(replaced) 요소가 원본 해상도로 렌더됐다.
//   → 공통 primitive(mediaViewportHtml + .f2r-media-viewport/.f2r-media-play/.f2r-media-player)
//     로 뷰포트를 유지한 채 그 "안에서" 플레이어를 덧붙인다.

const COLLECTION_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_collection.js'), 'utf8');
const COLLECTION_CSS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_collection.css'), 'utf8');

test('mediaAspectRatio: 세로/가로 영상 비율을 9:16~16:9 로 클램프하고, 모르면 null', () => {
  const s = loadSectionsModule();
  // 세로 수업 영상(406x722 ≈ 9:16) → 거의 그대로
  const portrait = s.mediaAspectRatio(406, 722);
  assert.match(portrait, /^\d+ \/ 1000$/);
  assert.ok(Number(portrait.split(' / ')[0]) / 1000 >= 9 / 16 - 0.001);
  // 극단적 초세로(1x4) → 9/16 로 클램프
  assert.equal(s.mediaAspectRatio(100, 400).split(' / ')[0] / 1000 >= 9 / 16 - 0.001, true);
  // 가로(1920x1080) → 16/9
  assert.equal(Math.round((16 / 9) * 1000) + ' / 1000', s.mediaAspectRatio(1920, 1080));
  // 모름
  assert.equal(s.mediaAspectRatio(null, null), null);
  assert.equal(s.mediaAspectRatio(0, 100), null);
});

test('mediaViewportHtml: playable 이면 뷰포트 div + 내부 재생 버튼, 정적이면 div 만', () => {
  const s = loadSectionsModule();
  const play = s.mediaViewportHtml({
    viewportClass: 'f2r-phone-screen', face: '<img src="/t.jpg">',
    playable: true, src: '/v/8/stream', poster: '/v/8/thumbnail', label: '수업 영상 재생'
  });
  assert.match(play, /^<div class="f2r-phone-screen f2r-media-viewport">/);
  assert.match(play, /<button type="button" class="f2r-media-play" data-collection-play data-video-src="\/v\/8\/stream" data-video-poster="\/v\/8\/thumbnail" aria-label="수업 영상 재생">/);
  assert.match(play, /<img src="\/t\.jpg"><\/button><\/div>$/);
  // 트리거가 곧 뷰포트인 옛 구조(button.f2r-phone-screen)가 아니어야 한다
  assert.doesNotMatch(play, /<button[^>]*class="f2r-phone-screen"/);

  const staticOnly = s.mediaViewportHtml({ viewportClass: 'f2r-phone-screen', face: '<img>', playable: false });
  assert.equal(staticOnly, '<div class="f2r-phone-screen f2r-media-viewport"><img></div>');

  const withAspect = s.mediaViewportHtml({
    viewportClass: 'f2r-program-card-media f2r-program-card-media-video',
    face: 'x', playable: true, src: '/s', aspect: '563 / 1000'
  });
  assert.match(withAspect, /style="--f2r-media-aspect:563 \/ 1000"/);
});

test('공개 Program Card 영상 stage 는 실제 업로드 영상 비율(--f2r-media-aspect)을 쓴다', () => {
  const academy = academyFixture({
    name: '테스트영어',
    lessons: [{ lessonId: 13, name: '내신', tabShortName: '내신', mediaType: 'VIDEO', mediaVideoId: 6 }],
    pageSettings: { sectionVisibility: {}, builderDraft: { lessonsLayout: 'horizontal' } }
  });
  const pub = Object.fromEntries(bothRenderers()).public;
  // 세로 영상(406x722)이 videos 목록에 있다
  primeRenderer(pub, academy, [{ videoId: 6, width: 406, height: 722, thumbnailUrl: '/api/academies/x/videos/6/thumbnail' }]);
  const html = pub.renderLessons(academy);
  assert.match(html, /class="f2r-program-card-media f2r-program-card-media-video f2r-media-viewport" style="--f2r-media-aspect:\d+ \/ 1000"/);
  assert.match(html, /data-collection-play data-video-src="\/api\/academies\/[^"]+\/videos\/6\/stream"/);
});

test('Actual Class 는 폰 프레임(9:19)을 유지하고 그 안에서 재생한다', () => {
  const academy = academyFixture({
    name: '테스트영어', lessons: [],
    pageSettings: { sectionVisibility: {}, builderDraft: { videosLayout: 'horizontal' } }
  });
  const pub = Object.fromEntries(bothRenderers()).public;
  primeRenderer(pub, academy, [{ videoId: 8, videoUrl: '/api/academies/x/videos/8/stream', thumbnailUrl: '/t', durationSec: 17, width: 406, height: 720, purpose: 'LESSON' }]);
  const html = pub.renderVideos(academy);
  // 프레임 유지 + 스크린이 뷰포트 + 그 안 재생 버튼
  assert.match(html, /class="f2r-phone-frame"/);
  assert.match(html, /<div class="f2r-phone-screen f2r-media-viewport"><button type="button" class="f2r-media-play" data-collection-play/);
});

test('onPlayClick: 트리거를 치환하지 않고 .f2r-media-viewport 안에 .f2r-media-player 를 덧붙이며 트리거를 숨긴다', () => {
  // 옛 버그의 핵심 라인이 사라졌는지 + 새 계약 라인이 있는지 (소스 계약)
  assert.doesNotMatch(COLLECTION_JS, /trigger\.replaceWith\(node\)/, '트리거 replaceWith 금지(뷰포트 소실)');
  assert.doesNotMatch(COLLECTION_JS, /f2r-video-card-player/, '옛 플레이어 클래스 제거');
  assert.match(COLLECTION_JS, /closest\(['"]\.f2r-media-viewport['"]\)/, '감싸는 Media Viewport 를 찾는다');
  assert.match(COLLECTION_JS, /node\.className = ['"]f2r-media-player['"]/);
  assert.match(COLLECTION_JS, /viewport\.appendChild\(node\)/, '뷰포트 안에 플레이어 삽입');
  assert.match(COLLECTION_JS, /trigger\.hidden = true/, '재생 후 트리거 숨김');
  // YouTube 임베드는 autoplay 파라미터를 붙인다
  assert.match(COLLECTION_JS, /autoplay=1/);
  // 중복 재생 방지
  assert.match(COLLECTION_JS, /querySelector\(['"]\.f2r-media-player['"]\)/);
  // §J·§10 — 클릭(user gesture) 안에서 play() 를 직접 부른다(모바일 autoplay 정책 대응).
  // src 는 새 <video> 에 한 번만 지정하고, load() 재호출/재대입으로 최초 로드를 끊지 않는다.
  assert.match(COLLECTION_JS, /node\.play\(\)/, 'user gesture 안에서 play() 직접 호출');
  assert.doesNotMatch(COLLECTION_JS, /node\.load\(\)/, 'load() 재호출 금지(최초 로드 중단)');
  assert.doesNotMatch(COLLECTION_JS, /node\.src\s*=\s*node\.src/, 'src 재대입 금지');
});

test('CSS: 플레이어는 뷰포트를 채우고(inset:0) 원본 비율은 object-fit:contain 으로 보존(crop 금지)', () => {
  const compact = COLLECTION_CSS.replace(/\s+/g, '');
  assert.doesNotMatch(COLLECTION_CSS, /\.f2r-video-card-player\s*\{/, '옛 플레이어 규칙 제거');
  assert.match(compact, /\.f2r-media-viewport\{position:relative;?\}/);
  assert.match(compact, /\.f2r-media-player\{[^}]*position:absolute;[^}]*inset:0;[^}]*width:100%;[^}]*height:100%/);
  // 업로드 <video> 는 letterbox(contain) — 강제 cover(잘라내기) 금지
  assert.match(compact, /video\.f2r-media-player\{object-fit:contain;?\}/);
  assert.doesNotMatch(compact, /\.f2r-media-player\{[^}]*object-fit:cover/);
  // Program 영상 stage 는 --f2r-media-aspect 변수를 쓴다(IMAGE 는 16/10 기본)
  assert.match(compact, /\.f2r-program-card-media\{[^}]*aspect-ratio:var\(--f2r-media-aspect,16\/10\)/);
  // 재생 트리거 버튼은 뷰포트를 꽉 채운다
  assert.match(compact, /\.f2r-media-play\{[^}]*position:absolute;[^}]*inset:0/);
});

test('빌더 캔버스 영상 카드는 재생 트리거 없이 뷰포트만 렌더한다(정적)', () => {
  const academy = academyFixture({
    name: '테스트영어', lessons: [],
    pageSettings: { sectionVisibility: {}, builderDraft: { videosLayout: 'horizontal' } }
  });
  const bld = Object.fromEntries(bothRenderers()).builder;
  primeRenderer(bld, academy, [{ videoId: 8, thumbnailUrl: '/t', width: 406, height: 720, purpose: 'LESSON' }]);
  const html = bld.renderVideos(academy);
  assert.match(html, /class="f2r-phone-screen f2r-media-viewport"/);
  assert.doesNotMatch(html, /data-collection-play/);
  assert.doesNotMatch(html, /f2r-media-play/);
});
