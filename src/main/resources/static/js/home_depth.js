/* F2R 홈 입체감(2026-11-22): 로고·구역 제목·지도 위치(home_depth.css).
 * - 화면에 들어온 구역 제목·3D 로고·위치 지도 카드에 .hd-in을 붙여 일어서기·홀로그램 훑기·한 바퀴를 켠다.
 * - 큰 로고는 그림을 여러 장 겹쳐 두께가 있는 3D 로고로 만들고(buildLogo3d), 로고와 위치 지도 카드는 마우스·손가락 쪽으로 기운다.
 * - 서비스 지도 3D 계산(projectPoint·swayAngles·nodeDepth)을 window.f2rDepth로 내놓는다(home_winter.js가 씀).
 * 움직임 줄이기 설정이거나 IntersectionObserver가 없으면 .hd-ready를 붙이지 않아 아무것도 숨기거나 눕히지 않는다. */
(function () {
  'use strict';
  var REVEAL = '.hl-head h2, .hd-logo3d, .hd-loc';
  var TILT = '.hd-logo3d, .hd-loc';
  var LOGO_LAYERS = 12;
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

  /* 3D 로고(GPU·3D 파일 없이): AI 코디 큰 로고 그림을 같은 자리에 여러 장 겹쳐 앞뒤(translateZ)로 벌려 두께를 만든다.
   * 맨 앞 장이 원래 로고(대체 글 유지), 뒤 장들은 장식이라 스크린리더에서 숨긴다. 로고가 없으면 아무것도 하지 않는다. */
  function buildLogo3d(doc, count) {
    var img = doc.querySelector ? doc.querySelector('.hw-coord-logo') : null;
    if (!img || !img.parentNode) return null;
    var n = typeof count === 'number' ? count : LOGO_LAYERS;
    var wrap = doc.createElement('span'); wrap.className = 'hd-logo3d';
    var spin = doc.createElement('span'); spin.className = 'hd-logo3d-spin';
    img.parentNode.insertBefore(wrap, img);
    wrap.appendChild(spin);
    for (var i = n - 1; i >= 1; i--) {
      var layer = img.cloneNode(false);
      layer.className = 'hw-coord-logo hd-logo-layer';
      layer.setAttribute('alt', ''); layer.setAttribute('aria-hidden', 'true');
      layer.style.setProperty('--hd-z', -i);
      spin.appendChild(layer);
    }
    img.style.setProperty('--hd-z', 0);
    spin.appendChild(img);
    return wrap;
  }

  // 위치 지도: 자리(.hd-loc-map[data-map-src])에 지도 iframe을 한 번만 넣는다. 그림으로만 쓰므로 초점·스크린리더에서 뺀다.
  function mountMap(doc, holder) {
    var src = holder && holder.getAttribute ? holder.getAttribute('data-map-src') : '';
    if (!src || holder.firstChild) return null;
    var frame = doc.createElement('iframe');
    frame.setAttribute('src', src); frame.setAttribute('loading', 'lazy'); frame.setAttribute('tabindex', '-1');
    frame.setAttribute('aria-hidden', 'true'); frame.setAttribute('title', 'F2R 위치 지도');
    holder.appendChild(frame);
    return frame;
  }
  function mountMaps(doc, scope) {
    var list = (scope || doc).querySelectorAll ? (scope || doc).querySelectorAll('.hd-loc-map[data-map-src]') : [];
    return Array.prototype.slice.call(list).map(function (h) { return mountMap(doc, h); });
  }

  function init(doc, win) {
    var root = doc.getElementById('homeTop');
    if (!root) return null;
    var reduced = win.matchMedia && win.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || typeof win.IntersectionObserver !== 'function') { mountMaps(doc); return { active: false }; }
    buildLogo3d(doc);
    var items = Array.prototype.slice.call(doc.querySelectorAll(REVEAL));
    root.classList.add('hd-ready');
    var observer = new win.IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('hd-in');
        mountMaps(doc, entry.target);
        observer.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.2 });
    items.forEach(function (el) { observer.observe(el); });
    var tilted = Array.prototype.slice.call(doc.querySelectorAll(TILT));
    tilted.forEach(bindTilt);
    return { active: true, items: items, tilted: tilted };
  }

  /* ── F2R 서비스 지도 3D(2026-11-22 감독 지시 "서비스 지도 부분 3d로") ──
   * home_winter.js가 점·선을 그릴 때 이 계산을 쓴다(window.f2rDepth). 지도 칸 안 위치 x·y(0~1)와 깊이 z(-0.5~0.5)를
   * 세로축(ay)·가로축(ax)으로 돌린 뒤 원근으로 줄여 화면 위치와 크기(s)를 돌려준다. fit은 돌린 점이 칸 밖으로 나가지 않게 줄이는 비율. */
  function projectPoint(x, y, z, ay, ax, dist, fit) {
    var d = typeof dist === 'number' ? dist : 2.4;
    var k = typeof fit === 'number' ? fit : 0.72;
    var cx = x - 0.5, cy = y - 0.5, cz = z || 0;
    var x1 = cx * Math.cos(ay) + cz * Math.sin(ay);
    var z1 = -cx * Math.sin(ay) + cz * Math.cos(ay);
    var y2 = cy * Math.cos(ax) - z1 * Math.sin(ax);
    var z2 = cy * Math.sin(ax) + z1 * Math.cos(ax);
    var s = d / (d + z2);
    return { x: 0.5 + x1 * s * k, y: 0.5 + y2 * s * k, s: s, depth: z2 };
  }

  // 가만히 있을 때 지도가 좌우로 천천히 흔들리는 각도(라디안). t는 초.
  function swayAngles(t) {
    return { ay: 0.42 * Math.sin(t * 0.25), ax: 0.2 + 0.06 * Math.sin(t * 0.17) };
  }

  // 점의 깊이: 묶음 중심(hub)은 가운데 가까이, 묶음마다 앞·뒤 층을 나누고, 같은 묶음 안에서도 조금씩 어긋나게 한다(매번 같은 값).
  var HUB_Z = [0, 0.12, -0.12, 0.18];
  var GROUP_Z = [-0.2, 0.22, -0.24, 0.24];
  function nodeDepth(i, node) {
    if (node && node.hub) return HUB_Z[i % HUB_Z.length];
    var base = node && typeof node.p === 'number' ? GROUP_Z[node.p % GROUP_Z.length] : 0;
    return Math.round((base + (((i * 37) % 7) / 7 - 0.5) * 0.14) * 1000) / 1000;
  }

  var api = { init: init, tiltFromPointer: tiltFromPointer, buildLogo3d: buildLogo3d, mountMap: mountMap, projectPoint: projectPoint, swayAngles: swayAngles, nodeDepth: nodeDepth };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.f2rDepth = api;
  if (typeof document !== 'undefined') init(document, window);
})();
