import { Editor } from 'https://esm.sh/@tiptap/core@2.11.7';
import StarterKit from 'https://esm.sh/@tiptap/starter-kit@2.11.7';
import Image from 'https://esm.sh/@tiptap/extension-image@2.11.7';
import Link from 'https://esm.sh/@tiptap/extension-link@2.11.7';

const BOARD_COLOR = {
  FREE:{color:'#5a626e',bg:'#f1f2f4'}, QNA:{color:'#3b5bdb',bg:'#eef1fd'}, SUBJECT:{color:'#1f9d57',bg:'#e4f6ec'},
};
const postId = location.pathname.split('/').filter(Boolean).pop();
let me = readUser();
let detailCache = null;
let editingAnswerId = null;
let answerEditor = null;

const qDetail = document.getElementById('questionDetail');
const answersSection = document.getElementById('answersSection');
const answersList = document.getElementById('answersList');

init();

function init() {
  document.getElementById('btnAsk').addEventListener('click', () => {
    if (!me) { alert('로그인 후 질문할 수 있습니다.'); if (typeof openLoginModal === 'function') openLoginModal(); return; }
    location.href = '/community/posts/new';
  });
  qDetail.addEventListener('click', onQuestionClick);
  answersList.addEventListener('click', onAnswersClick);
  window.addEventListener('f2r:login', (event) => {
    me = event.detail || readUser();
    revealAnswerComposer();
    if (detailCache) render(detailCache);
  });

  if (me) {
    revealAnswerComposer();
  } else {
    document.getElementById('answerLoginHint').hidden = false;
  }
  load();
}

function revealAnswerComposer() {
  document.getElementById('answerComposeWrap').hidden = false;
  document.getElementById('answerLoginHint').hidden = true;
  if (answerEditor) return;
  setupAnswerEditor();
  document.getElementById('answerToolbar').addEventListener('click', (e) => editorCmd(answerEditor, e, document.getElementById('answerImageInput')));
  document.getElementById('answerImageInput').addEventListener('change', (e) => uploadImage(e, answerEditor));
  document.getElementById('submitAnswer').addEventListener('click', submitAnswer);
}

function setupAnswerEditor() {
  answerEditor = new Editor({
    element: document.getElementById('answerEditor'),
    extensions: [StarterKit, Image.configure({ inline: false, allowBase64: false }), Link.configure({ openOnClick: false })],
    content: '',
    editorProps: { attributes: { class: 'ProseMirror', 'data-placeholder': '답변을 작성해주세요.' } },
  });
}

async function load() {
  try {
    const res = await fetch('/api/open-community/posts/' + encodeURIComponent(postId));
    if (res.status === 404) { qDetail.innerHTML = '<div class="feed-error">질문을 찾을 수 없습니다.</div>'; return; }
    if (!res.ok) throw new Error();
    detailCache = await res.json();
    render(detailCache);
  } catch { qDetail.innerHTML = '<div class="feed-error">불러오지 못했습니다.</div>'; }
}

function render(d) {
  const c = BOARD_COLOR[d.boardType] || BOARD_COLOR.FREE;
  document.getElementById('bcCategory').textContent = d.boardLabel;
  qDetail.innerHTML = `
    <div class="q-card-meta">
      <span class="cat-badge" style="color:${c.color};background:${c.bg};">${esc(d.boardLabel)}</span>
      <span class="who">${esc(d.authorName)} · ${fmt(d.createdAt)}</span>
    </div>
    <h1 class="detail-title">${esc(d.title)}</h1>
    <div class="content-html">${d.contentHtml || ''}</div>
    <div class="detail-actions">
      <button type="button" class="like-btn ${d.likedByMe ? 'liked' : ''}" data-like ${d.likeAllowed ? '' : 'disabled'}>♥ 추천 ${num(d.likeCount)}</button>
      ${d.likeAllowed ? '' : '<span class="like-closed">추천 기간 종료</span>'}
      ${canEdit(d.authorUserId) ? '<button type="button" class="txt-btn" data-edit-q>수정</button>' : ''}
      ${canManage(d.authorUserId) ? '<button type="button" class="txt-btn" data-del-q>삭제</button>' : ''}
    </div>`;
  answersSection.hidden = false;
  document.getElementById('answersTitle').textContent = `답변 ${(d.answers || []).length}`;
  answersList.innerHTML = (d.answers || []).map(renderAnswer).join('');
}

