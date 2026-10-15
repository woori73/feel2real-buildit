(function () {
  'use strict';
  const TP = window.TP = { user: null };

  TP.esc = function (v) {
    return String(v == null ? '' : v).replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));
  };

  TP.api = async function (url, opts) {
    opts = opts || {};
    const res = await fetch(url, Object.assign({
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' }
    }, opts));
    if (res.status === 401 && url !== '/api/auth/me') { TP.openLoginModal(); throw new Error('login required'); }
    if (!res.ok) {
      let msg = res.statusText;
      try { const j = await res.json(); msg = j.message || msg; } catch (e) { /* ignore */ }
      throw new Error(msg);
    }
    if (res.status === 204) return null;
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  };

  TP.loadMe = async function () {
    try {
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      TP.user = res.ok ? await res.json() : null;
    } catch (e) { TP.user = null; }
    TP.renderAuth();
    return TP.user;
  };

  TP.renderAuth = function () {
    const slot = document.getElementById('tp-auth');
    if (!slot) return;
    if (TP.user) {
      slot.innerHTML = '<span class="who">' + TP.esc(TP.user.loginId) + '</span>'
        + '<a class="btn btn-ghost btn-sm" href="/testprep/scores">My Scores</a>'
        + '<button class="btn btn-ghost btn-sm" type="button" data-tp-logout>Logout</button>';
      slot.querySelector('[data-tp-logout]').addEventListener('click', async () => {
        // feel2real과 세션을 공유 — TestPrep 로그아웃은 Feel2Real에서도 로그아웃된다.
        if (!confirm('TestPrep uses your Feel2Real account. Logging out here signs you out of Feel2Real too. Continue?\n\nTestPrep은 Feel2Real 계정을 사용합니다. 여기서 로그아웃하면 Feel2Real에서도 로그아웃됩니다. 계속할까요?')) return;
        await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
        location.href = '/home';
      });
    } else {
      slot.innerHTML = '<button class="btn btn-ghost btn-sm" type="button" data-tp-login>Login</button>';
      slot.querySelector('[data-tp-login]').addEventListener('click', () => TP.openLoginModal());
    }
  };

  // 비동기 작업을 호출 순서대로 하나씩 실행하고, 호출자는 "앞선 작업까지 모두 끝난 뒤"에 resolve되는 Promise를 받는다.
  // 답안 자동저장(PUT)이 진행 중일 때 제출(POST)이 먼저 서버에 닿아 같은 (응시, 문항) 행을 동시에 INSERT하는 경쟁을 막는다.
  TP.serialize = function (fn) {
    let tail = Promise.resolve();
    return function () {
      const args = arguments;
      tail = tail.then(() => fn.apply(null, args), () => fn.apply(null, args));
      return tail;
    };
  };

  TP.requireLogin = function (then) {
    if (TP.user) { then(); return; }
    TP.openLoginModal();
    window.addEventListener('tp:login', then, { once: true });
  };

  // 로그인 후 돌아올 곳은 /testprep 내부 경로만 허용한다(open redirect 방지, 서버 핸들러도 내부 경로만 허용).
  TP.safeReturn = function (url) {
    if (typeof url !== 'string' || !/^\/testprep(\/|\?|$)/.test(url)) return null;
    if (/[\\\x00-\x1f\x7f]|\.\./.test(url)) return null;
    return url;
  };

  // target: 로그인 후 이용하려던 화면(미지정 시 현재 화면). 기존 네이버 로그인(/oauth2/authorization/naver)을 재사용한다.
  TP.openLoginModal = function (target) {
    if (document.getElementById('tp-login-modal')) return;
    const here = location.pathname + location.search.replace(/[?&](login|returnUrl)=[^&]*/g, '').replace(/^&/, '?');
    const back = TP.safeReturn(target) || TP.safeReturn(here) || '/testprep';
    const div = document.createElement('div');
    div.id = 'tp-login-modal';
    div.className = 'login-modal';
    div.innerHTML = '<div class="modal-card"><h2>TestPrep<span class="ko">로그인 필요</span></h2>'
      + '<p class="section lead" style="margin:0">TESTPREP 서비스를 이용하려면 로그인이 필요합니다.'
      + '<span class="ko">Log in to take exams, submit answers, and see your scores and review.</span></p>'
      + '<p class="modal-msg" data-msg></p>'
      + '<div style="display:flex;gap:10px;margin-top:14px;flex-wrap:wrap">'
      + '<a class="btn btn-primary" data-tp-naver href="/oauth2/authorization/naver">네이버 로그인하고 시작하기</a>'
      + '<button class="btn btn-outline" type="button" data-close>Cancel</button></div>'
      + '</div>';
    document.body.appendChild(div);
    div.querySelector('[data-tp-naver]').addEventListener('click', () => {
      // NaverOAuth2LoginSuccessHandler가 이 쿠키를 읽어 로그인 후 원래 화면으로 복귀시킨다(5분, 내부 경로만).
      const secure = location.protocol === 'https:' ? '; Secure' : '';
      document.cookie = 'oauth_return_url=' + encodeURIComponent(back) + '; Path=/; Max-Age=300; SameSite=Lax' + secure;
    });
    div.querySelector('[data-close]').addEventListener('click', () => div.remove());
    div.addEventListener('click', e => { if (e.target === div) div.remove(); });
  };

  // 비회원이 보호 화면(시험·성적·오답·학습) 링크를 누르면 이동하지 않고 로그인 안내를 띄운다.
  // 화면 숨김은 UX일 뿐이며, 실제 차단은 서버(SecurityConfig /testprep/**, /api/**)가 한다.
  TP.interceptProtectedLinks = function () {
    document.addEventListener('click', e => {
      const a = e.target.closest && e.target.closest('a[href^="/testprep/"]');
      if (!a || TP.user) return;
      e.preventDefault();
      TP.openLoginModal(a.getAttribute('href'));
    });
  };

  document.addEventListener('DOMContentLoaded', async () => {
    TP.interceptProtectedLinks();
    await TP.loadMe();
    const params = new URLSearchParams(location.search);
    const returnUrl = TP.safeReturn(params.get('returnUrl'));
    if (params.get('login') === 'required' && !TP.user) TP.openLoginModal(returnUrl);
    else if (TP.user && returnUrl) location.href = returnUrl;
  });
})();
