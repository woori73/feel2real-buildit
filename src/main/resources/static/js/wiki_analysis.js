/** 분석 자료 전체 목록(/wiki/analysis). */
(function () {
  var grade = '3';
  var tabs = document.getElementById('gradeTabs');
  var list = document.getElementById('list');
  var itemsById = {};

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
      var items = (await res.json()).filter(function (it) { return !/이전 편집 시안|검토본/.test(it.title || ''); });
      if (!items.length) { list.innerHTML = '<p class="list-empty">등록된 분석 자료가 없습니다.</p>'; return; }
      var admin = window.WikiAdminManage ? await window.WikiAdminManage.isAdmin() : false;
      itemsById = {};
      list.innerHTML = items.map(function (it) {
        itemsById[it.wikiAnalysisMaterialId] = it;
        var where = (it.schoolName || '전국') + ' · ' + it.grade + '학년 · ' + it.year + ' ' + it.semester + ' ' + it.examType;
        var review = /복습|자습/.test(it.title || '') && /\.html?$/i.test(it.originalFileName || '');
        var q = String(it.title || '').match(/(\d+)(?:[~～-]\d+)?번/);
        var href = review ? '/wiki?grade=' + encodeURIComponent(it.grade) + '&month=' + (/9월|9모/.test(it.title || '') ? 9 : 10) + (q ? '&question=' + q[1] : '') + '#review' : it.downloadUrl;
        var card = '<a class="analysis-card" ' + (review ? '' : 'data-member-download ') + 'href="' + escapeAttr(href) + '">'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + (it.description ? '<span class="analysis-meta">' + escapeHtml(it.description) + '</span>' : '')
          + '<span class="analysis-meta">' + escapeHtml(where) + '</span>'
          + '<span class="analysis-badge">' + escapeHtml(it.subject || '영어') + ' · ' + meta(it) + '</span></a>';
        if (!admin) return card;
        return '<div class="analysis-card-wrap">' + card + window.WikiAdminManage.buttonsHtml('analysis', it.wikiAnalysisMaterialId) + '</div>';
      }).join('');
    } catch (_) {
      list.innerHTML = '<p class="error">목록을 불러오지 못했습니다.</p>';
    }
  }

  if (window.WikiAdminManage) {
    window.WikiAdminManage.bind(list, {
      onEdit: function (kind, id) {
        var item = itemsById[id];
        if (item && window.wikiAdminUpload) window.wikiAdminUpload.edit('analysis', item);
      },
      reload: load,
    });
  }

  function meta(it) {
    var ext = (it.originalFileName || '').split('.').pop().toUpperCase();
    var mb = it.fileSize ? (it.fileSize / (1024 * 1024)).toFixed(1) + 'MB' : '';
    return [ext, mb].filter(Boolean).join(' · ');
  }
  function escapeHtml(v) { return String(v || '').replace(/[&<>'"]/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c]; }); }
  function escapeAttr(v) { return escapeHtml(v).replace(/`/g, '&#96;'); }
})();

