import { Editor } from 'https://esm.sh/@tiptap/core@2.11.7';
import StarterKit from 'https://esm.sh/@tiptap/starter-kit@2.11.7';
import Image from 'https://esm.sh/@tiptap/extension-image@2.11.7';
import Link from 'https://esm.sh/@tiptap/extension-link@2.11.7';

/**
 * BuildIt Wiki 전용 글 상세(/wiki/posts/{id}). /home·/community 로 절대 이동하지 않는다.
 * open-community API 재사용 + 첨부 다운로드/추가/삭제.
 */
const BOARD_KEY = { EXAM_REVIEW: 'review', VARIANT: 'variant', MATERIAL: 'share' };
const BOARD_ATTACH = { VARIANT: true, MATERIAL: true };
const WIKI_BOARDS = { EXAM_REVIEW: '시험 후기', VARIANT: '변형문제', MATERIAL: '자료 공유·요청' };

const postId = location.pathname.split('/').filter(Boolean).pop();
let me = readUser();
let cache = null;
let answerEditor = null;
let editingAnswerId = null;

const detailEl = document.getElementById('postDetail');
const answersSection = document.getElementById('answersSection');
const answersList = document.getElementById('answersList');

init();

function init() {
  detailEl.addEventListener('click', onDetailClick);
  answersList.addEventListener('click', onAnswersClick);
  window.addEventListener('f2r:login', (e) => { me = e.detail || readUser(); revealAnswerComposer(); if (cache) render(cache); });
  load();
}

async function load() {
  try {
    const res = await fetch('/api/open-community/posts/' + encodeURIComponent(postId));
    if (res.status === 404) { detailEl.innerHTML = '<p class="error">글을 찾을 수 없습니다.</p>'; return; }
    if (!res.ok) throw new Error();
    cache = await res.json();
    render(cache);
  } catch { detailEl.innerHTML = '<p class="error">불러오지 못했습니다.</p>'; }
}

function render(d) {
  const wiki = !!WIKI_BOARDS[d.boardType];
  const boardKey = BOARD_KEY[d.boardType] || 'review';
  const backLink = document.querySelector('.back-link');
  if (backLink) backLink.setAttribute('href', wiki ? '/wiki/board/' + boardKey : '/wiki');

  if (!wiki) {
    detailEl.innerHTML = '<div class="notice-box">이 글은 F2R 커뮤니티 글입니다. '
      + '<a href="/community/posts/' + d.postId + '">커뮤니티에서 보기 →</a></div>';
    answersSection.hidden = true;
    return;
  }

  const atts = d.attachments || [];
  detailEl.innerHTML = `
    <div class="detail-meta"><span class="cat-badge">${esc(d.boardLabel)}</span>${esc(d.authorName)} · ${fmt(d.createdAt)}</div>
    <h1 class="detail-title">${esc(d.title)}</h1>
    <div class="content-html">${d.contentHtml || ''}</div>
    ${atts.length || canEdit(d.authorUserId) && BOARD_ATTACH[d.boardType] ? renderAttachBlock(d, atts, boardKey) : ''}
    <div class="detail-actions">
      <button type="button" class="like-btn ${d.likedByMe ? 'liked' : ''}" data-like ${d.likeAllowed ? '' : 'disabled'}>♥ 추천 ${num(d.likeCount)}</button>
      ${d.likeAllowed ? '' : '<span class="like-closed">추천 기간 종료</span>'}
      ${canEdit(d.authorUserId) ? '<button type="button" class="txt-btn" data-edit>수정</button>' : ''}
      ${canManage(d.authorUserId) ? '<button type="button" class="txt-btn" data-del>삭제</button>' : ''}
    </div>`;

  answersSection.hidden = false;
  document.getElementById('answersTitle').textContent = `답변 ${(d.answers || []).length}`;
  answersList.innerHTML = (d.answers || []).map(renderAnswer).join('');

  if (me) revealAnswerComposer();
  else document.getElementById('answerLoginHint').hidden = false;

  const addInput = document.getElementById('attachAddInput');
  if (addInput) addInput.addEventListener('change', onAddAttachments);
}

function renderAttachBlock(d, atts, boardKey) {
  return `<div class="attach-block">
    <h4>첨부파일</h4>
    ${atts.map(a => `<div class="attach-row" data-att="${a.attachmentId}">
      <span class="name">${esc(a.originalFileName)}</span>
      <span class="size">${(a.fileSize / (1024 * 1024)).toFixed(1)}MB</span>
      <a class="dl" href="${escAttr(a.downloadUrl)}">내려받기 ↓</a>
      ${canEdit(d.authorUserId) ? '<button type="button" class="rm" data-rm-att>삭제</button>' : ''}
    </div>`).join('')}
    ${canEdit(d.authorUserId) && BOARD_ATTACH[d.boardType]
      ? '<label class="file-pending" style="cursor:pointer;color:#14705a;"><input type="file" id="attachAddInput" multiple hidden>＋ 파일 추가</label>'
      : ''}
  </div>`;
}

function renderAnswer(a) {
  return `<div class="answer-item" data-answer-id="${a.answerId}">
    <div class="answer-meta">${esc(a.authorName)} · ${fmt(a.createdAt)}</div>
    <div class="content-html">${a.contentHtml || ''}</div>
    <div class="answer-actions">
      ${canEdit(a.authorUserId) ? '<button type="button" class="txt-btn" data-edit-a>수정</button>' : ''}
      ${canManage(a.authorUserId) ? '<button type="button" class="txt-btn" data-del-a>삭제</button>' : ''}
    </div>
  </div>`;
}

