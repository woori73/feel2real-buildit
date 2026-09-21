// admin_newstart_wiki.js - 부실차주 Wiki 관리자 페이지

let currentUser = null;
let currentPage = 0;
const pageSize = 20;
let currentSort = 'createdAt';

const form = document.getElementById('knowledgeForm');
const formMessage = document.getElementById('kFormMessage');
const modal = document.getElementById('knowledgeModal');

form.addEventListener('submit', saveKnowledge);
modal.addEventListener('click', (event) => { if (event.target === modal) closeKnowledgeModal(); });
const reviewModal = document.getElementById('reviewModal');
if (reviewModal) reviewModal.addEventListener('click', (event) => { if (event.target === reviewModal) closeReviewModal(); });
document.addEventListener('keydown', (event) => { if (event.key === 'Escape') { closeKnowledgeModal(); closeReviewModal(); } });
document.addEventListener('click', (event) => { if (!event.target.closest('.card-menu-wrap')) closeAllMenus(); });

(async () => {
  currentUser = await ensureCurrentUser();
  if (!currentUser || currentUser.role !== 'ADMIN') { location.href = '/wiki'; return; }
  document.getElementById('userName').textContent = window.userDisplayName(currentUser);
  await loadStats();
  loadKnowledge();
  loadReviewQueue();
})();

async function loadStats() {
  try {
    const res = await fetchAuth('/api/admin/newstart/wiki/stats');
    if (!res.ok) throw new Error();
    const stats = await res.json();
    document.getElementById('statTotal').textContent = stats.totalKnowledge || 0;
    document.getElementById('statActive').textContent = stats.activeCount || 0;
    document.getElementById('statReview').textContent = stats.reviewCount || 0;
    document.getElementById('statDraft').textContent = stats.draftCount || 0;
    document.getElementById('statCases').textContent = stats.caseCount || 0;
    document.getElementById('statGaps').textContent = stats.knowledgeGaps || 0;
  } catch (_) {
    console.warn('Stats load failed');
  }
}

async function loadKnowledge() {
  const list = document.getElementById('knowledgeList');
  list.innerHTML = '<div class="empty-card">불러오는 중...</div>';

  const params = new URLSearchParams();
  const q = document.getElementById('searchInput')?.value?.trim();
  const sourceType = document.getElementById('filterSourceType')?.value;
  const category = document.getElementById('filterCategory')?.value;
  const status = document.getElementById('filterStatus')?.value;

  if (q) params.set('q', q);
  if (sourceType) params.set('sourceType', sourceType);
  if (category) params.set('category', category);
  if (status) params.set('status', status);
  params.set('page', currentPage);
  params.set('size', pageSize);
  params.set('sort', currentSort);

  try {
    const res = await fetchAuth(`/api/admin/newstart/wiki?${params.toString()}`);
    if (!res.ok) throw new Error();
    const data = await res.json();

    const items = data.content || [];
    if (items.length === 0) {
      list.innerHTML = '<div class="empty-card">조건에 맞는 지식이 없습니다.</div>';
      return;
    }

    list.innerHTML = renderCreateCard()
      + items.map(renderItem).join('');
  } catch (err) {
    list.innerHTML = '<div class="empty-card error">지식을 불러오지 못했습니다.</div>';
  }
}

function renderCreateCard() {
  return `<article class="knowledge-card create" onclick="openKnowledgeModal()" role="button" tabindex="0"
    onkeydown="if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openKnowledgeModal(); }">
    <div class="create-circle">+</div>
    <div class="create-title">지식 등록</div>
  </article>`;
}

