/**
 * Storefront Cinematic — GSAP + ScrollTrigger 를 소유하는 *유일한* 모듈.
 *
 * 책임 범위는 정확히 한 섹션이다:
 *   #sec-digitaltwin  AI Native Pipeline — 하나의 canvas 안에서 동일한 오브젝트들이 이동한다:
 *   학교 자료 4개 → BUILD IT BRAIN → EXAMINER DIGITAL TWIN + TEACHER INSIGHT 결합 → 문제
 *   4개 생성 → 한 장의 FINAL EXAM 으로 조립. 정적 scene 교체가 아니다 — 하나의 timeline.
 *     · 데스크톱(≥900): 가로형 canvas(660×460). 세로 스크롤 progress 0→1 을 timeline 에 직접
 *       묶는다(ScrollTrigger scrub, pin 아님).
 *     · 모바일(<900): 세로형 canvas(330×900, 오브젝트 2×2 재배치, 배치는 academy_cinematic.css).
 *       섹션이 뷰포트에 들어오면 ~6.5s timeline 을 1회 자동재생하고 FINAL 에서 멈춘다
 *       (scroll scrub 아님, 무한 반복 없음). reduced-motion → 재생 없이 완성 Pipeline 정적.
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

  var MOBILE_MAX = 900;   // < 900 = 모바일(세로형 pipeline + 1회 자동재생), ≥ 900 = 데스크톱(scroll scrub)

  var registered = false;
  // 데스크톱 scroll-scrub 경로 사용 조건.
  function usable() {
    if (!gsap() || !ST()) return false;
    if (reduced()) return false;
    if (viewportWidth() < MOBILE_MAX) return false;
    if (!registered) {
      try { gsap().registerPlugin(ST()); } catch (e) { return false; }
      // 모바일 주소창 리사이즈로 refresh 가 계속 도는 것을 막는다(우리는 데스크톱 전용).
      try { ST().config({ ignoreMobileResize: true }); } catch (e2) { /* older ST */ }
      registered = true;
    }
    return true;
  }
  // 모바일 1회-자동재생 경로 사용 조건(ScrollTrigger 불필요, gsap core 만).
  function mobileUsable() {
    return !!gsap() && !reduced() && viewportWidth() < MOBILE_MAX;
  }

  // canvas 좌표계 — 데스크톱은 가로형(660×460), 모바일은 세로형(330×900). fitCanvas 가 stage
  // 실제 폭에 맞춰 scale 하므로 px 이동량도 함께 스케일된다. 두 레이아웃의 절대배치는 CSS.
  function canvasBox() {
    return viewportWidth() < MOBILE_MAX ? { w: 330, h: 760 } : { w: 660, h: 460 };
  }

  var built = [];        // ScrollTrigger 인스턴스
  var timelines = [];    // gsap.timeline 인스턴스
  var byId = {};         // id → ScrollTrigger (seek 용)
  var lastRoot = null;
  var resizeBound = false;
  var resizeTimer = null;
  var mobileIO = null;   // 모바일 자동재생 트리거 IntersectionObserver
  var mobilePlayed = false;   // 한 번 재생하면 CSR 재렌더에도 다시 재생하지 않는다

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
    if (mobileIO && typeof mobileIO.disconnect === 'function') {
      try { mobileIO.disconnect(); } catch (e3) { /* noop */ }
    }
    mobileIO = null;
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
  function fitCanvas(stage, canvas) {
    var box = canvasBox();
    var w = stage.clientWidth || box.w;
    if (!w) return;
    var s = w / box.w;
    canvas.style.transform = 'scale(' + s + ')';
    stage.style.height = (box.h * s) + 'px';
  }

  function wireAiNativeDesktop(root) {
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

  // ── AI Native — 모바일 세로형 Pipeline (scroll scrub 아님, 1회 자동재생) ──────────
  // 데스크톱 가로 canvas 를 축소하지 않는다. 같은 오브젝트를 세로로 재배치(2×2 자료 → Twin·
  // Insight → Brain → 2×2 문제 → FINAL, 좌표계 330×900, 배치는 academy_cinematic.css @media).
  // 섹션이 뷰포트에 들어오면 timeline 을 0 부터 한 번만 재생하고 FINAL 상태에서 멈춘다.
  // 무한 반복·flashing 없음. reduced-motion 이면 이 경로 자체가 실행되지 않아(mobileUsable=false)
  // CSS 기본 배치(완성된 Pipeline)가 정적으로 보인다.
  function wireAiNativeMobile(root) {
    var section = root.querySelector('#sec-digitaltwin');
    if (!section) return;
    var stage = section.querySelector('[data-ai-stage]');
    var canvas = section.querySelector('[data-ai-canvas]');
    if (!stage || !canvas) return;
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

    if (flowPath && typeof flowPath.getTotalLength === 'function') {
      var len = 900;
      try { len = flowPath.getTotalLength() || 900; } catch (e) { len = 900; }
      g.set(flowPath, { strokeDasharray: len, strokeDashoffset: len });
      tl.to(flowPath, { strokeDashoffset: 0, duration: 6.2, ease: 'none' }, 0);
    }

    // 0–1s: 자료 4개 등장(2×2)
    tl.fromTo(srcs,
      { y: -46, opacity: 0, scale: 0.8 },
      { y: 0, opacity: 1, scale: 1, duration: 0.6, ease: 'power2.out', stagger: 0.14 }, 0.1);
    // 1–2.5s: 자료가 Brain 방향(아래 중앙)으로 이동하며 축소
    tl.to(srcs, {
      x: function (i) { return [76, -76, 76, -76][i] || 0; },
      y: 210, scale: 0.34, opacity: 0.2,
      duration: 1.2, ease: 'power2.inOut', stagger: 0.1
    }, 1.0);
    // 2.3–3.6s: Brain 생성 + Twin / Insight 결합
    tl.fromTo(brain, { scale: 0.15, opacity: 0.3 }, { scale: 1, opacity: 1, duration: 1.0, ease: 'back.out(1.4)' }, 2.3);
    tl.fromTo(twin, { y: 40, opacity: 0 }, { y: 0, opacity: 1, duration: 0.7, ease: 'power2.out' }, 2.6);
    tl.fromTo(insight, { y: 40, opacity: 0 }, { y: 0, opacity: 1, duration: 0.7, ease: 'power2.out' }, 2.9);
    tl.to(brain, { scale: 1.08, duration: 0.35, yoyo: true, repeat: 1, ease: 'power1.inOut' }, 3.5);
    // 3.6–5s: Brain 에서 문제 4개 생성(2×2, 아래로)
    tl.fromTo(qouts,
      { y: -110, scale: 0.25, opacity: 0 },
      { y: 0, scale: 1, opacity: 1, duration: 0.9, ease: 'power2.out', stagger: 0.22 }, 3.7);
    // 5–6.5s: 문제가 FINAL EXAM 으로 조립
    tl.fromTo(finalSheet, { y: 54, scale: 0.42, opacity: 0 }, { y: 0, scale: 1, opacity: 1, duration: 1.0, ease: 'power2.out' }, 5.0);
    tl.to(qouts, {
      x: function (i) { return [36, -36, 36, -36][i] || 0; },
      y: function (i) { return [134, 134, 88, 88][i] || 0; },
      scale: 0.5, opacity: 0.28,
      duration: 1.0, ease: 'power2.inOut', stagger: 0.08
    }, 5.2);

    timelines.push(tl);
    // playhead 를 명시적으로 0 에 두어 모든 .fromTo() 의 "from"(흩어진 시작) 상태를 렌더한다.
    tl.pause(0);

    // 이미 재생했으면(= CSR 재렌더) 다시 재생하지 않고 완성 상태로.
    if (mobilePlayed || typeof global.IntersectionObserver !== 'function') {
      tl.progress(1);
      return;
    }
    function playOnce() {
      if (mobilePlayed) return;
      if (mobileIO && typeof mobileIO.disconnect === 'function') { try { mobileIO.disconnect(); } catch (e) { /* noop */ } }
      mobileIO = null;
      mobilePlayed = true;
      tl.play(0);
      emitScene(root, 'sec-digitaltwin', 0, 5);
    }
    mobileIO = new global.IntersectionObserver(function (entries) {
      for (var i = 0; i < entries.length; i += 1) {
        if (entries[i].isIntersecting) { playOnce(); return; }
      }
    }, { threshold: 0.3 });
    mobileIO.observe(section);
    // 페일세이프(스크롤 리스너 아님, 고정 시각 1회): IO 콜백이 안 오는 환경에서도, 섹션이
    // 이미 화면에 들어와 있으면 재생한다. 아직 안 보이면 그대로 두고 IO 를 기다린다.
    global.setTimeout(function () {
      if (mobilePlayed || typeof section.getBoundingClientRect !== 'function') return;
      var r = section.getBoundingClientRect();
      var vh = global.innerHeight || (doc.documentElement && doc.documentElement.clientHeight) || 800;
      if (r.top < vh * 0.85 && r.bottom > vh * 0.15) playOnce();
    }, 3500);
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

    if (usable()) {
      // 데스크톱 — 세로 스크롤 progress 0→1 을 하나의 timeline 에 직접 묶는다(scrub).
      mark();
      try {
        wireAiNativeDesktop(root);
      } catch (e) {
        teardown();
        return;
      }
      if (!built.length) {
        if (doc.documentElement.classList.contains('js-cinematic')) {
          doc.documentElement.classList.remove('js-cinematic');
        }
        return;
      }
      // reveal 트랜지션·이미지·폰트가 늦게 정착하면 start/end 가 밀린다 — 몇 번 더 맞춘다.
      scheduleRefresh();
      return;
    }

    if (mobileUsable()) {
      // 모바일 — 세로형 Pipeline, 섹션 진입 시 1회 자동재생(scroll scrub 아님, js-cinematic 안 붙임).
      try {
        wireAiNativeMobile(root);
      } catch (e2) {
        teardown();
      }
      return;
    }
    // GSAP 미로드 / reduced-motion → no-op. CSS 기본 배치가 정적 Pipeline 다이어그램.
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
