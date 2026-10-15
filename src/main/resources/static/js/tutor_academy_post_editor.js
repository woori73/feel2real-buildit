import { Editor } from 'https://esm.sh/@tiptap/core@2.11.7';
import StarterKit from 'https://esm.sh/@tiptap/starter-kit@2.11.7';
import Image from 'https://esm.sh/@tiptap/extension-image@2.11.7';
import Link from 'https://esm.sh/@tiptap/extension-link@2.11.7';
import BubbleMenu from 'https://esm.sh/@tiptap/extension-bubble-menu@2.11.7';
import TextAlign from 'https://esm.sh/@tiptap/extension-text-align@2.11.7';
import { createIcons, icons } from 'https://esm.sh/lucide@0.460.0';

// 정렬을 inline style 대신 class로 저장한다 (sanitizer가 style은 제거하지만 class는 허용 — 기존 패턴과 일관).
const ALIGN_TO_CLASS = { left: 'text-left', center: 'text-center', right: 'text-right' };
const CLASS_TO_ALIGN = { 'text-left': 'left', 'text-center': 'center', 'text-right': 'right' };
const ClassTextAlign = TextAlign.extend({
  addGlobalAttributes() {
    return [{
      types: this.options.types,
      attributes: {
        textAlign: {
          default: this.options.defaultAlignment,
          parseHTML: (el) => {
            const cls = (el.getAttribute('class') || '').split(/\s+/).find((c) => CLASS_TO_ALIGN[c]);
            return cls ? CLASS_TO_ALIGN[cls] : (el.style.textAlign || this.options.defaultAlignment);
          },
          renderHTML: (attrs) => (ALIGN_TO_CLASS[attrs.textAlign] ? { class: ALIGN_TO_CLASS[attrs.textAlign] } : {})
        }
      }
    }];
  }
});

// 표시 크기 프리셋(작게/중간/크게)을 class로 저장하기 위해 Image 노드에 class 속성을 추가한다.
const SizedImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      class: {
        default: null,
        parseHTML: (el) => el.getAttribute('class'),
        renderHTML: (attrs) => (attrs.class ? { class: attrs.class } : {})
      },
      // 이미지 정렬은 크기 class와 충돌하지 않도록 별도 data-align 속성으로 저장한다.
      align: {
        default: null,
        parseHTML: (el) => el.getAttribute('data-align'),
        renderHTML: (attrs) => (attrs.align ? { 'data-align': attrs.align } : {})
      }
    };
  }
});

const postId = pathPostId();
const sourceIds = new Set();
let editor = null;
// 마지막으로 본문을 클릭한 좌표 — 크기 메뉴를 이미지 상단이 아니라 클릭 위치에 띄우기 위해 사용한다.
let lastPointer = { x: 0, y: 0 };
const statusMsg = document.getElementById('statusMsg');

init();

async function init() {
  setupEditor();
  document.getElementById('postForm').addEventListener('submit', save);
  document.getElementById('imageButton').addEventListener('click', () => document.getElementById('imageInput').click());
  document.getElementById('imageInput').addEventListener('change', uploadImage);
  document.querySelectorAll('[data-command]').forEach((btn) => btn.addEventListener('click', runCommand));
  document.getElementById('title').addEventListener('input', autoSlug);
  document.getElementById('deleteButton').addEventListener('click', deletePost);
  createIcons({ icons }); // 툴바의 data-lucide 아이콘을 SVG로 렌더
  if (postId) await loadPost();
  updateToolbarState();
}

