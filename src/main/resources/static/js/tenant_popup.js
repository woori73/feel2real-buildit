/* BuildIt 2단계 — 테넌트 캠페인 팝업.
 *
 * eduholic_midterm_popup.js 의 테넌트 일반화판. 동작(모바일 bottom sheet,
 * safe-area, ESC, focus trap, body scroll lock, "오늘은 그만 보기" KST 날짜)은 동일하고,
 * 하드코딩된 호스트·API·문구만 학원별 설정(builderDraft['tenant.campaignPopup'])에서 읽는다.
 *
 * 설정 스키마 (JSON):
 *   { "enabled": true,
 *     "landingPath": "/campaign.html",
 *     "dismissKey": "acme-campaign-dismissed-date",
 *     "kicker": "MIDTERM LIVE",
 *     "headline": "...", "body": "...",
 *     "visualUrl": "https://...",
 *     "schoolChips": ["A중", "B중"],
 *     "ctaLabel": "준비 현장 보기 →",
 *     "laterLabel": "오늘은 그만 보기",
 *     "ariaLabel": "캠페인 안내" }
 * enabled !== true 이면 아무 것도 하지 않는다.
 */
(function () {
  'use strict';

  // eduholic 호스트는 기존 eduholic_midterm_popup.js 가 담당한다.
  if (window.location.hostname === 'eduholic.feel2real.co.kr') return;
  if (!window.F2RTenantSettings) return;

  window.F2RTenantSettings.get('tenant.campaignPopup').then(function (cfg) {
    if (!cfg) return;
    start(cfg);
  }).catch(function () {});

  function start(cfg) {
    var LANDING_PATH = cfg.landingPath || '';
    var DISMISS_KEY = cfg.dismissKey || 'tenant-campaign-dismissed-date';
    if (LANDING_PATH && window.location.pathname === LANDING_PATH) return;

    function seoulDate() {
      try {
        return new Intl.DateTimeFormat('en-CA', {
          timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit'
        }).format(new Date());
      } catch (_) {
        var kst = new Date(Date.now() + 9 * 60 * 60 * 1000);
        return kst.toISOString().slice(0, 10);
      }
    }

    var today = seoulDate();
    try {
      if (window.localStorage.getItem(DISMISS_KEY) === today) return;
    } catch (_) {}

    var data = {
      visualUrl: cfg.visualUrl || '',
      headline: cfg.headline || '',
      body: cfg.body || '',
      schoolChips: Array.isArray(cfg.schoolChips) ? cfg.schoolChips : []
    };
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function () { mount(data, cfg, today, DISMISS_KEY, LANDING_PATH); });
    } else {
      mount(data, cfg, today, DISMISS_KEY, LANDING_PATH);
    }
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function mount(data, cfg, today, dismissKey, landingPath) {
    if (document.getElementById('tenantCampaignPopup')) return;

    injectStyle();
    var lastFocus = document.activeElement;

    var overlay = el('div');
    overlay.id = 'tenantCampaignPopup';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.setAttribute('aria-label', cfg.ariaLabel || '캠페인 안내');

    var card = el('div', 'tcp-card');

    var closeX = el('button', 'tcp-close', '×');
    closeX.type = 'button';
    closeX.setAttribute('aria-label', '닫기');
    card.appendChild(closeX);

    if (data.visualUrl) {
      var fig = el('div', 'tcp-visual');
      var img = document.createElement('img');
      img.src = data.visualUrl;
      img.alt = '';
      img.loading = 'lazy';
      img.decoding = 'async';
      fig.appendChild(img);
      card.appendChild(fig);
    }

    var bodyWrap = el('div', 'tcp-body');
    var kicker = el('div', 'tcp-kicker');
    kicker.appendChild(el('span', 'tcp-dot'));
    kicker.appendChild(document.createTextNode(cfg.kicker || 'CAMPAIGN'));
    bodyWrap.appendChild(kicker);

    if (data.headline) bodyWrap.appendChild(el('h2', null, data.headline));
    if (data.body) bodyWrap.appendChild(el('p', null, data.body));

    if (data.schoolChips && data.schoolChips.length) {
      var chips = el('div', 'tcp-chips');
      data.schoolChips.slice(0, 4).forEach(function (label) {
        chips.appendChild(el('span', 'tcp-chip', label));
      });
      bodyWrap.appendChild(chips);
    }

    if (landingPath) {
      var cta = el('a', 'tcp-cta', cfg.ctaLabel || '자세히 보기 →');
      cta.href = landingPath;
      cta.addEventListener('click', function () { dismissForToday(); });
      bodyWrap.appendChild(cta);
    }

    var later = el('button', 'tcp-later', cfg.laterLabel || '오늘은 그만 보기');
    later.type = 'button';
    bodyWrap.appendChild(later);

    card.appendChild(bodyWrap);
    overlay.appendChild(card);
    document.body.appendChild(overlay);
    document.body.classList.add('tcp-lock');

    closeX.focus();

    function dismissForToday() {
      try { window.localStorage.setItem(dismissKey, today); } catch (_) {}
    }
    function close() {
      overlay.remove();
      document.body.classList.remove('tcp-lock');
      document.removeEventListener('keydown', onKey);
      if (lastFocus && lastFocus.focus) lastFocus.focus();
    }
    function closeToday() { dismissForToday(); close(); }
    function onKey(e) {
      if (e.key === 'Escape') { close(); return; }
      if (e.key === 'Tab') {
        var f = card.querySelectorAll('button, a[href]');
        if (!f.length) return;
        var first = f[0], last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    }

    closeX.addEventListener('click', close);
    later.addEventListener('click', closeToday);
    overlay.addEventListener('click', function (e) { if (e.target === overlay) close(); });
    document.addEventListener('keydown', onKey);
  }

  function injectStyle() {
    if (document.getElementById('tenantCampaignPopupStyle')) return;
    var style = el('style');
    style.id = 'tenantCampaignPopupStyle';
    style.textContent = [
      'body.tcp-lock{overflow:hidden}',
      '#tenantCampaignPopup{position:fixed;inset:0;z-index:12000;display:flex;align-items:flex-end;justify-content:center;background:rgba(12,15,24,.5);backdrop-filter:blur(4px)}',
      '#tenantCampaignPopup .tcp-card{position:relative;width:min(460px,100%);background:#fff;border-radius:22px 22px 0 0;overflow:hidden;box-shadow:0 -12px 44px rgba(0,0,0,.22);animation:tcp-up .28s ease}',
      '@keyframes tcp-up{from{transform:translateY(24px);opacity:.6}to{transform:translateY(0);opacity:1}}',
      '#tenantCampaignPopup .tcp-close{position:absolute;right:12px;top:10px;z-index:2;width:34px;height:34px;border:0;border-radius:50%;background:rgba(255,255,255,.85);color:#333;font-size:22px;line-height:1;cursor:pointer}',
      '#tenantCampaignPopup .tcp-visual{aspect-ratio:16/9;background:#eef0f6}',
      '#tenantCampaignPopup .tcp-visual img{width:100%;height:100%;object-fit:cover;display:block}',
      '#tenantCampaignPopup .tcp-body{padding:22px 22px calc(20px + env(safe-area-inset-bottom,0px))}',
      '#tenantCampaignPopup .tcp-kicker{display:flex;align-items:center;gap:7px;font-size:12px;font-weight:800;letter-spacing:.12em;color:#1f2b52;margin-bottom:12px}',
      '#tenantCampaignPopup .tcp-dot{width:7px;height:7px;border-radius:50%;background:#1f7a4d}',
      '#tenantCampaignPopup h2{margin:0 0 10px;font-size:22px;line-height:1.3;letter-spacing:-.02em;color:#14161a;word-break:keep-all}',
      '#tenantCampaignPopup p{margin:0 0 16px;font-size:14px;line-height:1.65;color:#55606c;word-break:keep-all}',
      '#tenantCampaignPopup .tcp-chips{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:18px}',
      '#tenantCampaignPopup .tcp-chip{font-size:12px;font-weight:600;padding:5px 10px;border:1px solid #e4e1da;border-radius:999px;color:#14161a}',
      '#tenantCampaignPopup .tcp-cta{display:flex;align-items:center;justify-content:center;min-height:48px;background:#1f2b52;color:#fff;border-radius:12px;text-decoration:none;font-weight:800;font-size:15px}',
      '#tenantCampaignPopup .tcp-later{display:block;width:100%;margin-top:10px;padding:10px;border:0;background:none;color:#98a2b3;font-size:13px;text-decoration:underline;cursor:pointer}',
      '@media(min-width:560px){#tenantCampaignPopup{align-items:center}#tenantCampaignPopup .tcp-card{border-radius:22px}}',
      '@media(prefers-reduced-motion:reduce){#tenantCampaignPopup .tcp-card{animation:none}}'
    ].join('');
    document.head.appendChild(style);
  }
})();
