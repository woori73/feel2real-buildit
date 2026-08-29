/**
 * 학원 공개 페이지 섹션 렌더 — 공개(academy_public.js)와 빌더 캔버스(academy_builder.js)가
 * 공유하는 단일 정본.
 *
 * 왜 공용인가
 *   두 파일이 같은 섹션 HTML을 각자 하드코딩하고 있었다. 한쪽만 고치면 빌더 캔버스와
 *   실제 발행 결과가 갈라진다. 마크업을 한 벌만 두어 그 드리프트를 구조적으로 없앤다.
 *
 * 두 화면의 차이를 다루는 방법 (ctx 슬롯 주입)
 *   차이는 "편집 앵커 속성"만이 아니다. 실측하면 최소 다섯 종류다.
 *     1) 엘리먼트 타입 — 소셜 카드가 공개는 <a href>, 빌더는 <div role="button">
 *     2) 인터랙션    — 영상 재생목록이 공개는 <button>, 빌더는 <div>
 *     3) 자식 노드   — 특징/약력 항목에 빌더만 추가·삭제 버튼
 *     4) 폴백 정책   — 업로드 영상을 못 찾았을 때 공개는 stream URL 합성, 빌더는 null
 *     5) 빈 문구     — "대표 수업 준비 중입니다" vs "클릭해 대표 수업을 선택하세요"
 *   그래서 렌더 함수 안에 모드 분기를 흩뿌리는 대신, 분기를 ctx 구현 2벌에 모은다.
 *   렌더 함수는 ctx가 무엇을 돌려주는지 모른 채 자리만 비워둔다.
 *
 * ⚠ 편집 앵커를 항상 출력하는 방식은 쓰지 않는다. 공개 페이지가 무시할 수 있는 것은
 *   속성이지 태그가 아니다 — 소셜 카드를 <div role="button">으로 내보내면 발행 페이지의
 *   네이버 블로그·지도 링크가 클릭 불가가 되고, role/tabindex/title은 CSS로 지울 수 없어
 *   방문자에게 "클릭하여 원장 대표사진을 등록하세요, 버튼"이 읽힌다.
 */
