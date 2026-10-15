(function () {
  'use strict';

  const API_BASE = '/api/admin/landing/videos';
  const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
  const SLOT_ORDER = ['HERO_BACKGROUND', 'ACADEMY_SHORTS_DEMO', 'PARENT_REPORT_DEMO'];
  const RATIO_GUIDANCE = {
    HERO_BACKGROUND: '권장 비율 16:9 · 화면을 채우는 가로 영상',
    ACADEMY_SHORTS_DEMO: '권장 비율 9:16 · 세로형 쇼츠 영상',
    PARENT_REPORT_DEMO: '권장 비율 9:16 · 세로형 리포트 영상'
  };

  let videos = [];
  const cardsHost = document.getElementById('landingVideoCards');

  init();

  async function init() {
    const currentUser = await ensureCurrentUser();
    if (!currentUser || currentUser.role !== 'ADMIN') {
      location.href = '/home';
      return;
    }
    document.getElementById('userName').textContent = window.userDisplayName(currentUser);
    await loadVideos();
  }

  async function loadVideos() {
    cardsHost.setAttribute('aria-busy', 'true');
    try {
      const response = await fetchAuth(API_BASE);
      if (!response.ok) throw new Error(await responseMessage(response));
      const payload = await response.json();
      videos = SLOT_ORDER.map(slot => payload.find(item => item.slot === slot)).filter(Boolean);
      if (videos.length !== SLOT_ORDER.length) {
        throw new Error('고정 영상 슬롯 정보를 모두 받지 못했습니다.');
      }
      renderCards();
    } catch (error) {
      cardsHost.innerHTML =
        `<p class="landing-video-load-error" role="alert">${escapeHtml(error.message || '영상 정보를 불러오지 못했습니다.')}</p>`;
    } finally {
      cardsHost.setAttribute('aria-busy', 'false');
    }
  }

  function renderCards() {
    cardsHost.innerHTML = videos.map(renderCard).join('');
    cardsHost.querySelectorAll('[data-upload-form]').forEach(form => {
      form.addEventListener('submit', uploadReplacement);
    });
    cardsHost.querySelectorAll('[data-visibility]').forEach(toggle => {
      toggle.addEventListener('change', updateVisibility);
    });
  }

  function renderCard(video) {
    const id = idFor(video.slot);
    const previewClass =
      video.slot === 'HERO_BACKGROUND' ? 'is-landscape' : 'is-portrait';
    const sourceLabel = sourceLabelFor(video.sourceType);
    const metadata = [
      RATIO_GUIDANCE[video.slot],
      video.originalFilename ? `파일: ${video.originalFilename}` : '현재 번들 기본 영상을 사용 중입니다.',
      formatTechnicalMetadata(video)
    ].filter(Boolean).map(escapeHtml).join('<br>');

    return `
      <article class="landing-video-card" id="card-${id}" data-slot="${video.slot}" aria-labelledby="title-${id}" aria-busy="false">
        <header class="landing-video-card-header">
          <div>
            <h3 id="title-${id}">${escapeHtml(video.label)}</h3>
            <p class="landing-video-description">${escapeHtml(video.description)}</p>
          </div>
          <div class="landing-video-badges" aria-label="영상 상태">
            <span class="landing-video-badge ${video.visible ? 'is-visible' : 'is-hidden'}">${video.visible ? '공개' : '숨김'}</span>
            <span class="landing-video-badge">${sourceLabel}</span>
          </div>
        </header>
        <div class="landing-video-preview ${previewClass}">
          <span class="landing-video-preview-label">${video.visible ? '랜딩에 표시 중' : '관리자 미리보기'}</span>
          <video controls playsinline preload="metadata" aria-label="${escapeAttr(video.label)} 미리보기">
            <source src="${escapeAttr(video.previewUrl)}" type="video/mp4">
            이 브라우저에서는 영상을 재생할 수 없습니다.
          </video>
        </div>
        <div class="landing-video-card-body">
          <p class="landing-video-meta">${metadata}</p>
          <form data-upload-form data-slot="${video.slot}">
            <label class="landing-video-file" for="file-${id}">
              <span>교체할 영상 선택 (60초 미만 · 100MB 이하)</span>
              <input id="file-${id}" name="video" type="file" accept="video/*" required>
            </label>
            <div class="landing-video-actions">
              <button class="landing-video-upload" type="submit">영상 교체</button>
              <label class="landing-video-toggle" for="visible-${id}">
                <input id="visible-${id}" type="checkbox" data-visibility data-slot="${video.slot}" ${video.visible ? 'checked' : ''}>
                랜딩에 공개
              </label>
            </div>
          </form>
          <p class="landing-video-message" id="message-${id}" role="status" aria-live="polite"></p>
        </div>
      </article>`;
  }

  async function uploadReplacement(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const slot = form.dataset.slot;
    const fileInput = form.elements.video;
    const file = fileInput.files[0];
    if (!file) {
      setMessage(slot, '교체할 영상 파일을 선택해 주세요.', true);
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setMessage(slot, '100MB 이하의 영상만 업로드할 수 있습니다.', true);
      return;
    }
    if (!window.confirm('현재 영상을 선택한 파일로 교체하시겠습니까? 변환이 끝날 때까지 기존 영상은 유지됩니다.')) {
      return;
    }

    const body = new FormData();
    body.set('video', file);
    await mutate(slot, '영상을 변환하고 교체하는 중입니다.', async () => {
      const response = await fetchAuth(`${API_BASE}/${slot}`, { method: 'PUT', body });
      if (!response.ok) throw new Error(await responseMessage(response));
      return response.json();
    });
  }

  async function updateVisibility(event) {
    const toggle = event.currentTarget;
    const slot = toggle.dataset.slot;
    const previous = !toggle.checked;
    await mutate(slot, '공개 상태를 저장하는 중입니다.', async () => {
      const response = await fetchAuth(`${API_BASE}/${slot}/visibility`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ visible: toggle.checked })
      });
      if (!response.ok) throw new Error(await responseMessage(response));
      return response.json();
    }, () => {
      toggle.checked = previous;
    });
  }

  async function mutate(slot, busyMessage, request, onError) {
    setBusy(slot, true);
    setMessage(slot, busyMessage, false);
    try {
      const updated = await request();
      videos = videos.map(item => item.slot === slot ? updated : item);
      renderCards();
      setMessage(slot, '변경 사항을 저장했습니다.', false);
    } catch (error) {
      if (onError) onError();
      setBusy(slot, false);
      setMessage(slot, error.message || '변경 사항을 저장하지 못했습니다.', true);
    }
  }

  function setBusy(slot, busy) {
    const card = document.getElementById(`card-${idFor(slot)}`);
    if (!card) return;
    card.setAttribute('aria-busy', String(busy));
    card.querySelectorAll('button, input').forEach(control => {
      control.disabled = busy;
    });
  }

  function setMessage(slot, message, error) {
    const output = document.getElementById(`message-${idFor(slot)}`);
    if (!output) return;
    output.textContent = message;
    output.classList.toggle('is-error', Boolean(error));
    if (error) output.setAttribute('role', 'alert');
    else output.setAttribute('role', 'status');
  }

  function formatTechnicalMetadata(video) {
    const parts = [];
    if (video.width && video.height) parts.push(`${video.width}×${video.height}`);
    if (video.durationSec != null) parts.push(`${video.durationSec}초`);
    if (video.sizeBytes != null) parts.push(formatBytes(video.sizeBytes));
    return parts.join(' · ');
  }

  function sourceLabelFor(sourceType) {
    switch (sourceType) {
      case 'MANAGED':
        return '업로드 영상';
      case 'BUNDLED':
        return '기본 영상';
      default:
        throw new Error('지원하지 않는 영상 sourceType입니다.');
    }
  }

  function formatBytes(bytes) {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))}KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
  }

  async function responseMessage(response) {
    try {
      const payload = await response.json();
      return payload.message || payload.error || '요청을 처리하지 못했습니다.';
    } catch (_) {
      return '요청을 처리하지 못했습니다.';
    }
  }

  function idFor(slot) {
    return String(slot).toLowerCase().replace(/_/g, '-');
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>'"]/g, character => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      "'": '&#39;',
      '"': '&quot;'
    }[character]));
  }

  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }
})();
