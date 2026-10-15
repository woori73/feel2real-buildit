/* ============================================================
 * Phase 12 — Uploaded Materials page
 *
 * URL: /conversation/materials?id={conversationId}
 *
 * 책임:
 *  1) GET /api/conversations/{id} 로 첨부 자료 fetch
 *  2) exam_id 기준 그룹화 후 카드 그리드
 *  3) 카드 클릭 → 라이트박스 (PDF page도 평면 슬라이드)
 *
 * 라이트박스 로직은 conversation.js와 동일 패턴 — 페이지 독립을 위해 복사.
 * ============================================================ */

const conversationId = new URLSearchParams(location.search).get('id');
const isAdminView    = new URLSearchParams(location.search).get('asAdmin') === '1';
const isTutorMaterials = window.MATERIAL_CONTEXT === 'TUTOR' || location.pathname.startsWith('/tutor/');

let lightboxItems = [];
let lightboxIndex = 0;
let lastSourcesById = {};

// 분석 폴링 — conversation_dashboard.js 패턴을 이 페이지에 국소 복제(공유 모듈 추출 안 함: 전역 심볼 충돌 회피).
const IN_PROGRESS_STATUSES = ['PENDING', 'ANALYZING'];
const POLL_INTERVAL_MS = 3000;
let pollTimer = null;

document.addEventListener('DOMContentLoaded', init);
document.addEventListener('keydown', lightboxKeyHandler);

async function init() {
  if (!conversationId) {
    alert('잘못된 접근입니다. 대화 ID가 없습니다.');
    location.href = '/stdt_dashboard';
    return;
  }
  if (!sessionStorage.getItem('currentUser')) {
    location.href = '/home';
    return;
  }

  wireSidebarLinks();

  try {
    const detail = await fetchJson(detailApiUrl());
    renderSidebarStudent(detail);
    renderMaterials(detail.sources || []);
    maybeScrollToHash();
    wireUpload(detail);
  } catch (e) {
    console.error('materials load failed', e);
    document.getElementById('materialsGrid').innerHTML =
      '<div class="empty-state">자료를 불러오지 못했어요.</div>';
  }
}


function detailApiUrl() {
  return isTutorMaterials
      ? `/api/tutor/conversations/${conversationId}`
      : `/api/conversations/${conversationId}`;
}

function defaultTutorExamMeta() {
  const today = new Date();
  return {
    examType: 'OTHER',
    examYear: String(today.getFullYear()),
    examSemester: today.getMonth() + 1 <= 8 ? '1' : '2'
  };
}

/* ----- 사이드바 ------------------------------------------------ */

