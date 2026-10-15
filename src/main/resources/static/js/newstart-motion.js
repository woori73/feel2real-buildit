/* NewStart 기능성 모션 Phase 1 — 스크롤 진입 reveal (1회성)
 * prefers-reduced-motion 또는 IntersectionObserver 미지원이면 아무 것도 하지 않는다.
 * JS 없는 환경에서는 ns-motion-on이 붙지 않아 [data-ns-reveal] 콘텐츠가 항상 보인다. */
(function () {
  'use strict';

  function prefersReduced() {
    return typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  if (prefersReduced() || typeof IntersectionObserver !== 'function') return;

  var targets = Array.prototype.slice.call(
    document.querySelectorAll('[data-ns-reveal], [data-ns-reveal-group]'));
  if (!targets.length) return;

  document.documentElement.classList.add('ns-motion-on');

  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('ns-revealed');
      io.unobserve(entry.target); // 1회성: 스크롤을 오갈 때마다 재재생하지 않는다.
    });
  }, { threshold: 0.08, rootMargin: '0px 0px -6% 0px' });

  targets.forEach(function (el) { io.observe(el); });
})();
