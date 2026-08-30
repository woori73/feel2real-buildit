/**
 * Storefront Motion primitives — 공개 학원 페이지(academy_public.js)가 소비한다.
 *
 * Builder V2 방향: PAGE = Vertical Story. 각 섹션은 스크롤 진입 시 "의미가 진행되는" 모션을
 * 갖는다. 여기서는 그 최소 재사용 단위 3개만 둔다(범용 animation engine 아님).
 *
 *   onEnter(el, cb)          — 요소가 뷰포트에 한 번 들어오면 cb. (IntersectionObserver, 1회)
 *   revealSequence(root)     — root 안의 .f2r-reveal 를 DOM 순서대로 stagger 등장(정보 위계).
 *   processProgress(list)    — list 안의 [data-step] 을 "커넥터 활성 → 다음 카드 활성" 순으로
 *                              순차 진행. 지나간 단계는 is-done, 끝나면 list 에 is-complete.
 *   sceneFlow(sections)      — 각 Story Section 에 스크롤 위치 기반 data-scene = enter|active|exit
 *                              을 준다(V2-10). 1회성 아님 — 위/아래 스크롤에 따라 다시 바뀐다
 *                              (bidirectional). IntersectionObserver 만 쓰고 scroll 이벤트를 걸지
 *                              않는다. CSS(academy_scene.css)가 opacity/translateY 로 표현한다.
 *
 * 규칙
 *   - scroll 이벤트를 직접 계산하지 않는다(IntersectionObserver만).
 *   - 새 animation 라이브러리를 추가하지 않는다(CSS transform/opacity/transition + 클래스 토글).
 *   - academy_public.js 의 GSAP applyReveal() 과 요소가 겹치지 않는다(그쪽은 data-fade, 이쪽은
 *     .f2r-reveal / [data-step]). 책임 분리.
 *   - prefers-reduced-motion: reduce → 모든 항목 즉시 최종 상태(이동/트랜지션 없음).
 *   - IntersectionObserver 미지원 → 즉시 최종 상태(콘텐츠는 모션 없이도 완전히 보인다).
 */
