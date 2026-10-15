/* ============================================================
 * 숙제 및 피드백 (conversation 단위)
 *
 *  - 강사/Admin: PDF/이미지 숙제 부여(+ 버튼) + 목록/삭제
 *  - 학생: 부여된 숙제 다운로드 + 풀이 재업로드(/complete)
 *  데이터: /api/conversations/{id}/learning-materials (기존 LearningMaterial 플로우 재사용)
 * ============================================================ */

const conversationId = new URLSearchParams(location.search).get('id');
const isAdminView    = new URLSearchParams(location.search).get('asAdmin') === '1';

let canAssign = false;   // ADMIN(asAdmin=1) 또는 TUTOR
let isStudent = false;   // 학생 본인 — 제출 UI 노출
let _materials = [];     // 최근 로드한 숙제 목록 (피드백 모달 드롭다운용)

document.addEventListener('DOMContentLoaded', init);

async function init() {
  if (!conversationId) { location.href = '/stdt_dashboard'; return; }
  if (!await ensureCurrentUser()) { location.href = '/home'; return; }

  wireSidebarLinks();
  resolveAccess();
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      closeHomeworkPdf();
      closeAnswerLightbox();
    }
    if (_answerItems.length && !document.getElementById('answerLightbox').hidden) {
      if (e.key === 'ArrowLeft')  answerLightboxPrev();
      if (e.key === 'ArrowRight') answerLightboxNext();
    }
  });

  try {
    const detail = await fetchJsonAuth(`/api/conversations/${conversationId}`);
    renderSidebarStudent(detail);
  } catch (e) {
    console.error('homework sidebar load failed', e);
  }

  await loadAssignedMaterials();
}

function resolveAccess() {
  const userRaw = sessionStorage.getItem('currentUser');
  let role = null;
  try { role = userRaw ? JSON.parse(userRaw).role : null; } catch (_) {}
  canAssign = role === 'TUTOR' || (role === 'ADMIN' && isAdminView);
  isStudent = role === 'STUDENT';
  const btn = document.getElementById('btnAssignMaterial');
  if (btn) btn.hidden = !canAssign;
}

/* ── 목록 ─────────────────────────────────────────────── */

async function loadAssignedMaterials() {
  const listEl = document.getElementById('materialsList');
  try {
    const items = await fetchJsonAuth(`/api/conversations/${conversationId}/learning-materials`);
    _materials = items;
    if (!items.length) {
      listEl.innerHTML = canAssign
        ? `<div class="materials-empty">부여한 숙제가 아직 없어요. 우측 상단 [+ 숙제 부여]로 시작하세요.</div>`
        : `<div class="materials-empty">아직 부여된 숙제가 없어요.</div>`;
      renderFeedbackSection([]);
      return;
    }
    listEl.innerHTML = items.map(assignedMaterialHtml).join('');
    renderFeedbackSection(items);
  } catch (e) {
    console.error('learning-materials load failed', e);
    listEl.innerHTML = `<div class="materials-empty">숙제 목록을 불러오지 못했어요.</div>`;
  }
}

