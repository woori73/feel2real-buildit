/** 분석 자료 전체 목록(/wiki/analysis). */
(function () {
  var grade = '3';
  var tabs = document.getElementById('gradeTabs');
  var list = document.getElementById('list');

  tabs.addEventListener('click', function (e) {
    var b = e.target.closest('.grade-tab');
    if (!b) return;
    tabs.querySelectorAll('.grade-tab').forEach(function (t) { t.classList.toggle('active', t === b); });
    grade = b.dataset.grade;
    list.addEventListener('click', async function (e) {
    var link = e.target.closest('[data-member-download]');
    if (!link) return;
    e.preventDefault();
    try {
      var me = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (me.ok) { window.location.href = link.href; return; }
    } catch (_) {}
    alert('자료 다운로드는 로그인 회원만 이용할 수 있습니다.');
    if (typeof window.openLoginModal === 'function') window.openLoginModal();
  });

  load();
  });

  load();

  // wiki_admin_upload.js 가 업로드 성공 후 새로고침 없이 목록만 재조회하도록.
  window.wikiListReload = load;

  async function load() {
    list.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/wiki/analysis-materials' + (grade ? '?grade=' + grade : ''));
      if (!res.ok) throw new Error();
      var items = await res.json();
      if (!items.length) { list.innerHTML = '<p class="list-empty">등록된 분석 자료가 없습니다.</p>'; return; }
      list.innerHTML = items.map(function (it) {
        var where = (it.schoolName || '전국') + ' · ' + it.grade + '학년 · ' + it.year + ' ' + it.semester + ' ' + it.examType;
        return '<a class="analysis-card" data-member-download href="' + escapeAttr(it.downloadUrl) + '">'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + (it.description ? '<span class="analysis-meta">' + escapeHtml(it.description) + '</span>' : '')
          + '<span class="analysis-meta">' + escapeHtml(where) + '</span>'
          + '<span class="analysis-badge">' + escapeHtml(it.subject || '영어') + ' · ' + meta(it) + '</span></a>';
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
