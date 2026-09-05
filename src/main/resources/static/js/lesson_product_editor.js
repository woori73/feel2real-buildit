(function () {
  const stateApi = window.F2RLessonProductEditorState;
  const form = document.getElementById('lessonProductForm');
  const tabs = document.getElementById('courseTabs');
  const addButton = document.getElementById('addCourseTab');
  const deleteButton = document.getElementById('deleteLessonButton');
  const saveButton = document.getElementById('saveLessonsButton');
  const statusBox = document.getElementById('editorStatus');
  const errorBox = document.getElementById('editorErrors');
  const draftState = document.getElementById('draftState');
  const preview = document.getElementById('lessonPreview');
  const previewShell = preview?.closest('.preview');
  const programCardPreview = document.getElementById('lessonProgramCardPreview');
  const programCardSlot = programCardPreview?.querySelector('[data-program-card-slot]');
  const programCardOtRow = programCardPreview?.querySelector('[data-program-card-ot]');
  const mediaModal = document.getElementById('mediaModal');
  const mediaArea = document.getElementById('courseMediaArea');
  const otMediaArea = document.getElementById('otMediaArea');
  const otVideoControl = document.getElementById('otVideoControl');
  const SECTIONS = window.F2RAcademySections || null;
  // 가로 컬렉션 학원: 편집기 미리보기가 공개 storefront 와 같은 programCardHtml 을 쓴다.
  // 그 외: 기존 showcase 골격(#lessonPreview) 유지.
  let horizontalLayout = false;
  const closeMediaButton = document.getElementById('closeMediaModal');
  const clearSelectedMediaButton = document.getElementById('clearSelectedMediaButton');
  const mediaImageInput = document.getElementById('mediaImageInput');
  const mediaImageLabel = document.getElementById('mediaImageLabel');
  const mediaVideoInput = document.getElementById('mediaVideoInput');
  const mediaVideoList = document.getElementById('mediaVideoList');
  const mediaModalStatus = document.getElementById('mediaModalStatus');
  const mediaModalTitle = document.getElementById('mediaModalTitle');
  const mediaModalHint = document.getElementById('mediaModalHint');
  let editorState = null;
  let myVideos = [];
  let mediaTarget = 'hero';
  // imageKey -> blob URL. 업로드 직후에는 서버 lesson row가 아직 새 mediaImageKey를 모른다
  // (컬렉션 저장 전까지). lessonId 기반 hero-image URL만 쓰면 저장 전에는 항상 예전 이미지가
  // 되돌아온다 — 그래서 방금 고른 파일의 로컬 미리보기를 imageKey로 붙들어 두고, 그게 있으면
  // 그걸 최우선으로 보여준다. 저장 후 새로고침하면 맵이 비므로 자연히 서버 URL로 넘어간다.
  const localImagePreviews = new Map();

  const NUMBER_FIELDS = new Set([
    'priceAmount',
    'maxCapacity',
    'weeklyCount',
    'durationWeeks',
    'durationMonths',
    'mediaVideoId',
    'otVideoId'
  ]);

  if (!stateApi || !form || !tabs || !addButton || !deleteButton) return;

  form.addEventListener('input', captureDraft);
  form.addEventListener('change', captureDraft);
  form.addEventListener('keydown', suppressImplicitSubmit);
  form.addEventListener('submit', saveCollection);
  deleteButton.addEventListener('click', deleteActiveLesson);
  addButton.addEventListener('click', addLesson);
  mediaArea?.addEventListener('click', () => openMediaPicker('hero'));
  otMediaArea?.addEventListener('click', () => openMediaPicker('ot'));
  otVideoControl?.addEventListener('click', () => openMediaPicker('ot'));
  programCardPreview?.addEventListener('click', (event) => {
    const trigger = event.target.closest?.('[data-editor-media]');
    if (!trigger) return;
    openMediaPicker(trigger.dataset.editorMedia === 'ot' ? 'ot' : 'hero');
  });
  closeMediaButton?.addEventListener('click', () => mediaModal?.close());
  clearSelectedMediaButton?.addEventListener('click', clearSelectedMedia);
  mediaImageInput?.addEventListener('change', (event) => uploadMedia(event, 'image'));
  mediaVideoInput?.addEventListener('change', (event) => uploadMedia(event, 'video'));
  document.addEventListener('error', onMediaPreviewError, true);
  init();

  async function init() {
    setBusy(true);
    clearMessages();
    try {
      const response = await fetchAuth('/api/tutor/academy/me');
      const action = stateApi.academyLoadAction(response.status);
      if (action.type === 'redirect') {
        location.replace(action.location);
        return;
      }
      if (!response.ok) throw new Error('Academy 조회에 실패했습니다.');

      const academy = await response.json();
      const builderDraft = (academy.pageSettings && academy.pageSettings.builderDraft) || {};
      // 가로 컬렉션이 기본이다 — 명시적 'vertical'일 때만 기존 세로 레이아웃(academy_sections.js
      // isHorizontalCollection과 동일 규칙).
      horizontalLayout = SECTIONS != null
        && String(builderDraft.lessonsLayout || '').trim().toLowerCase() !== 'vertical';
      document.querySelector('.lesson-editor-app')
        ?.classList.toggle('is-horizontal-layout', horizontalLayout);
      const params = new URLSearchParams(location.search);
      const requestedLessonId = params.get('lessonId');
      editorState = stateApi.createState(academy.lessons || [], {
        activeLessonId: requestedLessonId,
        newDraft: params.get('new') === 'true'
      });
      renderAll();
      if (editorState.selectionError) showError(editorState.selectionError);
    } catch (error) {
      showError(error.message || '수업상품 편집기를 불러오지 못했습니다.');
    } finally {
      setBusy(false);
    }
  }

  function renderAll() {
    renderTabs();
    bindActiveForm();
    renderPreview();
    renderDirtyState();
    updateSaveLabel();
  }

  function renderTabs() {
    tabs.replaceChildren();
    tabs.classList.toggle('is-overflowing', editorState.items.length + 1 > 5);
    tabs.style.setProperty('--course-tab-columns', String(Math.min(editorState.items.length + 1, 5)));
    for (const item of editorState.items) {
      const value = item.appliedPreview;
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'course-tab';
      button.dataset.clientKey = item.clientKey;
      if (item.clientKey === editorState.activeKey) {
        button.classList.add('active');
        button.classList.add('is-active');
        button.setAttribute('aria-current', 'true');
      }
      const guidance = document.createElement('small');
      guidance.textContent = value.tabGuidanceLabel || '안내 문구';
      const title = document.createElement('strong');
      title.textContent = value.tabShortName || value.name || '새 수업상품';
      button.append(guidance, title);
      button.addEventListener('click', () => selectLesson(item.clientKey));
      tabs.appendChild(button);
    }
    tabs.appendChild(addButton);
    if (tabs.classList.contains('is-overflowing')) {
      tabs.querySelector('.course-tab.is-active')?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
  }

  function suppressImplicitSubmit(event) {
    const target = event.target;
    const decision = stateApi.enterSubmitDecision(event.key, target && target.tagName);
    if (decision === 'block') event.preventDefault();
  }

  function selectLesson(clientKey) {
    editorState = stateApi.selectItem(editorState, clientKey, readForm());
    clearMessages();
    renderAll();
    tabs.querySelector(`[data-client-key="${cssEscape(clientKey)}"]`)?.focus();
  }

  function addLesson() {
    if (stateApi.getActiveItem(editorState)) {
      editorState = stateApi.updateActiveDraft(editorState, readForm());
    }
    editorState = stateApi.addDraft(editorState);
    clearMessages();
    renderAll();
    document.getElementById('name')?.focus();
  }

  // 입력 즉시 미리보기에 반영한다. appliedPreview를 draft와 같이 옮겨 두어야
  // 탭 라벨과 저장 replay 판정이 입력 내용과 어긋나지 않는다.
  function captureDraft() {
    if (!stateApi.getActiveItem(editorState)) return;
    editorState = stateApi.updateActiveDraft(editorState, readForm());
    editorState = stateApi.applyActivePreview(editorState);
    syncCommonTheme();
    renderTabs();
    renderPreview();
    renderDirtyState();
  }

  function syncCommonTheme() {
    const active = stateApi.getActiveItem(editorState);
    if (!active) return;
    editorState = stateApi.setCommonTheme(editorState, active.draft.theme);
  }

  function readForm() {
    const draft = {};
    for (const field of stateApi.AUTHORING_FIELDS) {
      if (field === 'weekdays') {
        // 간소화된 화면에는 요일 체크박스가 없다. querySelectorAll은 없어도 빈 NodeList를
        // 돌려주므로, 존재 여부를 먼저 확인하지 않으면 매 입력마다 기존 요일 값이 빈 배열로
        // 덮어써진다 — 다른 필드처럼 control이 없으면 patch에서 아예 생략해 기존 값을 지킨다.
        const weekdayInputs = form.querySelectorAll('[name="weekdays"]');
        if (weekdayInputs.length === 0) continue;
        draft.weekdays = Array.from(weekdayInputs)
          .filter((input) => input.checked)
          .map((input) => input.value);
        continue;
      }
      const control = form.elements.namedItem(field);
      if (!control) continue;
      const value = typeof control.value === 'string' ? control.value.trim() : control.value;
      draft[field] = NUMBER_FIELDS.has(field)
        ? (value === '' ? null : Number(value))
        : (value === '' ? null : value);
    }
    return draft;
  }

  function bindActiveForm() {
    const active = stateApi.getActiveItem(editorState);
    const controls = form.querySelectorAll('input, textarea, select, button:not([aria-disabled="true"])');
    controls.forEach((control) => { control.disabled = !active; });
    if (!active) {
      form.reset();
      return;
    }
    for (const field of stateApi.AUTHORING_FIELDS) {
      if (field === 'weekdays') {
        const selected = new Set(active.draft.weekdays || []);
        form.querySelectorAll('[name="weekdays"]').forEach((input) => {
          input.checked = selected.has(input.value);
        });
        continue;
      }
      const control = form.elements.namedItem(field);
      if (control) control.value = active.draft[field] ?? '';
    }
  }

  // 선택한 수업을 삭제한다. 기존 수업은 서버에서 숨김 처리(soft delete)하고,
  // 아직 저장 전인 신규 draft는 탭만 없앤다. 마지막 하나를 지우면 빈 draft를 새로 만든다.
  async function deleteActiveLesson() {
    const active = stateApi.getActiveItem(editorState);
    if (!active) return;
    const label = active.draft.name || active.serverSnapshot?.name || '이 수업';
    if (!window.confirm(`'${label}'을(를) 삭제할까요?`)) return;

    const lessonId = active.serverSnapshot?.lessonId ?? null;
    clearMessages();
    if (lessonId != null) {
      setBusy(true);
      try {
        const response = await fetchAuth(`/api/tutor/academy/lessons/${lessonId}`, { method: 'DELETE' });
        if (response.status === 409) {
          // 승인된 신청/수강생이 있는 수업은 서버가 숨김을 막는다.
          showError('신청한 수강생이 있어 삭제할 수 없습니다. 수강생 정리 후 다시 시도해 주세요.');
          return;
        }
        if (!response.ok) {
          showError('수업을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.');
          return;
        }
      } catch (_) {
        showError('네트워크 오류로 수업을 삭제하지 못했습니다.');
        return;
      } finally {
        setBusy(false);
      }
    }
    releaseLocalImagePreviewFor(active);
    editorState = stateApi.removeItem(editorState, active.clientKey);
    renderAll();
    statusBox.textContent = `'${label}'을(를) 삭제했습니다.`;
  }

  function renderPreview() {
    const active = stateApi.getActiveItem(editorState);
    if (!active) {
      preview.hidden = true;
      if (programCardPreview) programCardPreview.hidden = true;
      return;
    }
    const value = active.appliedPreview;
    const theme = value.theme || 'NAVY';
    if (previewShell) previewShell.dataset.theme = theme;

    // 가로 컬렉션 학원: 공개 storefront 와 같은 programCardHtml 로 렌더한다.
    if (horizontalLayout) {
      preview.hidden = true;
      if (programCardPreview) {
        programCardPreview.hidden = false;
        programCardPreview.dataset.theme = theme;
        renderProgramCardPreview(value, active);
      }
      return;
    }

    if (programCardPreview) programCardPreview.hidden = true;
    preview.hidden = false;
    preview.dataset.theme = theme;
    setPreview('kicker', value.kicker, '상단 보조 문구');
    setPreview('tabShortName', value.tabShortName || value.name, '수업명');
    setPreview('name', value.name, '수업명을 입력해 주세요');
    setPreview('highlight', value.highlight, '강조 문구가 여기에 표시됩니다.');
    setPreview('description', value.description, '수업 소개가 여기에 표시됩니다.');
    setPreview('targetGroup', label(value.targetGroup), '수강 대상');
    setPreview('lessonType', label(value.lessonType), '수업 유형');
    setPreview('priceSummary', value.priceSummary, priceFallback(value));
    setPreview('scheduleSummary', value.scheduleSummary, '일정을 입력해 주세요');
    setPreview('coreDetailTitle', value.coreDetailTitle, '이렇게 수업합니다');
    setPreview('learnerNeed', value.learnerNeed, '학생의 고민이 여기에 표시됩니다.');
    setPreview('explanation1', value.explanation1, '첫 번째 핵심 설명');
    setPreview('explanation2', value.explanation2, '두 번째 핵심 설명');
    setPreview('explanation3', value.explanation3, '세 번째 핵심 설명');
    renderMedia();
  }

  /* ── 가로 컬렉션(Storefront V2) Program Card 미리보기 ──────────
   * 공개 홈페이지(academy_public.js)와 빌더 캔버스(academy_builder.js)가 쓰는 것과
   * 똑같은 F2RAcademySections.programCardHtml 을 호출한다 — 하나의 렌더 계약.
   * ctx 는 편집기 전용 두 슬롯만 채운다(미디어=클릭 시 모달, CTA=정적 표시).
   */
  const EDITOR_CARD_CTX = {
    programCardMedia(lesson) {
      if (lesson.mediaType === 'IMAGE') {
        // 저장 전에는 방금 고른 파일의 로컬 미리보기(blob)를 우선 쓴다 — lessonId가 있어도
        // 서버 hero-image는 저장 전까지 예전 이미지를 돌려주기 때문이다.
        const src = heroImageSrc(lesson.mediaImageKey, lesson.lessonId);
        if (src) {
          return `<button type="button" class="f2r-program-card-media" data-editor-media="hero" data-media-fit="image" aria-label="대표 이미지 변경">`
            + `<img src="${escapeAttr(src)}" alt="" loading="lazy"></button>`;
        }
        // 미저장 신규 수업 + 로컬 미리보기도 없는 경우(예: 새로고침 직후)만 안내 문구로 대체한다.
        return '<button type="button" class="f2r-program-card-media f2r-program-card-media-empty" data-editor-media="hero">'
          + '이미지가 등록되었습니다.<br>저장하면 카드에 표시됩니다.</button>';
      }
      if (lesson.mediaType === 'VIDEO' && lesson.mediaVideoId != null) {
        const found = myVideos.find((video) => String(video.videoId) === String(lesson.mediaVideoId));
        const thumb = found && found.thumbnailUrl
          ? `<img src="${escapeAttr(found.thumbnailUrl)}" alt="" loading="lazy">`
          : '<span class="f2r-program-card-media-ph" aria-hidden="true">🎬</span>';
        return `<button type="button" class="f2r-program-card-media f2r-program-card-media-video" data-editor-media="hero" aria-label="대표 영상 변경">`
          + `${thumb}<span class="f2r-video-play" aria-hidden="true">▶</span></button>`;
      }
      return '<button type="button" class="f2r-program-card-media f2r-program-card-media-empty" data-editor-media="hero">'
        + '대표 이미지 또는 수업 영상을<br>등록해 주세요</button>';
    },
    collectionConsultCta(labelText) {
      const text = String(labelText == null ? '' : labelText).trim() || '상담 신청하기';
      return `<span class="f2r-program-card-cta" aria-hidden="true">${escapeHtml(text)}</span>`;
    }
  };

  function renderProgramCardPreview(value, active) {
    if (!SECTIONS || !programCardSlot) return;
    const lesson = {
      lessonId: active.lessonId,
      tabShortName: value.tabShortName,
      name: value.name,
      kicker: value.kicker,
      learnerNeed: value.learnerNeed,
      highlight: value.highlight,
      gradeLabel: value.gradeLabel,
      tabGuidanceLabel: value.tabGuidanceLabel,
      mediaType: value.mediaVideoId != null ? 'VIDEO' : (value.mediaImageKey ? 'IMAGE' : null),
      mediaImageKey: value.mediaImageKey,
      mediaVideoId: value.mediaVideoId
    };
    programCardSlot.innerHTML = SECTIONS.programCardHtml(lesson, EDITOR_CARD_CTX);
    if (typeof SECTIONS.applyImageMediaAspect === 'function') SECTIONS.applyImageMediaAspect(programCardSlot);
    renderProgramCardOt(value.otVideoId);
  }

  function renderProgramCardOt(videoId) {
    if (!programCardOtRow) return;
    const has = videoId != null;
    const found = has ? myVideos.find((video) => String(video.videoId) === String(videoId)) : null;
    const name = found && found.originalFilename ? ` · ${escapeHtml(found.originalFilename)}` : '';
    programCardOtRow.innerHTML = `<span class="program-card-preview-ot-label">수업 OT 영상</span>`
      + `<button type="button" class="btn btn-secondary" data-editor-media="ot">${has ? 'OT 영상 변경' : 'OT 영상 등록'}</button>`
      + (has ? `<span class="program-card-preview-ot-state">등록됨${name} · 저장 후 공개</span>` : '');
  }

  /* ── 대표 미디어 ──────────────────────────────────────────── */

  /**
   * 업로드는 즉시 서버로 보내되, 수업상품과의 연결은 hidden input에만 남긴다.
   * DB 반영은 기존 컬렉션 저장 버튼이 담당한다 — 미디어만 별도 즉시저장 경로를 만들면
   * dirty 추적과 낙관적 락이 두 갈래가 된다.
   */
  function setMedia(imageKey, videoId) {
    const previousImageKey = currentMedia().imageKey;
    if (previousImageKey && previousImageKey !== imageKey) {
      revokeLocalImagePreview(previousImageKey);
    }
    const imageField = form.elements.namedItem('mediaImageKey');
    const videoField = form.elements.namedItem('mediaVideoId');
    if (imageField) imageField.value = imageKey || '';
    if (videoField) videoField.value = videoId == null ? '' : String(videoId);
    // captureDraft가 draft 반영 + 미리보기 적용까지 한다. 별도의 "미리보기 반영" 단계는 없다.
    captureDraft();
    renderAll();
  }

  /**
   * 방금 고른 파일을 imageKey에 붙여 즉시 미리보기 가능한 blob URL로 등록한다.
   * 서버 저장(컬렉션 PUT) 전에도 미리보기가 새 이미지를 보여줄 수 있는 유일한 방법 —
   * lessonId 기반 hero-image 엔드포인트는 저장 전까지 예전 이미지만 돌려준다.
   */
  function registerLocalImagePreview(imageKey, file) {
    if (!imageKey || !file) return;
    localImagePreviews.set(imageKey, URL.createObjectURL(file));
  }

  function revokeLocalImagePreview(imageKey) {
    const url = imageKey ? localImagePreviews.get(imageKey) : null;
    if (!url) return;
    URL.revokeObjectURL(url);
    localImagePreviews.delete(imageKey);
  }

  /**
   * 히어로 이미지 표시용 src 하나로 통일. 방금 올린 로컬 미리보기가 있으면 그걸 우선하고,
   * 없으면(저장된 지 오래된 이미지, 또는 새로고침 이후) lessonId 기반 서버 URL로 폴백한다.
   * 저장된 수업만 서버 URL을 만들 수 있어 lessonId가 없으면 null(호출부가 placeholder 처리).
   */
  function heroImageSrc(imageKey, lessonId) {
    const local = imageKey ? localImagePreviews.get(imageKey) : null;
    if (local) return local;
    if (lessonId == null) return null;
    return `/api/tutor/academy/lessons/${lessonId}/hero-image?v=${encodeURIComponent(imageKey || '')}`;
  }

  function setOtVideo(videoId) {
    const field = form.elements.namedItem('otVideoId');
    if (field) field.value = videoId == null ? '' : String(videoId);
    captureDraft();
    renderAll();
  }

  function currentMedia() {
    const active = stateApi.getActiveItem(editorState);
    const value = active ? active.appliedPreview : {};
    return { imageKey: value.mediaImageKey || null, videoId: value.mediaVideoId ?? null };
  }

  function currentOtVideoId() {
    const active = stateApi.getActiveItem(editorState);
    return active?.appliedPreview?.otVideoId ?? null;
  }

  function renderMedia() {
    // 가로 컬렉션 학원은 미디어도 Program Card 미리보기 안에서 렌더한다
    // (#courseMediaArea/#otMediaArea 는 숨겨져 있다).
    if (horizontalLayout) {
      const active = stateApi.getActiveItem(editorState);
      if (active) renderProgramCardPreview(active.appliedPreview, active);
      return;
    }
    const { imageKey, videoId } = currentMedia();
    renderMediaArea(imageKey, videoId);
    renderOtMedia(currentOtVideoId());
  }

  function renderOtMedia(videoId) {
    if (!otMediaArea) return;
    const hasVideo = videoId != null;
    const found = hasVideo
      ? myVideos.find((video) => String(video.videoId) === String(videoId))
      : null;

    if (found) {
      otMediaArea.innerHTML = `${videoPreviewMarkup(found, 'ot-media-image')}<span class="play-circle" aria-hidden="true">▶</span>`;
      return;
    }
    otMediaArea.innerHTML = `<div class="ot-placeholder">
      <div class="play-circle" aria-hidden="true">▶</div>
      ${hasVideo ? 'OT 영상이 등록되었습니다.' : '수업 OT 영상 영역'}<br>
      <small>${hasVideo ? '저장 후 공개 페이지에 표시됩니다.' : '클릭해 OT 영상을 등록해 주세요.'}</small>
    </div>`;
  }

  function renderMediaArea(imageKey, videoId) {
    if (!mediaArea) return;
    const active = stateApi.getActiveItem(editorState);

    if (videoId != null) {
      const found = myVideos.find((video) => String(video.videoId) === String(videoId));
      mediaArea.innerHTML = found
        ? videoPreviewMarkup(found, 'course-media-image')
        : '<div class="course-media-placeholder">영상이 등록되었습니다.</div>';
      return;
    }
    if (imageKey) {
      // 방금 고른 파일의 로컬 미리보기(blob)가 있으면 그걸 우선 쓴다. 없으면 저장된 수업만
      // lessonId 기반 조회 URL을 만들 수 있다 — 미저장 신규 수업은 안내 문구로 대체한다.
      const src = heroImageSrc(imageKey, active?.lessonId ?? null);
      mediaArea.innerHTML = src
        ? `<img class="course-media-image" src="${escapeAttr(src)}" alt="">`
        : '<div class="course-media-placeholder">이미지가 등록되었습니다.<br>저장하면 미리보기에 표시됩니다.</div>';
      return;
    }
    mediaArea.innerHTML =
      '<div class="course-media-placeholder">대표 교재 사진 또는<br>수업 쇼츠를 등록해 주세요.</div>';
  }

  async function openMediaPicker(target) {
    if (!mediaModal) return;
    mediaTarget = target === 'ot' ? 'ot' : 'hero';
    if (mediaModalTitle) {
      mediaModalTitle.textContent = mediaTarget === 'ot' ? '수업 OT 영상 등록' : '대표 미디어 등록';
    }
    if (mediaModalHint) {
      mediaModalHint.textContent = mediaTarget === 'ot'
        ? '대표 미디어와 별도로 저장되는 OT 영상 1개를 등록합니다.'
        : '이미지 1장 또는 영상 1개만 등록됩니다. 새로 등록하면 기존 대표 미디어는 교체됩니다.';
    }
    if (mediaImageLabel) mediaImageLabel.hidden = mediaTarget === 'ot';
    if (clearSelectedMediaButton) {
      const { imageKey, videoId } = currentMedia();
      const hasSelectedMedia = mediaTarget === 'ot'
        ? currentOtVideoId() != null
        : Boolean(imageKey) || videoId != null;
      clearSelectedMediaButton.hidden = !hasSelectedMedia;
      clearSelectedMediaButton.textContent = mediaTarget === 'ot'
        ? '현재 OT 영상 해제'
        : '현재 대표 미디어 해제';
    }
    setMediaStatus('');
    mediaModal.showModal();
    await loadMyVideos();
  }

  function clearSelectedMedia() {
    if (mediaTarget === 'ot') {
      setOtVideo(null);
    } else {
      setMedia(null, null);
    }
    mediaModal?.close();
  }

  // 수업 삭제/전환 시 그 수업의 로컬 미리보기 blob URL을 정리해 메모리를 남기지 않는다.
  function releaseLocalImagePreviewFor(item) {
    const key = item?.draft?.mediaImageKey;
    if (key) revokeLocalImagePreview(key);
  }

  async function loadMyVideos() {
    if (!mediaVideoList) return;
    try {
      const response = await window.fetchAuth('/api/tutor/academy/videos');
      const videos = response.ok ? await response.json() : [];
      // 원장 소개 영상은 수업상품 미디어 선택 목록에 섞이지 않는다. 용도 미상(레거시) 영상은
      // 어느 쪽이었는지 알 수 없으므로 계속 노출한다(하위 호환, 임의 마이그레이션 없음).
      myVideos = videos.filter((video) => !video.purpose || video.purpose === 'LESSON');
    } catch (error) {
      myVideos = [];
    }
    // 저장된 id만 먼저 렌더된 슬롯도 목록 응답의 썸네일/파일명으로 즉시 갱신한다.
    renderMedia();
    if (!myVideos.length) {
      mediaVideoList.innerHTML = '<p class="media-modal-note">등록된 영상이 없습니다.</p>';
      return;
    }
    mediaVideoList.innerHTML = myVideos.map((video) => `
      <button type="button" class="media-video-item" data-video-id="${escapeAttr(String(video.videoId))}">
        ${videoPreviewMarkup(video, '')}
        <span>${escapeHtml(video.originalFilename || '이름 없음')}</span>
      </button>`).join('');
    mediaVideoList.querySelectorAll('[data-video-id]').forEach((button) => {
      button.addEventListener('click', () => {
        if (mediaTarget === 'ot') {
          setOtVideo(Number(button.dataset.videoId));
        } else {
          setMedia(null, Number(button.dataset.videoId));
        }
        mediaModal?.close();
      });
    });
  }

  async function uploadMedia(event, kind) {
    const input = event.target;
    const file = input.files && input.files[0];
    if (!file) return;
    input.value = '';

    const isImage = kind === 'image';
    if (isImage && mediaTarget === 'ot') {
      setMediaStatus('OT 영역에는 영상만 등록할 수 있습니다.');
      return;
    }
    setMediaStatus(isImage ? '이미지를 처리하는 중…' : '영상을 변환하는 중… 시간이 걸릴 수 있습니다.');
    setBusy(true);
    try {
      const body = new FormData();
      body.append(isImage ? 'image' : 'video', file);
      // 수업상품 편집기에서 올리는 영상은 전부 수업 용도다 — 원장 소개 목록에 섞이지 않게 표시해 둔다.
      if (!isImage) body.append('purpose', 'LESSON');
      const response = await window.fetchAuth(
        isImage ? '/api/tutor/academy/media/lesson-hero' : '/api/tutor/academy/videos',
        { method: 'POST', body });
      const payload = await readJson(response);
      if (!response.ok) {
        setMediaStatus(payload?.message || '업로드에 실패했습니다.');
        return;
      }
      if (isImage) {
        registerLocalImagePreview(payload.imageKey, file);
        setMedia(payload.imageKey, null);
      } else {
        myVideos = [payload].concat(myVideos);
        if (mediaTarget === 'ot') {
          setOtVideo(payload.videoId);
        } else {
          setMedia(null, payload.videoId);
        }
      }
      mediaModal?.close();
    } catch (error) {
      setMediaStatus('업로드 중 오류가 발생했습니다.');
    } finally {
      setBusy(false);
    }
  }

  function setMediaStatus(message) {
    if (!mediaModalStatus) return;
    mediaModalStatus.textContent = message;
    mediaModalStatus.hidden = !message;
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (char) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[char]);
  }

  function escapeAttr(value) {
    return escapeHtml(value);
  }

  function videoPreviewMarkup(video, className) {
    const cssClass = escapeAttr(className || '');
    const videoUrl = escapeAttr(video.videoUrl || '');
    return video.thumbnailUrl
      ? `<img class="${cssClass}" src="${escapeAttr(video.thumbnailUrl)}"
              data-fallback-video="${videoUrl}" alt="">`
      : `<video class="${cssClass}" src="${videoUrl}"
                preload="metadata" muted playsinline aria-label="영상 미리보기"></video>`;
  }

  function onMediaPreviewError(event) {
    const image = event.target.closest('img[data-fallback-video]');
    if (!image) return;
    const previewVideo = document.createElement('video');
    previewVideo.className = image.className;
    previewVideo.src = image.dataset.fallbackVideo || '';
    previewVideo.preload = 'metadata';
    previewVideo.muted = true;
    previewVideo.playsInline = true;
    previewVideo.setAttribute('aria-label', '영상 미리보기');
    image.replaceWith(previewVideo);
  }

  function setPreview(field, value, fallback) {
    preview.querySelectorAll(`[data-preview-field="${field}"]`)
      .forEach((element) => { element.textContent = value || fallback; });
  }

  function renderDirtyState() {
    const active = stateApi.getActiveItem(editorState);
    if (!active) {
      draftState.textContent = '';
      return;
    }
    const view = stateApi.getItemView(active);
    draftState.textContent = view.saveDirty ? '저장할 변경 있음' : '서버와 동일';
  }

  async function saveCollection(event) {
    event.preventDefault();
    clearMessages();
    if (!stateApi.getActiveItem(editorState)) return showError('편집할 수업상품을 선택해 주세요.');
    editorState = stateApi.updateActiveDraft(editorState, readForm());
    const invalid = stateApi.findFirstInvalidSaveItem(editorState);
    if (invalid) {
      activateInvalidItem(invalid);
      return;
    }
    const request = stateApi.buildSaveRequest(editorState);
    if (request.items.length === 0) {
      showStatus('저장할 변경 내용이 없습니다.');
      return;
    }
    setBusy(true);
    try {
      const response = await fetchAuth('/api/tutor/academy/lessons/collection', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
      });
      const payload = await readJson(response);
      if (response.status === 409 && payload.code === 'LESSON_AUTHORING_STALE') {
        await recoverFromStaleConflict(request.items);
        return;
      }
      if (!response.ok) throw collectionError(response.status, payload);
      editorState = stateApi.reconcileSaveSuccess(editorState, payload);
      renderAll();
      showStatus(`수업상품 ${payload.requestedCount || request.items.length}개를 저장했습니다.`);
      alert('수업상품을 저장했습니다.');
      location.assign('/tutor/academy');
    } catch (error) {
      showError(error.message || '저장하지 못했습니다. 입력 내용은 그대로 유지됩니다.');
    } finally {
      setBusy(false);
    }
  }

  function activateInvalidItem(invalid) {
    const item = editorState.items.find((candidate) => candidate.clientKey === invalid.clientKey);
    if (item && item.clientKey !== editorState.activeKey) {
      editorState = stateApi.selectItem(editorState, item.clientKey, readForm());
      renderAll();
    }
    showError(`${tabName(item)}: ${invalid.message}`);
    const field = document.getElementById(invalid.field);
    field?.focus();
    field?.reportValidity?.();
  }

  async function recoverFromStaleConflict(submittedItems) {
    try {
      const response = await fetchAuth('/api/tutor/academy/me');
      if (!response.ok || response.status === 204) throw new Error();
      const academy = await response.json();
      const latestIds = new Set((academy.lessons || []).map((lesson) => String(lesson.lessonId)));
      const missing = submittedItems
        .filter((item) => item.lessonId != null && !latestIds.has(String(item.lessonId)));
      editorState = stateApi.rebaseServerState(editorState, academy.lessons || []);
      renderAll();
      if (missing.length > 0) {
        showError('다른 곳에서 숨김 또는 삭제된 수업상품이 있습니다. 로컬 입력은 유지했습니다. Academy 목록에서 상태를 확인해 주세요.');
        return;
      }
      showError('다른 곳의 최신 변경을 서버 기준으로 다시 불러왔습니다. 로컬 입력과 미리보기는 유지했습니다. 각 탭을 검토한 뒤 다시 저장해 주세요.');
    } catch (_) {
      showError('최신 서버 상태를 불러오지 못했습니다. 로컬 입력은 유지했습니다. 잠시 후 다시 시도해 주세요.');
    }
  }

  function updateSaveLabel() {
    saveButton.textContent = editorState.items.length > 0
        && editorState.items.every((item) => item.lessonId == null)
      ? '수업상품 생성'
      : '수업상품 저장';
  }

  function setBusy(busy) {
    saveButton.disabled = busy || !stateApi.getActiveItem(editorState);
    deleteButton.disabled = busy || !stateApi.getActiveItem(editorState);
    addButton.disabled = busy;
    form.setAttribute('aria-busy', String(busy));
  }

  function clearMessages() {
    statusBox.textContent = '';
    errorBox.textContent = '';
    errorBox.hidden = true;
  }

  function showStatus(message) {
    statusBox.textContent = message;
  }

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  async function readJson(response) {
    try { return await response.json(); } catch (_) { return {}; }
  }

  function collectionError(status, payload) {
    if (status === 409 && payload.retryable) {
      return new Error('동시에 저장 중인 변경이 있습니다. 잠시 후 다시 저장해 주세요. 입력 내용은 유지됩니다.');
    }
    return new Error(payload.message || '저장하지 못했습니다. 입력값을 확인해 주세요.');
  }

  function priceFallback(value) {
    if (value.priceUnit === 'NEGOTIABLE') return '가격 협의';
    if (value.priceAmount == null) return '-';
    const amount = `${Number(value.priceAmount).toLocaleString('ko-KR')}원`;
    if (value.priceUnit === 'MONTH') return `${amount} / 월`;
    if (value.priceUnit === 'COURSE') return `${amount} / 강좌`;
    // Collection에 남아 있는 legacy positive/null은 비가격 편집 시 그대로 표시·왕복한다.
    return amount;
  }

  function label(value) {
    return ({
      SUBSCRIPTION: '정규 수업', SHORT_TERM: '단기 특강',
      ELEMENTARY: '초등학생', MIDDLE: '중학생', HIGH: '고등학생',
      UNIVERSITY: '대학생', ADULT: '성인'
    }[value]) || value || '';
  }

  function tabName(item) {
    if (!item) return '수업상품';
    return item.draft.tabShortName || item.draft.name || '새 수업상품';
  }

  function cssEscape(value) {
    return window.CSS && typeof window.CSS.escape === 'function'
      ? window.CSS.escape(value)
      : String(value).replace(/(["\\])/g, '\\$1');
  }
})();