function assignedMaterialHtml(m) {
  const completed = m.status === 'COMPLETED';
  const statusChip = completed
    ? `<span class="materials-chip done">제출 완료</span>`
    : `<span class="materials-chip pending">풀이 대기</span>`;

  // reviewed 뱃지 — completed 여부와 무관, reviewed=true 일 때만 표시
  const reviewedBadge = m.reviewed
    ? `<span class="materials-chip reviewed" title="${escapeHtml(m.reviewedByName || '')}님이 ${formatAssignDate(m.reviewedAt)} 확인">확인 완료</span>`
    : '';

  // 강사/Admin: '확인 완료' 토글 버튼 (완료된 숙제에서만 의미 있음)
  const reviewedToggleHtml = canAssign && completed
    ? `<button class="materials-link materials-reviewed-btn" onclick="event.stopPropagation(); handleToggleReviewed(${m.materialId}, ${!m.reviewed})">${m.reviewed ? '확인 취소' : '확인/채점 완료'}</button>`
    : '';

  // 제출 답안 — 학교 자료 모음과 동일한 카드 그리드. 카드 클릭 → 묶음 뷰어(해당 인덱스).
  const answersHtml = completed && m.answers && m.answers.length
    ? `<div class="materials-grid hw-answer-grid">
         ${m.answers.map((a, i) => {
           const meta = answerFileMeta(a.originalFileName);
           const name = escapeHtml(a.originalFileName || ('답안 ' + a.orderNum));
           return `<div class="material-card" data-answer-id="${a.answerId}" data-name="${name}"
                        onclick="event.stopPropagation(); openAnswerLightbox(${m.materialId}, ${i})"
                        title="클릭하면 제출 답안을 화면에서 봅니다">
             <div class="material-card-header">
               <span class="material-card-icon">${meta.icon}</span>
               <span class="material-card-name" title="${name}">${name}</span>
             </div>
             <div class="material-card-meta"><span class="material-card-tag">${meta.tag}</span></div>
             <span class="material-card-status completed">제출</span>
           </div>`;
         }).join('')}
       </div>`
    : '';

  // 학생: 풀이 제출(재업로드) 컨트롤
  const submitHtml = isStudent
    ? `<label class="materials-submit" onclick="event.stopPropagation()">
         <input type="file" accept=".pdf,.jpg,.jpeg,.png,.webp" multiple
                onchange="handleSubmitHomework(${m.materialId}, this)" hidden>
         <span class="materials-link">${completed ? '재제출' : '풀이 제출'}</span>
       </label>`
    : '';
  const delHtml = canAssign
    ? `<button class="materials-del" onclick="event.stopPropagation(); handleDeleteMaterial(${m.materialId})">삭제</button>`
    : '';

  // 문제 PDF가 있는 숙제만 뷰어 클릭 / 다운로드 버튼을 노출. PDF 없이 부여된 숙제는 둘 다 숨긴다.
  const hasFile = m.hasProblemFile;
  const cardClick = hasFile ? `onclick="openHomeworkPdf(${m.materialId})"` : '';
  const cardTitle = hasFile ? '클릭하면 문제 PDF를 화면에서 봅니다' : '';
  const downloadHtml = hasFile
    ? `<a class="materials-link" href="/api/learning-materials/${m.materialId}/download" target="_blank" rel="noopener" onclick="event.stopPropagation()">다운로드</a>`
    : '';
  // PDF 없는 숙제는 설명(있으면)을 본문에 노출 — 부여 내용을 텍스트로 전달.
  const descHtml = (!hasFile && m.description)
    ? `<div class="materials-desc">${escapeHtml(m.description)}</div>`
    : '';

  return `
    <div class="materials-item" data-mid="${m.materialId}" ${cardClick} title="${cardTitle}">
      <div class="materials-row">
        <div class="materials-info">
          <div class="materials-name">${escapeHtml(m.title)} ${statusChip} ${reviewedBadge}</div>
          <div class="materials-meta">
            ${escapeHtml(m.studentName || '')} · ${escapeHtml(m.assignedByName || '')}님이 부여 · ${formatAssignDate(m.assignedAt)}
          </div>
          ${descHtml}
        </div>
        <div class="materials-actions">
          ${downloadHtml}
          ${reviewedToggleHtml}
          ${submitHtml}
          ${delHtml}
        </div>
      </div>
      ${answersHtml}
    </div>
  `;
}

/* ── 강사 피드백 (독립 섹션) ──────────────────────────── */

/** 피드백이 있는 숙제별로 행을 그린다. 추가/삭제는 모달·카드 버튼으로. */
function renderFeedbackSection(items) {
  const sec = document.getElementById('feedbackList');
  if (!sec) return;
  const btn = document.getElementById('btnAddFeedback');
  if (btn) btn.hidden = !(canAssign && items.length);  // 강사 + 숙제 존재 시에만 추가 버튼
  const targets = items.filter(m => (m.feedbacks || []).length);
  if (!targets.length) {
    sec.innerHTML = `<div class="materials-empty">${canAssign
      ? '아직 올린 피드백이 없어요. 우측 상단 [+ 피드백 추가]로 채점 결과를 올리세요.'
      : '아직 강사 피드백이 없어요.'}</div>`;
    return;
  }
  sec.innerHTML = targets.map(feedbackRowHtml).join('');
}

