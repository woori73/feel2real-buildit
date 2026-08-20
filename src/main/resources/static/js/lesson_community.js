(() => {
  const shell = document.querySelector('.community-shell');
  const lessonId = shell?.dataset.lessonId;
  let currentTab = 'all';
  let currentCategoryId = '';
  let context = null;
  let customTabs = [];
  let selectedPostId = null;
  let previewObjectUrls = [];
  let initialized = false;

  const DEFAULT_TABS = [
    { key: 'all', label: '전체글', postType: null, categoryId: '' },
    { key: 'general', label: '일반글', postType: 'GENERAL', categoryId: '' },
    { key: 'qna', label: 'Q&A', postType: 'QNA', categoryId: '' },
    { key: 'notice', label: '공지', postType: 'NOTICE', categoryId: '' },
  ];
  const MAX_ATTACHMENTS = 10;
  const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

  const $ = (sel) => document.querySelector(sel);
  const tabsEl = $('#tabs');
  const postList = $('#postList');
  const postDetail = $('#postDetail');

  document.addEventListener('DOMContentLoaded', init);
  window.addEventListener('f2r:login', init);

  async function init() {
    if (initialized) return;
    if (!await ensureCurrentUser()) { alert('로그인 후 이용할 수 있습니다.'); if (typeof openLoginModal === 'function') openLoginModal(); return; }
    initialized = true;
    bindEvents();
    try {
      await loadContext();
      await loadCustomTabs();
      await loadPosts();
    } catch (e) {
      postList.innerHTML = `<div class="empty-state">커뮤니티를 불러오지 못했습니다. ${escapeHtml(e.message || '')}</div>`;
    }
  }

  function renderAdminCommunityBanner() {
    if (document.getElementById('adminCommunityBanner')) return;
    const banner = document.createElement('div');
    banner.id = 'adminCommunityBanner';
    banner.style.cssText =
      'background:#e53e3e;color:#fff;padding:10px 16px;font-size:13px;' +
      'display:flex;align-items:center;justify-content:space-between;gap:12px;';
    banner.innerHTML =
      `<span>👁️ <strong>관리자 모드</strong> — ${escapeHtml(context.academyName)} / ` +
      `${escapeHtml(context.lessonName)} 커뮤니티를 조회 중입니다. ADMIN 권한으로 전체 게시글과 Q&A에 접근할 수 있습니다.</span>` +
      `<button onclick="location.href='/admin/lesson-communities'" style="background:rgba(0,0,0,0.2);` +
      `color:#fff;border:none;padding:6px 12px;border-radius:6px;cursor:pointer;font-size:12px;">커뮤니티 검색으로 ›</button>`;
    document.body.insertBefore(banner, document.body.firstChild);
  }

  function bindEvents() {
    tabsEl?.addEventListener('click', (event) => {
      const btn = event.target.closest('.tab');
      if (!btn) return;
      document.querySelectorAll('.tab').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.cat-tab').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentTab = btn.dataset.tab || 'all';
      currentCategoryId = btn.dataset.categoryId || '';
      closePostDetail();
      loadPosts();
    });
    $('#openPostForm')?.addEventListener('click', openPostForm);
    $('#closePostForm')?.addEventListener('click', () => closePostForm());
    $('#cancelPostForm')?.addEventListener('click', () => closePostForm());
    $('#postFormModal')?.addEventListener('click', (event) => {
      if (event.target.id === 'postFormModal') closePostForm();
    });
    $('#postForm')?.addEventListener('submit', submitPost);
    $('#postFiles')?.addEventListener('change', () => {
      if (!validatePostFiles($('#postFiles')?.files)) clearPostFiles();
      updateSelectedFileList();
    });
    initPostFileDrop();
    $('#openTabManager')?.addEventListener('click', openTabManager);
    $('#closeTabManager')?.addEventListener('click', closeTabManager);
    $('#customTabModal')?.addEventListener('click', (event) => {
      if (event.target.id === 'customTabModal') closeTabManager();
    });
    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') return;
      if (!$('#postFormModal')?.hidden) closePostForm();
      if (!$('#customTabModal')?.hidden) closeTabManager();
    });
    $('#customTabForm')?.addEventListener('submit', submitCustomTab);
    $('#postTab')?.addEventListener('change', syncQnaCheckbox);
    $('#customTabList')?.addEventListener('click', handleCustomTabAction);
  }

  async function loadContext() {
    const res = await fetchAuth(`/api/lessons/${lessonId}/community/context`);
    if (!res.ok) throw new Error('접근 권한이 없거나 수업을 찾을 수 없습니다.');
    context = await res.json();
    $('#lessonTitle').textContent = context.lessonName;
    $('#lessonMeta').textContent = `${context.academyName} · ${roleLabel(context.role)}`;
    if (context.admin) renderAdminCommunityBanner();
    $('#openTabManager').hidden = !(context.tutorOwner || context.admin);
  }

  async function loadCustomTabs() {
    const res = await fetchAuth(`/api/lessons/${lessonId}/community/categories`);
    if (!res.ok) throw new Error('탭을 불러오지 못했습니다.');
    customTabs = await res.json();
    renderTabs();
    renderPostTabOptions();
    renderCustomTabAdmin();
  }

  function renderTabs() {
    const defaultHtml = DEFAULT_TABS.map(tab => tabButtonHtml(tab.key, tab.label, '')).join('');
    const customHtml = customTabs.map(tab => tabButtonHtml(`custom-${tab.categoryId}`, tab.name, tab.categoryId)).join('');
    tabsEl.innerHTML = defaultHtml + customHtml;
    const active = currentCategoryId
      ? tabsEl.querySelector(`[data-category-id="${currentCategoryId}"]`)
      : tabsEl.querySelector(`[data-tab="${currentTab}"]`);
    (active || tabsEl.querySelector('[data-tab="all"]'))?.classList.add('active');
  }

  function tabButtonHtml(key, label, categoryId) {
    const active = (!categoryId && key === currentTab) || (categoryId && String(categoryId) === String(currentCategoryId));
    return `<button class="cat-tab tab ${active ? 'active' : ''}" data-tab="${escapeHtml(key)}" data-category-id="${escapeHtml(categoryId || '')}" type="button">${escapeHtml(label)}</button>`;
  }

  function renderPostTabOptions() {
    const select = $('#postTab');
    if (!select) return;
    const base = DEFAULT_TABS
      .filter(tab => tab.key !== 'all')
      .map(tab => `<option value="${tab.key}">${escapeHtml(tab.label)}</option>`)
      .join('');
    const custom = customTabs
      .map(tab => `<option value="custom:${tab.categoryId}">${escapeHtml(tab.name)}</option>`)
      .join('');
    select.innerHTML = base + custom;
  }

  function renderCustomTabAdmin() {
    const root = $('#customTabList');
    if (!root) return;
    if (!customTabs.length) {
      root.innerHTML = '<span class="muted">추가된 커스텀 탭이 없습니다.</span>';
      return;
    }
    root.innerHTML = customTabs.map(tab => `<div class="custom-tab-row">
      <span>${escapeHtml(tab.name)}</span>
      <div>
        <button type="button" class="ghost-btn small" data-edit-tab="${tab.categoryId}">수정</button>
        <button type="button" class="ghost-btn small danger" data-delete-tab="${tab.categoryId}">삭제</button>
      </div>
    </div>`).join('');
  }

  async function loadPosts() {
    const params = new URLSearchParams();
    params.set('tab', currentCategoryId ? 'custom' : currentTab);
    if (currentCategoryId) params.set('categoryId', currentCategoryId);
    const res = await fetchAuth(`/api/lessons/${lessonId}/community/posts?${params}`);
    if (!res.ok) throw new Error('게시글을 불러오지 못했습니다.');
    const data = await res.json();
    renderPosts(data.posts || [], data.pinnedNotice || null);
  }

  function renderPosts(posts, pinnedNotice) {
    const pinnedHtml = pinnedNotice ? postCardHtml(pinnedNotice, true) : '';
    if (!posts.length && !pinnedNotice) {
      postList.innerHTML = '<div class="feed-empty">아직 게시글이 없습니다.</div>';
      updateFeedCount(0, !!pinnedNotice);
      return;
    }
    postList.innerHTML = pinnedHtml + posts.map(p => postCardHtml(p, false)).join('');
    updateFeedCount(posts.length, !!pinnedNotice);
    postList.querySelectorAll('.post-card').forEach(card => card.addEventListener('click', () => loadPost(card.dataset.postId)));
  }

  function postCardHtml(post, pinned) {
    const selected = post.postId === selectedPostId;
    return `<article class="q-card post-card ${pinned ? 'pinned' : ''} ${selected ? 'active opened' : ''}" data-post-id="${post.postId}">
      <div class="q-card-meta"><span class="badges">${pinned ? '<span class="cat-badge pinned">최신 공지</span>' : ''}${badges(post)}</span><span class="who">${escapeHtml(post.authorName)} · ${formatDateTime(post.createdAt)}</span></div>
      <h3>${escapeHtml(post.title)}</h3>
      <div class="q-card-stats"><span class="answers">댓글 ${post.commentCount}</span><span>첨부 ${post.attachmentCount}</span></div>
    </article>`;
  }


  function updateFeedCount(count, hasPinned) {
    const countEl = $('#feedCount');
    if (!countEl) return;
    const suffix = hasPinned ? ' · 최신 공지 포함' : '';
    countEl.textContent = `게시글 ${count}개${suffix}`;
  }

  async function loadPost(postId) {
    selectedPostId = Number(postId);
    const res = await fetchAuth(`/api/community/posts/${postId}`);
    if (!res.ok) {
      await loadPosts();
      postDetail.innerHTML = '<div class="feed-error">게시글을 열 수 없습니다.</div>';
      placePostDetailAfterSelectedCard();
      postDetail.hidden = false;
      return;
    }
    const post = await res.json();
    await loadPosts();
    renderPostDetail(post);
  }

  function renderPostDetail(post) {
    placePostDetailAfterSelectedCard();
    postDetail.hidden = false;
    postDetail.innerHTML = `
      <article class="post-detail-card">
        <div class="badges">${badges(post)}</div>
        <h2>${escapeHtml(post.title)}</h2>
        <div class="meta">${escapeHtml(post.authorName)} · ${formatDateTime(post.createdAt)}</div>
        <div class="post-body">${escapeHtml(post.content)}</div>
        ${attachmentsHtml(post.attachments)}
        <div class="detail-actions">
          ${post.mine ? '<button class="ghost-btn" data-action="edit-post">수정</button>' : ''}
          ${post.canHide ? '<button class="ghost-btn" data-action="hide-post">숨김</button>' : ''}
          <button class="ghost-btn" data-action="close-detail">닫기</button>
        </div>
        <h3>댓글</h3>
        <div class="comments">${post.comments.map(commentHtml).join('') || '<div class="feed-empty">댓글이 없습니다.</div>'}</div>
        <form class="comment-form" id="commentForm">
          <textarea name="content" maxlength="5000" required placeholder="댓글을 입력하세요."></textarea>
          <button class="primary-btn" type="submit">댓글 등록</button>
        </form>
      </article>
    `;
    postDetail.querySelector('[data-action="hide-post"]')?.addEventListener('click', () => hidePost(post.postId));
    postDetail.querySelector('[data-action="edit-post"]')?.addEventListener('click', () => fillPostFormForEdit(post));
    postDetail.querySelector('[data-action="close-detail"]')?.addEventListener('click', closePostDetail);
    postDetail.querySelector('#commentForm')?.addEventListener('submit', (e) => submitComment(e, post.postId));
    postDetail.querySelectorAll('[data-hide-comment]').forEach(btn => btn.addEventListener('click', () => hideComment(btn.dataset.hideComment)));
  }

  function openPostForm() {
    const form = $('#postForm');
    if (form) form.dataset.editingPostId = '';
    $('#postFormTitle').textContent = '새 글 작성';
    $('#postFormModal').hidden = false;
    updateSelectedFileList();
    syncPostTabDefault();
    syncQnaCheckbox();
    form?.elements.title?.focus();
  }

  async function submitPost(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    if (!validatePostFiles(data.getAll('files'))) return;
    applyPostTabToFormData(data);
    const editing = form.dataset.editingPostId;
    if (editing) {
      const payload = {
        postType: data.get('postType'),
        title: data.get('title'),
        content: data.get('content'),
        categoryId: data.get('categoryId') || null,
        qnaPublic: data.get('qnaPublic') === 'true'
      };
      const res = await fetchAuth(`/api/community/posts/${editing}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
      });
      if (!res.ok) { alert('글 수정에 실패했습니다.'); return; }
      form.dataset.editingPostId = '';
    } else {
      const res = await fetchAuth(`/api/lessons/${lessonId}/community/posts`, { method: 'POST', body: data });
      if (!res.ok) { alert('글 등록에 실패했습니다.'); return; }
    }
    closePostForm();
    await loadPosts();
  }

  function applyPostTabToFormData(data) {
    const selected = data.get('postTab') || 'general';
    data.delete('postTab');
    data.delete('postType');
    data.delete('categoryId');
    if (selected === 'qna') {
      data.set('postType', 'QNA');
      data.set('categoryId', '');
    } else if (selected === 'notice') {
      data.set('postType', 'NOTICE');
      data.set('categoryId', '');
    } else if (selected.startsWith('custom:')) {
      data.set('postType', 'GENERAL');
      data.set('categoryId', selected.slice('custom:'.length));
    } else {
      data.set('postType', 'GENERAL');
      data.set('categoryId', '');
    }
  }

  async function submitComment(e, postId) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    const res = await fetchAuth(`/api/community/posts/${postId}/comments`, { method: 'POST', body: data });
    if (!res.ok) { alert('댓글 등록에 실패했습니다.'); return; }
    form.reset(); await loadPost(postId);
  }

  function openTabManager() {
    const modal = $('#customTabModal');
    if (!modal) return;
    modal.hidden = false;
    modal.querySelector('input[name="name"]')?.focus();
  }

  function closeTabManager() {
    const modal = $('#customTabModal');
    if (!modal) return;
    modal.hidden = true;
    $('#customTabForm')?.reset();
  }

  async function submitCustomTab(e) {
    e.preventDefault();
    const input = e.currentTarget.elements.name;
    const name = input.value.trim();
    if (!name) return;
    const res = await fetchAuth(`/api/lessons/${lessonId}/community/categories`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name })
    });
    if (!res.ok) { alert(await failureMessage(res, '탭 추가에 실패했습니다.')); return; }
    input.value = '';
    await loadCustomTabs();
  }

  async function handleCustomTabAction(event) {
    const editButton = event.target.closest('[data-edit-tab]');
    const deleteButton = event.target.closest('[data-delete-tab]');
    if (editButton) {
      const tab = customTabs.find(t => String(t.categoryId) === String(editButton.dataset.editTab));
      if (!tab) return;
      const name = prompt('탭 이름을 입력하세요.', tab.name);
      if (name == null || !name.trim()) return;
      const res = await fetchAuth(`/api/lessons/${lessonId}/community/categories/${tab.categoryId}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name.trim(), sortOrder: tab.sortOrder })
      });
      if (!res.ok) { alert(await failureMessage(res, '탭 수정에 실패했습니다.')); return; }
      await loadCustomTabs();
    }
    if (deleteButton) {
      const tab = customTabs.find(t => String(t.categoryId) === String(deleteButton.dataset.deleteTab));
      if (!tab) return;
      if (!confirm(`'${tab.name}' 탭을 삭제할까요? 이 탭의 글은 일반글로 이동합니다.`)) return;
      const res = await fetchAuth(`/api/lessons/${lessonId}/community/categories/${tab.categoryId}`, { method: 'DELETE' });
      if (!res.ok) { alert(await failureMessage(res, '탭 삭제에 실패했습니다.')); return; }
      if (String(currentCategoryId) === String(tab.categoryId)) {
        currentTab = 'general';
        currentCategoryId = '';
      }
      closePostDetail();
      await loadCustomTabs();
      await loadPosts();
    }
  }

  async function hidePost(postId) {
    if (!confirm('이 글을 숨김 처리할까요?')) return;
    const res = await fetchAuth(`/api/community/posts/${postId}`, { method: 'DELETE' });
    if (!res.ok) { alert('숨김 처리에 실패했습니다.'); return; }
    closePostDetail(); await loadPosts();
  }

  async function hideComment(commentId) {
    if (!confirm('이 댓글을 숨김 처리할까요?')) return;
    const res = await fetchAuth(`/api/community/comments/${commentId}`, { method: 'DELETE' });
    if (!res.ok) { alert('댓글 숨김 처리에 실패했습니다.'); return; }
    if (selectedPostId) await loadPost(selectedPostId);
  }

  function fillPostFormForEdit(post) {
    $('#postFormModal').hidden = false;
    $('#postFormTitle').textContent = '글 수정';
    const form = $('#postForm');
    form.dataset.editingPostId = post.postId;
    form.elements.postTab.value = post.categoryId ? `custom:${post.categoryId}` : postTypeToTab(post.postType);
    form.elements.title.value = post.title;
    form.elements.content.value = post.content;
    form.elements.qnaPublic.checked = !!post.qnaPublic;
    updateSelectedFileList();
    syncQnaCheckbox();
  }

  function syncPostTabDefault() {
    const form = $('#postForm');
    if (form.dataset.editingPostId) return;
    const select = $('#postTab');
    if (!select) return;
    if (currentCategoryId) select.value = `custom:${currentCategoryId}`;
    else if (currentTab !== 'all') select.value = currentTab;
    else select.value = 'general';
  }

  function syncQnaCheckbox() {
    const row = document.querySelector('.checkbox-row');
    if (!row) return;
    const isQna = $('#postTab')?.value === 'qna';
    row.hidden = !isQna;
    if (!isQna) {
      const checkbox = row.querySelector('input[name="qnaPublic"]');
      if (checkbox) checkbox.checked = false;
    }
  }

  function closePostForm() {
    const form = $('#postForm');
    form.reset();
    form.dataset.editingPostId = '';
    $('#postFormTitle').textContent = '새 글 작성';
    $('#postFormModal').hidden = true;
    revokePreviewObjectUrls();
    updateSelectedFileList();
    syncQnaCheckbox();
  }

  function placePostDetailAfterSelectedCard() {
    const card = postList?.querySelector(`.post-card[data-post-id="${selectedPostId}"]`);
    if (card && postDetail.parentElement !== postList) {
      postList.appendChild(postDetail);
    }
    if (card) card.insertAdjacentElement('afterend', postDetail);
  }

  function initPostFileDrop() {
    const modal = $('#postFormModal');
    const dropZone = $('#fileDropZone');
    const content = $('#postForm textarea[name="content"]');
    [modal, dropZone, content].forEach(target => {
      if (!target) return;
      target.addEventListener('dragover', (event) => {
        event.preventDefault();
        dropZone?.classList.add('drag-over');
      });
      target.addEventListener('dragleave', () => dropZone?.classList.remove('drag-over'));
      target.addEventListener('drop', (event) => {
        event.preventDefault();
        dropZone?.classList.remove('drag-over');
        appendDroppedFiles(event.dataTransfer?.files);
      });
    });
  }

  function appendDroppedFiles(fileList) {
    const input = $('#postFiles');
    if (!input || !fileList || !fileList.length) return;
    const transfer = new DataTransfer();
    [...(input.files || [])].forEach(file => transfer.items.add(file));
    [...fileList].forEach(file => transfer.items.add(file));
    if (!validatePostFiles(transfer.files)) {
      return;
    }
    input.files = transfer.files;
    updateSelectedFileList();
  }

  function validatePostFiles(fileList) {
    const files = [...(fileList || [])].filter(file => file && file.size > 0);
    if (files.length > MAX_ATTACHMENTS) {
      alert(`첨부파일은 최대 ${MAX_ATTACHMENTS}개입니다.`);
      return false;
    }
    const tooLarge = files.find(file => file.size > MAX_ATTACHMENT_BYTES);
    if (tooLarge) {
      alert(`첨부파일은 파일당 10MB 이하만 가능합니다. (${tooLarge.name})`);
      return false;
    }
    return true;
  }

  function clearPostFiles() {
    const input = $('#postFiles');
    if (!input) return;
    input.value = '';
  }

  function updateSelectedFileList() {
    const input = $('#postFiles');
    const list = $('#selectedFileList');
    if (!input || !list) return;
    revokePreviewObjectUrls();
    const files = [...(input.files || [])].filter(file => file && file.size > 0);
    if (!files.length) {
      list.textContent = '선택된 첨부파일이 없습니다.';
      return;
    }
    list.innerHTML = files.map(selectedFilePreviewHtml).join('');
  }

  function closePostDetail() {
    document.querySelectorAll('.post-card.opened').forEach(card => card.classList.remove('opened', 'active'));
    selectedPostId = null;
    postDetail.innerHTML = '';
    postDetail.hidden = true;
    if (postDetail.parentElement !== postList?.parentElement) {
      postList?.parentElement?.appendChild(postDetail);
    }
  }

  function badges(p) {
    const type = p.postType;
    const parts = [];
    if (p.authorAdmin) parts.push('<span class="cat-badge admin">관리자</span>');
    if (type === 'NOTICE') parts.push('<span class="cat-badge notice">공지</span>');
    else if (type === 'QNA') parts.push('<span class="cat-badge qna">Q&A</span>');
    else parts.push('<span class="cat-badge general">일반</span>');
    if (p.categoryName) parts.push(`<span class="cat-badge custom">${escapeHtml(p.categoryName)}</span>`);
    if (type === 'QNA' && !p.qnaPublic) parts.push('<span class="cat-badge private">비공개</span>');
    return parts.join('');
  }

  function commentHtml(c) {
    const adminBadge = c.authorAdmin ? '<span class="badge admin meta-badge">관리자</span>' : '';
    return `<div class="comment-card">
      <div class="meta">${escapeHtml(c.authorName)}${adminBadge} · ${formatDateTime(c.createdAt)}</div>
      <div class="post-body">${escapeHtml(c.content)}</div>
      ${attachmentsHtml(c.attachments)}
      ${c.mine || context?.tutorOwner || context?.admin ? `<button class="ghost-btn" data-hide-comment="${c.commentId}">숨김</button>` : ''}
    </div>`;
  }

  function attachmentsHtml(items) {
    if (!items || !items.length) return '';
    return `<div class="attachments">${items.map(attachmentHtml).join('')}</div>`;
  }

  function attachmentHtml(a) {
    const name = escapeHtml(a.originalFileName);
    const url = escapeHtml(a.fileUrl);
    const size = humanFileSize(a.fileSize);
    if (isImageContentType(a.contentType)) {
      return `<a class="attachment-card image" href="${url}" target="_blank" rel="noopener" title="${name}">
        <img src="${url}" alt="${name}" loading="lazy">
        <span class="attachment-info">
          <span class="attachment-name">${name}</span>
          <span class="attachment-meta">${size}</span>
        </span>
      </a>`;
    }
    return `<a class="attachment-card file" href="${url}" target="_blank" rel="noopener" title="${name}">
      <span class="attachment-icon">📎</span>
      <span class="attachment-info">
        <span class="attachment-name">${name}</span>
        <span class="attachment-meta">${escapeHtml(a.contentType || '파일')} · ${size}</span>
      </span>
    </a>`;
  }

  function selectedFilePreviewHtml(file) {
    const name = escapeHtml(file.name);
    const size = humanFileSize(file.size);
    if (isImageContentType(file.type)) {
      const url = URL.createObjectURL(file);
      previewObjectUrls.push(url);
      return `<span class="selected-file-card image" title="${name}">
        <img src="${url}" alt="${name}">
        <span class="selected-file-info">
          <span class="selected-file-name">${name}</span>
          <span class="selected-file-meta">${size}</span>
        </span>
      </span>`;
    }
    return `<span class="selected-file-card file" title="${name}">
      <span class="attachment-icon">📎</span>
      <span class="selected-file-info">
        <span class="selected-file-name">${name}</span>
        <span class="selected-file-meta">${escapeHtml(file.type || '파일')} · ${size}</span>
      </span>
    </span>`;
  }

  function revokePreviewObjectUrls() {
    previewObjectUrls.forEach(url => URL.revokeObjectURL(url));
    previewObjectUrls = [];
  }

  function isImageContentType(contentType) {
    return String(contentType || '').toLowerCase().startsWith('image/');
  }

  function humanFileSize(size) {
    const bytes = Number(size || 0);
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  async function ensureCurrentUser() {
    const cached = readCurrentUser();
    if (cached) return cached;
    try {
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (!res.ok) return null;
      const user = await res.json();
      sessionStorage.setItem('currentUser', JSON.stringify(user));
      return user;
    } catch (_) {
      return null;
    }
  }

  async function failureMessage(res, fallback) {
    try {
      const text = await res.text();
      return text || fallback;
    } catch (_) {
      return fallback;
    }
  }

  function readCurrentUser() { try { return JSON.parse(sessionStorage.getItem('currentUser') || 'null'); } catch (_) { return null; } }
  function roleLabel(role) { return role === 'TUTOR' ? '강사' : role === 'ADMIN' ? '관리자' : '학생'; }
  function postTypeToTab(type) { return type === 'QNA' ? 'qna' : type === 'NOTICE' ? 'notice' : 'general'; }
  function formatDateTime(value) { return value ? new Date(value).toLocaleString('ko-KR') : '-'; }
  function escapeHtml(str) { return String(str ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;'); }
})();
