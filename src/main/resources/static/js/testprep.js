(function () {
  'use strict';
  const TP = window.TP = { user: null };
  const SSO_LOGIN = window.TP_SSO_LOGIN || '/login';

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

  TP.requireLogin = function (then) {
    if (TP.user) { then(); return; }
    TP.openLoginModal();
    window.addEventListener('tp:login', then, { once: true });
  };

  TP.openLoginModal = function () {
    if (document.getElementById('tp-login-modal')) return;
    const next = new URLSearchParams(location.search).get('next');
    const div = document.createElement('div');
    div.id = 'tp-login-modal';
    div.className = 'login-modal';
    const loginHref = SSO_LOGIN + (SSO_LOGIN.includes('?') ? '&' : '?') + 'next=' + encodeURIComponent(location.pathname + location.search);
    div.innerHTML = '<div class="modal-card"><h2>Log in to TestPrep<span class="ko">TestPrep 로그인</span></h2>'
      + '<p class="section lead" style="margin:0">TestPrep uses your existing <b>Feel2Real</b> account — no separate signup. '
      + 'New here? Create an account at Feel2Real first.'
      + '<span class="ko">TestPrep은 기존 <b>Feel2Real</b> 계정을 그대로 사용하므로 따로 가입할 필요가 없습니다. 처음이시라면 Feel2Real에서 먼저 계정을 만드세요.</span></p>'
      + '<p class="modal-msg" data-msg></p>'
      + '<div style="display:flex;gap:10px;margin-top:14px">'
      + '<a class="btn btn-primary" href="' + TP.esc(loginHref) + '">Login with Feel2Real</a>'
      + '<button class="btn btn-outline" type="button" data-close>Cancel</button></div>'
      + '</div>';
    document.body.appendChild(div);
    div.querySelector('[data-close]').addEventListener('click', () => div.remove());
    div.addEventListener('click', e => { if (e.target === div) div.remove(); });
  };

  document.addEventListener('DOMContentLoaded', async () => {
    await TP.loadMe();
    const next = new URLSearchParams(location.search).get('next');
    if (next) {
      if (TP.user) { location.href = next; return; }
      TP.openLoginModal();
    }
  });
})();
