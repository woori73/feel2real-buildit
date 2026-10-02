(function () {
  'use strict';
  var loginForm = document.getElementById('ns-login-form');
  var signupForm = document.getElementById('ns-signup-form');
  var msg = document.getElementById('ns-auth-msg');
  var tabs = document.querySelectorAll('[data-ns-tab]');
  if (!loginForm || !signupForm || !msg) return;

  // 로그인 전에 누른 NewStart 기능으로 복귀. 같은 사이트의 /newstart 경로만 허용(open redirect 방지).
  function nextTarget() {
    var raw = new URLSearchParams(window.location.search).get('next') || '';
    var ok = /^\/newstart(?:[/?#][^\\\s]*)?$/.test(raw) && raw.indexOf('//') < 0
      && !/^\/newstart\/login(?:[/?#]|$)/.test(raw);
    return ok ? raw : '/newstart';
  }

  function show(text, isError) {
    msg.textContent = text;
    msg.hidden = !text;
    msg.setAttribute('data-error', isError ? 'true' : 'false');
  }

  function selectTab(name) {
    var signup = name === 'signup';
    loginForm.hidden = signup;
    signupForm.hidden = !signup;
    tabs.forEach(function (b) { b.setAttribute('aria-selected', b.getAttribute('data-ns-tab') === name ? 'true' : 'false'); });
    show('', false);
  }
  if (new URLSearchParams(window.location.search).has('next')) {
    show('로그인이 필요한 서비스입니다. 로그인하면 보시던 화면으로 돌아갑니다.', false);
  }
  tabs.forEach(function (b) { b.addEventListener('click', function () { selectTab(b.getAttribute('data-ns-tab')); }); });

  function post(url, body) {
    return fetch(url, {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) { return { ok: res.ok, status: res.status, data: data }; });
    });
  }

  loginForm.addEventListener('submit', function (e) {
    e.preventDefault();
    show('로그인 중...', false);
    post('/api/auth/login', {
      loginId: document.getElementById('ns-login-id').value.trim().toLowerCase(),
      password: document.getElementById('ns-login-pw').value
    }).then(function (r) {
      if (r.ok) { window.location.href = nextTarget(); return; }
      show('아이디 또는 비밀번호가 올바르지 않습니다.', true);
    }).catch(function () { show('네트워크 오류입니다. 잠시 후 다시 시도해 주세요.', true); });
  });

  signupForm.addEventListener('submit', function (e) {
    e.preventDefault();
    show('가입 중...', false);
    post('/api/newstart/auth/signup', {
      loginId: document.getElementById('ns-signup-id').value,
      cafeNickname: document.getElementById('ns-signup-nick').value,
      password: document.getElementById('ns-signup-pw').value
    }).then(function (r) {
      if (r.ok && r.data && r.data.ok) {
        show(r.data.message || '가입되었습니다.', false);
        setTimeout(function () { window.location.href = nextTarget(); }, 1200);
        return;
      }
      show((r.data && r.data.message) || '가입하지 못했습니다. 입력값을 확인해 주세요.', true);
    }).catch(function () { show('네트워크 오류입니다. 잠시 후 다시 시도해 주세요.', true); });
  });
})();
