/* ============================================================
 * Phase 12 — Conversation Dashboard
 *
 * URL: /conversation/dashboard?id={conversationId}
 *
 * 데이터 source:
 *   - GET /api/conversations/{id}          (대화 상세 + 첨부 자료 + 학생)
 *   - GET /api/conversations/{id}/weakness (radar용 카테고리별 정답률)
 *   - GET /api/conversations/{id}/score-timeline (line용 conversation별 시계열)
 *
 * 사이드바 4메뉴는 단순 a 링크. asAdmin=1 / impersonatedStudent는
 * 학생 임퍼소네이트 모드(어드민이 학생 dashboard 들어옴)를 위해 query에 propagate.
 * ============================================================ */

const conversationId = new URLSearchParams(location.search).get('id');
const isAdminView    = new URLSearchParams(location.search).get('asAdmin') === '1';

/** 분석이 아직 진행 중이라 폴링을 계속해야 하는 상태. */
const IN_PROGRESS_STATUSES = ['PENDING', 'ANALYZING'];
/** 폴링 주기 (ms) — 분석 중인 자료가 있을 때만 작동. */
const POLL_INTERVAL_MS = 3000;

let radarChartInstance = null;
let timelineChartInstance = null;
let pollTimer = null;
/** 상담자료 업로드/삭제 버튼 노출 여부 — 강사/Admin(임퍼소네이트 포함)만 true. 학생은 조회/다운로드만. */
let canManageConsultation = false;

document.addEventListener('DOMContentLoaded', init);

/** 헤더/카드 버튼이 호출하는 진입점. 업로드 모듈에 위임. */
function openUploadModal() {
  if (window.ConversationUpload) ConversationUpload.open();
}

async function init() {
  if (!conversationId) {
    alert('잘못된 접근입니다. 대화 ID가 없습니다.');
    location.href = '/stdt_dashboard';
    return;
  }

  const currentUser = readCurrentUser();
  if (!currentUser) {
    location.href = '/home';
    return;
  }

  // 상담자료 업로드/삭제는 강사/Admin만 — 어드민 임퍼소네이트(asAdmin=1)도 포함. 백엔드도 동일하게 가드한다.
  canManageConsultation = isAdminView
      || currentUser.role === 'TUTOR'
      || currentUser.role === 'ADMIN';

  // 사이드바 메뉴 링크에 id를 박아 넣음 (단순 페이지 이동)
  wireSidebarLinks();

  try {
    const detail = await fetchJson(`/api/conversations/${conversationId}`);
    renderHeader(detail);
    renderSidebarStudent(detail);
    renderDashboardSources(detail.sources || []);

    // 업로드 모듈 초기화 — detail.student의 학교/학년을 모달 default로 사용.
    // 임퍼소네이트 모드에서는 대상 학생의 userId를 보내야 source.uploaded_by가 학생으로 저장된다.
    // 어드민 userId가 들어가면 학생이 본 계정으로 들어왔을 때 verifyOwnsSource에서 403이 떨어진다.
    const uploaderUserId = isAdminView
        ? readImpersonatedStudent()?.userId
        : currentUser.userId;
    ConversationUpload.init({
      conversationId,
      userId: uploaderUserId,
      studentDefaults: {
        schoolName: detail.student?.schoolName || '',
        grade:      detail.student?.grade || '',
      },
      asAdmin: isAdminView,
      onComplete: handleUploadComplete,
    });
    // admin 임퍼소네이트도 학생과 동일하게 자료 업로드 가능 — 백엔드는 ?targetUserId로 통과시킴.

    // 첫 로드 시점에 분석 중인 자료가 있으면 폴링 시작 (chat에서 막 올린 파일도 잡힘)
    if ((detail.sources || []).some(s => IN_PROGRESS_STATUSES.includes(s.processingStatus))) {
      startPollingIfNeeded();
    }

    // radar + timeline 병렬 로드
    const [weakness, timeline] = await Promise.all([
      fetchJson(`/api/conversations/${conversationId}/weakness`).catch(() => null),
      fetchJson(`/api/conversations/${conversationId}/score-timeline`).catch(() => null),
    ]);

    renderRadar(weakness);
    renderTimeline(timeline);
  } catch (e) {
    // 권한 없음(403)으로 api.js가 본인 대시보드로 redirect 중이면 중복 alert를 띄우지 않는다.
    if (window.isAuthRedirecting && window.isAuthRedirecting()) return;
    console.error('dashboard init failed', e);
    alert('대시보드를 불러오지 못했어요. 다시 시도해주세요.');
  }
}

/* ----- 업로드 완료 후 처리 + 분석 폴링 ----------------------- */