function renderItem(item) {
  const statusClass = item.status === 'ACTIVE' ? '' : item.status === 'REVIEW' ? 'review' : item.status === 'DRAFT' ? 'draft' : 'archived';
  const sourceIcon = sourceIconMap[item.sourceType] || '📄';
  const statusPill = statusPillMap[item.status] || `<span class="pill pill-draft">${item.status}</span>`;

  return `<article class="knowledge-card ${statusClass}">
    <div class="card-menu-wrap">
      <button class="card-menu" type="button" onclick="toggleMenu(event, ${item.knowledgeId})" aria-label="지식 메뉴">⋮</button>
      <div id="menu-${item.knowledgeId}" class="menu-pop">
        <button type="button" onclick="editKnowledge(${item.knowledgeId})">수정</button>
        <button type="button" onclick="changeKnowledgeStatus(${item.knowledgeId}, '${nextStatus(item.status)}')">${nextStatusLabel(item.status)}</button>
        <button type="button" onclick="viewKnowledge(${item.knowledgeId})">상세 보기</button>
        <button type="button" class="danger" onclick="deleteKnowledge(${item.knowledgeId})">삭제</button>
      </div>
    </div>
    <div>
      <div class="knowledge-header">
        <span class="source-icon">${sourceIcon}</span>
        ${statusPill}
        <span class="source-type">${sourceTypeLabel(item.sourceType)}</span>
      </div>
      <h3 class="knowledge-title">${escapeHtml(item.title)}</h3>
      <div class="knowledge-meta">
        <span class="category-tag">${escapeHtml(item.category || '-')}</span>
        ${item.authority ? `<span class="authority">${escapeHtml(item.authority)}</span>` : ''}
        ${item.sourceDate ? `<span class="source-date">${item.sourceDate}</span>` : ''}
      </div>
      ${item.summary ? `<div class="knowledge-summary">${escapeHtml(item.summary)}</div>` : ''}
      ${item.tags ? `<div class="knowledge-tags">${escapeHtml(item.tags).split(',').map(t => `<span class="tag">${escapeHtml(t.trim())}</span>`).join('')}</div>` : ''}
      <div class="knowledge-footer">
        <span class="knowledge-id">ID: ${item.knowledgeId}</span>
        <span class="knowledge-date">${formatDate(item.createdAt)}</span>
      </div>
    </div>
  </article>`;
}

const sourceIconMap = {
  'OFFICIAL_CURRENT': '🏛️',
  'OFFICIAL_HISTORICAL': '📜',
  'AUDIT': '🔍',
  'EBOOK': '📖',
  'CAFE_ARTICLE': '💬',
  'CAFE_COMMENT': '💭',
  'CASE': '🎯',
  'OPERATOR_KNOWLEDGE': '🧠',
  'DERIVED_RULE': '⚙️'
};

const statusPillMap = {
  'ACTIVE': '<span class="pill pill-active">ACTIVE</span>',
  'REVIEW': '<span class="pill pill-review">REVIEW</span>',
  'DRAFT': '<span class="pill pill-draft">DRAFT</span>',
  'ARCHIVED': '<span class="pill pill-archived">ARCHIVED</span>'
};

function sourceTypeLabel(type) {
  const labels = {
    'OFFICIAL_CURRENT': '공식(현행)',
    'OFFICIAL_HISTORICAL': '공식(과거)',
    'AUDIT': '감사원',
    'EBOOK': '전자책',
    'CAFE_ARTICLE': '카페글',
    'CAFE_COMMENT': '카페댓글',
    'CASE': '사례',
    'OPERATOR_KNOWLEDGE': '운영자지식',
    'DERIVED_RULE': '파생규칙'
  };
  return labels[type] || type;
}

function nextStatus(current) {
  const order = ['DRAFT', 'REVIEW', 'ACTIVE', 'ARCHIVED'];
  const idx = order.indexOf(current);
  return order[(idx + 1) % order.length];
}

function nextStatusLabel(current) {
  const labels = {
    'DRAFT': 'REVIEW로 승격',
    'REVIEW': 'ACTIVE로 승인',
    'ACTIVE': 'ARCHIVED로 보관',
    'ARCHIVED': 'DRAFT로 복원'
  };
  return labels[current] || '상태 변경';
}

function openKnowledgeModal() {
  resetKnowledgeForm(false);
  document.getElementById('knowledgeFormTitle').textContent = '지식 등록';
  openKnowledgeModalDialog();
}

function openKnowledgeModalDialog() {
  modal.classList.add('open');
  modal.setAttribute('aria-hidden', 'false');
  setTimeout(() => document.getElementById('kTitle').focus(), 0);
}

function closeKnowledgeModal() {
  modal.classList.remove('open');
  modal.setAttribute('aria-hidden', 'true');
  closeAllMenus();
}

function toggleMenu(event, id) {
  event.stopPropagation();
  const menu = document.getElementById(`menu-${id}`);
  const wasOpen = menu.classList.contains('open');
  closeAllMenus();
  if (!wasOpen) menu.classList.add('open');
}

function closeAllMenus() {
  document.querySelectorAll('.menu-pop.open').forEach(menu => menu.classList.remove('open'));
}

async function editKnowledge(id) {
  closeAllMenus();
  try {
    const res = await fetchAuth(`/api/admin/newstart/wiki/${id}`);
    if (!res.ok) throw new Error();
    const item = await res.json();
    populateForm(item);
    document.getElementById('knowledgeFormTitle').textContent = '지식 수정';
    openKnowledgeModalDialog();
  } catch (err) {
    alert('지식을 불러오지 못했습니다.');
  }
}