function feedbackRowHtml(m) {
  const feedbacks = m.feedbacks || [];
  let fileCardIndex = 0;
  const cards = feedbacks.map(f => {
    const hasFile = !!f.sourceId;
    const hasText = !!(f.feedbackText || '').trim();
    const fileIndex = hasFile ? fileCardIndex++ : -1;
    const name = escapeHtml(f.originalFileName || (hasText ? '텍스트 피드백' : ('피드백 ' + f.orderNum)));
    const fbDel = canAssign
      ? `<button class="material-card-delete" title="피드백 삭제" onclick="event.stopPropagation(); handleDeleteFeedback(${m.materialId}, ${f.feedbackId})">✕</button>`
      : '';
    const fileAttrs = hasFile
      ? `data-file-url="/api/learning-materials/${m.materialId}/feedback/${f.feedbackId}/file" onclick="event.stopPropagation(); openFeedbackLightbox(${m.materialId}, ${fileIndex})" title="클릭하면 피드백을 화면에서 봅니다"`
      : `onclick="event.stopPropagation()" title="첨부 파일 없이 등록된 피드백입니다"`;
    return `<div class="material-card" data-feedback-id="${f.feedbackId}" data-has-file="${hasFile}" data-name="${name}" ${fileAttrs}>
        <div class="material-card-header">
          <span class="material-card-icon">${hasFile ? answerFileMeta(f.originalFileName).icon : '✅'}</span>
          <span class="material-card-name" title="${name}">${name}</span>
          ${fbDel}
        </div>
        ${hasText ? `<div class="material-card-feedback-text">${escapeHtml(f.feedbackText)}</div>` : ''}
        <div class="material-card-meta"><span class="material-card-tag">${hasFile ? answerFileMeta(f.originalFileName).tag : '첨부 없음'}</span></div>
      </div>`;
  }).join('');

  return `
    <div class="materials-item" data-fb-mid="${m.materialId}">
      <div class="materials-row">
        <div class="materials-info">
          <div class="materials-name">${escapeHtml(m.title)}</div>
          <div class="materials-meta">${escapeHtml(m.studentName || '')} 제출물에 대한 강사 피드백</div>
        </div>
      </div>
      <div class="materials-grid hw-answer-grid">${cards}</div>
    </div>
  `;
}

/* ── 문제 PDF inline 뷰어 ─────────────────────────────── */

/** 카드 클릭 시 호출. 문제 PDF를 모달 iframe(inline /view)으로 띄운다. */
function openHomeworkPdf(materialId) {
  const frame = document.getElementById('homeworkPdfFrame');
  const modal = document.getElementById('homeworkPdfModal');
  if (!frame || !modal) return;
  frame.src = `/api/learning-materials/${materialId}/view`;
  modal.hidden = false;
}

/** 모달 닫기 + iframe.src 리셋(재오픈 시 stale 방지). */
function closeHomeworkPdf() {
  const frame = document.getElementById('homeworkPdfFrame');
  const modal = document.getElementById('homeworkPdfModal');
  if (modal) modal.hidden = true;
  if (frame) frame.src = '';
}

/** 배경(backdrop) 클릭 시에만 닫기 — 내부 box 클릭은 무시. */
function onHomeworkPdfBackdrop(event) {
  if (event.target.id === 'homeworkPdfModal') closeHomeworkPdf();
}

/* ── 제출 답안 / 피드백 공용 뷰어 ─────────────────────── */

/** 답안·피드백 파일들을 iframe 모달로 순서대로 열 수 있도록 한다.
 *  이미지/PDF 모두 Content-Type inline 서빙이므로 iframe에서 그대로 표시된다. */
let _answerItems = [];   // [{fileUrl, label}]
let _answerIndex = 0;

/** 파일명 확장자로 카드 아이콘·타입 태그를 추론한다(답안 DTO에 fileType 없음). */
function answerFileMeta(name) {
  const ext = (name || '').split('.').pop().toLowerCase();
  const isPdf = ext === 'pdf';
  return { icon: isPdf ? '📄' : '🖼️', tag: isPdf ? 'PDF' : (ext ? ext.toUpperCase() : '이미지') };
}

/** data-file-url 속성을 가진 카드 그룹에서 뷰어를 연다.
 *  답안 카드(data-answer-id), 피드백 카드(data-feedback-id) 모두 지원. */
