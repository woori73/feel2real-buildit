(function () {
  'use strict';

  /* NewStart 전용 "내 프로필"(별명) 모달. fragments/_newstart_auth.html의 계정 메뉴에서 연다.
     공용 account_menu.js/css는 건드리지 않는다 — 이 파일이 로드되지 않는 페이지(NewStart 밖)에는
     [data-ns-profile-trigger]/[data-ns-profile-modal] 자체가 없으므로 완전히 독립적이다. */

  function init() {
    var trigger = document.querySelector('[data-ns-profile-trigger]');
    var modal = document.querySelector('[data-ns-profile-modal]');
    if (!trigger || !modal) return;

    var closeBtn = modal.querySelector('[data-ns-profile-close]');
    var input = modal.querySelector('[data-ns-profile-input]');
    var saveBtn = modal.querySelector('[data-ns-profile-save]');
    var message = modal.querySelector('[data-ns-profile-message]');

    function setMessage(text, isError) {
      if (!message) return;
      message.textContent = text || '';
      message.hidden = !text;
      message.classList.toggle('ns-profile-modal-error', !!isError);
    }

    function openModal() {
      modal.hidden = false;
      setMessage('', false);
      input.value = '';
      fetch('/api/newstart/profile/me', { credentials: 'same-origin' })
        .then(function (res) { return res.ok ? res.json() : null; })
        .then(function (data) {
          if (data && data.nickname) input.value = data.nickname;
        })
        .catch(function () { /* 조회 실패해도 빈 입력으로 그대로 둔다 */ });
      input.focus();
    }

    function closeModal() {
      modal.hidden = true;
    }

    trigger.addEventListener('click', function (e) {
      e.preventDefault();
      openModal();
    });
    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    modal.addEventListener('click', function (e) {
      if (e.target === modal) closeModal();
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !modal.hidden) closeModal();
    });

    if (saveBtn) {
      saveBtn.addEventListener('click', function () {
        var nickname = (input.value || '').trim();
        saveBtn.disabled = true;
        fetch('/api/newstart/profile/nickname', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify({ nickname: nickname })
        })
          .then(function (res) {
            return res.json().then(function (data) { return { ok: res.ok, data: data }; });
          })
          .then(function (result) {
            if (result.ok) {
              setMessage('별명을 저장했습니다.', false);
            } else {
              setMessage((result.data && result.data.errorMessage) || '저장하지 못했습니다.', true);
            }
          })
          .catch(function () {
            setMessage('서버 연결에 실패했습니다.', true);
          })
          .finally(function () {
            saveBtn.disabled = false;
          });
      });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
