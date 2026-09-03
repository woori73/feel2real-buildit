/**
 * 공개 BuildIt Wiki 문항 상세(/wiki/questions/{id}).
 *
 * 구조: 문맥 → 학습자료 → (AI 요약) → THREAD → 변형문제 → 기출 THREAD.
 * 데이터는 성격별로 나뉘어 온다: threads(토론) / variants(사용자 제작 문제) / examEvidence(실제 기출).
 * 읽기는 누구나, 작성/답글/수정/삭제는 로그인 후. 권한은 서버가 최종 결정한다.
 */
(function () {
  var CATEGORY = {
    QUESTION: '질문', INTERPRETATION: '해석·구문', GRAMMAR: '어법', VOCAB: '어휘',
    EXAM_POINT: '출제포인트', PREDICTED: '예상문제', VARIANT: '변형문제', SOLUTION: '풀이·해설',
    EXPLANATION_IMPROVEMENT: '해설 개선', CLASS_INSIGHT: '수업 인사이트', ETC: '기타'
  };

  var questionWikiId = location.pathname.split('/').filter(Boolean).pop();
  var me = null;
  var cache = null;
  var activeFilter = 'all';
  var editingPostId = null;
  var replyParentId = null;

  var els = {
    context: document.getElementById('qwContext'),
    title: document.getElementById('qwTitle'),
    passageNote: document.getElementById('qwPassageNote'),
    materials: document.getElementById('qwMaterials'),
    aiSection: document.getElementById('qwAiSummarySection'),
    aiBox: document.getElementById('qwAiSummary'),
    filters: document.getElementById('qwFilters'),
    writeBtn: document.getElementById('qwWriteBtn'),
    composer: document.getElementById('qwComposer'),
    category: document.getElementById('qwCategory'),
    dataKind: document.getElementById('qwDataKind'),
    postTitle: document.getElementById('qwPostTitle'),
    postContent: document.getElementById('qwPostContent'),
    variantFields: document.getElementById('qwVariantFields'),
    evidenceFields: document.getElementById('qwEvidenceFields'),
    composerCancel: document.getElementById('qwComposerCancel'),
    composerSubmit: document.getElementById('qwComposerSubmit'),
    composerMsg: document.getElementById('qwComposerMsg'),
    loginHint: document.getElementById('qwLoginHint'),
    threadList: document.getElementById('qwThreadList'),
    variantList: document.getElementById('qwVariantList'),
    evidenceList: document.getElementById('qwEvidenceList')
  };

  Object.keys(CATEGORY).forEach(function (key) {
    var opt = document.createElement('option');
    opt.value = key;
    opt.textContent = CATEGORY[key];
    els.category.appendChild(opt);
  });

  init();

  async function init() {
    me = (typeof ensureCurrentUser === 'function') ? await ensureCurrentUser() : readUser();
    els.filters.addEventListener('click', onFilterClick);
    els.writeBtn.addEventListener('click', openComposerForNew);
    els.composerCancel.addEventListener('click', closeComposer);
    els.dataKind.addEventListener('change', syncSubFields);
    els.composer.addEventListener('submit', onSubmit);
    els.threadList.addEventListener('click', onThreadClick);
    els.variantList.addEventListener('click', onThreadClick);
    els.evidenceList.addEventListener('click', onThreadClick);
    window.addEventListener('f2r:login', function (e) { me = (e && e.detail) || readUser(); syncAuthUi(); });
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
      els.threadList.innerHTML = '<p class="error">불러오지 못했습니다.</p>';
    }
  }

  function render() {
    var q = cache.question;
    els.context.textContent = q.displayContext;
    els.title.textContent = q.title || (q.questionNumber + '번 문항');
    document.title = q.displayContext + ' · BuildIt Wiki';
    if (q.passageGroupKey && (q.passageGroupQuestionNumbers || []).length > 1) {
      els.passageNote.hidden = false;
      els.passageNote.textContent = '공동지문 세트: ' + q.passageGroupQuestionNumbers.join('–') + '번';
    }

    renderMaterials(cache.materials || []);
    renderAiSummary(cache.aiSummary);
    renderThreads();
    renderVariants(cache.variants || []);
    renderEvidence(cache.examEvidence || []);
  }

  function renderMaterials(materials) {
    if (!materials.length) return;
    els.materials.innerHTML = materials.map(function (m) {
      var kind = m.materialKind === 'EXAM_ORIGINAL' ? '모의고사 원본' : '분석 자료';
      return '<a class="qw-material" href="' + escAttr(m.downloadUrl) + '">'
        + '<span class="qw-material-kind">' + kind + '</span>'
        + '<b>' + esc(m.label || kind) + '</b><span class="qw-material-dl">내려받기 ↓</span></a>';
    }).join('');
  }

  function renderAiSummary(summary) {
    if (!summary) return;
    els.aiSection.hidden = false;
    els.aiBox.innerHTML = '<div class="qw-ai-meta">Summary v' + summary.version
      + (summary.threadPostCount != null ? ' · THREAD ' + summary.threadPostCount + '건 분석' : '') + '</div>'
      + '<div class="qw-ai-text">' + esc(summary.summaryText).replace(/\n/g, '<br>') + '</div>';
  }

  function renderThreads() {
    var list = (cache.threads || []).filter(function (p) {
      if (activeFilter === 'all' || activeFilter === 'EXAM_EVIDENCE' || activeFilter === 'VARIANT') return activeFilter === 'all';
      return p.category === activeFilter;
    });
    if (activeFilter === 'VARIANT') { els.threadList.innerHTML = renderPointer('변형문제'); return; }
    if (activeFilter === 'EXAM_EVIDENCE') { els.threadList.innerHTML = renderPointer('기출 THREAD'); return; }
    if (!list.length) {
      els.threadList.innerHTML = '<p class="muted">아직 글이 없어요. 이 문항에 대한 첫 질문이나 설명을 남겨보세요.</p>';
      return;
    }
    els.threadList.innerHTML = list.map(renderPost).join('');
  }

  function renderPointer(name) {
    return '<p class="muted">' + name + '은(는) 아래 "' + name + '" 섹션에서 확인하세요.</p>';
  }

  function renderVariants(variants) {
    if (!variants.length) {
      els.variantList.innerHTML = '<p class="muted">아직 공유된 변형문제가 없습니다. 직접 만든 문제를 THREAD에 올려보세요.</p>';
      return;
    }
    els.variantList.innerHTML = variants.map(function (p) {
      var v = p.variantDetail || {};
      return '<article class="qw-post qw-variant" data-post-id="' + p.postId + '">'
        + postHead(p)
        + (p.title ? '<h4>' + esc(p.title) + '</h4>' : '')
        + (p.content ? '<p class="qw-post-body">' + esc(p.content).replace(/\n/g, '<br>') + '</p>' : '')
        + '<dl class="qw-kv">'
        + kv('문제', v.questionText)
        + kv('유형', v.questionType)
        + kv('정답', v.answer)
        + kv('해설', v.explanation)
        + kv('근거 문장', v.sourceSentence)
        + kv('원 문항', v.sourceQuestion)
        + kv('난이도', v.difficulty)
        + '</dl>'
        + attachBlock(p)
        + postActions(p)
        + replies(p)
        + '</article>';
    }).join('');
  }

  function renderEvidence(evidence) {
    if (!evidence.length) {
      els.evidenceList.innerHTML = '<p class="muted">아직 등록된 기출이 없습니다. 실제 학교 시험에서 이 지문이 출제됐다면 공유해 주세요.</p>';
      return;
    }
    els.evidenceList.innerHTML = evidence.map(function (p) {
      var e = p.examEvidence || {};
      var head = [e.schoolName, e.gradeLabel, e.examPeriod].filter(Boolean).join(' · ');
      return '<article class="qw-post qw-evidence" data-post-id="' + p.postId + '">'
        + postHead(p)
        + (head ? '<h4>' + esc(head) + '</h4>' : '')
        + (p.content ? '<p class="qw-post-body">' + esc(p.content).replace(/\n/g, '<br>') + '</p>' : '')
        + '<dl class="qw-kv">'
        + kv('사용 문장', e.relatedSentence)
        + kv('출제 유형', e.examQuestionType)
        + kv('출제 포인트', e.examPoint)
        + kv('설명', e.description)
        + '</dl>'
        + attachBlock(p)
        + postActions(p)
        + replies(p)
        + '</article>';
    }).join('');
  }

  function renderPost(p) {
    return '<article class="qw-post" data-post-id="' + p.postId + '">'
      + postHead(p)
      + (p.title ? '<h4>' + esc(p.title) + '</h4>' : '')
      + '<p class="qw-post-body">' + esc(p.content).replace(/\n/g, '<br>') + '</p>'
      + attachBlock(p)
      + postActions(p)
      + replies(p)
      + '</article>';
  }

  function postHead(p) {
    return '<div class="qw-post-head">'
      + '<span class="qw-badge qw-badge-' + p.dataKind + '">' + esc(p.dataKindLabel) + '</span>'
      + '<span class="qw-badge qw-cat">' + esc(p.categoryLabel) + '</span>'
      + '<span class="qw-participant">' + esc(p.participantType) + '</span>'
      + '<span class="qw-author">' + esc(p.authorName) + '</span>'
      + '<span class="qw-time">' + fmt(p.createdAt) + '</span>'
      + '</div>';
  }

  function postActions(p) {
    var parts = ['<button type="button" class="qw-txt-btn" data-reply>답글</button>'];
    if (p.mine) parts.push('<button type="button" class="qw-txt-btn" data-edit>수정</button>');
    if (p.canManage) parts.push('<button type="button" class="qw-txt-btn" data-del>삭제</button>');
    return '<div class="qw-post-actions">' + parts.join('') + '</div>';
  }

  function replies(p) {
    var rs = p.replies || [];
    var box = '<div class="qw-replies">';
    box += rs.map(function (r) {
      return '<div class="qw-reply" data-post-id="' + r.postId + '">'
        + '<div class="qw-post-head"><span class="qw-participant">' + esc(r.participantType) + '</span>'
        + '<span class="qw-author">' + esc(r.authorName) + '</span><span class="qw-time">' + fmt(r.createdAt) + '</span></div>'
        + '<p class="qw-post-body">' + esc(r.content).replace(/\n/g, '<br>') + '</p>'
        + '<div class="qw-post-actions">'
        + (r.mine ? '<button type="button" class="qw-txt-btn" data-edit-reply>수정</button>' : '')
        + (r.canManage ? '<button type="button" class="qw-txt-btn" data-del-reply>삭제</button>' : '')
        + '</div></div>';
    }).join('');
    box += '</div>';
    return box;
  }

  function attachBlock(p) {
    var atts = p.attachments || [];
    if (!atts.length) return '';
    return '<div class="qw-attach">' + atts.map(function (a) {
      return '<a class="qw-attach-row" href="' + escAttr(a.downloadUrl) + '">'
        + esc(a.originalFileName) + ' <span class="muted">(' + (a.fileSize / 1024 / 1024).toFixed(1) + 'MB)</span> 내려받기 ↓</a>';
    }).join('') + '</div>';
  }

  function kv(label, value) {
    if (!value) return '';
    return '<dt>' + label + '</dt><dd>' + esc(value).replace(/\n/g, '<br>') + '</dd>';
  }

  /* ── composer ─────────────────────────────────────────────── */

  function onFilterClick(e) {
    var b = e.target.closest('.qw-chip');
    if (!b) return;
    els.filters.querySelectorAll('.qw-chip').forEach(function (c) { c.classList.toggle('active', c === b); });
    activeFilter = b.dataset.filter;
    renderThreads();
  }

  function openComposerForNew() {
    if (!me) { promptLogin(); return; }
    editingPostId = null;
    replyParentId = null;
    els.composer.reset();
    Array.prototype.forEach.call(els.category.options, function (o) { o.selected = o.value === 'QUESTION'; });
    els.dataKind.disabled = false;
    els.dataKind.parentElement.hidden = false;
    els.category.parentElement.hidden = false;
    syncSubFields();
    els.composerSubmit.textContent = '등록';
    els.composerMsg.textContent = '';
    els.composer.hidden = false;
    els.postContent.focus();
  }

  function closeComposer() {
    els.composer.hidden = true;
    editingPostId = null;
    replyParentId = null;
  }

  function syncSubFields() {
    var kind = els.dataKind.value;
    els.variantFields.hidden = kind !== 'GENERATED_PRACTICE';
    els.evidenceFields.hidden = kind !== 'EXAM_EVIDENCE';
    if (kind === 'GENERATED_PRACTICE') selectCategory('VARIANT');
    if (kind === 'EXAM_EVIDENCE') selectCategory('EXAM_POINT');
  }

  function selectCategory(value) {
    Array.prototype.forEach.call(els.category.options, function (o) { o.selected = o.value === value; });
  }

  function openReply(parentId) {
    if (!me) { promptLogin(); return; }
    editingPostId = null;
    replyParentId = parentId;
    els.composer.reset();
    els.dataKind.parentElement.hidden = true;
    els.category.parentElement.hidden = true;
    els.variantFields.hidden = true;
    els.evidenceFields.hidden = true;
    els.composerSubmit.textContent = '답글 등록';
    els.composerMsg.textContent = '';
    els.composer.hidden = false;
    var anchor = els.threadList.querySelector('[data-post-id="' + parentId + '"]')
      || els.variantList.querySelector('[data-post-id="' + parentId + '"]')
      || els.evidenceList.querySelector('[data-post-id="' + parentId + '"]');
    els.composer.scrollIntoView({ behavior: 'smooth', block: 'center' });
    els.postContent.focus();
    void anchor;
  }

  function openEdit(post) {
    if (!post) return;
    editingPostId = post.postId;
    replyParentId = null;
    els.composer.reset();
    els.dataKind.parentElement.hidden = !!post.parentPostId;
    els.category.parentElement.hidden = !!post.parentPostId;
    if (!post.parentPostId) {
      selectCategory(post.category);
      Array.prototype.forEach.call(els.dataKind.options, function (o) { o.selected = o.value === post.dataKind; });
      syncSubFields();
      var v = post.variantDetail;
      if (v) {
        setVal('v_questionText', v.questionText); setVal('v_questionType', v.questionType);
        setVal('v_answer', v.answer); setVal('v_explanation', v.explanation);
        setVal('v_sourceSentence', v.sourceSentence); setVal('v_sourceQuestion', v.sourceQuestion);
        setVal('v_difficulty', v.difficulty);
      }
      var e = post.examEvidence;
      if (e) {
        setVal('e_schoolName', e.schoolName); setVal('e_gradeLabel', e.gradeLabel);
        setVal('e_examPeriod', e.examPeriod); setVal('e_relatedSentence', e.relatedSentence);
        setVal('e_examQuestionType', e.examQuestionType); setVal('e_examPoint', e.examPoint);
        setVal('e_description', e.description);
      }
    }
    els.postTitle.value = post.title || '';
    els.postContent.value = post.content || '';
    els.composerSubmit.textContent = '수정';
    els.composerMsg.textContent = '';
    els.composer.hidden = false;
    els.composer.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function setVal(name, value) {
    var el = els.composer.querySelector('[name="' + name + '"]');
    if (el) el.value = value || '';
  }

  async function onSubmit(e) {
    e.preventDefault();
    if (!me) { promptLogin(); return; }
    var content = els.postContent.value.trim();
    if (!content) { els.composerMsg.textContent = '내용을 입력해 주세요.'; return; }

    var body = {
      category: els.category.parentElement.hidden ? null : els.category.value,
      dataKind: els.dataKind.parentElement.hidden ? null : els.dataKind.value,
      title: els.postTitle.value.trim() || null,
      content: content
    };
    if (body.dataKind === 'GENERATED_PRACTICE') {
      body.variant = {
        questionText: fv('v_questionText'), questionType: fv('v_questionType'), answer: fv('v_answer'),
        explanation: fv('v_explanation'), sourceSentence: fv('v_sourceSentence'),
        sourceQuestion: fv('v_sourceQuestion'), difficulty: fv('v_difficulty')
      };
    }
    if (body.dataKind === 'EXAM_EVIDENCE') {
      body.examEvidence = {
        schoolName: fv('e_schoolName'), gradeLabel: fv('e_gradeLabel'), examPeriod: fv('e_examPeriod'),
        relatedSentence: fv('e_relatedSentence'), examQuestionType: fv('e_examQuestionType'),
        examPoint: fv('e_examPoint'), description: fv('e_description')
      };
    }

    var url, method;
    if (editingPostId) {
      url = '/api/wiki/questions/posts/' + editingPostId; method = 'PUT';
    } else if (replyParentId) {
      url = '/api/wiki/questions/' + questionWikiId + '/posts/' + replyParentId + '/replies'; method = 'POST';
    } else {
      url = '/api/wiki/questions/' + questionWikiId + '/posts'; method = 'POST';
    }
    els.composerSubmit.disabled = true;
    var ok = await send(url, method, body);
    els.composerSubmit.disabled = false;
    if (ok) closeComposer();
  }

  function fv(name) {
    var el = els.composer.querySelector('[name="' + name + '"]');
    return el && el.value.trim() ? el.value.trim() : null;
  }

  async function onThreadClick(e) {
    var article = e.target.closest('[data-post-id]');
    if (!article) return;
    var postId = article.dataset.postId;
    if (e.target.closest('[data-reply]')) { openReply(postId); return; }
    if (e.target.closest('[data-edit]')) { openEdit(findPost(postId)); return; }
    if (e.target.closest('[data-edit-reply]')) { openEditReply(postId); return; }
    if (e.target.closest('[data-del]') || e.target.closest('[data-del-reply]')) {
      if (!confirm('이 글을 삭제할까요?')) return;
      await send('/api/wiki/questions/posts/' + postId, 'DELETE');
    }
  }

  function openEditReply(replyId) {
    var reply = null;
    allRoots().forEach(function (r) {
      (r.replies || []).forEach(function (x) { if (String(x.postId) === String(replyId)) reply = x; });
    });
    if (reply) openEdit(reply);
  }

  function findPost(postId) {
    return allRoots().find(function (p) { return String(p.postId) === String(postId); }) || null;
  }

  function allRoots() {
    if (!cache) return [];
    return [].concat(cache.threads || [], cache.variants || [], cache.examEvidence || []);
  }

  async function send(url, method, body) {
    try {
      var opts = { method: method, redirectOnAuthError: false };
      if (body) { opts.headers = { 'Content-Type': 'application/json' }; opts.body = JSON.stringify(body); }
      var res = await fetchAuth(url, opts);
      if (res.status === 401 || res.status === 403) { promptLogin(); return false; }
      if (method === 'DELETE') {
        if (res.ok || res.status === 204) { await load(); return true; }
        alert('삭제에 실패했습니다.'); return false;
      }
      if (!res.ok) { els.composerMsg.textContent = (await res.text()) || '처리에 실패했습니다.'; return false; }
      cache = await res.json();
      render();
      return true;
    } catch (_) {
      els.composerMsg.textContent = '처리에 실패했습니다.';
      return false;
    }
  }

  function syncAuthUi() {
    var loggedIn = !!me;
    els.writeBtn.hidden = false;
    els.loginHint.hidden = loggedIn;
  }

  function promptLogin() {
    alert('로그인하면 읽고, 묻고, 직접 참여할 수 있어요.');
    if (typeof openLoginModal === 'function') openLoginModal();
  }

  function readUser() { try { return JSON.parse(sessionStorage.getItem('currentUser')); } catch (_) { return null; } }
  function fmt(v) { return v ? new Date(v).toLocaleDateString('ko-KR') : ''; }
  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>'"]/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c];
    });
  }
  function escAttr(v) { return esc(v).replace(/`/g, '&#96;'); }
})();
