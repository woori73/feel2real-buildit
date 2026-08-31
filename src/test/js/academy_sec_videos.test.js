const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { bothRenderers, loadRenderHooks, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * sec-videos(수업 영상) 섹션의 교차 파일 정합성.
 *
 * DEFAULT_SECTION_ORDER는 서버/공개/빌더 3개 파일에 각각 복제되어 있다. 서버 size-lock은
 * 완화됐지만(빠진 섹션은 canonical 위치로 채워 넣음), 세 목록이 어긋나면 새 섹션이 JS가
 * 따라잡기 전까지 기본 위치에 렌더되므로 이 테스트가 여전히 정합성을 못박는다.
 */

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');

const academyServiceJava = read('../../main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyService.java');
const publicJs = read('../../main/resources/static/js/academy_public.js');
const builderJs = read('../../main/resources/static/js/academy_builder.js');
const builderTemplate = read('../../main/resources/templates/tutor/academy_builder.html');
const tutorAcademyTemplate = read('../../main/resources/templates/tutor/tutor_academy.html');

/**
 * `"sec-a", "sec-b"` / `'sec-a', 'sec-b'` 형태의 선언에서 섹션 id를 순서대로 뽑는다.
 * JS는 `[...]`, Java는 `List.of(...)`라 닫는 기호가 다르다 — 문(statement) 종결자 `;`까지 자른다.
 */
function sectionIdsFrom(source, declaration) {
  const start = source.indexOf(declaration);
  assert.notEqual(start, -1, `선언을 찾지 못했다: ${declaration}`);
  const body = source.slice(start, source.indexOf(';', start) + 1);
  return [...body.matchAll(/['"](sec-[a-z0-9-]+)['"]/g)].map((m) => m[1]);
}

const serverOrder = sectionIdsFrom(academyServiceJava, 'static final List<String> DEFAULT_SECTION_ORDER');
const publicOrder = sectionIdsFrom(publicJs, 'const DEFAULT_SECTION_ORDER');
const builderOrder = sectionIdsFrom(builderJs, 'const DEFAULT_SECTION_ORDER');
const builderManaged = sectionIdsFrom(builderJs, 'const MANAGED_SECTION_IDS');

test('given 섹션 순서 정의 when 서버·공개·빌더를 비교 then 세 곳이 완전히 동일하다', () => {
  // given / when / then — 길이가 다르면 validateSectionOrder의 size-lock에서 400이 난다
  assert.deepEqual(publicOrder, serverOrder);
  assert.deepEqual(builderOrder, serverOrder);
});

test('given 빌더 MANAGED_SECTION_IDS when 기본 순서와 비교 then hero만 제외한 나머지와 일치한다', () => {
  // given / when / then — hero는 항상 ON 고정이라 토글 목록에서 빠진다
  assert.deepEqual(builderManaged, serverOrder.filter((id) => id !== 'sec-hero'));
});

test('given sec-videos when 각 정의를 확인 then 학습 철학 뒤에 등록되어 있다', () => {
  // given / when / then
  assert.ok(serverOrder.includes('sec-videos'), '서버 DEFAULT_SECTION_ORDER에 없음');
  assert.equal(serverOrder[serverOrder.indexOf('sec-principal') + 1], 'sec-philosophy');
  assert.equal(serverOrder[serverOrder.indexOf('sec-philosophy') + 1], 'sec-videos');
});

test('given sec-videos when 빌더 렌더 분기를 확인 then 준비중 placeholder가 아니라 실제 렌더로 연결된다', () => {
  // given / when / then — 지원 섹션 분기가 있고 미래 섹션 집합에는 포함되지 않아야 한다
  assert.match(builderJs, /if \(id === 'sec-videos'\) return renderVideos\(academy\);/);
  assert.match(publicJs, /if \(id === 'sec-videos'\) return renderVideos\(academy\);/);
  const futureIds = sectionIdsFrom(builderJs, 'const FUTURE_SECTION_IDS');
  assert.ok(!futureIds.includes('sec-videos'));
});

test('given 빌더 좌측 블록 목록 when sec-videos 카드를 확인 then 토글 가능한 데이터 연동 섹션으로 노출된다', () => {
  // given / when / then
  assert.match(builderTemplate, /data-section-toggle="sec-videos"/);
  assert.match(builderTemplate, /data-section-id="sec-videos"[^>]*data-section-toggle="sec-videos"/);
  assert.match(builderTemplate, /data-section-id="sec-philosophy"[^>]*data-section-toggle="sec-philosophy"/);
});

test('given 영상이 없을 때 when 공개·빌더를 렌더 then 섹션은 유지하고 준비중 placeholder를 보여준다', () => {
  // 빌더에서 ON으로 발행했는데 공개 페이지에서 섹션이 통째로 사라지면 강사가 발행 실패로
  // 오해한다. 다른 준비중 섹션과 동일하게 placeholder를 노출한다.
  for (const [name, hooks] of bothRenderers()) {
    // given
    const academy = academyFixture();

    // when — 영상 목록이 빈 상태
    const html = primeRenderer(hooks, academy, []).renderVideos(academy);

    // then
    assert.match(html, /<section id="sec-videos"/, `${name}: 섹션이 통째로 사라짐`);
    assert.match(html, /수업 영상을 준비 중입니다/, `${name}: 준비중 placeholder 없음`);
    assert.doesNotMatch(html, /video-playlist-layout/, `${name}: 영상이 없는데 재생목록이 나옴`);
  }
});

test('given RAW Ready 영상에 thumbnail이 없을 때 then 공개·빌더 모두 깨진 img 대신 중립 placeholder를 쓴다', () => {
  // given — 첫 영상은 썸네일 없음, 둘째는 있음
  const videos = [
    { videoId: 1, videoUrl: '/media/1.mp4', thumbnailUrl: null, durationSec: 75 },
    { videoId: 2, videoUrl: '/media/2.mp4', thumbnailUrl: '/media/2.jpg', durationSec: 30 }
  ];

  for (const [name, hooks] of bothRenderers()) {
    // when
    const academy = academyFixture();
    const html = primeRenderer(hooks, academy, videos).renderVideos(academy);

    // then — 썸네일 없는 항목은 중립 placeholder
    assert.match(html, /video-playlist-thumb-placeholder/, `${name}: null thumbnail placeholder 없음`);
    // then — 있는 항목은 img
    assert.match(html, /<img class="video-playlist-thumb" src="\/media\/2\.jpg"/, `${name}: 썸네일 img 없음`);
    // then — 첫 영상에 썸네일이 없으므로 메인 플레이어에 poster를 붙이지 않는다
    assert.doesNotMatch(html, /poster=/, `${name}: 썸네일이 없는데 poster를 붙임`);
  }
});

test('given sec-videos 섹션 골격 when 공개·빌더를 렌더 then 제목만 남고 입력 안내 요소는 없다', () => {
  // 배지("수업 영상")와 안내문은 빌더 입력 안내 성격이라 학원 페이지에서 뺐다.
  // cv-eyebrow/cv-h2는 히어로 전용이라 섹션에 쓰면 디자인이 어긋난다.
  for (const [name, hooks] of bothRenderers()) {
    // given
    const academy = academyFixture({ name: '테스트학원' });

    // when
    const html = primeRenderer(hooks, academy, []).renderVideos(academy);

    // then — 제목은 videosSectionTitle draft로 독립 편집한다. 미설정이면 학원명에서 파생한 기본값.
    assert.match(html, /<h2 class="section-title"[^>]*>테스트학원의 수업 현장<\/h2>/, `${name}: section-title 없음`);
    if (name === 'builder') {
      assert.match(html, /data-builder-draft="videosSectionTitle"/, 'builder: 수업 현장 제목 편집 앵커 없음');
    }
    assert.doesNotMatch(html, /<span class="section-badge">수업 영상<\/span>/, `${name}: 배지가 남아 있음`);
    assert.doesNotMatch(html, /<p class="section-sub">/, `${name}: 안내문이 남아 있음`);
    assert.doesNotMatch(html, /cv-eyebrow|cv-h2/, `${name}: 히어로 전용 클래스가 남아 있음`);
  }
});

test('given 재생목록 when 공개·빌더를 렌더 then 공개만 클릭 재생이 되고 빌더는 비인터랙티브다', () => {
  // given
  const videos = [{ videoId: 1, videoUrl: '/media/1.mp4', thumbnailUrl: '/media/1.jpg', durationSec: 75 }];
  const academy = academyFixture();

  // when
  const publicHtml = primeRenderer(loadRenderHooks('public'), academy, videos).renderVideos(academy);
  const builderHtml = primeRenderer(loadRenderHooks('builder'), academy, videos).renderVideos(academy);

  // then — 공개는 클릭하면 메인 플레이어 src를 갈아끼운다. id가 없으면 그 배선이 죽는다.
  assert.match(publicHtml, /<button type="button" class="video-playlist-item active"/);
  assert.match(publicHtml, /data-video-select="\/media\/1\.mp4"/);
  assert.match(publicHtml, /id="academyVideoPlayer"/);

  // then — 빌더 캔버스는 재생 전환을 걸지 않는다
  assert.match(builderHtml, /<div class="video-playlist-item active">/);
  assert.doesNotMatch(builderHtml, /data-video-select/);
  assert.doesNotMatch(builderHtml, /id="academyVideoPlayer"/);
});

test('given 강사 Academy 화면 when 진입점을 확인 then 영상 관리 페이지로 가는 링크가 있다', () => {
  // given / when / then — 빌더 안 버튼은 스타일 컨트롤러에 가려 클릭이 어려워
  // /tutor/academy에 별도 진입점을 둔다
  assert.match(tutorAcademyTemplate, /href="\/tutor\/academy\/videos"/);
  assert.match(tutorAcademyTemplate, /영상 관리하기/);
});

test('given 발행된 Academy when 빌더 수업 영상 섹션을 렌더 then 업로드 관리 화면으로 가는 링크가 있다', () => {
  // given — 발행된 Academy만 관리 화면으로 이동한다
  const academy = academyFixture({ publicPathUrl: '/academies/test-academy' });

  // when
  const html = primeRenderer(loadRenderHooks('builder'), academy, []).renderVideos(academy);

  // then
  assert.match(html, /<a class="cv-btn-primary builder-video-manage" href="\/tutor\/academy\/videos"/);
  assert.doesNotMatch(html, /data-video-publish-required/);
  // 미리보기 CTA는 클릭이 막혀 있는데 이 버튼만 예외로 열어둔다.
  assert.match(
    builderTemplate,
    /\.builder-public-preview \.cv-btn-primary\.builder-video-manage\{pointer-events:auto;cursor:pointer;\}/);
});

test('given 발행 전 Academy when 빌더 수업 영상 섹션을 렌더 then 링크 대신 발행 안내 버튼을 낸다', () => {
  // given — publicPathUrl이 없으면 아직 발행 전이다
  const academy = academyFixture();

  // when
  const html = primeRenderer(loadRenderHooks('builder'), academy, []).renderVideos(academy);

  // then
  assert.match(html, /<button type="button" class="cv-btn-primary builder-video-manage" data-video-publish-required/);
  assert.doesNotMatch(html, /href="\/tutor\/academy\/videos"/, '발행 전인데 관리 링크가 열려 있음');

  // then — 클릭 차단 배선과 안내 문구는 빌더에 남아 있다
  assert.match(builderJs, /wireVideoManagement\(\)/);
  assert.match(builderJs, /'학원 홈페이지를 먼저 발행한 후 영상을 등록할 수 있습니다\.'/);
});

test('given GNB와 히어로 when 공개·빌더를 렌더 then 입력 안내용 이동 버튼이 없다', () => {
  // 발행 쪽만 지우고 빌더를 빠뜨려 원장 화면에 그대로 남았던 적이 있다.
  for (const [name, hooks] of bothRenderers()) {
    // given — 대표 수업 1개를 고른 상태
    const academy = academyFixture({
      lessons: [{ lessonId: 1, name: '수업 1' }],
      pageSettings: { builderDraft: { featuredLessonIds: '1' } }
    });
    primeRenderer(hooks, academy);

    // when
    const gnb = hooks.renderGnb(academy, '');
    const hero = hooks.renderHero(academy);

    // then — 제거한 안내용 이동 버튼들
    assert.doesNotMatch(gnb, /href="#sec-(intro|principal|lessons|social)"/, `${name}: GNB 앵커가 남아 있음`);
    assert.doesNotMatch(hero, /학원 소개 보기/, `${name}: 히어로 '학원 소개 보기' CTA가 남아 있음`);
    assert.doesNotMatch(hero, /cv-cta-ghost/, `${name}: 고아가 된 ghost 버튼 클래스가 남아 있음`);
    // 새 시안에는 "대표 수업 보기" CTA만 남긴다.
    assert.doesNotMatch(hero, /수업상품 보기/, `${name}: V8에서 제거한 CTA가 남아 있음`);

    // then — 대체 통로는 살아 있다
    assert.match(hero, /대표 수업 보기/, `${name}: CTA를 대신할 대표 수업 통로가 없음`);
    assert.match(hero, /scrollToId\('sec-lessons'\)/, `${name}: 수업 목록으로 가는 통로가 사라짐`);
  }
  // 빌더의 편집 진입점은 살아 있어야 한다 — 지우면 원장이 값을 고칠 통로를 잃는다.
  assert.match(builderJs, /data-consult-contact-edit/);
});

test('given GNB 학원명 when 공개·빌더를 확인 then 히어로 제목과 별개인 headerTitle draft를 쓴다', () => {
  // given / when / then — 이전에는 name을 그대로 비추는 mirror라 상단만 따로 못 고쳤다.
  for (const [name, source] of [['public', publicJs], ['builder', builderJs]]) {
    assert.match(source, /draftText\(academy, 'headerTitle', academy\.name\)/, `${name}: headerTitle 폴백이 academy.name이 아님`);
  }
  // 빌더에서만 편집 가능해야 한다.
  assert.match(builderJs, /data-builder-draft="headerTitle"/);
  // 편집은 섹션 선택을 거친다. GNB에 section-key가 없으면 편집 훅만 있고 클릭이 먹지 않는다.
  assert.match(builderJs, /data-builder-section-key="\$\{FIXED_HEADER_SECTION_ID\}"/, 'builder: GNB가 선택 가능한 섹션이 아님');
  assert.match(builderJs, /FIXED_HEADER_SECTION_ID = 'sec-header'/);
  // 고정 영역이므로 순서 목록에 넣으면 서버 sectionOrder size-lock이 깨진다.
  assert.doesNotMatch(builderJs, /DEFAULT_SECTION_ORDER = \[[^\]]*'sec-header'/s, 'builder: sec-header가 순서 목록에 들어감');
  assert.doesNotMatch(publicJs, /data-builder-draft/, 'public: 발행 화면에 편집 훅이 새어나감');
  // mirror는 렌더되지 않아야 한다(양쪽 다 draft/직접 렌더로 대체됨).
  assert.doesNotMatch(builderJs, /data-builder-text-mirror/, 'builder: 쓰이지 않는 mirror가 남아 있음');
});

test('given 푸터 브랜드 when 공개·빌더를 확인 then 로고만 남고 학원명 텍스트는 없다', () => {
  // given / when / then — 로고 심볼은 유지하고 텍스트만 뺀다.
  for (const [name, source] of [['public', publicJs], ['builder', builderJs]]) {
    const start = source.indexOf('foot-top');
    const brand = source.slice(start, start + 400);
    assert.doesNotMatch(brand, /eh-logo-title/, `${name}: 푸터에 학원명 텍스트가 남아 있음`);
    assert.match(brand, /renderLogoSymbol/, `${name}: 푸터 로고 심볼이 사라짐`);
  }
});

test('given 히어로 강조 문구 when 공개·빌더를 렌더 then 과목 전문 element를 렌더하지 않는다', () => {
  // 카톡 피드백에 따라 학원명 아래 "00 전문" 줄을 제거한다.
  for (const [name, hooks] of bothRenderers()) {
    // given
    const academy = academyFixture();

    // when
    const hero = primeRenderer(hooks, academy).renderHero(academy, '영어', '강남구');

    // then
    assert.doesNotMatch(hero, /class="accent"/, `${name}: 제거된 accent element가 남아 있음`);
  }
});

test('given sec-intro when 공개·빌더를 렌더 then 입력 안내 라벨과 구 요약 레이아웃이 없다', () => {
  // 과목/학원 특징 라벨은 빌더 입력 안내 성격이라 뺐고,
  // 구 2열 요약 레이아웃은 BuildIt notes 블록으로 전면 교체됐다.
  for (const [name, hooks] of bothRenderers()) {
    // given
    const academy = academyFixture({ pageSettings: { builderDraft: { introBodySecondary: '본문값' } } });
    primeRenderer(hooks, academy);

    // when
    const intro = hooks.renderIntro(academy);
    const hero = hooks.renderHero(academy, '영어', '강남구');

    // then — 구 레이아웃 잔재가 없다
    assert.doesNotMatch(intro, /intro-summary-label/, `${name}: 입력 안내 라벨이 남아 있음`);
    assert.doesNotMatch(intro, /intro-subject-badges/, `${name}: sec-intro 과목 배지가 남아 있음`);
    assert.doesNotMatch(intro, /intro-divider/, `${name}: 구분선이 남아 있음`);
    assert.doesNotMatch(intro, /intro-summary-area|intro-summary-block/, `${name}: 구 요약 레이아웃이 남아 있음`);

    // then — 남기기로 한 것들
    assert.match(intro, /intro-feature-list/, `${name}: 학원 특징 목록이 사라짐`);
    assert.ok(intro.includes('본문값'), `${name}: 본문(introBodySecondary)이 렌더되지 않음`);

    // then — 교재 카드는 새 히어로 시안에서 제거됐다.
    assert.doesNotMatch(hero, /textbook-card|book-slot/, `${name}: 구 교재 카드가 남아 있음`);
  }

  // then — 편집 통로는 히어로에 남아야 한다. sec-intro에서 지웠다고 못 고치게 되면 안 된다.
  const builderHooks = bothRenderers()[1][1];
  const academy = academyFixture();
  const builderHero = primeRenderer(builderHooks, academy).renderHero(academy, '영어', '강남구');
  assert.match(builderHero, /cv-hero-subject-badges" data-builder-basic="subjects"/, 'builder: 과목 편집 진입점 소실');
  assert.match(builderHero, /hero-quote builder-multiline-text"[^>]* data-builder-text="intro"/, 'builder: 소개 편집 진입점 소실');
});

test('given 영상 플레이리스트 when 공개·빌더를 렌더 then 양쪽 모두 순번과 재생시간을 표시한다', () => {
  // 빌더 캔버스에만 meta가 빠져 발행 화면과 다르게 보였던 적이 있다.
  const videos = [
    { videoId: 1, videoUrl: '/media/1.mp4', thumbnailUrl: null, durationSec: 75 },
    { videoId: 2, videoUrl: '/media/2.mp4', thumbnailUrl: null, durationSec: 5 }
  ];

  for (const [name, hooks] of bothRenderers()) {
    // when
    const academy = academyFixture();
    const html = primeRenderer(hooks, academy, videos).renderVideos(academy);

    // then
    assert.match(html, /video-playlist-meta/, `${name}: 재생목록 meta 없음`);
    assert.match(html, /<span class="video-playlist-index">1<\/span>/, `${name}: 순번 없음`);
    assert.match(html, /<span class="video-playlist-duration">1:15<\/span>/, `${name}: 재생시간 포맷이 다름`);
    // 초가 한 자리면 0을 채운다
    assert.match(html, /<span class="video-playlist-duration">0:05<\/span>/, `${name}: 초 zero-padding 없음`);
  }
});
