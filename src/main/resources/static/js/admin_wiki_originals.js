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
  loadRounds();
  loadOriginals();
})();

document.getElementById('roundForm').addEventListener('submit', createRound);

async function loadRounds() {
  const list = document.getElementById('roundList');
  try {
    const res = await fetchAuth('/api/admin/wiki/rounds');
    if (!res.ok) throw new Error();
    const rounds = await res.json();
    list.innerHTML = rounds.length ? rounds.map(renderRound).join('') : '<div class="empty-card">등록된 회차가 없습니다.</div>';
  } catch (_) {
    list.innerHTML = '<div class="empty-card error">회차를 불러오지 못했습니다.</div>';
  }
}

function renderRound(r) {
  return `<article class="analysis-card ${r.isPublished ? '' : 'hidden'}">
    <div>
      <div class="card-icon">${r.isCurrent ? '⭐' : '🗂️'}</div>
      ${r.isCurrent ? '<span class="pill pill-visible">메인 회차</span>' : ''}
      ${r.isPublished ? '<span class="pill pill-visible">공개</span>' : '<span class="pill pill-hidden">비공개</span>'}
      <h3 class="analysis-title">${escapeHtml(r.title)}</h3>
      <div class="analysis-meta">${r.examYear}년 ${r.examMonth}월${r.bannerPath ? ' · 배너 있음' : ''}</div>
    </div>
    <div class="analysis-sub">${escapeHtml(r.subtitle || '')}</div>
    <div class="form-actions" style="margin-top:10px;">
      ${r.isCurrent ? '' : `<button class="btn btn-primary" type="button" onclick="setCurrentRound(${r.wikiExamRoundId})">메인으로 지정</button>`}
      <button class="btn btn-secondary" type="button" onclick="toggleRoundPublished(${r.wikiExamRoundId}, ${!r.isPublished})">${r.isPublished ? '비공개 전환' : '공개 전환'}</button>
    </div>
  </article>`;
}

async function createRound(e) {
  e.preventDefault();
  const msg = document.getElementById('roundMessage');
  const data = new URLSearchParams();
  data.set('examYear', value('roundYear'));
  data.set('examMonth', value('roundMonth'));
  data.set('title', value('roundTitle'));
  data.set('subtitle', value('roundSubtitle'));
  data.set('bannerPath', value('roundBanner'));
  data.set('published', 'true');
  try {
    const res = await fetchAuth('/api/admin/wiki/rounds', {
      method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: data
    });
    if (!res.ok) throw new Error(await res.text());
    msg.textContent = '회차가 추가됐습니다.';
    msg.className = 'muted';
    e.target.reset();
    document.getElementById('roundYear').value = '2026';
    await loadRounds();
  } catch (err) {
    msg.textContent = '회차 추가 실패: ' + (err.message || '');
    msg.className = 'error';
  }
}

async function setCurrentRound(id) {
  const res = await fetchAuth(`/api/admin/wiki/rounds/${id}/current`, { method: 'PATCH' });
  if (!res.ok) alert('메인 지정에 실패했습니다.');
  await loadRounds();
}

async function toggleRoundPublished(id, isPublished) {
  const res = await fetchAuth(`/api/admin/wiki/rounds/${id}/published`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ isVisible: isPublished })
  });
  if (!res.ok) alert('공개 전환에 실패했습니다.');
  await loadRounds();
}

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
    <div class="create-title">새 자료</div>
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
      <div class="analysis-meta">${escapeHtml(roundLabel(item))} · 고${item.grade} · ${escapeHtml(item.docTypeLabel)}</div>
    </div>
    <div class="analysis-sub">파일 ${escapeHtml(item.originalFileName)}<br>ID ${item.wikiExamOriginalId}</div>
  </article>`;
}

function openCreateModal() {
  resetForm(false);
  document.getElementById('formTitle').textContent = '자료 등록';
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
  document.getElementById('formTitle').textContent = '자료 수정';
  document.getElementById('originalId').value = item.wikiExamOriginalId;
  document.getElementById('title').value = item.title;
  document.getElementById('grade').value = String(item.grade);
  document.getElementById('docType').value = item.docType;
  const ym = yearMonthOf(item);
  document.getElementById('examYear').value = ym.year;
  document.getElementById('examMonth').value = String(ym.month);
  document.getElementById('visible').checked = item.isVisible;
  document.getElementById('file').value = '';
  document.getElementById('fileHint').textContent = '(교체 시에만 선택)';
  formMessage.textContent = '';
  openModal();
}

/** 회차 표시 — 구조화된 연도·월이 있으면 그걸, 없으면(구 자료) 라벨에서 파싱한다. */
function yearMonthOf(item) {
  if (item.examYear && item.examMonth) return { year: item.examYear, month: item.examMonth };
  const label = item.examLabel || '';
  const y = label.match(/(20\d{2})/);
  const m = label.match(/(\d{1,2})월/);
  return { year: y ? Number(y[1]) : 2026, month: m ? Number(m[1]) : 9 };
}

function roundLabel(item) {
  const ym = yearMonthOf(item);
  return `${ym.year}년 ${ym.month}월 모의고사`;
}

async function saveOriginal(e) {
  e.preventDefault();
  const id = document.getElementById('originalId').value;
  const data = new FormData();
  data.set('title', value('title'));
  data.set('grade', value('grade'));
  data.set('examYear', value('examYear'));
  data.set('examMonth', value('examMonth'));
  data.set('docType', value('docType'));
  data.set('visible', document.getElementById('visible').checked ? 'true' : 'false');
  const file = document.getElementById('file').files[0];
  if (file) data.set('file', file);
  if (!id && !file) {
    formMessage.textContent = '신규 등록에는 파일(PDF·HWP·HWPX·HTML·이미지)이 필요합니다.';
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
  document.getElementById('formTitle').textContent = '자료 등록';
  document.getElementById('examYear').value = '2026';
  document.getElementById('examMonth').value = '10';
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
