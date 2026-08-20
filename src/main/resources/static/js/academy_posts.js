(function () {
  const form = document.getElementById('academyPostsSearch');
  const list = document.getElementById('academyPostsList');

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    loadPosts();
  });

  list.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-like-post-id]');
    if (!button) return;
    event.preventDefault();
    await toggleLike(button.dataset.likePostId);
  });

  loadPosts();

  async function loadPosts() {
    const params = new URLSearchParams(new FormData(form));
    list.innerHTML = '<p class="muted">글을 불러오는 중...</p>';
    try {
      const res = await fetch('/api/academy-posts?' + params.toString());
      if (!res.ok) throw new Error('목록을 불러오지 못했습니다.');
      const posts = await res.json();
      list.innerHTML = posts.length
        ? posts.map(card).join('')
        : '<div class="card empty-card"><p class="muted">조건에 맞는 공개 글이 없습니다.</p></div>';
    } catch (error) {
      list.innerHTML = '<div class="card empty-card"><p class="error">블로그 글을 불러오지 못했습니다.</p></div>';
    }
  }

  async function toggleLike(postId) {
    try {
      const res = await fetch('/api/academy-posts/' + encodeURIComponent(postId) + '/like', { method: 'POST' });
      if (res.status === 401 || res.status === 403) {
        alert('로그인 후 좋아요를 누를 수 있습니다.');
        if (typeof openLoginModal === 'function') openLoginModal();
        return;
      }
      if (!res.ok) {
        const message = await res.text();
        alert(message || '좋아요 처리에 실패했습니다.');
        return;
      }
      await loadPosts();
    } catch (error) {
      alert('좋아요 처리에 실패했습니다.');
    }
  }

  function card(post) {
    const summary = post.summary ? `<p>${escapeHtml(post.summary)}</p>` : '<p class="muted">요약이 없습니다.</p>';
    const academy = post.academySlug ? `<span>${escapeHtml(post.academySlug)}</span>` : '';
    return `<article class="card academy-post-card">
      <a class="academy-post-card-main" href="${post.publicUrl}">
        <div class="academy-post-card-meta">
          ${academy}
          <span>${formatDate(post.publishedAt || post.updatedAt)}</span>
        </div>
        <h2>${escapeHtml(post.title)}</h2>
        ${summary}
      </a>
      <div class="academy-post-card-actions">
        <button type="button" class="like-button ${post.likedByMe ? 'liked' : ''}" data-like-post-id="${post.postId}" ${post.likeAllowed ? '' : 'disabled'}>
          추천 ${Number(post.likeCount || 0)}
        </button>
        ${post.likeAllowed ? '' : '<span class="like-closed">추천 기간 종료</span>'}
      </div>
    </article>`;
  }

  function formatDate(value) {
    return value ? new Date(value).toLocaleDateString('ko-KR') : '';
  }

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch]));
  }
})();
