/**
 * NewStart 페이지 전용: 공통 로그인 모달의 '간편 로그인' 자리(.login-modal-social)에
 * 실제 "네이버로 로그인" 버튼을 주입한다. 다른 페이지에는 이 스크립트를 로드하지 않는다.
 * 기존 ID/PW 로그인 폼은 그대로 유지된다.
 */
(function initNaverLogin() {
  if (window.__f2rNaverLoginInitialized) return;
  window.__f2rNaverLoginInitialized = true;

  function enhance() {
    var list = document.querySelector('.login-modal-social-list');
    if (!list || list.getAttribute('data-naver-enhanced')) return;
    list.setAttribute('data-naver-enhanced', 'true');

    var divider = document.querySelector('.login-modal-social .login-modal-divider span');
    if (divider) divider.textContent = '간편 로그인';
    var wrap = document.querySelector('.login-modal-social');
    if (wrap) wrap.removeAttribute('aria-label');
    list.removeAttribute('aria-hidden');

    var link = document.createElement('a');
    link.className = 'login-modal-naver';
    var returnUrl = typeof window.safeInternalReturnUrl === 'function'
      ? window.safeInternalReturnUrl(location.pathname + location.search + location.hash)
      : (location.pathname || '/newstart');
    link.href = '/oauth/naver/start?returnUrl=' + encodeURIComponent(returnUrl);
    // document-level modal handler와 독립적으로 OAuth start로 이동한다.
    link.addEventListener('click', function (event) {
      event.preventDefault();
      window.location.assign(link.href);
    });
    var mark = document.createElement('span');
    mark.className = 'login-modal-naver-mark';
    mark.setAttribute('aria-hidden', 'true');
    mark.textContent = 'N';
    var label = document.createElement('span');
    label.textContent = '네이버로 로그인';
    link.appendChild(mark);
    link.appendChild(label);
    list.replaceChildren(link);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', enhance);
  } else {
    enhance();
  }
})();
