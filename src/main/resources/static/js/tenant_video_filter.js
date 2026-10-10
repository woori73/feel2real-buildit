/* BuildIt 2단계 — 테넌트 Actual Class 영상 필터.
 *
 * eduholic_actual_class_video_filter.js 의 테넌트 일반화판. "홈페이지 MAIN FEATURE
 * 전용 영상이 Actual Class(sec-videos)나 대표 수업 카드에 새어 나가지 않게 한다"는
 * 동작은 동일하고, 하드코딩된 호스트·표식 문자열만 학원별 설정
 * (builderDraft['tenant.videoFilter'])에서 읽는다.
 *
 * 설정 스키마 (JSON):
 *   { "enabled": true, "marker": "homepage-hero-53s" }
 * enabled !== true 이거나 marker 가 비어 있으면 필터링하지 않는다.
 *
 * 타이밍: 이 스크립트는 academy_public.js 보다 먼저(동기) 로드된다. 설정은 비동기로
 * 도착하므로, facade 래퍼를 먼저 설치하고 marker 는 가변 변수로 둔다. 설정 fetch 는
 * academy_public.js 의 academy fetch 와 같은 엔드포인트라 1회 왕복으로 끝나고,
 * 공개 페이지의 videos fetch + render 보다 항상 먼저 완료된다.
 *
 * 중요: F2RAcademySections 와 그 context 들은 Object.freeze() 되어 있다.
 * 프로퍼티를 직접 패치하지 말고 교체용 facade 를 만들어 window 에 올린다.
 */
(function () {
  'use strict';

  // eduholic 호스트는 기존 eduholic_actual_class_video_filter.js 가 담당한다.
  if (window.location.hostname === 'eduholic.feel2real.co.kr') return;

  var activeMarker = null;

  function isHomepageHero(video) {
    return !!(activeMarker && video
      && String(video.originalFilename || '').indexOf(activeMarker) >= 0);
  }

  function heroVideoId(videos) {
    var hero = (videos || []).find(isHomepageHero);
    return hero && hero.videoId != null ? String(hero.videoId) : null;
  }

  // academy_public.js 보다 먼저 동기 실행되므로 facade 를 즉시 교체한다.
  var sections = window.F2RAcademySections;
  if (sections && typeof sections.createPublicContext === 'function') {
    var originalCreatePublicContext = sections.createPublicContext;
    var facade = Object.assign({}, sections);
    facade.createPublicContext = function (deps) {
      var base = originalCreatePublicContext(deps);
      if (!base) return base;
      var scoped = Object.assign({}, base);
      scoped.videoList = function () {
        return (base.videoList() || []).filter(function (video) { return !isHomepageHero(video); });
      };
      scoped.programCardMedia = function (lesson) {
        var id = lesson && lesson.mediaVideoId != null ? String(lesson.mediaVideoId) : null;
        var heroId = heroVideoId(base.videoList());
        if (id && heroId && id === heroId) return '';
        return base.programCardMedia(lesson);
      };
      return Object.freeze(scoped);
    };
    window.F2RAcademySections = Object.freeze(facade);
  }

  // 설정 도착 시 marker 활성화. render() 보다 먼저 완료되는 것이 정상 경로이며,
  // 늦게 도착해도 videoList() 호출 시점의 값을 읽으므로 다음 렌더부터 적용된다.
  if (window.F2RTenantSettings) {
    window.F2RTenantSettings.get('tenant.videoFilter').then(function (cfg) {
      if (cfg && cfg.marker) activeMarker = String(cfg.marker);
    }).catch(function () {});
  }
})();
