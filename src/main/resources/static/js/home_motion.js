/* 맞춤문제 2만원 서비스(#service) 모션: 화면에 들어오면 블록이 차례로 떠오르고, 적중 수치가 0부터 올라가며,
 * 제작 과정 단계 선이 채워진다. 움직임 줄이기 설정이거나 IntersectionObserver가 없으면 아무것도 숨기지 않는다. */
(function () {
  'use strict';
  var REVEAL = '#service > section, #service .hit-stats li, #service .exam-card, #service .hl-pay > div';
  var DURATION = 1200;

  // "93.5%" → {value:93.5, decimals:1, suffix:"%"}, "14/14" → {value:14, decimals:0, suffix:"/14"}
  function parseCount(text) {
    var m = /^(\d+(?:\.\d+)?)(.*)$/.exec(String(text).trim());
    if (!m) return null;
    return { value: parseFloat(m[1]), decimals: (m[1].split('.')[1] || '').length, suffix: m[2] };
  }
  function formatCount(count, progress) {
    var p = Math.min(1, Math.max(0, progress));
    return (count.value * p).toFixed(count.decimals) + count.suffix;
  }
  function ease(t) { return 1 - Math.pow(1 - t, 3); }

  function countUp(el, win) {
    var count = parseCount(el.textContent);
    if (!count) return;
    var start = null;
    function step(now) {
      if (start === null) start = now;
      var t = Math.min(1, (now - start) / DURATION);
      el.textContent = formatCount(count, ease(t));
      if (t < 1) win.requestAnimationFrame(step);
    }
    win.requestAnimationFrame(step);
  }

  function init(doc, win) {
    var root = doc.getElementById('service');
    if (!root) return null;
    var reduced = win.matchMedia && win.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || typeof win.IntersectionObserver !== 'function') return { active: false };
    var items = Array.prototype.slice.call(doc.querySelectorAll(REVEAL));
    items.forEach(function (el, i) {
      el.classList.add('mo-reveal');
      el.style.setProperty('--mo-delay', (i % 4) * 90 + 'ms');
    });
    root.classList.add('motion-ready');
    var observer = new win.IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var el = entry.target;
        el.classList.add('is-in');
        observer.unobserve(el);
        if (el.matches && el.matches('.hit-stats li')) {
          var number = el.querySelector('b');
          if (number) countUp(number, win);
        }
      });
    }, { rootMargin: '0px 0px -10% 0px', threshold: 0.15 });
    items.forEach(function (el) { observer.observe(el); });
    return { active: true, items: items };
  }

  var api = { init: init, parseCount: parseCount, formatCount: formatCount };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof document !== 'undefined') init(document, window);
})();
