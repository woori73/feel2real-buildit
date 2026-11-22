/* F2R 홈 입체감(2026-11-22): 로고·구역 제목·지도 위치(home_depth.css).
 * - 화면에 들어온 구역 제목·큰 로고·지도 위치 카드에 .hd-in을 붙여 일어서기·한 바퀴·핀 떨어짐을 켠다.
 * - 큰 로고와 지도 위치 카드는 마우스·손가락 위치 쪽으로 살짝 기운다(--hd-rx, --hd-ry).
 * 움직임 줄이기 설정이거나 IntersectionObserver가 없으면 .hd-ready를 붙이지 않아 아무것도 숨기거나 눕히지 않는다. */
(function () {
  'use strict';
  var REVEAL = '.hl-head h2, .hw-coord-logo, .hd-loc';
  var TILT = '.hw-coord-logo, .hd-loc';
  var MAX_TILT = 12;

  // 요소 상자(rect) 안의 점(x, y)을 기울기 각도로 바꾼다. 가운데는 0도, 가장자리는 ±max도, 상자 밖은 ±max에서 멈춘다.
  function tiltFromPointer(rect, x, y, max) {
    var limit = typeof max === 'number' ? max : MAX_TILT;
    if (!rect || !(rect.width > 0) || !(rect.height > 0)) return { rx: 0, ry: 0 };
    var nx = Math.max(-1, Math.min(1, ((x - rect.left) / rect.width) * 2 - 1));
    var ny = Math.max(-1, Math.min(1, ((y - rect.top) / rect.height) * 2 - 1));
    // 오른쪽을 가리키면 오른쪽이 멀어지도록(rotateY +), 아래를 가리키면 아래가 멀어지도록(rotateX -)
    return { rx: round(-ny * limit), ry: round(nx * limit) };
  }
  function round(v) { var r = Math.round(v * 10) / 10; return r === 0 ? 0 : r; }

  function bindTilt(el) {
    function move(e) {
      var t = tiltFromPointer(el.getBoundingClientRect(), e.clientX, e.clientY);
      el.style.setProperty('--hd-rx', t.rx + 'deg');
      el.style.setProperty('--hd-ry', t.ry + 'deg');
    }
    function reset() {
      el.style.setProperty('--hd-rx', '0deg');
      el.style.setProperty('--hd-ry', '0deg');
    }
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerleave', reset);
    el.addEventListener('blur', reset);
  }

  function init(doc, win) {
    var root = doc.getElementById('homeTop');
    if (!root) return null;
    var reduced = win.matchMedia && win.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || typeof win.IntersectionObserver !== 'function') return { active: false };
    var items = Array.prototype.slice.call(doc.querySelectorAll(REVEAL));
    root.classList.add('hd-ready');
    var observer = new win.IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('hd-in');
        observer.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.2 });
    items.forEach(function (el) { observer.observe(el); });
    var tilted = Array.prototype.slice.call(doc.querySelectorAll(TILT));
    tilted.forEach(bindTilt);
    return { active: true, items: items, tilted: tilted };
  }

  var api = { init: init, tiltFromPointer: tiltFromPointer };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof document !== 'undefined') init(document, window);
})();
