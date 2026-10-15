const form = document.getElementById('communitySearchForm');
const keywordInput = document.getElementById('keyword');
const resetButton = document.getElementById('resetButton');
const listEl = document.getElementById('communityList');
const resultMeta = document.getElementById('resultMeta');

window.addEventListener('DOMContentLoaded', () => {
  loadCommunities();
});

form.addEventListener('submit', (event) => {
  event.preventDefault();
  loadCommunities(keywordInput.value);
});

resetButton.addEventListener('click', () => {
  keywordInput.value = '';
  loadCommunities();
});

async function loadCommunities(keyword = '') {
  const normalized = keyword.trim();
  listEl.innerHTML = '<div class="empty-card">불러오는 중...</div>';
  resultMeta.textContent = '불러오는 중...';
  try {
    const query = normalized ? `?keyword=${encodeURIComponent(normalized)}` : '';
    const res = await fetchAuth(`/api/admin/lesson-communities${query}`);
    if (!res.ok) throw new Error(await res.text());
    const items = await res.json();
    resultMeta.textContent = normalized
      ? `검색어 “${normalized}” 결과 ${items.length}개${items.length >= 100 ? ' · 최대 100개 표시' : ''}`
      : `최근 수업 커뮤니티 ${items.length}개${items.length >= 100 ? ' · 최대 100개 표시' : ''}`;
    renderCommunities(items);
  } catch (error) {
    console.error(error);
    resultMeta.textContent = '커뮤니티 목록을 불러오지 못했습니다.';
    listEl.innerHTML = '<div class="empty-card">커뮤니티 목록을 불러오지 못했습니다.</div>';
  }
}

function renderCommunities(items) {
  if (!items.length) {
    listEl.innerHTML = '<div class="empty-card">검색 결과가 없습니다.</div>';
    return;
  }
  listEl.innerHTML = items.map(communityCardHtml).join('');
}

function communityCardHtml(item) {
  const hidden = !item.academyVisible || !item.lessonVisible || item.lessonStatus === 'HIDDEN';
  const latest = item.latestPostId
    ? `<div class="latest">
        <div class="latest-title">${escapeHtml(item.latestPostTitle)}</div>
        <div class="latest-meta">${labelPostType(item.latestPostType)} · ${escapeHtml(item.latestAuthorName)} · ${formatDateTime(item.latestPostCreatedAt)}</div>
      </div>`
    : '<div class="latest"><div class="latest-title">아직 작성된 글이 없습니다.</div><div class="latest-meta">커뮤니티에 바로 접근할 수 있습니다.</div></div>';
  return `<article class="community-card ${hidden ? 'hidden' : ''}">
    <div class="card-top">
      <div class="pill-group">
        <span class="pill ${hidden ? 'pill-hidden' : 'pill-visible'}">${hidden ? '숨김 포함' : '공개'}</span>
        ${item.lessonStatus === 'HIDDEN' ? '<span class="pill pill-warn">수업 숨김</span>' : ''}
      </div>
    </div>
    <div>
      <h3 class="community-title">${escapeHtml(item.lessonName)}</h3>
      <p class="community-meta">${escapeHtml(item.academyName)} · 강사 ${escapeHtml(item.tutorName)}</p>
    </div>
    <div class="stat-row">
      <div class="stat"><div class="stat-num">${item.postCount}</div><div class="stat-label">게시글</div></div>
      <div class="stat"><div class="stat-num">${item.commentCount}</div><div class="stat-label">댓글</div></div>
    </div>
    ${latest}
    <div class="card-actions">
      <a class="btn btn-primary" href="/lessons/${item.lessonId}/community">커뮤니티 열기</a>
      <a class="btn btn-secondary" href="/academies/${encodeURIComponent(item.academySlug)}" target="_blank" rel="noopener">학원 보기</a>
    </div>
  </article>`;
}

function labelPostType(type) {
  return ({ NOTICE: '공지', QNA: 'Q&A', GENERAL: '일반' }[type]) || '글';
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
