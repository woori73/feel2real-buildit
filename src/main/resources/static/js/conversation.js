/** 분석이 아직 진행 중이라 폴링을 계속해야 하는 상태. */
const IN_PROGRESS_STATUSES = ['PENDING', 'ANALYZING'];
/** 폴링 주기 (ms) — 분석 중인 자료가 있을 때만 작동. */
const POLL_INTERVAL_MS = 3000;

let currentUser    = null;
let currentStudent = null;
let conversationId = null;
let isSending      = false;
let pollTimer      = null;
let asAdmin        = false;  // Phase 10 — 어드민 임퍼소네이트 모드 (보기 전용)
let isTutorConversation = false;

/* Phase 10 — 라이트박스 상태. items: [{sourceId, page}, ...] 페이지를 펼친 평면 리스트 */
let lightboxItems  = [];
let lightboxIndex  = 0;

/* ── 초기화 ── */

async function init() {
  const params = new URLSearchParams(location.search);
  conversationId = params.get('id');
  isTutorConversation = window.CONVERSATION_CONTEXT === 'TUTOR' || location.pathname.startsWith('/tutor/conversation/chat');
  if (!conversationId) { location.href = isTutorConversation ? '/tutor/conversation_dashboard' : '/stdt_dashboard'; return; }

  // sessionStorage 비어도 JWT 쿠키로 복원 (탭/브라우저 재시작 대응)
  currentUser = await ensureCurrentUser();
  if (!currentUser) { location.href = '/home'; return; }

  // Phase 10 — 어드민 임퍼소네이트 모드. impersonatedStudent를 currentStudent로 사용.
  asAdmin = params.get('asAdmin') === '1';
  let storedStudent;
  if (isTutorConversation) {
    if (currentUser.role !== 'TUTOR') {
      location.href = dashboardPathForRole(currentUser.role);
      return;
    }
    currentStudent = null;
  } else if (asAdmin) {
    if (currentUser.role !== 'ADMIN') {
      alert('관리자 권한이 필요합니다.');
      location.href = '/home';
      return;
    }
    storedStudent = sessionStorage.getItem('impersonatedStudent');
    if (!storedStudent) { location.href = '/admin/students'; return; }
    currentStudent = JSON.parse(storedStudent);
  } else if (currentUser.role === 'TUTOR' || currentUser.role === 'ADMIN') {
    // 강사/Admin이 학생 대화를 직접 열람(임퍼소네이트 아님) — sessionStorage엔 학생 정보가 없으므로
    // conversation API에서 가져온다. 접근 권한(강사=본인 lesson에 APPROVED로 매칭된 대화만,
    // admin=전체)은 백엔드 verifyOwnsConversation이 검증한다 — 권한 없으면 403 → /home.
    try {
      const res = await fetchAuth(`/api/conversations/${conversationId}`);
      if (!res.ok) throw new Error();
      const detail = await res.json();
      if (!detail.student) throw new Error();
      currentStudent = detail.student;
    } catch {
      location.href = '/home';
      return;
    }
  } else {
    storedStudent = sessionStorage.getItem('currentStudent');
    if (!storedStudent) { location.href = '/home'; return; }
    currentStudent = JSON.parse(storedStudent);
  }

  document.getElementById('userNameDisplay').textContent = `${window.userDisplayName(currentUser)}님`;

  if (asAdmin) {
    enterAdminViewOnlyMode();
  }

  wireSidebarLinks();  // Phase 12 — 사이드바 4메뉴 href 세팅

  const uploadConfig = {
    conversationId,
    // 임퍼소네이트 모드에서는 대상 학생의 userId를 보내야 source.uploaded_by가 학생으로 저장된다.
    // 어드민 userId가 들어가면 학생이 본 계정으로 들어왔을 때 verifyOwnsSource에서 403이 떨어진다.
    userId: asAdmin ? currentStudent.userId : currentUser.userId,
    studentDefaults: currentStudent
        ? { schoolName: currentStudent.schoolName, grade: currentStudent.grade }
        : { schoolName: '', grade: '' },
    asAdmin,
    onComplete: handleUploadComplete,
  };
  if (isTutorConversation) {
    Object.assign(uploadConfig, {
      uploadUrl: `/api/tutor/conversations/${conversationId}/sources`,
      requireExamMeta: true,
      examMetaDefaults: defaultTutorExamMeta(),
    });
  }
  ConversationUpload.init(uploadConfig);
  if (ConversationUpload.update) ConversationUpload.update(uploadConfig);

  await loadConversation();
  document.addEventListener('keydown', lightboxKeyHandler); // Phase 10 — 키보드 ESC/←/→
}

