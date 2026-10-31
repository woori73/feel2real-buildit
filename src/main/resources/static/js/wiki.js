/**
 * 공개 BuildIt Wiki 허브(/wiki).
 * - 학년 탭(고1/고2/고3)으로 모의고사 원본·분석·학습 자료를 필터
 * - 메인 진입 시 /api/wiki/home-feed 1회로 상단 실시간 영역(최신 업데이트·인기 다운로드·최근 문항·WIKI LIVE·최근 회원)을 채움
 * - WIKI LIVE / 인기 다운로드는 경량 폴링으로 갱신 (서버 푸시 미사용, sinceId 증분)
 * - 자료는 새 Wiki 자료실 시스템(/api/wiki/originals, /api/wiki/analysis-materials, /api/wiki/learning-materials)
 * - 게시판 글쓰기/상세는 /wiki 전용 화면(/wiki/board/{key}/new, /wiki/posts/{id})
 */
(function () {
  var grade = 1;
  var entryGrade = Number(new URLSearchParams(location.search).get('grade'));
  if ([1,2,3].indexOf(entryGrade) >= 0) grade = entryGrade;
  var liveMaxId = 0;
  var LIVE_POLL_MS = 8000;
  var POPULAR_POLL_MS = 45000;

  var gradeTabs = document.getElementById('gradeTabs');
  gradeTabs.querySelectorAll('.grade-tab').forEach(function (tab) { tab.classList.toggle('active', Number(tab.dataset.grade) === grade); });
  var gradeNote = document.getElementById('gradeNote');

  var LEARNING_TYPE_LABEL = { VOCAB: '어휘', WORKBOOK: 'Workbook', VARIANT: '변형문제', WEEKLY: 'WEEKLY', ETC: '기타' };
  var UPDATE_KIND_LABEL = { ORIGINAL: '모의고사 원본', ANALYSIS: '분석 자료', LEARNING: '학습 자료' };
  // 학년 안내 문구 — 회차를 특정하지 않는 기본 문구. 현재 회차가 로드되면 월 표기를 앞에 붙인다.
  var GRADE_NOTE = {
    1: '모의고사를 내신 대비 자료로 활용합니다. 지문 분석·어휘·구문·어법·내신형 변형문제를 무료로 공유합니다.',
    2: '모의고사를 내신 대비 자료로 활용합니다. 지문 분석·어휘·구문·어법·내신형 변형문제를 무료로 공유합니다.',
    3: '모의평가 현장 분석과 함께, 11월 수능까지 매주 유사 패턴의 새 문제를 무료로 제공합니다.'
  };
  var currentRound = null;
  var VIEWABLE_EXTS = { html: 1, htm: 1, png: 1, jpg: 1, jpeg: 1, webp: 1 };

  // ── 학년 탭 ──────────────────────────────────────────────
  gradeTabs.addEventListener('click', function (e) {
    var b = e.target.closest('.grade-tab');
    if (!b) return;
    gradeTabs.querySelectorAll('.grade-tab').forEach(function (t) { t.classList.toggle('active', t === b); });
    grade = Number(b.dataset.grade);
    renderGradeLists();
  });

  // ── 회원 전용 다운로드/열람 게이트 (문서 전역에 한 번만) ────
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
    alert('자료 다운로드는 로그인 회원만 이용할 수 있습니다.');
    if (typeof window.openLoginModal === 'function') window.openLoginModal();
  });

  // ── 게시판 글쓰기 게이트 ─────────────────────────────────
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

  // ── WIKI LIVE 작성 ──────────────────────────────────────
  var liveForm = document.getElementById('liveForm');
  var liveInput = document.getElementById('liveInput');
  var liveMsg = document.getElementById('liveMsg');
  liveForm.addEventListener('submit', async function (e) {
    e.preventDefault();
    var text = (liveInput.value || '').trim();
    if (!text) { liveMsg.textContent = '내용을 입력해 주세요.'; return; }
    document.getElementById('liveSubmit').disabled = true;
    try {
      var res = await fetch('/api/wiki/live-messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ message: text })
      });
      if (res.status === 401) {
        liveMsg.textContent = '로그인 후 한 줄 인사를 남길 수 있습니다.';
        if (typeof window.openLoginModal === 'function') window.openLoginModal();
        return;
      }
      if (res.status === 429) { liveMsg.textContent = '잠시 후 다시 시도해 주세요.'; return; }
      if (!res.ok) { liveMsg.textContent = '등록하지 못했습니다.'; return; }
      liveInput.value = '';
      liveMsg.textContent = '';
      await pollLive();
    } catch (_) {
      liveMsg.textContent = '등록하지 못했습니다.';
    } finally {
      document.getElementById('liveSubmit').disabled = false;
    }
  });

  document.getElementById('liveMore').addEventListener('click', loadLiveHistory);

  // 업로드 UI(wiki_admin_upload.js / wiki_learning_upload.js)가 현재 학년을 읽도록 노출.
  window.wikiHub = {
    currentGrade: function () { return grade; },
    reloadOriginals: loadOriginals,
    reloadAnalyses: loadAnalyses,
    reloadLearning: loadLearningMaterials,
  };

  // 관리자 자료 관리(수정/삭제) — 원본·분석 목록 공용 모듈에 위임.
  if (window.WikiAdminManage) {
    window.WikiAdminManage.bind(document.getElementById('originalList'), {
      onEdit: function (kind, id) {
        var item = hubOriginalsById[id];
        if (item && window.wikiAdminUpload) window.wikiAdminUpload.edit('original', item);
      },
      reload: loadOriginals,
    });
    window.WikiAdminManage.bind(document.getElementById('analysisList'), {
      onEdit: function (kind, id) {
        var item = hubAnalysesById[id];
        if (item && window.wikiAdminUpload) window.wikiAdminUpload.edit('analysis', item);
      },
      reload: loadAnalyses,
    });
  }

  // ── 부팅 ────────────────────────────────────────────────
  renderGradeLists();
  loadBoards();
  loadHomeFeed();
  loadRounds();
  recordVisit();
  setInterval(pollLive, LIVE_POLL_MS);
  setInterval(loadPopularDownloads, POPULAR_POLL_MS);

  function renderGradeLists() {
    var note = GRADE_NOTE[grade] || '';
    if (currentRound && grade !== 3) {
      note = currentRound.examMonth + '월 ' + note;
    }
    gradeNote.textContent = note;
    loadOriginals();
    loadAnalyses();
    loadLearningMaterials();
  }

  /** /wiki 방문 집계 비컨. 같은 세션·같은 날 중복은 서버(HttpSession)가 무시한다. */
  function recordVisit() {
    try {
      fetch('/api/wiki/visits', { method: 'POST', credentials: 'same-origin', keepalive: true }).catch(function () {});
    } catch (_) { /* 집계 실패는 무시 */ }
  }

  // ── 메인 aggregate ──────────────────────────────────────
  async function loadHomeFeed() {
    try {
      var res = await fetch('/api/wiki/home-feed');
      if (!res.ok) throw new Error();
      var feed = await res.json();
      var reviewLists = await Promise.all([1, 2].map(function (g) {
        return fetch('/api/wiki/analysis-materials?grade=' + g, { credentials: 'same-origin', cache: 'no-store' })
          .then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; });
      }));
      var reviews = reviewLists.reduce(function (all, list) { return all.concat(Array.isArray(list) ? list : []); }, [])
        .filter(function (it) { return /10월|10모/.test(it.title || '') && /복습/.test(it.title || '') && !/검토본|이전 편집|시안/.test(it.title || ''); })
        .map(function (it) {
          var q = String(it.title).match(/(\d+)(?:[~～-](\d+))?번/);
          return { title: '2026년 고' + it.grade + ' 10모 ' + (q ? q[1] + (q[2] ? '~' + q[2] : '') + '번 ' : '') + '복습과제',
            grade: it.grade, kind: 'review', isNew: true, updatedAt: it.updatedAt || it.createdAt,
            href: '/wiki?grade=' + it.grade + '&month=10&material=' + encodeURIComponent(it.wikiAnalysisMaterialId) + (q ? '&question=' + q[1] : '') + '#review' };
        });
      renderLatestUpdates(reviews.concat((feed.latestUpdates || []).filter(function (it) {
        return !/검토본|이전 편집|시안|복습/.test(it.title || '');
      })).sort(function (a, b) { return new Date(b.updatedAt) - new Date(a.updatedAt); }).slice(0, 8));
      renderRecentThreads(feed.latestThreads || []);
      renderRecentMembers(feed.recentMembers || []);
      renderLive(feed.liveMessages || []);
      renderPopular(feed.popularDownloads || null);
    } catch (_) {
      document.getElementById('latestUpdates').innerHTML = '<p class="error">최신 업데이트를 불러오지 못했습니다.</p>';
    }
  }

  function renderLatestUpdates(items) {
    var box = document.getElementById('latestUpdates');
    if (!items.length) {
      box.innerHTML = '<p class="muted">최근 등록된 자료가 아직 없습니다.</p>';
      return;
    }
    box.innerHTML = items.map(function (it) {
      var kind = it.kind === 'review' ? '복습과제' : UPDATE_KIND_LABEL[it.kind] || '자료';
      var g = it.grade ? '고' + it.grade + ' · ' : '';
      return '<a class="update-row' + (it.isNew ? ' is-new' : '') + '" href="' + escapeAttr(it.href) + '">'
        + (it.isNew ? '<span class="new-badge">NEW</span>' : '<span class="new-badge ghost">·</span>')
        + '<span class="update-title">' + escapeHtml(it.title) + '</span>'
        + '<span class="update-sub">' + escapeHtml(g + kind) + ' · ' + relTime(it.updatedAt) + '</span>'
        + '</a>';
    }).join('');
  }

  function renderRecentThreads(items) {
    var box = document.getElementById('recentThreads');
    if (!items.length) {
      box.innerHTML = '<p class="muted">아직 문항 분석 활동이 없습니다.</p>';
      return;
    }
    box.innerHTML = items.map(function (it) {
      return '<a class="thread-row" href="' + escapeAttr(it.href) + '">'
        + '<b>고' + it.grade + ' ' + escapeHtml(it.examLabel) + ' ' + it.questionNumber + '번</b>'
        + '<span>분석 · 해설 · Insight · AI 제작</span>'
        + '</a>';
    }).join('');
  }

  function renderRecentMembers(items) {
    var box = document.getElementById('recentMembers');
    if (!items.length) { box.innerHTML = '<span class="muted">-</span>'; return; }
    box.innerHTML = items.map(function (m) {
      return '<span class="member-chip">' + escapeHtml(m.displayName) + '</span>';
    }).join('');
  }

  // ── 회차(연도·월) — 히어로·해설 자료·아카이브 ─────────────
  async function loadRounds() {
    try {
      var res = await fetch('/api/wiki/rounds/current');
      if (!res.ok) return;
      currentRound = await res.json();
      renderHero(currentRound);
      var block = document.getElementById('roundBlock');
      if (block) block.hidden = false;
      renderGradeLists();
      loadRoundMaterials(currentRound);
      var listRes = await fetch('/api/wiki/rounds');
      if (listRes.ok) renderArchiveOptions(await listRes.json(), currentRound);
    } catch (_) { /* 회차 메타 실패 시 기존 허브만 표시 */ }
  }

  function renderHero(round) {
    var hero = document.getElementById('examHero');
    if (!hero || !round) return;
    document.getElementById('examHeroTitle').textContent = round.title || '';
    document.title = (round.title || '모의고사위키') + ' · BuildIt Wiki';
    var img = document.getElementById('examHeroImg');
    var text = document.getElementById('examHeroText');
    if (round.bannerPath) {
      // 배너 안에 문구가 이미 들어 있어 텍스트를 겹치지 않는다. alt로만 제공한다.
      img.src = round.bannerPath;
      img.alt = (round.title || '') + (round.subtitle ? ' — ' + round.subtitle : '');
      img.hidden = false;
      text.hidden = true;
    } else {
      img.hidden = true;
      document.getElementById('examHeroTextTitle').textContent = round.title || '';
      document.getElementById('examHeroSubtitle').textContent = round.subtitle || '';
      text.hidden = false;
    }
    hero.hidden = false;
  }

  function renderArchiveOptions(rounds, current) {
    var block = document.getElementById('roundBlock');
    var select = document.getElementById('archiveSelect');
    if (!block || !select) return;
    var past = (rounds || []).filter(function (r) {
      return !(current && r.examYear === current.examYear && r.examMonth === current.examMonth);
    });
    select.innerHTML = past.map(function (r) {
      return '<option value="' + r.examYear + '-' + r.examMonth + '">' + escapeHtml(r.title) + '</option>';
    }).join('');
    select.disabled = !past.length;
    document.getElementById('archiveGo').disabled = !past.length;
    block.hidden = false;
  }

  document.getElementById('archiveGo') && document.getElementById('archiveGo').addEventListener('click', function () {
    var v = document.getElementById('archiveSelect').value;
    if (!v) return;
    var parts = v.split('-');
    location.href = '/wiki/archive?year=' + encodeURIComponent(parts[0]) + '&month=' + encodeURIComponent(parts[1]);
  });

  async function loadRoundMaterials(round) {
    var box = document.getElementById('roundMaterials');
    var title = document.getElementById('roundTitle');
    if (!box || !round) return;
    if (title) title.textContent = round.title;
    try {
      var res = await fetch('/api/wiki/originals?year=' + round.examYear + '&month=' + round.examMonth);
      if (res.status === 401) {
        box.innerHTML = '<p class="muted">해설 자료는 로그인 후 학년별로 볼 수 있습니다.</p>';
        return;
      }
      if (!res.ok) throw new Error();
      var items = await res.json();
      // 이번 회차 섹션은 해설(문제풀이 해설·구문독해) 중심이다. 문제지·정답지는 아래 원본 섹션과 아카이브에서 본다.
      var guides = items.filter(function (it) { return it.docType === 'EXPLANATION' || it.docType === 'SYNTAX'; });
      if (!guides.length) {
        box.innerHTML = '<p class="muted">이번 회차 해설 자료가 준비 중입니다. 업로드되는 대로 학년별로 이곳에 표시됩니다.</p>';
        return;
      }
      box.innerHTML = [1, 2, 3].map(function (g) {
        var rows = guides.filter(function (it) { return it.grade === g; });
        if (!rows.length) return '';
        return '<div class="round-grade-group"><h3>고' + g + '</h3><div class="round-mat-list">'
          + rows.map(roundMaterialCard).join('') + '</div></div>';
      }).join('');
    } catch (_) {
      box.innerHTML = '<p class="error">해설 자료를 불러오지 못했습니다.</p>';
    }
  }

  function roundMaterialCard(it) {
    var ext = (it.fileExtension || (it.originalFileName || '').split('.').pop() || '').toLowerCase();
    var canView = !!VIEWABLE_EXTS[ext];
    var view = canView
      ? '<a class="mat-act" data-member-download data-newtab="1" href="' + escapeAttr(it.viewUrl) + '">열람</a>'
      : '';
    return '<div class="mat-card">'
      + '<span class="file-badge">' + escapeHtml(it.docTypeLabel) + '</span>'
      + '<b>' + escapeHtml(it.title) + '</b>'
      + '<span class="file-meta">' + escapeHtml((ext || '').toUpperCase()) + ' · ' + fmtDay(it.createdAt) + ' 등록</span>'
      + '<span class="mat-actions">' + view
      + '<a class="mat-act primary" data-member-download href="' + escapeAttr(it.downloadUrl) + '">내려받기 ↓</a></span>'
      + '</div>';
  }

  // ── WIKI LIVE ───────────────────────────────────────────
  function renderLive(items) {
    var box = document.getElementById('liveMessages');
    if (!items.length) {
      box.innerHTML = '<li class="muted">첫 한 줄을 남겨보세요.</li>';
    } else {
      box.innerHTML = items.map(liveRow).join('');
    }
    items.forEach(function (m) { if (m.id > liveMaxId) liveMaxId = m.id; });
    document.getElementById('liveMore').hidden = items.length < 6;
  }

  function liveRow(m) {
    // m.message 는 서버에서 이미 HTML escape 되어 저장/전달된다 — 여기서 재이스케이프하지 않는다.
    return '<li class="live-row">'
      + '<span class="live-name">' + escapeHtml(m.displayName) + '</span>'
      + '<span class="live-text">' + String(m.message == null ? '' : m.message) + '</span>'
      + '<span class="live-time">' + relTime(m.createdAt) + '</span>'
      + '</li>';
  }

  async function pollLive() {
    try {
      var res = await fetch('/api/wiki/live-messages?sinceId=' + liveMaxId);
      if (!res.ok) return;
      var items = await res.json();
      if (!items.length) return;
      var box = document.getElementById('liveMessages');
      var placeholder = box.querySelector('.muted');
      if (placeholder) box.innerHTML = '';
      items.forEach(function (m) {
        box.insertAdjacentHTML('beforeend', liveRow(m));
        if (m.id > liveMaxId) liveMaxId = m.id;
      });
      while (box.children.length > 40) box.removeChild(box.firstChild);
    } catch (_) { /* 폴링 실패는 조용히 무시 */ }
  }

  async function loadLiveHistory() {
    try {
      var res = await fetch('/api/wiki/live-messages');
      if (!res.ok) return;
      var items = await res.json();
      var box = document.getElementById('liveMessages');
      box.innerHTML = items.length ? items.map(liveRow).join('') : '<li class="muted">첫 한 줄을 남겨보세요.</li>';
      liveMaxId = 0;
      items.forEach(function (m) { if (m.id > liveMaxId) liveMaxId = m.id; });
      document.getElementById('liveMore').hidden = true;
    } catch (_) {}
  }

  // ── 인기 다운로드 TOP 10 ────────────────────────────────
  async function loadPopularDownloads() {
    try {
      var res = await fetch('/api/wiki/popular-downloads');
      if (!res.ok) return;
      renderPopular(await res.json());
    } catch (_) {}
  }

  function renderPopular(data) {
    var box = document.getElementById('popularDownloads');
    var period = document.getElementById('popularPeriod');
    if (!data || !data.items || !data.items.length) {
      box.innerHTML = '<li class="muted">최근 다운로드 데이터가 아직 충분하지 않습니다.</li>';
      return;
    }
    if (period) period.textContent = (data.periodLabel || '') + ' 기준';
    box.innerHTML = data.items.filter(function (it) { return /복습|자습|변형문제|WORKBOOK/i.test(it.title || '') && it.materialId && it.grade; }).map(function (it) {
      var g = it.grade ? '고' + it.grade : '학년 미상';
      var q = String(it.title || '').match(/(\d+)(?:[~～-]\d+)?번/);
      var href = '/wiki?grade=' + encodeURIComponent(it.grade) + '&month=' + (/9월|9모/.test(it.title || '') ? 9 : 10) + '&material=' + encodeURIComponent(it.materialId) + (q ? '&question=' + q[1] : '') + '#review';
      return '<li class="popular-row' + (it.rank <= 3 ? ' top' : '') + '">'
        + '<span class="pop-rank">' + String(it.rank).padStart(2, '0') + '</span>'
        + '<span class="pop-trend ' + trendClass(it.trend) + '">' + trendLabel(it.trend, it.trendAmount) + '</span>'
        + '<a class="pop-main" href="' + escapeAttr(href) + '">'
        + '<span class="pop-cat">' + escapeHtml(g + ' · ' + (it.categoryLabel || '')) + '</span>'
        + '<span class="pop-title">' + escapeHtml(it.title) + '</span>'
        + '</a>'
        + '<span class="pop-count">' + it.downloadCount + '회</span>'
        + '</li>';
    }).join('');
  }

  function trendClass(t) {
    return t === 'UP' ? 'up' : t === 'DOWN' ? 'down' : t === 'NEW' ? 'new' : 'same';
  }
  function trendLabel(t, n) {
    if (t === 'NEW') return 'NEW';
    if (t === 'UP') return '▲' + n;
    if (t === 'DOWN') return '▼' + n;
    return '―';
  }

  // ── 자료 목록 (학년별) ──────────────────────────────────
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

  var hubOriginalsById = {};
  var hubAnalysesById = {};

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
      var admin = window.WikiAdminManage ? await window.WikiAdminManage.isAdmin() : false;
      hubOriginalsById = {};
      box.innerHTML = items.map(function (it) {
        hubOriginalsById[it.wikiExamOriginalId] = it;
        var card = '<a class="file-card" data-member-download href="' + escapeAttr(it.downloadUrl) + '">'
          + '<span class="file-badge">' + escapeHtml(it.docTypeLabel) + '</span>'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + '<span class="file-meta">' + escapeHtml(it.examLabel) + ' · 고' + it.grade + ' · ' + fileMeta(it) + '</span>'
          + '<span class="file-dl">내려받기 ↓</span>'
          + '</a>';
        if (!admin) return card;
        return '<div class="file-card-wrap">' + card + window.WikiAdminManage.buttonsHtml('original', it.wikiExamOriginalId) + '</div>';
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
      var items = (await res.json()).filter(function (it) { return /실전|예상/.test(it.title || '') && !/10월|10모/.test((it.title || '') + ' ' + (it.examType || '')) && !/이전 편집 시안|검토본/.test(it.title || ''); }).slice(0, 6);
      if (!items.length) {
        box.innerHTML = '<p class="muted">해당 학년의 실전예상문제가 아직 등록되지 않았습니다.</p>';
        return;
      }
      var adminA = window.WikiAdminManage ? await window.WikiAdminManage.isAdmin() : false;
      hubAnalysesById = {};
      box.innerHTML = items.map(function (it) {
        hubAnalysesById[it.wikiAnalysisMaterialId] = it;
        var where = (it.schoolName || '전국') + ' · ' + it.grade + '학년 · ' + it.year + ' ' + it.semester + ' ' + it.examType;
        var review = /복습|자습/.test(it.title || '') && /\.html?$/i.test(it.originalFileName || '');
        var q = String(it.title || '').match(/(\d+)(?:[~～-]\d+)?번/);
        var href = review ? '/wiki?grade=' + encodeURIComponent(it.grade) + '&month=' + (/9월|9모/.test(it.title || '') ? 9 : 10) + (q ? '&question=' + q[1] : '') + '#review' : it.downloadUrl;
        var card = '<a class="analysis-card" ' + (review ? '' : 'data-member-download ') + 'href="' + escapeAttr(href) + '">'
          + '<b>' + escapeHtml(it.title) + '</b>'
          + '<span class="analysis-meta">' + escapeHtml(where) + '</span>'
          + '<span class="analysis-badge">' + escapeHtml(it.subject || '영어') + ' · ' + fileMeta(it) + '</span>'
          + '</a>';
        if (!adminA) return card;
        return '<div class="analysis-card-wrap">' + card + window.WikiAdminManage.buttonsHtml('analysis', it.wikiAnalysisMaterialId) + '</div>';
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

  // ── helpers ─────────────────────────────────────────────
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
  function relTime(v) {
    if (!v) return '';
    var then = new Date(v).getTime();
    if (isNaN(then)) return '';
    var diff = Math.floor((Date.now() - then) / 1000);
    if (diff < 60) return '방금';
    if (diff < 3600) return Math.floor(diff / 60) + '분 전';
    if (diff < 86400) return Math.floor(diff / 3600) + '시간 전';
    if (diff < 604800) return Math.floor(diff / 86400) + '일 전';
    return fmtDay(v);
  }
  function escapeHtml(v) {
    return String(v == null ? '' : v).replace(/[&<>'"]/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c];
    });
  }
  function escapeAttr(v) { return escapeHtml(v).replace(/`/g, '&#96;'); }
})();

