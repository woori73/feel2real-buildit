/**
 * 공개 BuildIt Wiki 게시판별 목록(/wiki/board/{key}).
 * key = review | variant | share. open-community 목록 API를 scope=wiki로 재사용한다.
 * 글쓰기/상세는 /wiki 전용 화면(/wiki/board/{key}/new, /wiki/posts/{id}).
 */
(function () {
  var BOARDS = {
    review: { boardType: 'EXAM_REVIEW', title: '시험 후기', desc: '시험을 본 사람들의 현장 후기를 나눕니다.' },
    variant: { boardType: 'VARIANT', title: '변형문제', desc: '유형별 변형문제·고난도 문제를 공유합니다.' },
    share: { boardType: 'MATERIAL', title: '자료 공유·요청', desc: '가진 자료를 공유하거나 필요한 자료·문제 유형을 요청합니다.' }
  };

  var key = location.pathname.split('/').filter(Boolean).pop();
  var board = BOARDS[key];
  var list = document.getElementById('boardList');
  var form = document.getElementById('boardSearch');

  if (!board) {
    document.getElementById('boardTitle').textContent = '게시판을 찾을 수 없습니다.';
    list.innerHTML = '<p class="error">주소를 확인해 주세요. <a href="/wiki">BuildIt Wiki로 이동</a></p>';
    return;
  }

  document.getElementById('bcBoard').textContent = board.title;
  document.getElementById('boardTitle').textContent = board.title;
  document.getElementById('boardDesc').textContent = board.desc;
  document.title = board.title + ' · BuildIt Wiki';

  document.getElementById('btnWrite').addEventListener('click', goWrite);
  form.addEventListener('submit', function (e) { e.preventDefault(); load(); });
  form.querySelector('select').addEventListener('change', load);

  load();

  function goWrite() {
    if (!readUser()) {
      alert('로그인 후 작성할 수 있습니다.');
      if (typeof openLoginModal === 'function') openLoginModal();
      return;
    }
    location.href = '/wiki/board/' + encodeURIComponent(key) + '/new';
  }

  async function load() {
    var params = new URLSearchParams();
    params.set('scope', 'wiki');
    params.set('boardType', board.boardType);
    var fd = new FormData(form);
    if (fd.get('keyword')) params.set('keyword', fd.get('keyword'));
    params.set('sort', fd.get('sort') || 'latest');
    list.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/open-community/posts?' + params.toString());
      if (!res.ok) throw new Error();
      var posts = await res.json();
      list.innerHTML = posts.length
        ? posts.map(card).join('')
        : '<p class="muted">아직 글이 없어요. 첫 글을 남겨보세요.</p>';
    } catch (_) {
      list.innerHTML = '<p class="error">목록을 불러오지 못했습니다.</p>';
    }
  }

  function card(p) {
    return '<a class="q-card" href="/wiki/posts/' + p.postId + '">'
      + '<div class="q-card-meta"><span class="who">' + escapeHtml(p.authorName) + ' · ' + fmt(p.createdAt) + '</span></div>'
      + '<h3>' + escapeHtml(p.title) + '</h3>'
      + '<div class="q-card-stats"><span class="answers">✓ 답변 ' + Number(p.answerCount || 0) + '</span>'
      + '<span>♥ ' + Number(p.likeCount || 0) + '</span></div>'
      + '</a>';
  }

  function readUser() { try { return JSON.parse(sessionStorage.getItem('currentUser')); } catch (_) { return null; } }
  function fmt(v) { return v ? new Date(v).toLocaleDateString('ko-KR') : ''; }
  function escapeHtml(v) {
    return String(v || '').replace(/[&<>'"]/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c];
    });
  }
})();