function populateForm(item) {
  document.getElementById('knowledgeId').value = item.knowledgeId || '';
  document.getElementById('kTitle').value = item.title || '';
  document.getElementById('kSourceType').value = item.sourceType || '';
  document.getElementById('kCategory').value = item.category || '';
  document.getElementById('kAuthority').value = item.authority || '';
  document.getElementById('kSourceDate').value = item.sourceDate || '';
  document.getElementById('kEffectiveFrom').value = item.effectiveFrom || '';
  document.getElementById('kEffectiveTo').value = item.effectiveTo || '';
  document.getElementById('kTags').value = item.tags || '';
  document.getElementById('kSourceUrl').value = item.sourceUrl || '';
  document.getElementById('kSummary').value = item.summary || '';
  document.getElementById('kContent').value = item.content || '';
  document.getElementById('kRelatedCaseIds').value = item.relatedCaseIds || '';
  document.getElementById('kConfidence').value = item.confidence || 'MEDIUM';
  document.getElementById('kEffectiveFrom2').value = item.effectiveFrom || '';
  document.getElementById('kEffectiveTo2').value = item.effectiveTo || '';
  document.getElementById('kVersion').value = item.version || 1;
  document.getElementById('kStatus').value = item.status || 'DRAFT';
  document.getElementById('kRelatedRuleIds').value = item.relatedRuleIds || '';
}

async function saveKnowledge(e) {
  e.preventDefault();
  const id = document.getElementById('knowledgeId').value;
  const data = {
    sourceType: document.getElementById('kSourceType').value,
    category: document.getElementById('kCategory').value,
    title: document.getElementById('kTitle').value,
    summary: document.getElementById('kSummary').value,
    content: document.getElementById('kContent').value,
    authority: document.getElementById('kAuthority').value,
    tags: document.getElementById('kTags').value,
    sourceUrl: document.getElementById('kSourceUrl').value,
    sourceDate: document.getElementById('kSourceDate').value || null,
    effectiveFrom: document.getElementById('kEffectiveFrom').value || null,
    effectiveTo: document.getElementById('kEffectiveTo').value || null,
    authority: document.getElementById('kAuthority').value,
    confidence: document.getElementById('kConfidence').value,
    relatedCaseIds: document.getElementById('kRelatedCaseIds').value,
    relatedRuleIds: document.getElementById('kRelatedRuleIds').value,
    status: document.getElementById('kStatus').value,
    version: parseInt(document.getElementById('kVersion').value) || 1,
    effectiveFrom: document.getElementById('kEffectiveFrom').value || null,
    effectiveTo: document.getElementById('kEffectiveTo').value || null
  };

  if (!data.sourceType || !data.category || !document.getElementById('kTitle').value.trim()) {
    formMessage.textContent = '필수 항목(출처, 카테고리, 제목)을 모두 입력해주세요.';
    formMessage.className = 'error';
    return;
  }

  try {
    const url = id ? `/api/admin/newstart/wiki/${id}` : '/api/admin/newstart/wiki';
    const method = id ? 'PUT' : 'POST';
    const res = await fetchAuth(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) throw new Error(await res.text());
    closeKnowledgeModal();
    resetKnowledgeForm(false);
    await loadKnowledge();
    await loadReviewQueue();
  } catch (err) {
    formMessage.textContent = '저장 실패: ' + (err.message || '입력값을 확인해주세요.');
    formMessage.className = 'error';
  }
}

async function changeKnowledgeStatus(id, newStatus) {
  closeAllMenus();
  const reason = prompt(`상태를 ${newStatus}로 변경하시겠습니까?\n이유를 입력해주세요:`);
  if (reason === null) return;
  if (!reason.trim()) {
    alert('이유를 입력해주세요.');
    return;
  }
  try {
    const params = new URLSearchParams({ status: newStatus, reason: reason.trim() });
    const res = await fetchAuth(`/api/admin/newstart/wiki/${id}/status?${params.toString()}`, {
      method: 'PUT'
    });
    if (!res.ok) throw new Error(await res.text());
    await loadKnowledge();
    await loadReviewQueue();
  } catch (err) {
    alert('상태 변경 실패: ' + err.message);
  }
}

async function viewKnowledge(id) {
  closeAllMenus();
  try {
    const res = await fetchAuth(`/api/admin/newstart/wiki/${id}`);
    if (!res.ok) throw new Error();
    const item = await res.json();
    alert(`[${item.title}]\n\n${item.content?.substring(0, 500)}...`);
  } catch (err) {
    alert('상세 정보를 불러오지 못했습니다.');
  }
}

