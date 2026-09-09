/* Eduholic homepage placement guard.
 * The dedicated 53-second midterm production video belongs only to the homepage
 * MAIN FEATURE. It must not leak into Actual Class (sec-videos) or a representative
 * lesson/program card merely because the uploaded asset is classified as LESSON. */
(function () {
  'use strict';
  var EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  var MAIN_FEATURE_MARKER = '내신대비_라이브_최종_53초';
  if (window.location.hostname !== EDUHOLIC_HOST) return;

  var sections = window.F2RAcademySections;
  if (!sections || typeof sections.createPublicContext !== 'function') return;
  var originalCreatePublicContext = sections.createPublicContext;

  function isHomepageHero(video) {
    return !!(video && String(video.originalFilename || '').indexOf(MAIN_FEATURE_MARKER) >= 0);
  }

  function heroVideoId(videos) {
    var hero = (videos || []).find(isHomepageHero);
    return hero && hero.videoId != null ? String(hero.videoId) : null;
  }

  /* Patch the public context before academy_public.js creates it.  This is the
   * shared data boundary used by both Actual Class and representative Program
   * Cards; the global F2RAcademyPublicVideos array remains untouched so the
   * dedicated MAIN FEATURE can still select and render the same asset. */
  sections.createPublicContext = function (deps) {
    var base = originalCreatePublicContext(deps);
    if (!base) return base;

    var scoped = Object.create(base);
    scoped.videoList = function () {
      return (base.videoList() || []).filter(function (video) { return !isHomepageHero(video); });
    };
    scoped.programCardMedia = function (lesson) {
      var id = lesson && lesson.mediaVideoId != null ? String(lesson.mediaVideoId) : null;
      var heroId = heroVideoId(base.videoList());
      if (id && heroId && id === heroId) return '';
      return base.programCardMedia(lesson);
    };
    return scoped;
  };
})();