function wireSidebarLinks() {
  const adminSuffix = isAdminView ? '&asAdmin=1' : '';
  const routes = isTutorMaterials ? {
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
}

function renderSidebarStudent(detail) {
  const student = detail.student;
  const box = document.getElementById('sidebarStudent');
  if (!box) return;
  if (!student) {
    box.innerHTML = `
      <div class="sidebar-student-name">${escapeHtml(detail.title || '강사 기록')}</div>
      <div class="sidebar-student-meta">강사용 업로드 자료 모음</div>
    `;
    return;
  }
  box.innerHTML = `
    <div class="sidebar-student-name">
      ${escapeHtml(student.name || '')}
      <span class="sidebar-student-grade">· ${student.grade ? `Grade ${student.grade}` : ''}</span>
    </div>
    <div class="sidebar-student-meta">
      ${escapeHtml(student.schoolName || '')}<br>
      ${escapeHtml(detail.title || '')}
    </div>
  `;
}

/* ----- 자료 그리드 --------------------------------------------- */

function renderMaterials(sources) {
  sources = filterByMaterialFilter(sources);
  cacheSources(sources);
  const grid = document.getElementById('materialsGrid');
  const countChip = document.getElementById('materialsCountChip');
  grid.innerHTML = '';
  countChip.textContent = `${sources.length}개`;

  if (sources.length === 0) {
    grid.innerHTML = '<div class="empty-state">아직 첨부된 자료가 없어요.</div>';
    return;
  }

  const groups = groupSources(sources);
  groups.forEach(g => grid.appendChild(renderGroupCard(g)));
}

/**
 * window.MATERIAL_FILTER에 따라 학생 업로드 자료를 거른다.
 *  - ALL: 학생 업로드 전체(학교 자료 모음)
 *  - EXAM: ContentKind EXAM_BLANK/EXAM_GRADED(시험지 대시보드)
 *  - GENERAL: ContentKind GENERAL(학습자료 대시보드)
 * 강사 시험대비 자료(TUTOR_MATERIAL)·숙제(HOMEWORK_*)는 이 뷰들에서 제외한다.
 */
function filterByMaterialFilter(sources) {
  const f = window.MATERIAL_FILTER || 'ALL';
  const studentOnly = sources.filter(s => s.sourceType == null || s.sourceType === 'STUDENT_MATERIAL');
  if (f === 'EXAM') {
    return studentOnly.filter(s => s.contentKind === 'EXAM_BLANK' || s.contentKind === 'EXAM_GRADED');
  }
  if (f === 'GENERAL') {
    return studentOnly.filter(s => s.contentKind === 'GENERAL');
  }
  return studentOnly;
}

function groupSources(sources) {
  const grouped = new Map();
  const singles = [];
  for (const s of sources) {
    if (s.examId != null) {
      if (!grouped.has(s.examId)) grouped.set(s.examId, { examId: s.examId, displayName: s.examDisplayName, items: [] });
      grouped.get(s.examId).items.push(s);
    } else {
      singles.push({ examId: null, displayName: null, items: [s] });
    }
  }
  return [...grouped.values(), ...singles];
}

function renderGroupCard(group) {
  const first = group.items[0];
  const title = group.displayName || first.fileName;
  const count = group.items.length;
  const status = pickGroupStatus(group.items);
  const isExam = group.examId != null;
  const icon = isExam ? '📋' : (first.fileType === 'PDF' ? '📄' : '🖼️');
  const ids = group.items.map(i => i.sourceId).join(',');

  const card = document.createElement('div');
  card.className = 'material-card';
  card.id = `source-${first.sourceId}`;
  card.dataset.sourceIds = ids;
  card.onclick = () => openLightboxForGroup(card);

  const tags = [];
  if (isExam) tags.push(`<span class="material-card-tag exam">시험지</span>`);
  if (count > 1) tags.push(`<span class="material-card-tag">${count}장</span>`);
  if (first.fileType) tags.push(`<span class="material-card-tag">${escapeHtml(first.fileType)}</span>`);

  card.innerHTML = `
    <div class="material-card-header">
      <span class="material-card-icon">${icon}</span>
      <span class="material-card-name" title="${escapeHtml(title)}">${escapeHtml(title)}</span>
      <button class="material-card-delete" title="자료 삭제"
              onclick="deleteMaterialGroup(event, '${ids}', '${escapeHtml(title)}')">✕</button>
    </div>
    <div class="material-card-meta">${tags.join('')}</div>
    ${statusBadgeHtml(status, first.sourceId)}
  `;
  return card;
}

/** 자료 묶음 영구 삭제 — conversation_dashboard.js와 동일 동작. */
async function deleteMaterialGroup(event, sourceIdsCsv, title) {
  event.stopPropagation();
  if (!confirm(`"${title}" 자료를 영구 삭제할까요?\n\n첨부된 대화에서 제거되고, 시험지였다면 채점 결과(점수/답안)도 함께 사라집니다.`)) {
    return;
  }
  const sourceIds = sourceIdsCsv.split(',').map(s => s.trim()).filter(Boolean);
  try {
    for (const sid of sourceIds) {
      const res = await fetchAuth(`/api/sources/${sid}`, { method: 'DELETE' });
      if (!res.ok && res.status !== 204) {
        const text = await res.text();
        throw new Error(text || `${res.status}`);
      }
    }
    // 자료 목록 다시 fetch + 갱신
    const detail = await fetchJson(detailApiUrl());
    renderMaterials(detail.sources || []);
  } catch (e) {
    alert('삭제 실패: ' + (e.message || ''));
  }
}

/** material 카드용 상태 뱃지. PENDING_REVIEW는 클릭하면 검증 페이지로. */
function statusBadgeHtml(status, sourceId) {
  switch (status) {
    case 'PENDING':
    case 'ANALYZING':
      return '<span class="material-card-status analyzing">분석 중</span>';
    case 'PENDING_REVIEW':
      return `<span class="material-card-status pending-review clickable" onclick="goExamReview(event, ${sourceId})">검증하기 ›</span>`;
    case 'COMPLETED':
      return '<span class="material-card-status completed">완료</span>';
    case 'FAILED':
      return '<span class="material-card-status failed">실패</span>';
    default:
      return `<span class="material-card-status">${status}</span>`;
  }
}

function goExamReview(event, sourceId) {
  event.stopPropagation();
  if (!sourceId) return;
  const adminSuffix = isAdminView ? '&asAdmin=1' : '';
  const returnUrl = isTutorMaterials
      ? `/tutor/conversation/materials?id=${conversationId}`
      : `/conversation/materials?id=${conversationId}${adminSuffix}`;
  location.href = `/exam_review?id=${sourceId}&returnUrl=${encodeURIComponent(returnUrl)}`;
}

const STATUS_PRIORITY = ['PENDING', 'ANALYZING', 'PENDING_REVIEW', 'FAILED', 'COMPLETED'];

function pickGroupStatus(items) {
  for (const target of STATUS_PRIORITY) {
    if (items.some(i => i.processingStatus === target)) return target;
  }
  return items[0]?.processingStatus || 'COMPLETED';
}

function maybeScrollToHash() {
  if (!location.hash) return;
  const el = document.getElementById(location.hash.slice(1));
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.style.outline = '2px solid #3751d1';
    setTimeout(() => { el.style.outline = ''; }, 1500);
  }
}