function detailApiUrl() {
  return isTutorConversation
      ? `/api/tutor/conversations/${conversationId}`
      : `/api/conversations/${conversationId}`;
}

function messageApiUrl() {
  if (isTutorConversation) return `/api/tutor/conversations/${conversationId}/messages`;
  const adminTarget = (asAdmin && currentStudent && currentStudent.userId)
      ? `?targetUserId=${currentStudent.userId}` : '';
  return `/api/conversations/${conversationId}/messages${adminTarget}`;
}

function conversationHomeUrl() {
  return isTutorConversation ? '/tutor/conversation_dashboard' : '/stdt_dashboard';
}

function defaultTutorExamMeta() {
  const today = new Date();
  return {
    examType: 'OTHER',
    examYear: String(today.getFullYear()),
    examSemester: today.getMonth() + 1 <= 8 ? '1' : '2'
  };
}

/** 업로드 완료 후: 분석 배너 띄우고 sources 즉시 갱신 + 폴링 시작. */
async function handleUploadComplete() {
  const banner = document.getElementById('analysisBanner');
  if (banner) {
    banner.style.display = 'flex';
    const txt = document.getElementById('analysisBannerText');
    if (txt) txt.textContent = '자료 처리 중...';
  }
  await refreshSources();
  startPollingIfNeeded();
}

/**
 * Phase 12 — 사이드바 메뉴 4개에 현재 conversationId를 박은 링크를 세팅.
 * asAdmin 모드면 query에 asAdmin=1을 propagate.
 */
function wireSidebarLinks() {
  const adminSuffix = asAdmin ? '&asAdmin=1' : '';
  const routes = isTutorConversation ? {
    dashboard: `/tutor/conversation/dashboard?id=${conversationId}`,
    'exam-materials': `/tutor/conversation/materials?id=${conversationId}&menu=exam-materials&filter=EXAM`,
    'study-materials': `/tutor/conversation/materials?id=${conversationId}&menu=study-materials&filter=GENERAL`,
    materials: `/tutor/conversation/materials?id=${conversationId}&menu=materials&filter=ALL`,
    chat: `/tutor/conversation/chat?id=${conversationId}`,
  } : {
    dashboard:         `/conversation/dashboard?id=${conversationId}${adminSuffix}`,
    'exam-materials':  `/conversation/exam-materials?id=${conversationId}${adminSuffix}`,
    'study-materials': `/conversation/study-materials?id=${conversationId}${adminSuffix}`,
    materials:         `/conversation/materials?id=${conversationId}${adminSuffix}`,
    'tutor-materials': `/conversation/tutor-materials?id=${conversationId}${adminSuffix}`,
    homework:          `/conversation/homework?id=${conversationId}${adminSuffix}`,
    review:            `/conversation/review?id=${conversationId}${adminSuffix}`,
    chat:              `/conversation/chat?id=${conversationId}${adminSuffix}`,
    report:            `/conversation/report?id=${conversationId}${adminSuffix}`,
  };
  document.querySelectorAll('.sidebar-link').forEach(a => {
    const key = a.dataset.menu;
    if (routes[key]) a.href = routes[key];
  });

  // 사이드바 하단 학생 정보 카드 — sessionStorage 기반 (currentStudent 또는 impersonated)
  const box = document.getElementById('sidebarStudent');
  if (box && currentStudent) {
    box.innerHTML =
      `<div class="sidebar-student-name">` +
      `${escapeHtml(currentStudent.name || '')}` +
      `<span class="sidebar-student-grade">· ${currentStudent.grade ? 'Grade ' + currentStudent.grade : ''}</span>` +
      `</div>` +
      `<div class="sidebar-student-meta">${escapeHtml(currentStudent.schoolName || '')}</div>`;
  }

  const backButton = document.getElementById('btnBackToTutorDashboard');
  if (backButton && isTutorConversation) {
    backButton.onclick = () => { location.href = `/tutor/conversation/dashboard?id=${conversationId}`; };
  }
}

