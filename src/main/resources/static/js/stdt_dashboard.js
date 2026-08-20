let currentUser    = null;
let currentStudent = null;
let asAdmin        = false;  // Phase 10 — 어드민 임퍼소네이트 모드
let conversationCache = [];
let editingConversationId = null;

/* ── 초기화 ── */

async function init() {
  // sessionStorage 비어도 JWT 쿠키로 복원 (탭/브라우저 재시작 대응)
  currentUser = await ensureCurrentUser();
  if (!currentUser) { location.href = '/home'; return; }

  // Phase 10 — URL ?asAdmin=1 이면 임퍼소네이트 모드
  asAdmin = new URLSearchParams(location.search).get('asAdmin') === '1';

  if (asAdmin) {
    // 어드민이 학생 보기 모드. impersonatedStudent를 currentStudent로 사용.
    if (currentUser.role !== 'ADMIN') {
      alert('관리자 권한이 필요합니다.');
      location.href = '/home';
      return;
    }
    const impersonated = sessionStorage.getItem('impersonatedStudent');
    if (!impersonated) { location.href = '/admin/students'; return; }
    currentStudent = JSON.parse(impersonated);
    renderImpersonationBanner();
  } else {
    let storedStudent = sessionStorage.getItem('currentStudent');
    if (!storedStudent && currentUser.role === 'STUDENT') {
      try {
        const res = await fetch('/api/users/students/me');
        if (res.ok) {
          storedStudent = await res.text();
          sessionStorage.setItem('currentStudent', storedStudent);
        }
      } catch (_) {}
    }
    if (!storedStudent) { location.href = '/home'; return; }
    currentStudent = JSON.parse(storedStudent);
  }

  document.getElementById('userNameDisplay').textContent = `${window.userDisplayName(currentUser)}님`;
  document.getElementById('avatarText').textContent      = currentStudent.name.charAt(0);
  document.getElementById('studentName').textContent     = currentStudent.name;
  document.getElementById('studentDetail').textContent   =
    `${currentStudent.schoolName} · ${currentStudent.grade}학년`;

  // admin 임퍼소네이트도 학생과 동일하게 새 대화/업로드/삭제 가능 — 백엔드는 role+targetStudentId로 통과.

  await Promise.all([loadConversations(), loadPendingReviews(), loadLearningMaterials(), loadLessonApplications()]);
}

/**
 * Phase 10 — 어드민 임퍼소네이트 모드에서 화면 최상단에 빨간 배너를 노출한다.
 */
function renderImpersonationBanner() {
  const banner = document.createElement('div');
  banner.style.cssText =
    'background:#e53e3e;color:#fff;padding:10px 16px;font-size:13px;' +
    'display:flex;align-items:center;justify-content:space-between;gap:12px;';
  banner.innerHTML =
    `<span>👁️ <strong>관리자 모드</strong> — ${escapeHtml(currentStudent.name)}` +
    `${currentStudent.schoolName ? ' (' + escapeHtml(currentStudent.schoolName) +
      (currentStudent.grade ? ' · ' + currentStudent.grade + '학년' : '') + ')' : ''}` +
    `의 대시보드를 조회 중. 학생 본인과 동일한 권한으로 작업합니다.</span>` +
    `<button onclick="exitImpersonation()" style="background:rgba(0,0,0,0.2);` +
      `color:#fff;border:none;padding:6px 12px;border-radius:6px;cursor:pointer;font-size:12px;">학생 목록으로 ›</button>`;
  document.body.insertBefore(banner, document.body.firstChild);
}

function exitImpersonation() {
  sessionStorage.removeItem('impersonatedStudent');
  location.href = '/admin/students';
}

async function handleLogout() {
  // 서버에서 JWT 쿠키를 삭제(Max-Age=0). 실패해도 클라이언트 측 sessionStorage는 클리어한다.
  try { await fetch('/api/auth/logout', { method: 'POST' }); } catch (_) {}
  sessionStorage.clear();
  location.href = '/';
}

/* ── 대화 목록 ── */

/**
 * ADMIN 임퍼소네이트 모드에서만 query 파라미터로 target ID를 부착한다.
 * 본인 학생 진입 시에는 JWT가 자동으로 자기 ID를 결정하므로 query 불필요.
 */
function adminTargetQuery(kind) {
  if (!asAdmin || !currentStudent) return '';
  const prefix = (kind === 'student') ? 'targetStudentId' : 'targetUserId';
  const value  = (kind === 'student') ? currentStudent.studentId : currentStudent.userId;
  return value ? `${prefix}=${value}` : '';
}

