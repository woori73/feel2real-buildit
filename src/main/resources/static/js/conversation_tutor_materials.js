/* ============================================================
 * 시험대비 자료 (conversation 단위) — 강사가 올리는 '제출 불필요' 참고자료
 *
 *  - 강사/Admin: 자료 업로드(POST /api/conversations/{id}/tutor-materials)
 *  - 학생: 열람/다운로드만 (제출 UI 없음)
 *  데이터: GET /api/conversations/{id} 의 sources 중 sourceType === 'TUTOR_MATERIAL'
 * ============================================================ */

const conversationId = new URLSearchParams(location.search).get('id');
const isAdminView    = new URLSearchParams(location.search).get('asAdmin') === '1';

let canUpload = false;   // ADMIN(asAdmin=1) 또는 TUTOR

let tutorSourcesById = {};
let lightboxItems = [];
let lightboxIndex = 0;

document.addEventListener('DOMContentLoaded', init);
document.addEventListener('keydown', lightboxKeyHandler);

async function init() {
  if (!conversationId) { location.href = '/stdt_dashboard'; return; }
  if (!await ensureCurrentUser()) { location.href = '/home'; return; }

  wireSidebarLinks();
  resolveAccess();
  await loadTutorMaterials();
}

function resolveAccess() {
  const userRaw = sessionStorage.getItem('currentUser');
  let role = null;
  try { role = userRaw ? JSON.parse(userRaw).role : null; } catch (_) {}
  canUpload = role === 'TUTOR' || (role === 'ADMIN' && isAdminView);
  const btn = document.getElementById('btnUploadTutorMaterial');
  if (btn) btn.hidden = !canUpload;
}

async function loadTutorMaterials() {
  const grid = document.getElementById('tutorMaterialsGrid');
  const countChip = document.getElementById('tutorMaterialsCountChip');
  try {
    const detail = await fetchJsonAuth(`/api/conversations/${conversationId}`);
    renderSidebarStudent(detail);
    const items = (detail.sources || []).filter(s => s.sourceType === 'TUTOR_MATERIAL');
    tutorSourcesById = {};
    for (const s of items) tutorSourcesById[s.sourceId] = s;
    countChip.textContent = `${items.length}개`;
    if (!items.length) {
      grid.innerHTML = `<div class="empty-state">아직 강사가 올린 시험대비 자료가 없어요.</div>`;
      return;
    }
    grid.innerHTML = items.map(tutorMaterialCard).join('');
  } catch (e) {
    console.error('tutor-materials load failed', e);
    grid.innerHTML = `<div class="empty-state">자료를 불러오지 못했어요.</div>`;
  }
}

/** exam-materials(conversation_materials.js renderGroupCard)와 동일한 카드 구조 + 클릭 시 라이트박스. */
function tutorMaterialCard(s) {
  const icon = s.fileType === 'PDF' ? '📄' : '🖼️';
  const title = s.fileName || '';
  const tags = [];
  if (s.fileType) tags.push(`<span class="material-card-tag">${escapeHtml(s.fileType)}</span>`);
  if ((s.pageCount || 1) > 1) tags.push(`<span class="material-card-tag">${s.pageCount}장</span>`);
  return `
    <div class="material-card" data-source-id="${s.sourceId}" onclick="openTutorLightbox(${s.sourceId})">
      <div class="material-card-header">
        <span class="material-card-icon">${icon}</span>
        <span class="material-card-name" title="${escapeHtml(title)}">${escapeHtml(title)}</span>
      </div>
      <div class="material-card-meta">${tags.join('')}</div>
    </div>
  `;
}

/* ── 라이트박스 (conversation_materials.js와 동일 패턴 — 페이지 독립을 위해 복사) ── */

function openTutorLightbox(sourceId) {
  const s = tutorSourcesById[sourceId];
  if (!s) return;
  lightboxItems = [];
  const pages = s.pageCount || 1;
  for (let p = 1; p <= pages; p++) lightboxItems.push({ sourceId: s.sourceId, page: p });
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
  if (e.key === 'Escape')          { closeLightbox(); e.preventDefault(); }
  else if (e.key === 'ArrowLeft')  { lightboxPrev();  e.preventDefault(); }
  else if (e.key === 'ArrowRight') { lightboxNext();  e.preventDefault(); }
}

async function handleTutorUpload(input) {
  const files = input.files;
  if (!files || !files.length) return;
  const btn = document.getElementById('btnUploadTutorMaterial');
  const prev = btn ? btn.textContent : '';
  if (btn) { btn.disabled = true; btn.textContent = '업로드 중...'; }
  try {
    const form = new FormData();
    for (const f of files) form.append('files', f);
    const res = await fetchAuth(`/api/conversations/${conversationId}/tutor-materials`, {
      method: 'POST',
      body: form
    });
    if (!res.ok) throw new Error(await res.text().catch(() => ''));
    await loadTutorMaterials();
  } catch (e) {
    alert('업로드 실패: ' + (e.message || ''));
  } finally {
    input.value = '';
    if (btn) { btn.disabled = false; btn.textContent = prev || '+ 자료 추가'; }
  }
}

/* ── 사이드바 / 유틸 ──────────────────────────────────── */

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

function renderSidebarStudent(detail) {
  const student = detail.student;
  const box = document.getElementById('sidebarStudent');
  if (!box || !student) { if (box) box.innerHTML = ''; return; }
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

function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
