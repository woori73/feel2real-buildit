/**
 * 공개 BuildIt Wiki 허브(/wiki).
 * - 학년 탭(고3/고2/고1)으로 모의고사 원본·분석 자료를 필터
 * - 커뮤니티 3개 게시판(시험 후기 / 변형문제 / 자료 공유·요청) 최신글 미리보기
 * - 자료는 새 Wiki 자료실 시스템(/api/wiki/originals, /api/wiki/analysis-materials) — /analyzes 재사용 안 함
 * - 게시판 글쓰기/상세는 /wiki 전용 화면(/wiki/board/{key}/new, /wiki/posts/{id})
 */
(function () {
  var grade = 3;

  var gradeTabs = document.getElementById('gradeTabs');
  var gradeNote = document.getElementById('gradeNote');
  var LEARNING_TYPE_LABEL = { VOCAB: '어휘', WORKBOOK: 'Workbook', VARIANT: '변형문제', ETC: '기타' };
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
    document.addEventListener('click', async function (e) {
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

  render();
  });

  document.querySelectorAll('.board-write').forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (!readUser()) {
        alert('로그인 후 작성할 수 있습니다.');
        if (typeof openLoginModal === 'function') openLoginModal();
        return;
      }
      location.href = '/wiki/board/' + encodeURIComponent(btn.dataset.key) + '/new';
    });
  });

  render();
  recordVisit();

  // 업로드 UI(wiki_admin_upload.js / wiki_learning_upload.js)가 현재 학년을 읽고
  // 업로드 후 목록만 새로고침하도록 노출.
  window.wikiHub = {
    currentGrade: function () { return grade; },
    reloadOriginals: loadOriginals,
    reloadAnalyses: loadAnalyses,
    reloadLearning: loadLearningMaterials,
  };

  function render() {
    gradeNote.textContent = GRADE_NOTE[grade] || '';
    loadOriginals();
    loadAnalyses();
    loadLearningMaterials();
    loadBoards();
  }

  /** /wiki 방문 집계 비컨. 같은 세션·같은 날 중복은 서버(HttpSession)가 무시한다. */
  function recordVisit() {
    try {
      fetch('/api/wiki/visits', { method: 'POST', credentials: 'same-origin', keepalive: true }).catch(function () {});
    } catch (_) { /* 집계 실패는 무시 */ }
  }

  async function loadLearningMaterials() {
    var box = document.getElementById('learningList');
    if (!box) return;
    box.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/wiki/learning-materials?grade=' + grade);
      if (!res.ok) throw new Error();
      var items = (await res.json()).slice(0, 6);
      if (!items.length) {
        box.innerHTML = '<p class="muted">이 학년의 학습 자료가 아직 없습니다. 첫 자료를 올려보세요.</p>';
        return;
      }
      // 카드 클릭 = 즉시 다운로드가 아니라 Wiki 안의 상세/미리보기 화면으로 이동한다.
      box.innerHTML = items.map(function (it) {
        var label = it.dataTypeLabel || LEARNING_TYPE_LABEL[it.dataType] || '기타';
        return '<a class="learning-card" href="/wiki/learning/' + encodeURIComponent(it.wikiLearningMaterialId) + '">'
          + '<span class="learning-badge">' + escapeHtml(label) + '</span>'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + '<span class="learning-meta">고' + it.grade + ' · ' + escapeHtml(it.uploaderName || '회원') + ' · ' + fileMeta(it) + '</span>'
          + '</a>';
      }).join('');
    } catch (_) {
      box.innerHTML = '<p class="error">학습 자료를 불러오지 못했습니다.</p>';
    }
  }

  async function loadOriginals() {
    var box = document.getElementById('originalList');
    box.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/wiki/originals?grade=' + grade);
      if (!res.ok) throw new Error();
      var items = (await res.json()).slice(0, 6);
      if (!items.length) {
        box.innerHTML = '<p class="muted">아직 등록된 원본이 없습니다. 준비되는 대로 이곳에 올라옵니다.</p>';
        return;
      }
      box.innerHTML = items.map(function (it) {
        return '<a class="file-card" data-member-download href="' + escapeAttr(it.downloadUrl) + '">'
          + '<span class="file-badge">' + escapeHtml(it.docTypeLabel) + '</span>'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + '<span class="file-meta">' + escapeHtml(it.examLabel) + ' · 고' + it.grade + ' · ' + fileMeta(it) + '</span>'
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
      var res = await fetch('/api/wiki/analysis-materials?grade=' + grade);
      if (!res.ok) throw new Error();
      var items = (await res.json()).slice(0, 6);
      if (!items.length) {
        box.innerHTML = '<p class="muted">해당 학년의 공개 분석 자료가 아직 없습니다.</p>';
        return;
      }
      box.innerHTML = items.map(function (it) {
        var where = (it.schoolName || '전국') + ' · ' + it.grade + '학년 · ' + it.year + ' ' + it.semester + ' ' + it.examType;
        return '<a class="analysis-card" data-member-download href="' + escapeAttr(it.downloadUrl) + '">'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + '<span class="analysis-meta">' + escapeHtml(where) + '</span>'
          + '<span class="analysis-badge">' + escapeHtml(it.subject || '영어') + ' · ' + fileMeta(it) + '</span>'
          + '</a>';
      }).join('');
    } catch (_) {
      box.innerHTML = '<p class="error">분석 자료를 불러오지 못했습니다.</p>';
    }
  }

  function loadBoards() {
    document.querySelectorAll('.board-col').forEach(loadBoard);
  }

  async function loadBoard(col) {
    var board = col.dataset.board;
    var list = col.querySelector('.board-col-list');
    list.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/open-community/posts?scope=wiki&boardType=' + encodeURIComponent(board) + '&sort=latest');
      if (!res.ok) throw new Error();
      var posts = (await res.json()).slice(0, 5);
      if (!posts.length) {
        list.innerHTML = '<p class="muted">아직 글이 없어요. 첫 글을 남겨보세요.</p>';
        return;
      }
      list.innerHTML = posts.map(function (p) {
        return '<a class="post-row" href="/wiki/posts/' + p.postId + '">'
          + '<span class="post-title">' + escapeHtml(p.title) + '</span>'
          + '<span class="post-sub">' + fmtDay(p.createdAt) + ' · ' + escapeHtml(p.authorName) + '</span>'
          + '</a>';
      }).join('');
    } catch (_) {
      list.innerHTML = '<p class="error">목록을 불러오지 못했습니다.</p>';
    }
  }

  function fileMeta(it) {
    var ext = (it.originalFileName || '').split('.').pop().toUpperCase();
    var mb = it.fileSize ? (it.fileSize / (1024 * 1024)).toFixed(1) + 'MB' : '';
    return [ext, mb].filter(Boolean).join(' · ');
  }
  function readUser() { try { return JSON.parse(sessionStorage.getItem('currentUser')); } catch (_) { return null; } }
  function fmtDay(v) {
    if (!v) return '';
    var d = new Date(v);
    return (d.getMonth() + 1).toString().padStart(2, '0') + '.' + d.getDate().toString().padStart(2, '0');
  }
  function escapeHtml(v) {
    return String(v || '').replace(/[&<>'"]/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c];
    });
  }
  function escapeAttr(v) { return escapeHtml(v).replace(/`/g, '&#96;'); }
})();