function revealAnswerComposer() {
  const wrap = document.getElementById('answerComposeWrap');
  document.getElementById('answerLoginHint').hidden = true;
  wrap.hidden = false;
  if (answerEditor) return;
  answerEditor = new Editor({
    element: document.getElementById('answerEditor'),
    extensions: [StarterKit, Image.configure({ inline: false, allowBase64: false }), Link.configure({ openOnClick: false })],
    content: '',
    editorProps: { attributes: { class: 'ProseMirror', 'data-placeholder': '답변을 작성해주세요.' } },
  });
  document.getElementById('answerToolbar').addEventListener('click', (e) => {
    const b = e.target.closest('[data-cmd]'); if (!b) return;
    const chain = () => answerEditor.chain().focus();
    switch (b.dataset.cmd) {
      case 'bold': chain().toggleBold().run(); break;
      case 'italic': chain().toggleItalic().run(); break;
      case 'bulletList': chain().toggleBulletList().run(); break;
      case 'code': chain().toggleCode().run(); break;
      case 'link': { const u = prompt('링크 URL'); if (u) chain().setLink({ href: u }).run(); break; }
    }
  });
  document.getElementById('submitAnswer').addEventListener('click', submitAnswer);
}

async function onDetailClick(e) {
  const d = cache;
  if (e.target.closest('[data-like]')) { await send('/api/open-community/posts/' + postId + '/like', 'POST'); }
  else if (e.target.closest('[data-edit]')) { location.href = '/wiki/board/' + (BOARD_KEY[d.boardType] || 'review') + '/new?edit=' + postId; }
  else if (e.target.closest('[data-del]')) {
    if (confirm('이 글을 삭제할까요?') && await send('/api/open-community/posts/' + postId, 'DELETE')) {
      location.href = '/wiki/board/' + (BOARD_KEY[d.boardType] || 'review');
    }
  } else if (e.target.closest('[data-rm-att]')) {
    const row = e.target.closest('[data-att]');
    if (confirm('첨부파일을 삭제할까요?') && await send('/api/open-community/attachments/' + row.dataset.att, 'DELETE')) load();
  }
}

async function onAddAttachments(e) {
  const files = Array.from(e.target.files || []);
  e.target.value = '';
  if (!files.length) return;
  const fd = new FormData();
  files.forEach(f => fd.append('files', f));
  try {
    const res = await fetchAuth('/api/open-community/posts/' + postId + '/attachments',
      { method: 'POST', body: fd, redirectOnAuthError: false });
    if (res.status === 401 || res.status === 403) { authFail(); return; }
    if (!res.ok) { alert((await res.text()) || '첨부에 실패했습니다.'); return; }
    cache = await res.json();
    render(cache);
  } catch { alert('첨부에 실패했습니다.'); }
}

async function onAnswersClick(e) {
  const box = e.target.closest('[data-answer-id]');
  if (e.target.closest('[data-edit-a]')) {
    const id = box.dataset.answerId;
    const a = (cache.answers || []).find(x => String(x.answerId) === id);
    editingAnswerId = id;
    answerEditor.commands.setContent(a ? a.contentHtml : '');
    document.getElementById('submitAnswer').textContent = '답변 수정';
    document.getElementById('answerComposeWrap').scrollIntoView({ behavior: 'smooth' });
  } else if (e.target.closest('[data-del-a]')) {
    if (confirm('답변을 삭제할까요?')) await send('/api/open-community/answers/' + box.dataset.answerId, 'DELETE');
  }
}

async function submitAnswer() {
  if (answerEditor.getText().trim() === '') { alert('답변 내용을 입력해주세요.'); return; }
  const payload = { contentJson: JSON.stringify(answerEditor.getJSON()), contentHtml: answerEditor.getHTML() };
  const url = editingAnswerId ? '/api/open-community/answers/' + editingAnswerId : '/api/open-community/posts/' + postId + '/answers';
  if (await send(url, editingAnswerId ? 'PUT' : 'POST', payload)) {
    answerEditor.commands.setContent('');
    editingAnswerId = null;
    document.getElementById('submitAnswer').textContent = '답변 등록';
  }
}

async function send(url, method, body) {
  try {
    const opts = { method, redirectOnAuthError: false };
    if (body) { opts.headers = { 'Content-Type': 'application/json' }; opts.body = JSON.stringify(body); }
    const res = await fetchAuth(url, opts);
    if (res.status === 401 || res.status === 403) { authFail(); return false; }
    if (method === 'DELETE') {
      if (res.ok || res.status === 204) { if (url.endsWith('/posts/' + postId)) return true; await load(); return true; }
      throw new Error();
    }
    if (!res.ok) { alert((await res.text()) || '처리에 실패했습니다.'); return false; }
    cache = await res.json();
    render(cache);
    return true;
  } catch { alert('처리에 실패했습니다.'); return false; }
}

function authFail() { alert('로그인 후 이용할 수 있습니다.'); if (typeof openLoginModal === 'function') openLoginModal(); }
function canManage(uid) { return me && (me.userId === uid || me.role === 'ADMIN'); }
function canEdit(uid) { return me && me.userId === uid; }
function readUser() { try { return JSON.parse(sessionStorage.getItem('currentUser')); } catch { return null; } }
function num(n) { return Number(n || 0); }
function fmt(v) { return v ? new Date(v).toLocaleDateString('ko-KR') : ''; }
function esc(v) { return String(v || '').replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c])); }
function escAttr(v) { return esc(v).replace(/`/g, '&#96;'); }
