/**
 * 새 비밀번호 설정(/account/password-change). 임시 비밀번호로 로그인한 회원이 비밀번호를 바꾸기 전까지 머무는 화면.
 * 순수 함수(validate)는 Node 테스트에서 module.exports로 쓴다.
 */
(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.PasswordChangeRequired = api;
})(typeof self !== 'undefined' ? self : this, function () {
  var MIN = 8;

  /** 입력 확인. 문제가 없으면 null, 있으면 안내 문장. */
  function validate(newPassword, confirmPassword) {
    var pw = String(newPassword == null ? '' : newPassword);
    if (pw.length < MIN) return '새 비밀번호는 ' + MIN + '자 이상이어야 합니다.';
    if (pw !== String(confirmPassword == null ? '' : confirmPassword)) return '새 비밀번호 확인이 일치하지 않습니다.';
    return null;
  }

  function init() {
    var form = document.querySelector('[data-password-change-form]');
    if (!form) return;
    var error = document.querySelector('[data-password-change-error]');
    var account = document.querySelector('[data-password-change-account]');
    var btn = form.querySelector('[type="submit"]');

    function showError(msg) {
      error.textContent = msg || '';
      error.hidden = !msg;
    }
    function goHome(user) {
      var path = (typeof window.dashboardPathForRole === 'function' && user) ? window.dashboardPathForRole(user.role) : '/';
      location.href = path;
    }

    fetch('/api/auth/me', { credentials: 'same-origin' }).then(function (res) {
      if (res.status === 401) { location.href = '/'; return null; }
      return res.ok ? res.json() : null;
    }).then(function (user) {
      if (!user) return;
      if (!user.passwordChangeRequired) { goHome(user); return; }
      account.textContent = '아이디: ' + user.loginId;
      account.hidden = false;
    }).catch(function () {});

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var msg = validate(form.newPassword.value, form.confirmPassword.value);
      if (msg) { showError(msg); return; }
      showError('');
      btn.disabled = true;
      fetch('/api/auth/password-change/temporary', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newPassword: form.newPassword.value, confirmPassword: form.confirmPassword.value })
      }).then(function (res) {
        if (res.ok) return res.json().then(function (user) {
          try { sessionStorage.setItem('currentUser', JSON.stringify(user)); } catch (_) {}
          alert('새 비밀번호로 바꿨습니다. 다음부터는 새 비밀번호로 로그인해 주세요.');
          goHome(user);
        });
        return res.text().then(function (t) {
          var text = t;
          try { var j = JSON.parse(t); text = j.message || t; } catch (_) {}
          showError(text || '바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.');
        });
      }).catch(function () {
        showError('서버 연결에 실패했습니다.');
      }).finally(function () { btn.disabled = false; });
    });

    document.querySelector('[data-password-change-logout]').addEventListener('click', function () {
      fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }).finally(function () {
        try { sessionStorage.removeItem('currentUser'); } catch (_) {}
        location.href = '/';
      });
    });
  }

  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
  }
  return { validate: validate };
});
