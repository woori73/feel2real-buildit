/* 압구정에듀홀릭 홈페이지 "내신 LIVE" 캠페인 announcement.
 *
 * 한국 학원 광고가 아니라 Shopify 브랜드의 NEW COLLECTION announcement 톤을 목표로 한다(§24).
 * - eduholic.feel2real.co.kr 에서만 노출(다른 Academy subdomain·apex 미노출).
 * - 서버가 popup_enabled && published 로 판정 — 데이터가 없으면 아무것도 mount 하지 않는다(fake 금지, §5).
 * - 모바일 bottom sheet + safe-area + ESC(desktop) + focus trap + body scroll lock.
 */
(function () {
  'use strict';

  var EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  var LANDING_PATH = '/eduholic-midterm-2026.html';
  var DISMISS_KEY = 'eduholic-midterm-2026-dismissed-date';
  var POPUP_API = '/api/academies/eduholic/midterm-campaign/popup';

  if (window.location.hostname !== EDUHOLIC_HOST) return;
  if (window.location.pathname === LANDING_PATH) return;

  // "오늘은 그만 보기" = 한국 달력 기준 하루. toISOString() 은 UTC 라 KST 로는 자정이 아니라
  // 오전 9시가 경계가 돼(전날 밤에 닫으면 다음날 오전까지 안 뜸) 운영자가 "팝업이 안 나온다"고
  // 오해하게 된다. Asia/Seoul 로컬 날짜(YYYY-MM-DD)를 쓴다.
  function seoulDate() {
    try {
      return new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit'
      }).format(new Date());
    } catch (_) {
      // Intl/timeZone 미지원 환경 폴백 — UTC+9 로 근사.
      var kst = new Date(Date.now() + 9 * 60 * 60 * 1000);
      return kst.toISOString().slice(0, 10);
    }
  }

  var today = seoulDate();
  try {
    if (window.localStorage.getItem(DISMISS_KEY) === today) return;
  } catch (_) { /* storage 차단 환경에서도 진행 */ }

  fetch(POPUP_API, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (data) {
      if (!data) return;
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { mount(data); });
      } else {
        mount(data);
      }
    })
    .catch(function () { /* 조용히 무시 — 팝업은 부가 요소 */ });

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function dismissForToday() {
    try { window.localStorage.setItem(DISMISS_KEY, today); } catch (_) {}
  }

  function mount(data) {
    if (document.getElementById('eduholicMidtermPopup')) return;

    injectStyle();
    var lastFocus = document.activeElement;

    var overlay = el('div');
    overlay.id = 'eduholicMidtermPopup';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.setAttribute('aria-label', '2026 2학기 내신 LIVE 안내');

    var card = el('div', 'emp-card');

    var closeX = el('button', 'emp-close', '×');
    closeX.type = 'button';
    closeX.setAttribute('aria-label', '닫기');
    card.appendChild(closeX);

    if (data.visualUrl) {
      var fig = el('div', 'emp-visual');
      var img = document.createElement('img');
      img.src = data.visualUrl;
      img.alt = '';
      img.loading = 'lazy';
      img.decoding = 'async';
      fig.appendChild(img);
      card.appendChild(fig);
    }

    var bodyWrap = el('div', 'emp-body');
    var kicker = el('div', 'emp-kicker');
    kicker.appendChild(el('span', 'emp-dot'));
    kicker.appendChild(document.createTextNode('MIDTERM LIVE'));
    bodyWrap.appendChild(kicker);

    bodyWrap.appendChild(el('h2', null, data.headline || '학교별 중간고사 준비, 지금 진행 중입니다.'));
    bodyWrap.appendChild(el('p', null, data.body
      || '학교 프린트 분석부터 학습자료·변형문제·시험 직전 대비까지. 박정민 원장이 실제 준비 과정을 보여드립니다.'));

    if (data.schoolChips && data.schoolChips.length) {
      var chips = el('div', 'emp-chips');
      data.schoolChips.slice(0, 4).forEach(function (label) {
        chips.appendChild(el('span', 'emp-chip', label));
      });
      bodyWrap.appendChild(chips);
    }

    var cta = el('a', 'emp-cta', '준비 현장 보기 →');
    cta.href = LANDING_PATH;
    bodyWrap.appendChild(cta);

    var later = el('button', 'emp-later', '오늘은 그만 보기');
    later.type = 'button';
    bodyWrap.appendChild(later);

    card.appendChild(bodyWrap);
    overlay.appendChild(card);
    document.body.appendChild(overlay);
    document.body.classList.add('emp-lock');

    closeX.focus();

    function close() {
      overlay.remove();
      document.body.classList.remove('emp-lock');
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
    cta.addEventListener('click', dismissForToday);
    overlay.addEventListener('click', function (e) { if (e.target === overlay) close(); });
    document.addEventListener('keydown', onKey);
  }

  function injectStyle() {
    if (document.getElementById('eduholicMidtermPopupStyle')) return;
    var style = el('style');
    style.id = 'eduholicMidtermPopupStyle';
    style.textContent = [
      'body.emp-lock{overflow:hidden}',
      '#eduholicMidtermPopup{position:fixed;inset:0;z-index:12000;display:flex;align-items:flex-end;justify-content:center;background:rgba(12,15,24,.5);backdrop-filter:blur(4px)}',
      '#eduholicMidtermPopup .emp-card{position:relative;width:min(460px,100%);background:#fff;border-radius:22px 22px 0 0;overflow:hidden;box-shadow:0 -12px 44px rgba(0,0,0,.22);animation:emp-up .28s ease}',
      '@keyframes emp-up{from{transform:translateY(24px);opacity:.6}to{transform:translateY(0);opacity:1}}',
      '#eduholicMidtermPopup .emp-close{position:absolute;right:12px;top:10px;z-index:2;width:34px;height:34px;border:0;border-radius:50%;background:rgba(255,255,255,.85);color:#333;font-size:22px;line-height:1;cursor:pointer}',
      '#eduholicMidtermPopup .emp-visual{aspect-ratio:16/9;background:#eef0f6}',
      '#eduholicMidtermPopup .emp-visual img{width:100%;height:100%;object-fit:cover;display:block}',
      '#eduholicMidtermPopup .emp-body{padding:22px 22px calc(20px + env(safe-area-inset-bottom,0px))}',
      '#eduholicMidtermPopup .emp-kicker{display:flex;align-items:center;gap:7px;font-size:12px;font-weight:800;letter-spacing:.12em;color:#1f2b52;margin-bottom:12px}',
      '#eduholicMidtermPopup .emp-dot{width:7px;height:7px;border-radius:50%;background:#1f7a4d}',
      '#eduholicMidtermPopup h2{margin:0 0 10px;font-size:22px;line-height:1.3;letter-spacing:-.02em;color:#14161a;word-break:keep-all}',
      '#eduholicMidtermPopup p{margin:0 0 16px;font-size:14px;line-height:1.65;color:#55606c;word-break:keep-all}',
      '#eduholicMidtermPopup .emp-chips{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:18px}',
      '#eduholicMidtermPopup .emp-chip{font-size:12px;font-weight:600;padding:5px 10px;border:1px solid #e4e1da;border-radius:999px;color:#14161a}',
      '#eduholicMidtermPopup .emp-cta{display:flex;align-items:center;justify-content:center;min-height:48px;background:#1f2b52;color:#fff;border-radius:12px;text-decoration:none;font-weight:800;font-size:15px}',
      '#eduholicMidtermPopup .emp-later{display:block;width:100%;margin-top:10px;padding:10px;border:0;background:none;color:#98a2b3;font-size:13px;text-decoration:underline;cursor:pointer}',
      '@media(min-width:560px){#eduholicMidtermPopup{align-items:center}#eduholicMidtermPopup .emp-card{border-radius:22px}}',
      '@media(prefers-reduced-motion:reduce){#eduholicMidtermPopup .emp-card{animation:none}}'
    ].join('');
    document.head.appendChild(style);
  }
})();
