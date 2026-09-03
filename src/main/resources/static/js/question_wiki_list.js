/**
 * 공개 BuildIt Wiki 문항별 THREAD 네비게이션(/wiki/questions).
 * 학년 탭으로 2026년 9월 전국연합학력평가(영어) 문항을 필터하고, 문항 카드 → /wiki/questions/{id} 로 이동.
 */
(function () {
  var grade = 2;
  var gradeTabs = document.getElementById('gradeTabs');
  var grid = document.getElementById('qwGrid');
  var title = document.getElementById('qwListTitle');

  gradeTabs.addEventListener('click', function (e) {
    var b = e.target.closest('.grade-tab');
    if (!b) return;
    gradeTabs.querySelectorAll('.grade-tab').forEach(function (t) { t.classList.toggle('active', t === b); });
    grade = Number(b.dataset.grade);
    load();
  });

  load();

  async function load() {
    grid.innerHTML = '<p class="muted">불러오는 중...</p>';
    title.textContent = '고' + grade + ' 문항';
    try {
      var res = await fetch('/api/wiki/questions?grade=' + grade);
      if (!res.ok) throw new Error();
      var items = await res.json();
      if (!items.length) {
        grid.innerHTML = '<p class="muted">이 학년의 문항이 아직 준비되지 않았습니다.</p>';
        return;
      }
      grid.innerHTML = items.map(function (q) {
        var sub = q.title ? escapeHtml(q.title) : (q.examYear + ' · ' + escapeHtml(q.examLabel));
        var group = q.passageGroupKey ? '<span class="qw-card-group">공동지문</span>' : '';
        return '<a class="qw-card" href="/wiki/questions/' + q.questionWikiId + '">'
          + '<span class="qw-card-num">' + q.questionNumber + '번</span>'
          + group
          + '<span class="qw-card-sub">' + sub + '</span>'
          + '<span class="qw-card-thread">THREAD ' + Number(q.threadCount || 0) + '</span>'
          + '</a>';
      }).join('');
    } catch (_) {
      grid.innerHTML = '<p class="error">문항 목록을 불러오지 못했습니다.</p>';
    }
  }

  function escapeHtml(v) {
    return String(v || '').replace(/[&<>'"]/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c];
    });
  }
})();
