/**
 * Academy World Engine — BuildIt Academy 전용 캐릭터형 AI Sidekick "Bildi(빌디)"의 공용 엔진.
 *
 * 하나의 파일에 4개 엔진을 담는다(러닝게임의 "환경이 연속 전환되는 stage" 경험을 grammar로만
 * 차용 — 외부 게임 asset/이미지는 일절 쓰지 않는다. 캐릭터·배경은 BuildIt 고유 SVG다).
 *
 *   ├─ Environment Engine : createSky() — 스크롤 진행도(0..1)에 따라 하늘/날씨 scene 크로스페이드
 *   ├─ Bildi Character    : createBildi() — 인라인 SVG + 상태머신(IDLE/RUN/WINGS_OPEN/FLY/LAND/
 *   │                        POINT/TALK/THINK/HAPPY/WAIT), scene 반응 hook
 *   ├─ Scroll Story       : createScrollStory() — 스크롤 = 세계 진행. rAF-coalesced scroll +
 *   │                        IntersectionObserver, 방향/속도 감지, 매 프레임 루프 없음
 *   └─ Dialogue           : createDialogue() — Bildi에 tether된 말풍선(say + actions)
 *
 * 원칙(§11):
 *   - CSS transform/opacity 우선. rAF는 scroll 이벤트 coalescing에만.
 *   - prefers-reduced-motion / 저사양 → 이동/파티클 없이 즉시 최종 상태.
 *   - Bildi는 pointer-events:none(말풍선 버튼만 auto) — 실제 버튼 클릭을 막지 않는다.
 *   - 이 모듈이 로드 실패해도 홈페이지/빌더 콘텐츠는 그대로 읽힌다(소비자가 null 가드).
 *
 * Creator Mode(Builder) / Visitor Mode(Published) 둘 다 이 엔진을 쓴다. 이번 PR은 Creator Mode만
 * 실제 배선하고, Visitor Mode 연결 지점은 docs/academy/BILDI_WORLD_ENGINE.md 에 설계로 남긴다.
 */
