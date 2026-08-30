const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { loadSectionsModule } = require('./helpers/browser_script_harness');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

// V2-9: Mini Lectures Collection — "교육자가 직접 설명하는 짧은 학습 콘텐츠".
//   Actual Class(sec-videos)와 역할이 다른 별도의 Horizontal Collection.
//   opt-in(기본 OFF). academy_videos 재사용 + 강의별 메타는 builderDraft.miniLectures JSON.
//   재생은 V2-8 공통 Media Viewport primitive 를 그대로 쓴다.

const SECTIONS_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_sections.js'), 'utf8');
const COLLECTION_CSS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_collection.css'), 'utf8');
const BUILDER_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js'), 'utf8');
const BUILDER_HTML = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/tutor/academy_builder.html'), 'utf8');

const LECTURE_VIDEOS = [
  { videoId: 21, videoUrl: '/api/academies/x/videos/21/stream', thumbnailUrl: '/t21', durationSec: 92, width: 1280, height: 720 },
  { videoId: 22, videoUrl: '/api/academies/x/videos/22/stream', thumbnailUrl: '/t22', durationSec: 140, width: 720, height: 1280 }
];

function fixture(builderDraft, visibility) {
  return academyFixture({
    name: '테스트영어교실',
    pageSettings: { sectionVisibility: visibility || {}, builderDraft: builderDraft || {} }
  });
}

test('opt-in: sectionVisibility 에 명시적 true 가 없으면 렌더하지 않는다(기본 OFF)', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const academy = fixture({ miniLectures: JSON.stringify([{ v: 21, t: '관계대명사' }]) });
    primeRenderer(hooks, academy, LECTURE_VIDEOS);
    assert.equal(hooks.renderMiniLectures(academy), '', `${kind}: opt-in 아닌데 렌더됨`);
  }
});

test('공개: ON 이어도 콘텐츠가 없으면 섹션 자체를 내지 않는다(가짜 콘텐츠 금지)', () => {
  const pub = Object.fromEntries(bothRenderers()).public;
  const academy = fixture({}, { 'sec-minilectures': true });
  primeRenderer(pub, academy, LECTURE_VIDEOS);
  assert.equal(pub.renderMiniLectures(academy), '');
});

test('빌더: ON + 콘텐츠 없음 → 편집 진입점(placeholder)을 보여준다', () => {
  const bld = Object.fromEntries(bothRenderers()).builder;
  const academy = fixture({}, { 'sec-minilectures': true });
  primeRenderer(bld, academy, LECTURE_VIDEOS);
  const html = bld.renderMiniLectures(academy);
  assert.match(html, /<section id="sec-minilectures"/);
  assert.match(html, /data-mini-lectures-edit/);
  assert.doesNotMatch(html, /f2r-lecture-card/, '콘텐츠 없는데 카드가 나옴');
});

