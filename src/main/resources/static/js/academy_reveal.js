/**
 * Storefront reveal — 단 하나의 스크롤 모션 primitive.
 *
 * Shopify Dawn 계열 원칙(레퍼런스 연구 확정):
 *   - translateY + opacity → 최종 상태
 *   - 1회성. 나타나면 그대로 유지 — 지나간 콘텐츠를 흐리게/축소하지 않는다.
 *   - 형제 간 작은 stagger.
 *   - reveal 후 unobserve.
 *   - prefers-reduced-motion → 즉시 최종 상태(관찰 안 함).
 *   - IntersectionObserver 미지원·미발화 → 3초 failsafe 로 최종 상태.
 *
 * 마크업 계약
 *   [data-reveal]         트리거(그룹). 뷰포트에 들어오면 이 그룹의 하위 [data-reveal-item] 을 stagger.
 *   [data-reveal-item]    실제로 움직이는 요소. --reveal-order 로 지연이 정해진다(primitive 가 index 부여).
 *   요소 하나만 페이드시키려면 같은 요소에 [data-reveal data-reveal-item] 둘 다.
 *
 * 표현(수치)은 academy_reveal.css 의 --reveal-* 토큰. 로드 시점에 html.js-reveal 을 붙여
 * 초기 opacity:0 게이트가 마크업보다 먼저 걸리게 한다(플래시 방지). 모듈 미로드 → 게이트도
 * 안 붙어 콘텐츠가 정적으로 완전히 보인다.
 */
(function (global) {
  'use strict';

  var doc = global.document;
  if (!doc || !doc.documentElement) return;

  var REDUCED = typeof global.matchMedia === 'function'
    && global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var HAS_IO = typeof global.IntersectionObserver === 'function';

  doc.documentElement.classList.add('js-reveal');

  function each(list, fn) { Array.prototype.forEach.call(list, fn); }

  function revealGroup(group) {
    var items = group.querySelectorAll('[data-reveal-item]');
    for (var i = 0; i < items.length && i < 12; i += 1) {
      items[i].style.setProperty('--reveal-order', i);
    }
    group.classList.add('is-revealed');
  }

  /**
   * root 안의 모든 [data-reveal] 그룹을 배선한다. CSR 재렌더마다 호출 — 이미 처리한 그룹은 건너뛴다.
   */
  function wire(root) {
    var scope = (root && root.querySelectorAll) ? root : doc;
    var groups = scope.querySelectorAll('[data-reveal]:not([data-reveal-wired])');
    if (!groups.length) return;
    each(groups, function (g) { g.setAttribute('data-reveal-wired', 'true'); });

    if (REDUCED || !HAS_IO) {
      each(groups, revealGroup);
      return;
    }

    var io = new global.IntersectionObserver(function (entries, obs) {
      for (var i = 0; i < entries.length; i += 1) {
        if (!entries[i].isIntersecting) continue;
        revealGroup(entries[i].target);
        obs.unobserve(entries[i].target);
      }
    }, { rootMargin: '0px 0px -12% 0px', threshold: 0 });

    each(groups, function (g) { io.observe(g); });

    // failsafe: IO 가 안 오는 환경에서도 콘텐츠가 갇히지 않게.
    global.setTimeout(function () {
      each(groups, function (g) {
        if (!g.classList.contains('is-revealed')) { revealGroup(g); io.unobserve(g); }
      });
    }, 3000);
  }

  global.F2RReveal = Object.freeze({ wire: wire });
})(typeof window !== 'undefined' ? window : globalThis);
