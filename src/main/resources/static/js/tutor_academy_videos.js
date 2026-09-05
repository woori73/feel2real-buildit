(function () {
  'use strict';

  const MAX_DURATION_SEC = 120;
  const MAX_SIZE_BYTES = 209715200;

  const SECTIONS = window.F2RAcademySections;
  if (!SECTIONS) {
    throw new Error('F2RAcademySections must be loaded before tutor_academy_videos.js');
  }

  const input = document.getElementById('videoInput');
  const uploadButton = document.getElementById('uploadButton');
  const status = document.getElementById('uploadStatus');
  const progress = document.getElementById('uploadProgress');
  const groups = document.getElementById('videoGroups');
  const emptyState = document.getElementById('emptyState');
  const countLabel = document.getElementById('videoCount');

  /** 마지막으로 불러온 전체 목록. 용도 변경 후 재조회로만 갱신한다(낙관적 갱신 없음). */
  let allVideos = [];

  document.addEventListener('DOMContentLoaded', async () => {
    await window.ensureCurrentUser();
    uploadButton.addEventListener('click', upload);
    groups.addEventListener('click', onDeleteClick);
    groups.addEventListener('change', onPurposeChange);
    groups.addEventListener('error', onThumbnailError, true);
    await refresh();
  });

  async function refresh() {
    const res = await window.fetchAuth('/api/tutor/academy/videos');
    if (!res.ok) return showStatus('영상 목록을 불러오지 못했습니다.', 'error');
    allVideos = await res.json();
    render(allVideos);
  }

  /**
   * 용도별로 완전히 분리된 세 영역으로 그린다(단일 목록 + 필터 드롭다운 방식은 쓰지 않는다 —
   * 내부 값만 나뉘고 화면은 여전히 한 목록이면 강사가 어떤 영상이 어디 쓰이는지 알기 어렵다).
   * 미지정 영역은 분류할 것이 있을 때만 맨 위에 나타나 주의를 끈다.
   */
  function render(videos) {
    countLabel.textContent = `(${videos.length}/10)`;
    emptyState.classList.toggle('hidden', videos.length > 0);

    const unclassified = videos.filter(SECTIONS.isUnclassifiedVideo);
    const directorVideos = videos.filter(SECTIONS.isDirectorVideo);
    const lessonVideos = videos.filter(SECTIONS.isLessonVideo);

    groups.innerHTML = [
      unclassified.length
        ? videoGroupHtml({
            groupClass: 'video-group-unclassified',
            title: `⚠️ 용도 미지정 (${unclassified.length})`,
            hint: '아래에서 대표 원장 소개 영상인지 수업 영상인지 지정해 주세요. 지정하기 전까지 공개 Academy에 노출되지 않습니다.',
            videos: unclassified
          })
        : '',
      videoGroupHtml({
        groupClass: 'video-group-director',
        title: `대표 원장 소개 영상 (${directorVideos.length})`,
        hint: '원장 소개·히어로 영역에서만 선택할 수 있습니다.',
        videos: directorVideos,
        emptyText: '아직 지정된 대표 원장 소개 영상이 없습니다.'
      }),
      videoGroupHtml({
        groupClass: 'video-group-lesson',
        title: `수업 영상 (${lessonVideos.length})`,
        hint: '실제 수업 현장·대표 수업·미니 강의에서 선택할 수 있습니다.',
        videos: lessonVideos,
        emptyText: '아직 지정된 수업 영상이 없습니다.'
      })
    ].join('');
  }

  function videoGroupHtml(opts) {
    const body = opts.videos.length
      ? `<div class="video-grid">${opts.videos.map(videoCardHtml).join('')}</div>`
      : `<p class="muted video-group-empty">${escapeHtml(opts.emptyText || '')}</p>`;
    return `<section class="video-group ${escapeHtml(opts.groupClass)}">
      <h3 class="video-group-title">${escapeHtml(opts.title)}</h3>
      <p class="muted video-group-hint">${escapeHtml(opts.hint)}</p>
      ${body}
    </section>`;
  }

  function videoCardHtml(video) {
    const purpose = video.purpose || '';
    return `<article class="video-card">
      ${thumbnailMarkup(video)}
      <div class="video-card-body">
        <label class="video-card-purpose-label">
          용도
          <select class="video-card-purpose-select" data-purpose-select="${escapeHtml(String(video.videoId))}">
            <option value="" ${purpose ? '' : 'selected'} disabled hidden>선택 안 함</option>
            <option value="LESSON" ${purpose === 'LESSON' ? 'selected' : ''}>수업 영상</option>
            <option value="DIRECTOR" ${purpose === 'DIRECTOR' ? 'selected' : ''}>대표 원장 소개 영상</option>
          </select>
        </label>
        <span class="video-card-duration">${formatDuration(video.durationSec)}</span>
        <span class="video-card-size">${escapeHtml(String(video.width || '—'))}×${escapeHtml(String(video.height || '—'))}</span>
        <span class="video-card-ready">Ready</span>
      </div>
      <button class="btn btn-danger video-card-delete" type="button"
              data-delete-video="${escapeHtml(String(video.videoId))}">삭제</button>
    </article>`;
  }

  function thumbnailMarkup(video) {
    return video.thumbnailUrl
      ? `<img class="video-card-thumb" src="${escapeHtml(video.thumbnailUrl)}"
              data-video-url="${escapeHtml(video.videoUrl)}" alt="" loading="lazy" />`
      : `<video class="video-card-thumb" src="${escapeHtml(video.videoUrl)}"
                preload="metadata" muted playsinline aria-label="영상 미리보기"></video>`;
  }

  function onThumbnailError(event) {
    const image = event.target.closest('.video-card-thumb');
    if (!image || image.tagName !== 'IMG') return;
    const preview = document.createElement('video');
    preview.className = 'video-card-thumb';
    preview.src = image.dataset.videoUrl || '';
    preview.preload = 'metadata';
    preview.muted = true;
    preview.playsInline = true;
    preview.setAttribute('aria-label', '영상 미리보기');
    image.replaceWith(preview);
  }

  async function onDeleteClick(event) {
    const button = event.target.closest('[data-delete-video]');
    if (!button || !confirm('이 영상을 삭제할까요? 되돌릴 수 없습니다.')) return;
    button.disabled = true;
    const res = await window.fetchAuth(`/api/tutor/academy/videos/${button.dataset.deleteVideo}`, {
      method: 'DELETE'
    });
    if (!res.ok) {
      button.disabled = false;
      return showStatus(await errorMessage(res, '삭제에 실패했습니다.'), 'error');
    }
    showStatus('영상을 삭제했습니다.', 'ok');
    await refresh();
  }

  /**
   * 카드의 용도 select가 바뀌면 즉시 서버에 반영한다(별도 저장 버튼 없음 — 목록 자체가 이미
   * "관리" 화면이라 변경=저장). NULL→DIRECTOR/LESSON, DIRECTOR↔LESSON 모두 이 한 경로로
   * 처리되며, 성공하면 목록을 다시 불러와 해당 카드가 올바른 영역으로 즉시 옮겨간다.
   */
  async function onPurposeChange(event) {
    const select = event.target.closest('[data-purpose-select]');
    if (!select) return;
    const videoId = select.dataset.purposeSelect;
    const nextPurpose = select.value;
    if (!nextPurpose) return;
    select.disabled = true;
    try {
      const res = await window.fetchAuth(`/api/tutor/academy/videos/${videoId}/purpose`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ purpose: nextPurpose })
      });
      if (!res.ok) {
        return showStatus(await errorMessage(res, '용도 변경에 실패했습니다.'), 'error');
      }
      showStatus('영상 용도를 변경했습니다.', 'ok');
      await refresh();
    } finally {
      select.disabled = false;
    }
  }

  async function upload() {
    const file = input.files && input.files[0];
    if (!file) return showStatus('업로드할 영상을 선택해 주세요.', 'error');
    const purpose = selectedPurpose();
    if (!purpose) {
      return showStatus('영상 용도(수업 영상 / 대표 원장 소개 영상)를 선택해 주세요.', 'error');
    }
    if (!isSupportedFile(file)) {
      return showStatus('휴대폰에서 촬영한 MP4 또는 MOV 영상만 업로드할 수 있습니다.', 'error');
    }
    if (file.size > MAX_SIZE_BYTES) {
      return showStatus('영상 용량은 200MiB 이하여야 합니다.', 'error');
    }
    const duration = await readDuration(file);
    if (duration !== null && duration > MAX_DURATION_SEC) {
      return showStatus(`영상 길이는 120초 이하여야 합니다. (현재 약 ${Math.round(duration)}초)`, 'error');
    }

    const body = new FormData();
    body.append('video', file);
    body.append('purpose', purpose);
    setBusy(true);
    setProgress(5);
    showStatus('영상을 업로드하고 변환하고 있습니다. 창을 닫지 말고 잠시 기다려 주세요…', 'busy');
    try {
      const res = await window.fetchAuth('/api/tutor/academy/videos', {method: 'POST', body});
      if (!res.ok) return showStatus(await errorMessage(res, '업로드에 실패했습니다.'), 'error');
      input.value = '';
      clearSelectedPurpose();
      setProgress(100);
      showStatus('영상 업로드가 완료되었습니다.', 'ok');
      await refresh();
    } catch (_) {
      showStatus('업로드 중 네트워크 연결이 끊겼습니다. 잠시 후 다시 시도해 주세요.', 'error');
    } finally {
      setBusy(false);
    }
  }

  /** 이번 업로드부터 용도는 필수 선택이다 — 기본 선택값을 두지 않는다(강제 클릭 유도). */
  function selectedPurpose() {
    const checked = document.querySelector('input[name="videoPurpose"]:checked');
    return checked ? checked.value : null;
  }

  function clearSelectedPurpose() {
    document.querySelectorAll('input[name="videoPurpose"]').forEach((el) => { el.checked = false; });
  }

  function isSupportedFile(file) {
    return /\.(mp4|mov)$/i.test(file && file.name ? file.name : '');
  }

  function readDuration(file) {
    return new Promise((resolve) => {
      const url = URL.createObjectURL(file);
      const probe = document.createElement('video');
      const done = (value) => { URL.revokeObjectURL(url); resolve(value); };
      probe.preload = 'metadata';
      probe.onloadedmetadata = () => done(Number.isFinite(probe.duration) ? probe.duration : null);
      probe.onerror = () => done(null);
      probe.src = url;
    });
  }

  async function errorMessage(res, fallback) {
    try {
      const body = await res.json();
      if (body && body.message) return body.message;
    } catch (_) { /* JSON 오류가 아니면 기본 문구를 사용한다. */ }
    return fallback;
  }

  function setBusy(busy) { uploadButton.disabled = busy; input.disabled = busy; }
  function setProgress(value) {
    progress.value = value;
    progress.classList.toggle('hidden', value === 0 || value === 100);
  }
  function showStatus(message, tone) {
    status.textContent = message;
    status.dataset.tone = tone;
    status.classList.remove('hidden');
  }
  function formatDuration(seconds) {
    const total = Number(seconds) || 0;
    return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
  }
  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  window.__academyVideoUploadTestApi = {
    MAX_DURATION_SEC, MAX_SIZE_BYTES, thumbnailMarkup, isSupportedFile
  };
})();
