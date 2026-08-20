(function () {
  if (!window.F2RAcademyBusinessInfo) {
    throw new Error('F2RAcademyBusinessInfo must be loaded before academy_blog_public.js');
  }
  const BUSINESS_INFO = window.F2RAcademyBusinessInfo;
  const parts = location.pathname.split('/').filter(Boolean);
  const academySlug = decodeURIComponent(parts[1] || '');
  const isDetail = parts.length >= 4;
  document.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-like-post-id]');
    if (!button) return;
    event.preventDefault();
    await toggleLike(button.dataset.likePostId);
  });
  if (isDetail) loadDetail(decodeURIComponent(parts[3] || '')); else loadList();

  async function loadList() {
    const root = document.getElementById('blogListView');
    try {
      const [academyRes, postsRes] = await Promise.all([
        fetch('/api/academies/' + encodeURIComponent(academySlug)),
        fetch('/api/academies/' + encodeURIComponent(academySlug) + '/blog-posts')
      ]);
      if (!academyRes.ok || !postsRes.ok) throw new Error('블로그를 찾을 수 없습니다.');
      const academy = await academyRes.json();
      const posts = await postsRes.json();
      document.title = academy.name + ' 블로그';
      root.innerHTML = `<div class="topbar"><div><h1>${escapeHtml(academy.name)} 블로그</h1><p class="muted">Academy 소식과 학습 정보를 확인하세요.</p></div><div class="blog-detail-actions"><a class="btn btn-ghost" href="/academy-posts">전체 글 보기</a><a class="btn btn-ghost" href="/academies/${academy.slug}">Academy로</a></div></div>
        <div class="blog-public-list">${posts.length ? posts.map(card).join('') : '<p class="muted">아직 공개된 글이 없습니다.</p>'}</div>`;
      renderAcademyBusinessFooter(academy);
    } catch (e) { root.innerHTML = '<p class="error">블로그를 불러오지 못했습니다.</p>'; }
  }

  async function loadDetail(postSlug) {
    const root = document.getElementById('blogPostView');
    try {
      const [academyRes, postRes] = await Promise.all([
        fetch('/api/academies/' + encodeURIComponent(academySlug)),
        fetch('/api/academies/' + encodeURIComponent(academySlug) + '/blog-posts/' + encodeURIComponent(postSlug))
      ]);
      if (!academyRes.ok || !postRes.ok) throw new Error('글을 찾을 수 없습니다.');
      const academy = await academyRes.json();
      const post = await postRes.json();
      document.title = post.metaTitle || post.title;
      const meta = document.querySelector('meta[name="description"]');
      if (meta) meta.setAttribute('content', post.metaDescription || post.summary || post.title);
      root.innerHTML = `<p><a class="btn btn-ghost" href="/academies/${post.academySlug}/posts">← 글 목록</a></p>
        <h1>${escapeHtml(post.title)}</h1>
        <p class="muted">${formatDate(post.publishedAt || post.updatedAt)}</p>
        <div class="blog-detail-actions">${likeButton(post)}<a class="btn btn-ghost" href="/academy-posts">전체 글 보기</a></div>
        ${post.summary ? `<p class="lead">${escapeHtml(post.summary)}</p>` : ''}
        <div class="blog-public-content">${post.contentHtml || ''}</div>`;
      renderAcademyBusinessFooter(academy);
    } catch (e) { root.innerHTML = '<p class="error">글을 불러오지 못했습니다.</p>'; }
  }

  function renderAcademyBusinessFooter(academy) {
    const footerRoot = document.getElementById('academyBusinessFooter');
    if (!footerRoot || !academy) return;
    const year = new Date().getFullYear();
    const businessInfo = academy.pageSettings && academy.pageSettings.businessInfo;
    footerRoot.innerHTML = `<footer class="academy-business-footer">
      <div class="academy-business-footer__brand">
        <div><div class="academy-business-footer__brand-name">${escapeHtml(academy.name)}</div><span class="academy-business-footer__brand-sub">Academy 블로그</span></div>
      </div>
      <hr class="academy-business-footer__divider">
      <div class="academy-business-footer__rows">${BUSINESS_INFO.renderRows(businessInfo, escapeHtml)}</div>
      <div class="academy-business-footer__copyright">© ${year} ${escapeHtml(academy.name)}. All rights reserved.</div>
    </footer>`;
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
      if (isDetail) await loadDetail(decodeURIComponent(parts[3] || '')); else await loadList();
    } catch (error) {
      alert('좋아요 처리에 실패했습니다.');
    }
  }

  function card(post) {
    return `<article class="blog-public-card"><a href="${post.publicUrl}" class="academy-post-card-main"><h2>${escapeHtml(post.title)}</h2><p class="muted">${formatDate(post.publishedAt || post.updatedAt)}</p><p>${escapeHtml(post.summary || '')}</p></a><div class="blog-detail-actions">${likeButton(post)}</div></article>`;
  }

  function likeButton(post) {
    return `<button type="button" class="like-button ${post.likedByMe ? 'liked' : ''}" data-like-post-id="${post.postId}" ${post.likeAllowed ? '' : 'disabled'}>추천 ${Number(post.likeCount || 0)}</button>${post.likeAllowed ? '' : '<span class="like-closed">추천 기간 종료</span>'}`;
  }

  function formatDate(v) { return v ? new Date(v).toLocaleDateString('ko-KR') : ''; }
  function escapeHtml(value) { return String(value || '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch])); }
})();
