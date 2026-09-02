// 진입 가드: ADMIN만 (sessionStorage 비어도 JWT 쿠키로 복원 — 하단 init)
let currentUser = null;

let originals = [];
const form = document.getElementById('originalForm');
const formMessage = document.getElementById('formMessage');
const modal = document.getElementById('originalModal');

form.addEventListener('submit', saveOriginal);
modal.addEventListener('click', (event) => { if (event.target === modal) closeModal(); });
document.addEventListener('keydown', (event) => { if (event.key === 'Escape') closeModal(); });
document.addEventListener('click', (event) => {
  if (!event.target.closest('.card-menu-wrap')) closeAllMenus();
});

(async () => {
  currentUser = await ensureCurrentUser();
  if (!currentUser || currentUser.role !== 'ADMIN') { location.href = '/wiki'; return; }
  document.getElementById('userName').textContent = window.userDisplayName(currentUser);
  loadOriginals();
})();

async function loadOriginals() {
  const list = document.getElementById('originalList');
  list.innerHTML = '<div class="empty-card">불러오는 중...</div>';
  try {
    const res = await fetchAuth('/api/admin/wiki/originals');
    if (!res.ok) throw new Error();
    originals = await res.json();
    list.innerHTML = renderCreateCard()
      + (originals.length ? originals.map(renderItem).join('') : '<div class="empty-card">등록된 원본 자료가 없습니다.</div>');
  } catch (_) {
    list.innerHTML = '<div class="empty-card error">원본 자료를 불러오지 못했습니다.</div>';
  }
}

function renderCreateCard() {
  return `<article class="analysis-card create" onclick="openCreateModal()" role="button" tabindex="0"
    onkeydown="if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openCreateModal(); }">
    <div class="create-circle">+</div>
    <div class="create-title">새 원본</div>
  </article>`;
}

function renderItem(item) {
  return `<article class="analysis-card ${item.isVisible ? '' : 'hidden'}">
    <div class="card-menu-wrap">
      <button class="card-menu" type="button" onclick="toggleMenu(event, ${item.wikiExamOriginalId})" aria-label="원본자료 메뉴">⋮</button>
      <div id="menu-${item.wikiExamOriginalId}" class="menu-pop">
        <a href="${escapeAttr(item.downloadUrl)}" target="_blank">파일 내려받기</a>
        <button type="button" onclick="editOriginal(${item.wikiExamOriginalId})">수정</button>
        <button type="button" onclick="toggleVisibility(${item.wikiExamOriginalId}, ${!item.isVisible})">${item.isVisible ? '비공개 전환' : '공개 전환'}</button>
        <button type="button" onclick="softDeleteOriginal(${item.wikiExamOriginalId})">숨김 삭제</button>
        <button type="button" class="danger" onclick="hardDeleteOriginal(${item.wikiExamOriginalId})">완전 삭제</button>
      </div>
    </div>
    <div>
      <div class="card-icon">${item.isVisible ? '📄' : '📕'}</div>
      ${visibilityPill(item.isVisible)}
      <h3 class="analysis-title">${escapeHtml(item.title)}</h3>
      <div class="analysis-meta">${escapeHtml(item.examLabel)} · 고${item.grade} · ${escapeHtml(item.docTypeLabel)}</div>
    </div>
    <div class="analysis-sub">파일 ${escapeHtml(item.originalFileName)}<br>ID ${item.wikiExamOriginalId}</div>
  </article>`;
}

function openCreateModal() {
  resetForm(false);
  document.getElementById('formTitle').textContent = '원본 자료 등록';
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

function editOriginal(id) {
  const item = originals.find(o => o.wikiExamOriginalId === id);
  if (!item) return;
  closeAllMenus();
  document.getElementById('formTitle').textContent = '원본 자료 수정';
  document.getElementById('originalId').value = item.wikiExamOriginalId;
  document.getElementById('title').value = item.title;
  document.getElementById('grade').value = String(item.grade);
  document.getElementById('docType').value = item.docType;
  document.getElementById('examLabel').value = item.examLabel;
  document.getElementById('visible').checked = item.isVisible;
  document.getElementById('file').value = '';
  document.getElementById('fileHint').textContent = '(교체 시에만 선택)';
  formMessage.textContent = '';
  openModal();
}

async function saveOriginal(e) {
  e.preventDefault();
  const id = document.getElementById('originalId').value;
  const data = new FormData();
  data.set('title', value('title'));
  data.set('grade', value('grade'));
  data.set('examLabel', value('examLabel'));
  data.set('docType', value('docType'));
  data.set('visible', document.getElementById('visible').checked ? 'true' : 'false');
  const file = document.getElementById('file').files[0];
  if (file) data.set('file', file);
  if (!id && !file) {
    formMessage.textContent = '신규 등록에는 PDF 파일이 필요합니다.';
    formMessage.className = 'error';
    return;
  }
  try {
    const res = await fetchAuth(id ? `/api/admin/wiki/originals/${id}` : '/api/admin/wiki/originals', {
      method: id ? 'PUT' : 'POST',
      body: data
    });
    if (!res.ok) throw new Error(await res.text());
    closeModal();
    resetForm(false);
    await loadOriginals();
  } catch (err) {
    formMessage.textContent = '저장 실패: ' + (err.message || '입력값을 확인해주세요.');
    formMessage.className = 'error';
  }
}

async function toggleVisibility(id, isVisible) {
  await mutate(`/api/admin/wiki/originals/${id}/visibility`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ isVisible })
  });
}
async function softDeleteOriginal(id) {
  if (!confirm('이 자료를 숨김 삭제하시겠습니까? 공개 목록에서 제외됩니다.')) return;
  await mutate(`/api/admin/wiki/originals/${id}`, { method: 'DELETE' });
}
async function hardDeleteOriginal(id) {
  if (!confirm('완전 삭제하면 복구할 수 없습니다. 계속하시겠습니까?')) return;
  await mutate(`/api/admin/wiki/originals/${id}/hard`, { method: 'DELETE' });
}
async function mutate(url, opts) {
  closeAllMenus();
  const res = await fetchAuth(url, opts);
  if (!res.ok) alert('처리에 실패했습니다.');
  await loadOriginals();
}

function resetForm(clearMessage = true) {
  form.reset();
  document.getElementById('originalId').value = '';
  document.getElementById('formTitle').textContent = '원본 자료 등록';
  document.getElementById('examLabel').value = '2026 9월 모의고사';
  document.getElementById('visible').checked = true;
  document.getElementById('fileHint').textContent = '';
  formMessage.className = 'muted';
  if (clearMessage) formMessage.textContent = '';
}

function visibilityPill(isVisible) {
  return isVisible ? '<span class="pill pill-visible">공개</span>' : '<span class="pill pill-hidden">비공개</span>';
}
function value(id) { return document.getElementById(id).value.trim(); }
function escapeHtml(value) { return String(value || '').replace(/[&<>'"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch])); }
function escapeAttr(value) { return escapeHtml(value).replace(/`/g, '&#96;'); }