function renderAnswer(a) {
  return `<div class="answer-item" data-answer-id="${a.answerId}">
    <div class="answer-meta"><span>${esc(a.authorName)}</span> · <span>${fmt(a.createdAt)}</span></div>
    <div class="content-html">${a.contentHtml || ''}</div>
    <div class="answer-actions">
      ${canEdit(a.authorUserId) ? '<button type="button" class="txt-btn" data-edit-a>수정</button>' : ''}
      ${canManage(a.authorUserId) ? '<button type="button" class="txt-btn" data-del-a>삭제</button>' : ''}
    </div>
  </div>`;
}

/* ── question ── */
async function onQuestionClick(e) {
  if (e.target.closest('[data-like]')) { await send('/api/open-community/posts/' + postId + '/like', 'POST'); }
  else if (e.target.closest('[data-edit-q]')) { location.href = '/community/posts/new?edit=' + postId; }
  else if (e.target.closest('[data-del-q]')) {
    if (confirm('이 질문을 삭제할까요?') && await send('/api/open-community/posts/' + postId, 'DELETE')) location.href = '/community';
  }
}

/* ── answers ── */
async function onAnswersClick(e) {
  const box = e.target.closest('[data-answer-id]');
  if (e.target.closest('[data-edit-a]')) {
    const id = box.dataset.answerId;
    const a = (detailCache.answers || []).find(x => String(x.answerId) === id);
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
/* ── shared ── */
async function send(url, method, body) {
  try {
    const opts = { method };
    if (body) { opts.headers = { 'Content-Type': 'application/json' }; opts.body = JSON.stringify(body); }
    opts.redirectOnAuthError = false;
    const res = await fetchAuth(url, opts);
    if (res.status === 401 || res.status === 403) { alert('로그인 후 이용할 수 있습니다.'); if (typeof openLoginModal === 'function') openLoginModal(); return false; }
    if (method === 'DELETE') {
      if (res.ok || res.status === 204) { if (url.endsWith('/posts/' + postId)) return true; await load(); return true; }
      throw new Error();
    }
    if (!res.ok) { alert((await res.text()) || '처리에 실패했습니다.'); return false; }
    detailCache = await res.json();
    render(detailCache);
    return true;
  } catch { alert('처리에 실패했습니다.'); return false; }
}

function editorCmd(ed, e, imageInput) {
  const b = e.target.closest('[data-cmd]'); if (!b) return;
  const chain = () => ed.chain().focus();
  switch (b.dataset.cmd) {
    case 'bold': chain().toggleBold().run(); break;
    case 'italic': chain().toggleItalic().run(); break;
    case 'strike': chain().toggleStrike().run(); break;
    case 'bulletList': chain().toggleBulletList().run(); break;
    case 'code': chain().toggleCode().run(); break;
    case 'link': { const u = prompt('링크 URL'); if (u) chain().setLink({ href: u }).run(); break; }
    case 'image': imageInput.click(); break;
  }
}
async function uploadImage(e, ed) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  const form = new FormData(); form.append('image', file);
  try {
    const res = await fetchAuth('/api/open-community/images', { method: 'POST', body: form, redirectOnAuthError: false });
    if (res.status === 401 || res.status === 403) { alert('로그인 후 이용할 수 있습니다.'); if (typeof openLoginModal === 'function') openLoginModal(); return; }
    if (!res.ok) throw new Error();
    const img = await res.json();
    ed.chain().focus().setImage({ src: img.url }).run();
  } catch { alert('이미지 업로드에 실패했습니다.'); }
  e.target.value = '';
}

function canManage(uid) { return me && (me.userId === uid || me.role === 'ADMIN'); }
function canEdit(uid) { return me && me.userId === uid; }
function readUser() { try { return JSON.parse(sessionStorage.getItem('currentUser')); } catch { return null; } }
function num(n) { return Number(n || 0); }
function fmt(v) { return v ? new Date(v).toLocaleDateString('ko-KR') : ''; }
function esc(v) { return String(v || '').replace(/[&<>'"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c])); }
