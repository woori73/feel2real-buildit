(function () {
  'use strict';

  const EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  const LANDING_PATH = '/eduholic-midterm-2026.html';
  const DISMISS_KEY = 'eduholic-midterm-2026-dismissed-date';

  if (window.location.hostname !== EDUHOLIC_HOST) return;
  if (window.location.pathname === LANDING_PATH) return;

  const today = new Date().toISOString().slice(0, 10);
  try {
    if (window.localStorage.getItem(DISMISS_KEY) === today) return;
  } catch (_) {
    // Storage 차단 환경에서도 팝업은 정상 노출한다.
  }

  function dismissForToday() {
    try { window.localStorage.setItem(DISMISS_KEY, today); } catch (_) {}
    const overlay = document.getElementById('eduholicMidtermPopup');
    if (overlay) overlay.remove();
  }

  function mount() {
    if (document.getElementById('eduholicMidtermPopup')) return;
    const overlay = document.createElement('div');
    overlay.id = 'eduholicMidtermPopup';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.setAttribute('aria-label', '2026 2학기 중간고사 내신대비 안내');
    overlay.innerHTML = `
      <div class="eduholic-midterm-card">
        <button class="eduholic-midterm-close" type="button" aria-label="닫기">×</button>
        <div class="eduholic-midterm-kicker">NOW · 2026 2학기 중간고사</div>
        <h2>학교별 내신대비<br>지금 진행 중입니다.</h2>
        <p>학교 프린트와 교과서 분석부터 학습자료·변형문제·시험 직전 교재까지, 압구정에듀홀릭의 실제 내신 준비 과정을 공개합니다.</p>
        <a class="eduholic-midterm-cta" href="${LANDING_PATH}">내신대비 현장 보기 →</a>
        <button class="eduholic-midterm-today" type="button">오늘은 그만 보기</button>
      </div>`;
    const style = document.createElement('style');
    style.textContent = `
      #eduholicMidtermPopup{position:fixed;inset:0;z-index:12000;display:flex;align-items:center;justify-content:center;padding:20px;background:rgba(10,15,30,.48);backdrop-filter:blur(5px)}
      .eduholic-midterm-card{position:relative;width:min(440px,100%);padding:34px 30px 24px;border-radius:24px;background:#fff;box-shadow:0 24px 80px rgba(0,0,0,.24);text-align:left}
      .eduholic-midterm-kicker{font-size:12px;font-weight:800;letter-spacing:.12em;color:#3157d5;margin-bottom:12px}
      .eduholic-midterm-card h2{margin:0 0 14px;font-size:30px;line-height:1.18;letter-spacing:-.04em;color:#101828}
      .eduholic-midterm-card p{margin:0 0 22px;font-size:15px;line-height:1.7;color:#475467;word-break:keep-all}
      .eduholic-midterm-cta{display:block;padding:14px 18px;border-radius:12px;background:#172b4d;color:#fff;text-align:center;text-decoration:none;font-size:15px;font-weight:800}
      .eduholic-midterm-close{position:absolute;right:18px;top:16px;border:0;background:transparent;color:#667085;font-size:28px;line-height:1;cursor:pointer}
      .eduholic-midterm-today{display:block;margin:15px auto 0;border:0;background:transparent;color:#98a2b3;font-size:13px;text-decoration:underline;cursor:pointer}
      @media(max-width:520px){#eduholicMidtermPopup{align-items:flex-end;padding:12px}.eduholic-midterm-card{border-radius:22px;padding:30px 22px 22px}.eduholic-midterm-card h2{font-size:27px}}
    `;
    document.head.appendChild(style);
    document.body.appendChild(overlay);
    overlay.querySelector('.eduholic-midterm-close').addEventListener('click', () => overlay.remove());
    overlay.querySelector('.eduholic-midterm-today').addEventListener('click', dismissForToday);
    overlay.addEventListener('click', (event) => { if (event.target === overlay) overlay.remove(); });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount);
  else mount();
})();
