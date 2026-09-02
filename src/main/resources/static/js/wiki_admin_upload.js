/**
 * /wiki 메인의 관리자 전용 인라인 업로드(모의고사 원본 / 분석 자료).
 *
 * - 이 스크립트는 서버에서 `role == 'ADMIN'` 일 때만 페이지에 포함된다(wiki.html th:if).
 * - 실제 권한은 `/api/admin/wiki/**` 가 ROLE_ADMIN 으로 게이트한다 — 비관리자가 직접 호출하면 403.
 * - 기존 admin original / analysis API·서비스를 그대로 재사용한다(새 저장 시스템 없음).
 * - 업로드 성공 시 새로고침 없이 해당 목록만 다시 조회한다(window.wikiHub).
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
  forms.original.addEventListener('submit', function (e) { submit(e, 'original'); });
  forms.analysis.addEventListener('submit', function (e) { submit(e, 'analysis'); });

  function bindOpen(btnId, kind) {
    var btn = document.getElementById(btnId);
    if (btn) btn.addEventListener('click', function () { openModal(kind); });
  }

  function openModal(kind) {
    Object.keys(forms).forEach(function (k) {
      forms[k].hidden = k !== kind;
      forms[k].reset();
      msg(forms[k], '', false);
    });
    // 현재 선택된 학년 탭을 기본값으로.
    var grade = currentGrade();
    var gradeSel = forms[kind].querySelector('[name="grade"]');
    if (gradeSel) gradeSel.value = String(grade);
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

  function currentGrade() {
    if (window.wikiHub && typeof window.wikiHub.currentGrade === 'function') return window.wikiHub.currentGrade();
    var active = document.querySelector('#gradeTabs .grade-tab.active');
    return active ? Number(active.dataset.grade) : 3;
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
      if (res.status === 401 || res.status === 403) {
        msg(form, '관리자만 업로드할 수 있습니다.', true);
        return;
      }
      if (!res.ok) {
        msg(form, '업로드 실패: ' + ((await res.text()) || res.status), true);
        return;
      }
      closeModal();
      if (window.wikiHub) {
        if (kind === 'original') window.wikiHub.reloadOriginals();
        else window.wikiHub.reloadAnalyses();
      }
    } catch (_) {
      msg(form, '업로드 중 오류가 발생했습니다.', true);
    } finally {
      submitBtn.disabled = false;
    }
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
    if (!file) { msg(form, 'PDF 파일을 선택해주세요.', true); return null; }
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
