/**
 * 공통 API fetch 헬퍼.
 *
 * - 보호 API 호출에서 401이 떨어지면 sessionStorage를 비우고 /home으로 redirect한다.
 *   (JWT 쿠키가 만료/위변조됐거나 로그아웃된 상태)
 * - 응답 그대로 반환하므로 호출 측에서 res.ok / res.json() 등을 그대로 쓸 수 있다.
 *
 * 사용 예:
 *   const res = await fetchAuth('/api/conversations');
 *   const items = await res.json();
 *
 * 본 파일은 모든 페이지가 다른 JS보다 먼저 로드한다 (templates HTML에서 script 순서 주의).
 */
(function () {
  // 403 처리로 본인 대시보드 redirect를 시작하면 true. 같은 페이지의 후속 fetch가
  // 403을 또 받아도 중복 alert/redirect를 막는다. (window.isAuthRedirecting으로 노출)
  let authRedirecting = false;

  function archiveAwareUrl(url) {
    if (typeof url !== 'string') return url;
    if (location.pathname !== '/archived' && !location.pathname.startsWith('/archived/')) return url;
    if (!url.startsWith('/api/')) return url;
    return '/archived' + url;
  }

  function dashboardPathForRole(role) {
    if (role === 'STUDENT') return '/stdt_dashboard';
    if (role === 'ADMIN') return '/admin/students';
    return '/tutor/academy';
  }

  function userDisplayName(user) {
    if (!user) return '';
    if (user.role === 'ADMIN') return '관리자';
    return user.displayName || user.nickname || user.name || user.loginId || '';
  }

  function storeCurrentUser(user) {
    try {
      sessionStorage.setItem('currentUser', JSON.stringify(user));
    } catch (_) {}
  }

  /**
   * 현재 로그인 사용자 반환. sessionStorage 캐시 우선, 없으면 /api/auth/me로 복원(쿠키 기반).
   * 역할 가드 페이지는 sessionStorage만 보고 튕기지 말고 이걸 await 해서 쿠키 세션도 인정해야 한다.
   * (sessionStorage는 탭 단위라 새 탭/브라우저 재시작 시 비지만 JWT 쿠키는 유효할 수 있다.)
   */
  async function ensureCurrentUser() {
    const cached = sessionStorage.getItem('currentUser');
    if (cached) {
      try { return JSON.parse(cached); } catch (_) { /* 손상 캐시 무시, 재조회 */ }
    }
    try {
      const res = await fetch(archiveAwareUrl('/api/auth/me'), { credentials: 'same-origin' });
      if (!res.ok) return null;
      const user = await res.json();
      storeCurrentUser(user);
      return user;
    } catch (_) {
      return null;
    }
  }

  async function redirectAuthenticatedUser() {
    // 호환용 bootstrap: 쿠키 세션이 유효하면 UI 캐시만 복원한다.
    // #120 이후 /home은 최종 landing이므로 role dashboard로 자동 이동하지 않는다.
    const res = await fetch(archiveAwareUrl('/api/auth/me'), { credentials: 'same-origin' });
    if (!res.ok) return false;
    const user = await res.json();
    storeCurrentUser(user);
    if (typeof window.refreshAccountArea === 'function') {
      window.refreshAccountArea();
    }
    return true;
  }

  async function fetchAuth(url, opts) {
    const requestOpts = Object.assign({}, opts || {});
    const redirectOnAuthError = requestOpts.redirectOnAuthError !== false;
    delete requestOpts.redirectOnAuthError;

    const res = await fetch(archiveAwareUrl(url), requestOpts);
    if (res.status === 401) {
      if (!redirectOnAuthError) return res;
      // 인증 만료/위변조 — 로그인부터 다시
      try { sessionStorage.clear(); } catch (_) {}
      if (location.pathname !== '/home' && location.pathname !== '/') {
        location.href = '/home';
      }
      throw new Error('Unauthorized — redirected to /home');
    }
    if (res.status === 403) {
      if (!redirectOnAuthError) return res;
      // 인증은 됐지만 다른 학생의 자료 접근 시도 — 본인 대시보드로 강제 이동.
      // ADMIN의 임퍼소네이트 호출이 403이 떨어지는 경우는 거의 없지만, 떨어지면 admin 학생 목록으로 보낸다.
      const currentUserRaw = sessionStorage.getItem('currentUser');
      let role = null;
      try { role = currentUserRaw ? JSON.parse(currentUserRaw).role : null; } catch (_) {}
      const fallback = dashboardPathForRole(role);
      // 이미 fallback 경로면 무한 루프 방지 (대시보드 자체 fetch가 403일 때).
      // authRedirecting 플래그로 첫 403만 alert+redirect — 한 페이지에서 여러 fetch가
      // 동시에 403을 받아도 alert가 중복되지 않게 한다.
      if (!authRedirecting && !location.pathname.startsWith(fallback)) {
        authRedirecting = true;
        alert('접근 권한이 없습니다.');
        location.href = fallback;
      }
      throw new Error('Forbidden — redirected to ' + fallback);
    }
    return res;
  }

  /** fetchAuth + json 파싱 + non-2xx 시 throw. 기존 fetchJson 호환용. */
  async function fetchJsonAuth(url, opts) {
    const res = await fetchAuth(url, opts);
    if (!res.ok) throw new Error(`${url} ${res.status}`);
    return res.json();
  }

  window.archiveAwareUrl = archiveAwareUrl;
  window.userDisplayName = userDisplayName;
  window.dashboardPathForRole = dashboardPathForRole;
  window.ensureCurrentUser = ensureCurrentUser;
  window.redirectAuthenticatedUser = redirectAuthenticatedUser;
  window.fetchAuth = fetchAuth;
  window.fetchJsonAuth = fetchJsonAuth;
  window.isAuthRedirecting = () => authRedirecting;
})();
