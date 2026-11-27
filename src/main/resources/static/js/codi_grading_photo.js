/* Selected photos stay in this tab until the user submits a specific grading record. */
'use strict';
window.bindCodiGradingPhoto = function ({ request, context, beginActivity }) {
  const byId = id => document.getElementById(id);
  const form = byId('uaPhotoForm');
  const picker = byId('uaPhotoFile');
  const image = byId('uaPhotoPreview');
  const records = byId('uaPhotoGrading');
  const kind = byId('uaPhotoKind');
  const more = byId('uaPhotoMore');
  const feedback = byId('uaPhotoFeedback');
  const targets = new Map();
  const path = '/grading-photo-targets';
  let generation = 0;
  let previewUrl = '';
  let selected = null;
  let previewReady = false;
  let nextBefore = null;
  let lookupFailed = false;
  let forbidden = false;
  let loading = false;
  let uploading = false;

  function sync() {
    const ctx = context();
    const available = ctx.work && ctx.ready && ctx.businessId && !forbidden;
    byId('uaPhotoOpen').hidden = !ctx.work;
    byId('uaPhotoOpen').disabled = uploading;
    byId('uaDocumentOpen').disabled = uploading;
    picker.disabled = !available || uploading;
    records.disabled = !available || uploading || !targets.size;
    kind.disabled = !available || uploading;
    more.disabled = !available || loading || uploading;
    more.hidden = !nextBefore && !lookupFailed;
    const cancel = byId('uaPhotoCancel');
    cancel.hidden = !selected;
    cancel.disabled = uploading;
    byId('uaPhotoName').hidden = !selected;
    byId('uaPhotoName').textContent = selected?.name || '';
    const target = targets.get(records.value);
    const confirmation = byId('uaPhotoTarget');
    confirmation.hidden = !target;
    confirmation.textContent = target ? '첨부 대상 · ' + records.selectedOptions[0].textContent + ' · ' + kind.selectedOptions[0].textContent : '';
    byId('uaPhotoLabel').textContent = selected ? '채점 사진 교체' : '채점 사진 선택';
    byId('uaPhotoSubmit').disabled = !available || uploading || loading || !previewReady || !targets.has(records.value);
  }

  function clearFile(clearPicker = true) {
    previewReady = false;
    selected = null;
    if (clearPicker) picker.value = '';
    image.hidden = true;
    image.removeAttribute('src');
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = '';
  }

  function reset() {
    generation++;
    clearFile();
    form.hidden = true;
    byId('uaUploadForm').hidden = false;
    byId('uaDocumentOpen').setAttribute('aria-pressed', 'true');
    byId('uaPhotoOpen').setAttribute('aria-pressed', 'false');
    records.replaceChildren(new Option('기록을 선택하세요', ''));
    targets.clear();
    kind.value = 'STUDENT';
    nextBefore = null;
    forbidden = loading = uploading = lookupFailed = false;
    feedback.textContent = '';
    byId('uaPhotoScope').textContent = '';
    sync();
  }

  async function loadTargets() {
    const { businessId } = context();
    if (!businessId || loading || form.hidden) return;
    const current = generation;
    const query = new URLSearchParams({ businessId });
    if (nextBefore) query.set('before', nextBefore);
    loading = true;
    sync();
    feedback.textContent = '채점 기록을 불러오고 있습니다.';
    try {
      const page = await request(path + '?' + query);
      if (current !== generation) return;
      if (!Array.isArray(page.targets)) throw new Error('채점 기록 목록을 확인할 수 없습니다.');
      for (const row of page.targets) {
        const id = String(row.gradingId);
        if (targets.has(id)) continue;
        targets.set(id, row);
        const title = [row.studentName, row.gradedOn, row.subjectArea, row.material, '기록 ' + id].filter(Boolean).join(' · ');
        const option = new Option(title, id);
        if (row.photoCount >= 10) {
          option.disabled = true;
          option.textContent += ' · 사진 10장 첨부됨';
        }
        records.add(option);
      }
      nextBefore = page.nextBefore || null;
      lookupFailed = false;
      more.textContent = '이전 기록 더 보기';
      if (!targets.size) feedback.textContent = '이 사업체에는 사진을 첨부할 채점 기록이 없습니다.';
      else if (previewReady) feedback.textContent = '사진을 선택했습니다. 대상 기록과 사진 종류를 확인해 주세요.';
      else feedback.textContent = '';
    } catch (err) {
      if (current !== generation) return;
      forbidden = err.status === 403;
      if (forbidden) clearFile();
      lookupFailed = !forbidden;
      more.textContent = '기록 다시 불러오기';
      feedback.textContent = forbidden ? '선택한 사업체의 관리자 또는 연결된 원장 계정에서 채점 사진을 첨부할 수 있습니다.' : err.message;
    } finally {
      if (current === generation) { loading = false; sync(); }
    }
  }

  function openPhotos() {
    if (!context().work || uploading || !form.hidden) return;
    form.hidden = false;
    byId('uaUploadForm').hidden = true;
    byId('uaDocumentOpen').setAttribute('aria-pressed', 'false');
    byId('uaPhotoOpen').setAttribute('aria-pressed', 'true');
    const ctx = context();
    byId('uaPhotoScope').textContent = ctx.businessId ? '선택한 사업체 · ' + (ctx.businessName || '사업체 ' + ctx.businessId) : '먼저 사업체를 선택해 주세요.';
    sync();
    if (ctx.businessId) loadTargets();
  }

  async function chooseFile(candidate) {
    if (!candidate || !context().work || uploading || forbidden) return;
    clearFile(false);
    selected = candidate;
    sync();
    try {
      if (!candidate.size || candidate.size > 10 * 1024 * 1024) throw new Error('내용이 있는 10MB 이하 사진을 선택해 주세요.');
      const bytes = new Uint8Array(await candidate.slice(0, 8).arrayBuffer());
      if (selected !== candidate) return;
      const jpeg = bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
      const png = [137,80,78,71,13,10,26,10].every((byte, i) => byte === bytes[i]);
      if (!jpeg && !png) throw new Error('JPEG 또는 PNG 사진을 선택해 주세요.');
      previewUrl = URL.createObjectURL(candidate);
      image.src = previewUrl;
      await image.decode();
      if (selected !== candidate) return;
      image.hidden = false;
      previewReady = true;
      if (context().businessId && (targets.size || loading)) {
        feedback.textContent = '사진을 선택했습니다. 대상 기록과 사진 종류를 확인해 주세요.';
      }
    } catch (err) {
      if (selected !== candidate) return;
      clearFile();
      feedback.textContent = err.name === 'EncodingError' ? '사진을 읽을 수 없습니다. 다른 JPEG 또는 PNG를 선택해 주세요.' : err.message;
    } finally { sync(); }
  }

  async function submit(e) {
    e.preventDefault();
    const target = targets.get(records.value);
    const ctx = context();
    if (!target || target.photoCount >= 10 || !ctx.work || !ctx.businessId || !ctx.ready || forbidden || !previewReady || loading || uploading || form.hidden) return;
    const current = generation;
    uploading = true;
    sync();
    const activity = beginActivity('work', 'grading-photo');
    feedback.textContent = '선택한 채점 기록에 사진을 첨부하고 있습니다.';
    const label = records.selectedOptions[0].textContent;
    try {
      const body = new FormData();
      body.append('file', selected);
      const query = new URLSearchParams({ businessId: ctx.businessId, studentId: target.studentId, kind: kind.value });
      const result = await request(path + '/' + encodeURIComponent(target.gradingId) + '/photos?' + query, { method: 'POST', body });
      if (current !== generation) return;
      if (!result.photoId) throw new Error('첨부 결과를 확인할 수 없습니다. 다시 보내기 전에 채점 기록을 확인해 주세요.');
      target.photoCount = (target.photoCount || 0) + 1;
      if (target.photoCount >= 10) {
        records.selectedOptions[0].disabled = true;
        records.selectedOptions[0].textContent += ' · 사진 10장 첨부됨';
      }
      clearFile();
      records.value = '';
      feedback.textContent = label + '에 ' + (result.kindLabel || kind.selectedOptions[0].textContent) + ' 사진을 첨부했습니다.';
      activity('result');
    } catch (err) {
      if (current !== generation) return;
      feedback.textContent = err.status === 403 ? '이 사업체의 채점 사진을 저장할 권한이 없습니다.' :
        err.status === 404 ? '선택한 채점 기록을 찾을 수 없습니다. 다른 기록을 선택해 주세요.' :
        err.status ? err.message : '첨부 결과를 확인할 수 없습니다. 다시 보내기 전에 채점 기록을 확인해 주세요.';
      activity('error');
    } finally {
      if (current === generation) { uploading = false; sync(); }
    }
  }

  byId('uaDocumentOpen').addEventListener('click', () => {
    if (!uploading) { reset(); byId('uaFile').focus({ preventScroll: true }); }
  });
  byId('uaPhotoOpen').addEventListener('click', openPhotos);
  more.addEventListener('click', loadTargets);
  picker.addEventListener('change', () => chooseFile(picker.files[0]));
  records.addEventListener('change', sync);
  kind.addEventListener('change', sync);
  byId('uaPhotoCancel').addEventListener('click', () => {
    if (uploading) return;
    clearFile();
    records.value = '';
    kind.value = 'STUDENT';
    feedback.textContent = '사진 선택을 취소했습니다.';
    sync();
    picker.focus({ preventScroll: true });
  });
  form.addEventListener('submit', submit);
  window.addEventListener('pagehide', reset);
  sync();
  function openFile(file) {
    if (!context().work || uploading) return;
    openPhotos();
    picker.focus({ preventScroll: true });
    chooseFile(file);
  }
  return { sync, reset, openFile };
};
