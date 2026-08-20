(function () {
  const tabs = document.getElementById('catTabs');
  const form = document.getElementById('feedSearch');
  const list = document.getElementById('feedList');
  const countEl = document.getElementById('feedCount');
  const btnAsk = document.getElementById('btnAsk');

  // 게시판 종류별 배지 색. 과목(SUBJECT)은 단일 색으로 통일.
  const BOARD_COLOR = {
    FREE:    { color: '#5a626e', bg: '#f1f2f4' },
    QNA:     { color: '#3b5bdb', bg: '#eef1fd' },
    SUBJECT: { color: '#1f9d57', bg: '#e4f6ec' },
  };

  // 현재 선택된 탭: boardType('' = 전체) + subjectId(SUBJECT 탭일 때만).
  let boardType = '';
  let subjectId = '';

  loadSubjects();   // 과목 탭 동적 추가
  load();           // 첫 목록

  tabs.addEventListener('click', (e) => {
    const b = e.target.closest('.cat-tab');
    if (!b) return;
    tabs.querySelectorAll('.cat-tab').forEach(t => t.classList.toggle('active', t === b));
    boardType = b.dataset.board || '';
    subjectId = b.dataset.subject || '';
    load();
  });
  form.addEventListener('submit', (e) => { e.preventDefault(); load(); });
  form.querySelector('select').addEventListener('change', load);
  btnAsk.addEventListener('click', () => {
    if (!readUser()) { alert('로그인 후 질문할 수 있습니다.'); if (typeof openLoginModal === 'function') openLoginModal(); return; }
    location.href = '/community/posts/new';
  });

  async function loadSubjects() {
    try {
      const res = await fetch('/api/subjects');
      if (!res.ok) return;
      const subjects = await res.json();
      subjects.forEach(s => {
        const btn = document.createElement('button');
        btn.className = 'cat-tab';
        btn.dataset.board = 'SUBJECT';
        btn.dataset.subject = s.subjectId;
        btn.textContent = s.nameKo;
        tabs.appendChild(btn);
      });
    } catch { /* 과목 탭 없이도 동작 */ }
  }

  async function load() {
    const fd = new FormData(form);
    const params = new URLSearchParams();
    if (boardType) params.set('boardType', boardType);
    if (boardType === 'SUBJECT' && subjectId) params.set('subjectId', subjectId);
    if (fd.get('keyword')) params.set('keyword', fd.get('keyword'));
    params.set('sort', fd.get('sort') || 'latest');
    list.innerHTML = '<div class="feed-empty">불러오는 중...</div>';
    try {
      const res = await fetch('/api/open-community/posts?' + params.toString());
      if (!res.ok) throw new Error();
      const posts = await res.json();
      countEl.textContent = `질문 · ${posts.length}`;
      list.innerHTML = posts.length ? posts.map(card).join('')
        : '<div class="feed-empty">아직 질문이 없어요. 첫 질문을 남겨보세요.</div>';
    } catch {
      list.innerHTML = '<div class="feed-error">목록을 불러오지 못했습니다.</div>';
    }
  }

  function card(p) {
    const c = BOARD_COLOR[p.boardType] || BOARD_COLOR.FREE;
    return `<a class="q-card" href="/community/posts/${p.postId}">
      <div class="q-card-meta">
        <span class="cat-badge" style="color:${c.color};background:${c.bg};">${escapeHtml(p.boardLabel)}</span>
        <span class="who">${escapeHtml(p.authorName)} · ${fmt(p.createdAt)}</span>
      </div>
      <h3>${escapeHtml(p.title)}</h3>
      <div class="q-card-stats">
        <span class="answers">✓ 답변 ${num(p.answerCount)}</span>
        <span>♥ ${num(p.likeCount)}</span>
      </div>
    </a>`;
  }

  function readUser() { try { return JSON.parse(sessionStorage.getItem('currentUser')); } catch { return null; } }
  function num(n) { return Number(n || 0); }
  function fmt(v) { return v ? new Date(v).toLocaleDateString('ko-KR') : ''; }
  function escapeHtml(v) {
    return String(v || '').replace(/[&<>'"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));
  }
})();
