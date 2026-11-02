(function () {
  'use strict';
  function init(doc, win) {
    var root = doc.getElementById('homeHero');
    if (!root) return null;
    var slides = Array.from(root.querySelectorAll('[data-hero-slide]'));
    var dots = Array.from(root.querySelectorAll('[data-hero-go]'));
    var pause = doc.getElementById('heroPause');
    var reduced = win.matchMedia('(prefers-reduced-motion: reduce)');
    var stopped = reduced.matches, hovered = false, focused = false, index = 0, timer;
    function schedule() {
      win.clearTimeout(timer);
      if (!stopped && !hovered && !focused && !doc.hidden) {
        timer = win.setTimeout(function () { show(index + 1, false); }, 3000);
      }
    }
    function show(next, announce) {
      index = (next + slides.length) % slides.length;
      slides.forEach(function (slide, i) { slide.hidden = i !== index; });
      dots.forEach(function (dot, i) { dot.setAttribute('aria-pressed', String(i === index)); });
      root.setAttribute('data-active', String(index));
      var count = doc.getElementById('heroCount');
      if (count) count.textContent = (index + 1) + ' / ' + slides.length;
      if (announce) doc.getElementById('heroStatus').textContent = slides[index].getAttribute('aria-label');
      schedule();
    }
    function syncPause() {
      pause.setAttribute('aria-pressed', String(stopped));
      pause.setAttribute('aria-label', stopped ? '배너 자동 전환 재생' : '배너 자동 전환 일시정지');
      pause.textContent = stopped ? '▶' : 'Ⅱ';
      schedule();
    }
    dots.forEach(function (dot, i) { dot.addEventListener('click', function () { show(i, true); }); });
    // 이전/다음 버튼은 선택 사항이다(밝은 개편 홈은 아래 타일 4개로 고른다).
    var next = doc.getElementById('heroNext'), prev = doc.getElementById('heroPrev');
    if (next) next.addEventListener('click', function () { show(index + 1, true); });
    if (prev) prev.addEventListener('click', function () { show(index - 1, true); });
    pause.addEventListener('click', function () { stopped = !stopped; syncPause(); });
    root.addEventListener('mouseenter', function () { hovered = true; schedule(); });
    root.addEventListener('mouseleave', function () { hovered = false; schedule(); });
    root.addEventListener('focusin', function () { focused = true; schedule(); });
    root.addEventListener('focusout', function (event) { focused = root.contains(event.relatedTarget); schedule(); });
    doc.addEventListener('visibilitychange', schedule);
    reduced.addEventListener('change', function () { if (reduced.matches) { stopped = true; syncPause(); } });
    root.querySelector('.hero-controls').hidden = false;
    show(0, false); syncPause();
    return { show: show, current: function () { return index; } };
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = { init: init };
  if (typeof document !== 'undefined') init(document, window);
})();
