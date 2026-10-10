/* BuildIt 2단계 — 테넌트 featured CTA.
 *
 * eduholic_featured_cta.js 의 테넌트 일반화판. 「#sec-featured 섹션 헤더에 pill CTA를
 * 붙인다」는 동작은 동일하고, 하드코딩된 호스트·문구·링크만 학원별 설정
 * (builderDraft['tenant.featuredCta'])에서 읽는다.
 *
 * 설정 스키마 (JSON):
 *   { "enabled": true,
 *     "label": "2026 중간고사 LIVE",
 *     "landingPath": "/campaign.html",
 *     "ariaLabel": "2026 중간고사 대비 현장 보기" }
 * enabled !== true 이면 아무 것도 하지 않는다.
 *
 * - 공용 섹션 마크업(academy_sections.js)은 건드리지 않는다. 제목 h2 를 titlerow 로
 *   감싸고 그 뒤에 <a> 를 추가할 뿐이다(클래스·data-* 속성 보존).
 * - 접근성: 실제 <a href> 라 키보드 focus 가능, aria-label 제공, :focus-visible 표시.
 */
(function () {
  'use strict';

  // eduholic 호스트는 기존 eduholic_featured_cta.js 가 담당한다.
  if (window.location.hostname === 'eduholic.feel2real.co.kr') return;
  if (!window.F2RTenantSettings) return;

  var PILL_CLASS = 'tenant-featured-pill';
  var ROW_CLASS = 'tenant-featured-titlerow';

  window.F2RTenantSettings.get('tenant.featuredCta').then(function (cfg) {
    if (!cfg) return;
    start(cfg);
  }).catch(function () {});

  function start(cfg) {
    var LANDING_PATH = cfg.landingPath || '';
    var LABEL = cfg.label || 'LIVE';
    if (!LANDING_PATH) return;
    if (window.location.pathname === LANDING_PATH) return;

    var applying = false;

    function apply() {
      if (applying) return;
      var head = document.querySelector('#sec-featured .featured-head');
      if (!head) return;
      var title = head.querySelector('h2.featured-title');
      if (!title) return;
      if (head.querySelector('.' + PILL_CLASS)) return; // 이미 주입됨

      applying = true;
      try {
        var row = title.closest('.' + ROW_CLASS);
        if (!row) {
          row = document.createElement('span');
          row.className = ROW_CLASS;
          title.parentNode.insertBefore(row, title);
          row.appendChild(title); // h2 는 클래스·data-reveal-item 그대로 유지된다
        }
        var link = document.createElement('a');
        link.className = PILL_CLASS;
        link.href = LANDING_PATH;
        link.setAttribute('aria-label', cfg.ariaLabel || LABEL);
        link.appendChild(document.createTextNode(LABEL + ' '));
        var arrow = document.createElement('span');
        arrow.setAttribute('aria-hidden', 'true');
        arrow.textContent = '→';
        link.appendChild(arrow);
        row.appendChild(link);
      } finally {
        applying = false;
      }
    }

    function injectStyle() {
      if (document.getElementById('tenantFeaturedCtaStyle')) return;
      var style = document.createElement('style');
      style.id = 'tenantFeaturedCtaStyle';
      style.textContent = [
        '.' + ROW_CLASS + '{display:flex;flex-wrap:wrap;align-items:center;justify-content:center;gap:12px 16px;}',
        '.' + ROW_CLASS + ' > .featured-title{min-width:0;}',
        '.' + PILL_CLASS + '{',
        '  display:inline-flex;align-items:center;gap:6px;flex:none;align-self:center;',
        '  padding:8px 16px;border-radius:999px;text-decoration:none;white-space:nowrap;',
        '  font-size:clamp(12px,1.4vw,14px);font-weight:800;letter-spacing:.01em;line-height:1;',
        '  color:#fff;background:#1d4ed8;border:1px solid #1d4ed8;',
        '  transition:background .15s ease, box-shadow .15s ease, transform .06s ease;',
        '}',
        '.' + PILL_CLASS + ' span{font-size:1.05em;}',
        '.' + PILL_CLASS + ':hover{background:#1e40af;box-shadow:0 6px 18px rgba(29,78,216,.32);}',
        '.' + PILL_CLASS + ':focus-visible{outline:3px solid #93c5fd;outline-offset:3px;background:#1e40af;}',
        '.' + PILL_CLASS + ':active{transform:translateY(1px);}',
        '@media (max-width:600px){',
        '  .' + ROW_CLASS + '{flex-direction:column;gap:12px;}',
        '  .' + PILL_CLASS + '{font-size:12px;padding:7px 13px;}',
        '}',
        '@media (prefers-reduced-motion:reduce){ .' + PILL_CLASS + '{transition:none;} }'
      ].join('');
      document.head.appendChild(style);
    }

    function boot() {
      injectStyle();
      apply();
      var pv = document.getElementById('publicView');
      if (pv && window.MutationObserver) {
        // 공개 섹션은 CSR 렌더 + 빌더 재적용 message 로 다시 그려질 수 있어 관찰이 필요하다.
        new MutationObserver(apply).observe(pv, { childList: true, subtree: true });
      }
    }

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', boot);
    } else {
      boot();
    }
  }
})();
