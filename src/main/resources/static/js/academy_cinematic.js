/**
 * Storefront Cinematic — GSAP + ScrollTrigger 를 소유하는 *유일한* 모듈.
 *
 * 책임 범위는 정확히 한 섹션이다:
 *   #sec-digitaltwin  AI Native 5-Scene Panorama — 세로 스크롤 progress 로 scene timeline 구동.
 *   (Phase 5.1: sec-featured 의 가로 showcase 는 제거됨 — 학교별 Digital Twin 은 이제 sec-lessons
 *    「중학교 영어내신」 Program Card 안에서 보여준다. academy_public.js wireProgramCardTwin.)
 *
 * 원칙
 *   USER SCROLL = STORY TIMELINE.  scrub — 스크롤을 멈추면 진행도 멈추고, 위로 스크롤하면 그대로
 *   역행한다.  immediateRender 기본(true) 이므로 progress 0 에서 요소는 "흩어진 시작" 상태 →
 *   스크롤하면 실제로 이동/결합/분기/조립된다(slideshow 아님).  pin 을 쓰지 않는다 — 레이아웃은
 *   CSS position:sticky 가 담당(진입/탈출 jump 없음), ScrollTrigger 는 progress→transform/opacity 만.
 *
 * 안전 / 폴백  (아래 중 하나라도면 전부 no-op — 섹션은 CSS [data-active] 로 조립 완료 상태 정적 표시)
 *   - window.gsap / window.ScrollTrigger 미로드
 *   - prefers-reduced-motion: reduce
 *   - 뷰포트 폭 < 900 (모바일 — 5개 세로 블록 + inline glyph 가 담당)
 *
 * 생명주기
 *   wire(root)  : 이전 ScrollTrigger/timeline 를 전부 kill 후 재구성(CSR 재렌더/리사이즈 안전).
 *                 실제로 무언가 만들었을 때만 html.js-cinematic 를 붙인다(CSS 초기 상태 게이트).
 *   refresh()   : ScrollTrigger.refresh() 위임(이미지/폰트 로드 후 위치 재계산).
 *   리사이즈    : 단일 디바운스 리스너가 wire(lastRoot) 재호출 → 데스크톱↔모바일 경계도 재평가.
 *
 * 이벤트
 *   root 에 'f2r:cinematic-scene' CustomEvent { detail: { section, index, total } } 를 디스패치한다.
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

  // ── AI Native — 5-Scene Panorama ───────────────────────────────────────
  // 각 scene 의 조립 애니메이션은 timeline 창 [i+0.06, i+0.9] 전체에 펼친다(immediateRender 기본
  // = true 이므로 progress 0 에서 요소는 "흩어진 시작" 상태). scene 활성 구간 [i/n,(i+1)/n) 동안
  // 스크롤하는 내내 이동/결합/분기/조립이 눈에 보이고, 창 끝(i+0.9)에서 완성된다.
  // 스크롤을 멈추면(scrub) 멈추고, 위로 스크롤하면 그대로 역행(분해)한다.
  function buildSceneTweens(tl, scene, index) {
    var q = function (sel) { return scene.querySelectorAll(sel); };
    var one = function (sel) { return scene.querySelector(sel); };
    var at = index + 0.06;
    if (index === 0) {
      // 시험지 / 학교 프린트 / 교과서 / 수업 자료가 흩어진 상태에서 중앙으로 모여 stack.
      tl.from(q('.ai-doc'), {
        x: function (i) { return [-190, 170, -150, 185][i] || 0; },
        y: function (i) { return [-120, -95, 115, 105][i] || 0; },
        rotate: function (i) { return [-16, 13, 11, -12][i] || 0; },
        opacity: 0, stagger: 0.12, duration: 0.78, ease: 'power2.out'
      }, at);
    } else if (index === 1) {
      // 01 에서 모인 자료가 그대로 분석 대상 → scan → pattern bar → EXAMINER DIGITAL TWIN.
      tl.fromTo(one('.ai-scanline'), { yPercent: -120, opacity: 0.15 },
        { yPercent: 240, opacity: 0.9, duration: 0.5, ease: 'none' }, at);
      tl.from(q('.ai-bars i'), { scaleX: 0, transformOrigin: 'left center', stagger: 0.09, duration: 0.42, ease: 'power1.out' }, at + 0.34);
      tl.from(one('.ai-twin-card'), { scale: 0.5, opacity: 0, duration: 0.4, ease: 'back.out(1.6)' }, at + 0.66);
    } else if (index === 2) {
      // DIGITAL TWIN + TEACHER INSIGHT 두 스트림이 실제로 중앙으로 이동 → BUILD IT BRAIN 결합.
      tl.from(one('.ai-stream--a'), { xPercent: -200, opacity: 0, duration: 0.5, ease: 'power2.out' }, at);
      tl.from(one('.ai-stream--b'), { xPercent: 200, opacity: 0, duration: 0.5, ease: 'power2.out' }, at);
      tl.from(one('.ai-brain'), { scale: 0, opacity: 0, duration: 0.42, ease: 'back.out(1.5)' }, at + 0.4);
      tl.from(q('.ai-tokens li'), { y: 16, opacity: 0, stagger: 0.07, duration: 0.32, ease: 'power1.out' }, at + 0.62);
    } else if (index === 3) {
      // BUILD IT BRAIN 에서 원본 문항 → 변형 A / 변형 B / 서술형 으로 분기 생성.
      tl.from(one('.ai-q--origin'), { scale: 0.55, opacity: 0, y: -16, duration: 0.42, ease: 'back.out(1.4)' }, at);
      tl.from(q('.ai-q--var'), { y: -34, opacity: 0, scale: 0.6, stagger: 0.12, duration: 0.42, ease: 'power2.out' }, at + 0.34);
      tl.from(one('.ai-stamp'), { scale: 1.8, opacity: 0, duration: 0.34, ease: 'back.out(2)' }, at + 0.66);
    } else if (index === 4) {
      // 생성된 문항 Q01~Q05 가 모여 FINAL 실전 대비 시험지 하나로 조립.
      tl.from(q('.ai-qc'), {
        x: function (i) { return [-150, 150, -115, 130, 0][i] || 0; },
        y: function (i) { return [-95, -80, 95, 85, -130][i] || 0; },
        opacity: 0, scale: 0.5, stagger: 0.1, duration: 0.5, ease: 'power2.out'
      }, at);
      tl.from(one('.ai-final'), { scale: 0.86, opacity: 0, duration: 0.42, ease: 'power2.out' }, at + 0.44);
      tl.to(one('.ai-final'), { scale: 1.03, duration: 0.18, ease: 'power1.inOut' }, at + 0.82);
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
    // immediateRender 기본(true): progress 0 에서 모든 .from() 요소가 "흩어진 시작" 상태로
    // 렌더된다 → scene 0 은 처음부터 흩어져 있고 스크롤하면 중앙으로 모인다(정적 아님).
    // GSAP 없음 / reduced-motion / 모바일에서는 wire() 가 no-op → CSS [data-active] 로 조립 완료
    // 상태를 정적 표시(academy_public.css). 이 timeline 은 cinematic 활성일 때만 만들어진다.
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
      start: 'top 88%',
      end: 'bottom 22%',
      scrub: 0.5,
      invalidateOnRefresh: true,
      onUpdate: function (self) { paint(self.progress); },
      onRefresh: function (self) { paint(self.progress); }
    });
    built.push(st);
    byId['cinematic-ai-native'] = st;
    paint(0);
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

  global.F2RCinematic = Object.freeze({
    wire: wire,
    refresh: refresh,
    active: active
  });
})(typeof window !== 'undefined' ? window : globalThis);
