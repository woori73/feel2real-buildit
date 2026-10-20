/**
 * Academy Motion — 공개 홈페이지 동적 인터랙션 컨트롤러.
 *
 * academy_reveal.js(스크롤 등장, 1회성 primitive)와 함께 동작하는 상위 레이어다.
 * 이 파일이 없어도 페이지는 정적으로 완전히 보인다(점진적 향상).
 *
 * 마크업 계약
 *  [data-motion]                 루트 효과 선택: slide(기본) | fade | none
 *                                academy_reveal.css 의 토큰 오버라이드와 연동된다.
 *  [data-motion-speed]           루트 속도 선택: slow | normal(기본) | fast
 *  [data-countup]                숫자 카운트업. 텍스트 앞부분의 숫자를 파싱해 애니메이션한다.
 *                                ("20만원" → 0→20 + "만원" 고정. 숫자 없으면 정적 유지)
 *  [data-motion-collapse]        펼치기/접기 영역. 자식으로
 *                                button[data-motion-collapse-toggle] (aria-expanded/aria-controls),
 *                                [data-motion-collapse-region] > .motion-collapse-inner 를 둔다.
 *
 * wire(root): CSR 재렌더(빌더 미리보기)마다 호출. 이미 처리한 요소는 건너뛴다.
 * applyPrefs({effect, speed}): builderDraft 모션 설정을 루트에 반영한다.
 *
 * 접근성: 모든 토글은 네이티브 <button>. prefers-reduced-motion 이면
 * 애니메이션을 건너뛰고 최종 상태를 즉시 적용한다.
 */
