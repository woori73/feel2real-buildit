import { Editor } from 'https://esm.sh/@tiptap/core@2.11.7';
import StarterKit from 'https://esm.sh/@tiptap/starter-kit@2.11.7';
import Image from 'https://esm.sh/@tiptap/extension-image@2.11.7';
import Link from 'https://esm.sh/@tiptap/extension-link@2.11.7';

/**
 * BuildIt Wiki 전용 글쓰기(/wiki/board/{key}/new). /home·/community 로 절대 이동하지 않는다.
 * 게시판은 URL의 {key}로 고정. variant/share만 문서 첨부.
 */
const BOARDS = {
  review: { boardType: 'EXAM_REVIEW', title: '시험 후기', attach: false, lead: '시험을 본 현장 후기를 나눠주세요.' },
  variant: { boardType: 'VARIANT', title: '변형문제', attach: true, lead: '유형별 변형문제·고난도 문제를 공유해주세요.' },
  share: { boardType: 'MATERIAL', title: '자료 공유·요청', attach: true, lead: '자료를 공유하거나 필요한 자료·문제 유형을 요청해주세요.' },
};
const MAX_FILES = 10;

const params = new URLSearchParams(location.search);
const editId = params.get('edit');
const segments = location.pathname.split('/').filter(Boolean); // ['wiki','board','{key}','new']
const key = segments[2];
const board = BOARDS[key];

const statusMsg = document.getElementById('statusMsg');
let editor = null;
let pendingFiles = [];

if (!board) {
  document.getElementById('writeTitle').textContent = '게시판을 찾을 수 없습니다.';
  document.getElementById('postForm').hidden = true;
} else {
  init();
}

async function init() {
  const boardHome = '/wiki/board/' + key;
  document.getElementById('backLink').setAttribute('href', boardHome);
  document.getElementById('cancelLink').setAttribute('href', boardHome);
  document.getElementById('writeTitle').textContent = board.title + (editId ? ' 수정' : ' 작성');
  document.getElementById('writeLead').textContent = board.lead;
  if (board.attach) document.getElementById('attachField').hidden = false;

  editor = new Editor({
    element: document.getElementById('editor'),
    extensions: [StarterKit, Image.configure({ inline: false, allowBase64: false }), Link.configure({ openOnClick: false })],
    content: '',
    editorProps: { attributes: { class: 'ProseMirror', 'data-placeholder': '내용을 자세히 작성해주세요.' } },
  });

  document.getElementById('toolbar').addEventListener('click', onCmd);
  document.getElementById('imageInput').addEventListener('change', onImage);
  document.getElementById('postForm').addEventListener('submit', onSubmit);
  const fileInput = document.getElementById('fileInput');
  if (fileInput) fileInput.addEventListener('change', onFilePick);

  if (editId) {
    document.getElementById('submitBtn').textContent = '수정';
    await loadForEdit();
  }
}

async function loadForEdit() {
  try {
    const res = await fetch('/api/open-community/posts/' + encodeURIComponent(editId));
    if (!res.ok) throw new Error();
    const p = await res.json();
    document.getElementById('title').value = p.title || '';
    editor.commands.setContent(p.contentHtml || '');
  } catch { show('글을 불러오지 못했습니다.', true); }
}

function onCmd(e) {
  const b = e.target.closest('[data-cmd]');
  if (!b) return;
  const chain = () => editor.chain().focus();
  switch (b.dataset.cmd) {
    case 'bold': chain().toggleBold().run(); break;
    case 'italic': chain().toggleItalic().run(); break;
    case 'strike': chain().toggleStrike().run(); break;
    case 'h3': chain().toggleHeading({ level: 3 }).run(); break;
    case 'bulletList': chain().toggleBulletList().run(); break;
    case 'orderedList': chain().toggleOrderedList().run(); break;
    case 'blockquote': chain().toggleBlockquote().run(); break;
    case 'code': chain().toggleCode().run(); break;
    case 'link': { const u = prompt('링크 URL'); if (u) chain().setLink({ href: u }).run(); break; }
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
    if (res.status === 401 || res.status === 403) { authFail(); return; }
    if (!res.ok) throw new Error();
    const img = await res.json();
    editor.chain().focus().setImage({ src: img.url }).run();
    show('', false);
  } catch { show('이미지 업로드에 실패했습니다.', true); }
  e.target.value = '';
}

function onFilePick(e) {
  const picked = Array.from(e.target.files || []);
  for (const f of picked) {
    if (pendingFiles.length >= MAX_FILES) { show('첨부는 최대 ' + MAX_FILES + '개까지 가능합니다.', true); break; }
    pendingFiles.push(f);
  }
  e.target.value = '';
  renderPending();
}

function renderPending() {
  const box = document.getElementById('filePending');
  box.innerHTML = pendingFiles.map((f, i) =>
    `<div class="file-pending"><span>${escapeHtml(f.name)} (${(f.size / (1024 * 1024)).toFixed(1)}MB)</span><button type="button" data-rm="${i}">제거</button></div>`
  ).join('');
  box.querySelectorAll('[data-rm]').forEach(btn => btn.addEventListener('click', () => {
    pendingFiles.splice(Number(btn.dataset.rm), 1);
    renderPending();
  }));
}

async function onSubmit(e) {
  e.preventDefault();
  const title = document.getElementById('title').value.trim();
  if (!title) { show('제목을 입력해주세요.', true); return; }
  if (editor.getText().trim() === '') { show('내용을 입력해주세요.', true); return; }

  const payload = {
    boardType: board.boardType,
    subjectId: null,
    title,
    contentJson: JSON.stringify(editor.getJSON()),
    contentHtml: editor.getHTML(),
  };
  const url = editId ? '/api/open-community/posts/' + editId : '/api/open-community/posts';
  const submitBtn = document.getElementById('submitBtn');
  submitBtn.disabled = true;
  try {
    const res = await fetchAuth(url, {
      method: editId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      redirectOnAuthError: false,
    });
    if (res.status === 401 || res.status === 403) { submitBtn.disabled = false; authFail(); return; }
    if (!res.ok) { submitBtn.disabled = false; show((await res.text()) || '저장에 실패했습니다.', true); return; }
    const saved = await res.json();
    const postId = saved.postId;

    if (pendingFiles.length) {
      show('첨부파일 업로드 중...', false);
      const fd = new FormData();
      pendingFiles.forEach(f => fd.append('files', f));
      const up = await fetchAuth('/api/open-community/posts/' + postId + '/attachments',
        { method: 'POST', body: fd, redirectOnAuthError: false });
      if (!up.ok && up.status !== 401 && up.status !== 403) {
        alert('첨부파일 업로드에 실패했습니다: ' + ((await up.text()) || up.status) + '\n글은 저장되었습니다.');
      }
    }
    location.href = '/wiki/posts/' + postId;
  } catch {
    submitBtn.disabled = false;
    show('저장에 실패했습니다.', true);
  }
}

function authFail() {
  alert('로그인 후 이용할 수 있습니다.');
  if (typeof openLoginModal === 'function') openLoginModal();
}
function show(msg, err) { statusMsg.textContent = msg; statusMsg.style.color = err ? '#c53030' : '#5b6472'; }
function escapeHtml(v) { return String(v || '').replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c])); }