async function loadConversations() {
  const listEl = document.getElementById('conversationList');
  try {
    const q = adminTargetQuery('student');
    const res = await fetchAuth(`/api/conversations${q ? '?' + q : ''}`);
    if (!res.ok) throw new Error();
    const items = await res.json();
    conversationCache = items;
    renderConversations(items, listEl);
  } catch {
    listEl.innerHTML = '<div class="empty-state">대화 목록을 불러오지 못했습니다.</div>';
  }
}

function renderConversations(items, listEl) {
  const sortedItems = [...items].sort((a, b) =>
    new Date(b.lastMessageAt || b.createdAt || 0) - new Date(a.lastMessageAt || a.createdAt || 0));
  const createCard = `
    <button type="button" class="learning-record-card learning-record-create new-learning-record-action" onclick="createNewConversation()">
      <span class="create-plus">+</span>
      <span class="create-title">새 학습기록</span>
    </button>`;
  if (items.length === 0) {
    listEl.innerHTML = `
      ${createCard}
      <div class="empty-state learning-record-empty">
        <div class="empty-icon">📒</div>
        <p>아직 학습기록이 없어요.</p>
        <p class="empty-sub"><strong>새 학습기록</strong> 카드로 시작해보세요.</p>
      </div>`;
    return;
  }

  listEl.innerHTML = createCard + sortedItems.map(item => {
    const hasLesson = Boolean(item.lessonName);
    const badge = hasLesson
      ? '<span class="conv-badge conv-badge-lesson">수업 연결</span>'
      : '<span class="conv-badge conv-badge-personal">개인 기록</span>';
    const lessonHtml = hasLesson
      ? `<div class="conv-lesson">연결 수업: ${escapeHtml(item.lessonName)}</div>`
      : '';
    const academyHtml = hasLesson && item.academyName
      ? ` · ${escapeHtml(item.academyName)}`
      : '';
    return `
    <div class="learning-record-card conversation-item ${hasLesson ? 'lesson-linked' : ''}" onclick="openConversation(${item.conversationId})">
      <button type="button" class="learning-record-menu" aria-label="학습기록 메뉴" onclick="toggleConversationMenu(event, ${item.conversationId})">⋮</button>
      <div class="conversation-card-menu" id="conversationMenu-${item.conversationId}" hidden onclick="event.stopPropagation()">
        <button type="button" onclick="openConversationEditModal(${item.conversationId})">수정하기</button>
        <button type="button" class="danger" onclick="deleteConversation(${item.conversationId})">삭제하기</button>
      </div>
      <div class="conv-icon">${hasLesson ? '🎓' : '📒'}</div>
      <div class="conv-body">
        <div class="conv-badges">${badge}</div>
        <div class="conv-title">${escapeHtml(item.title || '새 학습기록')}</div>
        ${lessonHtml}
        <div class="conv-meta">
          ${item.sourceCount > 0 ? `파일 ${item.sourceCount}개 · ` : ''}
          ${item.messageCount > 0 ? `메시지 ${item.messageCount}개 · ` : ''}
          ${formatDate(item.lastMessageAt || item.createdAt)}${academyHtml}
        </div>
      </div>
      <div class="conv-arrow">›</div>
    </div>`;
  }).join('');
}

/* ── 검증 대기 시험지 목록 ── */

async function loadPendingReviews() {
  const section = document.getElementById('pendingReviewSection');
  const listEl  = document.getElementById('pendingReviewList');
  try {
    const q = adminTargetQuery('user');
    const res = await fetchAuth(`/api/sources/pending-review${q ? '?' + q : ''}`);
    if (!res.ok) throw new Error();
    const items = await res.json();
    if (!items.length) {
      section.hidden = true;
      return;
    }
    section.hidden = false;
    // conversation.js의 groupSources 패턴 동일 — 같은 examId면 카드 1개로 묶고
    // 검증 페이지 진입은 묶음의 첫 sourceId 사용 (모든 source는 같은 analysis_result 공유)
    const groups = groupPendingByExam(items);
    listEl.innerHTML = groups.map(group => pendingGroupHtml(group)).join('');
  } catch {
    section.hidden = true;
  }
}

