/**
 * "모의고사 학습 자료" 업로드 — /wiki(허브), /wiki/learning(전체보기) 공용.
 *
 * 원칙: "UI 표시 ≠ 권한 부여".
 * - 버튼·모달·이 스크립트는 principal 과 무관하게 항상 로드된다.
 * - [+ 자료 올리기] 클릭 시 GET /api/auth/me 로 회원 여부 확인 → 비회원이면 안내 후 로그인/회원가입.
 * - 최종 권한은 POST /api/wiki/learning-materials (Spring Security 인증 게이트) 가 결정한다.
 * - 학년은 현재 Wiki 학년 탭에서 자동으로 온다(모달에서 다시 고르지 않는다).
 * - 제목은 선택한 파일명에서 자동 생성하고, 사용자가 건드리면 그대로 둔다.
 */
(function () {
  var modal = document.getElementById('wikiLearningModal');
  if (!modal) return;

  var form = document.getElementById('wikiLearningForm');
  var typeGroup = document.getElementById('wlTypeGroup');
  var fileInput = form.querySelector('[name="file"]');
  var titleInput = form.querySelector('[name="title"]');
  var selectedType = 'VOCAB';
  var titleDirty = false;

  typeGroup.addEventListener('click', function (e) {
    var b = e.target.closest('.wl-type-btn');
    if (!b) return;
    typeGroup.querySelectorAll('.wl-type-btn').forEach(function (t) { t.classList.toggle('active', t === b); });
    selectedType = b.dataset.type;
  });

  titleInput.addEventListener('input', function () { titleDirty = true; });

  fileInput.addEventListener('change', function () {
    var f = fileInput.files[0];
    if (f && !titleDirty) titleInput.value = titleFromName(f.name);
  });

  modal.addEventListener('click', function (e) {
    if (e.target === modal || e.target.closest('[data-learning-close]')) closeModal();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && modal.classList.contains('open')) closeModal();
  });
  form.addEventListener('submit', submit);

  bindOpen('btnUploadLearning');

  // 다른 스크립트(예: 전체보기 페이지)에서 프로그램적으로 열 수 있게.
  window.wikiLearningUpload = { open: function (btn) { requestOpen(btn || null); } };

  function bindOpen(btnId) {
    var btn = document.getElementById(btnId);
    if (!btn) return;
    btn.addEventListener('click', function () { requestOpen(btn); });
  }

  /** 버튼 클릭 → 회원 여부 확인 후 modal 열기 / 회원가입 안내. */
  async function requestOpen(btn) {
    if (btn) btn.disabled = true;
    try {
      var me = null;
      try {
        var res = await fetch('/api/auth/me', { credentials: 'same-origin' });
        if (res.ok) me = await res.json();
      } catch (_) { /* 네트워크 오류 → 비로그인 취급 */ }

      if (!me) {
        alert('자료 업로드는 회원 가입 후 이용할 수 있습니다.');
        if (typeof window.openLoginModal === 'function') window.openLoginModal();
        return;
      }
      openModal();
    } finally {
      if (btn) btn.disabled = false;
    }
  }

  function openModal() {
    form.reset();
    titleDirty = false;
    selectedType = 'VOCAB';
    typeGroup.querySelectorAll('.wl-type-btn').forEach(function (t, i) { t.classList.toggle('active', i === 0); });
    msg('', false);
    modal.classList.add('open');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(function () { fileInput.focus(); }, 0);
  }

  function closeModal() {
    modal.classList.remove('open');
    modal.setAttribute('aria-hidden', 'true');
  }

  /** 현재 선택된 학년 탭(고3/고2/고1). 미확인이면 3. */
  function currentGrade() {
    if (window.wikiHub && typeof window.wikiHub.currentGrade === 'function') {
      var g = window.wikiHub.currentGrade();
      if (g) return g;
    }
    var active = document.querySelector('#gradeTabs .grade-tab.active');
    var n = active ? Number(active.dataset.grade) : 0;
    return n >= 1 && n <= 3 ? n : 3;
  }

  async function submit(e) {
    e.preventDefault();
    var submitBtn = form.querySelector('[data-learning-submit]');
    var file = fileInput.files[0];
    if (!file) { msg('파일을 선택해주세요.', true); return; }

    var fd = new FormData();
    fd.set('dataType', selectedType);
    fd.set('grade', String(currentGrade()));
    fd.set('title', titleInput.value.trim());
    fd.set('file', file);

    submitBtn.disabled = true;
    msg('올리는 중...', false);
    try {
      var res = await fetchAuth('/api/wiki/learning-materials', { method: 'POST', body: fd, redirectOnAuthError: false });
      if (res.status === 401) {
        msg('자료 업로드는 회원 가입 후 이용할 수 있습니다.', true);
        if (typeof window.openLoginModal === 'function') window.openLoginModal();
        return;
      }
      if (!res.ok) { msg('업로드 실패: ' + ((await res.text()) || res.status), true); return; }
      closeModal();
      alert('자료가 등록되었습니다.');
      reloadList();
    } catch (_) {
      msg('업로드 중 오류가 발생했습니다.', true);
    } finally {
      submitBtn.disabled = false;
    }
  }

  /** 업로드 성공 → 새로고침 없이 목록만 재조회. 훅이 없으면 location.reload(). */
  function reloadList() {
    if (window.wikiHub && typeof window.wikiHub.reloadLearning === 'function') { window.wikiHub.reloadLearning(); return; }
    if (typeof window.wikiLearningListReload === 'function') { window.wikiLearningListReload(); return; }
    location.reload();
  }

  function titleFromName(name) {
    var base = String(name || '');
    var slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
    if (slash >= 0) base = base.slice(slash + 1);
    var dot = base.lastIndexOf('.');
    if (dot > 0) base = base.slice(0, dot);
    return base.replace(/_/g, ' ').replace(/\s+/g, ' ').trim();
  }

  function msg(text, isError) {
    var el = form.querySelector('[data-learning-msg]');
    if (!el) return;
    el.textContent = text;
    el.style.color = isError ? '#c53030' : '#5b6472';
  }
})();