(function (global) {
  'use strict';

  // 렌더 대상이 아닌 예정 섹션. 저장값이 ON이어도 그리지 않는다.
  const FUTURE_SECTION_IDS = new Set([
    'sec-reviews',
    'sec-curriculum',
    'sec-effect',
    'sec-market',
    'sec-payment'
  ]);

  // Education Storefront 신규 섹션. 기존 사이트에는 이 키가 없으므로, 명시적 true일 때만 렌더한다
  // (기존 섹션은 반대로 명시적 false일 때만 숨긴다). 이 opt-in 규칙 덕분에 새 섹션을
  // DEFAULT_SECTION_ORDER에 추가해도 기존 학원 공개 페이지의 모습이 그대로 유지된다.
  const OPT_IN_SECTION_IDS = new Set([
    'sec-featured',
    'sec-digitaltwin'
  ]);

  // ── 공용 헬퍼 ──────────────────────────────────────────────────────────────
  // String(value || '')을 쓰면 숫자 0과 false가 빈 문자열로 지워진다. null/undefined만 거른다.
  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /** 속성값 전용 — 템플릿 리터럴 백틱까지 막는다. */
  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }

  /** 스킴 가드 — http/https만 통과시킨다(javascript:/data: 차단). 서버 검증과 다층 방어. */
  function safeUrl(url) {
    return /^https?:\/\//i.test(String(url || '')) ? url : null;
  }

  /**
   * 두 호출자의 인자 형태 차이를 흡수한다.
   * 공개는 AcademyResponse를, 빌더는 currentPreview를 넘기는데 가시성 저장 위치가
   * (preview.sectionVisibility / pageSettings.sectionVisibility) 서로 다르다.
   */
  function toSectionView(source, extra) {
    const academy = source || {};
    const options = extra || {};
    const pageSettings = options.pageSettings || academy.pageSettings || {};
    const visibility = options.sectionVisibility
      || academy.sectionVisibility
      || pageSettings.sectionVisibility
      || {};
    return Object.assign(Object.create(null), academy, {
      pageSettings,
      builderDraft: pageSettings.builderDraft || {},
      sectionVisible(id) {
        if (FUTURE_SECTION_IDS.has(id)) return false;
        if (OPT_IN_SECTION_IDS.has(id)) return !!(visibility && visibility[id] === true);
        return !(visibility && visibility[id] === false);
      }
    });
  }

  // ── sec-social ────────────────────────────────────────────────────────────
  const SOCIAL_CARDS = [
    { type: 'blog', field: 'blogUrl', label: '블로그 URL', icon: '📷', headerClass: 'blog', modifier: 'is-blog', title: '네이버 블로그', subtitle: '학원 공식 블로그' },
    { type: 'map', field: 'naverMapUrl', label: '지도 URL', icon: '📍', headerClass: 'store', modifier: 'is-map', title: '네이버 지도', subtitle: '방문 안내 · 상담 예약' }
  ];

  function socialCardUrl(pageSettings, card) {
    // 지도는 legacy storeUrl에 저장된 값도 계속 읽는다.
    return card.type === 'map'
      ? (pageSettings.naverMapUrl || pageSettings.storeUrl)
      : pageSettings[card.field];
  }

  function socialPreviewCard(ctx, pageSettings, card) {
    const url = safeUrl(socialCardUrl(pageSettings, card));
    const cardClass = `social-card social-preview-card ${card.modifier}`;
    const header = `<div class="social-card-header ${card.headerClass}"><div class="sc-ico">${card.icon}</div><div style="flex:1;"><div class="sc-title">${card.title}</div><div class="sc-sub">${card.subtitle}</div></div></div>`;
    return ctx.wrapSocialCard({ cardClass, header, url, field: card.field, label: card.label });
  }

  // 제목의 raw `&`는 추출 전 두 렌더러가 쓰던 그대로다. A-2는 동작 변화 0이 계약이라 유지한다.
  // HTML 문법상 &amp;가 맞지만 시안 마크업 교체(Phase C) 때 함께 정리한다.
  function renderExternalServices(view, ctx) {
    if (!view.sectionVisible('sec-social')) return '';
    const pageSettings = view.pageSettings;
    return `<section id="sec-social" data-builder-section-key="sec-social" class="section-wrap" style="background:#f8faff;" data-fade>
        <div class="section-inner">
          <div class="section-header"><h2 class="section-title">최신 소식 & 방문 안내</h2></div>
          <div class="social-grid">
            ${SOCIAL_CARDS.map((card) => socialPreviewCard(ctx, pageSettings, card)).join('\n            ')}
          </div>
        </div>
      </section>`;
  }

  /** builderDraft에서 값을 읽되 비어 있으면 fallback. 두 렌더러가 쓰던 구현과 동일하다. */
  function draftText(view, key, fallback) {
    const draft = view && view.pageSettings && view.pageSettings.builderDraft;
    const value = draft && draft[key];
    return value == null || value === '' ? fallback : value;
  }

  // ── sec-intro ─────────────────────────────────────────────────────────────
  // BuildIt_3 시안에서 기존 "학원 소개" 자리에 대응하는 것은 notes snap(교육 노트)이다.
  // 별도 블로그/칼럼 테이블이 아직 없으므로 builderDraft의 기존 intro* / introFeatures 저장소를
  // 재사용해 공개/빌더 모두 같은 정적 노트 레이아웃을 렌더한다.
  const INTRO_KEYWORD_DEFAULT = '📖 COLUMN';
  const INTRO_TITLE_DEFAULT = '000 원장의\n교육 노트';
  const INTRO_LEAD_DEFAULT = '교육은 점수를 만드는 일이 아니라\n학생의 가능성을 발견하는 일입니다.';
  const INTRO_FEATURE_DEFAULT = [
    '학생의 마음을 먼저 읽어야 하는 이유|성적보다 먼저 봐야 할 것은 아이의 마음입니다.',
    '감으로 푸는 문법, 왜 위험한가|정확한 구조 이해 없이는 실력이 쌓이지 않습니다.',
    '산만한 아이, 문제는 집중력이 아니다|몰입은 훈련이 아니라 관계에서 시작됩니다.'
  ];

  /** 학원 특징 — builderDraft.introFeatures는 개행 결합 문자열이다(구버전 배열도 허용). */
  function introFeatureList(view) {
    const draft = view && view.pageSettings && view.pageSettings.builderDraft;
    const raw = draft && draft.introFeatures;
    const arr = Array.isArray(raw) ? raw : (typeof raw === 'string' ? raw.split('\n') : []);
    const list = arr.map((v) => (typeof v === 'string' ? v.trim() : '')).filter(Boolean);
    return list.length ? list : INTRO_FEATURE_DEFAULT.slice();
  }


  function maskedIntroTitle(value) {
    const text = String(value || '');
    return text.replace(/박정민 원장의/g, '000 원장의').replace(/박정민 원장이/g, '000 원장이');
  }

  function renderIntro(view, ctx) {
    if (!view.sectionVisible('sec-intro')) return '';
    const keyword = draftText(view, 'introHeading', INTRO_KEYWORD_DEFAULT);
    const title = maskedIntroTitle(draftText(view, 'introMotto', INTRO_TITLE_DEFAULT));
    const lead = draftText(view, 'introBodySecondary', INTRO_LEAD_DEFAULT);
    return `<section id="sec-intro" data-builder-section-key="sec-intro" class="section-wrap intro-wrap notes snap" data-fade>
        <div class="notes-head" data-fade>
          <div class="eyebrow notes-eyebrow"><span class="stroke"></span>${ctx.editableText({ tag: 'span', className: 'notes-keyword', draftKey: 'introHeading', value: keyword, omitWhenEmpty: true })}</div>
          ${ctx.editableText({ tag: 'h2', className: 'section-head notes-title', draftKey: 'introMotto', value: title, builderClass: 'builder-multiline-text', omitWhenEmpty: true })}
          ${ctx.editableText({ tag: 'blockquote', className: 'notes-quote', draftKey: 'introBodySecondary', value: lead, builderClass: 'builder-multiline-text', quoted: true })}
        </div>
        <div class="notes-track intro-feature-list"${ctx.introFeatureListAttrs()}>${introFeatureList(view).map((text, index) => ctx.introFeatureItem(text, index)).join('')}</div>
        ${ctx.introFooter()}
      </section>`;
  }


  const PHILOSOPHY_KEYWORD_DEFAULT = '학습 철학';
  const PHILOSOPHY_TITLE_DEFAULT = '000 원장이\n가르치는 방식';
  const PHILOSOPHY_ITEMS = Object.freeze([
    '주의력이 산만한 학생도 몰입하는 1:1 마음 코칭',
    '공식 암기가 아닌, 문장 구조를 직접 조립하는 F2R 영문법',
    '실수를 실력으로, 지역 내신 밀착 케어',
    '아이 성향에 맞춘 어휘 확장부터 수능 1등급 독해 체력까지'
  ]);

  function philosophyItems(view) {
    return PHILOSOPHY_ITEMS.map((fallback, index) => draftText(view, 'philosophyItem' + (index + 1), fallback));
  }

  function renderPhilosophy(view, ctx) {
    if (!view.sectionVisible('sec-philosophy')) return '';
    const keyword = draftText(view, 'philosophyKeyword', PHILOSOPHY_KEYWORD_DEFAULT);
    const title = maskedIntroTitle(draftText(view, 'philosophyTitle', PHILOSOPHY_TITLE_DEFAULT));
    return `<section id="sec-philosophy" data-builder-section-key="sec-philosophy" class="section-wrap philosophy snap" data-fade>
        <div class="philosophy-inner">
          <div class="section-head philosophy-head" data-fade>
            <div class="eyebrow philosophy-eyebrow"><span class="stroke"></span>${ctx.editableText({ tag: 'span', className: 'philosophy-keyword', draftKey: 'philosophyKeyword', value: keyword, omitWhenEmpty: true })}</div>
            ${ctx.editableText({ tag: 'h2', className: 'philosophy-title', draftKey: 'philosophyTitle', value: title, builderClass: 'builder-multiline-text', omitWhenEmpty: true })}
          </div>
          <div class="philosophy-grid">${philosophyItems(view).map((item, index) => ctx.philosophyCard(item, index)).join('')}</div>
        </div>
      </section>`;
  }

  // ── sec-featured : Featured / Seasonal Program ────────────────────────────
  // 재사용 가능한 generic 섹션. "지금 이 교육자가 가장 알리고 싶은 프로그램/시즌 캠페인"을
  // Shopify Featured처럼 강한 hierarchy(eyebrow → 큰 제목 → 짧은 설명 → 항목 → 단일 CTA)로 보여준다.
  // 학교명·연도·학원명은 전부 builderDraft 입력값이며 코드에 하드코딩하지 않는다.
  const FEATURED_EYEBROW_DEFAULT = 'FEATURED';
  const FEATURED_TITLE_DEFAULT = '이번 시즌\n가장 중요한 프로그램';
  const FEATURED_DESC_DEFAULT = '지금 모집·진행 중인 대표 프로그램을 한눈에 보여주세요. 대상, 시기, 핵심 포인트를 짧게 적으면 됩니다.';
  const FEATURED_ITEM_DEFAULTS = ['핵심 포인트 1', '핵심 포인트 2', '핵심 포인트 3', '핵심 포인트 4'];
  const FEATURED_CTA_DEFAULT = '상담 신청하기';

  function featuredItems(view) {
    return FEATURED_ITEM_DEFAULTS.map((fallback, index) => draftText(view, 'featuredItem' + (index + 1), fallback));
  }

  function renderFeatured(view, ctx) {
    if (!view.sectionVisible('sec-featured')) return '';
    const eyebrow = draftText(view, 'featuredEyebrow', FEATURED_EYEBROW_DEFAULT);
    const title = draftText(view, 'featuredTitle', FEATURED_TITLE_DEFAULT);
    const desc = draftText(view, 'featuredDescription', FEATURED_DESC_DEFAULT);
    const ctaLabel = draftText(view, 'featuredCtaLabel', FEATURED_CTA_DEFAULT);
    return `<section id="sec-featured" data-builder-section-key="sec-featured" class="section-wrap featured-wrap" data-fade>
        <div class="section-inner featured-inner">
          <div class="featured-head">
            <div class="eyebrow featured-eyebrow"><span class="stroke"></span>${ctx.editableText({ tag: 'span', className: 'featured-eyebrow-text', draftKey: 'featuredEyebrow', value: eyebrow, omitWhenEmpty: true })}</div>
            ${ctx.editableText({ tag: 'h2', className: 'featured-title', draftKey: 'featuredTitle', value: title, builderClass: 'builder-multiline-text', omitWhenEmpty: true })}
            ${ctx.editableText({ tag: 'p', className: 'featured-desc', draftKey: 'featuredDescription', value: desc, publicClass: 'academy-multiline-text', builderClass: 'builder-multiline-text', omitWhenEmpty: true })}
          </div>
          <div class="featured-items">${featuredItems(view).map((item, index) => ctx.featuredItem(item, index)).join('')}</div>
          <div class="featured-cta">${ctx.featuredCta(ctaLabel)}</div>
        </div>
      </section>`;
  }

  // ── sec-digitaltwin : AI Native Education Process ─────────────────────────
  // 섹션 id는 기존 예약값 sec-digitaltwin을 그대로 쓴다(순서/저장 호환). 표시 문구는 특정
  // 학원 전용이 아니라, 어떤 교육자든 "자료 → 분석 → 수업 인사이트 → 제작 → 완성"류의 자기
  // AI Native 교육 과정을 단계 흐름으로 표현할 수 있는 generic 섹션이다. 공개 화면에는
  // 개발 용어(RAG/LLM/Embedding 등)를 쓰지 않는다 — 입력값은 교육자가 직접 쓴 문장이다.
  const AI_PROCESS_EYEBROW_DEFAULT = 'AI NATIVE';
  const AI_PROCESS_TITLE_DEFAULT = '경험 있는 교육자 + AI';
  const AI_PROCESS_DESC_DEFAULT = '오랜 현장 경험을 AI가 기록·정리·확장해 실제 수업과 시험 준비의 정밀도를 높입니다. 우리 교육 과정을 단계로 보여주세요.';
  const AI_PROCESS_STEP_DEFAULTS = ['자료 수집', '경향 분석', '수업 인사이트', '자료 제작', '최종본'];
  const AI_PROCESS_NOTE_DEFAULT = 'AI는 교육자를 대체하지 않습니다. 교육자의 판단과 노하우를 넓히는 도구로 씁니다.';

  function aiProcessSteps(view) {
    return AI_PROCESS_STEP_DEFAULTS.map((fallback, index) => draftText(view, 'aiProcessStep' + (index + 1), fallback));
  }

  function renderAiProcess(view, ctx) {
    if (!view.sectionVisible('sec-digitaltwin')) return '';
    const eyebrow = draftText(view, 'aiProcessEyebrow', AI_PROCESS_EYEBROW_DEFAULT);
    const title = draftText(view, 'aiProcessTitle', AI_PROCESS_TITLE_DEFAULT);
    const desc = draftText(view, 'aiProcessDescription', AI_PROCESS_DESC_DEFAULT);
    const note = draftText(view, 'aiProcessNote', AI_PROCESS_NOTE_DEFAULT);
    return `<section id="sec-digitaltwin" data-builder-section-key="sec-digitaltwin" class="section-wrap ai-process-wrap" data-fade>
        <div class="section-inner ai-process-inner">
          <div class="section-header ai-process-head">
            <div class="eyebrow ai-process-eyebrow"><span class="stroke"></span>${ctx.editableText({ tag: 'span', className: 'ai-process-eyebrow-text', draftKey: 'aiProcessEyebrow', value: eyebrow, omitWhenEmpty: true })}</div>
            ${ctx.editableText({ tag: 'h2', className: 'section-title ai-process-title', draftKey: 'aiProcessTitle', value: title, builderClass: 'builder-multiline-text', omitWhenEmpty: true })}
            ${ctx.editableText({ tag: 'p', className: 'ai-process-desc', draftKey: 'aiProcessDescription', value: desc, publicClass: 'academy-multiline-text', builderClass: 'builder-multiline-text', omitWhenEmpty: true })}
          </div>
          <ol class="ai-process-steps">${aiProcessSteps(view).map((step, index) => ctx.aiProcessStep(step, index)).join('')}</ol>
          ${ctx.editableText({ tag: 'p', className: 'ai-process-note', draftKey: 'aiProcessNote', value: note, publicClass: 'academy-multiline-text', builderClass: 'builder-multiline-text', quoted: false, omitWhenEmpty: true })}
        </div>
      </section>`;
  }


  function noteParts(value, index) {
    const fallbackDates = ['2026.07.28', '2026.07.15', '2026.06.30'];
    const raw = String(value || '').trim();
    const parts = raw.split('|').map((part) => part.trim());
    if (parts.length >= 3) return { date: parts[0] || fallbackDates[0], title: parts[1] || '교육 노트', body: parts.slice(2).join('|') };
    const fallbackDate = fallbackDates[Math.max(0, Number.isInteger(index) ? index : Math.abs(hashCode(raw))) % fallbackDates.length];
    return { date: fallbackDate, title: parts[0] || '교육 노트', body: parts[1] || '' };
  }

  function hashCode(value) {
    let hash = 0;
    for (let index = 0; index < value.length; index += 1) hash = ((hash << 5) - hash) + value.charCodeAt(index);
    return hash | 0;
  }

  // ── YouTube 링크 ──────────────────────────────────────────────────────────
  function validYoutubeId(value) {
    return /^[A-Za-z0-9_-]{11}$/.test(String(value || '')) ? value : null;
  }

  function youtubeVideoId(url) {
    try {
      const parsed = new URL(String(url || '').trim());
      const host = parsed.hostname.replace(/^www\./, '').toLowerCase();
      if (host === 'youtu.be') return validYoutubeId(parsed.pathname.split('/').filter(Boolean)[0]);
      if (host === 'youtube.com' || host === 'm.youtube.com' || host === 'music.youtube.com') {
        if (parsed.pathname === '/watch') return validYoutubeId(parsed.searchParams.get('v'));
        const parts = parsed.pathname.split('/').filter(Boolean);
        if (parts[0] === 'embed' || parts[0] === 'shorts' || parts[0] === 'live') return validYoutubeId(parts[1]);
      }
    } catch (_) {
      return null;
    }
    return null;
  }

  function youtubeEmbedUrl(url) {
    const videoId = youtubeVideoId(url);
    return videoId ? 'https://www.youtube.com/embed/' + videoId : null;
  }

  /** 업로드 영상 플레이어. 네 값 모두 속성값이라 escapeAttr을 쓴다. */
  function uploadedVideoPlayer(video, className, title) {
    const poster = video.thumbnailUrl ? ` poster="${escapeAttr(video.thumbnailUrl)}"` : '';
    return `<video class="${escapeAttr(className)}" controls playsinline preload="metadata"${poster} src="${escapeAttr(video.videoUrl || '')}" aria-label="${escapeAttr(title)}"></video>`;
  }

  // ── sec-principal ─────────────────────────────────────────────────────────
  // 실제 인물 정보 대신 학원장이 직접 채우도록 안내하는 마스킹 placeholder를 둔다.
  const PRINCIPAL_CAREER_DEFAULT = [
    '주요 학력을 입력해 주세요 (예: ○○대학교 영어교육 전공)',
    '주요 경력을 입력해 주세요 (예: 영어 교육 ○년)',
    '자격·수상 이력을 입력해 주세요',
    '지도 분야를 입력해 주세요 (예: 내신·수능 영어)'
  ];

  /** 약력 — builderDraft.principalCareer는 개행 결합 문자열이다(구버전 배열도 허용). */
  function principalCareerList(view) {
    const draft = view && view.pageSettings && view.pageSettings.builderDraft;
    const raw = draft && draft.principalCareer;
    const arr = Array.isArray(raw) ? raw : (typeof raw === 'string' ? raw.split('\n') : []);
    const list = arr.map((v) => (typeof v === 'string' ? v.trim() : '')).filter(Boolean);
    return list.length ? list : PRINCIPAL_CAREER_DEFAULT.slice();
  }

  function renderPrincipalVideo(view, ctx) {
    const embedUrl = youtubeEmbedUrl(draftText(view, 'principalYoutubeUrl', ''));
    const uploadedVideo = ctx.findUploadedVideo(draftText(view, 'principalVideoId', ''));
    // 가로(16:9)가 기본, Shorts 링크면 세로(9:16). orientation은 입력 시점에 감지해 저장한다.
    const orientationClass = draftText(view, 'principalVideoOrientation', 'landscape') === 'portrait'
      ? 'is-portrait' : 'is-landscape';
    const body = uploadedVideo
      ? uploadedVideoPlayer(uploadedVideo, 'principal-video-frame', '원장 소개 업로드 영상')
      : (embedUrl
        ? `<iframe class="principal-video-frame" src="${escapeHtml(embedUrl)}" title="원장 소개 YouTube 영상" loading="lazy" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>`
        : '<div class="principal-video-overlay"><div class="pv-play" aria-hidden="true">▶</div><div class="pv-caption">원장님 소개 영상</div></div>');
    return ctx.principalVideoBox(orientationClass, body);
  }

  function renderPrincipal(view, ctx) {
    if (!view.sectionVisible('sec-principal')) return '';
    const name = draftText(view, 'principalName', '○○○ 원장');
    const role = draftText(view, 'principalRole', '○○ 아카데미 원장');
    const desc = draftText(view, 'principalDesc', '원장 소개 문구를 입력해 주세요. 학원의 교육 철학과 지도 방식을 소개합니다.');
    const quote = draftText(view, 'principalQuote', '원장님의 교육 신념이나 학생·학부모에게 전하고 싶은 한마디를 입력해 주세요.');
    const careerItems = principalCareerList(view).map((text) => ctx.principalCareerItem(text)).join('');
    return `<section id="sec-principal" data-builder-section-key="sec-principal" class="section-wrap principal-wrap" data-fade>
        <div class="principal-inner">
          <div class="principal-head"><span class="section-badge">🎓 대표 원장 소개</span></div>
          <div class="principal-grid">
            <div class="principal-card">
              <div class="principal-avatar" aria-hidden="true">👤</div>
              <div class="principal-info">
                ${ctx.editableText({ tag: 'div', className: 'principal-name', draftKey: 'principalName', value: name })}
                ${ctx.editableText({ tag: 'div', className: 'principal-role', draftKey: 'principalRole', value: role })}
                ${ctx.editableText({ tag: 'div', className: 'principal-desc', draftKey: 'principalDesc', value: desc, publicClass: 'academy-multiline-text', builderClass: 'builder-multiline-text' })}
                <ul class="principal-career"${ctx.principalCareerListAttrs()}>${careerItems}</ul>
                ${ctx.principalCareerFooter()}
                ${ctx.editableText({ tag: 'div', className: 'principal-bottom', draftKey: 'principalQuote', value: quote, publicClass: 'academy-multiline-text', builderClass: 'builder-multiline-text' })}
              </div>
            </div>
            ${renderPrincipalVideo(view, ctx)}
          </div>
        </div>
      </section>`;
  }

  // ── sec-hero ──────────────────────────────────────────────────────────────
  const HERO_VIDEO_META = '<div class="video-meta"><div class="video-label">학원 소개 영상</div><div class="video-sub">원장 소개 · 수업 철학 · 학원 안내</div></div>';

  // 패널은 수업 탭과 같은 짧은 이름을 쓴다. tabShortName이 비면 정식 수업명으로 떨어진다.
  function lessonPanelTitle(lesson) {
    return lesson.tabShortName || lesson.name || '수업';
  }
  function lessonPanelDesc(lesson) {
    return lesson.highlight || lesson.kicker || lesson.description || '수업 한줄 소개를 입력하세요.';
  }

  /** 대표 수업은 자동 앞 4개가 아니라 저장된 선택 id만 쓴다(선택 순서 유지, 중복 제거, 최대 4개). */
  function featuredLessonIds(view) {
    const raw = draftText(view, 'featuredLessonIds', '');
    return Array.from(new Set(String(raw).split(',').map((id) => id.trim()).filter((id) => /^\d+$/.test(id)))).slice(0, 4);
  }
  function selectedFeaturedLessons(view) {
    const lessonsById = new Map((view.lessons || []).map((lesson) => [String(lesson.lessonId), lesson]));
    return featuredLessonIds(view).map((id) => lessonsById.get(id)).filter(Boolean);
  }

  // 상세는 sec-lessons가 담당하고 여기서는 네비게이션만 한다(클릭 → 스크롤).

  /** 시안 우측 컬럼 — iPad 프레임 안에 원장 사진. 캡션은 원장명 + 학원명. */
  function renderPortraitTablet(view, ctx) {
    const name = draftText(view, 'principalName', '원장');
    const body = ctx.hasHeroMedia(view, 'principal')
      ? `<img src="${escapeHtml(ctx.heroMediaUrl('principal'))}" alt="${escapeHtml(view.name)} 원장 사진">`
      : '<div class="tablet-empty"><span>원장 대표사진</span></div>';
    return `<div class="tablet"${ctx.portraitClusterAttrs()}>
            <div class="cam"></div>
            <div class="tablet-screen">
              ${body}
              <div class="tablet-cap"><b>${escapeHtml(name)}</b>${escapeHtml(view.name)}</div>
            </div>
            ${ctx.portraitClusterExtra()}
          </div>`;
  }





  const HERO_STATS = [
    { draftKey: 'monthlyTuition', label: '월 수강료' },
    { draftKey: 'scoreImprovement', label: '성적 향상률' },
    { draftKey: 'operationYears', label: '운영 경력' }
  ];

  function renderHero(view, ctx) {
    const eyebrow = draftText(view, 'principalName', '');
    const stats = HERO_STATS.map((stat) =>
      `<div><b>${ctx.heroStatValue(stat.draftKey, draftText(view, stat.draftKey, '—'))}</b><span>${stat.label}</span></div>`
    ).join('');
    return `<section class="hero" id="sec-hero" data-builder-section-key="sec-hero" data-fade>
        <div class="brand-glow" aria-hidden="true"></div>
        <div class="hero-grid">
          <div>
            ${eyebrow ? `<div class="eyebrow"><span class="stroke"></span>${ctx.heroEyebrow(eyebrow)}</div>` : ''}
            <h1>${ctx.heroTitleInner(view.name)}</h1>
            ${ctx.editableText({ tag: 'blockquote', className: 'hero-quote', anchor: 'text', draftKey: 'intro', value: view.intro, builderClass: 'builder-multiline-text' })}
            <div class="hero-actions">
              <button type="button" class="btn" onclick="requestConsult()">상담 신청하기</button>
              <button type="button" class="btn outline" onclick="scrollToId('sec-lessons')">대표 수업 보기</button>
            </div>
            <div class="hero-stats">${stats}</div>
            ${ctx.heroBadges(view)}
          </div>
          ${renderPortraitTablet(view, ctx)}
        </div>
      </section>`;
  }

  // ── sec-videos ────────────────────────────────────────────────────────────
  // 영상이 없어도 섹션을 숨기지 않는다. 빌더에서 ON으로 발행했는데 공개 페이지에서 통째로
  // 사라지면 강사가 "발행이 안 됐다"고 오해한다. 다른 준비중 섹션과 같은 placeholder를 쓴다.
  const VIDEO_EMPTY_PLACEHOLDER =
    '<div class="demo-placeholder"><div class="ph-ico">🎬</div><p>수업 영상을 준비 중입니다.</p></div>';

  function formatDuration(seconds) {
    const total = Number(seconds) || 0;
    const minutes = Math.floor(total / 60);
    return `${minutes}:${String(total % 60).padStart(2, '0')}`;
  }

  /** RAW Ready 영상은 썸네일이 없을 수 있다. 깨진 img 대신 중립 placeholder를 쓴다. */
  function videoThumbnail(video) {
    return video.thumbnailUrl
      ? `<img class="video-playlist-thumb" src="${escapeHtml(video.thumbnailUrl)}" alt="" loading="lazy" />`
      : '<span class="video-playlist-thumb video-playlist-thumb-placeholder" aria-label="썸네일 준비 중">🎬</span>';
  }

  function videoPlaylist(videos, ctx) {
    const first = videos[0];
    const playlist = videos.map((video, index) => ctx.videoPlaylistItem({
      video,
      index,
      inner: `${videoThumbnail(video)}<span class="video-playlist-meta"><span class="video-playlist-index">${index + 1}</span><span class="video-playlist-duration">${formatDuration(video.durationSec)}</span></span>`
    })).join('');
    return `<div class="video-playlist-layout">
          <div class="video-main">
            <video${ctx.videoMainPlayerAttrs()} class="video-main-player" controls playsinline preload="metadata"
                   ${first.thumbnailUrl ? `poster="${escapeHtml(first.thumbnailUrl)}"` : ''}
                   src="${escapeHtml(first.videoUrl)}"></video>
          </div>
          <div class="video-playlist" role="list">${playlist}</div>
        </div>`;
  }

  function renderVideos(view, ctx) {
    if (!view.sectionVisible('sec-videos')) return '';
    const videos = ctx.videoList();
    const body = videos.length ? videoPlaylist(videos, ctx) : VIDEO_EMPTY_PLACEHOLDER;
    return `<section id="sec-videos" data-builder-section-key="sec-videos" class="section-wrap" data-fade>
      <div class="section-inner">
        <div class="section-header">
          <h2 class="section-title">${escapeHtml(view.name)}의 수업 현장</h2>
        </div>
        ${body}
        ${ctx.videosFooter(view)}
      </div>
    </section>`;
  }

  // ── sec-lessons ───────────────────────────────────────────────────────────
  /** 시간표 달력 이미지 슬롯 key. 서버 HeroMediaSlot.SCHEDULE와 같은 문자열이어야 한다. */
  const SCHEDULE_MEDIA_SLOT = 'schedule';

  /** 학원마다 달라야 하는 제목이라 기본값도 학원명에서 파생한다(고정 문구 금지). */
  function lessonsTitleDefault(view) {
    const name = String((view && view.name) || '').trim();
    return name ? name + ' 커리큘럼' : '커리큘럼';
  }

  function renderLessons(view, ctx) {
    if (!view.sectionVisible('sec-lessons')) return '';
    const title = draftText(view, 'curriculumTitle', lessonsTitleDefault(view));
    return `<section id="sec-lessons" data-builder-section-key="sec-lessons" class="section-wrap classes-wrap" data-fade>
        <div class="section-inner classes-container">
          <div class="section-head classes-head">
            <div class="eyebrow"><span class="stroke"></span>대표 수업</div>
            ${ctx.editableText({ tag: 'h2', className: 'classes-title', draftKey: 'curriculumTitle', value: title, builderClass: 'builder-multiline-text', omitWhenEmpty: true })}
          </div>
          <div class="lesson-product-slot">${ctx.renderLessonShowcase(view.lessons || [])}</div>
          ${ctx.lessonsFooter(view)}
        </div>
        ${renderLessonSchedule(view, ctx)}
      </section>`;
  }

  /**
   * 시간표 달력 이미지(V948 schedule 슬롯). 개설 시간표 섹션의 유일한 본문이다.
   *
   * <p>원래는 수업상품에서 파생한 행 목록(`.schedule-list`)을 그렸으나, 그게 학원이 실제로 쓰는
   * 달력형 시간표로 보이지 않는다는 이유로 걷어냈다. 시간표는 원장이 올린 달력 이미지로만 낸다.
   *
   * <p>공개 페이지는 이미지가 없으면 블록 자체를 내지 않는다(빈 자리를 남기지 않는다).
   * 빌더는 반대로 비어 있어도 placeholder를 그려야 원장이 클릭해 올릴 수 있다 — 그 차이를
   * {@code scheduleCalendarEmpty()}가 가른다(빈 문자열이면 블록 없음).
   */
  function renderScheduleCalendar(view, ctx) {
    const placeholder = ctx.scheduleCalendarEmpty();
    const filled = ctx.hasHeroMedia(view, SCHEDULE_MEDIA_SLOT);
    if (!filled && !placeholder) return '';
    const body = filled
      ? `<img class="schedule-calendar-image" src="${escapeAttr(ctx.heroMediaUrl(SCHEDULE_MEDIA_SLOT))}" alt="${escapeAttr(view.name)} 개설 시간표">`
      : placeholder;
    return `<figure class="schedule-calendar"${ctx.scheduleCalendarAttrs()} data-fade>${body}${ctx.scheduleCalendarExtra()}</figure>`;
  }

  function renderLessonSchedule(view, ctx) {
    const calendar = renderScheduleCalendar(view, ctx);
    // 달력이 없으면 섹션을 통째로 뺀다 — 본문이 달력 하나뿐이라 머리말만 남으면 빈 껍데기가 된다.
    // 빌더는 placeholder가 있어 calendar가 항상 채워지므로 업로드 통로는 남는다.
    if (!calendar) return '';
    return `<div class="lesson-schedule-wrap">
      <div class="lesson-schedule-inner">
        <div class="section-head schedule-head">
          <div class="eyebrow"><span class="stroke"></span>개설 시간표</div>
          <h2>지금 모집 중인 반</h2>
          <div class="toggle-row">
            <button type="button" class="toggle on" role="switch" aria-checked="true" aria-label="시간표 공개 전환" data-schedule-toggle></button>
            <span class="toggle-label"><b>시간표 공개</b> — 학원이 선택할 수 있는 기능입니다</span>
          </div>
        </div>
        ${calendar}
      </div>
    </div>`;
  }

  // ── ctx 구현 2벌 ───────────────────────────────────────────────────────────
  // deps는 전부 지연 평가 함수다. ctx는 모듈 로드 시점에 만들어지는데 slug 같은 값은
  // 그때 아직 정해지지 않았고, 테스트에서는 정본 모듈이 소비자와 다른 컨텍스트에서 돌아
  // 전역을 공유하지 않기 때문이다.
  function resolve(dep) {
    return typeof dep === 'function' ? dep() : dep;
  }

  /** 공개 페이지: 연동된 URL이면 링크, 미연동이면 갈 곳이 없으므로 감싸지 않는다. */
  function createPublicContext(deps) {
    const options = deps || {};
    return Object.freeze({
      mode: 'public',
      wrapSocialCard({ cardClass, header, url }) {
        if (!url) return `<div class="${cardClass}">${header}</div>`;
        return `<a class="${cardClass}" href="${escapeAttr(url)}" target="_blank" rel="noopener">${header}</a>`;
      },
      renderLessonShowcase(items) {
        return resolve(options.showcase).render(items, {
          emptyMessage: '아직 공개된 수업 상품이 없습니다.',
          // 대표 미디어 URL을 만들려면 slug가 필요하다.
          slug: resolve(options.slug)
        });
      },
      // 공개 페이지에는 관리 버튼이 없다.
      lessonsFooter() { return ''; },

      /**
       * 텍스트 노드. 값이 비면 요소 자체를 생략해 빈 자리가 남지 않게 한다
       * (빌더는 반대로 항상 렌더해야 원장이 클릭해 채울 수 있다).
       * quoted는 인용문 앞뒤에 붙는 장식 따옴표다.
       */
      editableText({ tag, className, value, publicClass, omitWhenEmpty, quoted }) {
        if (omitWhenEmpty && !value) return '';
        const cls = publicClass ? `${className} ${publicClass}` : className;
        const inner = quoted
          ? `<span class="qm">"</span>${escapeHtml(value)}<span class="qm">"</span>`
          : escapeHtml(value);
        return `<${tag} class="${cls}">${inner}</${tag}>`;
      },

      // 히어로 — 공개는 편집 크롬을 일절 얹지 않는다.
      heroTitleInner(name) { return escapeHtml(name); },
      heroEyebrow(text) { return escapeHtml(text); },
      // 시안 통계는 그라디언트 텍스트다(.u). 값만 감싸고 라벨은 렌더 쪽이 낸다.
      heroStatValue(draftKey, value) {
        return `<span class="u">${escapeHtml(value)}</span>`;
      },
      // 시안 히어로에는 대상·과목 배지가 없다. 공개 페이지는 렌더하지 않는다.
      heroBadges() { return ''; },
      portraitClusterAttrs() { return ''; },
      portraitClusterExtra() { return ''; },
      badgeRowAttrs() { return ''; },
      // 공개 페이지는 정식 명칭을 쓴다(초등학생/중학생/…).
      targetBadgeLabel(value) {
        return ({ SUBSCRIPTION: '구독형', SHORT_TERM: '단기형', ELEMENTARY: '초등학생', MIDDLE: '중학생', HIGH: '고등학생', UNIVERSITY: '대학생', ADULT: '성인' }[value]) || value;
      },
      hasHeroMedia(view, slot) {
        return (view.heroMediaSlots || []).indexOf(slot) !== -1;
      },
      heroMediaUrl(slot) {
        // 공개 경로는 발행된 학원만 통과한다.
        return `/api/academies/${encodeURIComponent(resolve(options.slug))}/hero-media/${slot}`;
      },
      // 달력 이미지가 없으면 공개 페이지에는 블록을 만들지 않는다(빈 문자열 = 블록 없음).
      scheduleCalendarEmpty() { return ''; },
      scheduleCalendarAttrs() { return ''; },
      scheduleCalendarExtra() { return ''; },
      introFeatureListAttrs() { return ''; },
      introFeatureItem(text, index) {
        const parts = noteParts(text, index);
        return `<article class="note-card intro-feature-item" data-fade><h3 class="intro-feature-text">${escapeHtml(parts.title)}</h3>${parts.body ? `<p>${escapeHtml(parts.body)}</p>` : ''}</article>`;
      },
      introFooter() { return ''; },
      philosophyCard(text, index) {
        return `<div class="p-card" data-fade><span class="n">${String(index + 1).padStart(2, '0')}</span><p>${escapeHtml(text)}</p></div>`;
      },
      // 값이 빈 항목은 공개 페이지에서 생략한다(빈 칩이 남지 않게).
      featuredItem(text) {
        const label = String(text == null ? '' : text).trim();
        return label ? `<div class="featured-item" data-fade><span class="featured-item-label">${escapeHtml(label)}</span></div>` : '';
      },
      featuredCta(label) {
        const text = String(label == null ? '' : label).trim();
        return text ? `<button type="button" class="btn featured-cta-btn" onclick="requestConsult()">${escapeHtml(text)}</button>` : '';
      },
      aiProcessStep(text, index) {
        const label = String(text == null ? '' : text).trim();
        return label ? `<li class="ai-process-step" data-fade><span class="ai-process-step-num">${index + 1}</span><span class="ai-process-step-label">${escapeHtml(label)}</span></li>` : '';
      },

      // 공개 페이지는 목록 조회가 실패해도 영상을 그린다 — id가 유효하면 stream URL을 합성해
      // 재생을 시도한다(로딩 실패 내성). 빌더는 목록에 없으면 placeholder를 띄운다.
      findUploadedVideo(videoId) {
        const id = String(videoId || '').trim();
        if (!/^[1-9]\d*$/.test(id)) return null;
        const slug = resolve(options.slug);
        return (resolve(options.videos) || []).find((video) => String(video.videoId) === id) || {
          videoId: Number(id),
          videoUrl: `/api/academies/${encodeURIComponent(slug)}/videos/${encodeURIComponent(id)}/stream`,
          thumbnailUrl: null
        };
      },
      principalVideoBox(orientationClass, body) {
        return `<div class="principal-video-box ${orientationClass}">${body}</div>`;
      },
      videoList() { return resolve(options.videos) || []; },
      // 재생목록 클릭이 이 플레이어의 src를 갈아끼운다 — id가 없으면 재생 전환이 죽는다.
      videoMainPlayerAttrs() { return ' id="academyVideoPlayer"'; },
      videoPlaylistItem({ video, index, inner }) {
        return `<button type="button" class="video-playlist-item${index === 0 ? ' active' : ''}"
              data-video-select="${escapeHtml(video.videoUrl)}" data-video-index="${index}">
        ${inner}
      </button>`;
      },
      videosFooter() { return ''; },
      principalCareerListAttrs() { return ''; },
      principalCareerItem(text) {
        return `<li><span class="li-check" aria-hidden="true">✓</span><span class="principal-career-text">${escapeHtml(text)}</span></li>`;
      },
      principalCareerFooter() { return ''; }
    });
  }

  /** 빌더 캔버스: 카드 컨테이너 자체가 URL 편집 진입점이라 URL 유무와 무관하게 div다. */
  function createBuilderContext(deps) {
    const options = deps || {};
    return Object.freeze({
      mode: 'builder',
      wrapSocialCard({ cardClass, header, url, field, label }) {
        return `<div class="${cardClass}" data-social-url-edit="${escapeAttr(field)}" role="button" tabindex="0" aria-label="${escapeAttr(label)} ${url ? '수정' : '등록'}">${header}</div>`;
      },
      renderLessonShowcase(items) {
        return resolve(options.showcase).render(items, {
          emptyMessage: '아직 등록된 수업이 없습니다.',
          // 빌더는 아직 발행되지 않은(DRAFT) 학원도 렌더한다. 공개 이미지 경로는 발행 상태를
          // 요구해 404가 나므로 소유자 전용 경로를 쓴다.
          heroImageBase: '/api/tutor/academy/lessons'
        });
      },
      // 발행 전에는 관리 화면으로 보낼 수 없으므로 안내를 띄우는 button으로 바꾼다.
      lessonsFooter(view) {
        const manageBtn = view.publicPathUrl
          ? '<a class="cv-btn-primary builder-lesson-manage" href="/tutor/academy/lessons/editor">📚 수업 관리</a>'
          : '<button type="button" class="cv-btn-primary builder-lesson-manage" data-lesson-publish-required>📚 수업 관리</button>';
        return `<div style="text-align:center;margin-top:14px;">${manageBtn}</div>`;
      },

      /**
       * 텍스트 노드. 값이 비어도 항상 렌더한다 — 요소가 없으면 원장이 클릭해 채울 수 없다.
       * contenteditable은 false로 두고 섹션 편집 모드에 들어갈 때 true로 바뀐다.
       */
      // anchor='text'는 Academy 본체 필드(name/intro), 기본값 'draft'는 builderDraft 키다.
      // 커밋 경로가 달라 속성 이름도 다르다.
      editableText({ tag, className, draftKey, value, builderClass, anchor }) {
        const cls = builderClass ? `${className} ${builderClass}` : className;
        const attr = anchor === 'text' ? 'data-builder-text' : 'data-builder-draft';
        return `<${tag} class="${cls}" ${attr}="${escapeAttr(draftKey)}" contenteditable="false" spellcheck="false">${escapeHtml(value)}</${tag}>`;
      },

      // 히어로 — 클릭 가능한 편집 진입점과 "수정" 칩을 얹는다.
      heroTitleInner(name) {
        return `<span data-builder-text="name" contenteditable="false" spellcheck="false">${escapeHtml(name)}</span>`;
      },
      heroEyebrow(text) {
        return `<span data-builder-draft="principalName" contenteditable="false" spellcheck="false">${escapeHtml(text)}</span>`;
      },
      heroStatValue(draftKey, value) {
        return `<span class="u" data-builder-draft="${escapeAttr(draftKey)}" contenteditable="false" spellcheck="false">${escapeHtml(value)}</span>`;
      },
      /**
       * 시안 히어로에는 대상·과목 배지가 없다. 그런데 이 배지 행이 두 값을 고칠 수 있는
       * 유일한 통로다(sec-intro에서 이미 뺐다). 지우면 원장이 값을 고칠 방법을 잃으므로
       * 공개 페이지에서는 감추고 캔버스에만 편집 통로로 남긴다.
       */
      heroBadges(view) {
        const target = (view.targetGroups || []).map((v) => this.targetBadgeLabel(v)).filter(Boolean)
          .map((t) => `<span class="badge">${escapeHtml(t)}</span>`).join('');
        const subject = (view.subjects || []).map((x) => x.nameKo || x.nameEn).filter(Boolean)
          .map((t) => `<span class="badge">${escapeHtml(t)}</span>`).join('');
        return '<div class="cv-hero-badges builder-only-badges">'
          + `<div class="cv-hero-badge-row cv-hero-target-badges"${this.badgeRowAttrs('targetGroups')}>${target || '<span class="badge muted">대상 미등록</span>'}</div>`
          + `<div class="cv-hero-badge-row cv-hero-subject-badges"${this.badgeRowAttrs('subjects')}>${subject || '<span class="badge muted">과목 미등록</span>'}</div>`
          + '</div>';
      },
      portraitClusterAttrs() {
        return ' data-hero-media-edit="principal" role="button" tabindex="0" title="클릭하여 원장 대표사진을 등록하세요"';
      },
      portraitClusterExtra() {
        return '<span class="builder-hero-media-chip">🖼 사진 수정</span>';
      },
      badgeRowAttrs(field) {
        const title = field === 'subjects' ? '클릭하여 과목 수정' : '클릭하여 수업대상 수정';
        return ` data-builder-basic="${escapeAttr(field)}" tabindex="0" title="${title}"`;
      },
      // ⚠ 캔버스는 축약형(중등)을, 공개 페이지는 정식 명칭(중학생)을 쓴다. 개편 전부터
      //   두 화면의 배지 문구가 달랐다 — 추출이 드러낸 기존 드리프트이며 여기서 통일하지 않는다.
      targetBadgeLabel(value) {
        return ({ ELEMENTARY: '초등', MIDDLE: '중등', HIGH: '고등', UNIVERSITY: '대학', ADULT: '성인' })[value] || value;
      },
      // 저장 전 업로드(pendingHeroMedia)도 캔버스에는 보여야 한다.
      hasHeroMedia(view, slot) {
        const pending = resolve(options.pendingHeroMedia) || {};
        if (pending[slot]) return true;
        return (view.heroMediaSlots || []).indexOf(slot) !== -1;
      },
      heroMediaUrl(slot) {
        const pending = (resolve(options.pendingHeroMedia) || {})[slot];
        if (pending) return pending.objectUrl;
        // 소유자 전용 경로 + 캐시버스팅. 발행 전 학원도 봐야 하므로 공개 경로를 쓸 수 없다.
        return `/api/tutor/academy/hero-media/${slot}?v=${resolve(options.heroMediaVersion)}`;
      },
      // 비어 있어도 자리를 그려야 원장이 클릭해 달력 이미지를 올릴 수 있다.
      scheduleCalendarEmpty() {
        return '<span class="schedule-calendar-placeholder">🗓 클릭해서 시간표 달력 이미지를 등록하세요<br><small>수업별 자동 목록 위에 학원이 만든 달력이 그대로 표시됩니다</small></span>';
      },
      scheduleCalendarAttrs() {
        return ` data-hero-media-edit="${SCHEDULE_MEDIA_SLOT}" role="button" tabindex="0" title="클릭하여 시간표 달력 이미지를 등록하세요"`;
      },
      scheduleCalendarExtra() {
        return '<span class="builder-hero-media-chip">🗓 시간표 수정</span>';
      },
      introFeatureListAttrs() { return ' data-intro-feature-list'; },
      // 본문(p)도 제목과 같은 편집 대상이다. 공개 렌더와 달리 값이 비어도 항상 렌더한다 —
      // 요소가 없으면 원장이 클릭해 본문을 채울 수 없다(빈 본문은 CSS placeholder로 안내).
      introFeatureItem(text, index) {
        const parts = noteParts(text, index);
        return `<article class="note-card intro-feature-item" data-fade><h3 class="intro-feature-text" data-intro-feature-item contenteditable="false" spellcheck="false">${escapeHtml(parts.title)}</h3><p data-intro-feature-body contenteditable="false" spellcheck="false">${escapeHtml(parts.body)}</p><button type="button" class="intro-feature-remove" data-intro-feature-remove aria-label="노트 삭제">✕</button></article>`;
      },
      introFooter() {
        return '<button type="button" class="intro-feature-add" data-intro-feature-add>＋ 특징 추가</button>';
      },
      philosophyCard(text, index) {
        const draftKey = 'philosophyItem' + (index + 1);
        return `<div class="p-card" data-fade><span class="n">${String(index + 1).padStart(2, '0')}</span><p data-builder-draft="${draftKey}" contenteditable="false" spellcheck="false">${escapeHtml(text)}</p></div>`;
      },
      // 빈 값이어도 항상 렌더한다 — 요소가 없으면 원장이 클릭해 채울 수 없다(빈 값은 CSS placeholder로 안내).
      featuredItem(text, index) {
        const draftKey = 'featuredItem' + (index + 1);
        return `<div class="featured-item" data-fade><span class="featured-item-label" data-builder-draft="${draftKey}" contenteditable="false" spellcheck="false">${escapeHtml(String(text == null ? '' : text))}</span></div>`;
      },
      featuredCta(label) {
        return `<span class="btn featured-cta-btn" data-builder-draft="featuredCtaLabel" contenteditable="false" spellcheck="false">${escapeHtml(String(label == null ? '' : label))}</span>`;
      },
      aiProcessStep(text, index) {
        const draftKey = 'aiProcessStep' + (index + 1);
        return `<li class="ai-process-step" data-fade><span class="ai-process-step-num">${index + 1}</span><span class="ai-process-step-label" data-builder-draft="${draftKey}" contenteditable="false" spellcheck="false">${escapeHtml(String(text == null ? '' : text))}</span></li>`;
      },

      // 빌더는 아직 발행 전 학원도 렌더하므로 공개 stream 경로를 합성하지 않는다.
      // 목록에 없으면 placeholder로 떨어뜨린다.
      findUploadedVideo(videoId) {
        const id = String(videoId || '').trim();
        return id ? (resolve(options.videos) || []).find((video) => String(video.videoId) === id) || null : null;
      },
      principalVideoBox(orientationClass, body) {
        return `<div class="principal-video-box ${orientationClass} builder-principal-video-edit" data-principal-video-edit="true" role="button" tabindex="0" title="클릭하여 업로드 영상 또는 YouTube 링크를 선택하세요">${body}<span class="builder-hero-video-edit-chip">🎥 영상 수정</span></div>`;
      },
      videoList() { return resolve(options.videos) || []; },
      // 캔버스는 재생 전환을 걸지 않으므로 플레이어에 id를 두지 않는다.
      videoMainPlayerAttrs() { return ''; },
      videoPlaylistItem({ index, inner }) {
        return `<div class="video-playlist-item${index === 0 ? ' active' : ''}">
              ${inner}
            </div>`;
      },
      // 발행 전에는 영상 관리로 보낼 수 없으므로 안내를 띄우는 button으로 바꾼다.
      videosFooter(view) {
        const manageBtn = view.publicPathUrl
          ? '<a class="cv-btn-primary builder-video-manage" href="/tutor/academy/videos">🎬 영상 관리</a>'
          : '<button type="button" class="cv-btn-primary builder-video-manage" data-video-publish-required>🎬 영상 관리</button>';
        return `<div style="text-align:center;margin-top:14px;">${manageBtn}</div>`;
      },
      principalCareerListAttrs() { return ' data-principal-career-list'; },
      principalCareerItem(text) {
        return `<li class="principal-career-item"><span class="li-check" aria-hidden="true">✓</span><span class="principal-career-text" data-principal-career-item contenteditable="false" spellcheck="false">${escapeHtml(text)}</span><button type="button" class="principal-career-remove" data-principal-career-remove aria-label="약력 삭제">✕</button></li>`;
      },
      principalCareerFooter() {
        return '<button type="button" class="principal-career-add" data-principal-career-add>＋ 약력 추가</button>';
      }
    });
  }

  global.F2RAcademySections = Object.freeze({
    FUTURE_SECTION_IDS,
    OPT_IN_SECTION_IDS,
    escapeHtml,
    escapeAttr,
    safeUrl,
    toSectionView,
    createPublicContext,
    createBuilderContext,
    draftText,
    introFeatureList,
    philosophyItems,
    principalCareerList,
    youtubeEmbedUrl,
    uploadedVideoPlayer,
    featuredLessonIds,
    selectedFeaturedLessons,
    featuredItems,
    aiProcessSteps,
    lessonPanelTitle,
    renderHero,
    renderIntro,
    renderPhilosophy,
    renderPrincipal,
    renderVideos,
    renderExternalServices,
    renderLessons,
    renderFeatured,
    renderAiProcess
  });
})(typeof window !== 'undefined' ? window : globalThis);