/** examId가 같은 source들을 한 그룹으로 묶는다. examId가 없으면 source별 단독 그룹. */
function groupPendingByExam(items) {
  const groups = [];
  const examMap = new Map();
  for (const item of items) {
    if (item.examId == null) {
      groups.push({ examId: null, displayName: null, sources: [item] });
      continue;
    }
    if (!examMap.has(item.examId)) {
      const g = { examId: item.examId, displayName: item.examDisplayName, sources: [] };
      examMap.set(item.examId, g);
      groups.push(g);
    }
    examMap.get(item.examId).sources.push(item);
  }
  return groups;
}

function pendingGroupHtml(group) {
  const first = group.sources[0];
  const title = group.displayName || first.originalFileName || '시험지';
  return `
    <div class="pending-review-item" onclick="openExamReview(${first.sourceId})">
      <div class="pending-icon">📝</div>
      <div class="pending-body">
        <div class="pending-title">${escapeHtml(title)}</div>
        <div class="pending-meta">분석 완료 · ${formatDate(first.createdAt)}</div>
      </div>
      <span class="pending-cta">확인하기 ›</span>
    </div>`;
}

function openExamReview(sourceId) {
  location.href = `/exam_review?id=${sourceId}&returnUrl=${encodeURIComponent(currentPageUrl())}`;
}

/* ── 받은 학습자료 (Phase 2 §5.5) ── */

let learningMaterialsCache = [];   // 모달에서 다시 참조하기 위해 캐싱
let pendingCompleteMaterialId = null;

async function loadLearningMaterials() {
  const section = document.getElementById('learningMaterialsSection');
  const listEl  = document.getElementById('learningMaterialsList');
  try {
    const q = adminTargetQuery('student');
    const res = await fetchAuth(`/api/learning-materials${q ? '?' + q : ''}`);
    if (!res.ok) throw new Error();
    const items = await res.json();
    learningMaterialsCache = items;
    if (!items.length) {
      section.hidden = true;
      return;
    }
    section.hidden = false;
    listEl.innerHTML = items.map(materialHtml).join('');
  } catch {
    section.hidden = true;
  }
}

function materialHtml(item) {
  const completed = item.status === 'COMPLETED';
  const statusLabel = completed
    ? `<span class="lm-status lm-status-done">제출 완료</span>`
    : `<span class="lm-status lm-status-pending">풀이 대기</span>`;
  const descHtml = item.description
    ? `<div class="lm-desc">${escapeHtml(item.description)}</div>` : '';
  const completeBtnLabel = completed ? '풀이 다시 업로드' : '풀이 업로드';
  const answersHtml = completed && item.answers && item.answers.length
    ? `<div class="lm-answers">
         ${item.answers.map(a => `
           <a class="lm-answer-chip" href="/api/learning-materials/${item.materialId}/answers/${a.answerId}/file" target="_blank" rel="noopener">
             📎 ${escapeHtml(a.originalFileName || ('파일 ' + a.orderNum))}
           </a>`).join('')}
       </div>`
    : '';
  return `
    <div class="lm-item">
      <div class="lm-head">
        <div class="lm-title-wrap">
          <span class="lm-icon">📘</span>
          <div>
            <div class="lm-title">${escapeHtml(item.title)}</div>
            <div class="lm-meta">
              ${escapeHtml(item.assignedByName || '')}님이 ${formatDate(item.assignedAt)} 부여
              · ${statusLabel}
            </div>
          </div>
        </div>
        <div class="lm-actions">
          ${item.hasProblemFile
            ? `<button class="lm-btn-small lm-btn-download" onclick="downloadMaterial(${item.materialId})">📥 PDF 받기</button>`
            : ''}
          <button class="lm-btn-small lm-btn-upload" onclick="openCompleteModal(${item.materialId})">${completeBtnLabel}</button>
        </div>
      </div>
      ${descHtml}
      ${answersHtml}
    </div>`;
}

function downloadMaterial(materialId) {
  // 백엔드에서 Content-Disposition: attachment를 내려주므로 location.href로 충분.
  window.open(`/api/learning-materials/${materialId}/download`, '_blank');
}

function openCompleteModal(materialId) {
  pendingCompleteMaterialId = materialId;
  const item = learningMaterialsCache.find(m => m.materialId === materialId);
  const sub = item && item.status === 'COMPLETED'
    ? '이미 업로드된 풀이가 있어요. 새로 업로드하면 기존 결과는 사라집니다.'
    : '학습자료 풀이 결과를 사진(JPG/PNG) 또는 PDF로 업로드하세요.';
  document.getElementById('completeModalSub').textContent = sub;
  document.getElementById('completeModalTitle').textContent =
    item ? `${item.title} — 풀이 업로드` : '풀이 결과 업로드';
  document.getElementById('completeFiles').value = '';
  document.getElementById('completeFileList').innerHTML = '';
  document.getElementById('completeSubmitBtn').disabled = true;
  document.getElementById('completeModal').hidden = false;
}

