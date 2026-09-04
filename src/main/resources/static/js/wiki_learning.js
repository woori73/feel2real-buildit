/**
 * 모의고사 학습 자료 전체 목록(/wiki/learning).
 * - 목록/제목은 공개, 다운로드는 회원 전용(클릭 시 /api/auth/me 확인).
 * - ADMIN 이면 각 카드에 삭제 버튼 노출(DELETE /api/admin/wiki/learning-materials/{id}).
 */
(function () {
  var grade = '3';
  var isAdmin = false;
  var tabs = document.getElementById('gradeTabs');
  var list = document.getElementById('list');

  var TYPE_LABEL = { VOCAB: '어휘', WORKBOOK: 'Workbook', VARIANT: '변형문제', ETC: '기타' };

  tabs.addEventListener('click', function (e) {
    var b = e.target.closest('.grade-tab');
    if (!b) return;
    tabs.querySelectorAll('.grade-tab').forEach(function (t) { t.classList.toggle('active', t === b); });
    grade = b.dataset.grade;
    load();
  });

  // wiki_learning_upload.js 가 업로드 성공 후 목록만 재조회하도록.
  window.wikiLearningListReload = load;

  (async function init() {
    try {
      var res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (res.ok) isAdmin = (await res.json()).role === 'ADMIN';
    } catch (_) { /* 익명 */ }
    load();
  })();

  async function load() {
    list.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/wiki/learning-materials' + (grade ? '?grade=' + grade : ''));
      if (!res.ok) throw new Error();
      var items = await res.json();
      if (!items.length) { list.innerHTML = '<p class="list-empty">등록된 학습 자료가 없습니다.</p>'; return; }
      list.innerHTML = items.map(card).join('');
      list.querySelectorAll('.learning-card').forEach(function (el) {
        el.addEventListener('click', function () { download(el.dataset.downloadUrl); });
      });
      list.querySelectorAll('.learning-del').forEach(function (el) {
        el.addEventListener('click', function (e) {
          e.stopPropagation();
          remove(el.dataset.id, el.dataset.title);
        });
      });
    } catch (_) {
      list.innerHTML = '<p class="error">목록을 불러오지 못했습니다.</p>';
    }
  }

  function card(it) {
    var label = it.dataTypeLabel || TYPE_LABEL[it.dataType] || '기타';
    var admin = isAdmin
      ? '<span class="learning-card-admin"><button type="button" class="learning-del" data-id="' + it.wikiLearningMaterialId
        + '" data-title="' + escapeAttr(it.title) + '">삭제</button></span>'
      : '';
    return '<button type="button" class="learning-card" data-download-url="' + escapeAttr(it.downloadUrl) + '">'
      + '<span class="learning-badge">' + escapeHtml(label) + '</span>'
      + '<b>' + escapeHtml(it.title) + '</b>'
      + '<span class="learning-meta">고' + it.grade + ' · ' + escapeHtml(it.uploaderName || '회원') + ' · ' + meta(it) + '</span>'
      + admin
      + '</button>';
  }

  async function download(url) {
    if (!url) return;
    var me = null;
    try {
      var res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (res.ok) me = await res.json();
    } catch (_) { /* 비로그인 */ }
    if (!me) {
      alert('자료 다운로드는 회원 가입 후 이용할 수 있습니다.');
      if (typeof window.openLoginModal === 'function') window.openLoginModal();
      return;
    }
    window.location.href = url;
  }

  async function remove(id, title) {
    if (!id) return;
    if (!confirm('"' + title + '" 자료를 삭제할까요? 되돌릴 수 없습니다.')) return;
    try {
      var res = await fetchAuth('/api/admin/wiki/learning-materials/' + id, { method: 'DELETE', redirectOnAuthError: false });
      if (res.status === 401 || res.status === 403) { alert('관리자만 삭제할 수 있습니다.'); return; }
      if (!res.ok && res.status !== 204) { alert('삭제 실패: ' + res.status); return; }
      load();
    } catch (_) {
      alert('삭제 중 오류가 발생했습니다.');
    }
  }

  function meta(it) {
    var ext = (it.originalFileName || '').split('.').pop().toUpperCase();
    var mb = it.fileSize ? (it.fileSize / (1024 * 1024)).toFixed(1) + 'MB' : '';
    return [ext, mb].filter(Boolean).join(' · ');
  }
  function escapeHtml(v) { return String(v || '').replace(/[&<>'"]/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c]; }); }
  function escapeAttr(v) { return escapeHtml(v).replace(/`/g, '&#96;'); }
})();
