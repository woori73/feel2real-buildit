/* Eduholic homepage placement + Actual Class playback guard.
 * The dedicated 53-second midterm production video belongs only to the homepage
 * MAIN FEATURE. It must not leak into Actual Class (sec-videos) or a representative
 * lesson/program card merely because the uploaded asset is classified as LESSON.
 *
 * Actual Class is intentionally a phone-screen deck. Its thumbnail already uses
 * object-fit:cover, but the shared player uses object-fit:contain. For landscape or
 * mixed-aspect uploads that makes playback collapse into a short horizontal strip
 * (roughly 1/3 of the phone screen). Eduholic Actual Class therefore uses cover for
 * the player too, matching the pre-play thumbnail and keeping the phone viewport full.
 * This rule is scoped to .f2r-phone-screen only: MAIN FEATURE and representative
 * lesson/program media keep the shared contain behavior.
 *
 * Important: F2RAcademySections and the contexts it creates are Object.freeze()'d.
 * Do not monkey-patch properties on those frozen objects. Build replacement facades
 * instead, then let academy_public.js consume the replacement before it creates ctx.
 */
(function () {
  'use strict';
  var EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  var MAIN_FEATURE_MARKER = '내신대비_라이브_최종_53초';
  if (window.location.hostname !== EDUHOLIC_HOST) return;

  /* Keep this presentation fix local to Eduholic and to Actual Class phone cards.
   * A <style> node avoids changing the shared Academy collection contract for every
   * tenant. The selector is deliberately more specific than the shared
   * video.f2r-media-player { object-fit: contain } rule. */
  if (typeof document !== 'undefined' && document.head && !document.getElementById('f2r-eduholic-actual-class-fill')) {
    var style = document.createElement('style');
    style.id = 'f2r-eduholic-actual-class-fill';
    style.textContent = '.f2r-phone-screen > video.f2r-media-player{object-fit:cover;width:100%;height:100%;}';
    document.head.appendChild(style);
  }

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

  /* academy_sections.js exports Object.freeze({...}), so assigning
   * sections.createPublicContext = ... is a no-op/TypeError and was the reason the
   * previous production fixes had no effect. Replace the exported facade itself. */
  var facade = Object.assign({}, sections);
  facade.createPublicContext = function (deps) {
    var base = originalCreatePublicContext(deps);
    if (!base) return base;

    /* createPublicContext also returns Object.freeze({...}). Object.create(base)
     * cannot shadow inherited non-writable properties, so make an own-property copy. */
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
})();
