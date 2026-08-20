/* ============================================================
 * 오답노트 (conversation 단위)
 *
 * 흐름:
 *  1. URL ?id={conversationId}로 대화 식별 + asAdmin=1로 임퍼소네이트 모드 판별
 *  2. 사이드바 라우팅 + 학생 정보 채움
 *  3. 수동 오답노트 업로드/목록/삭제
 *  4. 기존 자동 오답은 하단 접힘 섹션에서 보조로 유지
 * ============================================================ */

const conversationId = new URLSearchParams(location.search).get('id');
const isAdminView    = new URLSearchParams(location.search).get('asAdmin') === '1';

let cachedExams = [];          // API 응답 그대로 (필터 적용 전 원본)
let activeCategory = null;     // null = 전체, 'grammar' 등 = 해당 카테고리만
let manualEntries = [];
// 숙제 부여/제출은 /conversation/homework 로 이관됨 (이 페이지는 오답노트만 담당)

document.addEventListener('DOMContentLoaded', init);

async function init() {
  if (!conversationId) {
    location.href = '/stdt_dashboard';
    return;
  }
  if (!sessionStorage.getItem('currentUser')) {
    location.href = '/home';
    return;
  }

  wireSidebarLinks();
  wireWrongNoteForm();

  try {
    const detail = await fetchJsonAuth(`/api/conversations/${conversationId}`);
    renderSidebarStudent(detail);
  } catch (e) {
    console.error('review sidebar load failed', e);
  }

  await loadManualEntries();

  try {
    const data = await fetchJsonAuth(`/api/conversations/${conversationId}/wrong-answers`);
    cachedExams = data.exams || [];
    renderCategoryFilter(cachedExams);
    renderExamGroups();
  } catch (e) {
    console.error('wrong-answers load failed', e);
    showEmpty('오답노트를 불러오지 못했어요.');
  }
}

function wireWrongNoteForm() {
  const form = document.getElementById('wrongNoteForm');
  if (!form) return;
  form.addEventListener('submit', handleWrongNoteUpload);
}

async function loadManualEntries() {
  const list = document.getElementById('manualNoteList');
  const empty = document.getElementById('manualNoteEmpty');
  try {
    manualEntries = await fetchJsonAuth(`/api/conversations/${conversationId}/wrong-note-entries`);
    renderManualEntries();
  } catch (e) {
    console.error('manual wrong note load failed', e);
    if (list) list.innerHTML = '';
    if (empty) {
      empty.style.display = '';
      empty.textContent = '업로드된 오답을 불러오지 못했어요.';
    }
  }
}

async function handleWrongNoteUpload(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const submit = form.querySelector('button[type="submit"]');
  const files = Array.from(form.elements.files?.files || []);
  if (files.length === 0) {
    alert('업로드할 오답 파일을 선택해주세요.');
    return;
  }
  const body = new FormData(form);
  submit.disabled = true;
  submit.textContent = '업로드 중...';
  try {
    const res = await fetchAuth(`/api/conversations/${conversationId}/wrong-note-entries`, {
      method: 'POST',
      body,
    });
    if (!res.ok) throw new Error(`upload failed: ${res.status}`);
    form.reset();
    await loadManualEntries();
  } catch (e) {
    console.error('manual wrong note upload failed', e);
    alert('오답 업로드에 실패했어요. 이미지 또는 PDF 파일인지 확인해주세요.');
  } finally {
    submit.disabled = false;
    submit.textContent = '오답 올리기';
  }
}

function renderManualEntries() {
  const list = document.getElementById('manualNoteList');
  const empty = document.getElementById('manualNoteEmpty');
  const count = document.getElementById('manualNoteCount');
  if (count) count.textContent = `${manualEntries.length}개`;
  if (!list || !empty) return;
  if (manualEntries.length === 0) {
    list.innerHTML = '';
    empty.style.display = '';
    empty.textContent = '아직 업로드된 오답이 없어요. 틀린 문제를 사진으로 찍어 올려보세요.';
    return;
  }
  empty.style.display = 'none';
  list.innerHTML = manualEntries.map(renderManualEntry).join('');
}

function renderManualEntry(entry) {
  const files = Array.isArray(entry.files) && entry.files.length > 0
    ? entry.files
    : [{
        sourceId: entry.sourceId,
        originalFileName: entry.originalFileName,
        fileType: entry.fileType,
        fileSize: entry.fileSize,
        orderNum: 1,
        pageCount: entry.pageCount,
      }];
  const fileCards = files.map(renderManualEntryFile).join('');
  return `
    <article class="manual-note-card">
      <div class="manual-note-card-main">
        <div class="manual-note-title">${escapeHtml(entry.title)}</div>
        ${entry.memo ? `<div class="manual-note-memo">${escapeHtml(entry.memo)}</div>` : ''}
        <div class="manual-note-files-grid">${fileCards}</div>
      </div>
      <div class="manual-note-side">
        <div class="manual-note-uploader">${escapeHtml(entry.uploadedByName || '')} · ${escapeHtml(roleLabel(entry.uploadedByRole))}</div>
        <div class="manual-note-date">${formatDate(entry.createdAt)}</div>
        <button class="note-delete-btn" onclick="deleteManualEntry(${entry.entryId})">삭제</button>
      </div>
    </article>
  `;
}

