import { Editor } from 'https://esm.sh/@tiptap/core@2.11.7';
import StarterKit from 'https://esm.sh/@tiptap/starter-kit@2.11.7';
import Image from 'https://esm.sh/@tiptap/extension-image@2.11.7';
import Link from 'https://esm.sh/@tiptap/extension-link@2.11.7';

const params = new URLSearchParams(location.search);
const editId = params.get('edit');
// BuildIt Wiki(/wiki)에서 넘어온 경우: 게시판 고정 + 저장 후 wiki로 복귀.
const WIKI_BOARDS = { EXAM_REVIEW: '시험 후기', VARIANT: '변형문제', MATERIAL: '자료 공유·요청' };
const presetBoard = params.get('board');
const returnUrl = safeReturn(params.get('return'));
const statusMsg = document.getElementById('statusMsg');
let boardType = null;   // 'FREE' | 'QNA' | 'SUBJECT' | 'EXAM_REVIEW' | 'VARIANT' | 'MATERIAL'
let subjectId = null;   // SUBJECT일 때만
let editor = null;

/** open redirect 방지: 같은 출처의 절대경로만 허용. */
function safeReturn(v) {
  return v && v.startsWith('/') && !v.startsWith('//') ? v : null;
}

init();

async function init() {
  editor = new Editor({
    element: document.getElementById('editor'),
    extensions: [StarterKit, Image.configure({ inline: false, allowBase64: false }), Link.configure({ openOnClick: false })],
    content: '',
    editorProps: { attributes: { class: 'ProseMirror', 'data-placeholder': '질문 내용을 자세히 작성해주세요.' } },
  });

  document.getElementById('catPick').addEventListener('click', onCatPick);
  document.getElementById('toolbar').addEventListener('click', onCmd);
  document.getElementById('imageInput').addEventListener('change', onImage);
  document.getElementById('postForm').addEventListener('submit', onSubmit);

  await loadSubjects();   // 과목 칩 먼저 채워야 edit 선택이 동작

  if (editId) {
    document.getElementById('writeTitle').textContent = '질문 수정';
    document.getElementById('submitBtn').textContent = '수정';
    await loadForEdit();
  } else if (WIKI_BOARDS[presetBoard]) {
    lockWikiBoard(presetBoard);
    document.getElementById('writeTitle').textContent = WIKI_BOARDS[presetBoard] + ' 작성';
    document.querySelector('.lead').textContent = 'BuildIt Wiki에 함께 나눌 내용을 적어주세요.';
    const cancel = document.querySelector('.write-actions a.btn-ghost');
    if (cancel && returnUrl) cancel.setAttribute('href', returnUrl);
  }
}

/** wiki 게시판(칩 없음): 해당 게시판 칩 1개만 렌더하고 picker를 잠근다. */
function lockWikiBoard(board) {
  const pick = document.getElementById('catPick');
  pick.innerHTML = '';
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'cat-tab active';
  btn.dataset.board = board;
  btn.textContent = WIKI_BOARDS[board];
  pick.appendChild(btn);
  pick.classList.add('locked');
  boardType = board;
  subjectId = null;
}

async function loadSubjects() {
  try {
    const res = await fetch('/api/subjects');
    if (!res.ok) return;
    const subjects = await res.json();
    const pick = document.getElementById('catPick');
    subjects.forEach(s => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'cat-tab';
      btn.dataset.board = 'SUBJECT';
      btn.dataset.subject = s.subjectId;
      btn.textContent = s.nameKo;
      pick.appendChild(btn);
    });
  } catch { /* 과목 칩 없이도 동작 */ }
}

async function loadForEdit() {
  try {
    const res = await fetch('/api/open-community/posts/' + encodeURIComponent(editId));
    if (!res.ok) throw new Error();
    const p = await res.json();
    document.getElementById('title').value = p.title || '';
    if (WIKI_BOARDS[p.boardType]) {
      lockWikiBoard(p.boardType);
    } else {
      selectBoard(p.boardType, p.subjectId != null ? String(p.subjectId) : null);
    }
    editor.commands.setContent(p.contentHtml || '');
  } catch { show('질문을 불러오지 못했습니다.', true); }
}

function onCatPick(e) {
  const b = e.target.closest('.cat-tab');
  if (b) selectBoard(b.dataset.board, b.dataset.subject || null);
}
function selectBoard(board, subject) {
  boardType = board;
  subjectId = board === 'SUBJECT' ? subject : null;
  document.querySelectorAll('#catPick .cat-tab').forEach(t =>
    t.classList.toggle('active', t.dataset.board === board && (board !== 'SUBJECT' || t.dataset.subject === subject)));
}

function onCmd(e) {
  const b = e.target.closest('[data-cmd]');
  if (!b) return;
  const chain = () => editor.chain().focus();
  switch (b.dataset.cmd) {
    case 'bold': chain().toggleBold().run(); break;
    case 'italic': chain().toggleItalic().run(); break;
    case 'strike': chain().toggleStrike().run(); break;
    case 'h2': chain().toggleHeading({ level: 2 }).run(); break;
    case 'h3': chain().toggleHeading({ level: 3 }).run(); break;
    case 'bulletList': chain().toggleBulletList().run(); break;
    case 'orderedList': chain().toggleOrderedList().run(); break;
    case 'blockquote': chain().toggleBlockquote().run(); break;
    case 'code': chain().toggleCode().run(); break;
    case 'link': { const url = prompt('링크 URL'); if (url) chain().setLink({ href: url }).run(); break; }
    case 'image': document.getElementById('imageInput').click(); break;
  }
}

async function onImage(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  const form = new FormData(); form.append('image', file);
  try {
    show('이미지 업로드 중...', false);
    const res = await fetchAuth('/api/open-community/images', { method: 'POST', body: form, redirectOnAuthError: false });
    if (res.status === 401 || res.status === 403) { alert('로그인 후 이용할 수 있습니다.'); if (typeof openLoginModal === 'function') openLoginModal(); return; }
    if (!res.ok) throw new Error();
    const img = await res.json();
    editor.chain().focus().setImage({ src: img.url }).run();
    show('', false);
  } catch { show('이미지 업로드에 실패했습니다.', true); }
  e.target.value = '';
}

async function onSubmit(e) {
  e.preventDefault();
  const title = document.getElementById('title').value.trim();
  if (!title) { show('제목을 입력해주세요.', true); return; }
  if (!boardType) { show('게시판을 선택해주세요.', true); return; }
  if (boardType === 'SUBJECT' && !subjectId) { show('과목을 선택해주세요.', true); return; }
  if (editor.getText().trim() === '') { show('내용을 입력해주세요.', true); return; }
  const payload = {
    boardType,
    subjectId: boardType === 'SUBJECT' ? Number(subjectId) : null,
    title,
    contentJson: JSON.stringify(editor.getJSON()),
    contentHtml: editor.getHTML(),
  };
  const url = editId ? '/api/open-community/posts/' + editId : '/api/open-community/posts';
  try {
    const res = await fetchAuth(url, {
      method: editId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      redirectOnAuthError: false,
    });
    if (res.status === 401 || res.status === 403) { alert('로그인 후 이용할 수 있습니다.'); if (typeof openLoginModal === 'function') openLoginModal(); return; }
    if (!res.ok) { show((await res.text()) || '저장에 실패했습니다.', true); return; }
    const saved = await res.json();
    location.href = returnUrl || ('/community/posts/' + saved.postId);
  } catch { show('저장에 실패했습니다.', true); }
}

function show(msg, err) { statusMsg.textContent = msg; statusMsg.style.color = err ? '#c53030' : '#9aa1ab'; }
