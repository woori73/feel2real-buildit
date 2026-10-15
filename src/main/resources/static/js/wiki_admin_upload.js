/**
 * BuildIt Wiki 자료 업로드(모의고사 원본 / 분석 자료) — /wiki, /wiki/originals, /wiki/analysis 공용.
 *
 * 원칙: "UI 표시 ≠ 권한 부여".
 * - 이 스크립트와 [+ 업로드] 버튼·모달은 principal 과 무관하게 항상 페이지에 로드된다(서버 조건부 렌더 없음).
 * - 버튼 클릭 시 GET /api/auth/me 로 현재 사용자 상태를 확인해서 modal 을 열지/안내할지 결정한다(UX).
 * - 최종 권한은 POST /api/admin/wiki/{originals|analysis-materials} 가 Spring Security ROLE_ADMIN 으로
 *   결정한다 — frontend 가 ADMIN 이라 판단해도 서버가 401/403 이면 업로드되지 않는다.
 * - 기존 admin original / analysis API·서비스를 그대로 재사용(analyzes/ExamAnalysis 미사용, 새 저장소 없음).
 */
(function () {
  var modal = document.getElementById('wikiUploadModal');
  if (!modal) return;

  var titleEl = document.getElementById('wikiUploadTitle');
  var forms = {
    original: modal.querySelector('[data-upload-form="original"]'),
    analysis: modal.querySelector('[data-upload-form="analysis"]'),
  };
  var TITLES = { original: '원본 업로드', analysis: '분석 자료 업로드' };

  bindOpen('btnUploadOriginal', 'original');
  bindOpen('btnUploadAnalysis', 'analysis');

  modal.addEventListener('click', function (e) {
    if (e.target === modal || e.target.closest('[data-upload-close]')) closeModal();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && modal.classList.contains('open')) closeModal();
  });
  if (forms.original) forms.original.addEventListener('submit', function (e) { submit(e, 'original'); });
  if (forms.analysis) forms.analysis.addEventListener('submit', function (e) { submit(e, 'analysis'); });

  function bindOpen(btnId, kind) {
    var btn = document.getElementById(btnId);
    if (!btn) return;
    btn.addEventListener('click', function () { requestOpen(kind, btn); });
  }

  /** 버튼 클릭 → 실제 auth API 로 상태 확인 후 modal 열기/안내. */
  async function requestOpen(kind, btn) {
    btn.disabled = true;
    try {
      var me = null;
      try {
        var res = await fetch('/api/auth/me', { credentials: 'same-origin' });
        if (res.ok) me = await res.json();
      } catch (_) { /* 네트워크 오류 → 아래 비로그인 취급 */ }

      if (!me) {
        alert('자료 업로드는 로그인한 관리자만 가능합니다. 로그인 후 다시 시도해주세요.');
        if (typeof window.openLoginModal === 'function') window.openLoginModal();
        return;
      }
      if (me.role !== 'ADMIN') {
        alert('관리자만 자료를 업로드할 수 있습니다.');
        return;
      }
      openModal(kind);
    } finally {
      btn.disabled = false;
    }
  }

  function openModal(kind) {
    Object.keys(forms).forEach(function (k) {
      if (!forms[k]) return;
      forms[k].hidden = k !== kind;
      forms[k].reset();
      msg(forms[k], '', false);
    });
    var gradeSel = forms[kind].querySelector('[name="grade"]');
    if (gradeSel) gradeSel.value = String(currentGrade());
    titleEl.textContent = TITLES[kind];
    modal.classList.add('open');
    modal.setAttribute('aria-hidden', 'false');
    var first = forms[kind].querySelector('input, select');
    if (first) setTimeout(function () { first.focus(); }, 0);
  }

  function closeModal() {
    modal.classList.remove('open');
    modal.setAttribute('aria-hidden', 'true');
  }

  /** 현재 선택된 학년 탭(고3/고2/고1). 전체("")·미확인이면 3. */
  function currentGrade() {
    if (window.wikiHub && typeof window.wikiHub.currentGrade === 'function') {
      var g = window.wikiHub.currentGrade();
      if (g) return g;
    }
    var active = document.querySelector('#gradeTabs .grade-tab.active');
    var n = active ? Number(active.dataset.grade) : 0;
    return n >= 1 && n <= 3 ? n : 3;
  }

  async function submit(e, kind) {
    e.preventDefault();
    var form = forms[kind];
    var submitBtn = form.querySelector('[data-upload-submit]');
    var fd = kind === 'original' ? originalPayload(form) : analysisPayload(form);
    if (!fd) return;

    submitBtn.disabled = true;
    msg(form, '업로드 중...', false);
    try {
      var url = kind === 'original' ? '/api/admin/wiki/originals' : '/api/admin/wiki/analysis-materials';
      var res = await fetchAuth(url, { method: 'POST', body: fd, redirectOnAuthError: false });
      if (res.status === 401) { msg(form, '로그인이 만료되었습니다. 새로고침 후 다시 로그인해주세요.', true); return; }
      if (res.status === 403) { msg(form, '관리자만 자료를 업로드할 수 있습니다.', true); return; }
      if (!res.ok) { msg(form, '업로드 실패: ' + ((await res.text()) || res.status), true); return; }
      closeModal();
      reloadList(kind);
    } catch (_) {
      msg(form, '업로드 중 오류가 발생했습니다.', true);
    } finally {
      submitBtn.disabled = false;
    }
  }

  /** 업로드 성공 → 새로고침 없이 해당 목록만 재조회. 훅이 없으면 location.reload(). */
  function reloadList(kind) {
    if (window.wikiHub) {
      if (kind === 'original' && typeof window.wikiHub.reloadOriginals === 'function') { window.wikiHub.reloadOriginals(); return; }
      if (kind === 'analysis' && typeof window.wikiHub.reloadAnalyses === 'function') { window.wikiHub.reloadAnalyses(); return; }
    }
    if (typeof window.wikiListReload === 'function') { window.wikiListReload(); return; }
    location.reload();
  }

  function originalPayload(form) {
    var year = val(form, 'year');
    var exam = val(form, 'exam');
    var fd = new FormData();
    fd.set('title', val(form, 'title'));
    fd.set('grade', val(form, 'grade'));
    fd.set('docType', val(form, 'docType'));
    fd.set('examLabel', [year, exam].filter(Boolean).join(' '));
    fd.set('visible', 'true');
    var file = form.querySelector('[name="file"]').files[0];
    if (!file) { msg(form, '파일(PDF·HWP·HWPX)을 선택해주세요.', true); return null; }
    fd.set('file', file);
    return fd;
  }

  function analysisPayload(form) {
    var fd = new FormData();
    ['title', 'description', 'schoolName', 'grade', 'year', 'semester', 'examType', 'subject'].forEach(function (name) {
      fd.set(name, val(form, name));
    });
    fd.set('visible', 'true');
    var file = form.querySelector('[name="file"]').files[0];
    if (!file) { msg(form, '파일을 선택해주세요.', true); return null; }
    fd.set('file', file);
    return fd;
  }

  function val(form, name) {
    var el = form.querySelector('[name="' + name + '"]');
    return el ? String(el.value || '').trim() : '';
  }

  function msg(form, text, isError) {
    var el = form.querySelector('[data-upload-msg]');
    if (!el) return;
    el.textContent = text;
    el.style.color = isError ? '#c53030' : '#5b6472';
  }
})();