function renderManualEntryFile(file) {
  const sourceId = Number(file.sourceId);
  const pages = Math.max(1, Number(file.pageCount || 1));
  const pageLinks = Array.from({ length: pages }, (_, idx) => {
    const page = idx + 1;
    return `<a class="note-file-link" href="/api/sources/${sourceId}/file?page=${page}" target="_blank" rel="noopener">보기${pages > 1 ? ` ${page}` : ''}</a>`;
  }).join('');
  const fileType = escapeHtml(file.fileType || 'FILE');
  return `
    <div class="manual-note-file-card">
      <div class="manual-note-file-name" title="${escapeHtml(file.originalFileName || '첨부 파일')}">${escapeHtml(file.originalFileName || '첨부 파일')}</div>
      <div class="manual-note-file-actions">
        <span class="file-type">${fileType}</span>
        ${pageLinks}
      </div>
    </div>
  `;
}

async function deleteManualEntry(entryId) {
  if (!confirm('이 오답노트 항목을 삭제할까요?')) return;
  try {
    const res = await fetchAuth(`/api/conversations/${conversationId}/wrong-note-entries/${entryId}`, {
      method: 'DELETE',
    });
    if (!res.ok) throw new Error(`delete failed: ${res.status}`);
    await loadManualEntries();
  } catch (e) {
    console.error('manual wrong note delete failed', e);
    alert('오답노트 삭제에 실패했어요.');
  }
}

function wireSidebarLinks() {
  const adminSuffix = isAdminView ? '&asAdmin=1' : '';
  const routes = {
    dashboard:         `/conversation/dashboard?id=${conversationId}${adminSuffix}`,
    'exam-materials':  `/conversation/exam-materials?id=${conversationId}${adminSuffix}`,
    'study-materials': `/conversation/study-materials?id=${conversationId}${adminSuffix}`,
    materials:         `/conversation/materials?id=${conversationId}${adminSuffix}`,
    'tutor-materials': `/conversation/tutor-materials?id=${conversationId}${adminSuffix}`,
    homework:          `/conversation/homework?id=${conversationId}${adminSuffix}`,
    review:            `/conversation/review?id=${conversationId}${adminSuffix}`,
    chat:              `/conversation/chat?id=${conversationId}${adminSuffix}`,
    report:            `/conversation/report?id=${conversationId}${adminSuffix}`,
  };
  document.querySelectorAll('.sidebar-link').forEach(a => {
    const key = a.dataset.menu;
    if (routes[key]) a.href = routes[key];
  });
}

function renderSidebarStudent(detail) {
  const student = detail.student;
  const box = document.getElementById('sidebarStudent');
  if (!student) { box.innerHTML = ''; return; }
  box.innerHTML = `
    <div class="sidebar-student-name">
      ${escapeHtml(student.name || '')}
      <span class="sidebar-student-grade">· ${student.grade ? `Grade ${student.grade}` : ''}</span>
    </div>
    <div class="sidebar-student-meta">
      ${escapeHtml(student.schoolName || '')}<br>
      ${escapeHtml(detail.title || '')}
    </div>
  `;
}

/** 응답 전체에서 등장한 카테고리 목록을 추려서 필터 칩 그림. */
function renderCategoryFilter(exams) {
  const wrap = document.getElementById('categoryFilter');
  const seen = new Map();   // en -> ko
  exams.forEach(g => g.questions.forEach(q => {
    const en = q.categoryNameEn || 'other';
    const ko = q.categoryNameKo || '기타';
    if (!seen.has(en)) seen.set(en, ko);
  }));
  if (seen.size === 0) {
    wrap.innerHTML = '';
    return;
  }

  const chips = [`<span class="chip active" data-cat="">전체</span>`];
  seen.forEach((ko, en) => {
    chips.push(`<span class="chip" data-cat="${escapeHtml(en)}">${escapeHtml(ko)}</span>`);
  });
  wrap.innerHTML = chips.join('');

  wrap.querySelectorAll('.chip').forEach(chip => {
    chip.addEventListener('click', () => {
      wrap.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      activeCategory = chip.dataset.cat || null;
      renderExamGroups();
    });
  });
}

