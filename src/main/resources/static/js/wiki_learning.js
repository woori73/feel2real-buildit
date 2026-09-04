/**
 * 모의고사 학습 자료 전체 목록(/wiki/learning).
 * 카드 클릭 = 즉시 다운로드가 아니라 Wiki 안의 상세/미리보기 화면(/wiki/learning/{id})으로 이동.
 * 다운로드·삭제는 상세 화면에서 처리한다.
 */
(function () {
  var grade = '3';
  var tabs = document.getElementById('gradeTabs');
  var list = document.getElementById('list');

  var TYPE_LABEL = { VOCAB: '어휘', WORKBOOK: 'Workbook', VARIANT: '변형문제', WEEKLY: 'WEEKLY', ETC: '기타' };

  tabs.addEventListener('click', function (e) {
    var b = e.target.closest('.grade-tab');
    if (!b) return;
    tabs.querySelectorAll('.grade-tab').forEach(function (t) { t.classList.toggle('active', t === b); });
    grade = b.dataset.grade;
    load();
  });

  // wiki_learning_upload.js 가 업로드 성공 후 목록만 재조회하도록.
  window.wikiLearningListReload = load;

  load();

  async function load() {
    list.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/wiki/learning-materials' + (grade ? '?grade=' + grade : ''));
      if (!res.ok) throw new Error();
      var items = await res.json();
      if (!items.length) { list.innerHTML = '<p class="list-empty">등록된 학습 자료가 없습니다.</p>'; return; }
      list.innerHTML = items.map(card).join('');
    } catch (_) {
      list.innerHTML = '<p class="error">목록을 불러오지 못했습니다.</p>';
    }
  }

  function card(it) {
    var label = it.dataTypeLabel || TYPE_LABEL[it.dataType] || '기타';
    return '<a class="learning-card" href="/wiki/learning/' + encodeURIComponent(it.wikiLearningMaterialId) + '">'
      + '<span class="learning-badge">' + escapeHtml(label) + '</span>'
      + '<b>' + escapeHtml(it.title) + '</b>'
      + '<span class="learning-meta">고' + it.grade + ' · ' + escapeHtml(it.uploaderName || '회원') + ' · ' + meta(it) + '</span>'
      + '</a>';
  }

  function meta(it) {
    var ext = (it.originalFileName || '').split('.').pop().toUpperCase();
    var mb = it.fileSize ? (it.fileSize / (1024 * 1024)).toFixed(1) + 'MB' : '';
    return [ext, mb].filter(Boolean).join(' · ');
  }
  function escapeHtml(v) { return String(v || '').replace(/[&<>'"]/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c]; }); }
})();
