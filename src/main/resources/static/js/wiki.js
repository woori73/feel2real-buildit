/**
 * 공개 BuildIt Wiki 허브(/wiki).
 * - 학년 탭(고3/고2/고1)으로 분석·원본을 필터
 * - 커뮤니티 3개 게시판(시험 후기 / 변형문제 / 자료 공유·요청) 최신글 미리보기
 * - 게시판 글쓰기는 로그인 게이트 후 기존 /community/posts/new 재사용(scope=wiki)
 */
(function () {
  var grade = 3;

  var gradeTabs = document.getElementById('gradeTabs');
  var gradeNote = document.getElementById('gradeNote');
  var GRADE_NOTE = {
    3: '9월 모의평가 현장 분석과 함께, 11월 수능까지 매주 유사 패턴의 새 문제를 무료로 제공합니다.',
    2: '9월 모의고사를 내신 대비 자료로 활용합니다. 지문 분석·어휘·구문·어법·내신형 변형문제를 무료로 공유합니다.',
    1: '9월 모의고사를 내신 대비 자료로 활용합니다. 지문 분석·어휘·구문·어법·내신형 변형문제를 무료로 공유합니다.'
  };

  gradeTabs.addEventListener('click', function (e) {
    var b = e.target.closest('.grade-tab');
    if (!b) return;
    gradeTabs.querySelectorAll('.grade-tab').forEach(function (t) { t.classList.toggle('active', t === b); });
    grade = Number(b.dataset.grade);
    render();
  });

  document.querySelectorAll('.board-write').forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (!readUser()) {
        alert('로그인 후 작성할 수 있습니다.');
        if (typeof openLoginModal === 'function') openLoginModal();
        return;
      }
      var board = btn.dataset.board;
      var key = btn.dataset.key;
      location.href = '/community/posts/new?board=' + encodeURIComponent(board)
        + '&return=' + encodeURIComponent('/wiki/board/' + key);
    });
  });

  render();

  function render() {
    gradeNote.textContent = GRADE_NOTE[grade] || '';
    loadOriginals();
    loadAnalyses();
    loadBoards();
  }

  async function loadOriginals() {
    var box = document.getElementById('originalList');
    box.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/wiki/originals?grade=' + grade);
      if (!res.ok) throw new Error();
      var items = await res.json();
      if (!items.length) {
        box.innerHTML = '<p class="muted">아직 등록된 원본이 없습니다. 준비되는 대로 이곳에 올라옵니다.</p>';
        return;
      }
      box.innerHTML = items.map(function (it) {
        return '<a class="file-card" href="' + escapeAttr(it.downloadUrl) + '">'
          + '<span class="file-badge">' + escapeHtml(it.docTypeLabel) + '</span>'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + '<span class="file-meta">' + escapeHtml(it.examLabel) + ' · 고' + it.grade + ' · PDF</span>'
          + '<span class="file-dl">내려받기 ↓</span>'
          + '</a>';
      }).join('');
    } catch (_) {
      box.innerHTML = '<p class="error">원본 목록을 불러오지 못했습니다.</p>';
    }
  }

  async function loadAnalyses() {
    var box = document.getElementById('analysisList');
    box.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/analyzes?grade=' + grade);
      if (!res.ok) throw new Error();
      var items = (await res.json()).slice(0, 6);
      if (!items.length) {
        box.innerHTML = '<p class="muted">해당 학년의 공개 분석 자료가 아직 없습니다.</p>';
        return;
      }
      box.innerHTML = items.map(function (it) {
        return '<a class="analysis-card" href="' + escapeAttr(it.detailUrl) + '">'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + '<span class="analysis-meta">' + escapeHtml(it.schoolName) + ' · ' + it.grade + '학년 · '
          + escapeHtml(it.semester) + ' · ' + escapeHtml(it.examType) + '</span>'
          + '<span class="analysis-badge">' + escapeHtml((it.subject && (it.subject.nameKo || it.subject.nameEn)) || '영어') + '</span>'
          + '</a>';
      }).join('');
    } catch (_) {
      box.innerHTML = '<p class="error">분석 자료를 불러오지 못했습니다.</p>';
    }
  }

  function loadBoards() {
    document.querySelectorAll('.board-col').forEach(function (col) {
      loadBoard(col);
    });
  }

  async function loadBoard(col) {
    var board = col.dataset.board;
    var list = col.querySelector('.board-col-list');
    list.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/open-community/posts?scope=wiki&boardType=' + encodeURIComponent(board) + '&sort=latest');
      if (!res.ok) throw new Error();
      var posts = (await res.json()).slice(0, 4);
      if (!posts.length) {
        list.innerHTML = '<p class="muted">아직 글이 없어요. 첫 글을 남겨보세요.</p>';
        return;
      }
      list.innerHTML = posts.map(function (p) {
        return '<a class="post-row" href="/community/posts/' + p.postId + '">'
          + '<span class="post-title">' + escapeHtml(p.title) + '</span>'
          + '<span class="post-sub">' + escapeHtml(p.authorName) + ' · 답변 ' + Number(p.answerCount || 0) + '</span>'
          + '</a>';
      }).join('');
    } catch (_) {
      list.innerHTML = '<p class="error">목록을 불러오지 못했습니다.</p>';
    }
  }

  function readUser() { try { return JSON.parse(sessionStorage.getItem('currentUser')); } catch (_) { return null; } }
  function escapeHtml(v) {
    return String(v || '').replace(/[&<>'"]/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c];
    });
  }
  function escapeAttr(v) { return escapeHtml(v).replace(/`/g, '&#96;'); }
})();
