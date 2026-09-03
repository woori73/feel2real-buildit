/**
 * 공개 BuildIt Wiki 문항 상세(/wiki/questions/{id}).
 *
 * 화면은 2개 영역만 명확하게: [학습자료] / [이 문항에 대한 의견].
 * 내부 개념(data_kind / DISCUSSION / enum 등)은 사용자에게 노출하지 않는다.
 * 읽기는 누구나. 로그인하면 의견 작성. 학습자료 연결/업로드는 ADMIN. 권한은 서버가 최종 결정한다.
 *
 * 분류(질문/해설/출제포인트/변형문제/실제기출 …)는 사람에게 떠넘기지 않는다 —
 * 미지정 글은 그대로 저장되고 향후 Browser AI 가 자동 분류한다.
 */
(function () {
  var questionWikiId = location.pathname.split('/').filter(Boolean).pop();
  var me = null;
  var cache = null;
  var activeFilter = 'all';
  var composerMode = 'new'; // new | reply | edit
  var composerTarget = null; // parentPostId (reply) or postId (edit)

  var els = {
    context: document.getElementById('qwContext'),
    title: document.getElementById('qwTitle'),
    passageNote: document.getElementById('qwPassageNote'),
    materials: document.getElementById('qwMaterials'),
    materialAdmin: document.getElementById('qwMaterialAdmin'),
    findBtn: document.getElementById('qwFindMaterialBtn'),
    uploadBtn: document.getElementById('qwUploadMaterialBtn'),
    writeBtn: document.getElementById('qwWriteBtn'),
    loginHint: document.getElementById('qwLoginHint'),
    opinionList: document.getElementById('qwOpinionList'),
    filters: document.getElementById('qwFilters'),
    composerModal: document.getElementById('qwComposerModal'),
    composerTitle: document.getElementById('qwComposerTitle'),
    composer: document.getElementById('qwComposer'),
    postContent: document.getElementById('qwPostContent'),
    postFiles: document.getElementById('qwPostFiles'),
    composerSubmit: document.getElementById('qwComposerSubmit'),
    composerMsg: document.getElementById('qwComposerMsg'),
    findModal: document.getElementById('qwFindModal'),
    findInput: document.getElementById('qwFindInput'),
    findResults: document.getElementById('qwFindResults'),
    uploadModal: document.getElementById('qwUploadModal'),
    uploadForm: document.getElementById('qwUploadForm'),
    upLabel: document.getElementById('qwUpLabel'),
    upTitle: document.getElementById('qwUpTitle'),
    upDesc: document.getElementById('qwUpDesc'),
    upFile: document.getElementById('qwUpFile'),
    uploadSubmit: document.getElementById('qwUploadSubmit'),
    uploadMsg: document.getElementById('qwUploadMsg')
  };

  init();

  async function init() {
    me = (typeof ensureCurrentUser === 'function') ? await ensureCurrentUser() : readUser();

    els.writeBtn.addEventListener('click', function () { openComposer('new'); });
    els.composer.addEventListener('submit', onComposerSubmit);
    els.opinionList.addEventListener('click', onOpinionClick);
    els.filters.addEventListener('click', onFilterClick);

    els.findBtn.addEventListener('click', function () { openModal(els.findModal); els.findInput.focus(); });
    els.uploadBtn.addEventListener('click', function () { openModal(els.uploadModal); });
    els.findInput.addEventListener('input', debounce(runMaterialSearch, 250));
    els.findResults.addEventListener('click', onFindResultClick);
    els.uploadForm.addEventListener('submit', onUploadSubmit);

    document.querySelectorAll('[data-qw-close]').forEach(function (b) {
      b.addEventListener('click', function () { closeModals(); });
    });
    document.querySelectorAll('.qw-modal').forEach(function (m) {
      m.addEventListener('click', function (e) { if (e.target === m) closeModals(); });
    });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeModals(); });

    window.addEventListener('f2r:login', function (e) { me = (e && e.detail) || readUser(); syncAuthUi(); render(); });
    syncAuthUi();
    await load();
  }

  async function load() {
    try {
      var res = await fetch('/api/wiki/questions/' + encodeURIComponent(questionWikiId) + '/context');
      if (res.status === 404) { els.context.textContent = '문항을 찾을 수 없습니다.'; return; }
      if (!res.ok) throw new Error();
      cache = await res.json();
      render();
    } catch (_) {
      els.context.textContent = '';
      els.opinionList.innerHTML = '<p class="qw-error">불러오지 못했습니다.</p>';
    }
  }

  function isAdmin() { return !!(me && me.role === 'ADMIN'); }

  function render() {
    if (!cache) return;
    var q = cache.question;
    els.context.textContent = q.displayContext;
    els.title.textContent = q.title || (q.questionNumber + '번');
    document.title = q.displayContext + ' · BuildIt Wiki';
    if (q.passageGroupKey && (q.passageGroupQuestionNumbers || []).length > 1) {
      els.passageNote.hidden = false;
      els.passageNote.textContent = '이 지문은 ' + q.passageGroupQuestionNumbers.join('·') + '번이 함께 사용합니다.';
    }
    renderMaterials(cache.materials || []);
    renderOpinions();
  }

  /* ── 학습자료 ─────────────────────────────────────────────── */

  function renderMaterials(materials) {
    els.materialAdmin.hidden = !isAdmin();

    if (!materials.length) {
      els.materials.innerHTML = '<p class="qw-empty">아직 등록된 학습자료가 없습니다.'
        + (isAdmin() ? '' : ' 준비되는 대로 원문·해설지 등이 이곳에 올라옵니다.') + '</p>';
      return;
    }

    els.materials.innerHTML = materials.map(function (m) {
      var kindLine = esc(m.label || m.kindLabel);
      var title = esc(m.title || m.fileName || m.kindLabel);
      return '<article class="qw-material-card" data-link-id="' + m.linkId + '">'
        + '<div class="qw-material-body">'
        + '<span class="qw-material-kind">' + kindLine + '</span>'
        + '<b class="qw-material-title">' + title + '</b>'
        + (m.description ? '<span class="qw-material-desc">' + esc(m.description) + '</span>' : '')
        + '</div>'
        + '<div class="qw-material-side">'
        + '<a class="qw-material-view" href="' + escAttr(m.viewUrl) + '" target="_blank" rel="noopener">보기</a>'
        + (isAdmin() ? '<button type="button" class="qw-material-remove" data-remove-link>연결 해제</button>' : '')
        + '</div>'
        + '</article>';
    }).join('');
  }

  async function onMaterialListClick(e) {
    var card = e.target.closest('[data-link-id]');
    if (!card || !e.target.closest('[data-remove-link]')) return;
    if (!confirm('이 자료 연결을 해제할까요? (자료 자체는 삭제되지 않습니다)')) return;
    var res = await authSend('/api/admin/wiki/questions/material-links/' + card.dataset.linkId, 'DELETE');
    if (res) await load();
  }

  async function runMaterialSearch() {
    var q = els.findInput.value.trim();
    els.findResults.innerHTML = '<p class="muted">검색 중...</p>';
    try {
      var res = await fetchAuth('/api/admin/wiki/questions/materials/search?q=' + encodeURIComponent(q),
        { redirectOnAuthError: false });
      if (res.status === 401 || res.status === 403) { promptLogin(); return; }
      if (!res.ok) throw new Error();
      var items = await res.json();
      if (!items.length) {
        els.findResults.innerHTML = '<p class="muted">일치하는 자료가 없습니다.</p>';
        return;
      }
      els.findResults.innerHTML = items.map(function (it) {
        return '<button type="button" class="qw-find-row" data-kind="' + it.materialKind + '" data-id="' + it.materialId + '">'
          + '<span class="qw-find-kind">' + esc(it.kindLabel) + '</span>'
          + '<b>' + esc(it.title) + '</b>'
          + '<span class="qw-find-meta">' + esc(it.meta) + '</span>'
          + '</button>';
      }).join('');
    } catch (_) {
      els.findResults.innerHTML = '<p class="qw-error">검색에 실패했습니다.</p>';
    }
  }

  async function onFindResultClick(e) {
    var row = e.target.closest('.qw-find-row');
    if (!row) return;
    var label = prompt('이 자료의 종류를 입력하세요 (예: 상세 해설지). 비워두면 기본값을 사용합니다.', '상세 해설지');
    if (label === null) return;
    var body = { materialKind: row.dataset.kind, materialId: Number(row.dataset.id), label: label.trim() || null };
    var res = await fetchAuth('/api/admin/wiki/questions/' + questionWikiId + '/material-links',
      { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), redirectOnAuthError: false });
    if (res.status === 401 || res.status === 403) { promptLogin(); return; }
    if (res.status === 409) { alert('이미 연결된 자료입니다.'); return; }
    if (!res.ok) { alert((await res.text()) || '연결에 실패했습니다.'); return; }
    closeModals();
    await load();
  }

  async function onUploadSubmit(e) {
    e.preventDefault();
    if (!els.upFile.files.length) { els.uploadMsg.textContent = '파일을 선택해 주세요.'; return; }
    var fd = new FormData();
    fd.append('file', els.upFile.files[0]);
    if (els.upTitle.value.trim()) fd.append('title', els.upTitle.value.trim());
    if (els.upDesc.value.trim()) fd.append('description', els.upDesc.value.trim());
    if (els.upLabel.value.trim()) fd.append('docLabel', els.upLabel.value.trim());
    els.uploadSubmit.disabled = true;
    els.uploadMsg.textContent = '';
    try {
      var res = await fetchAuth('/api/admin/wiki/questions/' + questionWikiId + '/materials/upload',
        { method: 'POST', body: fd, redirectOnAuthError: false });
      if (res.status === 401 || res.status === 403) { promptLogin(); return; }
      if (!res.ok) { els.uploadMsg.textContent = (await res.text()) || '업로드에 실패했습니다.'; return; }
      els.uploadForm.reset();
      closeModals();
      await load();
    } catch (_) {
      els.uploadMsg.textContent = '업로드에 실패했습니다.';
    } finally {
      els.uploadSubmit.disabled = false;
    }
  }

  /* ── 의견 ─────────────────────────────────────────────────── */

  function allOpinions() {
    if (!cache) return [];
    return [].concat(cache.threads || [], cache.variants || [], cache.examEvidence || [])
      .sort(function (a, b) { return new Date(a.createdAt) - new Date(b.createdAt); });
  }

  function matchesFilter(p) {
    if (activeFilter === 'all') return true;
    if (activeFilter === 'VARIANT') return p.dataKind === 'GENERATED_PRACTICE';
    if (activeFilter === 'EXAM_EVIDENCE') return p.dataKind === 'EXAM_EVIDENCE';
    return p.category === activeFilter;
  }

  function renderOpinions() {
    var list = allOpinions().filter(matchesFilter);
    if (!list.length) {
      if (activeFilter !== 'all') {
        els.opinionList.innerHTML = '<p class="qw-empty">이 분류의 의견이 아직 없습니다.</p>';
        return;
      }
      els.opinionList.innerHTML = '<div class="qw-empty-cta">'
        + '<p>아직 의견이 없습니다.<br>이 문항에서 궁금하거나 중요하다고 생각한 점을 남겨보세요.</p>'
        + '<button type="button" class="qw-btn-primary" id="qwFirstBtn">+ 첫 의견 남기기</button>'
        + '</div>';
      var fb = document.getElementById('qwFirstBtn');
      if (fb) fb.addEventListener('click', function () { openComposer('new'); });
      return;
    }
    els.opinionList.innerHTML = list.map(renderOpinionCard).join('');
  }

  function roleLabel(p) { return p.participantType || '참여자'; }

  function renderOpinionCard(p) {
    var extra = '';
    if (p.dataKind === 'GENERATED_PRACTICE' && p.variantDetail) {
      extra = kvBlock([
        ['문제', p.variantDetail.questionText], ['유형', p.variantDetail.questionType],
        ['정답', p.variantDetail.answer], ['해설', p.variantDetail.explanation],
        ['근거 문장', p.variantDetail.sourceSentence], ['난이도', p.variantDetail.difficulty]
      ]);
    } else if (p.dataKind === 'EXAM_EVIDENCE' && p.examEvidence) {
      var e = p.examEvidence;
      var head = [e.schoolName, e.gradeLabel, e.examPeriod].filter(Boolean).join(' · ');
      extra = (head ? '<div class="qw-evidence-head">' + esc(head) + '</div>' : '')
        + kvBlock([
          ['사용 문장', e.relatedSentence], ['출제 유형', e.examQuestionType],
          ['출제 포인트', e.examPoint], ['설명', e.description]
        ]);
    }
    var tag = (p.category && p.category !== 'ETC') ? '<span class="qw-tag">#' + esc(p.categoryLabel) + '</span>' : '';
    var replyCount = (p.replies || []).length;

    return '<article class="qw-op-card" data-post-id="' + p.postId + '">'
      + '<div class="qw-op-meta"><span class="qw-op-role">' + esc(roleLabel(p)) + '</span>'
      + '<span class="qw-op-time">' + fmt(p.createdAt) + '</span></div>'
      + (p.title ? '<div class="qw-op-title">' + esc(p.title) + '</div>' : '')
      + '<div class="qw-op-body">' + nl2br(esc(p.content)) + '</div>'
      + extra
      + attachBlock(p)
      + '<div class="qw-op-foot">'
      + tag
      + '<span class="qw-op-actions">'
      + '<button type="button" class="qw-txt-btn" data-reply>답글' + (replyCount ? ' ' + replyCount : '') + '</button>'
      + (p.mine ? '<button type="button" class="qw-txt-btn" data-edit>수정</button>' : '')
      + (p.canManage ? '<button type="button" class="qw-txt-btn" data-del>삭제</button>' : '')
      + '</span></div>'
      + renderReplies(p.replies || [])
      + '</article>';
  }

  function renderReplies(rs) {
    if (!rs.length) return '';
    return '<div class="qw-replies">' + rs.map(function (r) {
      return '<div class="qw-reply" data-post-id="' + r.postId + '">'
        + '<div class="qw-op-meta"><span class="qw-op-role">' + esc(roleLabel(r)) + '</span>'
        + '<span class="qw-op-time">' + fmt(r.createdAt) + '</span></div>'
        + '<div class="qw-op-body">' + nl2br(esc(r.content)) + '</div>'
        + '<div class="qw-op-foot"><span class="qw-op-actions">'
        + (r.mine ? '<button type="button" class="qw-txt-btn" data-edit-reply>수정</button>' : '')
        + (r.canManage ? '<button type="button" class="qw-txt-btn" data-del-reply>삭제</button>' : '')
        + '</span></div></div>';
    }).join('') + '</div>';
  }

  function attachBlock(p) {
    var atts = p.attachments || [];
    if (!atts.length) return '';
    return '<div class="qw-attach">' + atts.map(function (a) {
      return '<a class="qw-attach-row" href="' + escAttr(a.downloadUrl) + '">'
        + esc(a.originalFileName) + ' <span class="muted">(' + mb(a.fileSize) + ')</span> 내려받기 ↓</a>';
    }).join('') + '</div>';
  }

  function kvBlock(pairs) {
    var rows = pairs.filter(function (p) { return p[1]; })
      .map(function (p) { return '<dt>' + p[0] + '</dt><dd>' + nl2br(esc(p[1])) + '</dd>'; }).join('');
    return rows ? '<dl class="qw-kv">' + rows + '</dl>' : '';
  }

  function onFilterClick(e) {
    var b = e.target.closest('.qw-chip');
    if (!b) return;
    els.filters.querySelectorAll('.qw-chip').forEach(function (c) { c.classList.toggle('active', c === b); });
    activeFilter = b.dataset.filter;
    renderOpinions();
  }

  async function onOpinionClick(e) {
    var node = e.target.closest('[data-post-id]');
    if (!node) return;
    var postId = node.dataset.postId;
    if (e.target.closest('[data-reply]')) { openComposer('reply', postId); return; }
    if (e.target.closest('[data-edit]')) { openComposer('edit', postId); return; }
    if (e.target.closest('[data-edit-reply]')) { openComposer('edit', postId); return; }
    if (e.target.closest('[data-del]') || e.target.closest('[data-del-reply]')) {
      if (!confirm('이 글을 삭제할까요?')) return;
      var res = await authSend('/api/wiki/questions/posts/' + postId, 'DELETE');
      if (res) await load();
    }
  }

  /* ── 작성 모달 ────────────────────────────────────────────── */

  function openComposer(mode, targetId) {
    if (!me) { promptLogin(); return; }
    composerMode = mode;
    composerTarget = targetId || null;
    els.composer.reset();
    els.composerMsg.textContent = '';
    els.composerSubmit.textContent = mode === 'edit' ? '수정' : (mode === 'reply' ? '답글 등록' : '등록');
    els.composerTitle.textContent = mode === 'edit' ? '의견 수정' : (mode === 'reply' ? '답글 남기기' : '의견 남기기');
    els.postFiles.closest('.qw-file-field').hidden = mode === 'edit';

    if (mode === 'edit') {
      var post = findAny(targetId);
      if (post) els.postContent.value = post.content || '';
    }
    openModal(els.composerModal);
    els.postContent.focus();
  }

  async function onComposerSubmit(e) {
    e.preventDefault();
    if (!me) { promptLogin(); return; }
    var content = els.postContent.value.trim();
    if (!content) { els.composerMsg.textContent = '내용을 입력해 주세요.'; return; }

    var url, method;
    if (composerMode === 'edit') {
      url = '/api/wiki/questions/posts/' + composerTarget; method = 'PUT';
    } else if (composerMode === 'reply') {
      url = '/api/wiki/questions/' + questionWikiId + '/posts/' + composerTarget + '/replies'; method = 'POST';
    } else {
      url = '/api/wiki/questions/' + questionWikiId + '/posts'; method = 'POST';
    }

    els.composerSubmit.disabled = true;
    try {
      var res = await fetchAuth(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: content }),
        redirectOnAuthError: false
      });
      if (res.status === 401 || res.status === 403) { promptLogin(); return; }
      if (!res.ok) { els.composerMsg.textContent = (await res.text()) || '처리에 실패했습니다.'; return; }
      var saved = await res.json();

      // 새 글/답글에 첨부가 있으면 이어서 업로드한다(가장 최근 내 글에).
      var files = els.postFiles.files;
      if (composerMode !== 'edit' && files && files.length) {
        var target = latestMinePostId(saved);
        if (target) {
          var afd = new FormData();
          for (var i = 0; i < files.length; i++) afd.append('files', files[i]);
          var ares = await fetchAuth('/api/wiki/questions/posts/' + target + '/attachments',
            { method: 'POST', body: afd, redirectOnAuthError: false });
          if (ares.ok) saved = await ares.json();
        }
      }
      cache = saved;
      closeModals();
      render();
    } catch (_) {
      els.composerMsg.textContent = '처리에 실패했습니다.';
    } finally {
      els.composerSubmit.disabled = false;
    }
  }

  function latestMinePostId(ctx) {
    var all = [];
    [].concat(ctx.threads || [], ctx.variants || [], ctx.examEvidence || []).forEach(function (p) {
      all.push(p);
      (p.replies || []).forEach(function (r) { all.push(r); });
    });
    var mine = all.filter(function (p) { return p.mine; })
      .sort(function (a, b) { return new Date(b.createdAt) - new Date(a.createdAt); });
    return mine.length ? mine[0].postId : null;
  }

  function findAny(postId) {
    var all = [];
    allOpinions().forEach(function (p) {
      all.push(p);
      (p.replies || []).forEach(function (r) { all.push(r); });
    });
    return all.find(function (p) { return String(p.postId) === String(postId); }) || null;
  }

  /* ── 공통 ─────────────────────────────────────────────────── */

  els.materials.addEventListener('click', onMaterialListClick);

  async function authSend(url, method) {
    try {
      var res = await fetchAuth(url, { method: method, redirectOnAuthError: false });
      if (res.status === 401 || res.status === 403) { promptLogin(); return false; }
      return res.ok || res.status === 204;
    } catch (_) { return false; }
  }

  function openModal(m) { closeModals(); m.hidden = false; document.body.classList.add('qw-modal-open'); }
  function closeModals() {
    document.querySelectorAll('.qw-modal').forEach(function (m) { m.hidden = true; });
    document.body.classList.remove('qw-modal-open');
  }

  function syncAuthUi() {
    els.writeBtn.hidden = false;
    els.loginHint.hidden = !!me;
    els.materialAdmin.hidden = !isAdmin();
  }

  function promptLogin() {
    alert('로그인하면 이 문항에 참여할 수 있어요.');
    if (typeof openLoginModal === 'function') openLoginModal();
  }

  function debounce(fn, ms) {
    var t;
    return function () { clearTimeout(t); t = setTimeout(fn, ms); };
  }
  function readUser() { try { return JSON.parse(sessionStorage.getItem('currentUser')); } catch (_) { return null; } }
  function fmt(v) {
    if (!v) return '';
    var d = new Date(v), diff = (Date.now() - d.getTime()) / 60000;
    if (diff < 1) return '방금 전';
    if (diff < 60) return Math.floor(diff) + '분 전';
    if (diff < 1440) return Math.floor(diff / 60) + '시간 전';
    return d.toLocaleDateString('ko-KR');
  }
  function mb(bytes) { return (Number(bytes || 0) / 1024 / 1024).toFixed(1) + 'MB'; }
  function nl2br(s) { return String(s).replace(/\n/g, '<br>'); }
  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>'"]/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c];
    });
  }
  function escAttr(v) { return esc(v).replace(/`/g, '&#96;'); }
})();