test('공개: ON + 콘텐츠 있음 → 가로 컬렉션 + Mini Lecture Card(미디어/제목/노트/대상, CTA 없음)', () => {
  const pub = Object.fromEntries(bothRenderers()).public;
  const academy = fixture(
    { miniLectures: JSON.stringify([
        { v: 21, t: '관계대명사 한 번에 정리', n: 'which·that 판단 기준', g: '중등 내신' },
        { v: 99, t: '삭제된 영상' } // 영상 목록에 없음 → 카드 만들지 않음
      ]) },
    { 'sec-minilectures': true }
  );
  primeRenderer(pub, academy, LECTURE_VIDEOS);
  const html = pub.renderMiniLectures(academy);
  assert.match(html, /<section id="sec-minilectures"/);
  assert.match(html, /f2r-collection--lectures/);
  assert.match(html, /class="f2r-lecture-card"/);
  assert.match(html, /f2r-lecture-card-title">관계대명사 한 번에 정리</);
  assert.match(html, /f2r-lecture-card-note">which·that 판단 기준</);
  assert.match(html, /f2r-lecture-card-tag">중등 내신</);
  // 영상 21 은 가로(1280x720) → aspect var, V2-8 media viewport + 재생 트리거
  assert.match(html, /class="f2r-lecture-media f2r-media-viewport" style="--f2r-media-aspect/);
  assert.match(html, /<button type="button" class="f2r-media-play" data-collection-play data-video-src="\/api\/academies\/x\/videos\/21\/stream"/);
  // 없는 영상(99)은 렌더하지 않는다 → 카드 1개뿐
  assert.equal((html.match(/f2r-lecture-card"/g) || []).length, 1);
  // Mini Lecture 는 판매 CTA 가 없다(Actual Class·Program 과 다르다)
  assert.doesNotMatch(html, /f2r-program-card-cta|requestConsult/);
});

test('깨진 miniLectures JSON 은 조용히 빈 목록으로 처리한다', () => {
  const s = loadSectionsModule();
  const view = { pageSettings: { builderDraft: { miniLectures: '{not json' }, sectionVisibility: {} } };
  assert.equal(s.miniLectureItems(view).length, 0);
  const view2 = { pageSettings: { builderDraft: { miniLectures: '{"v":1}' }, sectionVisibility: {} } };
  assert.equal(s.miniLectureItems(view2).length, 0); // 배열이 아님
});

test('miniLectureItems: 정규화 + videoId 없는 항목 제거 + 최대 12개', () => {
  const s = loadSectionsModule();
  const many = Array.from({ length: 20 }, (_, i) => ({ v: i + 1, t: 't' + i }));
  const view = { pageSettings: { builderDraft: { miniLectures: JSON.stringify(many.concat([{ t: '노비디오' }])) }, sectionVisibility: {} } };
  const items = s.miniLectureItems(view);
  assert.equal(items.length, 12);
  assert.ok(items.every((e) => e.v > 0));
});

test('SSoT: 공개/빌더가 같은 renderMiniLectures + 카드 구조를 쓴다', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture(
    { miniLectures: JSON.stringify([{ v: 22, t: '세로 강의', n: '포인트' }]) },
    { 'sec-minilectures': true }
  );
  primeRenderer(map.public, academy, LECTURE_VIDEOS);
  primeRenderer(map.builder, academy, LECTURE_VIDEOS);
  const pub = map.public.renderMiniLectures(academy);
  const bld = map.builder.renderMiniLectures(academy);
  for (const [kind, html] of [['public', pub], ['builder', bld]]) {
    assert.match(html, /class="f2r-lecture-card"/, `${kind}`);
    assert.match(html, /f2r-lecture-card-title">세로 강의</, `${kind}`);
    assert.match(html, /f2r-lecture-media f2r-media-viewport/, `${kind}`);
  }
  // 공개만 재생 트리거, 빌더는 정적
  assert.match(pub, /data-collection-play/);
  assert.doesNotMatch(bld, /data-collection-play/);
  assert.match(bld, /data-collection-static/, 'builder 는 정적 wrap');
});

test('빌더: 미니 강의 편집 모달·배선·블록이 존재한다', () => {
  assert.match(BUILDER_HTML, /id="builderMiniLecturesModal"/);
  assert.match(BUILDER_HTML, /id="builderMiniLecturesOptions"/);
  assert.match(BUILDER_HTML, /id="builderMiniLecturesApplyBtn"/);
  assert.match(BUILDER_HTML, /data-section-toggle="sec-minilectures"[^>]*data-section-key="sec-minilectures"/);
  assert.match(BUILDER_HTML, /is-storefront-optin[^>]*data-section-id="sec-minilectures"/);
  assert.match(BUILDER_JS, /function wireMiniLecturesEditing\(\)/);
  assert.match(BUILDER_JS, /function applyMiniLecturesFromModal\(\)/);
  assert.match(BUILDER_JS, /miniLectures: serialized/);
  // 별도 video player 를 만들지 않는다 — V2-8 mediaViewportHtml 재사용
  assert.doesNotMatch(SECTIONS_JS, /function miniLecturePlayer|f2r-lecture-player/);
});

test('CSS: Mini Lecture 카드는 V2-8 media viewport 계약 + --f2r-media-aspect 를 쓴다', () => {
  const compact = COLLECTION_CSS.replace(/\s+/g, '');
  assert.match(compact, /\.f2r-lecture-media\{[^}]*aspect-ratio:var\(--f2r-media-aspect,16\/9\)/);
  assert.match(compact, /\.f2r-lecture-card\{/);
  assert.match(compact, /\.f2r-collection--lectures\{--f2r-card-w/);
  // Mini Lecture 전용 플레이어 CSS 없음 — .f2r-media-player 공통 규칙을 그대로 탄다
  assert.doesNotMatch(compact, /\.f2r-lecture-player\{/);
});