/**
 * 어드민 임퍼소네이트 모드 진입 시 상단 안내 배너만 띄운다. 작업 권한은 학생 본인과 동일 —
 * 채팅 전송/파일 추가/자료 삭제 모두 가능. 백엔드가 ADMIN role + targetUserId로 통과시킴.
 */
function enterAdminViewOnlyMode() {
  const banner = document.createElement('div');
  banner.style.cssText =
    'background:#e53e3e;color:#fff;padding:10px 16px;font-size:13px;' +
    'display:flex;align-items:center;justify-content:space-between;gap:12px;';
  banner.innerHTML =
    `<span>👁️ <strong>관리자 모드</strong> — ${escapeHtml(currentStudent.name)}` +
    `${currentStudent.schoolName ? ' (' + escapeHtml(currentStudent.schoolName) +
      (currentStudent.grade ? ' · ' + currentStudent.grade + '학년' : '') + ')' : ''}` +
    `의 대화를 조회 중. 학생 본인과 동일한 권한으로 작업합니다.</span>` +
    `<button onclick="location.href='/stdt_dashboard?asAdmin=1'" style="background:rgba(0,0,0,0.2);` +
      `color:#fff;border:none;padding:6px 12px;border-radius:6px;cursor:pointer;font-size:12px;">학생 대시보드로 ›</button>`;
  document.body.insertBefore(banner, document.body.firstChild);
}

/* ── 대화 로드 ── */

async function loadConversation() {
  try {
    const res = await fetchAuth(detailApiUrl());
    if (!res.ok) throw new Error();
    const data = await res.json();

    document.getElementById('convTitle').textContent = data.lessonName || data.title || '새 대화';
    document.title = `${data.lessonName || data.title || '새 대화'} - F2R`;
    renderTutorSidebarCard(data);

    renderFileList(data.sources || []);
    renderMessageHistory(data.messages || []);
  } catch {
    alert('대화를 불러오지 못했습니다.');
    location.href = conversationHomeUrl();
  }
}

function renderTutorSidebarCard(detail) {
  if (!isTutorConversation) return;
  const box = document.getElementById('sidebarStudent');
  if (!box) return;
  box.innerHTML = `
    <div class="sidebar-student-name">${escapeHtml(detail.title || '강사 기록')}</div>
    <div class="sidebar-student-meta">자료를 참고해 AI Chat을 이어갑니다.</div>
  `;
}

function renderFileList(sources) {
  const listEl = document.getElementById('fileList');
  cacheSources(sources);  // Phase 10 — 라이트박스가 sourceId로 source 정보를 찾을 수 있게 캐시
  if (sources.length === 0) {
    listEl.innerHTML = '<div class="file-empty">파일을 추가하면<br>AI가 내용을 참고해요</div>';
    updateAnalysisBanner([]);
    return;
  }
  // Phase 9 — 같은 examId를 가진 sources는 한 줄로 묶어 표시
  const groups = groupSources(sources);
  listEl.innerHTML = groups.map(groupItemHtml).join('');
  updateAnalysisBanner(sources);
}

/**
 * sources를 examId 기준으로 그룹화. examId가 null이면 각자 단독 그룹.
 * 표시 순서는 원본 순서를 따른다.
 */
function groupSources(sources) {
  const groups = [];
  const examMap = new Map();
  for (const s of sources) {
    if (s.examId == null) {
      groups.push({ examId: null, displayName: null, sources: [s] });
      continue;
    }
    if (!examMap.has(s.examId)) {
      const g = { examId: s.examId, displayName: s.examDisplayName, sources: [] };
      examMap.set(s.examId, g);
      groups.push(g);
    }
    examMap.get(s.examId).sources.push(s);
  }
  return groups;
}

