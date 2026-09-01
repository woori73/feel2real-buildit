/**
 * Vertical Scene Flow — 세로 스크롤 위치에 따라 섹션이 enter → active → exit 한다.
 *
 * PAGE = Vertical Story. 지금 보는 섹션은 선명(active), 위로 지나간 섹션은 물러나며 흐려지고
 * (exit), 아래에서 올라올 섹션은 흐리게 대기(enter)한다. **1회성 reveal 이 아니다** — 위/아래
 * 스크롤에 따라 상태가 다시 바뀐다(bidirectional). 표현(opacity/transform)은 academy_scene.css.
 *
 * 원칙
 *   - IntersectionObserver 만 쓴다(scroll 이벤트·매 프레임 측정 루프 없음). threshold 교차마다
 *     entry.boundingClientRect / rootBounds 로 상태를 계산한다.
 *   - 섹션에는 opacity + 작은 translateY 만 걸린다 → transform 이 IO rect 를 흔든다. 그래서
 *     enter↔active, exit↔active 임계치를 비대칭(dead-zone)으로 둬 상태가 튀지 않게 한다.
 *   - prefers-reduced-motion / IO 미지원 → 전부 active(정적 최종). 모듈 미로드 → html.js-scene
 *     이 안 붙어 섹션이 정적으로 완전히 보인다.
 *   - 가로 스크롤(#sec-featured 학교 카드 등)은 섹션의 세로 rect 를 바꾸지 않으므로 영향 없다.
 *   - #sec-digitaltwin(sticky visual) / #sec-featured(가로 트랙)은 자식 transform 을 받지 않는다
 *     (academy_scene.css :not(...)) — opacity 페이드만.
 */
(function (global) {
  'use strict';

  var doc = global.document;
  if (!doc || !doc.documentElement) return;

  var REDUCED = typeof global.matchMedia === 'function'
    && global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var HAS_IO = typeof global.IntersectionObserver === 'function';

  // 로드 즉시(= academy_public.js 렌더보다 먼저) gate. 모듈 미로드 → 이 클래스도 없음 → 정적.
  doc.documentElement.classList.add('js-scene');

  var io = null;

  function viewportHeight() {
    return global.innerHeight
      || (doc.documentElement && doc.documentElement.clientHeight)
      || 800;
  }

  // 비대칭 임계치: active 로 (다시) 들어오려면 조금 더 확실히 들어와야 한다(transform 되먹임 방지).
  function classify(rect, vh, prev) {
    if (prev === 'exit') {
      return rect.bottom >= vh * 0.50 ? (rect.top >= vh * 0.64 ? 'enter' : 'active') : 'exit';
    }
    if (prev === 'enter') {
      return rect.top <= vh * 0.44 ? (rect.bottom <= vh * 0.36 ? 'exit' : 'active') : 'enter';
    }
    if (rect.bottom <= vh * 0.36) return 'exit';
    if (rect.top >= vh * 0.64) return 'enter';
    return 'active';
  }

  /**
   * sections: 관찰할 섹션 요소 배열/NodeList(순서 무관). CSR 재렌더마다 호출 — 이전 IO 를 끊는다.
   */
  function wire(sections) {
    var els = Array.prototype.slice.call(sections || []).filter(Boolean);
    if (io && typeof io.disconnect === 'function') { io.disconnect(); io = null; }
    if (!els.length) return;

    if (REDUCED || !HAS_IO) {
      els.forEach(function (el) { el.setAttribute('data-scene', 'active'); });
      return;
    }

    var vh = viewportHeight();
    // 초기 상태 동기 세팅(깜빡임 방지 — 이미 보이는 섹션은 active 로 시작).
    els.forEach(function (el) {
      if (typeof el.getBoundingClientRect === 'function') {
        el.setAttribute('data-scene', classify(el.getBoundingClientRect(), vh, null));
      }
    });

    var thresholds = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1];
    var sawActive = false;
    io = new global.IntersectionObserver(function (entries) {
      for (var i = 0; i < entries.length; i += 1) {
        var el = entries[i].target;
        var rb = entries[i].rootBounds;
        var h = (rb && rb.height) || viewportHeight();
        var prev = el.getAttribute('data-scene');
        var next = classify(entries[i].boundingClientRect, h, prev);
        if (next === 'active') sawActive = true;
        if (next !== prev) el.setAttribute('data-scene', next);
      }
    }, { threshold: thresholds });
    els.forEach(function (el) { io.observe(el); });

    // 하드 페일세이프: IO 가 6초 안에 한 번도 active 를 못 만들면(계측 이슈) 전부 active 로.
    global.setTimeout(function () {
      if (sawActive) return;
      if (io && typeof io.disconnect === 'function') io.disconnect();
      els.forEach(function (el) { el.setAttribute('data-scene', 'active'); });
    }, 6000);
  }

  global.F2RSceneFlow = Object.freeze({ wire: wire });
})(typeof window !== 'undefined' ? window : globalThis);
