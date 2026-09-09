/* Eduholic homepage placement guard.
 * The 53-second midterm production video is a homepage HERO asset, not an
 * Actual Class (sec-videos) phone-card asset. Keep the shared LESSON taxonomy
 * intact and narrow only the Eduholic public sec-videos render path.
 */
(function () {
  'use strict';

  var EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  var MAIN_FEATURE_MARKER = '내신대비_라이브_최종_53초';

  if (window.location.hostname !== EDUHOLIC_HOST) return;

  var sections = window.F2RAcademySections;
  if (!sections || typeof sections.renderVideos !== 'function') return;

  var originalRenderVideos = sections.renderVideos;

  function isHomepageHero(video) {
    return !!(video && String(video.originalFilename || '').indexOf(MAIN_FEATURE_MARKER) >= 0);
  }

  sections.renderVideos = function (view, ctx) {
    if (!ctx || typeof ctx.videoList !== 'function') {
      return originalRenderVideos(view, ctx);
    }

    var scopedCtx = Object.create(ctx);
    scopedCtx.videoList = function () {
      var videos = ctx.videoList() || [];
      return videos.filter(function (video) { return !isHomepageHero(video); });
    };

    return originalRenderVideos(view, scopedCtx);
  };
})();
