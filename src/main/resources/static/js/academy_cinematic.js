/**
 * Storefront Cinematic (Phase 5) — GSAP + ScrollTrigger 를 소유하는 *유일한* 모듈.
 *
 * 책임 범위는 정확히 두 섹션이다:
 *   #sec-featured     School Digital Twin Horizontal Showcase — 세로 스크롤 progress 로 가로 track 이동.
 *   #sec-digitaltwin  AI Native 5-Scene Panorama — 세로 스크롤 progress 로 scene timeline 구동.
 *
 * 원칙 (Phase 5 스펙)
 *   USER SCROLL = STORY TIMELINE.  scrub 이므로 스크롤을 멈추면 진행도 멈추고, 위로 스크롤하면
 *   그대로 역행한다.  pin 을 쓰지 않는다 — 레이아웃은 CSS position:sticky 가 담당하고(진입/탈출
 *   jump 없음), ScrollTrigger 는 progress→transform/opacity 매핑만 한다.
 *
 * 안전 / 폴백  (아래 중 하나라도면 전부 no-op — 두 섹션은 CSS 로 정적 판독 가능)
 *   - window.gsap / window.ScrollTrigger 미로드
 *   - prefers-reduced-motion: reduce
 *   - 뷰포트 폭 < 900 (모바일 — 네이티브 scroll-snap 스와이프가 담당)
 *
 * 생명주기
 *   wire(root)  : 이전 ScrollTrigger/timeline 를 전부 kill 후 재구성(CSR 재렌더/리사이즈 안전).
 *                 실제로 무언가 만들었을 때만 html.js-cinematic 를 붙인다(CSS 초기 상태 게이트).
 *   refresh()   : ScrollTrigger.refresh() 위임(이미지/폰트 로드 후 위치 재계산).
 *   리사이즈    : 단일 디바운스 리스너가 wire(lastRoot) 재호출 → 데스크톱↔모바일 경계도 재평가.
 *
 * 이벤트
 *   root 에 'f2r:cinematic-scene' CustomEvent { detail: { section, index, total } } 를 디스패치한다.
 *   academy_public.js 가 이걸 듣고 학교 칩 aria-pressed / CTA prefix 를 동기화한다(엔진은 timeline
 *   SSoT, chrome 동기화는 소비자 몫 — 한 요소를 두 엔진이 건드리지 않는다).
 */