function openAnswerLightbox(materialId, startIndex = 0) {
  const item = document.querySelector(`.materials-item[data-mid="${materialId}"]`);
  if (!item) return;
  // 답안 카드에서 fileUrl 수집 (data-file-url 속성 사용)
  const cards = item.querySelectorAll('.material-card[data-answer-id]');
  _answerItems = Array.from(cards).map((c) => ({
    fileUrl: c.dataset.fileUrl || `/api/learning-materials/${materialId}/answers/${c.dataset.answerId}/file`,
    label: c.dataset.name || '',
  })).filter(it => it.fileUrl);
  if (!_answerItems.length) return;
  _answerIndex = Math.min(Math.max(startIndex, 0), _answerItems.length - 1);
  _renderAnswerLightbox();
  document.getElementById('answerLightbox').hidden = false;
}

/** fileUrl 목록을 직접 받아 뷰어를 여는 범용 진입점 (피드백 카드 등에서 사용). */
function openFileLightbox(items, startIndex = 0) {
  if (!items || !items.length) return;
  _answerItems = items;  // [{fileUrl, label}]
  _answerIndex = Math.min(Math.max(startIndex, 0), _answerItems.length - 1);
  _renderAnswerLightbox();
  document.getElementById('answerLightbox').hidden = false;
}

/** 피드백 카드(data-feedback-id) 그룹에서 뷰어를 연다. */
function openFeedbackLightbox(materialId, startIndex = 0) {
  const item = document.querySelector(`.materials-item[data-fb-mid="${materialId}"]`);
  if (!item) return;
  const cards = item.querySelectorAll('.material-card[data-feedback-id][data-has-file="true"]');
  const items = Array.from(cards)
    .map(c => ({ fileUrl: c.dataset.fileUrl, label: c.dataset.name || '' }))
    .filter(it => it.fileUrl);
  openFileLightbox(items, startIndex);
}

function _renderAnswerLightbox() {
  const item = _answerItems[_answerIndex];
  if (!item) return;
  document.getElementById('answerLightboxFrame').src = item.fileUrl;
  document.getElementById('answerLightboxCounter').textContent =
    `${_answerIndex + 1} / ${_answerItems.length}`;
  document.getElementById('answerLightboxDownload').href = item.fileUrl;
  document.getElementById('answerLightboxPrev').disabled = (_answerIndex === 0);
  document.getElementById('answerLightboxNext').disabled = (_answerIndex === _answerItems.length - 1);
}

function closeAnswerLightbox() {
  document.getElementById('answerLightbox').hidden = true;
  document.getElementById('answerLightboxFrame').src = '';
  _answerItems = [];
}

function answerLightboxPrev() {
  if (_answerIndex > 0) { _answerIndex--; _renderAnswerLightbox(); }
}

function answerLightboxNext() {
  if (_answerIndex < _answerItems.length - 1) { _answerIndex++; _renderAnswerLightbox(); }
}

function onAnswerLightboxBackdrop(event) {
  if (event.target.id === 'answerLightbox') closeAnswerLightbox();
}

/* ── 학생 제출 ─────────────────────────────────────────── */

async function handleSubmitHomework(materialId, input) {
  const files = input.files;
  if (!files || !files.length) return;

  // 재제출 경고 — 카드의 현재 상태로 판단
  const card = document.querySelector(`.materials-item[data-mid="${materialId}"]`);
  const alreadyCompleted = card && card.querySelector('.materials-chip.done');
  if (alreadyCompleted) {
    if (!confirm('재제출시 기존에 업로드한 자료들이 사라집니다.')) {
      input.value = '';
      return;
    }
  }

  try {
    const form = new FormData();
    for (const f of files) form.append('files', f);
    const res = await fetchAuth(`/api/learning-materials/${materialId}/complete`, {
      method: 'POST',
      body: form
    });
    if (!res.ok) throw new Error(await res.text().catch(() => ''));
    await loadAssignedMaterials();
  } catch (e) {
    alert('제출 실패: ' + (e.message || ''));
  } finally {
    input.value = '';
  }
}

/* ── 강사 피드백 업로드(모달)/삭제 ─────────────────────── */