/** 한 그룹의 HTML. examId가 없거나 단독이면 파일명 그대로, 그룹이면 시험 표시명 + 개수. */
function groupItemHtml(group) {
  if (group.examId == null) {
    return fileItemHtml(group.sources[0]);
  }
  const title = group.displayName || '시험지';
  const count = group.sources.length;
  const status = pickGroupStatus(group.sources);
  const countHtml = count > 1 ? `<span class="file-group-count">${count}장</span>` : '';
  const ids = group.sources.map(s => s.sourceId).join(',');
  // PENDING_REVIEW 뱃지는 묶음의 첫 source id로 검증 페이지에 진입 (모든 source가 같은 analysis_result 공유)
  const firstSourceId = group.sources[0].sourceId;
  return `
    <div class="file-item file-group" data-exam-id="${group.examId}" data-source-ids="${ids}" onclick="openLightboxForGroup(this)">
      <span class="file-item-icon">📋</span>
      <span class="file-item-name" title="${escapeHtml(title)}">${escapeHtml(title)}${countHtml}</span>
      ${statusBadgeHtml(status, firstSourceId)}
    </div>`;
}

/** 첨부 파일 한 건의 HTML (상태 뱃지 포함). 단독/일반자료용. */
function fileItemHtml(src) {
  return `
    <div class="file-item" data-source-id="${src.sourceId}" onclick="openLightboxForSource(${src.sourceId})">
      <span class="file-item-icon">${src.fileType === 'PDF' ? '📄' : '🖼️'}</span>
      <span class="file-item-name" title="${escapeHtml(src.fileName)}">${escapeHtml(src.fileName)}</span>
      ${statusBadgeHtml(src.processingStatus, src.sourceId)}
    </div>`;
}

/**
 * 그룹의 대표 상태를 고른다. 우선순위:
 * PENDING > ANALYZING > PENDING_REVIEW > FAILED > COMPLETED
 * (가장 "진행 중"인 상태가 노출되어 학생이 알 수 있도록)
 */
function pickGroupStatus(sources) {
  const priority = ['PENDING', 'ANALYZING', 'PENDING_REVIEW', 'FAILED', 'COMPLETED'];
  for (const p of priority) {
    if (sources.some(s => s.processingStatus === p)) return p;
  }
  return null;
}

/**
 * processing_status 값에 맞는 뱃지 HTML. 알 수 없는 상태면 빈 문자열.
 * PENDING_REVIEW 뱃지는 클릭 시 검증 페이지(/exam_review?id=...)로 이동 — sourceId 필요.
 */
function statusBadgeHtml(status, sourceId) {
  switch (status) {
    case 'PENDING':
    case 'ANALYZING':
      return '<span class="file-status analyzing">분석 중</span>';
    case 'PENDING_REVIEW':
      return `<span class="file-status review clickable" onclick="openExamReview(event, ${sourceId})">검증하기 ›</span>`;
    case 'COMPLETED':
      return '<span class="file-status complete">완료</span>';
    case 'FAILED':
      return '<span class="file-status failed">실패</span>';
    default:
      return '';
  }
}

/** PENDING_REVIEW 뱃지 클릭 핸들러 — 부모 file-item의 라이트박스 클릭을 막고 검증 페이지로 이동. */
function openExamReview(event, sourceId) {
  event.stopPropagation();
  if (!sourceId) return;
  const adminSuffix = asAdmin ? '&asAdmin=1' : '';
  const returnUrl = isTutorConversation
      ? `/tutor/conversation/chat?id=${conversationId}`
      : `/conversation/dashboard?id=${conversationId}${adminSuffix}`;
  location.href = `/exam_review?id=${sourceId}&returnUrl=${encodeURIComponent(returnUrl)}`;
}

/** 분석 중인 자료가 있으면 배너를 보여주고 폴링 시작. */
function updateAnalysisBanner(sources) {
  const inProgress = sources.filter(s => IN_PROGRESS_STATUSES.includes(s.processingStatus));
  const banner = document.getElementById('analysisBanner');
  if (inProgress.length > 0) {
    document.getElementById('analysisBannerText').textContent =
      `${inProgress.length}개 자료 분석 중...`;
    banner.style.display = 'flex';
    startPollingIfNeeded();
  } else {
    banner.style.display = 'none';
    stopPolling();
  }
}

