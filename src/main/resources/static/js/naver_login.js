/**
 * 공용 로그인 창의 「네이버 로그인」 버튼. 누르면 지금 보던 화면으로 돌아오도록 복귀 주소를 붙여
 * 기존 네이버 로그인 시작점(/auth/naver/start)으로 보낸다. 서버가 복귀 주소를 다시 검사한다.
 * 순수 함수는 Node 테스트에서 module.exports로 쓴다.
 */
(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.F2RNaverLogin = api;
})(typeof self !== 'undefined' ? self : this, function () {
  /** 로그인 뒤 돌아가면 어색하거나 막힌 화면 — 이런 곳에서 누르면 첫 화면으로 돌아간다. */
  var BLOCKED = ['/admin', '/api', '/auth', '/login', '/oauth2', '/account', '/signin', '/logout', '/register', '/error'];

  function isSafeInternal(url) {
    if (!url || typeof url !== 'string') return false;
    if (url.charAt(0) !== '/' || url.indexOf('//') === 0) return false;
    if (url.indexOf('..') >= 0 || url.indexOf('\\') >= 0) return false;
    return !/[\u0000-\u001f\u007f]/.test(url);
  }

  function isBlocked(path) {
    return BLOCKED.some(function (p) { return path === p || path.indexOf(p + '/') === 0; });
  }

  /**
   * 로그인 뒤 돌아갈 주소. 첫 화면이 넘겨 둔 복귀 주소(pending — 「로그인 필요」 안내로 들어온 경우)나
   * 첫 화면의 ?returnUrl=이 있으면 그 주소를 쓰고, 없으면 지금 보던 화면.
   */
  function targetUrl(pathname, search, pending) {
    var path = pathname || '/';
    var params;
    try { params = new URLSearchParams(search || ''); } catch (e) { params = null; }
    var candidate;
    if (pending) {
      candidate = pending;
    } else if (path === '/' && params && params.get('returnUrl')) {
      candidate = params.get('returnUrl');
    } else {
      if (params) { params.delete('login'); params.delete('returnUrl'); }
      var q = params ? params.toString() : '';
      candidate = path + (q ? '?' + q : '');
    }
    if (!isSafeInternal(candidate)) return '/';
    var candidatePath = candidate.split('?')[0];
    return isBlocked(candidatePath) ? '/' : candidate;
  }

  function startHref(target) {
    return '/auth/naver/start?returnUrl=' + encodeURIComponent(target || '/');
  }

  if (typeof document !== 'undefined') {
    document.addEventListener('click', function (event) {
      var btn = event.target.closest && event.target.closest('[data-naver-login]');
      if (!btn) return;
      event.preventDefault();
      location.href = startHref(targetUrl(location.pathname, location.search, window.__f2rLoginReturnUrl));
    });
  }

  return { targetUrl: targetUrl, startHref: startHref, isSafeInternal: isSafeInternal };
});