/* ----- 업로드 + 분석 폴링 (conversation_dashboard.js 패턴 복제) ----- */

function readCurrentUser() {
  const raw = sessionStorage.getItem('currentUser');
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

/** 어드민 임퍼소네이트 진입 시 박아둔 학생 정보. asAdmin=1일 때만 의미 있음. */
function readImpersonatedStudent() {
  const raw = sessionStorage.getItem('impersonatedStudent');
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

/** window.MATERIAL_FILTER → 모달 디폴트 자료 타입. 시험지 대시보드는 시험지, 그외는 일반 자료. */
function defaultContentKindForFilter() {
  return (window.MATERIAL_FILTER || 'ALL') === 'EXAM' ? 'EXAM_BLANK' : 'GENERAL';
}

/** + 파일 추가 버튼 진입점. */
function openMaterialsUpload() {
  if (window.ConversationUpload) ConversationUpload.open(defaultContentKindForFilter());
}

/** 업로드 모듈 초기화 + 첫 로드 시 분석 중 자료 있으면 폴링. dashboard.js 임퍼소네이트 로직 복제. */
function wireUpload(detail) {
  if (!window.ConversationUpload) return;
  const currentUser = readCurrentUser();
  // 임퍼소네이트 모드면 대상 학생 userId를 보내야 source.uploaded_by가 학생으로 저장됨(아니면 읽기 403).
  const uploaderUserId = isAdminView
      ? readImpersonatedStudent()?.userId
      : currentUser?.userId;
  const config = {
    conversationId,
    userId: uploaderUserId,
    studentDefaults: {
      schoolName: detail.student?.schoolName || '',
      grade:      detail.student?.grade || '',
    },
    asAdmin: isAdminView,
    onComplete: handleUploadComplete,
  };
  if (isTutorMaterials) {
    config.uploadUrl = `/api/tutor/conversations/${conversationId}/sources`;
    config.requireExamMeta = true;
    config.examMetaDefaults = defaultTutorExamMeta();
  }
  ConversationUpload.init(config);
  if (ConversationUpload.update) ConversationUpload.update(config);
  if ((detail.sources || []).some(s => IN_PROGRESS_STATUSES.includes(s.processingStatus))) {
    startPollingIfNeeded();
  }
}

/** 업로드 완료 콜백 — 그자리 갱신 + 분석 폴링. */
async function handleUploadComplete() {
  await refreshMaterials();
  startPollingIfNeeded();
}

/** 자료 목록만 다시 로드(현재 필터 유지). 분석 중 자료 없으면 폴링 중단. */
async function refreshMaterials() {
  try {
    const detail = await fetchJson(detailApiUrl());
    const sources = detail.sources || [];
    renderMaterials(sources);
    if (!sources.some(s => IN_PROGRESS_STATUSES.includes(s.processingStatus))) {
      stopPolling();
    }
  } catch {
    // 일시 네트워크 오류는 무시(다음 tick 재시도)
  }
}

function startPollingIfNeeded() {
  if (pollTimer) return;
  pollTimer = setInterval(refreshMaterials, POLL_INTERVAL_MS);
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

/* ----- 라이트박스 (conversation.js와 동일 패턴) -------------- */

function cacheSources(sources) {
  lastSourcesById = {};
  for (const s of sources) lastSourcesById[s.sourceId] = s;
}

function openLightboxForGroup(el) {
  const ids = (el.dataset.sourceIds || '').split(',').map(Number).filter(Boolean);
  const sources = ids.map(id => lastSourcesById[id]).filter(Boolean);
  if (sources.length === 0) return;
  openLightbox(sources);
}

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

function lightboxBackdropClick(e) {
  if (e.target.id === 'lightbox') closeLightbox();
}

function lightboxKeyHandler(e) {
  const box = document.getElementById('lightbox');
  if (!box || box.style.display === 'none') return;
  if (e.key === 'Escape')    { closeLightbox();   e.preventDefault(); }
  else if (e.key === 'ArrowLeft')  { lightboxPrev(); e.preventDefault(); }
  else if (e.key === 'ArrowRight') { lightboxNext(); e.preventDefault(); }
}

/* ----- util --------------------------------------------------- */

async function fetchJson(url) {
  return fetchJsonAuth(url);
}

function escapeHtml(s) {
  if (s == null) return '';
  return String(s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}
