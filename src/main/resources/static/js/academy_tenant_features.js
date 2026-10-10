/* BuildIt 2단계 — Builder 테넌트 기능 설정 UI.
 *
 * 공개 페이지의 학원별 기능(campaignPopup / featuredCta / liveMode / videoFilter)을
 * 켜고 끄는 Builder 설정 모달. LMS 설정(builderLmsSettingsBtn/Modal)과 같은
 * 툴바 버튼 + 모달 패턴을 따른다.
 *
 * - 설정은 Academy.page_settings.builderDraft 의 `tenant.*` 키에 JSON 문자열로 저장된다.
 * - 저장은 PUT /api/tutor/academy/page-settings { builderDraft: {...} } 로 수행하며,
 *   서버(AcademyService)가 기존 builderDraft 와 부분 병합하므로 다른 설정값은 보존된다.
 * - 값 검증: URL 형식은 http(s) 또는 '/' 시작만 허용, sceneIntervalMs 는 양의 정수.
 *   잘못된 값은 저장하지 않고 오류를 표시한다.
 * - 기본값은 전부 비활성화. 에듀홀릭은 기존 eduholic_* 스크립트가 담당하므로
 *   이 UI 로 tenant.* 를 켜지 않는 것을 권장한다(문구로 안내).
 */
(function () {
  'use strict';

  var openBtn = document.getElementById('builderTenantFeaturesBtn');
  var modal = document.getElementById('builderTenantFeaturesModal');
  if (!openBtn || !modal) return;

  var errorBox = document.getElementById('builderTenantFeaturesError');
  var applyBtn = document.getElementById('builderTenantFeaturesApplyBtn');

  // [key, enabledId, fields: [[inputId, jsonField, kind]]]
  var FEATURES = [
    {
      key: 'tenant.campaignPopup', title: '캠페인 팝업',
      enabledId: 'tnfPopupEnabled',
      fields: [
        ['tnfPopupLandingPath', 'landingPath', 'path'],
        ['tnfPopupHeadline', 'headline', 'text'],
        ['tnfPopupBody', 'body', 'text'],
        ['tnfPopupCtaLabel', 'ctaLabel', 'text'],
        ['tnfPopupDismissKey', 'dismissKey', 'text']
      ]
    },
    {
      key: 'tenant.featuredCta', title: 'Featured CTA',
      enabledId: 'tnfCtaEnabled',
      fields: [
        ['tnfCtaLabel', 'label', 'text'],
        ['tnfCtaLandingPath', 'landingPath', 'path']
      ]
    },
    {
      key: 'tenant.liveMode', title: 'LIVE 모드',
      enabledId: 'tnfLiveEnabled',
      fields: [
        ['tnfLiveVideoSrc', 'videoSrc', 'path'],
        ['tnfLiveVideoTitle', 'videoTitle', 'text'],
        ['tnfLiveVideoDesc', 'videoDesc', 'text'],
        ['tnfLiveLandingPath', 'landingPath', 'path'],
        ['tnfLiveSceneIntervalMs', 'sceneIntervalMs', 'int'],
        ['tnfLiveFilterMarker', 'filterMarker', 'text']
      ]
    },
    {
      key: 'tenant.videoFilter', title: '영상 필터',
      enabledId: 'tnfFilterEnabled',
      fields: [
        ['tnfFilterMarker', 'marker', 'text']
      ]
    }
  ];

  function $(id) { return document.getElementById(id); }

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }
  function clearError() {
    errorBox.hidden = true;
    errorBox.textContent = '';
  }

  function parseDraft(draft) {
    var out = {};
    FEATURES.forEach(function (f) {
      var raw = draft && draft[f.key];
      if (!raw) return;
      try {
        var parsed = JSON.parse(raw);
        if (parsed && typeof parsed === 'object') out[f.key] = parsed;
      } catch (_) {}
    });
    return out;
  }

  function fillForm(parsed) {
    FEATURES.forEach(function (f) {
      var cfg = parsed[f.key] || {};
      $(f.enabledId).checked = cfg.enabled === true;
      f.fields.forEach(function (fd) {
        var v = cfg[fd[1]];
        $(fd[0]).value = v == null ? '' : String(v);
      });
    });
  }

  function validPath(v) {
    if (!v) return true;
    return v.charAt(0) === '/' || /^https?:\/\//i.test(v);
  }

  function collectDraft() {
    var draft = {};
    for (var i = 0; i < FEATURES.length; i++) {
      var f = FEATURES[i];
      var enabled = $(f.enabledId).checked;
      var cfg = { enabled: enabled };
      for (var j = 0; j < f.fields.length; j++) {
        var fd = f.fields[j];
        var raw = $(fd[0]).value.trim();
        if (!raw) continue;
        if (fd[2] === 'path' && !validPath(raw)) {
          showError('[' + f.title + '] 경로 형식이 올바르지 않습니다: ' + raw + ' ("/" 또는 "http(s)://" 로 시작)');
          $(fd[0]).focus();
          return null;
        }
        if (fd[2] === 'int') {
          var n = parseInt(raw, 10);
          if (isNaN(n) || n <= 0) {
            showError('[' + f.title + '] 장면 전환 간격은 양의 정수를 입력하세요.');
            $(fd[0]).focus();
            return null;
          }
          cfg[fd[1]] = n;
          continue;
        }
        cfg[fd[1]] = raw;
      }
      draft[f.key] = JSON.stringify(cfg);
    }
    return draft;
  }

  function openModal() {
    clearError();
    applyBtn.disabled = true;
    applyBtn.textContent = '불러오는 중…';
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    fetch('/api/tutor/academy/me', { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) throw new Error('load-failed');
        return res.json();
      })
      .then(function (academy) {
        var draft = academy && academy.pageSettings && academy.pageSettings.builderDraft;
        fillForm(parseDraft(draft));
      })
      .catch(function () {
        showError('테넌트 기능 설정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
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
    var draft = collectDraft();
    if (!draft) { applyBtn.disabled = false; applyBtn.textContent = '저장'; return; }
    applyBtn.disabled = true;
    applyBtn.textContent = '저장 중…';
    fetch('/api/tutor/academy/page-settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({ builderDraft: draft })
    })
      .then(function (res) {
        if (!res.ok) throw new Error('save-failed');
        return res.json();
      })
      .then(function () {
        closeModal();
        if (window.toast) window.toast('테넌트 기능 설정을 저장했습니다.');
      })
      .catch(function () {
        showError('저장에 실패했습니다. 입력값을 확인하고 다시 시도해 주세요.');
      })
      .finally(function () {
        applyBtn.disabled = false;
        applyBtn.textContent = '저장';
      });
  }

  openBtn.addEventListener('click', openModal);
  applyBtn.addEventListener('click', save);
  modal.querySelectorAll('[data-tenant-features-close]').forEach(function (btn) {
    btn.addEventListener('click', closeModal);
  });
  modal.addEventListener('click', function (e) {
    if (e.target === modal) closeModal();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && modal.classList.contains('is-open')) closeModal();
  });
})();