function renderExamGroups() {
  const root = document.getElementById('examGroups');
  const empty = document.getElementById('reviewEmpty');

  // 카테고리 필터 적용 — 비어버리는 그룹은 통째로 제외
  const groups = cachedExams
    .map(g => ({
      ...g,
      questions: activeCategory
        ? g.questions.filter(q => (q.categoryNameEn || 'other') === activeCategory)
        : g.questions,
    }))
    .filter(g => g.questions.length > 0);

  if (groups.length === 0) {
    root.innerHTML = '';
    empty.style.display = '';
    empty.textContent = cachedExams.length === 0
      ? '아직 오답이 없어요. 시험지를 첨부하고 채점하면 여기에 누적됩니다.'
      : '선택한 카테고리에 해당하는 오답이 없어요.';
    return;
  }

  empty.style.display = 'none';
  root.innerHTML = groups.map(renderExamGroup).join('');
}

function renderExamGroup(g) {
  const cards = g.questions.map(renderWrongCard).join('');
  return `
    <article class="exam-group">
      <header class="exam-group-header">
        <h2 class="exam-group-title">${escapeHtml(g.examDisplayName)}</h2>
        <div class="exam-group-count">
          ${g.wrongCount}문항 오답
          ${g.totalCount > 0 ? `<span class="total"> · 전체 ${g.totalCount}문항</span>` : ''}
        </div>
      </header>
      <div class="exam-group-body">
        ${cards}
      </div>
    </article>
  `;
}

function renderWrongCard(q) {
  const label = q.questionLabel || (q.orderNum != null ? String(q.orderNum) : '?');
  const choices = parseChoices(q.choices);
  const isMc = q.questionType === 'MULTIPLE_CHOICE' && choices.length > 0;
  const catEn = q.categoryNameEn || 'other';
  const catKo = q.categoryNameKo || '기타';

  let choicesHtml = '';
  if (isMc) {
    choicesHtml = `
      <ol class="wrong-card-choices">
        ${choices.map((c, idx) => {
          const num = String(idx + 1);
          const isCorrect = sameAnswer(q.correctAnswer, num) || sameAnswer(q.correctAnswer, c);
          const isStudent = sameAnswer(q.studentAnswer, num) || sameAnswer(q.studentAnswer, c);
          let cls = '';
          if (isCorrect) cls = 'correct';
          else if (isStudent) cls = 'student';
          return `<li class="${cls}">${escapeHtml(String(c))}</li>`;
        }).join('')}
      </ol>
    `;
  }

  const answersBlock = isMc ? '' : `
    <div class="wrong-card-answers">
      <div class="row"><span class="label">내 답</span><span class="student-answer">${escapeHtml(q.studentAnswer || '(미작성)')}</span></div>
      <div class="row"><span class="label">정답</span><span class="correct-answer">${escapeHtml(q.correctAnswer || '')}</span></div>
    </div>
  `;

  const expl = q.explanation
    ? `<div class="wrong-card-explanation">${escapeHtml(q.explanation)}</div>`
    : '';

  return `
    <div class="wrong-card">
      <div class="wrong-card-no">${escapeHtml(label)}</div>
      <div class="wrong-card-body">
        <div class="wrong-card-text">${escapeHtml(q.questionText)}</div>
        ${choicesHtml}
        ${answersBlock}
        ${expl}
      </div>
      <div class="wrong-card-meta">
        <span class="wrong-card-category ${catEn === 'other' ? 'other' : ''}">${escapeHtml(catKo)}</span>
        ${q.point != null && q.point > 0 ? `<span class="wrong-card-point">${q.point}점</span>` : ''}
      </div>
    </div>
  `;
}

/** choices는 DB에 JSON 문자열로 저장돼 있어 파싱 필요. 비정상 입력은 빈 배열로. */
function parseChoices(raw) {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/** "1" vs "1." vs " 1 " 같은 표기 차이를 흡수해 정답·내답 매칭한다. */
function sameAnswer(a, b) {
  if (a == null || b == null) return false;
  const na = String(a).trim().replace(/[.\s)]/g, '');
  const nb = String(b).trim().replace(/[.\s)]/g, '');
  return na !== '' && na === nb;
}

function showEmpty(msg) {
  const root = document.getElementById('examGroups');
  const empty = document.getElementById('reviewEmpty');
  root.innerHTML = '';
  empty.style.display = '';
  empty.textContent = msg;
}

function roleLabel(role) {
  if (role === 'TUTOR') return '강사';
  if (role === 'ADMIN') return '관리자';
  if (role === 'STUDENT') return '학생';
  return role || '';
}

function formatDate(value) {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getFullYear()}. ${d.getMonth() + 1}. ${d.getDate()}.`;
}

function escapeHtml(s) {
  if (s == null) return '';
  return String(s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}