(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.F2RAcademyWorld = api;
})(typeof window !== 'undefined' ? window : globalThis, function () {
  'use strict';

  // ── Environment Engine ────────────────────────────────────────────────────
  // 스크롤 progress 구간별 scene. 확장 가능한 registry — registerScene()으로 추가한다.
  // from/to는 0..1 스크롤 진행도. 정확한 비율은 소비자가 override 가능(createSky({scenes})).
  var DEFAULT_SCENES = [
    { id: 'morning', label: '맑은 아침', from: 0.00, to: 0.14 },
    { id: 'day', label: '화창한 낮', from: 0.14, to: 0.30 },
    { id: 'clouds', label: '구름', from: 0.30, to: 0.44 },
    { id: 'rain', label: '비', from: 0.44, to: 0.56 },
    { id: 'sunset', label: '비 갠 노을', from: 0.56, to: 0.70 },
    { id: 'night', label: '밤', from: 0.70, to: 0.86 },
    { id: 'stars', label: '별 / 우주', from: 0.86, to: 1.01 }
  ];

  function normalizeScenes(scenes) {
    var list = Array.isArray(scenes) && scenes.length ? scenes.slice() : DEFAULT_SCENES.slice();
    return list
      .filter(function (s) { return s && typeof s.id === 'string'; })
      .sort(function (a, b) { return (a.from || 0) - (b.from || 0); });
  }

  function sceneForProgress(progress, scenes) {
    var list = normalizeScenes(scenes);
    var p = clamp01(progress);
    for (var i = 0; i < list.length; i += 1) {
      if (p >= list[i].from && p < list[i].to) return list[i].id;
    }
    return list.length ? list[list.length - 1].id : 'day';
  }

  /**
   * scene별 Bildi 반응 hint(§7). 이번 PR은 shake/gaze/drift/dust/float만 정의하고
   * 실제 모션 강도는 CSS(.bildi[data-react=...])가 담당한다. 확장 여지를 남긴 map이다.
   */
  var SCENE_REACTIONS = {
    rain: 'shake',
    night: 'gaze',
    stars: 'gaze',
    clouds: 'drift',
    wind: 'drift',
    ash: 'dust',
    space: 'float'
  };
  function reactionForScene(sceneId) {
    return SCENE_REACTIONS[sceneId] || null;
  }

  function createSky(host, options) {
    var opts = options || {};
    var scenes = normalizeScenes(opts.scenes);
    var doc = host && host.ownerDocument ? host.ownerDocument : (typeof document !== 'undefined' ? document : null);
    var el = null;
    var current = null;
    if (host && doc && typeof doc.createElement === 'function') {
      el = doc.createElement('div');
      el.className = 'world-sky' + (opts.className ? ' ' + opts.className : '');
      el.setAttribute('aria-hidden', 'true');
      if (reducedOrLowPower()) el.classList.add('world-sky-reduced');
      host.insertBefore(el, host.firstChild || null);
    }
    function setScene(id) {
      if (!id || id === current) return;
      current = id;
      if (el) el.setAttribute('data-scene', id);
    }
    function setProgress(progress) {
      setScene(sceneForProgress(progress, scenes));
      if (el) el.style.setProperty('--world-progress', String(clamp01(progress)));
    }
    return {
      el: el,
      scenes: scenes,
      getScene: function () { return current; },
      setScene: setScene,
      setProgress: setProgress,
      destroy: function () { if (el && el.parentNode) el.parentNode.removeChild(el); el = null; }
    };
  }

  // ── Bildi Character Engine ────────────────────────────────────────────────
  var BILDI_STATES = Object.freeze({
    IDLE: 'idle', RUN: 'run', WINGS_OPEN: 'wings_open', FLY: 'fly', LAND: 'land',
    POINT: 'point', TALK: 'talk', THINK: 'think', HAPPY: 'happy', WAIT: 'wait'
  });

  // 상태 전이표(§4). 정의되지 않은 event는 현재 상태 유지.
  var BILDI_TRANSITIONS = Object.freeze({
    idle: { scrollStart: 'wings_open', point: 'point', talk: 'talk', think: 'think', happy: 'happy', wait: 'wait' },
    wait: { scrollStart: 'wings_open', talk: 'talk', settle: 'idle' },
    wings_open: { move: 'fly', scrollStop: 'idle', settle: 'idle' },
    fly: { arrive: 'land', scrollStop: 'land', faster: 'fly', slower: 'fly' },
    land: { settle: 'idle', point: 'point', talk: 'talk', scrollStart: 'wings_open' },
    run: { move: 'fly', scrollStop: 'idle', settle: 'idle' },
    point: { talk: 'talk', settle: 'idle', scrollStart: 'wings_open' },
    talk: { point: 'point', settle: 'idle', happy: 'happy', scrollStart: 'wings_open' },
    think: { talk: 'talk', settle: 'idle' },
    happy: { settle: 'idle', talk: 'talk' }
  });

  function nextBildiState(state, event) {
    var from = BILDI_TRANSITIONS[state] || BILDI_TRANSITIONS.idle;
    return (from && from[event]) || state;
  }

  // ── Bildi visual asset 구조 (Phase 5B.1a) ────────────────────────────────
  // Production visual = state별 transparent 이미지(<img>). academy_world.js는 이 asset을
  // "연결"만 하고, 파일이 없거나 로드 실패하면 아래 인라인 fallback SVG(BILDI_SVG)로 떨어진다.
  //   1차: src/main/resources/static/img/bildi/bildi-<state>.svg  (dev-assets/bildi/generate.mjs 생성)
  //   교체: 같은 파일명의 WebP/PNG(@2x, 투명)로 덮으면 코드 변경 없이 반영 (BILDI_WORLD_ENGINE.md §7)
  //   fallback: 이미지 미로드/비활성 → BILDI_SVG (data-state로 CSS 포즈, 상태머신·이동 로직 동일)
  var BILDI_ASSET_BASE = '/img/bildi/';
  var BILDI_STATE_ASSETS = Object.freeze({
    idle: 'bildi-idle.svg',
    wings_open: 'bildi-wings-open.svg',
    fly: 'bildi-fly.svg',
    land: 'bildi-land.svg',
    point: 'bildi-point.svg',
    talk: 'bildi-talk.svg',
    think: 'bildi-think.svg',
    happy: 'bildi-happy.svg'
  });
  // 전용 asset이 없는 상태는 근사 asset으로 매핑(상태머신은 그대로 — visual만 근사).
  var BILDI_ASSET_ALIASES = Object.freeze({ run: 'fly', wait: 'idle' });

  /** state → asset URL(없으면 null). opts.assets(state→url) / opts.assetBase 로 override 가능. */
  function bildiAssetUrl(state, opts) {
    var o = opts || {};
    if (o.assets && typeof o.assets[state] === 'string') return o.assets[state];
    var key = BILDI_STATE_ASSETS[state] ? state : (BILDI_ASSET_ALIASES[state] || state);
    var file = BILDI_STATE_ASSETS[key];
    if (!file) return null;
    var base = typeof o.assetBase === 'string' ? o.assetBase : BILDI_ASSET_BASE;
    return base + file;
  }

  // 인라인 fallback 캐릭터(reference 시안 기준: 큰 머리·큰 눈·이마 고글·BuildIt-blue 스카프+배지·
  // 비행 시 크게 펴지는 날개). 파트가 <g>로 분리돼 CSS가 날갯짓/눈깜빡을 준다. emoji/원형 아님.
  // fallback 캐릭터(dev-assets/bildi/generate.mjs 의 idle 과 같은 비율): 큰 둥근 머리 · 작은 통통한 몸 ·
  // 3갈래 정수리 털 · 크고 부드러운 눈 · BuildIt-blue 스카프+배지 · 평소 작은 날개(비행 시 CSS 로 펼침).
  // 고글은 fallback 에서는 이마에 얹되 옅게 — asset 이 로드되면 state 별로 교체된다.
  var BILDI_SVG = [
    '<svg class="bildi-svg" viewBox="0 0 128 128" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="빌디">',
    '<defs>',
    '<radialGradient id="bildiBody" cx="40%" cy="32%" r="74%">',
    '<stop offset="0%" stop-color="#ffe487"/><stop offset="55%" stop-color="#ffd23f"/><stop offset="100%" stop-color="#f4b813"/>',
    '</radialGradient>',
    '<linearGradient id="bildiScarf" x1="0" y1="0" x2="1" y2="1">',
    '<stop offset="0%" stop-color="#5A8FE0"/><stop offset="100%" stop-color="#0C238A"/>',
    '</linearGradient>',
    '</defs>',
    '<g class="bildi-wing bildi-wing-back" transform="translate(20 72)">',
    '<path d="M4 -2 C-9 -4 -16 4 -15 13 C-14 19 -4 19 3 14 C6 10 8 2 4 -2 Z" fill="#f4b813"/></g>',
    '<ellipse class="bildi-shadow" cx="64" cy="120" rx="24" ry="5.5" fill="rgba(12,35,138,.18)"/>',
    '<path class="bildi-tuft" d="M54 24 Q56 9 63 20 Q65 5 70 20 Q77 9 78 24 Q66 17 54 24 Z" fill="#f4b813"/>',
    '<path class="bildi-body" d="M64 22 C90 22 108 40 108 66 C108 90 91 106 74 108 Q64 109 54 108 C37 106 20 90 20 66 C20 40 38 22 64 22 Z" fill="url(#bildiBody)"/>',
    '<path class="bildi-belly" d="M64 62 C79 62 88 73 88 87 C88 101 77 110 64 110 C51 110 40 101 40 87 C40 73 49 62 64 62 Z" fill="#ffe08a" opacity="0.5"/>',
    '<ellipse class="bildi-cheek" cx="40" cy="78" rx="7" ry="4.6" fill="#ffa7bd" opacity="0.8"/>',
    '<ellipse class="bildi-cheek" cx="88" cy="78" rx="7" ry="4.6" fill="#ffa7bd" opacity="0.8"/>',
    '<g class="bildi-face">',
    '<ellipse class="bildi-eye" cx="52" cy="64" rx="8.4" ry="10" fill="#2a2b33"/>',
    '<ellipse class="bildi-eye" cx="76" cy="64" rx="8.4" ry="10" fill="#2a2b33"/>',
    '<circle class="bildi-eye-glint" cx="49" cy="59" r="3" fill="#fff"/>',
    '<circle class="bildi-eye-glint" cx="73" cy="59" r="3" fill="#fff"/>',
    '<path class="bildi-beak" d="M58 73 Q64 70 70 73 L64 82 Z" fill="#ff9f1c"/>',
    '<path class="bildi-mouth" d="M58 82 Q64 88 70 82" stroke="#c2790f" stroke-width="3" fill="none" stroke-linecap="round"/>',
    '</g>',
    '<g class="bildi-goggles" transform="translate(64 44)" opacity="0.9">',
    '<path d="M-22 3 Q0 -5 22 3 L21 7 Q0 0 -21 7 Z" fill="#5b4f3a"/>',
    '<ellipse cx="-9" cy="2" rx="6.6" ry="6" fill="#2c2e38" stroke="#e6c877" stroke-width="2.4"/>',
    '<ellipse cx="9" cy="2" rx="6.6" ry="6" fill="#2c2e38" stroke="#e6c877" stroke-width="2.4"/>',
    '<circle cx="-11" cy="-1" r="1.8" fill="#9ec5ff"/><circle cx="7" cy="-1" r="1.8" fill="#9ec5ff"/>',
    '</g>',
    '<g class="bildi-scarf" transform="translate(64 90)">',
    '<path d="M-20 -6 Q0 5 20 -6 L17 5 Q0 13 -17 5 Z" fill="url(#bildiScarf)"/>',
    '<path class="bildi-scarf-tail" d="M12 0 Q26 6 22 22 L13 15 Q9 7 8 3 Z" fill="url(#bildiScarf)"/>',
    '<circle cx="0" cy="0" r="7" fill="#eef3ff"/>',
    '<path d="M0 -4.6 L5 0 L0 4.6 L-5 0 Z" fill="#0C238A"/>',
    '</g>',
    '<g class="bildi-wing bildi-wing-front" transform="translate(108 72)">',
    '<path d="M-4 -2 C9 -4 16 4 15 13 C14 19 4 19 -3 14 C-6 10 -8 2 -4 -2 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="1.5"/></g>',
    '</svg>'
  ].join('');

  function createBildi(host, options) {
    var opts = options || {};
    var doc = host && host.ownerDocument ? host.ownerDocument : (typeof document !== 'undefined' ? document : null);
    var el = null;
    var sprite = null;      // <img> — state별 production asset
    var fallback = null;    // 인라인 SVG — 이미지 미로드 시
    var pendingSrc = '';
    var state = BILDI_STATES.IDLE;
    var reduced = reducedOrLowPower();
    if (host && doc && typeof doc.createElement === 'function') {
      el = doc.createElement('div');
      el.className = 'bildi' + (opts.className ? ' ' + opts.className : '');
      el.setAttribute('aria-hidden', 'true');
      if (reduced) el.classList.add('bildi-reduced');

      // .bildi        = 위치(이동) 전용 transform. 절대 CSS 애니메이션으로 덮이지 않게 한다.
      // .bildi-inner  = 캐릭터 애니메이션(호흡/비행 bob/착지/반응) 전용. 위치 transform과 분리해
      //                 CSS animation(transform: scale/rotate)이 이동을 삼키지 않도록 한다.
      var inner = doc.createElement('div');
      inner.className = 'bildi-inner';

      var art = doc.createElement('div');
      art.className = 'bildi-art';

      sprite = doc.createElement('img');
      sprite.className = 'bildi-sprite';
      sprite.setAttribute('alt', '');
      sprite.setAttribute('decoding', 'async');
      sprite.setAttribute('draggable', 'false');
      if (typeof sprite.addEventListener === 'function') {
        sprite.addEventListener('load', function () {
          if (sprite.getAttribute('src') === pendingSrc && el) el.classList.add('bildi-has-sprite');
        });
        sprite.addEventListener('error', function () {
          if (el) el.classList.remove('bildi-has-sprite'); // → CSS가 fallback SVG를 보여준다
        });
      }

      fallback = doc.createElement('div');
      fallback.className = 'bildi-fallback';
      fallback.setAttribute('aria-hidden', 'true');
      fallback.innerHTML = BILDI_SVG;

      art.appendChild(sprite);
      art.appendChild(fallback);
      inner.appendChild(art);
      el.appendChild(inner);
      el.setAttribute('data-state', state);
      host.appendChild(el);
      applySpriteFor(state);
    }

    /** 현재 state에 맞는 production asset을 <img>에 건다. 없으면 fallback SVG를 쓴다. */
    function applySpriteFor(next) {
      if (!sprite) return;
      var url = bildiAssetUrl(next, opts);
      if (!url) { if (el) el.classList.remove('bildi-has-sprite'); return; }
      pendingSrc = url;
      sprite.setAttribute('src', url);
    }

    function setState(next) {
      if (!next || next === state) return state;
      state = next;
      if (el) el.setAttribute('data-state', next);
      applySpriteFor(next);
      return state;
    }
    function dispatch(event) { return setState(nextBildiState(state, event)); }
    function face(dir) { if (el) el.setAttribute('data-facing', dir === 'up' ? 'up' : 'down'); }
    function setExpression(expr) { if (el) el.setAttribute('data-expr', expr || 'default'); }
    function react(name) {
      if (!el) return;
      if (reduced || !name) { el.removeAttribute('data-react'); return; }
      el.setAttribute('data-react', name);
    }
    /**
     * host 좌표계 안에서 targetRect(뷰포트 기준) 위치로 Bildi를 이동한다. reduced-motion이면
     * transition 없이 즉시(‥CSS가 .bildi-reduced에서 transition:none). offset으로 캐릭터를
     * 섹션 모서리에 살짝 얹는다.
     */
    function moveToRect(targetRect, hostRect, offset) {
      if (!el || !targetRect || !hostRect) return;
      var off = offset || {};
      var x = (targetRect.left - hostRect.left) + (typeof off.x === 'number' ? off.x : targetRect.width * 0.5);
      var y = (targetRect.top - hostRect.top) + (typeof off.y === 'number' ? off.y : 24);
      x = Math.max(6, Math.min(x, hostRect.width - 6));
      y = Math.max(6, Math.min(y, hostRect.height - 6));
      el.style.transform = 'translate3d(' + Math.round(x) + 'px,' + Math.round(y) + 'px,0)';
    }
    function moveToXY(x, y) {
      if (!el) return;
      el.style.transform = 'translate3d(' + Math.round(x) + 'px,' + Math.round(y) + 'px,0)';
    }

    return {
      el: el,
      sprite: sprite,
      fallback: fallback,
      STATES: BILDI_STATES,
      getState: function () { return state; },
      setState: setState,
      dispatch: dispatch,
      face: face,
      setExpression: setExpression,
      react: react,
      moveToRect: moveToRect,
      moveToXY: moveToXY,
      assetUrlFor: function (s) { return bildiAssetUrl(s, opts); },
      hasSprite: function () { return !!(el && el.classList && el.classList.contains('bildi-has-sprite')); },
      isReduced: function () { return reduced; },
      destroy: function () { if (el && el.parentNode) el.parentNode.removeChild(el); el = null; }
    };
  }

  // ── Dialogue Engine ──────────────────────────────────────────────────────
  function createDialogue(host, options) {
    var opts = options || {};
    var doc = host && host.ownerDocument ? host.ownerDocument : (typeof document !== 'undefined' ? document : null);
    var el = null;
    var bodyEl = null;
    if (host && doc && typeof doc.createElement === 'function') {
      el = doc.createElement('div');
      el.className = 'bildi-bubble' + (opts.className ? ' ' + opts.className : '');
      el.setAttribute('role', 'status');
      el.setAttribute('aria-live', 'polite');
      el.hidden = true;
      bodyEl = doc.createElement('div');
      bodyEl.className = 'bildi-bubble-body';
      el.appendChild(bodyEl);
      host.appendChild(el);
    }
    function say(html) {
      if (!el || !bodyEl) return;
      bodyEl.innerHTML = html == null ? '' : String(html);
      el.hidden = false;
    }
    function hide() { if (el) el.hidden = true; }
    function moveToXY(x, y) {
      if (!el) return;
      el.style.transform = 'translate3d(' + Math.round(x) + 'px,' + Math.round(y) + 'px,0)';
    }
    return {
      el: el,
      body: bodyEl,
      say: say,
      hide: hide,
      moveToXY: moveToXY,
      isVisible: function () { return !!el && !el.hidden; },
      destroy: function () { if (el && el.parentNode) el.parentNode.removeChild(el); el = null; bodyEl = null; }
    };
  }

  // ── Scroll Story Engine ──────────────────────────────────────────────────
  // Scroll = 세계 진행. Time = 세계가 살아 움직이는 CSS 애니메이션(scene 내부). 여기선 진행도만.
  function createScrollStory(config) {
    var cfg = config || {};
    var scrollEl = cfg.scrollEl || null;
    var win = cfg.window || (typeof window !== 'undefined' ? window : null);
    var getSections = typeof cfg.sections === 'function' ? cfg.sections : function () { return []; };
    var onUpdate = typeof cfg.onUpdate === 'function' ? cfg.onUpdate : function () {};
    var reduced = reducedOrLowPower();
    var frame = 0;
    var lastTop = readScrollTop();
    var lastTs = now();
    var activeIo = null;
    var activeSection = null;
    var detached = false;

    function now() { return (win && win.performance && win.performance.now) ? win.performance.now() : Date.now(); }
    function readScrollTop() {
      if (scrollEl && typeof scrollEl.scrollTop === 'number') return scrollEl.scrollTop;
      if (win) return win.pageYOffset || (win.document && win.document.documentElement && win.document.documentElement.scrollTop) || 0;
      return 0;
    }
    function scrollExtent() {
      if (scrollEl) return Math.max(1, scrollEl.scrollHeight - scrollEl.clientHeight);
      if (win && win.document) {
        var d = win.document.documentElement;
        return Math.max(1, (d.scrollHeight || 0) - (win.innerHeight || 0));
      }
      return 1;
    }

    function compute() {
      frame = 0;
      if (detached) return;
      var top = readScrollTop();
      var progress = clamp01(top / scrollExtent());
      var ts = now();
      var dt = Math.max(1, ts - lastTs);
      var velocity = Math.abs(top - lastTop) / dt; // px/ms
      var direction = top === lastTop ? 'none' : (top > lastTop ? 'down' : 'up');
      var speed = velocity > 1.6 ? 'fast' : (velocity > 0.15 ? 'normal' : 'slow');
      lastTop = top; lastTs = ts;
      onUpdate({
        progress: progress,
        direction: direction,
        speed: reduced ? 'slow' : speed,
        activeSection: activeSection,
        reduced: reduced
      });
    }

    function onScroll() {
      if (frame || detached) return;
      var raf = win && win.requestAnimationFrame ? win.requestAnimationFrame : function (fn) { return setTimeout(fn, 16); };
      frame = raf(compute);
    }

    function observeSections() {
      var HAS_IO = win && typeof win.IntersectionObserver === 'function';
      var sections = (getSections() || []).filter(Boolean);
      if (activeIo && activeIo.disconnect) { activeIo.disconnect(); activeIo = null; }
      if (!HAS_IO || !sections.length) {
        activeSection = sections[0] || null;
        return;
      }
      activeIo = new win.IntersectionObserver(function (entries) {
        var best = null;
        for (var i = 0; i < entries.length; i += 1) {
          if (!entries[i].isIntersecting) continue;
          if (!best || entries[i].intersectionRatio > best.intersectionRatio) best = entries[i];
        }
        if (best && best.target !== activeSection) {
          activeSection = best.target;
          compute();
        }
      }, { threshold: [0.25, 0.5, 0.75], root: scrollEl || null });
      sections.forEach(function (s) { activeIo.observe(s); });
    }

    var scrollTarget = scrollEl || win;
    if (scrollTarget && scrollTarget.addEventListener) {
      scrollTarget.addEventListener('scroll', onScroll, { passive: true });
    }
    observeSections();
    compute();

    return {
      refresh: function () { lastTop = readScrollTop(); observeSections(); compute(); },
      getActiveSection: function () { return activeSection; },
      detach: function () {
        detached = true;
        if (scrollTarget && scrollTarget.removeEventListener) scrollTarget.removeEventListener('scroll', onScroll);
        if (activeIo && activeIo.disconnect) activeIo.disconnect();
        activeIo = null;
      }
    };
  }

  /**
   * 스크롤 progress(0..1) → Bildi 화면 위치(px). 순수 함수 — 소비자가 waypoint 목록(섹션 프랙션)과
   * safe area(px), Bildi 크기(px)를 넘기면 그 사이를 보간해 돌려준다. "다음 섹션 waypoint 로
   * 날아가 착지" 궤적: segment 안에서 easeInOut + 위로 솟는 arc.
   *
   * @param opts.progress  전체 스크롤 진행도 0..1
   * @param opts.waypoints [{x,y}, …] safe area 기준 프랙션(0..1). 길이 = 섹션 수(count 로 override 가능)
   * @param opts.safe      { x0,y0,x1,y1 } stage 좌표계 px (Bildi 이동 허용 영역)
   * @param opts.size      { w,h } Bildi px. safe 안에 완전히 들어오도록 range 를 줄인다
   * @param opts.arc       비행 arc 세기(safe.h 대비 비율, 기본 0.10). 0 이면 직선 이동
   * @returns { x, y, frac, segIndex, moving }
   */
  function flightPosition(opts) {
    var o = opts || {};
    var wps = Array.isArray(o.waypoints) ? o.waypoints : [];
    var n = Math.max(1, o.count || wps.length || 1);
    var p = clamp01(o.progress);
    var seg = p * (n - 1);
    var i = Math.min(Math.max(0, n - 2), Math.max(0, Math.floor(seg)));
    var frac = n > 1 ? clamp01(seg - i) : 0;
    var a = wps[i] || { x: 0.2, y: 0.3 };
    var b = wps[Math.min(i + 1, n - 1)] || a;
    // easeInOutQuad
    var e = frac < 0.5 ? 2 * frac * frac : 1 - Math.pow(-2 * frac + 2, 2) / 2;
    var arc = o.arc == null ? 0.10 : Number(o.arc) || 0;
    var fx = a.x + (b.x - a.x) * e;
    var fy = a.y + (b.y - a.y) * e - Math.sin(clamp01(frac) * Math.PI) * arc;
    var safe = o.safe || { x0: 0, y0: 0, x1: 100, y1: 100 };
    var size = o.size || { w: 0, h: 0 };
    var rangeW = Math.max(0, (safe.x1 - safe.x0) - (size.w || 0));
    var rangeH = Math.max(0, (safe.y1 - safe.y0) - (size.h || 0));
    var x = safe.x0 + clamp01(fx) * rangeW;
    var y = safe.y0 + clamp01(fy) * rangeH;
    return {
      x: Math.round(Math.max(safe.x0, Math.min(x, safe.x1 - (size.w || 0)))),
      y: Math.round(Math.max(safe.y0, Math.min(y, safe.y1 - (size.h || 0)))),
      frac: frac,
      segIndex: i,
      moving: frac > 0.06 && frac < 0.94
    };
  }

  // ── shared helpers ───────────────────────────────────────────────────────
  function clamp01(n) {
    n = Number(n);
    if (isNaN(n)) return 0;
    return n < 0 ? 0 : (n > 1 ? 1 : n);
  }
  function prefersReducedMotion() {
    try {
      return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    } catch (_) { return false; }
  }
  /** 저사양 힌트: 하드웨어 동시성 낮거나 deviceMemory 작으면 파티클/이동 강도를 낮춘다. */
  function isLowPowerHint() {
    try {
      var nav = typeof navigator !== 'undefined' ? navigator : null;
      if (!nav) return false;
      if (typeof nav.deviceMemory === 'number' && nav.deviceMemory > 0 && nav.deviceMemory <= 2) return true;
      if (typeof nav.hardwareConcurrency === 'number' && nav.hardwareConcurrency > 0 && nav.hardwareConcurrency <= 2) return true;
      return false;
    } catch (_) { return false; }
  }
  function reducedOrLowPower() { return prefersReducedMotion() || isLowPowerHint(); }

  return Object.freeze({
    // Environment
    DEFAULT_SCENES: DEFAULT_SCENES,
    sceneForProgress: function (p, scenes) { return sceneForProgress(p, scenes); },
    reactionForScene: reactionForScene,
    createSky: createSky,
    // Bildi
    BILDI_STATES: BILDI_STATES,
    BILDI_TRANSITIONS: BILDI_TRANSITIONS,
    nextBildiState: nextBildiState,
    BILDI_SVG: BILDI_SVG,
    BILDI_ASSET_BASE: BILDI_ASSET_BASE,
    BILDI_STATE_ASSETS: BILDI_STATE_ASSETS,
    bildiAssetUrl: bildiAssetUrl,
    createBildi: createBildi,
    // Dialogue
    createDialogue: createDialogue,
    // Scroll Story
    createScrollStory: createScrollStory,
    flightPosition: flightPosition,
    // env
    prefersReducedMotion: prefersReducedMotion,
    isLowPowerHint: isLowPowerHint,
    reducedOrLowPower: reducedOrLowPower
  });
});
