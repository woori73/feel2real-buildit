/**
 * Storefront Cinematic — GSAP + ScrollTrigger 를 소유하는 *유일한* 모듈.
 *
 * 책임 범위는 정확히 한 섹션이다:
 *   #sec-digitaltwin  AI Native 5-Scene Panorama — 세로 스크롤 progress 로 scene timeline 구동.
 *   (학교별 Digital Twin 은 별개 — 「2학기 영어 내신」 섹션(sec-featured)의 패널.
 *    academy_public.js wireFeaturedSchoolTwin. 이 모듈과 무관.)
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
  // 각 scene 창 = timeline [index, index+1). 오브젝트는 창 전체([index+0.05, index+0.9])에 걸쳐
  // *위치가 크게* 이동한다(±140~260px). opacity 페이드는 최소로만 쓴다 — 위치 이동이 주 신호.
  function buildSceneTweens(tl, scene, index) {
    var q = function (sel) { return scene.querySelectorAll(sel); };
    var one = function (sel) { return scene.querySelector(sel); };
    var at = index + 0.05;
    var span = 0.86;   // scene 창에서 애니메이션이 차지하는 길이

    if (index === 0) {
      // 자료 4개가 stage 네 귀퉁이 → 중앙 stack. 큰 대각선 이동.
      tl.from(q('.ai-doc'), {
        x: function (i) { return [-230, 235, -215, 225][i] || 0; },
        y: function (i) { return [-150, -140, 150, 145][i] || 0; },
        rotation: function (i) { return [-26, 22, 20, -24][i] || 0; },
        duration: span, ease: 'power2.inOut', stagger: 0.04
      }, at);

    } else if (index === 1) {
      // 모인 문항 위로 scan 이 위→아래로 지나가고(큰 세로 이동), 영역별 bar 가 자라며 TWIN 카드 상승.
      tl.fromTo(one('.ai-scanline'), { y: -130 }, { y: 170, duration: span * 0.7, ease: 'none' }, at);
      tl.from(q('.ai-bars i'), { scaleX: 0, stagger: 0.08, duration: span * 0.55, ease: 'power1.out' }, at + span * 0.22);
      tl.from(one('.ai-twin-card'), { y: 150, opacity: 0, duration: span * 0.4, ease: 'back.out(1.5)' }, at + span * 0.55);

    } else if (index === 2) {
      // 두 스트림이 stage 좌우 끝 → 중앙으로 크게 이동해 만나고, BRAIN 이 커지며 결합, 토큰 등장.
      tl.from(one('.ai-stream--a'), { x: -260, duration: span * 0.62, ease: 'power2.inOut' }, at);
      tl.from(one('.ai-stream--b'), { x: 260, duration: span * 0.62, ease: 'power2.inOut' }, at);
      tl.from(one('.ai-brain'), { scale: 0.15, duration: span * 0.4, ease: 'back.out(1.6)' }, at + span * 0.5);
      tl.from(q('.ai-tokens li'), { y: 46, opacity: 0, stagger: 0.06, duration: span * 0.3, ease: 'power1.out' }, at + span * 0.66);

    } else if (index === 3) {
      // BRAIN 에서 원본 문항이 아래로 내려오고(큰 세로 이동), 3개 변형이 중앙 → 좌/중/우로 갈라진다.
      tl.from(one('.ai-q--origin'), { y: -150, duration: span * 0.42, ease: 'power2.out' }, at);
      tl.from(q('.ai-q--var'), {
        x: function (i) { return [116, 0, -116][i] || 0; },   // v0 은 왼쪽으로, v2 는 오른쪽으로 벌어진다
        y: -100, opacity: 0, stagger: 0.1, duration: span * 0.5, ease: 'power2.out'
      }, at + span * 0.4);
      tl.from(one('.ai-stamp'), { y: -40, scale: 1.6, opacity: 0, duration: span * 0.3, ease: 'back.out(2)' }, at + span * 0.72);

    } else if (index === 4) {
      // Q01~Q05 가 사방에서 중앙 FINAL 시험지로 날아와 조립된다. 큰 이동 + 마지막 살짝 pulse.
      tl.from(q('.ai-qc'), {
        x: function (i) { return [-250, 250, -210, 220, -20][i] || 0; },
        y: function (i) { return [-150, -140, 150, 140, -210][i] || 0; },
        rotation: function (i) { return [-18, 16, 14, -16, 8][i] || 0; },
        stagger: 0.08, duration: span * 0.66, ease: 'power2.inOut'
      }, at);
      tl.from(one('.ai-final'), { scale: 0.55, duration: span * 0.4, ease: 'power2.out' }, at + span * 0.42);
      tl.to(one('.ai-final'), { scale: 1.05, duration: span * 0.18, yoyo: true, repeat: 1, ease: 'power1.inOut' }, at + span * 0.8);
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

    // start 를 늦게 잡는다 — progress 0 이 "visual 이 뷰포트 중앙에 sticky 로 자리잡은" 시점이 되어야
    // scene 0 의 흩어짐→조립이 사용자 눈에 보인다(섹션 진입 중에 끝나버리면 안 됨).
    var st = ST().create({
      id: 'cinematic-ai-native',
      trigger: driver,
      start: 'top 46%',
      end: 'bottom 54%',
      scrub: 0.6,
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
