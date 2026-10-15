(function () {
  const params = new URLSearchParams(location.search);
  const pageMode = document.body.dataset.page || 'dashboard';
  let selectedId = numericId(params.get('id'));
  let conversations = [];
  let selectedDetail = null;
  let currentUser = null;
  let pollTimer = null;
  const IN_PROGRESS_STATUSES = ['PENDING', 'ANALYZING'];
  const POLL_INTERVAL_MS = 3000;

  const listEl = document.getElementById('conversationList');
  const countEl = document.getElementById('conversationCount');
  const titleEl = document.getElementById('conversationTitle');
  const metaEl = document.getElementById('conversationMeta');
  const messageList = document.getElementById('messageList');
  const messageForm = document.getElementById('messageForm');
  const messageInput = document.getElementById('messageInput');
  const sendButton = document.getElementById('sendButton');
  const materialsPageButton = document.getElementById('btnMaterialsPage');

  document.addEventListener('DOMContentLoaded', init);
  document.addEventListener('click', () => {
    document.querySelectorAll('.conversation-card-menu').forEach((menu) => { menu.hidden = true; });
  });

  async function init() {
    document.getElementById('logoutButton')?.addEventListener('click', handleLogout);

    const user = await ensureCurrentUser();
    currentUser = user;
    if (!user) {
      location.href = '/home';
      return;
    }
    if (user.role !== 'TUTOR') {
      location.href = dashboardPathForRole(user.role);
      return;
    }

    renderTutorProfile(user);

    if (pageMode === 'detail') {
      initDetailPage();
      return;
    }

    initDashboardPage();
  }

  function initDashboardPage() {
    document.getElementById('newConversationButton')?.addEventListener('click', createConversationAndOpen);
    document.getElementById('refreshButton')?.addEventListener('click', loadList);
    loadList();
  }

  function initDetailPage() {
    if (!selectedId) {
      alert('잘못된 접근입니다. 대화 ID가 없습니다.');
      location.href = '/tutor/conversation_dashboard';
      return;
    }
    document.getElementById('renameButton')?.addEventListener('click', () => renameConversation(selectedId, true));
    document.getElementById('deleteButton')?.addEventListener('click', () => deleteConversation(selectedId, true));
    document.getElementById('btnUploadHeader')?.addEventListener('click', () => openUploadModal());
    document.getElementById('btnUploadCard')?.addEventListener('click', () => openUploadModal());
    messageForm?.addEventListener('submit', sendMessage);
    wireTutorSidebarLinks();
    wireMaterialsPageLink();
    openConversationDetail(selectedId);
  }

  async function loadList() {
    if (!listEl) return;
    try {
      conversations = await fetchJsonAuth('/api/tutor/conversations');
      renderList();
    } catch (error) {
      showStatus('기록 목록을 불러오지 못했습니다.', true);
    }
  }

  function renderTutorProfile(user) {
    const displayName = window.userDisplayName ? window.userDisplayName(user) : (user.name || user.username || '강사');
    const userNameDisplay = document.getElementById('userNameDisplay');
    const avatarText = document.getElementById('avatarText');
    const tutorName = document.getElementById('tutorName');
    if (userNameDisplay) userNameDisplay.textContent = `${displayName}님`;
    if (avatarText) avatarText.textContent = String(displayName || '강').charAt(0);
    if (tutorName) tutorName.textContent = displayName;
  }

  async function handleLogout() {
    try { await fetch('/api/auth/logout', { method: 'POST' }); } catch (_) {}
    sessionStorage.clear();
    location.href = '/';
  }

  function renderList() {
    const items = Array.isArray(conversations) ? conversations : [];
    const sortedItems = [...items].sort((a, b) =>
      new Date(b.lastMessageAt || b.createdAt || 0) - new Date(a.lastMessageAt || a.createdAt || 0));
    if (countEl) countEl.textContent = items.length ? `${items.length}개 기록` : '아직 기록이 없습니다.';

    const createCard = `
      <button type="button" class="learning-record-card learning-record-create new-learning-record-action" data-create-conversation>
        <span class="create-plus">+</span>
        <span class="create-title">새 학습기록</span>
      </button>`;

    if (!items.length) {
      listEl.innerHTML = `
        ${createCard}
        <div class="empty-state learning-record-empty">
          <div class="empty-icon">📒</div>
          <p>아직 강사용 기록이 없어요.</p>
          <p class="empty-sub"><strong>새 학습기록</strong> 카드로 시작해보세요.</p>
        </div>`;
      bindListActions();
      return;
    }

    listEl.innerHTML = createCard + sortedItems.map((item) => `
      <div class="learning-record-card conversation-item tutor-conversation-card"
           data-conversation-id="${item.conversationId}" role="button" tabindex="0">
        <button type="button" class="learning-record-menu" aria-label="학습기록 메뉴" data-menu-button="${item.conversationId}">⋮</button>
        <div class="conversation-card-menu" id="conversationMenu-${item.conversationId}" hidden>
          <button type="button" data-rename-id="${item.conversationId}">수정하기</button>
          <button type="button" class="danger" data-delete-id="${item.conversationId}">삭제하기</button>
        </div>
        <div class="conv-icon">📒</div>
        <div class="conv-body">
          <div class="conv-badges"><span class="conv-badge conv-badge-personal">강사 기록</span></div>
          <div class="conv-title">${escapeHtml(item.title || '새 학습기록')}</div>
          <div class="conv-meta">
            ${item.messageCount > 0 ? `메시지 ${item.messageCount}개 · ` : ''}${formatDate(item.lastMessageAt || item.createdAt)}
          </div>
        </div>
        <div class="conv-arrow">›</div>
      </div>
    `).join('');
    bindListActions();
  }

  function bindListActions() {
    listEl.querySelectorAll('[data-create-conversation]').forEach((button) => {
      button.addEventListener('click', createConversationAndOpen);
    });
    listEl.querySelectorAll('[data-conversation-id]').forEach((card) => {
      card.addEventListener('click', () => openDetailPage(Number(card.dataset.conversationId)));
      card.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          openDetailPage(Number(card.dataset.conversationId));
        }
      });
    });
    listEl.querySelectorAll('[data-menu-button]').forEach((button) => {
      button.addEventListener('click', (event) => toggleConversationMenu(event, Number(button.dataset.menuButton)));
    });
    listEl.querySelectorAll('[data-rename-id]').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        renameConversation(Number(button.dataset.renameId), false);
      });
    });
    listEl.querySelectorAll('[data-delete-id]').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        deleteConversation(Number(button.dataset.deleteId), false);
      });
    });
  }

  async function createConversationAndOpen() {
    setBusy(true);
    try {
      const created = await fetchJsonAuth('/api/tutor/conversations', { method: 'POST' });
      openDetailPage(created.conversationId);
    } catch (error) {
      showStatus('새 기록 생성에 실패했습니다.', true);
    } finally {
      setBusy(false);
    }
  }

  function openDetailPage(id) {
    if (!id) return;
    location.href = `/tutor/conversation/dashboard?id=${id}`;
  }

  function wireTutorSidebarLinks() {
    if (!selectedId) return;
    const routes = {
      dashboard: `/tutor/conversation/dashboard?id=${selectedId}`,
      'exam-materials': `/tutor/conversation/materials?id=${selectedId}&menu=exam-materials&filter=EXAM`,
      'study-materials': `/tutor/conversation/materials?id=${selectedId}&menu=study-materials&filter=GENERAL`,
      materials: `/tutor/conversation/materials?id=${selectedId}&menu=materials&filter=ALL`,
      chat: `/tutor/conversation/chat?id=${selectedId}`,
    };
    document.querySelectorAll('.sidebar-link').forEach((link) => {
      const key = link.dataset.menu;
      if (routes[key]) link.href = routes[key];
    });
    document.getElementById('btnChatPage')?.setAttribute('href', routes.chat);
    document.getElementById('btnChatPageCta')?.setAttribute('href', routes.chat);
  }

  async function openConversationDetail(id) {
    try {
      selectedDetail = await fetchJsonAuth(`/api/tutor/conversations/${id}`);
      renderWorkspace(selectedDetail);
      renderMaterials(selectedDetail.sources || []);
      initUploadModule(selectedDetail);
      if ((selectedDetail.sources || []).some((source) => IN_PROGRESS_STATUSES.includes(source.processingStatus))) {
        startPollingIfNeeded();
      }
    } catch (error) {
      selectedDetail = null;
      showStatus('기록을 열 수 없습니다.', true);
    }
  }


  function initUploadModule(detail) {
    if (!window.ConversationUpload || !currentUser) return;
    const config = {
      conversationId: selectedId,
      userId: currentUser.userId,
      studentDefaults: { schoolName: '', grade: '' },
      asAdmin: false,
      uploadUrl: `/api/tutor/conversations/${selectedId}/sources`,
      requireExamMeta: true,
      examMetaDefaults: defaultTutorExamMeta(),
      onComplete: refreshMaterials
    };
    if (window.ConversationUpload.update) {
      window.ConversationUpload.init(config);
      window.ConversationUpload.update(config);
    } else {
      window.ConversationUpload.init(config);
    }
  }

  function defaultTutorExamMeta() {
    const today = new Date();
    return {
      examType: 'OTHER',
      examYear: String(today.getFullYear()),
      examSemester: today.getMonth() + 1 <= 8 ? '1' : '2'
    };
  }

  function wireMaterialsPageLink() {
    if (!materialsPageButton || !selectedId) return;
    materialsPageButton.href = `/tutor/conversation/materials?id=${selectedId}`;
  }

  function openUploadModal(defaultContentKind) {
    if (!window.ConversationUpload) return;
    window.ConversationUpload.open(defaultContentKind);
  }

  async function refreshMaterials() {
    try {
      selectedDetail = await fetchJsonAuth(`/api/tutor/conversations/${selectedId}`);
      renderWorkspace(selectedDetail);
      const sources = selectedDetail.sources || [];
      renderMaterials(sources);
      if (!sources.some((source) => IN_PROGRESS_STATUSES.includes(source.processingStatus))) {
        stopPolling();
      }
    } catch (_) {}
  }

  function startPollingIfNeeded() {
    if (pollTimer) return;
    pollTimer = setInterval(refreshMaterials, POLL_INTERVAL_MS);
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  function renderMaterials(sources) {
    const list = document.getElementById('materialsList');
    const countEl = document.getElementById('materialsCount');
    if (!list || !countEl) return;
    const items = sources || [];
    countEl.textContent = `${items.length}개`;
    list.innerHTML = '';
    if (!items.length) {
      list.innerHTML = '<div class="empty-state">아직 첨부된 자료가 없어요.</div>';
      return;
    }
    groupSources(items).forEach((group) => list.appendChild(renderMaterialGroup(group)));
  }

  function groupSources(sources) {
    const grouped = new Map();
    const singles = [];
    for (const source of sources) {
      if (source.examId != null) {
        if (!grouped.has(source.examId)) grouped.set(source.examId, { examId: source.examId, displayName: source.examDisplayName, items: [] });
        grouped.get(source.examId).items.push(source);
      } else {
        singles.push({ examId: null, displayName: null, items: [source] });
      }
    }
    return [...grouped.values(), ...singles];
  }

  function renderMaterialGroup(group) {
    const first = group.items[0];
    const title = group.displayName || first.fileName || '자료';
    const count = group.items.length;
    const status = pickGroupStatus(group.items);
    const icon = group.examId != null ? '📋' : (first.fileType === 'PDF' ? '📄' : '🖼️');
    const ids = group.items.map((source) => source.sourceId).join(',');
    const item = document.createElement('div');
    item.className = 'material-item';
    item.addEventListener('click', () => {
      location.href = `/tutor/conversation/materials?id=${selectedId}#source-${first.sourceId}`;
    });
    item.innerHTML = `
      <span class="material-icon">${icon}</span>
      <span class="material-name" title="${escapeHtml(title)}">${escapeHtml(title)}</span>
      ${count > 1 ? `<span class="material-meta">· ${count}장</span>` : ''}
      ${statusBadgeHtml(status)}
      <span class="material-open-hint">보기 ›</span>
      <button class="material-delete" title="자료 삭제" data-delete-source-ids="${ids}" data-delete-title="${escapeHtml(title)}">✕</button>
    `;
    item.querySelector('[data-delete-source-ids]')?.addEventListener('click', deleteMaterialGroup);
    return item;
  }

  async function deleteMaterialGroup(event) {
    event.stopPropagation();
    const sourceIds = String(event.currentTarget.dataset.deleteSourceIds || '').split(',').filter(Boolean);
    const title = event.currentTarget.dataset.deleteTitle || '자료';
    if (!sourceIds.length) return;
    if (!confirm(`"${title}" 자료를 영구 삭제할까요?`)) return;
    try {
      for (const sourceId of sourceIds) {
        const res = await fetchAuth(`/api/sources/${sourceId}`, { method: 'DELETE' });
        if (!res.ok && res.status !== 204) throw new Error(await res.text().catch(() => `${res.status}`));
      }
      await refreshMaterials();
    } catch (error) {
      showStatus('자료 삭제에 실패했습니다.', true);
    }
  }

  function statusBadgeHtml(status) {
    switch (status) {
      case 'PENDING':
      case 'ANALYZING':
        return '<span class="material-status analyzing">분석 중</span>';
      case 'PENDING_REVIEW':
        return '<span class="material-status pending-review">검증 대기</span>';
      case 'COMPLETED':
        return '<span class="material-status completed">완료</span>';
      case 'FAILED':
        return '<span class="material-status failed">실패</span>';
      default:
        return status ? `<span class="material-status">${escapeHtml(status)}</span>` : '';
    }
  }

  function pickGroupStatus(items) {
    const priority = ['PENDING', 'ANALYZING', 'PENDING_REVIEW', 'FAILED', 'COMPLETED'];
    for (const target of priority) {
      if (items.some((item) => item.processingStatus === target)) return target;
    }
    return items[0]?.processingStatus || 'COMPLETED';
  }

  function renderWorkspace(detail) {
    if (titleEl) titleEl.textContent = detail.title || '제목 없는 기록';
    if (metaEl) metaEl.textContent = `생성일 ${formatDate(detail.createdAt)} · 메시지 ${(detail.messages || []).length}개`;
    renderMessages(detail.messages || []);
  }

  function renderMessages(messages) {
    if (!messageList) return;
    if (!messages.length) {
      messageList.innerHTML = '<div class="message-empty">아직 메시지가 없습니다. 아래 입력창에서 첫 메시지를 보내세요.</div>';
      return;
    }
    messageList.innerHTML = messages.map((message) => {
      const role = String(message.role || '').toUpperCase() === 'USER' ? 'user' : 'assistant';
      const label = role === 'user' ? '나' : 'AI';
      return `<article class="message ${role}"><span class="message-role">${label}</span>${escapeHtml(message.content || '')}</article>`;
    }).join('');
    messageList.scrollTop = messageList.scrollHeight;
  }

  async function renameConversation(id, stayOnDetail) {
    if (!id) return;
    const listItem = conversations.find((item) => item.conversationId === id);
    const current = (selectedDetail && selectedDetail.conversationId === id && selectedDetail.title) || (listItem && listItem.title) || '';
    const next = prompt('기록 이름을 입력하세요.', current);
    if (next === null) return;
    const title = next.trim();
    if (!title) {
      alert('기록명을 입력해 주세요.');
      return;
    }
    try {
      const updated = await fetchJsonAuth(`/api/tutor/conversations/${id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title })
      });
      if (stayOnDetail) {
        selectedDetail = Object.assign({}, selectedDetail || {}, { conversationId: id, title: updated.title || title });
        if (titleEl) titleEl.textContent = selectedDetail.title;
      } else {
        await loadList();
      }
      showStatus('기록명을 변경했습니다.', false);
    } catch (error) {
      showStatus('기록명 변경에 실패했습니다.', true);
    }
  }

  async function deleteConversation(id, leaveDetail) {
    if (!id) return;
    const detail = selectedDetail && selectedDetail.conversationId === id ? selectedDetail : null;
    const listItem = conversations.find((item) => item.conversationId === id);
    const title = (detail && detail.title) || (listItem && listItem.title) || '제목 없는 기록';
    const ok = confirm(`'${title}' 기록을 삭제할까요?`);
    if (!ok) return;
    try {
      const res = await fetchAuth(`/api/tutor/conversations/${id}`, { method: 'DELETE' });
      if (!res.ok) throw new Error(await res.text().catch(() => ''));
      showStatus('기록을 삭제했습니다.', false);
      if (leaveDetail) {
        location.href = '/tutor/conversation_dashboard';
      } else {
        await loadList();
      }
    } catch (error) {
      showStatus('기록 삭제에 실패했습니다.', true);
    }
  }

  async function sendMessage(event) {
    event.preventDefault();
    if (!selectedId) return;
    const content = messageInput.value.trim();
    if (!content) return;
    sendButton.disabled = true;
    messageInput.disabled = true;
    try {
      await fetchJsonAuth(`/api/tutor/conversations/${selectedId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content })
      });
      messageInput.value = '';
      await openConversationDetail(selectedId);
    } catch (error) {
      showStatus('메시지 전송에 실패했습니다.', true);
    } finally {
      sendButton.disabled = false;
      messageInput.disabled = false;
      messageInput.focus();
    }
  }

  function toggleConversationMenu(event, conversationId) {
    event.stopPropagation();
    document.querySelectorAll('.conversation-card-menu').forEach((menu) => {
      if (menu.id !== `conversationMenu-${conversationId}`) menu.hidden = true;
    });
    const menu = document.getElementById(`conversationMenu-${conversationId}`);
    if (menu) menu.hidden = !menu.hidden;
  }

  function setBusy(busy) {
    document.getElementById('newConversationButton')?.toggleAttribute('disabled', busy);
    document.querySelectorAll('[data-create-conversation]').forEach((button) => {
      button.disabled = busy;
      button.classList.toggle('is-loading', busy);
    });
  }

  function showStatus(message, isError) {
    const banner = document.getElementById('statusBanner');
    if (!banner) return;
    banner.textContent = message;
    banner.className = `status-banner ${isError ? 'error' : 'success'}`;
    window.clearTimeout(showStatus.timer);
    showStatus.timer = window.setTimeout(() => banner.classList.add('hidden'), 3500);
  }

  function formatDate(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
  }

  function numericId(value) {
    const n = Number(value);
    return Number.isFinite(n) && n > 0 ? n : null;
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }
})();