/** 업로드 모듈이 콜백으로 호출. 자료 카드 즉시 갱신 + 폴링 시작. */
async function handleUploadComplete() {
  await refreshMaterials();
  startPollingIfNeeded();
}

/** materials만 다시 로드해 자료 카드 갱신. 분석 중인 자료가 없으면 폴링 중단. */
async function refreshMaterials() {
  try {
    const detail = await fetchJson(`/api/conversations/${conversationId}`);
    const sources = detail.sources || [];
    renderDashboardSources(sources);
    if (!sources.some(s => IN_PROGRESS_STATUSES.includes(s.processingStatus))) {
      stopPolling();
    }
  } catch {
    // 일시적 네트워크 오류는 무시 (다음 tick에서 재시도)
  }
}

function startPollingIfNeeded() {
  if (pollTimer) return;
  pollTimer = setInterval(refreshMaterials, POLL_INTERVAL_MS);
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

/* ----- 세션 / 라우팅 헬퍼 ------------------------------------ */

function readCurrentUser() {
  const raw = sessionStorage.getItem('currentUser');
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

/** 어드민 임퍼소네이트 진입 시 /admin/students에서 박아둔 학생 정보. asAdmin=1일 때만 의미 있음. */
function readImpersonatedStudent() {
  const raw = sessionStorage.getItem('impersonatedStudent');
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

function wireSidebarLinks() {
  const adminSuffix = isAdminView ? '&asAdmin=1' : '';
  const routes = {
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

async function fetchJson(url) {
  // 공통 헬퍼: 401 시 sessionStorage 비우고 /home으로 redirect, 그 외 non-2xx면 throw.
  return fetchJsonAuth(url);
}

/* ----- Header / Sidebar 학생 정보 ----------------------------- */

function renderHeader(detail) {
  document.getElementById('mainTitle').textContent = detail.lessonName || detail.title || 'English Dashboard';
  renderCommunityLink(detail);
}

function renderCommunityLink(detail) {
  const headerRight = document.querySelector('.main-header-right');
  if (!headerRight) return;
  headerRight.querySelector('.btn-community-header')?.remove();
  if (!detail.lessonId) return;

  const link = document.createElement('a');
  link.className = 'btn-community-header';
  link.href = `/lessons/${detail.lessonId}/community`;
  link.textContent = '수업 커뮤니티';
  headerRight.insertBefore(link, document.getElementById('btnUploadHeader'));
}

/* ----- 대화명 inline 편집 (PATCH /api/conversations/{id}) ----- */

function startTitleEdit() {
  const span = document.getElementById('mainTitle');
  const btn = document.getElementById('btnEditTitle');
  if (!span) return;
  const current = span.textContent.trim();
  const input = document.createElement('input');
  input.type = 'text';
  input.value = current;
  input.maxLength = 200;
  input.className = 'main-title-input';
  span.replaceWith(input);
  if (btn) btn.style.display = 'none';
  input.focus();
  input.select();

  let done = false;
  const restore = (text) => {
    const newSpan = document.createElement('span');
    newSpan.className = 'main-title';
    newSpan.id = 'mainTitle';
    newSpan.textContent = text;
    input.replaceWith(newSpan);
    if (btn) btn.style.display = '';
  };
  const finish = async (commit) => {
    if (done) return;
    done = true;
    const val = input.value.trim();
    if (!commit || !val || val === current) { restore(current); return; }
    try {
      const res = await fetchAuth(`/api/conversations/${conversationId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: val })
      });
      if (!res.ok) throw new Error(await res.text().catch(() => ''));
      const data = await res.json();
      restore(data.title || val);
    } catch (e) {
      alert('대화명 수정 실패: ' + (e.message || ''));
      restore(current);
    }
  };
  input.addEventListener('keydown', (ev) => {
    if (ev.key === 'Enter') { ev.preventDefault(); finish(true); }
    else if (ev.key === 'Escape') { ev.preventDefault(); finish(false); }
  });
  input.addEventListener('blur', () => finish(true));
}

function renderSidebarStudent(detail) {
  const student = detail.student;
  const box = document.getElementById('sidebarStudent');
  if (!student) { box.innerHTML = ''; return; }
  const metaLine2 = detail.lessonName ? escapeHtml(detail.academyName || '') : escapeHtml(student.schoolName || '');
  const metaLine3 = detail.lessonName ? escapeHtml(detail.lessonName) : escapeHtml(detail.title || '');
  box.innerHTML = `
    <div class="sidebar-student-name">
      ${escapeHtml(student.name || '')}
      <span class="sidebar-student-grade">· ${student.grade ? `Grade ${student.grade}` : ''}</span>
    </div>
    <div class="sidebar-student-meta">
      ${metaLine2}<br>
      ${metaLine3}
    </div>
  `;
}

/* ----- 카드 분기: 업로드 자료 vs 상담 자료 -------------------- */

/**
 * detail.sources를 두 카드로 분기한다.
 * - "업로드 자료" 카드: 화이트리스트(학생 업로드 STUDENT_MATERIAL/null + examId 있는 시험지)만.
 *   블랙리스트(`!== 'CONSULTATION'`)는 틀림 — WRONG_NOTE도 conversation_sources에 저장되므로 함께 누출된다.
 * - "개별 상담 및 피드백" 카드: sourceType === 'CONSULTATION'만.
 */
function renderDashboardSources(sources) {
  const all = sources || [];
  const uploadCardSources = all.filter(s =>
    s.sourceType == null || s.sourceType === 'STUDENT_MATERIAL' || s.examId != null);
  renderMaterials(uploadCardSources);
  renderConsultation(all.filter(s => s.sourceType === 'CONSULTATION'));
}

/* ----- 카드 1: 업로드 자료 ----------------------------------- */

function renderMaterials(sources) {
  const list = document.getElementById('materialsList');
  const countEl = document.getElementById('materialsCount');
  list.innerHTML = '';
  countEl.textContent = `${sources.length}개`;

  if (sources.length === 0) {
    list.innerHTML = '<div class="empty-state">아직 첨부된 자료가 없어요.</div>';
    return;
  }

  // 그룹화: exam_id 같은 source는 묶음 카드 하나로
  const groups = groupSources(sources);
  groups.forEach(g => list.appendChild(renderGroupItem(g)));
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

function renderGroupItem(group) {
  const first = group.items[0];
  const title = group.displayName || first.fileName;
  const count = group.items.length;
  const status = pickGroupStatus(group.items);
  const isExam = group.examId != null;
  const icon = isExam ? '📋' : (first.fileType === 'PDF' ? '📄' : '🖼️');

  const item = document.createElement('div');
  item.className = 'material-item';
  item.onclick = () => {
    // 자료 페이지로 이동 — 같은 conversation 컨텍스트
    const adminSuffix = isAdminView ? '&asAdmin=1' : '';
    location.href = `/conversation/materials?id=${conversationId}${adminSuffix}#source-${first.sourceId}`;
  };

  // 묶음 카드는 첫 source만 삭제 단추 노출 (시험지면 묶음 전체가 한 exam_id를 공유).
  // 학생 본인 + ADMIN 임퍼소네이트 모두 삭제 가능 — 백엔드가 ownership 검증.
  const sourceIdsCsv = group.items.map(s => s.sourceId).join(',');
  item.innerHTML = `
    <span class="material-icon">${icon}</span>
    <span class="material-name" title="${escapeHtml(title)}">${escapeHtml(title)}</span>
    ${count > 1 ? `<span class="material-meta">· ${count}장</span>` : ''}
    ${statusBadgeHtml(status, first.sourceId)}
    <button class="material-delete" title="자료 삭제"
            onclick="deleteMaterialGroup(event, '${sourceIdsCsv}', '${escapeHtml(title)}')">✕</button>
  `;
  return item;
}

/**
 * 자료 묶음 삭제. confirm 후 묶음의 각 source_id에 대해 DELETE /api/sources/{id} 호출.
 * 시험지였다면 채점 점수/답안까지 cascade로 사라진다 (백엔드 SourceService.delete 참고).
 */
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
    // 화면 갱신 — 자료 목록 + 차트 다시 로드
    await refreshMaterials();
    // radar도 갱신 (시험지 삭제 시 카테고리별 정답률 변동)
    try {
      const weakness = await fetchJson(`/api/conversations/${conversationId}/weakness`);
      renderRadar(weakness);
    } catch (_) {}
  } catch (e) {
    alert('삭제 실패: ' + (e.message || ''));
  }
}

/**
 * 상태에 맞는 뱃지 HTML.
 * - PENDING/ANALYZING: spinner가 도는 "분석 중" 뱃지
 * - PENDING_REVIEW: 클릭하면 검증 페이지로 이동 ("검증하기 ›")
 * - COMPLETED/FAILED: 정적 뱃지
 */
function statusBadgeHtml(status, sourceId) {
  switch (status) {
    case 'PENDING':
    case 'ANALYZING':
      return '<span class="material-status analyzing">분석 중</span>';
    case 'PENDING_REVIEW':
      return `<span class="material-status pending-review clickable" onclick="goExamReview(event, ${sourceId})">검증하기 ›</span>`;
    case 'COMPLETED':
      return '<span class="material-status completed">완료</span>';
    case 'FAILED':
      return '<span class="material-status failed">실패</span>';
    default:
      return `<span class="material-status">${status}</span>`;
  }
}

/** PENDING_REVIEW 뱃지 클릭 핸들러 — 부모 material-item 클릭 막고 검증 페이지로 이동. */
function goExamReview(event, sourceId) {
  event.stopPropagation();
  if (!sourceId) return;
  const adminSuffix = isAdminView ? '&asAdmin=1' : '';
  const returnUrl = `/conversation/dashboard?id=${conversationId}${adminSuffix}`;
  location.href = `/exam_review?id=${sourceId}&returnUrl=${encodeURIComponent(returnUrl)}`;
}

const STATUS_PRIORITY = ['PENDING', 'ANALYZING', 'PENDING_REVIEW', 'FAILED', 'COMPLETED'];

function pickGroupStatus(items) {
  for (const target of STATUS_PRIORITY) {
    if (items.some(i => i.processingStatus === target)) return target;
  }
  return items[0]?.processingStatus || 'COMPLETED';
}

/* ----- 카드: 개별 상담 및 피드백 ----------------------------- */

/**
 * 상담자료 카드 렌더. 자료 1건 = source 1개(파일별 1장). exam 그룹화 없음(상담자료는 examId 없음).
 * 강사/Admin 뷰에서만 업로드/삭제 버튼 노출(학생은 썸네일+다운로드만). 백엔드가 실제 권한을 가드한다.
 */
function renderConsultation(sources) {
  const list = document.getElementById('consultationList');
  const countEl = document.getElementById('consultationCount');
  const uploadBtn = document.getElementById('btnConsultationUpload');
  if (!list) return;
  if (uploadBtn) uploadBtn.style.display = canManageConsultation ? '' : 'none';
  if (countEl) countEl.textContent = `${sources.length}개`;
  list.innerHTML = '';

  if (sources.length === 0) {
    list.innerHTML = '<div class="empty-state">아직 등록된 상담 자료가 없어요.</div>';
    return;
  }
  sources.forEach(s => list.appendChild(renderConsultationItem(s)));
}

function renderConsultationItem(source) {
  const item = document.createElement('div');
  item.className = 'consultation-item';

  const thumbUrl = `/api/sources/${source.sourceId}/file?page=1`;
  const downloadUrl = `/api/sources/${source.sourceId}/download?page=1`;
  const fileName = source.fileName || '상담 자료';

  const deleteBtn = canManageConsultation
    ? `<button class="consultation-delete" title="자료 삭제"
              onclick="deleteConsultationMaterial(event, ${source.sourceId}, '${escapeHtml(fileName)}')">✕</button>`
    : '';

  item.innerHTML = `
    <a class="consultation-thumb" href="${downloadUrl}" target="_blank" rel="noopener" title="${escapeHtml(fileName)} 열기">
      <img src="${thumbUrl}" alt="${escapeHtml(fileName)}" loading="lazy">
    </a>
    <div class="consultation-info">
      <span class="consultation-name" title="${escapeHtml(fileName)}">${escapeHtml(fileName)}</span>
      <a class="consultation-download" href="${downloadUrl}" target="_blank" rel="noopener">다운로드 ↓</a>
    </div>
    ${deleteBtn}
  `;
  return item;
}

/** 상담자료 업로드 버튼 트리거 — 파일 선택 → FormData multipart POST. */
function openConsultationUpload() {
  const input = document.createElement('input');
  input.type = 'file';
  input.multiple = true;
  input.accept = 'image/*,application/pdf';
  input.onchange = () => {
    if (input.files && input.files.length > 0) uploadConsultationMaterials(input.files);
  };
  input.click();
}

async function uploadConsultationMaterials(fileList) {
  const formData = new FormData();
  for (const f of fileList) formData.append('files', f);
  try {
    const res = await fetchAuth(`/api/conversations/${conversationId}/consultation-materials`, {
      method: 'POST',
      body: formData,
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || `${res.status}`);
    }
    await refreshMaterials();
  } catch (e) {
    alert('상담 자료 업로드 실패: ' + (e.message || ''));
  }
}

async function deleteConsultationMaterial(event, sourceId, fileName) {
  event.stopPropagation();
  if (!confirm(`"${fileName}" 상담 자료를 영구 삭제할까요?`)) return;
  try {
    const res = await fetchAuth(
      `/api/conversations/${conversationId}/consultation-materials/${sourceId}`,
      { method: 'DELETE' });
    if (!res.ok && res.status !== 204) {
      const text = await res.text();
      throw new Error(text || `${res.status}`);
    }
    await refreshMaterials();
  } catch (e) {
    alert('상담 자료 삭제 실패: ' + (e.message || ''));
  }
}

/* ----- 카드 2: Radar ---------------------------------------- */

function renderRadar(weakness) {
  const empty = document.getElementById('radarEmpty');
  const wrap  = document.getElementById('radarWrap');
  const legend = document.getElementById('radarLegend');
  legend.innerHTML = '';

  const categories = weakness?.categories || [];
  const totalAnswers = categories.reduce((sum, c) => sum + c.total, 0);

  if (categories.length === 0 || totalAnswers === 0) {
    empty.style.display = 'flex';
    wrap.style.display = 'none';
    return;
  }
  empty.style.display = 'none';
  wrap.style.display = 'block';

  const labels = categories.map(c => c.categoryNameKo || c.categoryNameEn);
  const data   = categories.map(c => c.accuracyPercent);

  // 범례: 각 카테고리별 정답률 / 풀이 수
  categories.forEach(c => {
    const li = document.createElement('li');
    li.innerHTML = `${escapeHtml(c.categoryNameKo || c.categoryNameEn)} <strong>${c.accuracyPercent}%</strong> <span style="color:#a0aec0">(${c.correct}/${c.total})</span>`;
    legend.appendChild(li);
  });

  if (radarChartInstance) radarChartInstance.destroy();
  const ctx = document.getElementById('radarChart').getContext('2d');
  radarChartInstance = new Chart(ctx, {
    type: 'radar',
    data: {
      labels,
      datasets: [{
        label: '정답률 (%)',
        data,
        backgroundColor: 'rgba(55, 81, 209, 0.18)',
        borderColor: '#3751d1',
        borderWidth: 2,
        pointBackgroundColor: '#3751d1',
        pointRadius: 4,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        r: {
          min: 0,
          max: 100,
          ticks: { stepSize: 25, color: '#a0aec0', backdropColor: 'transparent' },
          grid: { color: '#edf2f7' },
          angleLines: { color: '#edf2f7' },
          pointLabels: { color: '#4a5568', font: { size: 12, weight: '600' } },
        }
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (ctx) => {
              const c = categories[ctx.dataIndex];
              return `${c.accuracyPercent}% · ${c.correct}/${c.total} 정답`;
            }
          }
        }
      }
    }
  });
}

/* ----- 카드 3: Timeline ------------------------------------- */

function renderTimeline(timeline) {
  const empty = document.getElementById('timelineEmpty');
  const wrap  = document.getElementById('timelineWrap');
  const sub   = document.getElementById('timelineSub');

  const points = timeline?.points || [];
  if (points.length === 0) {
    empty.style.display = 'flex';
    wrap.style.display = 'none';
    sub.textContent = '시계열 점수';
    return;
  }
  empty.style.display = 'none';
  wrap.style.display = 'block';
  sub.textContent = `${points.length}회 응시`;

  const labels = points.map(p => p.examDisplayName || `시험 ${p.examId}`);
  const data   = points.map(p => p.accuracyPercent);

  if (timelineChartInstance) timelineChartInstance.destroy();
  const ctx = document.getElementById('timelineChart').getContext('2d');
  timelineChartInstance = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: '정답률 (%)',
        data,
        fill: true,
        backgroundColor: 'rgba(55, 81, 209, 0.12)',
        borderColor: '#3751d1',
        borderWidth: 2,
        tension: 0.25,
        pointRadius: 5,
        pointBackgroundColor: '#3751d1',
        pointHoverRadius: 7,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        y: {
          min: 0, max: 100,
          ticks: { stepSize: 20, color: '#a0aec0' },
          grid: { color: '#edf2f7' },
        },
        x: {
          ticks: { color: '#4a5568', font: { size: 11 } },
          grid: { display: false },
        }
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (ctx) => {
              const p = points[ctx.dataIndex];
              // 점수가 있으면 절대점수 + 정답률 둘 다, 없으면 정답률만
              if (p.totalPoints && p.totalPoints > 0) {
                return `${p.accuracyPercent}% · ${p.earnedPoints}/${p.totalPoints}점 (${p.correctCount}/${p.totalQuestions})`;
              }
              return `${p.accuracyPercent}% · ${p.correctCount}/${p.totalQuestions}`;
            }
          }
        }
      }
    }
  });
}

/* ----- util -------------------------------------------------- */

function escapeHtml(s) {
  if (s == null) return '';
  return String(s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}
