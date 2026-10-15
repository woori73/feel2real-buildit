/* ============================================================
 * Conversation Upload Module
 *
 * 대화 첨부 파일 업로드를 위한 공통 모달/HEIC/제출 로직.
 * conversation_chat.html, conversation_dashboard.html 등에서
 * 공통으로 사용한다. 모달 마크업과 토스트를 직접 DOM에 주입한다.
 *
 * 사용 예:
 *   ConversationUpload.init({
 *     conversationId,
 *     userId,
 *     studentDefaults: { schoolName, grade },
 *     asAdmin,
 *     requireExamMeta,
 *     examMetaDefaults: { examType, examYear, examSemester },
 *     onComplete: async (responses) => { ... }
 *   });
 *   버튼 클릭 → ConversationUpload.open();
 * ============================================================ */

(function () {
  const MAX_FILES = 20;
  const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
  const HEIC_TYPES     = ['image/heic', 'image/heif'];

  let conversationId   = null;
  let userId           = null;
  let studentDefaults  = { schoolName: '', grade: '' };
  let asAdmin          = false;
  let onComplete       = function () {};
  let uploadUrl        = null;
  let requireExamMeta  = false;
  let examMetaDefaults = { examType: '', examYear: '', examSemester: '' };

  let pickedFiles      = [];
  let modalEl          = null;
  let toastEl          = null;
  let toastTimer       = null;
  let initialized      = false;

  /** 초기 설정 + 마크업 주입. 페이지마다 1회만 호출하면 충분하다. */
  function init(config) {
    conversationId  = config.conversationId;
    userId          = config.userId;
    studentDefaults = config.studentDefaults || { schoolName: '', grade: '' };
    asAdmin         = !!config.asAdmin;
    onComplete      = config.onComplete || function () {};
    uploadUrl       = config.uploadUrl || null;
    requireExamMeta = !!config.requireExamMeta;
    examMetaDefaults = config.examMetaDefaults || { examType: '', examYear: '', examSemester: '' };

    if (initialized) return;
    initialized = true;

    injectModalMarkup();
    injectToastMarkup();
    bindEvents();
  }

  /** 새 설정으로 갈아끼우고 싶을 때 (예: 컨텍스트 전환). */
  function update(config) {
    if (config.conversationId !== undefined)  conversationId  = config.conversationId;
    if (config.userId !== undefined)          userId          = config.userId;
    if (config.studentDefaults !== undefined) studentDefaults = config.studentDefaults;
    if (config.asAdmin !== undefined)         asAdmin         = !!config.asAdmin;
    if (config.onComplete !== undefined)      onComplete      = config.onComplete;
    if (config.uploadUrl !== undefined)       uploadUrl       = config.uploadUrl;
    if (config.requireExamMeta !== undefined) requireExamMeta = !!config.requireExamMeta;
    if (config.examMetaDefaults !== undefined) examMetaDefaults = config.examMetaDefaults;
  }

  function injectModalMarkup() {
    if (document.getElementById('uploadModal')) {
      modalEl = document.getElementById('uploadModal');
      return;
    }
    const wrapper = document.createElement('div');
    wrapper.innerHTML = `
      <div class="modal-backdrop" id="uploadModal" style="display:none">
        <div class="modal">
          <div class="modal-header">
            <h3>파일 추가</h3>
            <button class="modal-close" type="button" data-action="close" aria-label="닫기">✕</button>
          </div>

          <div class="modal-body">
            <p class="modal-notice">📋 한 번에 <strong>한 시험</strong>의 자료를 업로드해 주세요. 이미지·PDF 모두 가능, 최대 20개까지.</p>

            <div class="form-row">
              <span class="form-label">자료 타입</span>
              <div class="radio-group">
                <label><input type="radio" name="contentKind" value="GENERAL" checked> 일반 자료</label>
                <label><input type="radio" name="contentKind" value="EXAM_BLANK"> 시험지</label>
              </div>
            </div>

            <div id="examFields" class="exam-fields" style="display:none">
              <div class="form-row">
                <label class="form-label" for="schoolName">학교명<span class="required-mark"> *</span></label>
                <input type="text" id="schoolName" placeholder="예: 00중학교">
              </div>
              <div class="form-row">
                <label class="form-label" for="grade">학년<span class="required-mark"> *</span></label>
                <input type="number" id="grade" min="0" max="6" placeholder="0~6">
              </div>
              <div class="form-row">
                <label class="form-label" for="examType">시험 종류</label>
                <select id="examType">
                  <option value="">선택 안 함 (서버 기본값: 기타)</option>
                  <option value="MIDTERM">중간고사</option>
                  <option value="FINAL">기말고사</option>
                  <option value="MOCK">모의고사</option>
                  <option value="OTHER">기타</option>
                </select>
              </div>
              <div class="form-row">
                <label class="form-label" for="examYear">연도</label>
                <input type="number" id="examYear" min="2000" max="2099" placeholder="예: 2026">
              </div>
              <div class="form-row">
                <label class="form-label" for="examSemester">학기</label>
                <select id="examSemester">
                  <option value="">선택 안 함</option>
                  <option value="1">1학기</option>
                  <option value="2">2학기</option>
                </select>
              </div>
            </div>

            <div class="form-row">
              <span class="form-label">파일</span>
              <div class="file-pick-row">
                <button type="button" class="btn-secondary" data-action="pick">파일 선택</button>
                <span id="pickedFileName" class="picked-file-name">선택된 파일 없음</span>
              </div>
            </div>
            <p class="modal-hint" id="uploadModalHint">여러 장의 사진과 PDF를 함께 선택할 수 있어요.</p>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn-secondary" data-action="close">취소</button>
            <button type="button" class="btn-primary" id="btnUpload" data-action="submit" disabled>업로드</button>
          </div>
        </div>
      </div>

      <input type="file" id="fileInput" multiple
             accept="image/jpeg,image/png,image/webp,image/heic,image/heif,application/pdf"
             style="display:none">
    `;
    while (wrapper.firstChild) {
      document.body.appendChild(wrapper.firstChild);
    }
    modalEl = document.getElementById('uploadModal');
  }

  function injectToastMarkup() {
    if (document.getElementById('uploadToast')) {
      toastEl = document.getElementById('uploadToast');
      return;
    }
    toastEl = document.createElement('div');
    toastEl.id = 'uploadToast';
    toastEl.className = 'upload-toast';
    toastEl.style.display = 'none';
    document.body.appendChild(toastEl);
  }

  function bindEvents() {
    modalEl.addEventListener('click', (e) => {
      const action = e.target.dataset && e.target.dataset.action;
      if (!action) return;
      if (action === 'close')       close();
      else if (action === 'pick')   document.getElementById('fileInput').click();
      else if (action === 'submit') submit();
    });

    modalEl.querySelectorAll('input[name="contentKind"]').forEach(r => {
      r.addEventListener('change', () => {
        toggleExamFields(r.value === 'EXAM_BLANK' && r.checked);
      });
    });

    document.getElementById('fileInput').addEventListener('change', handleFilePick);
  }

  function toggleExamFields(show) {
    document.getElementById('examFields').style.display = show ? 'block' : 'none';
    updateUploadHint(show);
    if (show) applyExamDefaultsIfEmpty();
  }

  function applyExamDefaultsIfEmpty() {
    const today = new Date();
    const defaultSemester = today.getMonth() + 1 <= 8 ? '1' : '2';
    const examTypeEl = modalEl.querySelector('#examType');
    const examYearEl = modalEl.querySelector('#examYear');
    const examSemesterEl = modalEl.querySelector('#examSemester');
    if (examTypeEl && !examTypeEl.value) examTypeEl.value = examMetaDefaults.examType || 'OTHER';
    if (examYearEl && !examYearEl.value) examYearEl.value = examMetaDefaults.examYear || String(today.getFullYear());
    if (examSemesterEl && !examSemesterEl.value) examSemesterEl.value = examMetaDefaults.examSemester || defaultSemester;
  }

  function updateUploadHint(isExam) {
    const hint = modalEl && modalEl.querySelector('#uploadModalHint');
    if (!hint) return;
    hint.textContent = isExam && requireExamMeta
      ? '학교명·학년은 시험지 저장 필수값입니다. 비워두면 강사용 기본값(학원명 또는 강사 아이디, 학년 0)으로 저장됩니다.'
      : '여러 장의 사진과 PDF를 함께 선택할 수 있어요.';
  }

  /**
   * 모달 오픈. defaultContentKind로 자료 타입 라디오 기본값을 주입한다
   * ('EXAM_BLANK' | 'GENERAL', 미지정/그외 → 'GENERAL'). 시험지 대시보드 등에서 사용.
   */
  function open(defaultContentKind) {
    // admin 임퍼소네이트도 학생과 동일하게 모달 오픈 가능 — submit() 시 targetUserId가 자동 부착됨.
    if (!conversationId) {
      console.warn('ConversationUpload: conversationId 없음');
      return;
    }
    pickedFiles = [];
    const kind = defaultContentKind === 'EXAM_BLANK' ? 'EXAM_BLANK' : 'GENERAL';
    modalEl.querySelector(`input[name="contentKind"][value="${kind}"]`).checked = true;
    toggleExamFields(kind === 'EXAM_BLANK');
    modalEl.querySelector('#schoolName').value    = studentDefaults.schoolName || '';
    modalEl.querySelector('#grade').value         = studentDefaults.grade || '';
    const today = new Date();
    const defaultSemester = today.getMonth() + 1 <= 8 ? '1' : '2';
    modalEl.querySelector('#examType').value      = kind === 'EXAM_BLANK' ? (examMetaDefaults.examType || 'OTHER') : '';
    modalEl.querySelector('#examYear').value      = kind === 'EXAM_BLANK' ? (examMetaDefaults.examYear || String(today.getFullYear())) : '';
    modalEl.querySelector('#examSemester').value  = kind === 'EXAM_BLANK' ? (examMetaDefaults.examSemester || defaultSemester) : '';
    updateUploadHint(kind === 'EXAM_BLANK');
    modalEl.querySelector('#pickedFileName').textContent = '선택된 파일 없음';
    modalEl.querySelector('#btnUpload').disabled  = true;
    modalEl.style.display = 'flex';
  }

  function close() {
    modalEl.style.display = 'none';
  }

  async function handleFilePick(e) {
    const files = Array.from(e.target.files);
    e.target.value = '';
    if (files.length === 0) return;

    if (files.length > MAX_FILES) {
      alert(`한 번에 최대 ${MAX_FILES}개까지만 업로드할 수 있어요.`);
      return;
    }

    const processed = [];
    for (const file of files) {
      let p = file;
      if (isHeicFile(file)) {
        p = await convertHeic(file);
        if (!p) {
          alert(`${file.name}: HEIC 변환에 실패했습니다.`);
          return;
        }
      }
      if (!ACCEPTED_TYPES.includes(p.type)) {
        alert(`${file.name}: 지원하지 않는 형식입니다.`);
        return;
      }
      processed.push(p);
    }

    pickedFiles = processed;
    modalEl.querySelector('#pickedFileName').textContent =
      processed.length === 1
        ? processed[0].name
        : `${processed.length}개 파일 (${processed[0].name} 외 ${processed.length - 1}개)`;
    modalEl.querySelector('#btnUpload').disabled = false;
  }

  async function submit() {
    if (pickedFiles.length === 0) return;
    const contentKind = modalEl.querySelector('input[name="contentKind"]:checked').value;
    const btn = modalEl.querySelector('#btnUpload');
    btn.disabled = true;

    const formData = new FormData();
    for (const f of pickedFiles) formData.append('files', f);
    // JWT principal에서 자기 userId 결정. ADMIN 임퍼소네이트 시에만 명시적으로 target 부착.
    if (asAdmin && userId) formData.append('targetUserId', userId);
    formData.append('contentKind', contentKind);

    if (contentKind === 'EXAM_BLANK') {
      const schoolName   = modalEl.querySelector('#schoolName').value.trim();
      const grade        = modalEl.querySelector('#grade').value;
      const examType     = modalEl.querySelector('#examType').value;
      const examYear     = modalEl.querySelector('#examYear').value;
      const examSemester = modalEl.querySelector('#examSemester').value;
      if (schoolName)   formData.append('schoolName', schoolName);
      if (grade)        formData.append('grade', grade);
      if (examType)     formData.append('examType', examType);
      if (examYear)     formData.append('examYear', examYear);
      if (examSemester) formData.append('examSemester', examSemester);
    }

    const summary = pickedFiles.length === 1
      ? pickedFiles[0].name
      : `${pickedFiles.length}개 파일`;
    showToast(`${summary} 업로드 중...`, 'loading', 0);
    close();

    try {
      const res = await fetchAuth(uploadUrl || `/api/conversations/${conversationId}/sources`, {
        method: 'POST',
        body: formData
      });
      if (!res.ok) {
        let message = '업로드 실패. 다시 시도해주세요.';
        try {
          const errorBody = await res.json();
          message = errorBody.message || errorBody.error || message;
        } catch (_) {}
        throw new Error(message);
      }
      const data = await res.json();
      showToast(`${summary} 추가 완료`, 'success', 2000);
      try { await onComplete(data); } catch (err) { console.error(err); }
    } catch (err) {
      showToast(err.message || '업로드 실패. 다시 시도해주세요.', 'error', 3000);
    } finally {
      btn.disabled = false;
    }
  }

  function showToast(msg, type, autoHideMs) {
    if (toastTimer) { clearTimeout(toastTimer); toastTimer = null; }
    toastEl.textContent = msg;
    toastEl.className = `upload-toast ${type}`;
    toastEl.style.display = 'block';
    if (autoHideMs > 0) {
      toastTimer = setTimeout(hideToast, autoHideMs);
    }
  }

  function hideToast() {
    toastEl.style.display = 'none';
    toastTimer = null;
  }

  function isHeicFile(file) {
    return HEIC_TYPES.includes(file.type) || /\.(heic|heif)$/i.test(file.name);
  }

  async function convertHeic(file) {
    if (typeof heic2any === 'undefined') return null;
    try {
      const result = await heic2any({ blob: file, toType: 'image/jpeg', quality: 0.9 });
      const blob   = Array.isArray(result) ? result[0] : result;
      return new File([blob], file.name.replace(/\.(heic|heif)$/i, '.jpg'), { type: 'image/jpeg' });
    } catch { return null; }
  }

  window.ConversationUpload = { init, update, open, close };
})();
