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

    // 뷰포트 안이면 즉시 실행하는 이중 안전장치(IO 초기 콜백을 흘리는 환경 + 앵커/짧은 페이지).
    // 스크롤 이벤트를 걸지 않는다 — 다음 프레임 1회 + 4초 뒤 1회만 확인한다.
    function checkInView() {
      if (done || typeof el.getBoundingClientRect !== 'function') return;
      var r = el.getBoundingClientRect();
      var vh = global.innerHeight
        || (global.document && global.document.documentElement && global.document.documentElement.clientHeight)
        || 0;
      if (r.top < vh && r.bottom > 0) { io.disconnect(); run(); }
    }
    if (typeof global.requestAnimationFrame === 'function') global.requestAnimationFrame(checkInView);
    global.setTimeout(checkInView, 4000);
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

  global.F2RStorefrontMotion = Object.freeze({
    reduced: REDUCED,
    supported: HAS_IO,
    onEnter: onEnter,
    revealSequence: revealSequence,
    processProgress: processProgress
  });

  // 모듈이 로드된 즉시(= academy_public.js 렌더보다 먼저) gate 클래스를 붙인다.
  // 렌더 후에 붙이면 .f2r-reveal 이 opacity:1 로 한 프레임 보였다가 0 으로 트랜지션되며 깜빡인다.
  // 미리 붙여두면 요소가 생성 시점부터 opacity:0 이라 시작 트랜지션이 없다. 모듈이 아예 로드되지
  // 않으면 이 코드도 안 돌아 CSS opacity:0 도 안 걸린다(정적으로 완전히 보임 — 기존 안전장치 유지).
  if (typeof global.document !== 'undefined'
      && global.document.documentElement
      && global.document.documentElement.classList) {
    global.document.documentElement.classList.add('js-storefront-motion');
  }
})(typeof window !== 'undefined' ? window : globalThis);