function setupEditor() {
  const imageMenu = buildImageBubbleMenu();
  editor = new Editor({
    element: document.getElementById('editor'),
    extensions: [
      StarterKit,
      SizedImage.configure({ inline: false, allowBase64: false }),
      Link.configure({ openOnClick: false }),
      ClassTextAlign.configure({ types: ['heading', 'paragraph'] }),
      // 이미지를 선택(클릭)했을 때만 크기 선택 메뉴를 띄운다. 위치는 클릭 좌표 기준(큰 이미지 대응).
      BubbleMenu.configure({
        element: imageMenu,
        shouldShow: ({ editor }) => editor.isActive('image'),
        tippyOptions: {
          placement: 'top',
          getReferenceClientRect: () => new DOMRect(lastPointer.x, lastPointer.y, 0, 0)
        }
      })
    ],
    content: '<p>본문을 입력하세요. 한글과 이미지를 지원합니다.</p>',
    editorProps: { attributes: { class: 'ProseMirror' } },
    onSelectionUpdate: updateToolbarState,
    onTransaction: updateToolbarState
  });
  // 본문 클릭 좌표를 기록 (mousedown이 선택/메뉴 표시보다 먼저 발생).
  editor.view.dom.addEventListener('mousedown', (e) => { lastPointer = { x: e.clientX, y: e.clientY }; });
}

// 이미지 선택 시 떠오르는 플로팅 크기 메뉴(작게/중간/크게)를 만든다.
function buildImageBubbleMenu() {
  const menu = document.createElement('div');
  menu.className = 'image-bubble-menu';
  menu.innerHTML =
    '<button type="button" data-command="imgSmall">작게</button>'
    + '<button type="button" data-command="imgMedium">중간</button>'
    + '<button type="button" data-command="imgLarge">크게</button>';
  menu.querySelectorAll('[data-command]').forEach((b) => b.addEventListener('click', runCommand));
  document.body.appendChild(menu);
  return menu;
}

async function loadPost() {
  try {
    const res = await fetchAuth('/api/tutor/academy/blog-posts/' + postId);
    if (!res.ok) throw new Error(await res.text());
    const post = await res.json();
    document.getElementById('editorTitle').textContent = '블로그 글 수정';
    set('title', post.title); set('slug', post.slug); set('summary', post.summary);
    set('metaTitle', post.metaTitle); set('metaDescription', post.metaDescription);
    set('status', post.status); document.getElementById('visible').checked = post.visible;
    if (editor) editor.commands.setContent(post.contentJson ? JSON.parse(post.contentJson) : post.contentHtml);
    (post.sources || []).forEach((s) => sourceIds.add(s.sourceId));
    document.getElementById('deleteButton').classList.remove('hidden');
  } catch (e) { show('글을 불러오지 못했습니다.', true); }
}

