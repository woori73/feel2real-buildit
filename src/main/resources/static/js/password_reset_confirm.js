(function initPasswordResetConfirm() {
  var form = document.querySelector('[data-password-reset-confirm-form]');
  if (!form) return;

  var params = new URLSearchParams(location.search);
  var token = params.get('token') || '';
  var error = document.querySelector('[data-password-reset-error]');
  var success = document.querySelector('[data-password-reset-success]');
  form.token.value = token;

  function setError(message) {
    error.textContent = message || '';
    error.hidden = !message;
    form.newPassword.classList.toggle('is-invalid', !!message);
    form.newPasswordConfirm.classList.toggle('is-invalid', !!message);
    if (success) success.hidden = true;
  }

  if (!token) {
    setError('비밀번호 재설정 링크가 올바르지 않습니다.');
  }

  form.addEventListener('submit', async function(event) {
    event.preventDefault();
    var newPassword = form.newPassword.value;
    var confirm = form.newPasswordConfirm.value;
    if (!token) {
      setError('비밀번호 재설정 링크가 올바르지 않습니다.');
      return;
    }
    if (!newPassword || newPassword.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    if (newPassword !== confirm) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }

    var button = form.querySelector('[type="submit"]');
    setError('');
    if (button) button.disabled = true;
    try {
      var res = await fetch('/api/auth/password-reset/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ token: token, newPassword: newPassword })
      });
      if (!res.ok) {
        setError(await res.text() || '비밀번호 변경에 실패했습니다.');
        return;
      }
      form.reset();
      if (success) success.hidden = false;
    } catch (_) {
      setError('서버 연결에 실패했습니다.');
    } finally {
      if (button) button.disabled = false;
    }
  });
})();