(function (global) {
  'use strict';

  var doc = global.document;
  if (!doc || !doc.documentElement) return;

  function gsap() { return global.gsap; }
  function ST() { return global.ScrollTrigger; }

  function reduced() {
    return typeof global.matchMedia === 'function'
      && global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function viewportWidth() {
    return global.innerWidth || (doc.documentElement && doc.documentElement.clientWidth) || 0;
  }

  var registered = false;
  function usable() {
    if (!gsap() || !ST()) return false;
    if (reduced()) return false;
    if (viewportWidth() < 900) return false;
    if (!registered) {
      try { gsap().registerPlugin(ST()); } catch (e) { return false; }
      // 모바일 주소창 리사이즈로 refresh 가 계속 도는 것을 막는다(우리는 데스크톱 전용).
      try { ST().config({ ignoreMobileResize: true }); } catch (e2) { /* older ST */ }
      registered = true;
    }
    return true;
  }

  var built = [];        // ScrollTrigger 인스턴스
  var timelines = [];    // gsap.timeline 인스턴스
  var byId = {};         // id → ScrollTrigger (seek 용)
  var lastRoot = null;
  var resizeBound = false;
  var resizeTimer = null;

  function teardown() {
    for (var i = 0; i < built.length; i += 1) {
      try { built[i].kill(); } catch (e) { /* noop */ }
    }
    for (var j = 0; j < timelines.length; j += 1) {
      try { timelines[j].kill(); } catch (e2) { /* noop */ }
    }
    built = [];
    timelines = [];
    byId = {};
    if (doc.documentElement.classList.contains('js-cinematic')) {
      doc.documentElement.classList.remove('js-cinematic');
    }
  }

  function mark() { doc.documentElement.classList.add('js-cinematic'); }

  function emitScene(root, section, index, total) {
    try {
      root.dispatchEvent(new global.CustomEvent('f2r:cinematic-scene', {
        detail: { section: section, index: index, total: total }
      }));
    } catch (e) { /* CustomEvent 미지원 환경은 무시 */ }
  }

  // ── School Digital Twin — 가로 Showcase ─────────────────────────────────
  function wireFeatured(root) {
    var section = root.querySelector('#sec-featured');
    if (!section) return;
    var showcase = section.querySelector('[data-dt-showcase]');
    var track = showcase && showcase.querySelector('[data-dt-track]');
    var viewport = showcase && showcase.querySelector('.dt-viewport');
    if (!showcase || !track || !viewport) return;
    var scenes = track.querySelectorAll('.dt-scene');
    if (scenes.length < 2) return;

    var n = scenes.length;
    section.style.setProperty('--dt-count', String(n));

    var g = gsap();
    var current = showcase.querySelector('[data-dt-current]');
    var prevBtn = showcase.querySelector('[data-dt-nav-dir="prev"]');
    var nextBtn = showcase.querySelector('[data-dt-nav-dir="next"]');
    var lastIdx = -1;

    function place(p) {
      // p: 0..n-1 (연속). 중앙에 scene p 가 오도록 track 을 x 이동(transform 만).
      var stride = scenes[1].offsetLeft - scenes[0].offsetLeft;
      var pad = (viewport.clientWidth - scenes[0].offsetWidth) / 2;
      g.set(track, { x: Math.round(pad - p * stride) });
      var idx = Math.max(0, Math.min(n - 1, Math.round(p)));
      for (var i = 0; i < n; i += 1) {
        var d = Math.abs(i - p);
        // 중앙에서 멀수록 살짝 축소·감광(카메라 포커스). 섹션 전체를 흐리게 하는 게 아니다.
        g.set(scenes[i], { scale: d < 0.5 ? 1 : 0.9, opacity: d < 0.5 ? 1 : 0.4 });
        scenes[i].toggleAttribute('data-active', i === idx);
      }
      if (idx !== lastIdx) {
        lastIdx = idx;
        showcase.setAttribute('data-active', String(idx));
        if (current) current.textContent = idx + 1 < 10 ? '0' + (idx + 1) : String(idx + 1);
        if (prevBtn) prevBtn.disabled = idx === 0;
        if (nextBtn) nextBtn.disabled = idx === n - 1;
        emitScene(root, 'sec-featured', idx, n);
      }
    }

    var st = ST().create({
      id: 'cinematic-featured',
      trigger: section,
      start: 'top top',
      end: 'bottom bottom',
      scrub: 0.35,
      invalidateOnRefresh: true,
      onUpdate: function (self) { place(self.progress * (n - 1)); },
      onRefresh: function () { place(0); }
    });
    built.push(st);
    byId['cinematic-featured'] = st;
    place(0);
  }

  // ── AI Native — 5-Scene Panorama ───────────────────────────────────────
  // scene i 의 조립 애니메이션은 timeline 상 [at-0.45, at+0.15) 에 몰아넣는다(at=i, scene0 은 0.05~).
  // → scene 에 "진입하면서" 조립이 끝나고, 그 scene 의 활성 구간 [i/n,(i+1)/n) 내내 완성 상태 유지.
  // 뒤로 스크롤하면 이전 scene 구간에서 자연스럽게 분해된다(reverse).
  function buildSceneTweens(tl, scene, index) {
    var q = function (sel) { return scene.querySelectorAll(sel); };
    var one = function (sel) { return scene.querySelector(sel); };
    var at = Math.max(0.05, index - 0.45);
    if (index === 0) {
      tl.from(q('.ai-doc'), {
        x: function (i) { return [-150, 130, -110, 150][i] || 0; },
        y: function (i) { return [-90, -70, 90, 80][i] || 0; },
        rotate: function (i) { return [-12, 10, 8, -9][i] || 0; },
        opacity: 0, stagger: 0.06, duration: 0.42, ease: 'power2.out'
      }, at);
    } else if (index === 1) {
      tl.fromTo(one('.ai-scanline'), { yPercent: -110, opacity: 0.2 },
        { yPercent: 210, opacity: 0.9, duration: 0.4, ease: 'none' }, at);
      tl.from(q('.ai-bars i'), { scaleX: 0, transformOrigin: 'left center', stagger: 0.04, duration: 0.3, ease: 'power1.out' }, at + 0.12);
      tl.from(one('.ai-twin-card'), { scale: 0.55, opacity: 0, duration: 0.32, ease: 'back.out(1.6)' }, at + 0.3);
    } else if (index === 2) {
      tl.from(one('.ai-stream--a'), { xPercent: -160, opacity: 0, duration: 0.36, ease: 'power2.out' }, at);
      tl.from(one('.ai-stream--b'), { xPercent: 160, opacity: 0, duration: 0.36, ease: 'power2.out' }, at);
      tl.from(one('.ai-brain'), { scale: 0, opacity: 0, duration: 0.32, ease: 'back.out(1.5)' }, at + 0.22);
      tl.from(q('.ai-tokens li'), { y: 12, opacity: 0, stagger: 0.04, duration: 0.26, ease: 'power1.out' }, at + 0.36);
    } else if (index === 3) {
      tl.from(one('.ai-q--origin'), { scale: 0.6, opacity: 0, duration: 0.32, ease: 'back.out(1.4)' }, at);
      tl.from(q('.ai-q--var'), { y: -26, opacity: 0, scale: 0.7, stagger: 0.06, duration: 0.32, ease: 'power2.out' }, at + 0.22);
      tl.from(one('.ai-stamp'), { scale: 1.7, opacity: 0, duration: 0.28, ease: 'back.out(2)' }, at + 0.44);
    } else if (index === 4) {
      tl.from(q('.ai-qc'), {
        x: function (i) { return [-120, 120, -90, 100, 0][i] || 0; },
        y: function (i) { return [-70, -60, 70, 60, -100][i] || 0; },
        opacity: 0, scale: 0.6, stagger: 0.05, duration: 0.36, ease: 'power2.out'
      }, at);
      tl.from(one('.ai-final'), { scale: 0.9, opacity: 0, duration: 0.32, ease: 'power2.out' }, at + 0.3);
    }
  }

  function wireAiNative(root) {
    var section = root.querySelector('#sec-digitaltwin');
    if (!section) return;
    var visual = section.querySelector('[data-ai-visual]');
    var stage = section.querySelector('[data-ai-stage]');
    var driver = section.querySelector('.ai-native-steps');
    if (!visual || !stage || !driver) return;
    var scenes = stage.querySelectorAll('.ai-scene');
    if (scenes.length < 2) return;

    var g = gsap();
    var n = scenes.length;
    var tl = g.timeline({ paused: true });
    for (var i = 0; i < n; i += 1) buildSceneTweens(tl, scenes[i], i);
    // timeline 총 길이를 정확히 n 으로 고정한다(빈 tween 패딩). totalDuration(n) 은 기존 tween
    // 위치까지 스케일해 scene 정렬이 깨지므로 쓰지 않는다.
    tl.to({}, { duration: 0.001 }, n);
    timelines.push(tl);

    var lastIdx = -1;
    function paint(progress) {
      tl.progress(Math.max(0, Math.min(1, progress)));
      var idx = progress >= 1 ? n - 1 : Math.max(0, Math.min(n - 1, Math.floor(progress * n)));
      if (idx !== lastIdx) {
        lastIdx = idx;
        visual.setAttribute('data-active', String(idx));
        for (var i = 0; i < n; i += 1) scenes[i].classList.toggle('is-active', i === idx);
        emitScene(root, 'sec-digitaltwin', idx, n);
      }
    }

    var st = ST().create({
      id: 'cinematic-ai-native',
      trigger: driver,
      start: 'top 72%',
      end: 'bottom 34%',
      scrub: 0.4,
      invalidateOnRefresh: true,
      onUpdate: function (self) { paint(self.progress); },
      onRefresh: function () { paint(0); }
    });
    built.push(st);
    byId['cinematic-ai-native'] = st;
    paint(0);
  }

  /** 칩/네비 클릭 → 해당 scene 이 중앙에 오는 스크롤 위치로 부드럽게 이동(cinematic 활성 시). */
  function seek(id, index, count) {
    var st = byId[id];
    if (!st || count < 2) return false;
    var frac = Math.max(0, Math.min(1, index / (count - 1)));
    var target = st.start + (st.end - st.start) * frac;
    try {
      if (gsap().plugins && gsap().plugins.scrollTo) {
        gsap().to(global, { duration: 0.6, scrollTo: { y: target, autoKill: false }, ease: 'power2.inOut' });
      } else {
        global.scrollTo({ top: target, behavior: 'smooth' });
      }
    } catch (e) {
      global.scrollTo(0, target);
    }
    return true;
  }

  function bindResize() {
    if (resizeBound) return;
    resizeBound = true;
    global.addEventListener('resize', function () {
      if (resizeTimer) global.clearTimeout(resizeTimer);
      resizeTimer = global.setTimeout(function () {
        if (lastRoot) wire(lastRoot);
      }, 200);
    });
  }

  function wire(root) {
    if (!root || !root.querySelector) return;
    lastRoot = root;
    bindResize();
    teardown();
    if (!usable()) return;
    mark();
    try {
      wireFeatured(root);
      wireAiNative(root);
    } catch (e) {
      // 어떤 이유로든 실패하면 전부 되돌리고 정적 폴백에 맡긴다.
      teardown();
      return;
    }
    if (!built.length) {
      // 아무 trigger 도 안 만들어졌으면 게이트 클래스도 떼어 CSS 정적 경로를 탄다.
      if (doc.documentElement.classList.contains('js-cinematic')) {
        doc.documentElement.classList.remove('js-cinematic');
      }
      return;
    }
    // reveal 트랜지션·이미지·폰트가 늦게 정착하면 start/end 가 밀린다 — 몇 번 더 맞춘다
    // (자기 예약 루프 아님, 고정 횟수). GSAP 은 resize/load 에서 자동 refresh 하지만 이 페이지는
    // fetch 후 CSR 렌더라 load 는 이미 지난 뒤다.
    scheduleRefresh();
  }

  var refreshTimers = [];
  function scheduleRefresh() {
    for (var i = 0; i < refreshTimers.length; i += 1) global.clearTimeout(refreshTimers[i]);
    refreshTimers = [];
    if (typeof global.requestAnimationFrame === 'function') {
      global.requestAnimationFrame(function () { if (ST()) ST().refresh(); });
    }
    [250, 700, 1500].forEach(function (ms) {
      refreshTimers.push(global.setTimeout(function () { if (ST() && built.length) ST().refresh(); }, ms));
    });
  }

  function refresh() { if (ST()) ST().refresh(); }

  function active() { return doc.documentElement.classList.contains('js-cinematic') && built.length > 0; }
  function seekFeatured(index, count) { return seek('cinematic-featured', index, count); }
  function seekAiNative(index, count) { return seek('cinematic-ai-native', index, count); }

  global.F2RCinematic = Object.freeze({
    wire: wire,
    refresh: refresh,
    active: active,
    seekFeatured: seekFeatured,
    seekAiNative: seekAiNative
  });
})(typeof window !== 'undefined' ? window : globalThis);
