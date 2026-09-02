/** 모의고사 원본 전체 목록(/wiki/originals). */
(function () {
  var grade = '3';
  var tabs = document.getElementById('gradeTabs');
  var list = document.getElementById('list');

  tabs.addEventListener('click', function (e) {
    var b = e.target.closest('.grade-tab');
    if (!b) return;
    tabs.querySelectorAll('.grade-tab').forEach(function (t) { t.classList.toggle('active', t === b); });
    grade = b.dataset.grade;
    load();
  });

  load();

  // wiki_admin_upload.js 가 업로드 성공 후 새로고침 없이 목록만 재조회하도록.
  window.wikiListReload = load;

  async function load() {
    list.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/wiki/originals' + (grade ? '?grade=' + grade : ''));
      if (!res.ok) throw new Error();
      var items = await res.json();
      if (!items.length) { list.innerHTML = '<p class="list-empty">등록된 원본이 없습니다.</p>'; return; }
      list.innerHTML = items.map(function (it) {
        return '<a class="file-card" href="' + escapeAttr(it.downloadUrl) + '">'
          + '<span class="file-badge">' + escapeHtml(it.docTypeLabel) + '</span>'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + '<span class="file-meta">' + escapeHtml(it.examLabel) + ' · 고' + it.grade + ' · ' + meta(it) + '</span>'
          + '<span class="file-dl">내려받기 ↓</span></a>';
      }).join('');
    } catch (_) {
      list.innerHTML = '<p class="error">목록을 불러오지 못했습니다.</p>';
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
