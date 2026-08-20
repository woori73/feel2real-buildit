// 진입 가드: ADMIN만 (sessionStorage 비어도 JWT 쿠키로 복원 — 하단 init)
let currentUser = null;

let analyses = [];
const form = document.getElementById('analysisForm');
const formMessage = document.getElementById('formMessage');
const modal = document.getElementById('analysisModal');

document.getElementById('semesterSelect').addEventListener('change', syncOtherRows);
document.getElementById('examTypeSelect').addEventListener('change', syncOtherRows);
form.addEventListener('submit', saveAnalysis);
modal.addEventListener('click', (event) => {
  if (event.target === modal) closeModal();
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') closeModal();
});
document.addEventListener('click', (event) => {
  if (!event.target.closest('.card-menu-wrap')) closeAllMenus();
});

(async () => {
  currentUser = await ensureCurrentUser();
  if (!currentUser || currentUser.role !== 'ADMIN') { location.href = '/home'; return; }
  document.getElementById('userName').textContent = window.userDisplayName(currentUser);
  loadSubjects();
  loadAnalyses();
  syncOtherRows();
})();

async function loadSubjects() {
  const select = document.getElementById('subjectId');
  try {
    const res = await fetch('/api/subjects');
    if (!res.ok) throw new Error();
    const subjects = await res.json();
    select.innerHTML = subjects.map(s => `<option value="${s.subjectId}">${escapeHtml(s.nameKo || s.nameEn)}</option>`).join('');
  } catch (_) {
    select.innerHTML = '<option value="">과목 조회 실패</option>';
  }
}

async function loadAnalyses() {
  const list = document.getElementById('analysisList');
  list.innerHTML = '<div class="empty-card">불러오는 중...</div>';
  try {
    const res = await fetchAuth('/api/admin/analyzes');
    if (!res.ok) throw new Error();
    analyses = await res.json();
    list.innerHTML = renderCreateCard() + (analyses.length ? analyses.map(renderItem).join('') : '<div class="empty-card">등록된 분석 자료가 없습니다.</div>');
  } catch (_) {
    list.innerHTML = '<div class="empty-card error">분석 자료를 불러오지 못했습니다.</div>';
  }
}

function renderCreateCard() {
  return `<article class="analysis-card create" onclick="openCreateModal()" role="button" tabindex="0"
    onkeydown="if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openCreateModal(); }">
    <div class="create-circle">+</div>
    <div class="create-title">새 분석자료</div>
  </article>`;
}

function renderItem(item) {
  const subject = item.subject?.nameKo || item.subject?.nameEn || '과목';
  return `<article class="analysis-card ${item.isVisible ? '' : 'hidden'}">
    <div class="card-menu-wrap">
      <button class="card-menu" type="button" onclick="toggleMenu(event, ${item.examAnalysisId})" aria-label="분석자료 메뉴">⋮</button>
      <div id="menu-${item.examAnalysisId}" class="menu-pop">
        <a href="${escapeAttr(item.detailUrl)}" target="_blank">공개 페이지 보기</a>
        <button type="button" onclick="editAnalysis(${item.examAnalysisId})">수정</button>
        <button type="button" onclick="toggleVisibility(${item.examAnalysisId}, ${!item.isVisible})">${item.isVisible ? '비공개 전환' : '공개 전환'}</button>
        <button type="button" onclick="softDeleteAnalysis(${item.examAnalysisId})">숨김 삭제</button>
        <button type="button" class="danger" onclick="hardDeleteAnalysis(${item.examAnalysisId})">완전 삭제</button>
      </div>
    </div>
    <div>
      <div class="card-icon">${item.isVisible ? '📘' : '📕'}</div>
      ${visibilityPill(item.isVisible)}
      <h3 class="analysis-title">${escapeHtml(item.title)}</h3>
      <div class="analysis-meta">${escapeHtml(item.schoolName)} · ${item.grade}학년<br>${escapeHtml(item.semester)} · ${escapeHtml(item.examType)} · ${escapeHtml(subject)}</div>
    </div>
    <div class="analysis-sub">파일 ${escapeHtml(item.originalFileName)}<br>ID ${item.examAnalysisId}</div>
  </article>`;
}

function openCreateModal() {
  resetForm(false);
  document.getElementById('formTitle').textContent = '분석 자료 등록';
  openModal();
}

function openModal() {
  modal.classList.add('open');
  modal.setAttribute('aria-hidden', 'false');
  setTimeout(() => document.getElementById('title').focus(), 0);
}

