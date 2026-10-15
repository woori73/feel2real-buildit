/* ============================================================
 * Phase 12 — Achievement Report (placeholder)
 *
 * 본 페이지는 아직 콘텐츠가 없다. 사이드바 라우팅 + 학생 정보만 채운다.
 * ============================================================ */

const conversationId = new URLSearchParams(location.search).get('id');
const isAdminView    = new URLSearchParams(location.search).get('asAdmin') === '1';

document.addEventListener('DOMContentLoaded', init);

async function init() {
  if (!conversationId) {
    location.href = '/stdt_dashboard';
    return;
  }
  if (!sessionStorage.getItem('currentUser')) {
    location.href = '/home';
    return;
  }

  wireSidebarLinks();

  try {
    const detail = await fetchJson(`/api/conversations/${conversationId}`);
    renderSidebarStudent(detail);
  } catch (e) {
    console.error('report sidebar load failed', e);
  }
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

function renderSidebarStudent(detail) {
  const student = detail.student;
  const box = document.getElementById('sidebarStudent');
  if (!student) { box.innerHTML = ''; return; }
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
