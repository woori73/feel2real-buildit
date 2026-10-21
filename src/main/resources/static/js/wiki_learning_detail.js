/** 모의고사 학습 자료 상세/미리보기. PDF와 HTML을 iframe으로 표시하며 HTML은 강제 sandbox한다. */
(function () {
  var root = document.getElementById('wlDetail');
  var id = location.pathname.split('/').filter(Boolean).pop();
  if (!id || !/^\d+$/.test(id)) { root.innerHTML = '<p class="error">잘못된 주소입니다.</p>'; return; }
  var TYPE_LABEL = { VOCAB: '어휘', WORKBOOK: 'Workbook', VARIANT: '변형문제', WEEKLY: 'WEEKLY', ETC: '기타' };
  render();
  async function render() {
    var item, me = null;
    try {
      var res = await fetch('/api/wiki/learning-materials/' + encodeURIComponent(id));
      if (res.status === 404) { root.innerHTML = '<p class="error">자료를 찾을 수 없습니다.</p>'; return; }
      if (!res.ok) throw new Error(); item = await res.json();
    } catch (_) { root.innerHTML = '<p class="error">자료를 불러오지 못했습니다.</p>'; return; }
    try { var meRes = await fetch('/api/auth/me', { credentials: 'same-origin' }); if (meRes.ok) me = await meRes.json(); } catch (_) {}
    var label = item.dataTypeLabel || TYPE_LABEL[item.dataType] || '기타';
    var isAdmin = !!me && me.role === 'ADMIN';
    var ext = (item.originalFileName || '').split('.').pop().toLowerCase();
    var isHtml = ext === 'html' || ext === 'htm';
    var sandbox = isHtml ? ' sandbox=""' : '';
    var previewHtml = item.previewable
      ? '<iframe class="wl-preview" src="' + escapeAttr(item.previewUrl) + '" title="자료 미리보기" referrerpolicy="no-referrer"' + sandbox + '></iframe>'
      : '<p class="wl-nopreview">이 형식은 Wiki에서 미리보기를 제공하지 않습니다. 아래에서 내려받아 확인하세요.</p>';
    root.innerHTML = '<div class="wl-detail-head"><span class="learning-badge">' + escapeHtml(label) + '</span><h1>' + escapeHtml(item.title)
      + '</h1><p class="wl-detail-meta">고' + item.grade + ' · ' + escapeHtml(item.uploaderName || '회원') + ' · ' + fmtDate(item.createdAt) + ' · ' + meta(item)
      + '</p></div><div class="wl-preview-wrap">' + previewHtml + '</div><div class="wl-detail-actions"><button type="button" class="btn-primary" id="wlDownload">다운로드</button>'
      + (isAdmin ? '<button type="button" class="btn-ghost" id="wlEdit">수정</button><button type="button" class="btn-ghost" id="wlDelete">삭제</button>' : '') + '</div>'
      + '<div id="wlEditBox"></div>'
      + (me ? '' : '<p class="wl-detail-hint">다운로드는 회원 가입 후 이용할 수 있습니다.</p>');
    document.getElementById('wlDownload').addEventListener('click', function () { download(item, me); });
    if (isAdmin) {
      document.getElementById('wlDelete').addEventListener('click', function () { remove(item); });
      document.getElementById('wlEdit').addEventListener('click', function () { toggleEdit(item); });
    }
  }
  async function download(item, me) {
    var current = me;
    if (!current) { try { var res = await fetch('/api/auth/me', { credentials: 'same-origin' }); if (res.ok) current = await res.json(); } catch (_) {} }
    if (!current) { alert('자료 다운로드는 회원 가입 후 이용할 수 있습니다.'); if (typeof window.openLoginModal === 'function') window.openLoginModal(); return; }
    window.location.href = item.downloadUrl;
  }
  function toggleEdit(item) {
    var box = document.getElementById('wlEditBox');
    if (box.innerHTML) { box.innerHTML = ''; return; }
    var typeOptions = Object.keys(TYPE_LABEL).map(function (k) {
      return '<option value="' + k + '"' + (item.dataType === k ? ' selected' : '') + '>' + TYPE_LABEL[k] + '</option>';
    }).join('');
    var gradeOptions = [1, 2, 3].map(function (g) {
      return '<option value="' + g + '"' + (item.grade === g ? ' selected' : '') + '>고' + g + '</option>';
    }).join('');
    box.innerHTML = '<form class="wl-edit-form" id="wlEditForm">'
      + '<label>제목<input type="text" name="title" value="' + escapeAttr(item.title) + '"></label>'
      + '<label>학년<select name="grade">' + gradeOptions + '</select></label>'
      + '<label>자료 종류<select name="dataType">' + typeOptions + '</select></label>'
      + '<label>파일 교체 <span class="muted">(교체할 때만 선택 — 현재: ' + escapeHtml(item.originalFileName || '') + ')</span>'
      + '<input type="file" name="file"></label>'
      + '<div class="wl-edit-actions"><button type="submit" class="btn-primary">수정 저장</button>'
      + '<button type="button" class="btn-ghost" id="wlEditCancel">취소</button></div>'
      + '<p class="uf-msg" id="wlEditMsg"></p></form>';
    document.getElementById('wlEditCancel').addEventListener('click', function () { box.innerHTML = ''; });
    document.getElementById('wlEditForm').addEventListener('submit', function (e) { saveEdit(e, item); });
  }
  async function saveEdit(e, item) {
    e.preventDefault();
    var form = e.target;
    var msg = document.getElementById('wlEditMsg');
    var fd = new FormData();
    fd.set('dataType', form.querySelector('[name="dataType"]').value);
    fd.set('grade', form.querySelector('[name="grade"]').value);
    fd.set('title', form.querySelector('[name="title"]').value.trim());
    var file = form.querySelector('[name="file"]').files[0];
    if (file) fd.set('file', file);
    var btn = form.querySelector('[type="submit"]');
    btn.disabled = true;
    msg.textContent = '수정 중...';
    try {
      var res = await fetchAuth('/api/admin/wiki/learning-materials/' + item.wikiLearningMaterialId, { method: 'PUT', body: fd, redirectOnAuthError: false });
      if (res.status === 401 || res.status === 403) { msg.textContent = '관리자만 수정할 수 있습니다.'; return; }
      if (!res.ok) { msg.textContent = '수정 실패: ' + ((await res.text()) || res.status); return; }
      render();
    } catch (_) {
      msg.textContent = '수정 중 오류가 발생했습니다.';
    } finally {
      btn.disabled = false;
    }
  }
  async function remove(item) {
    if (!confirm('"' + item.title + '" 자료를 삭제할까요? 되돌릴 수 없습니다.')) return;
    try {
      var res = await fetchAuth('/api/admin/wiki/learning-materials/' + item.wikiLearningMaterialId, { method: 'DELETE', redirectOnAuthError: false });
      if (res.status === 401 || res.status === 403) { alert('관리자만 삭제할 수 있습니다.'); return; }
      if (!res.ok && res.status !== 204) { alert('삭제 실패: ' + res.status); return; } window.location.href = '/wiki/learning';
    } catch (_) { alert('삭제 중 오류가 발생했습니다.'); }
  }
  function meta(it) { var ext = (it.originalFileName || '').split('.').pop().toUpperCase(); var mb = it.fileSize ? (it.fileSize / (1024 * 1024)).toFixed(1) + 'MB' : ''; return [ext, mb].filter(Boolean).join(' · '); }
  function fmtDate(v) { if (!v) return ''; var d = new Date(v); if (isNaN(d)) return ''; return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); }
  function escapeHtml(v) { return String(v || '').replace(/[&<>'"]/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c]; }); }
  function escapeAttr(v) { return escapeHtml(v).replace(/`/g, '&#96;'); }
})();