function closeCompleteModal() {
  pendingCompleteMaterialId = null;
  document.getElementById('completeModal').hidden = true;
}

function onCompleteFilesChange() {
  const input = document.getElementById('completeFiles');
  const listEl = document.getElementById('completeFileList');
  const submitBtn = document.getElementById('completeSubmitBtn');
  const files = Array.from(input.files || []);
  if (!files.length) {
    listEl.innerHTML = '';
    submitBtn.disabled = true;
    return;
  }
  listEl.innerHTML = files
    .map(f => `<div class="lm-file-row">📄 ${escapeHtml(f.name)} <span class="lm-file-size">(${formatBytes(f.size)})</span></div>`)
    .join('');
  submitBtn.disabled = false;
}

async function submitComplete() {
  if (!pendingCompleteMaterialId) return;
  const input = document.getElementById('completeFiles');
  const submitBtn = document.getElementById('completeSubmitBtn');
  const files = Array.from(input.files || []);
  if (!files.length) return;

  submitBtn.disabled = true;
  submitBtn.textContent = '업로드 중...';
  try {
    const form = new FormData();
    files.forEach(f => form.append('files', f));
    const res = await fetchAuth(`/api/learning-materials/${pendingCompleteMaterialId}/complete`, {
      method: 'POST',
      body: form
    });
    if (!res.ok) {
      const msg = await res.text().catch(() => '');
      throw new Error(msg || '업로드 실패');
    }
    closeCompleteModal();
    await loadLearningMaterials();
  } catch (e) {
    alert('업로드 실패: ' + (e.message || ''));
    submitBtn.disabled = false;
    submitBtn.textContent = '업로드';
  }
}

function formatBytes(bytes) {
  if (!bytes) return '0 B';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}


/* ── 신청한 수업 ── */

async function loadLessonApplications() {
  const section = document.getElementById('lessonApplicationsSection');
  const listEl = document.getElementById('lessonApplicationsList');
  if (!section || currentUser.role !== 'STUDENT') return;
  try {
    const res = await fetchAuth('/api/lesson-applications/me');
    if (!res.ok) throw new Error();
    const items = await res.json();
    const activeItems = items.filter((item) => item.status === 'APPROVED');
    if (!activeItems.length) {
      section.hidden = true;
      listEl.innerHTML = '';
      return;
    }
    section.hidden = false;
    listEl.innerHTML = activeItems.map(applicationHtml).join('');
  } catch {
    section.hidden = true;
  }
}

function applicationHtml(item) {
  const canCancel = item.status === 'APPROVED';
  const communityLink = item.status === 'APPROVED'
    ? `<a class="lm-btn lm-btn-primary" href="/lessons/${item.lessonId}/community">커뮤니티</a>`
    : '';
  return `<div class="lesson-application-item">
    <div>
      <div class="lesson-application-title">${escapeHtml(item.lessonName)}</div>
      <div class="lesson-application-meta">${escapeHtml(item.academyName)} · ${labelApplicationStatus(item.status)} · ${formatDate(item.appliedAt)}</div>
    </div>
    <div class="lesson-application-actions">
      <a class="lm-btn lm-btn-cancel" href="/academies/${encodeURIComponent(item.academySlug)}">Academy</a>
      ${communityLink}
      ${canCancel ? `<button class="lm-btn lm-btn-primary" onclick="cancelLessonApplication(${item.applicationId})">신청 취소</button>` : ''}
    </div>
  </div>`;
}