(function (root, factory) {
  var api = factory(root);
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.AcademyMotion = api;
})(typeof window !== 'undefined' ? window : globalThis, function (global) {
  'use strict';

  var doc = global.document;
  var WIRE_ATTR = 'data-motion-wired';

  function reduced() {
    return typeof global.matchMedia === 'function' &&
      global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
  function hasIO() {
    return typeof global.IntersectionObserver === 'function';
  }
  function raf() {
    return typeof global.requestAnimationFrame === 'function'
      ? global.requestAnimationFrame.bind(global)
      : null;
  }

  function easeOutCubic(t) {
    var p = Math.min(1, Math.max(0, t));
    return 1 - Math.pow(1 - p, 3);
  }

  /**
   * "20만원" → { value: 20, suffix: "만원", decimals: 0 }
   * "32.5%"  → { value: 32.5, suffix: "%", decimals: 1 }
   * 숫자 없음 → null
   */
  function parseCountUp(text) {
    var m = /^([0-9][0-9,]*(\.[0-9]+)?)([\s\S]*)$/.exec(String(text || '').trim());
    if (!m) return null;
    var num = parseFloat(m[1].replace(/,/g, ''));
    if (!isFinite(num)) return null;
    return { value: num, suffix: m[3] || '', decimals: m[2] ? m[2].length - 1 : 0 };
  }

  function formatCountUp(v, decimals) {
    if (decimals > 0) return v.toFixed(decimals);
    return Math.round(v).toLocaleString('ko-KR');
  }

  function speedDuration(base) {
    if (!doc) return base;
    var s = doc.documentElement.getAttribute('data-motion-speed');
    if (s === 'slow') return Math.round(base * 1.6);
    if (s === 'fast') return Math.round(base * 0.55);
    return base;
  }

  function animateCount(el, target) {
    var request = raf();
    var duration = speedDuration(1100);
    function render(v) {
      el.textContent = formatCountUp(v, target.decimals) + target.suffix;
    }
    if (reduced() || !request || duration <= 0) {
      render(target.value);
      return;
    }
    var start = null;
    function frame(ts) {
      if (start === null) start = ts;
      var p = Math.min(1, (ts - start) / duration);
      render(target.value * easeOutCubic(p));
      if (p < 1) request(frame);
    }
    request(frame);
  }

  /**
   * 실제로 텍스트를 담는 홀더를 찾는다.
   * <b data-countup><span class="u" data-builder-draft="...">20만원</span></b> 처럼
   * 자식 요소가 텍스트를 감싸면 그 자식의 textContent만 갱신한다 —
   * 부모의 innerHTML을 덮어쓰면 빌더의 data-builder-draft 편집 속성이 파괴된다.
   */
  function textHolder(el) {
    if (el.childElementCount === 1 && el.firstElementChild &&
        el.firstElementChild.textContent.trim().length > 0) {
      return el.firstElementChild;
    }
    return el;
  }

  function wireCountUp(scope) {
    var els = scope.querySelectorAll('[data-countup]:not([' + WIRE_ATTR + '])');
    for (var i = 0; i < els.length; i += 1) {
      (function (el) {
        el.setAttribute(WIRE_ATTR, 'true');
        var holder = textHolder(el);
        var target = parseCountUp(holder.textContent);
        if (!target) return; // 숫자 없음 → 정적 유지
        if (reduced() || !hasIO()) {
          holder.textContent = formatCountUp(target.value, target.decimals) + target.suffix;
          return;
        }
        var io = new global.IntersectionObserver(function (entries, obs) {
          for (var j = 0; j < entries.length; j += 1) {
            if (!entries[j].isIntersecting) continue;
            obs.unobserve(entries[j].target);
            animateCount(textHolder(entries[j].target), target);
          }
        }, { threshold: 0.4 });
        io.observe(el);
      })(els[i]);
    }
  }

  function setCollapsed(collapse, collapsed, instant) {
    var toggle = collapse.querySelector('[data-motion-collapse-toggle]');
    var region = collapse.querySelector('[data-motion-collapse-region]');
    if (!toggle || !region) return;
    var inner = region.firstElementChild;
    toggle.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
    var label = collapse.getAttribute('data-motion-collapse-label') || '콘텐츠';
    toggle.textContent = collapsed ? label + ' 펼치기' : label + ' 접기';
    if (instant || reduced()) {
      region.classList.toggle('is-collapsed', collapsed);
      if (inner) inner.hidden = collapsed;
      return;
    }
    if (collapsed) {
      region.classList.add('is-collapsed');
      var done = false;
      var finish = function () {
        if (done) return;
        done = true;
        if (inner) inner.hidden = true;
      };
      region.addEventListener('transitionend', finish, { once: true });
      setTimeout(finish, 500); // transitionend 미발화 failsafe
    } else {
      if (inner) inner.hidden = false;
      // hidden 해제 후 리플로우를 한 프레임 기다려야 닫힘→열림 애니메이션이 발화한다.
      var request = raf();
      if (request) {
        request(function () { request(function () { region.classList.remove('is-collapsed'); }); });
      } else {
        region.classList.remove('is-collapsed');
      }
    }
  }

  function wireCollapse(scope) {
    var els = scope.querySelectorAll('[data-motion-collapse]:not([' + WIRE_ATTR + '])');
    for (var i = 0; i < els.length; i += 1) {
      (function (collapse) {
        collapse.setAttribute(WIRE_ATTR, 'true');
        var toggle = collapse.querySelector('[data-motion-collapse-toggle]');
        if (!toggle) return;
        toggle.addEventListener('click', function () {
          var collapsed = toggle.getAttribute('aria-expanded') === 'true';
          setCollapsed(collapse, collapsed, false);
        });
        // 키보드: 네이티브 button 이라 Enter/Space 자동 처리. 추가 작업 없음.
      })(els[i]);
    }
  }

  /**
   * builderDraft 모션 설정을 루트에 반영한다.
   * effect: slide | fade | none   (academy_reveal.css 토큰과 연동)
   * speed:  slow | normal | fast
   */
  function applyPrefs(prefs) {
    if (!doc) return;
    var effect = prefs && prefs.effect;
    var speed = prefs && prefs.speed;
    var rootEl = doc.documentElement;
    if (effect === 'fade' || effect === 'none' || effect === 'slide') {
      rootEl.setAttribute('data-motion', effect);
    } else {
      rootEl.removeAttribute('data-motion');
    }
    if (speed === 'slow' || speed === 'fast') {
      rootEl.setAttribute('data-motion-speed', speed);
    } else {
      rootEl.removeAttribute('data-motion-speed');
    }
  }

  function wire(scope) {
    var s = (scope && scope.querySelectorAll) ? scope : doc;
    if (!s) return;
    wireCountUp(s);
    wireCollapse(s);
  }

  // 초기 실행: DOM 파서 완료 시점에 1회. CSR 재렌더는 각 렌더러가 wire()를 호출한다.
  function boot() {
    wire(doc);
  }
  if (doc) {
    if (doc.readyState === 'loading') {
      doc.addEventListener('DOMContentLoaded', boot);
    } else {
      boot();
    }
  }

  return {
    wire: wire,
    applyPrefs: applyPrefs,
    parseCountUp: parseCountUp,
    formatCountUp: formatCountUp,
    textHolder: textHolder,
    easeOutCubic: easeOutCubic,
    setCollapsed: setCollapsed,
    prefersReducedMotion: reduced
  };
});
