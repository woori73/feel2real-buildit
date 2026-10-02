(function () {
  'use strict';

  // 회원 본인 비밀번호 변경/설정. 값은 서버로 보내는 순간 버린다 — 저장·로그·재표시하지 않는다.
  var form = document.getElementById('ns-pw-form');
  var msg = document.getElementById('ns-pw-msg');
  var submit = form && form.querySelector('[data-ns-pw-submit]');
  if (!form || !msg || !submit) return;

  var current = document.getElementById('ns-pw-current');
  var next = document.getElementById('ns-pw-new');
  var confirm = document.getElementById('ns-pw-confirm');
  var minLength = parseInt(next.getAttribute('minlength') || '8', 10);
  var forced = submit.getAttribute('data-change-required') === 'true';

  function show(text, isError) {
    msg.textContent = text;
    msg.hidden = !text;
    msg.setAttribute('data-error', isError ? 'true' : 'false');
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    if (!current.value) { show('현재 비밀번호를 입력해 주세요.', true); return; }
    if (next.value.length < minLength) { show('새 비밀번호는 ' + minLength + '자 이상으로 입력해 주세요.', true); return; }
    if (next.value !== confirm.value) { show('새 비밀번호가 서로 일치하지 않습니다.', true); return; }

    var body = JSON.stringify({
      currentPassword: current.value,
      newPassword: next.value,
      confirmPassword: confirm.value
    });
    // 입력란을 즉시 비운다(실수로 다시 보여지지 않게). 서버가 새 hash를 저장한 뒤 성공 안내만 남긴다.
    current.value = '';
    next.value = '';
    confirm.value = '';

    show('저장 중...', false);
    fetch('/api/newstart/me/password', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: body
    }).then(function (res) {
      return res.text().then(function (text) { return { ok: res.ok, status: res.status, text: text }; });
    }).then(function (r) {
      if (r.ok) {
        if (forced) { window.location.href = '/newstart'; return; }
        show('비밀번호가 변경되었습니다.', false);
        return;
      }
      if (r.status === 401) { window.location.href = '/newstart/login'; return; }
      show(serverMessage(r), true);
    }).catch(function () {
      show('네트워크 오류입니다. 잠시 후 다시 시도해 주세요.', true);
    });
  });

  // 서버 실패 메시지는 text/plain 문자열 body로 온다(GlobalExceptionHandler). 없으면 일반 안내로 대체.
  function serverMessage(result) {
    var text = (result.text || '').trim().replace(/^"|"$/g, '');
    if (!text || text.length > 200) return '변경하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    return text;
  }
})();