async function cancelLessonApplication(applicationId) {
  if (!confirm('수업 신청을 취소할까요?')) return;
  try {
    const res = await fetchAuth(`/api/lesson-applications/${applicationId}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(await res.text());
    await loadLessonApplications();
  } catch (e) {
    alert('신청 취소에 실패했습니다.');
  }
}

function labelApplicationStatus(status) {
  return ({ APPROVED: '신청 완료', CANCELED: '취소됨' }[status]) || status;
}

/* ── 새 학습기록 생성 ── */

async function createNewConversation() {
  const buttons = document.querySelectorAll('.new-learning-record-action');
  buttons.forEach(btn => {
    btn.disabled = true;
    btn.classList.add('is-loading');
  });
  try {
    // 학생 본인: principal에서 studentId 자동 결정. ADMIN 임퍼소네이트: ?targetStudentId 부착.
    const q = adminTargetQuery('student');
    const res = await fetchAuth(`/api/conversations${q ? '?' + q : ''}`, { method: 'POST' });
    if (!res.ok) throw new Error();
    const { conversationId } = await res.json();
    // admin 임퍼소네이트 모드면 새 대화 진입 후에도 모드를 유지
    const suffix = asAdmin ? `&asAdmin=1` : '';
    location.href = `/conversation/dashboard?id=${conversationId}${suffix}`;
  } catch {
    alert('학습기록 생성에 실패했습니다. 다시 시도해주세요.');
    buttons.forEach(btn => {
      btn.disabled = false;
      btn.classList.remove('is-loading');
    });
  }
}

function openConversation(conversationId) {
  // Phase 10 — 임퍼소네이트 모드면 대화 페이지에도 asAdmin 유지
  // Phase 12 — 기존 대화 클릭 시 자료/분석 한눈에 볼 수 있도록 대시보드로 진입.
  //           "새 대화" 버튼도 먼저 대시보드로 진입한다.
  const suffix = asAdmin ? `&asAdmin=1` : '';
  location.href = `/conversation/dashboard?id=${conversationId}${suffix}`;
}

function toggleConversationMenu(event, conversationId) {
  event.preventDefault();
  event.stopPropagation();
  document.querySelectorAll('.conversation-card-menu').forEach(menu => {
    if (menu.id !== `conversationMenu-${conversationId}`) menu.hidden = true;
  });
  const menu = document.getElementById(`conversationMenu-${conversationId}`);
  if (menu) menu.hidden = !menu.hidden;
}

document.addEventListener('click', () => {
  document.querySelectorAll('.conversation-card-menu').forEach(menu => { menu.hidden = true; });
});

function openConversationEditModal(conversationId) {
  const item = conversationCache.find(c => c.conversationId === conversationId);
  editingConversationId = conversationId;
  document.querySelectorAll('.conversation-card-menu').forEach(menu => { menu.hidden = true; });
  const input = document.getElementById('conversationTitleInput');
  input.value = item ? (item.title || '새 학습기록') : '';
  document.getElementById('conversationEditSubmitBtn').disabled = false;
  document.getElementById('conversationEditModal').hidden = false;
  setTimeout(() => input.focus(), 0);
}

function closeConversationEditModal() {
  editingConversationId = null;
  document.getElementById('conversationEditModal').hidden = true;
}

async function submitConversationEdit() {
  if (!editingConversationId) return;
  const input = document.getElementById('conversationTitleInput');
  const title = input.value.trim();
  if (!title) {
    alert('제목을 입력해 주세요.');
    input.focus();
    return;
  }
  const submitBtn = document.getElementById('conversationEditSubmitBtn');
  submitBtn.disabled = true;
  submitBtn.textContent = '저장 중...';
  try {
    const res = await fetchAuth(`/api/conversations/${editingConversationId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title })
    });
    if (!res.ok) throw new Error(await res.text().catch(() => ''));
    closeConversationEditModal();
    await loadConversations();
  } catch (e) {
    alert('학습기록 수정에 실패했습니다.');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = '저장';
  }
}

async function deleteConversation(conversationId) {
  const item = conversationCache.find(c => c.conversationId === conversationId);
  const title = item ? (item.title || '새 학습기록') : '이 학습기록';
  document.querySelectorAll('.conversation-card-menu').forEach(menu => { menu.hidden = true; });
  if (!confirm(`'${title}' 학습기록을 삭제할까요?

삭제한 학습기록은 되돌릴 수 없습니다.`)) return;
  try {
    const res = await fetchAuth(`/api/conversations/${conversationId}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(await res.text().catch(() => ''));
    await Promise.all([loadConversations(), loadPendingReviews(), loadLearningMaterials(), loadLessonApplications()]);
  } catch (e) {
    alert('학습기록 삭제에 실패했습니다.');
  }
}

/* ── 유틸 ── */

function currentPageUrl() {
  return `${location.pathname}${location.search}${location.hash}`;
}

function formatDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  const now = new Date();
  const diffMs = now - d;
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1)  return '방금 전';
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24)   return `${diffH}시간 전`;
  const diffD = Math.floor(diffH / 24);
  if (diffD < 7)    return `${diffD}일 전`;
  return d.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' });
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

init();
