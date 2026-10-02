(function initPasswordResetRequest() {
  var resetForm = document.querySelector('[data-password-reset-request-form]');
  var findForm = document.querySelector('[data-login-id-find-form]');
  if (!resetForm && !findForm) return;

  var resetLoginIdInput = resetForm ? resetForm.loginId : null;
  var resetEmailInput = resetForm ? resetForm.email : null;
  var resetError = document.querySelector('[data-password-reset-error]');
  var findEmailInput = findForm ? findForm.email : null;
  var findError = document.querySelector('[data-login-id-find-error]');
  var findResult = document.querySelector('[data-login-id-find-result]');
  var modal = document.querySelector('[data-password-reset-result]');
  var modalEmail = document.querySelector('[data-password-reset-result-email]');
  var resetLink = document.querySelector('[data-password-reset-link]');
  var emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  function setError(target, inputs, message) {
    if (target) {
      target.textContent = message || '';
      target.hidden = !message;
    }
    inputs.forEach(function(input) {
      if (input) input.classList.toggle('is-invalid', !!message);
    });
  }

  function openResult(email, url) {
    if (modalEmail) modalEmail.textContent = email;
    if (resetLink) {
      resetLink.href = url || '';
      resetLink.textContent = url || '';
      resetLink.hidden = !url;
    }
    if (modal) {
      modal.hidden = false;
      modal.setAttribute('aria-hidden', 'false');
    }
  }

  function validEmail(email, errorTarget, inputs) {
    if (!email) {
      setError(errorTarget, inputs, '이메일을 입력해 주세요.');
      return false;
    }
    if (!emailPattern.test(email)) {
      setError(errorTarget, inputs, '이메일 형식이 올바르지 않습니다.');
      return false;
    }
    return true;
  }

  function renderLoginIds(email, accounts) {
    if (!findResult) return;
    if (!accounts || accounts.length === 0) {
      findResult.innerHTML = '<p>해당 이메일로 가입된 아이디가 없습니다.</p>';
      findResult.hidden = false;
      return;
    }
    findResult.innerHTML = '<p><strong>' + escapeHtml(email) + '</strong>로 가입된 아이디</p>' +
        '<ul>' + accounts.map(function(account) {
          return '<li><button type="button" data-fill-login-id="' + escapeHtml(account.loginId) + '">' +
              escapeHtml(account.loginId) + ' (' + escapeHtml(account.roleLabel || account.role || '') + ')' +
              '</button></li>';
        }).join('') + '</ul>';
    findResult.hidden = false;
  }

  if (findForm) {
    findForm.addEventListener('submit', async function(event) {
      event.preventDefault();
      var email = findEmailInput.value.trim();
      if (!validEmail(email, findError, [findEmailInput])) return;

      var button = findForm.querySelector('[type="submit"]');
      setError(findError, [findEmailInput], '');
      if (findResult) findResult.hidden = true;
      if (button) button.disabled = true;
      try {
        var res = await fetch('/api/auth/login-id/find', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify({ email: email })
        });
        if (!res.ok) {
          setError(findError, [findEmailInput], await res.text() || '아이디 찾기에 실패했습니다.');
          return;
        }
        var payload = await res.json();
        renderLoginIds(payload.email || email, payload.accounts || []);
      } catch (_) {
        setError(findError, [findEmailInput], '서버 연결에 실패했습니다.');
      } finally {
        if (button) button.disabled = false;
      }
    });
    findEmailInput.addEventListener('input', function() { setError(findError, [findEmailInput], ''); });
  }

  if (resetForm) {
    resetForm.addEventListener('submit', async function(event) {
      event.preventDefault();
      var loginId = resetLoginIdInput.value.trim();
      var email = resetEmailInput.value.trim();
      if (!loginId) {
        setError(resetError, [resetLoginIdInput], '아이디를 입력해 주세요.');
        return;
      }
      if (!validEmail(email, resetError, [resetEmailInput])) return;

      var button = resetForm.querySelector('[type="submit"]');
      setError(resetError, [resetLoginIdInput, resetEmailInput], '');
      if (button) button.disabled = true;
      try {
        var res = await fetch('/api/auth/password-reset/request', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify({ loginId: loginId, email: email })
        });
        if (!res.ok) {
          setError(resetError, [resetLoginIdInput, resetEmailInput], await res.text() || '비밀번호 재설정 요청에 실패했습니다.');
          return;
        }
        var payload = await res.json();
        openResult(payload.email || email, payload.resetUrl);
      } catch (_) {
        setError(resetError, [resetLoginIdInput, resetEmailInput], '서버 연결에 실패했습니다.');
      } finally {
        if (button) button.disabled = false;
      }
    });
    resetLoginIdInput.addEventListener('input', function() { setError(resetError, [resetLoginIdInput, resetEmailInput], ''); });
    resetEmailInput.addEventListener('input', function() { setError(resetError, [resetLoginIdInput, resetEmailInput], ''); });
  }

  document.addEventListener('click', function(event) {
    var fill = event.target.closest('[data-fill-login-id]');
    if (fill && resetLoginIdInput) {
      resetLoginIdInput.value = fill.dataset.fillLoginId || '';
      if (findEmailInput && resetEmailInput) resetEmailInput.value = findEmailInput.value.trim();
      resetLoginIdInput.focus();
    }
    if (event.target.closest('[data-reset-go-back]')) {
      event.preventDefault();
      history.length > 1 ? history.back() : location.assign('/home');
    }
  });

  function escapeHtml(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
})();
