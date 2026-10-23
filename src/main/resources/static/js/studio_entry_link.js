/*
 * AI AGENT 제작소 링크 표시 — [data-studio-entry] 링크는 기본 hidden이다.
 * 로그인한 사용자만 서버(/api/studio-access)에 본인 권한을 묻고, ADMIN·운영 계정(modu)일 때만 보인다.
 * 표시 여부는 편의일 뿐이며, 실제 접근은 서버(SecurityConfig·StudioAccessGuard)가 차단한다.
 */
(function () {
  'use strict';

  async function signedIn() {
    try {
      const cached = JSON.parse(sessionStorage.getItem('currentUser') || 'null');
      if (cached && cached.role) return true;
    } catch (_) { /* 캐시가 깨졌으면 서버에 다시 묻는다 */ }
    try {
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      return res.ok;
    } catch (_) {
      return false;
    }
  }

  async function reveal() {
    const links = document.querySelectorAll('[data-studio-entry]');
    if (!links.length || !(await signedIn())) return;
    try {
      const res = await fetch('/api/studio-access', { credentials: 'same-origin' });
      if (!res.ok) return;
      const body = await res.json();
      if (body && body.allowed === true) links.forEach((el) => { el.hidden = false; });
    } catch (_) { /* 확인 실패 시 숨김 유지 */ }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', reveal);
  else reveal();
})();
