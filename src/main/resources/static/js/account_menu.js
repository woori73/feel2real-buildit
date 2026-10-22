(function initAccountMenu() {
  if (window.__f2rAccountMenuInitialized) return;
  window.__f2rAccountMenuInitialized = true;

  function currentReturnUrl() {
    return window.safeInternalReturnUrl(location.pathname + location.search + location.hash);
  }

  function signupUrl() {
    return '/register?returnUrl=' + encodeURIComponent(currentReturnUrl());
  }

  function roleHome(user) {
    if (!user || !user.role) return null;
    if (user.role === 'ADMIN') return { href: '/admin/students', label: '어드민 홈' };
    if (user.role === 'TUTOR') return { href: '/tutor/academy', label: '강사 홈' };
    if (user.role === 'STUDENT') return { href: '/stdt_dashboard', label: '학생 홈' };
    return null;
  }

  function readCachedUser() {
    try { return JSON.parse(sessionStorage.getItem('currentUser') || 'null'); } catch (_) { return null; }
  }

  function storeCurrentUser(user) {
    try { sessionStorage.setItem('currentUser', JSON.stringify(user)); } catch (_) {}
  }

  async function loadCurrentUser() {
    var cached = readCachedUser();
    if (cached) return cached;
    try {
      var res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (!res.ok) return null;
      var user = await res.json();
      storeCurrentUser(user);
      return user;
    } catch (_) {
      return null;
    }
  }

  function closeAll(except) {
    document.querySelectorAll('[data-account-menu]').forEach(function(menu) {
      if (menu === except) return;
      var trigger = menu.querySelector('[data-account-menu-trigger]');
      var panel = menu.querySelector('[data-account-menu-panel]');
      if (trigger) trigger.setAttribute('aria-expanded', 'false');
      if (panel) panel.hidden = true;
    });
  }

  function hydrateRoleHome(menu, user) {
    var link = menu.querySelector('[data-account-home]');
    if (!link) return;
    var target = roleHome(user || readCachedUser());
    if (!target) return;
    link.href = target.href;
    link.textContent = target.label;
  }

  function setSignupLinks() {
    document.querySelectorAll('[data-auth-signup]').forEach(function(link) {
      link.setAttribute('href', signupUrl());
    });
  }

  function setAuthState(user) {
    document.querySelectorAll('[data-auth-actions]').forEach(function(actions) {
      actions.hidden = !!user;
    });
    document.querySelectorAll('[data-account-menu]').forEach(function(menu) {
      menu.hidden = !user;
      if (user) hydrateRoleHome(menu, user);
    });
    setSignupLinks();
    showAssistantWelcome(user);
  }

  var assistantWelcomeUser=null;
  async function showAssistantWelcome(user) {
    var previous=document.getElementById('assistantWelcome');
    if (!user || !['ADMIN','TUTOR'].includes(user.role)) { if(previous)previous.remove(); assistantWelcomeUser=null; return; }
    if (location.pathname==='/dashboard' || assistantWelcomeUser===user.userId) return;
    assistantWelcomeUser=user.userId;
    try {
      var response=await fetch('/api/dashboard/assistant/v1/progress',{credentials:'same-origin',cache:'no-store'});
      if(!response.ok)return; var progress=await response.json(); if(progress.completed)return;
      var banner=document.createElement('aside'); banner.id='assistantWelcome';banner.setAttribute('aria-label','AI 비서 환영 안내');
      banner.style.cssText='position:fixed;bottom:16px;left:16px;width:min(340px,calc(100% - 32px));padding:18px;background:#fff;border:1px solid #b8d2c7;border-radius:12px;box-shadow:0 8px 30px #183a3026;z-index:90;color:#173c32;font:14px/1.7 system-ui;box-sizing:border-box';
      var text=document.createElement('p');text.textContent='반가워요. AI 업무 비서가 대시보드 사용법을 차근차근 안내해 드립니다.';
      var link=document.createElement('a');link.href='/dashboard';link.textContent=progress.step>0?'이어서 안내받기 →':'대시보드 안내 시작 →';link.style.cssText='display:inline-block;min-height:44px;color:#125f4d;font-weight:700';
      var close=document.createElement('button');close.type='button';close.textContent='나중에';close.style.cssText='margin-left:12px;min-height:44px;border:0;background:transparent;color:#536d62';close.onclick=function(){banner.remove();};
      banner.append(text,link,close);document.body.appendChild(banner);
    } catch (_) { /* Service navigation remains available if the guide is unavailable. */ }
  }

  function prepareLoginModals() {
    document.querySelectorAll('[data-login-modal]').forEach(function(node) {
      if (node.parentElement !== document.body) {
        document.body.appendChild(node);
      }
    });
  }

  async function refreshAccountArea() {
    prepareLoginModals();
    var user = await loadCurrentUser();
    setAuthState(user);
    return user;
  }

  function modal() {
    return document.querySelector('[data-login-modal]');
  }

  function setLoginMessage(text, isError) {
    var box = document.querySelector('[data-login-modal-message]');
    if (!box) return;
    box.textContent = text || '';
    box.hidden = !text;
    box.classList.toggle('is-error', !!isError);
  }

  function openLoginModal() {
    prepareLoginModals();
    var node = modal();
    if (!node) return false;
    setLoginMessage('', false);
    node.hidden = false;
    node.setAttribute('aria-hidden', 'false');
    setSignupLinks();
    var input = node.querySelector('[name="loginId"]');
    if (input) input.focus();
    return true;
  }

  function closeLoginModal() {
    var node = modal();
    if (!node) return;
    node.hidden = true;
    node.setAttribute('aria-hidden', 'true');
  }

  async function submitLogin(form) {
    var btn = form.querySelector('[type="submit"]');
    var loginId = form.loginId.value.trim();
    var password = form.password.value;
    if (!loginId || !password) {
      setLoginMessage('아이디와 비밀번호를 입력해 주세요.', true);
      return;
    }
    if (btn) btn.disabled = true;
    try {
      var res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ loginId: loginId, password: password })
      });
      if (!res.ok) {
        setLoginMessage(await res.text() || '로그인에 실패했습니다.', true);
        return;
      }
      var user = await res.json();
      storeCurrentUser(user);
      setAuthState(user);
      closeLoginModal();
      window.dispatchEvent(new CustomEvent('f2r:login', { detail: user }));
    } catch (_) {
      setLoginMessage('서버 연결에 실패했습니다.', true);
    } finally {
      if (btn) btn.disabled = false;
    }
  }

  document.addEventListener('click', function(event) {
    var loginTrigger = event.target.closest('[data-auth-login]');
    if (loginTrigger) {
      event.preventDefault();
      openLoginModal();
      return;
    }

    var closeTrigger = event.target.closest('[data-login-modal-close]');
    if (closeTrigger) {
      event.preventDefault();
      closeLoginModal();
      return;
    }

    var modalBackdrop = event.target.closest('[data-login-modal]');
    if (modalBackdrop && event.target === modalBackdrop) {
      closeLoginModal();
      return;
    }

    var trigger = event.target.closest('[data-account-menu-trigger]');
    if (trigger) {
      var menu = trigger.closest('[data-account-menu]');
      var panel = menu && menu.querySelector('[data-account-menu-panel]');
      if (!menu || !panel) return;
      hydrateRoleHome(menu);
      var nextOpen = panel.hidden;
      closeAll(menu);
      panel.hidden = !nextOpen;
      trigger.setAttribute('aria-expanded', String(nextOpen));
      event.preventDefault();
      return;
    }

    var logout = event.target.closest('[data-account-logout]');
    if (logout) {
      event.preventDefault();
      fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }).catch(function() {}).finally(function() {
        sessionStorage.removeItem('currentUser');
        sessionStorage.removeItem('currentStudent');
        sessionStorage.removeItem('impersonatedStudent');
        location.href = '/';
      });
      return;
    }

    if (!event.target.closest('[data-account-menu]')) closeAll(null);
  });

  document.addEventListener('submit', function(event) {
    var form = event.target.closest('[data-login-modal-form]');
    if (!form) return;
    event.preventDefault();
    submitLogin(form);
  });

  document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape') {
      closeAll(null);
      closeLoginModal();
    }
  });

  window.openLoginModal = openLoginModal;
  window.closeLoginModal = closeLoginModal;
  window.refreshAccountArea = refreshAccountArea;
  window.f2rRoleHome = roleHome;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', refreshAccountArea);
  } else {
    refreshAccountArea();
  }
})();
