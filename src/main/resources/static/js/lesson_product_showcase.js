(function (global) {
  'use strict';

  const THEMES = new Set(['NAVY', 'TEAL', 'PURPLE', 'ORANGE', 'SKY', 'PINK', 'GREEN']);
  let showcaseSequence = 0;

  function render(items, options) {
    const config = options || {};
    if (!Array.isArray(items) || !items.length) {
      return `<p class="lesson-empty">${escapeHtml(config.emptyMessage || '아직 등록된 수업이 없습니다.')}</p>`;
    }

    showcaseSequence += 1;
    const showcaseId = `lesson-product-showcase-${showcaseSequence}`;
    const lessons = items.map((lesson) => lesson || {});
    const tabs = lessons.map((lesson, index) => tab(lesson, showcaseId, index)).join('');
    const panels = lessons.map((lesson, index) => panel(lesson, showcaseId, index, config)).join('');

    // 탭은 처음엔 닫힌 상태라 클릭 가능하다는 신호가 없다. 탭 위에 안내 문구를 고정으로 둔다.
    return `<div id="${showcaseId}" class="lesson-product-showcase" data-active-index="" data-theme="${normalizedTheme(lessons[0].theme)}">
      <p class="lesson-product-tab-hint"><span aria-hidden="true">👆</span> 탭을 클릭하면 수업 상세 내용이 펼쳐집니다.</p>
      <nav class="course-nav lesson-product-nav" aria-label="수업상품 선택" style="--course-tab-columns:${lessons.length}">${tabs}</nav>
      <div class="lesson-product-collapse">
        <div class="lesson-product-collapse-inner">${panels}</div>
      </div>
    </div>`;
  }

  function tab(lesson, showcaseId, index) {
    const panelId = `${showcaseId}-panel-${index}`;
    return `<button class="course-tab lesson-product-tab" type="button" data-lesson-showcase-tab data-lesson-index="${index}" data-theme="${normalizedTheme(lesson.theme)}" aria-expanded="false" aria-controls="${panelId}">
      <small>${escapeHtml(textOr(lesson.tabGuidanceLabel, lesson.scheduleSummary, targetGroupLabel(lesson.targetGroup)))}</small>
      <strong>${escapeHtml(textOr(lesson.tabShortName, lesson.name, '수업상품'))}</strong>
    </button>`;
  }

  function targetGroupLabel(value) {
    const target = String(value || '').toUpperCase();
    const labels = {
      ELEMENTARY: '초등학생',
      MIDDLE: '중학생',
      HIGH: '고등학생',
      UNIVERSITY: '대학생',
      ADULT: '성인'
    };
    return labels[target] || '';
  }

  /**
   * 수업 상세. 수업 관리(/tutor/academy/lessons/editor)의 미리보기(.preview)와 같은 골격이다
   * — 히어로(selected-hero) + 상세(detail-section). 두 화면이 갈라지면 원장이 편집기에서 본
   * 결과와 공개 페이지가 달라지므로 클래스 이름까지 편집기와 맞춘다.
   */
  function panel(lesson, showcaseId, index, options) {
    const config = options || {};
    const explanations = [lesson.explanation1, lesson.explanation2, lesson.explanation3].filter(hasText);
    const hasHeading = hasText(lesson.learnerNeed) || hasText(lesson.coreDetailTitle);
    const otMedia = otVideoBlock(lesson, config);
    const explanationMarkup = explanations.length
      ? `<div class="explain-list lesson-product-explain-list">${explanations.map((value) => `<div class="explain-card">${escapeHtml(value)}</div>`).join('')}</div>`
      : '';
    const hasDetail = hasHeading || explanations.length || otMedia;

    // 처음에는 모든 패널이 닫혀 있다. 여는 것은 탭 클릭(toggle)뿐이다.
    return `<article id="${showcaseId}-panel-${index}" class="lesson-product-panel" data-lesson-panel="${index}" aria-hidden="true" hidden>
      <section class="selected-hero lesson-product-selected-hero">
        <div class="hero-copy">
          ${optionalText('hero-kicker', lesson.kicker)}
          <h1 class="hero-title"><span class="accent">${escapeHtml(textOr(lesson.tabShortName, lesson.name, '수업상품'))}</span><br><span>${escapeHtml(textOr(lesson.name, '수업상품'))}</span></h1>
          ${optionalText('hero-summary', lesson.description)}
          ${optionalText('hero-highlight', lesson.highlight)}
        </div>
        ${mediaBlock(lesson, config)}
      </section>
      ${hasDetail ? `<section class="detail-section lesson-product-detail-section">
        ${hasHeading ? `<div class="detail-heading">
          ${optionalText('lesson-product-learner-need', lesson.learnerNeed)}
          ${hasText(lesson.coreDetailTitle) ? `<h2><span>${escapeHtml(lesson.coreDetailTitle)}</span><br><span>${escapeHtml(textOr(lesson.tabShortName, lesson.name, '수업상품'))}</span></h2>` : ''}
        </div>` : ''}
        ${otMedia ? `<div class="lesson-product-ot-explain">${otMedia}${explanationMarkup}</div>` : explanationMarkup}
      </section>` : ''}
    </article>`;
  }

  /**
   * 히어로 대표 미디어.
   *
   * <p>영상은 썸네일 + ▶ 버튼만 그린다. 초기 렌더에 스트림 URL을 넣으면 탭을 열기만 해도
   * 서버가 mp4 전량을 힙에 올려 보내고, 동시 전송 상한(4)이 금방 마른다. 클릭 시에만 붙인다.
   *
   * <p>미디어가 없으면 빈 문자열이다. 빈 박스를 그리면 미등록 수업이 깨져 보인다.
   */
  function mediaBlock(lesson, config) {
    if (lesson.mediaType === 'VIDEO' && lesson.mediaVideoId != null && config.slug) {
      const base = `/api/academies/${encodeURIComponent(config.slug)}/videos/${encodeURIComponent(lesson.mediaVideoId)}`;
      return `<div class="course-media-area">
        <button type="button" class="course-media-video" data-lesson-video-src="${escapeHtml(base + '/stream')}" aria-label="수업 영상 재생">
          <img class="course-media-image" src="${escapeHtml(base + '/thumbnail')}" alt="" loading="lazy">
          <span class="play-circle" aria-hidden="true">▶</span>
        </button>
      </div>`;
    }
    if (lesson.mediaType === 'IMAGE' && lesson.lessonId != null) {
      // 저장소 key는 응답에 없다. 이미지도 전용 엔드포인트로만 받는다.
      const src = config.heroImageBase
        ? `${config.heroImageBase}/${encodeURIComponent(lesson.lessonId)}/hero-image`
        : (config.slug
            ? `/api/academies/${encodeURIComponent(config.slug)}/lessons/${encodeURIComponent(lesson.lessonId)}/hero-image`
            : null);
      if (!src) return '';
      return `<div class="course-media-area">
        <img class="course-media-image" src="${escapeHtml(src)}" alt="" loading="lazy">
      </div>`;
    }
    return '';
  }

  /** 대표 미디어와 별도 저장되는 수업 OT 영상. */
  function otVideoBlock(lesson, config) {
    if (lesson.otVideoId == null || !config.slug) return '';
    const base = `/api/academies/${encodeURIComponent(config.slug)}/videos/${encodeURIComponent(lesson.otVideoId)}`;
    return `<button type="button" class="course-media-video lesson-product-ot-video" data-lesson-video-src="${escapeHtml(base + '/stream')}" aria-label="수업 OT 영상 재생">
      <img class="course-media-image" src="${escapeHtml(base + '/thumbnail')}" alt="" loading="lazy">
      <span class="play-circle" aria-hidden="true">▶</span>
    </button>`;
  }

  /**
   * 탭 클릭 하나로 열고 닫는다.
   *
   * <p>닫힌 상태에서 탭을 누르면 그 패널이 열리고, 열린 탭을 다시 누르면 통째로 닫힌다.
   * 다른 탭을 누르면 닫지 않고 패널 내용만 바꾼다.
   */
  function toggle(button, index) {
    const showcase = button && button.closest ? button.closest('.lesson-product-showcase') : null;
    if (!showcase) return;

    const wasExpanded = showcase.classList.contains('is-expanded');
    const isSameTab = wasExpanded && showcase.dataset.activeIndex === String(index);
    const tabs = showcase.querySelectorAll('[data-lesson-showcase-tab]');
    const panels = showcase.querySelectorAll('[data-lesson-panel]');

    if (isSameTab) {
      showcase.classList.remove('is-expanded');
      showcase.dataset.activeIndex = '';
      tabs.forEach((tabButton) => {
        tabButton.classList.remove('active');
        tabButton.setAttribute('aria-expanded', 'false');
      });
      panels.forEach((lessonPanel) => lessonPanel.setAttribute('aria-hidden', 'true'));
      return;
    }

    tabs.forEach((tabButton) => {
      const active = tabButton.dataset.lessonIndex === String(index);
      tabButton.classList.toggle('active', active);
      tabButton.setAttribute('aria-expanded', active ? 'true' : 'false');
    });
    panels.forEach((lessonPanel) => {
      const active = lessonPanel.dataset.lessonPanel === String(index);
      lessonPanel.hidden = !active;
      lessonPanel.setAttribute('aria-hidden', active ? 'false' : 'true');
    });
    showcase.dataset.activeIndex = String(index);
    showcase.dataset.theme = button.dataset.theme || 'NAVY';

    if (!wasExpanded) {
      showcase.classList.add('is-expanded');
    }
  }

  function wire() {
    if (document.documentElement.dataset.lessonProductShowcaseWired === 'true') return;
    document.documentElement.dataset.lessonProductShowcaseWired = 'true';
    document.addEventListener('click', (event) => {
      const button = event.target && event.target.closest
        ? event.target.closest('[data-lesson-showcase-tab]')
        : null;
      if (!button) return;
      event.preventDefault();
      event.stopPropagation();
      toggle(button, Number(button.dataset.lessonIndex));
    }, true);

    // 영상은 클릭 시점에만 스트림을 붙인다. 초기 렌더에 넣으면 탭을 열기만 해도 서버가
    // mp4 전량을 힙에 올리고 동시 전송 상한이 마른다.
    document.addEventListener('click', (event) => {
      const trigger = event.target && event.target.closest
        ? event.target.closest('[data-lesson-video-src]')
        : null;
      if (!trigger) return;
      event.preventDefault();
      const video = document.createElement('video');
      video.className = 'course-media-video-player';
      video.src = trigger.dataset.lessonVideoSrc;
      video.controls = true;
      video.autoplay = true;
      video.playsInline = true;
      trigger.replaceWith(video);
    });
  }

  function optionalText(className, value) {
    return hasText(value) ? `<p class="${className}">${escapeHtml(value)}</p>` : '';
  }

  function normalizedTheme(value) {
    const theme = String(value || 'NAVY').toUpperCase();
    return THEMES.has(theme) ? theme : 'NAVY';
  }

  function textOr() {
    for (let index = 0; index < arguments.length; index += 1) {
      if (hasText(arguments[index])) return String(arguments[index]).trim();
    }
    return '';
  }

  function hasText(value) {
    return value != null && String(value).trim() !== '';
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  wire();
  global.F2RLessonProductShowcase = Object.freeze({ render, toggle });
})(window);