(function (global) {
  'use strict';

  var REDUCED = typeof global.matchMedia === 'function'
    && global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var HAS_IO = typeof global.IntersectionObserver === 'function';
  var INSTANT = REDUCED || !HAS_IO;

  /**
   * 요소가 처음 뷰포트에 들어오면 cb 한 번 실행하고 관찰 종료.
   * 로드 시점에 이미 뷰포트 안이면(앵커 진입·짧은 페이지·IO 초기 콜백 누락 환경) 다음 프레임에
   * getBoundingClientRect 로 직접 확인해 즉시 실행한다 — 그 외에는 IntersectionObserver 로 대기.
   */
  function onEnter(el, cb, options) {
    if (!el || typeof cb !== 'function') return;
    if (INSTANT) { cb(el); return; }
    var opt = options || {};
    var done = false;
    function run() { if (done) return; done = true; cb(el); }
    var io = new global.IntersectionObserver(function (entries) {
      for (var i = 0; i < entries.length; i += 1) {
        if (!entries[i].isIntersecting) continue;
        io.disconnect();
        run();
        return;
      }
    }, { threshold: opt.threshold == null ? 0.3 : opt.threshold, rootMargin: opt.rootMargin || '0px' });
    io.observe(el);

    // 안전장치 1: 로드 시점에 이미 뷰포트 안이면(앵커·짧은 페이지·IO 초기 콜백 누락) 다음 프레임에 실행.
    function checkInView() {
      if (done || typeof el.getBoundingClientRect !== 'function') return;
      var r = el.getBoundingClientRect();
      var vh = global.innerHeight
        || (global.document && global.document.documentElement && global.document.documentElement.clientHeight)
        || 0;
      if (r.top < vh && r.bottom > 0) { io.disconnect(); run(); }
    }
    if (typeof global.requestAnimationFrame === 'function') global.requestAnimationFrame(checkInView);

    // 안전장치 2(하드 페일세이프): IO/rAF 를 아예 흘리는 환경에서도 콘텐츠가 영구히 숨지 않도록
    // 6초 뒤 위치와 무관하게 무조건 실행한다. 그 전에 스크롤로 도달하면 정상 모션이 재생된다.
    // 스크롤 이벤트는 걸지 않는다.
    global.setTimeout(function () { io.disconnect(); run(); }, 6000);
  }

  function items(root, selector) {
    return root ? Array.prototype.slice.call(root.querySelectorAll(selector)) : [];
  }

  /**
   * reveal-sequence: root 진입 시 .f2r-reveal 를 DOM 순서대로 stagger 등장.
   * options.start(첫 지연 ms, 기본 0), options.step(항목 간격 ms, 기본 90).
   * 개별 등장 character(무게/스케일)는 CSS 가 요소 종류별로 정한다 — 여기서는 is-in 만 붙인다.
   */
  function revealSequence(root, options) {
    var els = items(root, '.f2r-reveal');
    if (!els.length) return;
    if (INSTANT) { els.forEach(function (el) { el.classList.add('is-in'); }); return; }
    var opt = options || {};
    var start = opt.start || 0;
    var step = opt.step || 90;
    onEnter(root, function () {
      els.forEach(function (el, i) {
        global.setTimeout(function () { el.classList.add('is-in'); }, start + i * step);
      });
    }, opt);
  }

  /**
   * process-progress: list 진입 시 [data-step] 을 순차 진행.
   * 각 tick: 이전 단계 is-done → 이번 단계로 들어오는 커넥터 활성(is-linked) →
   *          linkLead ms 뒤 이번 카드 활성(is-active). 첫 단계는 커넥터가 없어 바로 is-active.
   * 마지막 단계가 끝나면 list 에 is-complete(뒤따르는 note 가 CSS 형제 선택자로 등장).
   * options.start(기본 250), options.step(단계 간격, 기본 180), options.linkLead(커넥터→카드, 기본 90).
   */
  function processProgress(list, options) {
    var steps = items(list, '[data-step]');
    if (!steps.length) return;
    if (INSTANT) {
      steps.forEach(function (el) { el.classList.add('is-linked', 'is-active', 'is-done'); });
      if (list) list.classList.add('is-complete');
      return;
    }
    var opt = options || {};
    var start = opt.start || 250;
    var step = opt.step || 180;
    var linkLead = opt.linkLead == null ? 90 : opt.linkLead;
    onEnter(list, function () {
      steps.forEach(function (el, i) {
        global.setTimeout(function () {
          if (i > 0) {
            steps[i - 1].classList.add('is-done');
            el.classList.add('is-linked');
            global.setTimeout(function () { el.classList.add('is-active'); }, linkLead);
          } else {
            el.classList.add('is-active');
          }
          if (i === steps.length - 1) {
            global.setTimeout(function () {
              el.classList.add('is-done');
              if (list) list.classList.add('is-complete');
            }, step);
          }
        }, start + i * step);
      });
    }, opt);
  }

  /**
   * scene-flow (V2-10): PAGE = Vertical Story. 각 Story Section 이 스크롤 위치에 따라
   *   enter  — 아직 뷰포트 아래(다음에 등장할 장면)
   *   active — 현재 뷰포트를 차지한 장면
   *   exit   — 위로 지나간 장면(퇴장)
   * 을 data-scene 으로 표현한다. 1회성 entrance 가 아니라 **양방향** — 위로 다시 스크롤하면
   * exit → active → enter 로 되돌아온다.
   *
   * IntersectionObserver 만 쓴다(scroll 이벤트 없음). threshold 교차마다 콜백이 오고,
   * 그때 entry.boundingClientRect / rootBounds 로 상태를 계산한다 — 매 프레임 측정 루프 없음.
   * 섹션에는 opacity + 작은 translateY 만 걸리므로(CSS) transform 이 IO rect 를 흔든다. 그래서
   * 진입/이탈 임계치를 비대칭(dead-zone)으로 둬 상태가 튀지 않게 한다.
   *
   * data-story-staged(데스크톱 pin, academy_story.js) 섹션은 scene-flow 가 건드리지 않는다.
   * INSTANT(reduced-motion / IO 미지원) → 전부 active(정적 최종 상태).
   */
  function classifyScene(rect, vh, prev) {
    // 비대칭 임계치: active 로 돌아오려면 조금 더 들어와야 한다(transform 되먹임 방지).
    if (prev === 'exit') {
      return rect.bottom >= vh * 0.52 ? (rect.top >= vh * 0.60 ? 'enter' : 'active') : 'exit';
    }
    if (prev === 'enter') {
      return rect.top <= vh * 0.48 ? (rect.bottom <= vh * 0.40 ? 'exit' : 'active') : 'enter';
    }
    if (rect.bottom <= vh * 0.40) return 'exit';
    if (rect.top >= vh * 0.60) return 'enter';
    return 'active';
  }

  function sceneFlow(sections) {
    var els = Array.prototype.slice.call(sections || []).filter(Boolean);
    if (!els.length) return null;
    if (INSTANT) {
      els.forEach(function (el) { el.setAttribute('data-scene', 'active'); });
      return null;
    }
    var vhNow = global.innerHeight
      || (global.document && global.document.documentElement && global.document.documentElement.clientHeight)
      || 800;
    // 초기 상태를 동기적으로 세팅(깜빡임 방지 — 이미 보이는 섹션은 active 로 시작).
    els.forEach(function (el) {
      if (el.getAttribute('data-story-staged') === 'true') { el.removeAttribute('data-scene'); return; }
      if (typeof el.getBoundingClientRect === 'function') {
        el.setAttribute('data-scene', classifyScene(el.getBoundingClientRect(), vhNow, null));
      }
    });
    var thresholds = [0, 0.08, 0.16, 0.24, 0.32, 0.4, 0.5, 0.6, 0.68, 0.76, 0.84, 0.92, 1];
    var io = new global.IntersectionObserver(function (entries) {
      for (var i = 0; i < entries.length; i += 1) {
        var el = entries[i].target;
        if (el.getAttribute('data-story-staged') === 'true') { el.removeAttribute('data-scene'); continue; }
        var rb = entries[i].rootBounds;
        var vh = (rb && rb.height) || global.innerHeight || 800;
        var prev = el.getAttribute('data-scene');
        var next = classifyScene(entries[i].boundingClientRect, vh, prev);
        if (next !== prev) el.setAttribute('data-scene', next);
      }
    }, { threshold: thresholds });
    els.forEach(function (el) { io.observe(el); });
    return io;
  }

  global.F2RStorefrontMotion = Object.freeze({
    reduced: REDUCED,
    supported: HAS_IO,
    onEnter: onEnter,
    revealSequence: revealSequence,
    processProgress: processProgress,
    sceneFlow: sceneFlow
  });

  // 모듈이 로드된 즉시(= academy_public.js 렌더보다 먼저) gate 클래스를 붙인다.
  // 렌더 후에 붙이면 .f2r-reveal 이 opacity:1 로 한 프레임 보였다가 0 으로 트랜지션되며 깜빡인다.
  // 미리 붙여두면 요소가 생성 시점부터 opacity:0 이라 시작 트랜지션이 없다. 모듈이 아예 로드되지
  // 않으면 이 코드도 안 돌아 CSS opacity:0 도 안 걸린다(정적으로 완전히 보임 — 기존 안전장치 유지).
  if (typeof global.document !== 'undefined'
      && global.document.documentElement
      && global.document.documentElement.classList) {
    global.document.documentElement.classList.add('js-storefront-motion');
    // scene-flow(V2-10) 게이트 — 붙어야만 academy_scene.css 의 초기 opacity/transform 이 걸린다.
    // 모듈 미로드 시 이 클래스도 안 붙어 섹션이 정적으로 완전히 보인다.
    global.document.documentElement.classList.add('js-scene');
  }
})(typeof window !== 'undefined' ? window : globalThis);