function renderMessageHistory(messages) {
  const listEl = document.getElementById('messageList');
  if (messages.length === 0) {
    listEl.innerHTML = `
      <div class="welcome-msg">
        <div class="welcome-icon">🎓</div>
        <p>안녕하세요! 파일을 업로드하거나 바로 질문해보세요.</p>
      </div>`;
    return;
  }
  listEl.innerHTML = '';
  messages.forEach(m => appendMessage(m.role, m.content));
  scrollToBottom();
}

/* ── 파일 업로드 진입점 (모달 자체는 ConversationUpload 모듈) ── */

/** "+ 파일 추가" 버튼 핸들러. 모듈에 위임. */
function triggerFileInput() {
  ConversationUpload.open();
}

/* ── 폴링: 분석 진행 상태 갱신 ── */

function startPollingIfNeeded() {
  if (pollTimer) return; // 이미 폴링 중
  pollTimer = setInterval(refreshSources, POLL_INTERVAL_MS);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

/** 현재 대화의 sources 목록만 다시 불러와 UI 갱신. */
async function refreshSources() {
  try {
    const res = await fetchAuth(detailApiUrl());
    if (!res.ok) return;
    const data = await res.json();
    renderFileList(data.sources || []);
  } catch {
    // 일시적 네트워크 오류는 무시 (다음 tick에서 재시도)
  }
}

/* ── 메시지 전송 ── */

function handleKeyDown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
}

async function sendMessage() {
  // admin 임퍼소네이트도 학생과 동일하게 메시지 전송 가능 — 백엔드는 ?targetUserId로 통과.
  if (isSending) return;
  const input   = document.getElementById('chatInput');
  const content = input.value.trim();
  if (!content) return;

  isSending = true;
  input.value   = '';
  autoResize(input);
  document.getElementById('btnSend').disabled = true;

  appendMessage('USER', content);
  scrollToBottom();

  const typingEl = appendTypingIndicator();

  try {
    // 학생 본인: principal에서 userId 자동 결정.
    // ADMIN 임퍼소네이트: ?targetUserId=<학생의 user_id> 부착해 학생 명의로 발화.
    // 강사용 기록: tutor 전용 message API로 발화.
    const res = await fetchAuth(
      messageApiUrl(),
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content })
      }
    );
    typingEl.remove();

    if (!res.ok) {
      // 백엔드가 JSON body로 message를 보내면 그걸 사용자에게 표시 (예: 409 분석 차단 안내)
      const msg = await readErrorMessage(res, '죄송합니다, 응답 중 오류가 발생했습니다.');
      appendMessage('ASSISTANT', msg);
      // 분석 중이라 막힌 경우라면 폴링이 자동으로 상태를 따라잡도록 즉시 한 번 갱신
      if (res.status === 409) refreshSources();
    } else {
      const data = await res.json();
      appendMessage('ASSISTANT', data.assistantMessage.content);

      // 제목이 바뀌었을 수 있으니 갱신
      const detailRes = await fetchAuth(detailApiUrl());
      if (detailRes.ok) {
        const detail = await detailRes.json();
        document.getElementById('convTitle').textContent = detail.lessonName || detail.title || '새 대화';
        document.title = `${detail.lessonName || detail.title || '새 대화'} - F2R`;
      }
    }
  } catch {
    typingEl.remove();
    appendMessage('ASSISTANT', '서버 연결에 실패했습니다. 다시 시도해주세요.');
  } finally {
    isSending = false;
    document.getElementById('btnSend').disabled = false;
    scrollToBottom();
  }
}

function appendMessage(role, content) {
  const listEl = document.getElementById('messageList');
  const welcome = listEl.querySelector('.welcome-msg');
  if (welcome) welcome.remove();

  const div = document.createElement('div');
  div.className = `message ${role === 'USER' ? 'message-user' : 'message-assistant'}`;
  div.innerHTML = `
    <div class="message-bubble">${formatContent(content)}</div>`;
  listEl.appendChild(div);
  return div;
}

function appendTypingIndicator() {
  const listEl = document.getElementById('messageList');
  const div = document.createElement('div');
  div.className = 'message message-assistant';
  div.innerHTML = '<div class="message-bubble typing-indicator"><span></span><span></span><span></span></div>';
  listEl.appendChild(div);
  scrollToBottom();
  return div;
}

