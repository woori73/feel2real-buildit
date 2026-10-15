/* ============================================================
   tmpl-minimal-landing — 등장 페이드 스크립트 (바닐라 JS)
   100% 오리지널 코드 (MIT)
   ============================================================ */
(function () {
  "use strict";

  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var targets = document.querySelectorAll(".bi-fade");

  // 모션 감소 설정 시 즉시 표시
  if (reduceMotion || !("IntersectionObserver" in window)) {
    targets.forEach(function (el) { el.classList.add("bi-is-in"); });
    return;
  }

  // 순차 등장: 화면 진입 시 .bi-is-in 추가, 요소별 약간의 시차
  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        var el = entry.target;
        var siblings = Array.prototype.slice.call(
          el.parentElement.querySelectorAll(".bi-fade")
        );
        var idx = siblings.indexOf(el);
        el.style.transitionDelay = Math.min(idx * 90, 360) + "ms";
        el.classList.add("bi-is-in");
        io.unobserve(el);
      }
    });
  }, { threshold: 0.15, rootMargin: "0px 0px -6% 0px" });

  targets.forEach(function (el) { io.observe(el); });
})();
