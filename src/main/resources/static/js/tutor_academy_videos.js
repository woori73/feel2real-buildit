(function () {
  'use strict';

  const MAX_DURATION_SEC = 120;
  const MAX_SIZE_BYTES = 209715200;

  const input = document.getElementById('videoInput');
  const uploadButton = document.getElementById('uploadButton');
  const status = document.getElementById('uploadStatus');
  const progress = document.getElementById('uploadProgress');
  const list = document.getElementById('videoList');
  const emptyState = document.getElementById('emptyState');
  const countLabel = document.getElementById('videoCount');
  const purposeFilter = document.getElementById('purposeFilter');

  /** 마지막으로 불러온 전체 목록. 필터는 재조회 없이 이 배열을 다시 그린다. */
  let allVideos = [];

  const PURPOSE_LABEL = { DIRECTOR: '원장 소개', LESSON: '수업 영상' };

  document.addEventListener('DOMContentLoaded', async () => {
    await window.ensureCurrentUser();
    uploadButton.addEventListener('click', upload);
    list.addEventListener('click', onListClick);
    list.addEventListener('error', onThumbnailError, true);
    if (purposeFilter) purposeFilter.addEventListener('change', () => render(allVideos));
    await refresh();
  });

  async function refresh() {
    const res = await window.fetchAuth('/api/tutor/academy/videos');
    if (!res.ok) return showStatus('영상 목록을 불러오지 못했습니다.', 'error');
    allVideos = await res.json();
    render(allVideos);
  }

  function render(videos) {
    countLabel.textContent = `(${videos.length}/10)`;
    const filterValue = purposeFilter ? purposeFilter.value : 'ALL';
    const shown = filterValue === 'ALL' ? videos : videos.filter((video) => video.purpose === filterValue);
    emptyState.classList.toggle('hidden', shown.length > 0);
    list.innerHTML = shown.map((video) => `<article class="video-card">
      ${thumbnailMarkup(video)}
      <div class="video-card-body">
        <span class="video-card-purpose">${escapeHtml(PURPOSE_LABEL[video.purpose] || '용도 미지정')}</span>
        <span class="video-card-duration">${formatDuration(video.durationSec)}</span>
        <span class="video-card-size">${escapeHtml(String(video.width || '—'))}×${escapeHtml(String(video.height || '—'))}</span>
        <span class="video-card-ready">Ready</span>
      </div>
      <button class="btn btn-danger video-card-delete" type="button"
              data-delete-video="${escapeHtml(String(video.videoId))}">삭제</button>
    </article>`).join('');
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

  async function onListClick(event) {
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

  async function upload() {
    const file = input.files && input.files[0];
    if (!file) return showStatus('업로드할 영상을 선택해 주세요.', 'error');
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
    body.append('purpose', selectedPurpose());
    setBusy(true);
    setProgress(5);
    showStatus('영상을 업로드하고 변환하고 있습니다. 창을 닫지 말고 잠시 기다려 주세요…', 'busy');
    try {
      const res = await window.fetchAuth('/api/tutor/academy/videos', {method: 'POST', body});
      if (!res.ok) return showStatus(await errorMessage(res, '업로드에 실패했습니다.'), 'error');
      input.value = '';
      setProgress(100);
      showStatus('영상 업로드가 완료되었습니다.', 'ok');
      await refresh();
    } catch (_) {
      showStatus('업로드 중 네트워크 연결이 끊겼습니다. 잠시 후 다시 시도해 주세요.', 'error');
    } finally {
      setBusy(false);
    }
  }

  function selectedPurpose() {
    const checked = document.querySelector('input[name="videoPurpose"]:checked');
    return checked ? checked.value : 'LESSON';
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