function scrollToBottom() {
  const listEl = document.getElementById('messageList');
  listEl.scrollTop = listEl.scrollHeight;
}

/* ── 라이트박스 미리보기 (Phase 10) ── */

/** 현재 fileList에 표시된 sources 캐시. lightbox가 그룹/단독을 매핑할 때 사용. */
let lastSourcesById = {};

/** lightbox용 source 캐시 갱신 — renderFileList가 호출할 때 같이 채워준다. */
function cacheSources(sources) {
  lastSourcesById = {};
  for (const s of sources) lastSourcesById[s.sourceId] = s;
}

function openLightboxForSource(sourceId) {
  const src = lastSourcesById[sourceId];
  if (!src) return;
  openLightbox([src]);
}

function openLightboxForGroup(el) {
  const ids = (el.dataset.sourceIds || '').split(',').map(Number).filter(Boolean);
  const sources = ids.map(id => lastSourcesById[id]).filter(Boolean);
  if (sources.length === 0) return;
  openLightbox(sources);
}

/**
 * sources 배열로 라이트박스를 연다.
 * 각 source의 pageCount를 펼쳐 평면 리스트로 만든다 (PDF 페이지도 한 장씩 슬라이드).
 */
function openLightbox(sources) {
  lightboxItems = [];
  for (const s of sources) {
    const pages = s.pageCount || 1;
    for (let p = 1; p <= pages; p++) {
      lightboxItems.push({ sourceId: s.sourceId, page: p });
    }
  }
  if (lightboxItems.length === 0) return;
  lightboxIndex = 0;
  document.getElementById('lightbox').style.display = 'flex';
  renderLightbox();
}

function closeLightbox() {
  document.getElementById('lightbox').style.display = 'none';
  document.getElementById('lightboxImage').src = '';
  lightboxItems = [];
}

function lightboxPrev() {
  if (lightboxIndex > 0) { lightboxIndex--; renderLightbox(); }
}

function lightboxNext() {
  if (lightboxIndex < lightboxItems.length - 1) { lightboxIndex++; renderLightbox(); }
}

function renderLightbox() {
  const item = lightboxItems[lightboxIndex];
  if (!item) return;
  document.getElementById('lightboxImage').src =
    `/api/sources/${item.sourceId}/file?page=${item.page}`;
  const dl = document.getElementById('lightboxDownload');
  if (dl) dl.href = `/api/sources/${item.sourceId}/download?page=${item.page}`;
  document.getElementById('lightboxCounter').textContent =
    `${lightboxIndex + 1} / ${lightboxItems.length}`;
  document.querySelector('.lightbox-prev').disabled = (lightboxIndex === 0);
  document.querySelector('.lightbox-next').disabled = (lightboxIndex === lightboxItems.length - 1);
}

/** 배경 클릭(이미지/버튼 제외) 시 닫기. */
function lightboxBackdropClick(e) {
  if (e.target.id === 'lightbox') closeLightbox();
}

/** 키보드: ESC 닫기, ← / → 페이지 이동. */
function lightboxKeyHandler(e) {
  if (document.getElementById('lightbox').style.display === 'none') return;
  if (e.key === 'Escape') { closeLightbox(); e.preventDefault(); }
  else if (e.key === 'ArrowLeft') { lightboxPrev(); e.preventDefault(); }
  else if (e.key === 'ArrowRight') { lightboxNext(); e.preventDefault(); }
}

/* ── 유틸 ── */

/**
 * 에러 응답에서 표시 메시지를 추출.
 * 백엔드(GlobalExceptionHandler)는 JSON body로 { message: "...", pendingSources: [...] } 또는
 * text body로 메시지를 보낼 수 있다. 둘 다 안 통하면 fallback 반환.
 */
async function readErrorMessage(res, fallback) {
  try {
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('application/json')) {
      const body = await res.json();
      if (body && typeof body.message === 'string' && body.message.trim()) {
        return body.message;
      }
    } else {
      const text = await res.text();
      if (text && text.trim()) return text.trim();
    }
  } catch {
    // ignore
  }
  return fallback;
}

function autoResize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 160) + 'px';
}

function formatContent(text) {
  return escapeHtml(text).replace(/\n/g, '<br>');
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

init();
