/**
 * Easy 회계 공용 헬퍼 — /js/api.js(fetchAuth) 다음에 로드한다.
 * 인증 실패(401/403) 처리는 fetchAuth가 담당. 권한은 서버(/api/admin/**)가 최종 결정.
 */
(function () {
  function fmtWon(n) {
    return Number(n || 0).toLocaleString('ko-KR') + '원';
  }

  function fmtMonth(ym) {
    const parts = String(ym || '').split('-');
    if (parts.length !== 2) return String(ym || '');
    return parts[0] + '년 ' + Number(parts[1]) + '월';
  }

  function statusLabel(status) {
    if (status === 'UNPAID') return '미납';
    if (status === 'PARTIAL') return '일부납';
    if (status === 'PAID') return '완납';
    return String(status || '');
  }

  function statusClass(status) {
    if (status === 'UNPAID') return 'unpaid';
    if (status === 'PARTIAL') return 'partial';
    if (status === 'PAID') return 'paid';
    return '';
  }

  /** ADMIN 가드(UX) — 실제 차단은 서버. ADMIN이 아니면 안내 후 로그인으로. */
  async function guardAdmin() {
    let me = null;
    try {
      const cached = sessionStorage.getItem('currentUser');
      if (cached) me = JSON.parse(cached);
    } catch (_) {}
    if (!me || !me.role) {
      try {
        const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
        if (res.ok) {
          me = await res.json();
          try { sessionStorage.setItem('currentUser', JSON.stringify(me)); } catch (_) {}
        }
      } catch (_) {}
    }
    if (!me || me.role !== 'ADMIN') {
      alert('관리자만 이용할 수 있습니다.');
      location.href = '/admin/login';
      throw new Error('not admin');
    }
    const nameEl = document.getElementById('userName');
    if (nameEl) nameEl.textContent = '관리자';
    return me;
  }

  async function loadBusinesses() {
    const res = await fetchAuth('/api/admin/easy/businesses');
    if (!res.ok) throw new Error('사업체 목록을 불러오지 못했습니다.');
    return res.json();
  }

  function showError(id, msg) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = msg;
    el.style.display = 'block';
  }

  function showOk(id, msg) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = msg;
    el.style.display = 'block';
  }

  function hideMsg(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.style.display = 'none';
  }

  window.Easy = { fmtWon, fmtMonth, statusLabel, statusClass, guardAdmin, loadBusinesses, showError, showOk, hideMsg };
})();
