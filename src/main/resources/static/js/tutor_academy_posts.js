(function () {
  const list = document.getElementById('postList');
  const count = document.getElementById('postCount');
  load();
  async function load() {
    try {
      const res = await fetchAuth('/api/tutor/academy/blog-posts');
      if (!res.ok) throw new Error(await res.text());
      const posts = await res.json();
      count.textContent = posts.length + '개';
      list.innerHTML = posts.length ? posts.map(row).join('') : '<p class="muted">아직 작성된 글이 없습니다.</p>';
    } catch (e) { list.innerHTML = '<p class="error">글 목록을 불러오지 못했습니다.</p>'; }
  }
  function row(post) {
    const hidden = !post.visible ? '<span class="status-pill hidden">숨김</span>' : '';
    const draft = post.status === 'DRAFT' ? ' draft' : '';
    return `<article class="blog-post-row">
      <div><div><span class="status-pill${draft}">${post.status === 'PUBLISHED' ? '공개' : '임시저장'}</span>${hidden}</div>
      <h3>${escapeHtml(post.title)}</h3><p class="muted">/${escapeHtml(post.slug)} · ${formatDate(post.updatedAt)}</p>
      <p>${escapeHtml(post.summary || '')}</p></div>
      <div class="blog-post-actions"><a class="btn btn-secondary" href="/tutor/academy/posts/${post.postId}">수정</a>${post.status === 'PUBLISHED' && post.visible ? `<a class="btn btn-ghost" href="${post.publicUrl}" target="_blank">보기</a>` : ''}</div>
    </article>`;
  }
  function formatDate(v) { return v ? new Date(v).toLocaleDateString('ko-KR') : ''; }
  function escapeHtml(value) { return String(value || '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch])); }
})();
