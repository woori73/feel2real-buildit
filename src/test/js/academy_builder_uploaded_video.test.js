const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { loadRenderHooks, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');

const builderJs = read('../../main/resources/static/js/academy_builder.js');
const publicJs = read('../../main/resources/static/js/academy_public.js');
const builderTemplate = read('../../main/resources/templates/tutor/academy_builder.html');
const academyService = read('../../main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyService.java');

test('홈페이지 영상 선택값은 허용된 builderDraft key로 저장된다', () => {
  assert.match(academyService, /"heroVideoId"/);
  assert.match(academyService, /"principalVideoId"/);
  assert.match(academyService, /heroVideoId.*principalVideoId|principalVideoId.*heroVideoId/s);
});

test('히어로와 원장 영상 모달에서 기존 업로드 영상을 선택할 수 있다', () => {
  assert.match(builderTemplate, /data-home-video-list="hero"/);
  assert.match(builderTemplate, /data-home-video-list="principal"/);
  assert.match(builderTemplate, /href="\/tutor\/academy\/videos"/);
  assert.match(builderTemplate, /data-home-video-clear="hero"/);
  assert.match(builderTemplate, /data-home-video-clear="principal"/);
  assert.match(builderJs, /\/api\/tutor\/academy\/videos/);
  assert.match(builderJs, /data-home-video-id/);
});

test('업로드 영상을 선택하면 YouTube 값과 배타적으로 저장한다', () => {
  assert.match(builderJs, /heroVideoId:\s*String\(videoId\)/);
  assert.match(builderJs, /pageSettings\.heroYoutubeUrl\s*=\s*null/);
  assert.match(builderJs, /principalVideoId:\s*String\(videoId\)/);
  assert.match(builderJs, /principalYoutubeUrl:\s*''/);
});

test('given 원장 소개 영상이 목록에 있을 때 when 렌더 then 양쪽 모두 video 태그로 재생한다', () => {
  // given
  const academy = academyFixture({ pageSettings: { builderDraft: { principalVideoId: '7' } } });
  const videos = [{ videoId: 7, videoUrl: '/media/7.mp4', thumbnailUrl: '/media/7.jpg' }];

  for (const kind of ['public', 'builder']) {
    // when
    const html = primeRenderer(loadRenderHooks(kind), academy, videos).renderPrincipal(academy);

    // then
    assert.match(html, /<video class="principal-video-frame" controls playsinline preload="metadata"/, `${kind}: HTML5 video controls 없음`);
    assert.match(html, /src="\/media\/7\.mp4"/, `${kind}: 영상 URL 없음`);
    assert.match(html, /poster="\/media\/7\.jpg"/, `${kind}: 썸네일 poster 없음`);
  }
});

test('given 원장 소개 영상이 저장돼 있을 때 when 목록에 없으면 then 공개는 재생을 시도하고 빌더는 placeholder를 띄운다', () => {
  // 공개 페이지는 영상 목록 조회가 실패해도 화면이 비지 않아야 한다 — id가 유효하면
  // stream URL을 합성해 재생을 시도한다. 빌더는 아직 발행 전일 수 있어 그렇게 하지 않는다.
  const academy = academyFixture({ pageSettings: { builderDraft: { principalVideoId: '7' } } });

  // when — 영상 목록이 비어 있는 상태
  const publicHtml = primeRenderer(loadRenderHooks('public'), academy, []).renderPrincipal(academy);
  const builderHtml = primeRenderer(loadRenderHooks('builder'), academy, []).renderPrincipal(academy);

  // then
  assert.match(publicHtml, /<video class="principal-video-frame" controls playsinline preload="metadata"/,
    'public: 목록에 없어도 stream URL로 재생을 시도해야 한다');
  assert.match(publicHtml, /\/videos\/7\/stream/);
  assert.doesNotMatch(builderHtml, /<video/, 'builder: 목록에 없으면 placeholder여야 한다');
  assert.match(builderHtml, /principal-video-overlay/);
});

test('given 히어로 업로드 영상 when 렌더 then 새 시안 히어로에는 영상 카드를 두지 않는다', () => {
  // given — 값은 legacy 저장 호환 때문에 허용하지만, 새 시안 히어로 렌더 대상은 아니다.
  const academy = academyFixture({ pageSettings: { builderDraft: { heroVideoId: '9' } } });
  const videos = [{ videoId: 9, videoUrl: '/media/9.mp4', thumbnailUrl: '/media/9.jpg' }];

  for (const kind of ['public', 'builder']) {
    // when
    const html = primeRenderer(loadRenderHooks(kind), academy, videos).renderHero(academy);

    // then
    assert.doesNotMatch(html, /cv-hero-video-frame/, `${kind}: 구 히어로 영상 카드가 남아 있음`);
    assert.doesNotMatch(html, /src="\/media\/9\.mp4"/, `${kind}: 히어로 영상이 새 시안에 렌더됨`);
    assert.doesNotMatch(html, /cv-play-inner/, `${kind}: 구 재생 아이콘 placeholder가 남아 있음`);
  }
});