/** 피드백 모달 열기 — 숙제 드롭다운을 전체 숙제로 채운다(미제출 숙제도 선택 가능). */
function openFeedbackModal() {
  const sel = document.getElementById('feedbackMaterialSelect');
  if (!_materials.length) { alert('먼저 숙제를 부여하세요.'); return; }
  sel.innerHTML = _materials.map(m => {
    const sub = m.status === 'COMPLETED' ? '제출 완료' : '풀이 대기';
    return `<option value="${m.materialId}">${escapeHtml(m.title)} (${sub})</option>`;
  }).join('');
  document.getElementById('feedbackText').value = '';
  document.getElementById('feedbackFile').value = '';
  document.getElementById('feedbackFileDisplay').innerHTML = '';
  syncFeedbackSubmitState();
  document.getElementById('feedbackModal').hidden = false;
}
function closeFeedbackModal() {
  document.getElementById('feedbackModal').hidden = true;
}
function syncFeedbackSubmitState() {
  const text = document.getElementById('feedbackText')?.value.trim() || '';
  const files = Array.from(document.getElementById('feedbackFile')?.files || []);
  document.getElementById('feedbackSubmitBtn').disabled = !text && files.length === 0;
}
function onFeedbackFileChange() {
  const fileInput = document.getElementById('feedbackFile');
  const display = document.getElementById('feedbackFileDisplay');
  const files = Array.from(fileInput.files || []);
  if (!files.length) {
    display.innerHTML = '<span class="assign-file-row">파일 없이 설명만으로도 등록할 수 있습니다.</span>';
    syncFeedbackSubmitState();
    return;
  }
  const invalid = files.find(f => !isHomeworkUploadFile(f));
  if (invalid) {
    display.innerHTML = `<span class="assign-file-err">PDF / JPG / PNG / WebP 파일만 업로드할 수 있습니다.</span>`;
    fileInput.value = '';
    syncFeedbackSubmitState();
    return;
  }
  display.innerHTML = files.map(f => `<span class="assign-file-row">${fileIcon(f.name)} ${escapeHtml(f.name)} (${formatBytes(f.size)})</span>`).join('');
  syncFeedbackSubmitState();
}
function isHomeworkUploadFile(file) {
  const name = (file?.name || '').toLowerCase();
  return /\.(pdf|jpe?g|png|webp)$/.test(name);
}

function fileIcon(filename) {
  const meta = answerFileMeta(filename);
  return meta.tag === 'PDF' ? '📄' : '🖼️';
}

async function submitFeedback() {
  const materialId = document.getElementById('feedbackMaterialSelect').value;
  const feedbackText = document.getElementById('feedbackText').value.trim();
  const fileInput = document.getElementById('feedbackFile');
  const files = Array.from(fileInput.files || []);
  if (!materialId) { alert('숙제를 선택하세요.'); return; }
  if (!feedbackText && files.length === 0) { alert('피드백 설명 또는 자료 파일 중 하나는 입력해주세요.'); return; }
  const submitBtn = document.getElementById('feedbackSubmitBtn');
  submitBtn.disabled = true;
  submitBtn.textContent = '등록 중...';
  try {
    const form = new FormData();
    if (feedbackText) form.append('feedbackText', feedbackText);
    for (const f of files) form.append('files', f);
    const res = await fetchAuth(`/api/learning-materials/${materialId}/feedback`, {
      method: 'POST',
      body: form
    });
    if (!res.ok) throw new Error(await res.text().catch(() => '') || '업로드 실패');
    closeFeedbackModal();
    await loadAssignedMaterials();
  } catch (e) {
    alert('피드백 업로드 실패: ' + (e.message || ''));
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = '등록';
  }
}

async function handleDeleteFeedback(materialId, feedbackId) {
  if (!confirm('이 피드백을 삭제할까요?')) return;
  try {
    const res = await fetchAuth(`/api/learning-materials/${materialId}/feedback/${feedbackId}`, { method: 'DELETE' });
    if (!res.ok && res.status !== 204) throw new Error(await res.text().catch(() => ''));
    await loadAssignedMaterials();
  } catch (e) {
    alert('삭제 실패: ' + (e.message || ''));
  }
}

