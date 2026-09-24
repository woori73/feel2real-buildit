/*
 * Builder "LMS 설정" 모달(에듀홀릭 LMS Phase 1 §7). academy_builder.js의 canvas/preview 상태와는
 * 완전히 독립적이다 — LMS는 공개페이지 섹션이 아니라 별도 화면(/lms/{handle})이라 pageSettings의
 * 다른 저장 흐름(page-settings, layout)과 얽히지 않게 전용 엔드포인트(/api/tutor/academy/lms-settings)
 * 하나만 호출한다.
 */
(function () {
  'use strict';

  var openBtn = document.getElementById('builderLmsSettingsBtn');
  var modal = document.getElementById('builderLmsSettingsModal');
  if (!openBtn || !modal) return;

  var enabledInput = document.getElementById('builderLmsEnabledInput');
  var displayNameInput = document.getElementById('builderLmsDisplayNameInput');
  var mainTitleInput = document.getElementById('builderLmsMainTitleInput');
  var introInput = document.getElementById('builderLmsIntroInput');
  var sectionNameInput = document.getElementById('builderLmsSectionNameInput');
  var errorBox = document.getElementById('builderLmsSettingsError');
  var applyBtn = document.getElementById('builderLmsSettingsApplyBtn');

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  function clearError() {
    errorBox.hidden = true;
    errorBox.textContent = '';
  }

  function openModal() {
    clearError();
    applyBtn.disabled = true;
    applyBtn.textContent = '불러오는 중…';
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    fetch('/api/tutor/academy/lms-settings', { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) throw new Error('load-failed');
        return res.json();
      })
      .then(function (settings) {
        enabledInput.checked = !!settings.enabled;
        displayNameInput.value = settings.displayName || '';
        mainTitleInput.value = settings.mainTitle || '';
        introInput.value = settings.intro || '';
        sectionNameInput.value = settings.sectionName || '';
      })
      .catch(function () {
        showError('LMS 설정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      })
      .finally(function () {
        applyBtn.disabled = false;
        applyBtn.textContent = '저장';
      });
  }

  function closeModal() {
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    clearError();
  }

  function save() {
    clearError();
    applyBtn.disabled = true;
    applyBtn.textContent = '저장 중…';
    var payload = {
      enabled: enabledInput.checked,
      displayName: displayNameInput.value.trim(),
      mainTitle: mainTitleInput.value.trim(),
      intro: introInput.value.trim(),
      sectionName: sectionNameInput.value.trim()
    };
    fetch('/api/tutor/academy/lms-settings', {
      method: 'PUT',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(function (res) {
        if (!res.ok) throw new Error('save-failed');
        return res.json();
      })
      .then(function () {
        closeModal();
      })
      .catch(function () {
        showError('저장하지 못했습니다. 잠시 후 다시 시도해 주세요.');
      })
      .finally(function () {
        applyBtn.disabled = false;
        applyBtn.textContent = '저장';
      });
  }

  openBtn.addEventListener('click', openModal);
  modal.querySelectorAll('[data-lms-settings-close]').forEach(function (button) {
    button.addEventListener('click', closeModal);
  });
  modal.addEventListener('click', function (event) {
    if (event.target === modal) closeModal();
  });
  applyBtn.addEventListener('click', save);
})();
