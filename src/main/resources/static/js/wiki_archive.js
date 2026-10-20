/**
 * 모의고사 아카이브(/wiki/archive?year=&month=).
 * - 회차 목록은 /api/wiki/rounds(공개 메타), 자료는 /api/wiki/originals?year=&month=(회원).
 * - 과거 URL(?year=&month=)은 그대로 유지되며, 선택을 바꾸면 쿼리를 교체해 같은 화면에서 본다.
 */
(function () {
  var VIEWABLE_EXTS = { html: 1, htm: 1, png: 1, jpg: 1, jpeg: 1, webp: 1 };
  var params = new URLSearchParams(location.search);
  var year = params.get('year');
  var month = params.get('month');
  var grade = '';

  var select = document.getElementById('archiveRoundSelect');
  var list = document.getElementById('archiveList');

  document.addEventListener('click', async function (e) {
    var link = e.target.closest('[data-member-download]');
    if (!link) return;
    e.preventDefault();
    try {
      var me = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (me.ok) {
        if (link.dataset.newtab) { window.open(link.href, '_blank', 'noopener'); }
        else { window.location.href = link.href; }
        return;
      }
    } catch (_) {}
    alert('자료 열람·다운로드는 로그인 회원만 이용할 수 있습니다.');
    if (typeof window.openLoginModal === 'function') window.openLoginModal();
  });

  document.getElementById('gradeTabs').addEventListener('click', function (e) {
    var b = e.target.closest('.grade-tab');
    if (!b) return;
    document.querySelectorAll('#gradeTabs .grade-tab').forEach(function (t) {
      t.classList.toggle('active', t === b);
    });
    grade = b.dataset.grade;
    loadMaterials();
  });

  select.addEventListener('change', function () {
    var parts = select.value.split('-');
    year = parts[0];
    month = parts[1];
    history.replaceState(null, '', '/wiki/archive?year=' + encodeURIComponent(year) + '&month=' + encodeURIComponent(month));
    loadMaterials();
  });

  loadRounds();

  async function loadRounds() {
    try {
      var res = await fetch('/api/wiki/rounds');
      if (!res.ok) throw new Error();
      var rounds = await res.json();
      if (!rounds.length) {
        list.innerHTML = '<p class="muted">아직 아카이브된 회차가 없습니다.</p>';
        select.disabled = true;
        return;
      }
      if (!year || !month) {
        year = String(rounds[0].examYear);
        month = String(rounds[0].examMonth);
        history.replaceState(null, '', '/wiki/archive?year=' + year + '&month=' + month);
      }
      select.innerHTML = rounds.map(function (r) {
        var v = r.examYear + '-' + r.examMonth;
        var selected = String(r.examYear) === String(year) && String(r.examMonth) === String(month);
        return '<option value="' + v + '"' + (selected ? ' selected' : '') + '>' + escapeHtml(r.title) + '</option>';
      }).join('');
      var current = rounds.filter(function (r) {
        return String(r.examYear) === String(year) && String(r.examMonth) === String(month);
      })[0];
      if (current) {
        document.getElementById('archiveTitle').textContent = current.title + ' 아카이브';
        document.title = current.title + ' 아카이브 · BuildIt Wiki';
        if (current.subtitle) document.getElementById('archiveSubtitle').textContent = current.subtitle;
      }
      loadMaterials();
    } catch (_) {
      list.innerHTML = '<p class="error">회차 목록을 불러오지 못했습니다.</p>';
    }
  }

  async function loadMaterials() {
    if (!year || !month) return;
    list.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var q = '/api/wiki/originals?year=' + encodeURIComponent(year) + '&month=' + encodeURIComponent(month)
        + (grade ? '&grade=' + encodeURIComponent(grade) : '');
      var res = await fetch(q);
      if (res.status === 401) {
        list.innerHTML = '<p class="muted">아카이브 자료는 로그인 후 볼 수 있습니다.</p>';
        return;
      }
      if (!res.ok) throw new Error();
      var items = await res.json();
      if (!items.length) {
        list.innerHTML = '<p class="muted">이 회차에 등록된 자료가 아직 없습니다.</p>';
        return;
      }
      list.innerHTML = items.map(materialCard).join('');
    } catch (_) {
      list.innerHTML = '<p class="error">자료를 불러오지 못했습니다.</p>';
    }
  }

  function materialCard(it) {
    var ext = (it.fileExtension || (it.originalFileName || '').split('.').pop() || '').toLowerCase();
    var canView = !!VIEWABLE_EXTS[ext];
    var view = canView
      ? '<a class="mat-act" data-member-download data-newtab="1" href="' + escapeAttr(it.viewUrl) + '">열람</a>'
      : '';
    return '<div class="mat-card">'
      + '<span class="file-badge">' + escapeHtml(it.docTypeLabel) + '</span>'
      + '<b>' + escapeHtml(it.title) + '</b>'
      + '<span class="file-meta">고' + it.grade + ' · ' + escapeHtml((ext || '').toUpperCase()) + ' · ' + fmtDay(it.createdAt) + ' 등록</span>'
      + '<span class="mat-actions">' + view
      + '<a class="mat-act primary" data-member-download href="' + escapeAttr(it.downloadUrl) + '">내려받기 ↓</a></span>'
      + '</div>';
  }

  function fmtDay(v) {
    if (!v) return '';
    var d = new Date(v);
    return (d.getMonth() + 1).toString().padStart(2, '0') + '.' + d.getDate().toString().padStart(2, '0');
  }
  function escapeHtml(v) {
    return String(v == null ? '' : v).replace(/[&<>'"]/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c];
    });
  }
  function escapeAttr(v) { return escapeHtml(v).replace(/`/g, '&#96;'); }
})();