async function save(e) {
  e.preventDefault();
  const payload = {
    title: value('title'), slug: value('slug'), summary: value('summary'),
    metaTitle: value('metaTitle'), metaDescription: value('metaDescription'),
    status: value('status'), visible: document.getElementById('visible').checked,
    contentJson: JSON.stringify(editor.getJSON()), contentHtml: editor.getHTML(),
    sourceIds: Array.from(sourceIds)
  };
  try {
    const res = await fetchAuth(postId ? '/api/tutor/academy/blog-posts/' + postId : '/api/tutor/academy/blog-posts', {
      method: postId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(await res.text());
    location.href = '/tutor/academy/posts';
  } catch (e) { show('저장 실패: 제목, URL, 본문을 확인해 주세요.', true); }
}

async function uploadImage(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  const form = new FormData(); form.append('image', file);
  try {
    show('이미지 업로드 중...', false);
    const res = await fetchAuth('/api/tutor/academy/blog-sources/images', { method: 'POST', body: form });
    if (!res.ok) throw new Error(await res.text());
    const source = await res.json();
    sourceIds.add(source.sourceId);
    editor.chain().focus().setImage({ src: source.fileUrl, alt: source.originalFilename }).run();
    show('이미지를 추가했습니다.', false);
  } catch (err) { show('이미지 업로드에 실패했습니다.', true); }
  e.target.value = '';
}

function runCommand(e) {
  const cmd = e.currentTarget.dataset.command;
  const chain = () => editor.chain().focus();
  switch (cmd) {
    case 'bold': chain().toggleBold().run(); break;
    case 'italic': chain().toggleItalic().run(); break;
    case 'strike': chain().toggleStrike().run(); break;
    case 'h2': chain().toggleHeading({ level: 2 }).run(); break;
    case 'h3': chain().toggleHeading({ level: 3 }).run(); break;
    case 'bulletList': chain().toggleBulletList().run(); break;
    case 'orderedList': chain().toggleOrderedList().run(); break;
    case 'blockquote': chain().toggleBlockquote().run(); break;
    case 'alignLeft': applyAlign('left'); break;
    case 'alignCenter': applyAlign('center'); break;
    case 'alignRight': applyAlign('right'); break;
    case 'undo': chain().undo().run(); break;
    case 'redo': chain().redo().run(); break;
    case 'imgSmall': setImageSize('blog-img-sm'); break;
    case 'imgMedium': setImageSize('blog-img-md'); break;
    case 'imgLarge': setImageSize('blog-img-lg'); break;
    case 'link': {
      const url = prompt('링크 URL을 입력하세요.');
      if (url) chain().setLink({ href: url }).run();
      break;
    }
    default: break;
  }
}

// 현재 커서/선택 위치에서 활성화된 서식 버튼에 .is-active 표시 (직관적인 토글 상태).
const ACTIVE_CHECKS = {
  bold: () => editor.isActive('bold'),
  italic: () => editor.isActive('italic'),
  strike: () => editor.isActive('strike'),
  h2: () => editor.isActive('heading', { level: 2 }),
  h3: () => editor.isActive('heading', { level: 3 }),
  bulletList: () => editor.isActive('bulletList'),
  orderedList: () => editor.isActive('orderedList'),
  blockquote: () => editor.isActive('blockquote'),
  alignLeft: () => isAlignActive('left'),
  alignCenter: () => isAlignActive('center'),
  alignRight: () => isAlignActive('right'),
  link: () => editor.isActive('link')
};
// 이미지 선택 시엔 이미지 정렬(data-align), 아니면 문단 정렬(textAlign) 기준으로 활성 판정.
function isAlignActive(dir) {
  if (editor.isActive('image')) return editor.getAttributes('image').align === dir;
  return editor.isActive({ textAlign: dir });
}
function updateToolbarState() {
  document.querySelectorAll('.editor-toolbar [data-command]').forEach((btn) => {
    const check = ACTIVE_CHECKS[btn.dataset.command];
    btn.classList.toggle('is-active', !!(check && check()));
  });
}

// 정렬: 이미지가 선택돼 있으면 이미지 정렬(data-align), 아니면 문단/제목 정렬(text-align class).
function applyAlign(dir) {
  if (editor.isActive('image')) editor.chain().focus().updateAttributes('image', { align: dir }).run();
  else editor.chain().focus().setTextAlign(dir).run();
}

// 선택된 이미지에 표시 크기 class를 적용한다. 이미지를 먼저 클릭(선택)해야 한다.
function setImageSize(cls) {
  if (!editor.isActive('image')) { show('크기를 바꿀 이미지를 먼저 클릭해 주세요.', true); return; }
  editor.chain().focus().updateAttributes('image', { class: cls }).run();
}

async function deletePost() {
  if (!postId) return;
  if (!confirm('이 글을 삭제하면 복구할 수 없습니다. 삭제할까요?')) return;
  const res = await fetchAuth('/api/tutor/academy/blog-posts/' + postId, { method: 'DELETE' });
  if (res.ok) location.href = '/tutor/academy/posts'; else show('삭제에 실패했습니다.', true);
}
function autoSlug() { const slug = document.getElementById('slug'); if (slug.dataset.touched) return; slug.value = slugify(value('title')); }
document.getElementById('slug').addEventListener('input', (e) => e.currentTarget.dataset.touched = '1');
function slugify(v) { return String(v||'').trim().toLowerCase().replace(/[^a-z0-9가-힣]+/g, '-').replace(/^-+|-+$/g, ''); }
function pathPostId() { const last = location.pathname.split('/').filter(Boolean).pop(); return /^\d+$/.test(last) ? Number(last) : null; }
function value(id) { return document.getElementById(id).value.trim(); }
function set(id, v) { document.getElementById(id).value = v || ''; }
function show(msg, err) { statusMsg.textContent = msg; statusMsg.className = err ? 'error' : 'success'; }