async function deleteKnowledge(id) {
  closeAllMenus();
  if (!confirm('이 지식을 ARCHIVED 처리하시겠습니까? (원자료·이력은 보존됩니다)')) return;
  try {
    const res = await fetchAuth(`/api/admin/newstart/wiki/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(await res.text());
    await loadKnowledge();
  } catch (err) {
    alert('삭제 실패: ' + err.message);
  }
}

function resetKnowledgeForm(clearMessage = true) {
  form.reset();
  document.getElementById('knowledgeId').value = '';
  document.getElementById('kFormMessage').textContent = '';
  document.getElementById('kFormMessage').className = 'muted';
  document.getElementById('kVersion').value = '1';
  document.getElementById('kStatus').value = 'DRAFT';
  document.getElementById('kConfidence').value = 'MEDIUM';
  if (clearMessage) formMessage.textContent = '';
}

function escapeHtml(value) { return String(value || '').replace(/[&<>'"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch])); }
function formatDate(str) { if (!str) return ''; const d = new Date(str); return d.toLocaleDateString('ko-KR'); }

// ── ADMIN Review Workflow (REVIEW 검토함) ──────────────────────────────
// REVIEW 목록 → 상세(경고+provenance) → 승인/유지/보관.
// 경고가 있는 항목은 확인 체크 없이 ACTIVE 승인이 되지 않는다 (서버도 409로 차단).

let reviewDetailCache = null;

async function loadReviewQueue() {
  const box = document.getElementById('reviewQueue');
  if (!box) return;
  box.innerHTML = '<div class="empty-card">REVIEW 대기 목록을 불러오는 중...</div>';
  try {
    const res = await fetchAuth('/api/admin/newstart/wiki/review?page=0&size=20');
    if (!res.ok) throw new Error();
    const data = await res.json();
    const items = data.content || [];
    const countEl = document.getElementById('reviewQueueCount');
    if (countEl) countEl.textContent = (data.totalElements != null ? data.totalElements : items.length);
    if (items.length === 0) {
      box.innerHTML = '<div class="empty-card">REVIEW 대기 항목이 없습니다.</div>';
      return;
    }
    box.innerHTML = items.map(renderReviewRow).join('');
  } catch (_) {
    box.innerHTML = '<div class="empty-card error">REVIEW 목록을 불러오지 못했습니다.</div>';
  }
}

function renderReviewRow(item) {
  return `<div class="review-row" data-id="${item.knowledgeId}">
    <span class="source-icon">${(sourceIconMap[item.sourceType] || '📄')}</span>
    <div class="review-row-main">
      <div class="review-row-title">${escapeHtml(item.title)}</div>
      <div class="review-row-meta">${escapeHtml(sourceTypeLabel(item.sourceType))} · ${escapeHtml(item.category || '-')} · ${escapeHtml(item.authority || 'authority 미상')} · ID ${item.knowledgeId}</div>
    </div>
    <button class="btn btn-secondary" type="button" onclick="openReviewDetail(${item.knowledgeId})">검토하기</button>
  </div>`;
}

async function openReviewDetail(id) {
  closeAllMenus();
  try {
    const res = await fetchAuth(`/api/admin/newstart/wiki/review/${id}`);
    if (!res.ok) throw new Error(await res.text());
    reviewDetailCache = await res.json();
    renderReviewModal();
  } catch (err) {
    alert('검토 정보를 불러오지 못했습니다: ' + err.message);
  }
}

function renderReviewModal() {
  const detail = reviewDetailCache;
  if (!detail || !detail.item) return;
  const item = detail.item;
  const warnings = detail.warnings || [];
  const history = detail.history || [];

  document.getElementById('reviewTitle').textContent = item.title || '(제목 없음)';
  document.getElementById('reviewMeta').innerHTML =
    `<span class="pill pill-review">REVIEW</span> ` +
    `<span class="source-type">${escapeHtml(sourceTypeLabel(item.sourceType))}</span> ` +
    `<span class="category-tag">${escapeHtml(item.category || '-')}</span><br>` +
    `발행/작성 주체: ${escapeHtml(item.authority || '미상')} · ` +
    `원문일: ${escapeHtml(item.sourceDate || '-')} · ` +
    `적용: ${escapeHtml(item.effectiveFrom || '-')} ~ ${escapeHtml(item.effectiveTo || '-')}<br>` +
    (item.sourceUrl
      ? `원문 링크: <a href="${escapeHtml(item.sourceUrl)}" target="_blank" rel="noopener">${escapeHtml(item.sourceUrl)}</a>`
      : '<strong>원문 링크 없음 ⚠</strong>') +
    (item.relatedCaseIds ? `<br>연관 사례: ${escapeHtml(item.relatedCaseIds)}` : '') +
    (item.relatedRuleIds ? `<br>연관 규칙: ${escapeHtml(item.relatedRuleIds)}` : '');

  document.getElementById('reviewWarnings').innerHTML = warnings.length === 0
    ? '<div class="warn-ok">경고 없음 — 승인 가능 상태입니다.</div>'
    : '<div class="warn-head">⚠ 승인 전 확인 필요 (' + warnings.length + '건)</div>' +
      warnings.map(w => `<div class="warn-item"><strong>[${escapeHtml(w.code)}]</strong> ${escapeHtml(w.message)}</div>`).join('');

  const confirmBox = document.getElementById('reviewConfirmBox');
  confirmBox.style.display = warnings.length === 0 ? 'none' : 'block';
  document.getElementById('reviewConfirmed').checked = false;

  document.getElementById('reviewEvidence').textContent =
    (item.summary ? '[요약]\n' + item.summary + '\n\n' : '') +
    (item.content ? '[원문/정리]\n' + item.content : '(내용 없음)');

  document.getElementById('reviewHistory').innerHTML = history.length === 0
    ? '<div class="muted">이력 없음</div>'
    : history.map(h => `<div class="history-row">[${formatDate(h.createdAt)}] ${escapeHtml(h.fromStatus || '-')} → ${escapeHtml(h.toStatus)} · ${escapeHtml(h.changedBy || '-')} · ${escapeHtml(h.changeReason || '')}</div>`).join('');

  document.getElementById('reviewReason').value = '';
  const modal = document.getElementById('reviewModal');
  modal.classList.add('open');
  modal.setAttribute('aria-hidden', 'false');
}

function closeReviewModal() {
  const modal = document.getElementById('reviewModal');
  if (!modal) return;
  modal.classList.remove('open');
  modal.setAttribute('aria-hidden', 'true');
  reviewDetailCache = null;
}

function reviewReasonOrAlert(actionLabel) {
  const reason = document.getElementById('reviewReason').value.trim();
  if (!reason) {
    alert(actionLabel + ' 사유를 입력해주세요.');
    return null;
  }
  return reason;
}

async function postReviewAction(action, body) {
  const id = reviewDetailCache.item.knowledgeId;
  const res = await fetchAuth(`/api/admin/newstart/wiki/review/${id}/${action}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (res.status === 409) {
    const data = await res.json().catch(() => ({}));
    const list = (data.warnings || []).map(w => `[${w.code}] ${w.message}`).join('\n');
    alert('경고 확인이 필요합니다. 확인 체크 후 다시 승인해주세요.\n\n' + list);
    if (data.warnings) {
      reviewDetailCache.warnings = data.warnings;
      renderReviewModal();
    }
    return false;
  }
  if (!res.ok) throw new Error(await res.text());
  return true;
}

async function approveReview() {
  const warnings = (reviewDetailCache && reviewDetailCache.warnings) || [];
  const confirmed = document.getElementById('reviewConfirmed').checked;
  if (warnings.length > 0 && !confirmed) {
    alert('경고를 확인하고 체크박스에 동의해야 승인할 수 있습니다.');
    return;
  }
  const reason = reviewReasonOrAlert('승인');
  if (reason === null) return;
  try {
    const ok = await postReviewAction('approve', { reason, confirmed });
    if (!ok) return;
    closeReviewModal();
    await loadKnowledge();
    await loadReviewQueue();
    await loadStats();
  } catch (err) {
    alert('승인 실패: ' + err.message);
  }
}

async function keepReview() {
  const reason = document.getElementById('reviewReason').value.trim() || 'REVIEW 유지';
  try {
    const ok = await postReviewAction('keep', { reason });
    if (!ok) return;
    closeReviewModal();
    await loadReviewQueue();
  } catch (err) {
    alert('REVIEW 유지 실패: ' + err.message);
  }
}

async function archiveReview() {
  const reason = reviewReasonOrAlert('보관/반려');
  if (reason === null) return;
  if (!confirm('이 항목을 ARCHIVED 처리하시겠습니까?')) return;
  try {
    const ok = await postReviewAction('archive', { reason });
    if (!ok) return;
    closeReviewModal();
    await loadKnowledge();
    await loadReviewQueue();
    await loadStats();
  } catch (err) {
    alert('보관 실패: ' + err.message);
  }
}