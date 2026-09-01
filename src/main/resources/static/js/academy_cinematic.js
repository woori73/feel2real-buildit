/**
 * Storefront Cinematic — GSAP + ScrollTrigger 를 소유하는 *유일한* 모듈.
 *
 * 책임 범위는 정확히 한 섹션이다:
 *   #sec-digitaltwin  AI Native Pipeline — 하나의 넓은 canvas 안에서 동일한 오브젝트들이 세로
 *   스크롤 progress 0→1 에 직접 묶여 계속 이동한다: 왼쪽 학교 자료 4개가 화면을 가로질러
 *   BUILD IT BRAIN 으로 들어가고 → Twin + Insight 가 좌우에서 결합 → Brain 오른쪽에서 문제들이
 *   나오고 → 문제들이 아래로 모여 한 장의 FINAL EXAM 으로 조립된다. 정적 scene 교체가 아니다 —
 *   하나의 timeline, 오브젝트의 실제 x/y/scale 이동과 connector 선(.ai-flow-path stroke)이 신호다.
 *   (학교별 Digital Twin 은 별개 — 「2학기 영어 내신」 섹션(sec-featured)의 패널.
 *    academy_public.js wireFeaturedSchoolTwin. 이 모듈과 무관.)
 *
 * 원칙
 *   USER SCROLL = STORY TIMELINE.  scrub — 스크롤을 멈추면 진행도 멈추고, 위로 스크롤하면 그대로
 *   역행한다.  immediateRender 기본(true) 이므로 progress 0 에서 요소는 "흩어진 시작" 상태 →
 *   스크롤하면 실제로 이동/결합/분기/조립된다(slideshow 아님).  pin 을 쓰지 않는다 — 레이아웃은
 *   CSS position:sticky 가 담당(진입/탈출 jump 없음), ScrollTrigger 는 progress→transform/opacity 만.
 *
 * 안전 / 폴백  (아래 중 하나라도면 전부 no-op — canvas 는 CSS 기본 배치대로 파이프라인 다이어그램
 *              으로 정적 표시, 모바일은 canvas 숨김 + 5개 세로 블록)
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

  // ── AI Native — 하나의 파이프라인 canvas ─────────────────────────────────
  // canvas 좌표계는 660×460 고정(academy_cinematic.css 절대배치). fitCanvas 가 stage 폭에 맞춰
  // transform:scale 한다 → px 이동량도 함께 스케일된다. 하나의 timeline(progress 0→1)에
  // 네 단계가 이어진다. immediateRender 기본(true) → progress 0 에서 자료는 화면 왼쪽 밖, Brain·
  // 문제·시험지는 아직 없음(흩어진 시작). 스크롤하면 실제로 이동/결합/분기/조립되고, 멈추면
  // 멈추고, 위로 스크롤하면 역행(분해)한다. opacity 는 보조일 뿐 — x/y/scale 이동이 주 신호.
  var CANVAS_W = 660;
  var CANVAS_H = 460;

  function fitCanvas(stage, canvas) {
    var w = stage.clientWidth || CANVAS_W;
    if (!w) return;
    var s = w / CANVAS_W;
    canvas.style.transform = 'scale(' + s + ')';
    stage.style.height = (CANVAS_H * s) + 'px';
  }

  function wireAiNative(root) {
    var section = root.querySelector('#sec-digitaltwin');
    if (!section) return;
    var visual = section.querySelector('[data-ai-visual]');
    var stage = section.querySelector('[data-ai-stage]');
    var canvas = section.querySelector('[data-ai-canvas]');
    var driver = section.querySelector('.ai-native-steps');
    if (!visual || !stage || !canvas || !driver) return;
    var srcs = canvas.querySelectorAll('.ai-src');
    var qouts = canvas.querySelectorAll('.ai-qout');
    var brain = canvas.querySelector('.ai-brain');
    var twin = canvas.querySelector('[data-ai-node="twin"]');
    var insight = canvas.querySelector('[data-ai-node="insight"]');
    var finalSheet = canvas.querySelector('[data-ai-final]');
    var flowPath = canvas.querySelector('[data-ai-flow-path]');
    if (!srcs.length || !qouts.length || !brain || !finalSheet) return;

    var g = gsap();
    fitCanvas(stage, canvas);

    var tl = g.timeline({ paused: true });

    // 흐름 척추(connector) — 스크롤 내내 왼쪽 자료 → Brain → 문제 → 시험지 경로가 그려진다.
    if (flowPath && typeof flowPath.getTotalLength === 'function') {
      var len = 900;
      try { len = flowPath.getTotalLength() || 900; } catch (e) { len = 900; }
      g.set(flowPath, { strokeDasharray: len, strokeDashoffset: len });
      tl.to(flowPath, { strokeDashoffset: 0, duration: 1, ease: 'none' }, 0);
    }

    // scrub timeline 에서는 .from() 의 end 값이 잘못 캡처될 수 있어 전부 .fromTo()(양끝 명시)로 쓴다.
    // .fromTo() 는 immediateRender 기본(true) → progress 0 에서 모든 요소가 "흩어진 시작" 상태로 렌더.

    // A (0.02–0.50) 학교 자료 4개가 화면 왼쪽 밖에서 들어와 자리잡고(→ x0) → Brain 으로 빨려 들어가며 축소.
    tl.fromTo(srcs,
      { x: -360, y: function (i) { return [72, 26, -26, -72][i] || 0; }, opacity: 0, scale: 0.84 },
      { x: 0, y: 0, opacity: 1, scale: 1, duration: 0.28, ease: 'power2.out', stagger: 0.05 }, 0.02);
    tl.to(srcs, {
      x: function (i) { return 150 + (i % 2) * 14; },
      y: function (i) { return 40 - i * 20; },
      scale: 0.32, opacity: 0.18,
      duration: 0.20, ease: 'power2.in', stagger: 0.04
    }, 0.30);

    // B (0.28–0.72) BUILD IT BRAIN 이 커지며 생성 + Twin(왼쪽) / Insight(오른쪽)가 들어와 Brain 에 결합.
    tl.fromTo(brain,
      { scale: 0.14, opacity: 0.25 },
      { scale: 1, opacity: 1, duration: 0.20, ease: 'back.out(1.5)' }, 0.28);
    tl.fromTo(twin,
      { x: -150, y: 46, opacity: 0 },
      { x: 0, y: 0, opacity: 1, duration: 0.18, ease: 'power2.out' }, 0.38);
    tl.fromTo(insight,
      { x: 150, y: 46, opacity: 0 },
      { x: 0, y: 0, opacity: 1, duration: 0.18, ease: 'power2.out' }, 0.42);
    tl.to(brain, { scale: 1.1, duration: 0.09, yoyo: true, repeat: 1, ease: 'power1.inOut' }, 0.56);

    // C (0.50–0.80) Brain 오른쪽에서 원본 문제 → 변형 A / 변형 B → 서술형이 부채꼴로 나온다.
    tl.fromTo(qouts,
      { x: function (i) { return -[210, 250, 250, 232][i]; }, y: function (i) { return [96, 34, -34, -100][i] || 0; }, scale: 0.24, opacity: 0 },
      { x: 0, y: 0, scale: 1, opacity: 1, duration: 0.22, ease: 'power2.out', stagger: 0.07 }, 0.50);

    // D (0.74–1.0) 문제들이 아래로 모여 한 장의 FINAL EXAM 으로 조립된다.
    tl.fromTo(finalSheet,
      { scale: 0.42, opacity: 0, y: 66 },
      { scale: 1, opacity: 1, y: 0, duration: 0.22, ease: 'power2.out' }, 0.74);
    tl.to(qouts, {
      x: function (i) { return [10, -28, -28, 10][i] || 0; },
      y: function (i) { return [150, 96, 44, -8][i] || 0; },
      scale: 0.5, opacity: 0.28,
      duration: 0.22, ease: 'power2.inOut', stagger: 0.05
    }, 0.76);
    tl.to(finalSheet, { scale: 1.05, duration: 0.05, yoyo: true, repeat: 1, ease: 'power1.inOut' }, 0.9);

    // timeline 총 길이를 정확히 1 로 고정(빈 tween 패딩).
    tl.to({}, { duration: 0.001 }, 1);
    timelines.push(tl);

    var lastIdx = -1;
    function paint(progress) {
      var p = Math.max(0, Math.min(1, progress));
      tl.progress(p);
      var idx = p >= 1 ? 4 : Math.max(0, Math.min(4, Math.floor(p * 5)));
      if (idx !== lastIdx) {
        lastIdx = idx;
        emitScene(root, 'sec-digitaltwin', idx, 5);
      }
    }

    // start 를 늦게 잡는다 — progress 0 이 "stage 가 뷰포트 안에 sticky 로 자리잡은" 시점이 되어야
    // 자료 유입부터 사용자 눈에 보인다. steps(드라이버)는 데스크톱에서 충분히 길다(각 step min-height).
    var st = ST().create({
      id: 'cinematic-ai-native',
      trigger: driver,
      start: 'top 72%',
      end: 'bottom 42%',
      scrub: 0.6,
      invalidateOnRefresh: true,
      onUpdate: function (self) { paint(self.progress); },
      onRefresh: function (self) { fitCanvas(stage, canvas); paint(self.progress); }
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