/* ── 강사 reviewed 토글 ────────────────────────────────── */

async function handleToggleReviewed(materialId, reviewed) {
  try {
    const res = await fetchAuth(`/api/learning-materials/${materialId}/reviewed`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reviewed }),
    });
    if (!res.ok) throw new Error(await res.text().catch(() => ''));
    await loadAssignedMaterials();
  } catch (e) {
    alert('상태 변경 실패: ' + (e.message || ''));
  }
}

/* ── 강사 삭제 ─────────────────────────────────────────── */

async function handleDeleteMaterial(materialId) {
  if (!confirm('이 숙제를 영구 삭제할까요? 학생 풀이 결과도 함께 삭제됩니다.')) return;
  try {
    const res = await fetchAuth(`/api/learning-materials/${materialId}`, { method: 'DELETE' });
    if (!res.ok && res.status !== 204) throw new Error(await res.text().catch(() => ''));
    await loadAssignedMaterials();
  } catch (e) {
    alert('삭제 실패: ' + (e.message || ''));
  }
}

/* ── 숙제 부여 모달 (강사/Admin) ───────────────────────── */

function openAssignModal() {
  document.getElementById('assignTitle').value = '';
  document.getElementById('assignDescription').value = '';
  document.getElementById('assignFile').value = '';
  document.getElementById('assignFileDisplay').innerHTML = '';
  // 파일은 선택사항 — 제목 입력 여부로 부여 버튼 활성화.
  syncAssignSubmitState();
  document.getElementById('assignModal').hidden = false;
}

/** 제목이 비어있지 않으면 부여 가능 (파일은 선택). 잘못된 파일 선택 시엔 onAssignFileChange가 별도로 막는다. */
function syncAssignSubmitState() {
  const title = document.getElementById('assignTitle').value.trim();
  document.getElementById('assignSubmitBtn').disabled = !title;
}

function closeAssignModal() {
  document.getElementById('assignModal').hidden = true;
}

function onAssignFileChange() {
  const fileInput = document.getElementById('assignFile');
  const display = document.getElementById('assignFileDisplay');
  const file = fileInput.files && fileInput.files[0];
  // 파일은 선택사항 — 선택 안 하면 제목 유무만으로 부여 가능.
  if (!file) { display.innerHTML = ''; syncAssignSubmitState(); return; }
  if (!isHomeworkUploadFile(file)) {
    display.innerHTML = `<span class="assign-file-err">PDF / JPG / PNG / WebP 파일만 업로드할 수 있습니다.</span>`;
    document.getElementById('assignSubmitBtn').disabled = true;
    fileInput.value = '';
    return;
  }
  display.innerHTML = `<span class="assign-file-row">${fileIcon(file.name)} ${escapeHtml(file.name)} (${formatBytes(file.size)})</span>`;
  syncAssignSubmitState();
}

async function submitAssign() {
  const title = document.getElementById('assignTitle').value.trim();
  const description = document.getElementById('assignDescription').value.trim();
  const fileInput = document.getElementById('assignFile');
  const file = fileInput.files && fileInput.files[0];
  if (!title) { alert('제목을 입력하세요.'); return; }
  // 파일은 선택 — 없으면 title+description만으로 부여한다.

  const submitBtn = document.getElementById('assignSubmitBtn');
  submitBtn.disabled = true;
  submitBtn.textContent = '부여 중...';

  try {
    const form = new FormData();
    if (file) form.append('file', file);
    form.append('title', title);
    if (description) form.append('description', description);
    const res = await fetchAuth(`/api/conversations/${conversationId}/learning-materials`, {
      method: 'POST',
      body: form
    });
    if (!res.ok) throw new Error(await res.text().catch(() => '') || '부여 실패');
    closeAssignModal();
    await loadAssignedMaterials();
  } catch (e) {
    alert('부여 실패: ' + (e.message || ''));
    submitBtn.disabled = false;
    submitBtn.textContent = '부여하기';
  }
}

/* ── 사이드바 / 유틸 ──────────────────────────────────── */

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
  if (!box || !student) { if (box) box.innerHTML = ''; return; }
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

function formatAssignDate(iso) {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric' });
  } catch { return ''; }
}

function formatBytes(bytes) {
  if (!bytes) return '0 B';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