function closeModal() {
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

function editAnalysis(id) {
  const item = analyses.find(a => a.examAnalysisId === id);
  if (!item) return;
  closeAllMenus();
  document.getElementById('formTitle').textContent = '분석 자료 수정';
  document.getElementById('analysisId').value = item.examAnalysisId;
  document.getElementById('title').value = item.title;
  document.getElementById('schoolName').value = item.schoolName;
  document.getElementById('grade').value = item.grade;
  setSelectOrOther('semesterSelect', 'semesterOther', item.semester);
  setSelectOrOther('examTypeSelect', 'examTypeOther', item.examType);
  document.getElementById('subjectId').value = item.subject.subjectId;
  document.getElementById('visible').checked = item.isVisible;
  document.getElementById('file').value = '';
  document.getElementById('fileHint').textContent = '(교체 시에만 선택)';
  formMessage.textContent = '';
  syncOtherRows();
  openModal();
}

async function saveAnalysis(e) {
  e.preventDefault();
  const id = document.getElementById('analysisId').value;
  const data = new FormData();
  data.set('title', value('title'));
  data.set('schoolName', value('schoolName'));
  data.set('grade', value('grade'));
  data.set('semester', selectedValue('semesterSelect', 'semesterOther'));
  data.set('examType', selectedValue('examTypeSelect', 'examTypeOther'));
  data.set('subjectId', value('subjectId'));
  data.set('visible', document.getElementById('visible').checked ? 'true' : 'false');
  const file = document.getElementById('file').files[0];
  if (file) data.set('file', file);
  if (!id && !file) {
    formMessage.textContent = '신규 등록에는 HTML 파일이 필요합니다.';
    formMessage.className = 'error';
    return;
  }
  try {
    const res = await fetchAuth(id ? `/api/admin/analyzes/${id}` : '/api/admin/analyzes', {
      method: id ? 'PUT' : 'POST',
      body: data
    });
    if (!res.ok) throw new Error(await res.text());
    closeModal();
    resetForm(false);
    await loadAnalyses();
  } catch (err) {
    formMessage.textContent = '저장 실패: ' + (err.message || '입력값을 확인해주세요.');
    formMessage.className = 'error';
  }
}

async function toggleVisibility(id, isVisible) {
  await mutate(`/api/admin/analyzes/${id}/visibility`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ isVisible }) });
}
async function softDeleteAnalysis(id) {
  if (!confirm('이 자료를 숨김 삭제하시겠습니까? 공개 목록에서 제외됩니다.')) return;
  await mutate(`/api/admin/analyzes/${id}`, { method: 'DELETE' });
}
async function hardDeleteAnalysis(id) {
  if (!confirm('완전 삭제하면 복구할 수 없습니다. 계속하시겠습니까?')) return;
  await mutate(`/api/admin/analyzes/${id}/hard`, { method: 'DELETE' });
}
async function mutate(url, opts) {
  closeAllMenus();
  const res = await fetchAuth(url, opts);
  if (!res.ok) alert('처리에 실패했습니다.');
  await loadAnalyses();
}
function resetForm(clearMessage = true) {
  form.reset();
  document.getElementById('analysisId').value = '';
  document.getElementById('formTitle').textContent = '분석 자료 등록';
  document.getElementById('visible').checked = true;
  document.getElementById('fileHint').textContent = '';
  formMessage.className = 'muted';
  if (clearMessage) formMessage.textContent = '';
  syncOtherRows();
}
function syncOtherRows() {
  document.getElementById('semesterOtherRow').style.display = value('semesterSelect') === '기타' ? 'flex' : 'none';
  document.getElementById('examTypeOtherRow').style.display = value('examTypeSelect') === '기타' ? 'flex' : 'none';
}
function setSelectOrOther(selectId, otherId, val) {
  const select = document.getElementById(selectId);
  const known = Array.from(select.options).some(o => o.value === val || o.textContent === val) && val !== '기타';
  select.value = known ? val : '기타';
  document.getElementById(otherId).value = known ? '' : val;
}
function selectedValue(selectId, otherId) { return value(selectId) === '기타' ? value(otherId) : value(selectId); }
function visibilityPill(isVisible) { return isVisible ? '<span class="pill pill-visible">공개</span>' : '<span class="pill pill-hidden">비공개</span>'; }
function value(id) { return document.getElementById(id).value.trim(); }
function escapeHtml(value) { return String(value || '').replace(/[&<>'"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch])); }
function escapeAttr(value) { return escapeHtml(value).replace(/`/g, '&#96;'); }

async function handleLogout() {
  try { await fetch('/api/auth/logout', { method: 'POST' }); } catch (_) {}
  try { sessionStorage.clear(); } catch (_) {}
  location.href = '/';
}
