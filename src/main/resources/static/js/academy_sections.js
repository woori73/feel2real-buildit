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
    'sec-digitaltwin',
    'sec-minilectures',
    'sec-cta'
  ]);

  // ── Video Taxonomy (Phase 3) ─────────────────────────────────────────────
  // academy_videos.purpose 는 세 상태다: 'DIRECTOR' | 'LESSON' | 미지정(null/빈 문자열).
  // Phase 1/2는 미지정을 "두 목록 모두에 노출"로 하위호환 처리했는데, 이게 production에서
  // 실제 분리 실패의 원인이었다 — purpose 도입 이전에 올라간 레거시 원장 영상이 미지정 상태로
  // 남아 계속 "실제 수업 현장"에 노출됐다. Phase 3부터 미지정은 "관리자가 아직 분류하지 않은
  // 미완료 상태"로 취급해 공개 화면·양쪽 picker 어디에도 자동 노출하지 않는다 — 영상 관리
  // 화면에서 명시적으로 분류해야 노출된다. 이 세 predicate가 유일한 정의다: 다른 파일
  // (academy_builder.js, lesson_product_editor.js, tutor_academy_videos.js)은 문자열 비교를
  // 다시 구현하지 않고 이 함수들을 그대로 가져다 쓴다.
  function isDirectorVideo(video) {
    return !!video && video.purpose === 'DIRECTOR';
  }
  function isLessonVideo(video) {
    return !!video && video.purpose === 'LESSON';
  }
  function isUnclassifiedVideo(video) {
    return !!video && !isDirectorVideo(video) && !isLessonVideo(video);
  }

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

  // 이 제목은 이제 socialSectionTitle draft로 독립 편집한다(미설정 시 이 기본값).
  // editableText가 escapeHtml을 거치므로 `&`는 화면 동일한 `&amp;`로 정규화된다.
  const SOCIAL_TITLE_DEFAULT = '최신 소식 & 방문 안내';

  function renderExternalServices(view, ctx) {
    if (!view.sectionVisible('sec-social')) return '';
    const pageSettings = view.pageSettings;
    const title = draftText(view, 'socialSectionTitle', SOCIAL_TITLE_DEFAULT);
    return `<section id="sec-social" data-builder-section-key="sec-social" class="section-wrap" style="background:#f8faff;" data-reveal data-reveal-item>
        <div class="section-inner">
          <div class="section-header">${ctx.editableText({ tag: 'h2', className: 'section-title', draftKey: 'socialSectionTitle', value: title, omitWhenEmpty: true })}</div>
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

  // ── sec-cta ─────────────────────────────────────────────────────────────
  // 상담 신청 CTA (P0-3). 전화(tel:) + 카카오톡 채널 URL 연결. opt-in이라
  // sectionVisibility['sec-cta'] === true일 때만 렌더된다 — 기존 테넌트는 변화 없음.
  const CTA_TITLE_DEFAULT = '지금 상담 신청하세요';
  const CTA_SUB_DEFAULT = '궁금한 점이 있으시면 편하게 연락 주세요.\n친절하게 안내해 드리겠습니다.';
  const CTA_PHONE_LABEL_DEFAULT = '전화 상담';
  const CTA_KAKAO_LABEL_DEFAULT = '카카오톡 상담';

  /** CTA 전화번호: builderDraft.ctaPhone 오버라이드 > 사업자 정보 phone. */
  function ctaPhoneNumber(view) {
    const draft = view && view.pageSettings && view.pageSettings.builderDraft;
    const override = draft && draft.ctaPhone;
    if (override != null && String(override).trim() !== '') return String(override).trim();
    const businessInfo = view && view.pageSettings && view.pageSettings.businessInfo;
    const phone = businessInfo && businessInfo.phone;
    return phone != null ? String(phone).trim() : '';
  }

  function ctaTelHref(phone) {
    const dialable = String(phone || '').replace(/[^0-9+]/g, '');
    return /[0-9]/.test(dialable) ? `tel:${dialable}` : '';
  }

  function renderCta(view, ctx) {
    if (!view.sectionVisible('sec-cta')) return '';
    const isBuilder = ctx && ctx.mode === 'builder';
    const title = draftText(view, 'ctaTitle', CTA_TITLE_DEFAULT);
    const subtitle = draftText(view, 'ctaSubtitle', CTA_SUB_DEFAULT);
    const phoneLabel = draftText(view, 'ctaPhoneLabel', CTA_PHONE_LABEL_DEFAULT);
    const kakaoLabel = draftText(view, 'ctaKakaoLabel', CTA_KAKAO_LABEL_DEFAULT);
    const phone = ctaPhoneNumber(view);
    const telHref = ctaTelHref(phone);
    const kakaoUrl = safeUrl(draftText(view, 'ctaKakaoUrl', ''));
    const phoneBtn = telHref
      ? (isBuilder
        ? `<div class="cta-btn cta-btn-phone" data-cta-edit="phone" role="button" tabindex="0" aria-label="${escapeAttr(phoneLabel)} 수정">📞 ${escapeHtml(phoneLabel)}</div>`
        : `<a class="cta-btn cta-btn-phone" href="${escapeAttr(telHref)}">📞 ${escapeHtml(phoneLabel)}</a>`)
      : '';
    const kakaoBtn = kakaoUrl
      ? (isBuilder
        ? `<div class="cta-btn cta-btn-kakao" data-cta-edit="kakao" role="button" tabindex="0" aria-label="${escapeAttr(kakaoLabel)} 수정">💬 ${escapeHtml(kakaoLabel)}</div>`
        : `<a class="cta-btn cta-btn-kakao" href="${escapeAttr(kakaoUrl)}" target="_blank" rel="noopener">💬 ${escapeHtml(kakaoLabel)}</a>`)
      : '';
    const actions = (phoneBtn || kakaoBtn)
      ? `<div class="cta-actions">${phoneBtn}${kakaoBtn}</div>`
      : (isBuilder
        ? '<p class="cta-empty-note" data-cta-edit="phone" role="button" tabindex="0">전화번호나 카카오톡 채널 URL을 등록하면 상담 버튼이 표시됩니다. 여기를 눌러 등록하세요.</p>'
        : '');
    // 빌더에서도 버튼이 하나도 없으면 섹션 자체는 제목/설명만 보여준다(편집 진입점 유지).
    return `<section id="sec-cta" data-builder-section-key="sec-cta" class="section-wrap cta-section" data-reveal data-reveal-item>
        <div class="section-inner cta-inner">
          ${ctx.editableText({ tag: 'h2', className: 'cta-title', draftKey: 'ctaTitle', value: title, omitWhenEmpty: true })}
          ${ctx.editableText({ tag: 'p', className: 'cta-sub', draftKey: 'ctaSubtitle', value: subtitle, omitWhenEmpty: true })}
          ${actions}
        </div>
      </section>`;
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
    return `<section id="sec-intro" data-builder-section-key="sec-intro" class="section-wrap intro-wrap notes" data-reveal>
        <div class="notes-head" data-reveal-item>
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
    const items = philosophyItems(view);
    return `<section id="sec-philosophy" data-builder-section-key="sec-philosophy" class="section-wrap philosophy-step" data-reveal>
        <div class="philosophy-step-inner">
          <div class="philosophy-step-head" data-reveal-item>
            <div class="eyebrow philosophy-eyebrow"><span class="stroke"></span>${ctx.editableText({ tag: 'span', className: 'philosophy-keyword', draftKey: 'philosophyKeyword', value: keyword, omitWhenEmpty: true })}</div>
            ${ctx.editableText({ tag: 'h2', className: 'philosophy-step-title', draftKey: 'philosophyTitle', value: title, builderClass: 'builder-multiline-text', omitWhenEmpty: true, extraAttrs: 'data-reveal-item' })}
          </div>
          <div class="philosophy-step-card" data-philosophy-step-card>
            <div class="philosophy-step-progress" aria-hidden="true">
              ${items.map((_, i) => `<span class="philosophy-step-bar" data-step-bar="${i}"></span>`).join('')}
            </div>
            <div class="philosophy-step-content" data-philosophy-step-content>
              <p class="philosophy-step-meta"><span class="philosophy-step-label" data-step-label></span><span class="philosophy-step-counter" data-step-counter></span></p>
              <p class="philosophy-step-text" data-step-text></p>
            </div>
            <div class="philosophy-step-nav">
              <div class="philosophy-step-indicators" role="tablist" aria-label="원칙 선택" data-step-indicators>
                ${items.map((_, i) => `<button type="button" class="philosophy-step-indicator${i === 0 ? ' is-on' : ''}" data-step-indicator="${i}" role="tab" aria-selected="${i === 0}" aria-label="원칙 ${i + 1}">${i + 1}</button>`).join('')}
              </div>
              <div class="philosophy-step-arrows">
                <button type="button" class="philosophy-step-arrow" data-step-prev aria-label="이전 원칙" disabled><svg width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M13 10L7 10M7 10L11 6M7 10L11 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
                <button type="button" class="philosophy-step-arrow" data-step-next aria-label="다음 원칙"><svg width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M7 10L13 10M13 10L9 6M13 10L9 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
              </div>
            </div>
          </div>
        </div>
        <script type="application/json" data-philosophy-steps>${JSON.stringify(items).replace(/</g, '\\u003c')}</script>
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

  /** 비어있지 않은 학교 라벨만(순서 유지). */
  function featuredSchoolLabels(view) {
    return featuredItems(view).map((l) => String(l == null ? '' : l).trim()).filter(Boolean);
  }

  // sec-featured — eyebrow → 큰 제목 → 짧은 설명 → **커버플로우 캐러셀 (승인된 UI)**
  // (각 카드 = 그 학교의 실제 /api/analyzes 출제자 Digital Twin) → 단일 CTA.
  // 21st.dev @ruixen.ui/coverflow-carousel 스타일: 중앙 카드 확대, 좌우 카드 축소/투명도, 3D 회전 효과
  // 데스크톱: 마우스 드래그/휠 + 화살표 + 인디케이터
  // 모바일: 터치 스와이프 + 인디케이터
  // 배선: academy_public.js wireFeaturedCoverflow — #sec-featured 안에서만
  function renderFeatured(view, ctx) {
    if (!view.sectionVisible('sec-featured')) return '';
    const eyebrow = draftText(view, 'featuredEyebrow', FEATURED_EYEBROW_DEFAULT);
    const title = draftText(view, 'featuredTitle', FEATURED_TITLE_DEFAULT);
    const desc = draftText(view, 'featuredDescription', FEATURED_DESC_DEFAULT);
    const ctaLabel = draftText(view, 'featuredCtaLabel', FEATURED_CTA_DEFAULT);
    const schools = featuredItems(view).map((s) => String(s == null ? '' : s).trim()).filter(Boolean);
    if (!schools.length) return '';

    const cards = schools.map((school, i) =>
      `<article class="coverflow-card" role="listitem" data-coverflow-card data-index="${i}" data-school="${escapeAttr(school)}">
        <div class="coverflow-card-visual" aria-hidden="true">
          <div class="coverflow-card-glow"></div>
          <div class="coverflow-card-icon">
            <svg viewBox="0 0 48 48" fill="none"><path d="M24 4C12.95 4 4 12.95 4 24s8.95 20 20 20 20-8.95 20-20S35.05 4 24 4zm0 38C16.27 42 10 35.73 10 28S16.27 14 24 14s14 6.27 14 14-6.27 14-14 14z" stroke="currentColor" stroke-width="2"/><path d="M24 16v16M16 24h16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
          </div>
        </div>
        <div class="coverflow-card-content">
          <div class="coverflow-card-head">
            <b class="coverflow-card-name">${escapeHtml(school)}</b>
            <span class="coverflow-card-kind">출제자 Digital Twin</span>
          </div>
          <div class="coverflow-card-body" data-coverflow-card-body aria-live="polite">
            <p class="coverflow-card-loading">${escapeHtml(school)} 출제 분석 불러오는 중…</p>
          </div>
          <a class="coverflow-card-more" data-coverflow-card-more href="/analyzes" target="_blank" rel="noopener">출제 분석 보기 →</a>
        </div>
      </article>`
    ).join('');

    const indicators = schools.map((school, i) =>
      `<button type="button" class="coverflow-indicator${i === 0 ? ' is-on' : ''}" data-coverflow-indicator="${i}" aria-label="${escapeAttr(school)}" role="tab" aria-selected="${i === 0}"></button>`
    ).join('');

    return `<section id="sec-featured" data-builder-section-key="sec-featured" class="section-wrap featured-coverflow" data-reveal>
        <div class="section-inner featured-inner">
          <div class="featured-head">
            <div class="eyebrow featured-eyebrow" data-reveal-item><span class="stroke"></span>${ctx.editableText({ tag: 'span', className: 'featured-eyebrow-text', draftKey: 'featuredEyebrow', value: eyebrow, omitWhenEmpty: true })}</div>
            ${ctx.editableText({ tag: 'h2', className: 'featured-title', draftKey: 'featuredTitle', value: title, builderClass: 'builder-multiline-text', omitWhenEmpty: true, extraAttrs: 'data-reveal-item' })}
            ${ctx.editableText({ tag: 'p', className: 'featured-desc', draftKey: 'featuredDescription', value: desc, publicClass: 'academy-multiline-text', builderClass: 'builder-multiline-text', omitWhenEmpty: true, extraAttrs: 'data-reveal-item' })}
          </div>
          <div class="coverflow-viewport" data-coverflow-viewport role="list" aria-label="학교별 내신 대비">
            <div class="coverflow-track" data-coverflow-track>${cards}</div>
          </div>
          <div class="coverflow-controls">
            <button type="button" class="coverflow-arrow" data-coverflow-prev aria-label="이전 학교" disabled><svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M15 12H9M9 12L13 8M9 12L13 16" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
            <div class="coverflow-indicators" role="tablist" aria-label="학교 선택">${indicators}</div>
            <button type="button" class="coverflow-arrow" data-coverflow-next aria-label="다음 학교"><svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M9 12H15M15 12L11 8M15 12L11 16" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
          </div>
          <div class="featured-cta" data-reveal-item>${ctx.featuredCta(ctaLabel)}</div>
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
  // 위치별 프로세스 설명(generic — 특정 학원/학교/시즌 문자열 없음). 단계 이름은 교육자가
  // builderDraft.aiProcessStepN 으로 편집하지만, 이 한 줄 설명은 코드가 고정한다.
  const AI_PROCESS_STEP_DESC_DEFAULTS = [
    '우리 학교 기출·프린트·수업 자료를 모읍니다.',
    'AI가 반복되는 출제 유형과 배점 패턴을 분석합니다.',
    '오랜 현장 수업 경험과 분석 결과를 결합합니다.',
    '우리 학교 시험 유형에 맞춘 문항을 만듭니다.',
    '실전과 같은 시험지로 시험을 대비합니다.'
  ];
  const AI_PROCESS_NOTE_DEFAULT = 'AI는 교육자를 대체하지 않습니다. 교육자의 판단과 노하우를 넓히는 도구로 씁니다.';

  function aiProcessSteps(view) {
    return AI_PROCESS_STEP_DEFAULTS.map((fallback, index) => draftText(view, 'aiProcessStep' + (index + 1), fallback));
  }

  /**
   * AI Native 단계 아이콘 — 하나의 "정보 오브젝트"(학교 자료)가 단계마다 상태를 바꾼다:
   * 문서 → 스캔/패턴 → 두 노드 병합 → 문항 격자 → 문서+체크. currentColor SVG 도형, 이미지 자산 없음.
   */
  const AI_NATIVE_GLYPHS = [
    '<svg viewBox="0 0 48 48" role="presentation"><rect class="aig-doc" x="12" y="6" width="24" height="36" rx="3"/><line class="aig-line" x1="17" y1="16" x2="31" y2="16"/><line class="aig-line" x1="17" y1="23" x2="31" y2="23"/><line class="aig-line" x1="17" y1="30" x2="25" y2="30"/></svg>',
    '<svg viewBox="0 0 48 48" role="presentation"><rect class="aig-doc" x="12" y="6" width="24" height="36" rx="3"/><rect class="aig-hl" x="16" y="14" width="11" height="6" rx="2"/><rect class="aig-hl" x="16" y="27" width="16" height="6" rx="2"/><line class="aig-scan" x1="7" y1="24" x2="41" y2="24"/></svg>',
    '<svg viewBox="0 0 48 48" role="presentation"><circle class="aig-node" cx="14" cy="14" r="8"/><circle class="aig-node" cx="34" cy="14" r="8"/><path class="aig-merge" d="M14 22 Q24 33 24 38 Q24 33 34 22"/><circle class="aig-core" cx="24" cy="39" r="6"/></svg>',
    '<svg viewBox="0 0 48 48" role="presentation"><rect class="aig-q" x="9" y="10" width="13" height="13" rx="2"/><rect class="aig-q" x="26" y="10" width="13" height="13" rx="2"/><rect class="aig-q" x="9" y="27" width="30" height="11" rx="2"/></svg>',
    '<svg viewBox="0 0 48 48" role="presentation"><rect class="aig-doc" x="9" y="6" width="24" height="36" rx="3"/><line class="aig-line" x1="14" y1="16" x2="27" y2="16"/><line class="aig-line" x1="14" y1="23" x2="27" y2="23"/><circle class="aig-badge" cx="34" cy="34" r="10"/><path class="aig-check" d="M29 34 l4 4 l7 -8"/></svg>'
  ];

  /**
   * AI Native — 하나의 넓은 canvas 안에서 같은 오브젝트들이 계속 이동하며 만드는 하나의 파이프라인.
   *   왼쪽에서 학교 자료 4개가 화면을 가로질러 중앙 BUILD IT BRAIN 으로 들어간다
   *   → Brain 주변에서 EXAMINER DIGITAL TWIN + TEACHER INSIGHT 가 생성돼 Brain 에 결합
   *   → Brain 오른쪽에서 원본 문제·변형·서술형이 나온다
   *   → 문제들이 아래로 모여 한 장의 FINAL EXAM 으로 조립된다.
   * academy_cinematic.js 가 세로 스크롤 progress 0→1 을 이 오브젝트들의 x/y/scale 이동에 직접 연결한다
   * (GSAP timeline, scrub, pin 아님). 5개 정적 scene 교체 구조 아님. connector 선(.ai-flow) 이 흐름을 잇는다.
   *
   * DOM 은 canvas 좌표계(660×460) 안에 절대배치. GSAP 미로드·reduced-motion·데스크톱<900 이면
   * CSS 기본 배치(파이프라인 최종 상태)가 정적으로 보인다. 모바일은 canvas 숨김 + 5 세로 블록.
   */
  const AI_PIPELINE_HTML =
    '<div class="ai-canvas" data-ai-canvas>'
    + '<svg class="ai-flow" data-ai-flow viewBox="0 0 660 460" preserveAspectRatio="none" aria-hidden="true">'
    + '<path class="ai-flow-path" data-ai-flow-path d="M150,150 C232,150 250,225 320,225 C398,225 420,150 470,150 M320,225 C356,300 400,332 432,342"/>'
    + '</svg>'
    + '<div class="ai-src" data-ai-src="0">시험지</div>'
    + '<div class="ai-src" data-ai-src="1">학교 프린트</div>'
    + '<div class="ai-src" data-ai-src="2">교과서</div>'
    + '<div class="ai-src" data-ai-src="3">수업 자료</div>'
    + '<div class="ai-cap ai-cap--src" data-ai-el="capSrc">학교 자료</div>'
    + '<div class="ai-node ai-node--twin" data-ai-node="twin"><span>EXAMINER</span>DIGITAL TWIN</div>'
    + '<div class="ai-node ai-node--insight" data-ai-node="insight"><span>TEACHER</span>INSIGHT</div>'
    + '<div class="ai-brain" data-ai-brain><b>BUILD IT</b><span>BRAIN</span></div>'
    + '<div class="ai-qout" data-ai-qout="0"><b>원본 문제</b><i>18. 다음 글의 목적…</i></div>'
    + '<div class="ai-qout" data-ai-qout="1">변형 A</div>'
    + '<div class="ai-qout" data-ai-qout="2">변형 B</div>'
    + '<div class="ai-qout" data-ai-qout="3">서술형</div>'
    + '<div class="ai-final" data-ai-final><b>FINAL EXAM</b><p>학교별 실전 대비 시험지</p><ol><li></li><li></li><li></li><li></li></ol></div>'
    + '</div>';

  // ── AI Native 모바일 — 단계별 횡스크롤 Story (5개 Scene) ──────────────────
  // 데스크톱 scroll-linked canvas 와 별개. 대표수업(sec-lessons)과 동일한 renderCollectionShell
  // 위에 얹는다(새 carousel 없음). 각 Scene 카드는 자기만의 작은 canvas 를 갖고, academy_cinematic.js
  // wireAiNativeScenes 가 "활성 카드로 들어올 때마다" 그 Scene 애니메이션을 0 부터 1회 재생한다
  // (양방향 — 왼쪽으로 되돌아와도 재생). 자동 다음 장면 전환 없음. mobilePlayed 전체 1회 재생 없음.
  const AI_SCENE_BRAIN = '<div class="scn-brain" data-scn="brain"><b>BUILD IT</b><span>BRAIN</span></div>';
  const AI_SCENE_DEFS = [
    {
      key: 'sources', cap: '학교 자료',
      desc: '시험지·학교 프린트·교과서 등 다양한 학교 자료를 AI WIKI에 축적합니다.',
      canvas:
        '<div class="scn-doc" data-scn="d0">시험지</div>'
        + '<div class="scn-doc" data-scn="d1">학교 프린트</div>'
        + '<div class="scn-doc" data-scn="d2">교과서</div>'
        + '<div class="scn-doc" data-scn="d3">수업 자료</div>'
        + AI_SCENE_BRAIN
    },
    {
      key: 'brain', cap: 'BUILD IT BRAIN',
      desc: '학교 자료에 박원장님의 수업 인사이트와 노하우를 더해 하나의 교육 두뇌를 만듭니다.',
      canvas:
        '<span class="scn-ring" data-scn="r0"></span>'
        + '<span class="scn-ring" data-scn="r1"></span>'
        + '<div class="scn-brain scn-brain--lg" data-scn="brain"><b>BUILD IT</b><span>BRAIN</span></div>'
        + '<div class="scn-chip" data-scn="c0">시험 경향</div>'
        + '<div class="scn-chip" data-scn="c1">배점 패턴</div>'
        + '<div class="scn-chip" data-scn="c2">출제 포인트</div>'
    },
    {
      key: 'twin', cap: 'Digital Twin + Insight',
      desc: '출제자의 문제 패턴과 박원장님의 수업 인사이트를 결합해 우리 학교 시험을 이해합니다.',
      canvas:
        '<div class="scn-brain scn-brain--lg" data-scn="brain"><b>BUILD IT</b><span>BRAIN</span></div>'
        + '<div class="scn-node scn-node--twin" data-scn="twin"><span>EXAMINER</span>DIGITAL TWIN</div>'
        + '<div class="scn-node scn-node--insight" data-scn="insight"><span>TEACHER</span>INSIGHT</div>'
    },
    {
      key: 'questions', cap: '문제 생성',
      desc: '학교의 출제 패턴과 실제 수업 내용을 반영해 새로운 문제를 다양하게 만듭니다.',
      canvas:
        AI_SCENE_BRAIN
        + '<div class="scn-q" data-scn="q0"><b>원본 문제</b></div>'
        + '<div class="scn-q" data-scn="q1">변형 A</div>'
        + '<div class="scn-q" data-scn="q2">변형 B</div>'
        + '<div class="scn-q" data-scn="q3">서술형</div>'
    },
    {
      key: 'final', cap: 'FINAL EXAM',
      desc: 'AI가 만든 문제를 선별·조합해 우리 학생들을 위한 맞춤 시험을 완성합니다.',
      canvas:
        '<div class="scn-q scn-q--mini" data-scn="q0">원본</div>'
        + '<div class="scn-q scn-q--mini" data-scn="q1">변형 A</div>'
        + '<div class="scn-q scn-q--mini" data-scn="q2">변형 B</div>'
        + '<div class="scn-q scn-q--mini" data-scn="q3">서술형</div>'
        + '<div class="scn-final" data-scn="final"><b>FINAL EXAM</b><p>학교별 실전 대비 시험지</p><ol><li></li><li></li><li></li><li></li></ol></div>'
    }
  ];

  function aiScenesMobileHtml() {
    const cards = AI_SCENE_DEFS.map((scene, i) =>
      `<figure class="ai-scene" data-ai-scene="${i}" data-ai-scene-key="${scene.key}" aria-hidden="true">`
      + `<div class="ai-scene-canvas" data-ai-scene-canvas>${scene.canvas}</div>`
      + `<figcaption class="ai-scene-cap"><b>${i + 1}</b> ${escapeHtml(scene.cap)}</figcaption>`
      + `<p class="ai-scene-desc">${escapeHtml(scene.desc)}</p>`
      + `</figure>`);
    return `<div class="ai-scenes-mobile" data-ai-scenes>`
      + renderCollectionShell({
        navLabel: 'AI Native 단계',
        variant: 'ai-scenes',
        hint: '옆으로 밀어 단계별로 보기',
        cards: cards
      })
      + `</div>`;
  }

  function aiNativeHtml(steps, ctx) {
    const items = (steps || [])
      .map((s, i) => ({ i, name: textValue(s) || AI_PROCESS_STEP_DEFAULTS[i] || '' }))
      .filter((s) => s.i === 0 || s.name);
    if (!items.length) return '';
    const glyphOf = (i) => AI_NATIVE_GLYPHS[i] || AI_NATIVE_GLYPHS[AI_NATIVE_GLYPHS.length - 1];
    const blocks = items.map((s) => {
      const desc = AI_PROCESS_STEP_DESC_DEFAULTS[s.i] || '';
      const isTwin = s.i === 1;
      const isFinal = s.i === 4;
      return `<li class="ai-native-step" data-ai-step="${s.i}" data-reveal-item data-ai-step-index="${s.i}">
        <span class="ai-native-step-num">${s.i + 1}</span>
        <div class="ai-native-step-c">
          <span class="ai-native-step-glyph${isTwin ? ' is-twin' : ''}${isFinal ? ' is-final' : ''}" aria-hidden="true">${glyphOf(s.i)}</span>
          <div class="ai-native-step-text">
            <h3 class="ai-native-step-name">${ctx.aiStepName(s.i, s.name)}</h3>
            ${desc ? `<p class="ai-native-step-desc">${escapeHtml(desc)}</p>` : ''}
          </div>
          ${isFinal ? '<span class="ai-native-final-badge" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><path d="M20 6L9 17l-5-5" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg></span>' : ''}
        </div>
        ${s.i < items.length - 1 ? '<span class="ai-native-connection" data-ai-connection></span>' : ''}
      </li>`;
    }).join('');
    // 데스크톱: 수평 파이프라인 (sticky visual + 우측 단계 리스트, academy_cinematic.js 스크롤 연동)
    // 모바일: 수직 스택 + 연결선 + 단계별 횡스크롤 Story (.ai-scenes-mobile)
    return `<div class="ai-native" data-reveal>
        <figure class="ai-native-visual" data-ai-visual aria-hidden="true">
          <div class="ai-stage" data-ai-stage>${AI_PIPELINE_HTML}</div>
        </figure>
        <ol class="ai-native-steps">${blocks}</ol>
      </div>${ctx.aiScenesMobile ? ctx.aiScenesMobile() : ''}`;
  }

  function renderAiProcess(view, ctx) {
    if (!view.sectionVisible('sec-digitaltwin')) return '';
    const eyebrow = draftText(view, 'aiProcessEyebrow', AI_PROCESS_EYEBROW_DEFAULT);
    const title = draftText(view, 'aiProcessTitle', AI_PROCESS_TITLE_DEFAULT);
    const desc = draftText(view, 'aiProcessDescription', AI_PROCESS_DESC_DEFAULT);
    const note = draftText(view, 'aiProcessNote', AI_PROCESS_NOTE_DEFAULT);
    return `<section id="sec-digitaltwin" data-builder-section-key="sec-digitaltwin" class="section-wrap ai-native-wrap">
        <div class="section-inner ai-native-inner">
          <div class="section-header ai-native-head" data-reveal>
            <div class="eyebrow ai-native-eyebrow" data-reveal-item><span class="stroke"></span>${ctx.editableText({ tag: 'span', className: 'ai-native-eyebrow-text', draftKey: 'aiProcessEyebrow', value: eyebrow, omitWhenEmpty: true })}</div>
            ${ctx.editableText({ tag: 'h2', className: 'section-title ai-native-title', draftKey: 'aiProcessTitle', value: title, builderClass: 'builder-multiline-text', omitWhenEmpty: true, extraAttrs: 'data-reveal-item' })}
            ${ctx.editableText({ tag: 'p', className: 'ai-native-desc', draftKey: 'aiProcessDescription', value: desc, publicClass: 'academy-multiline-text', builderClass: 'builder-multiline-text', omitWhenEmpty: true, extraAttrs: 'data-reveal-item' })}
          </div>
          ${aiNativeHtml(aiProcessSteps(view), ctx)}
          ${ctx.editableText({ tag: 'p', className: 'ai-native-note', draftKey: 'aiProcessNote', value: note, publicClass: 'academy-multiline-text', builderClass: 'builder-multiline-text', quoted: false, omitWhenEmpty: true, extraAttrs: 'data-reveal data-reveal-item' })}
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
    return `<section id="sec-principal" data-builder-section-key="sec-principal" class="section-wrap principal-wrap" data-reveal data-reveal-item>
        <div class="principal-inner">
          <div class="principal-head"><span class="section-badge">🎓 대표 원장 소개</span></div>
          <div class="principal-grid">
            <div class="principal-card">
              <div class="principal-info">
                ${ctx.editableText({ tag: 'div', className: 'principal-name', draftKey: 'principalName', value: name })}
                ${ctx.editableText({ tag: 'div', className: 'principal-role', draftKey: 'principalRole', value: role })}
                ${ctx.editableText({ tag: 'div', className: 'principal-desc', draftKey: 'principalDesc', value: desc, publicClass: 'academy-multiline-text', builderClass: 'builder-multiline-text' })}
                ${ctx.aiAssistButton('PRINCIPAL_INTRO', 'principalDesc')}
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

  /** 시안 우측 컬럼 — iPad 프레임 안에 원장 사진/영상 순환. 캡션은 원장명 + 학원명. */
  function renderPortraitTablet(view, ctx) {
    const name = draftText(view, 'principalName', '원장');
    const photoUrl = ctx.hasHeroMedia(view, 'principal')
      ? ctx.heroMediaUrl('principal')
      : null;
    const videoUrl = '/video/eduholic/principal-intro.mp4';

    const photoHtml = photoUrl
      ? `<img class="tablet-photo" src="${escapeHtml(photoUrl)}" alt="${escapeHtml(view.name)} 원장 사진">`
      : '<div class="tablet-empty"><span>원장 대표사진</span></div>';
    const videoHtml = `<video class="tablet-video" playsinline muted preload="metadata" poster="${escapeHtml(photoUrl || '')}">
      <source src="${escapeHtml(videoUrl)}" type="video/mp4">
    </video>`;

    const soundToggleHtml = `<button type="button" class="tablet-sound-toggle" aria-label="소리 켜기/끄기" aria-pressed="false" title="소리 켜기">🔇</button>`;
    const soundHintHtml = `<div class="tablet-sound-hint" aria-hidden="true">소리와 함께 보기 🔊</div>`;

    return `<div class="tablet" data-reveal-item${ctx.portraitClusterAttrs()}>
            <div class="cam"></div>
            <div class="tablet-screen">
              <div class="tablet-media-stack">
                ${photoHtml}
                ${videoHtml}
              </div>
              ${soundToggleHtml}
              ${soundHintHtml}
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

  // ── P0 보완: 프리셋별 히어로 DOM 차별화 ────────────────────────────────────
  // designPreset이 없으면 기존 renderHeroDefault를 그대로 써서 기존 테넌트(에듀홀릭 등)
  // 렌더 결과가 바이트 단위로 동일하게 유지된다.
  const DESIGN_PRESET_IDS = ['EXAM', 'ENGLISH', 'ARTS', 'STUDY', 'ONLINE'];

  function designPresetOf(view) {
    const id = draftText(view, 'designPreset', '');
    return DESIGN_PRESET_IDS.indexOf(id) >= 0 ? id : null;
  }

  /** 프리셋별 원장 사진 — 검은색 태블릿이 아닌 각 프리셋 컨셉에 맞는 프레임. */
  function principalPhotoHtml(view, ctx, frameClass, captionHtml) {
    const name = draftText(view, 'principalName', '원장');
    const photoUrl = ctx.hasHeroMedia(view, 'principal')
      ? ctx.heroMediaUrl('principal')
      : null;
    const imgHtml = photoUrl
      ? `<img class="principal-photo-img" src="${escapeAttr(photoUrl)}" alt="${escapeHtml(view.name)} 원장 사진" loading="lazy">`
      : `<div class="principal-photo-empty"><span>원장 사진</span></div>`;
    return `<figure class="principal-photo ${frameClass}"${ctx.portraitClusterAttrs ? ctx.portraitClusterAttrs() : ''}>
      ${imgHtml}
      <figcaption class="principal-photo-cap">${captionHtml || `<b>${escapeHtml(name)}</b> <span>${escapeHtml(view.name)}</span>`}</figcaption>
      ${ctx.portraitClusterExtra ? ctx.portraitClusterExtra() : ''}
    </figure>`;
  }

  function heroEyebrowHtml(view, ctx) {
    const eyebrow = draftText(view, 'principalName', '');
    return eyebrow
      ? `<div class="eyebrow" data-reveal-item><span class="stroke"></span>${ctx.heroEyebrow(eyebrow)}</div>`
      : '';
  }

  function heroTitleHtml(view, ctx) {
    return `<h1 data-reveal-item>${ctx.heroTitleInner(view.name)}</h1>`;
  }

  function heroIntroHtml(view, ctx) {
    return ctx.editableText({ tag: 'blockquote', className: 'hero-quote', anchor: 'text', draftKey: 'intro', value: view.intro, builderClass: 'builder-multiline-text', extraAttrs: 'data-reveal-item' });
  }

  function heroStatsHtml(view, ctx, itemClass) {
    const stats = HERO_STATS.map((stat) =>
      `<div class="${itemClass || ''}"><b>${ctx.heroStatValue(stat.draftKey, draftText(view, stat.draftKey, '—'))}</b><span>${stat.label}</span></div>`
    ).join('');
    return `<div class="hero-stats" data-reveal-item>${stats}</div>`;
  }

  // EXAM — 중앙 정렬, 실적 숫자 3개 prominently, CTA 버튼 2개 나란히
  function renderHeroExam(view, ctx) {
    // 실적 데이터가 있는 항목만 표시 (빈 값은 숨김)
    const examStats = HERO_STATS.map((stat) => {
      const val = draftText(view, stat.draftKey, '');
      if (!val || val === '—') return '';
      return `<div class="hero-exam-stat"><b>${ctx.heroStatValue(stat.draftKey, escapeHtml(val))}</b><span>${stat.label}</span></div>`;
    }).join('');
    const statsHtml = examStats
      ? `<div class="hero-exam-stats" data-reveal-item>${examStats}</div>`
      : '';
    return `<section class="hero hero-exam" id="sec-hero" data-builder-section-key="sec-hero" data-design-preset="EXAM" data-reveal>
        <div class="brand-glow" aria-hidden="true"></div>
        <div class="hero-exam-inner">
          ${heroEyebrowHtml(view, ctx)}
          ${heroTitleHtml(view, ctx)}
          ${heroIntroHtml(view, ctx)}
          ${statsHtml}
          <div class="hero-exam-actions" data-reveal-item>
            <button type="button" class="btn" onclick="requestConsult()">상담 신청하기</button>
            <button type="button" class="btn outline" onclick="scrollToId('sec-lessons')">커리큘럼 보기</button>
          </div>
          ${ctx.heroBadges(view)}
        </div>
        <div class="hero-exam-principal" data-reveal-item>
          ${principalPhotoHtml(view, ctx, 'principal-frame-angular')}
        </div>
      </section>`;
  }

  // ENGLISH — 2열 (좌측 텍스트 + 우측 이미지), 둥근 CTA 버튼 1개
  function renderHeroEnglish(view, ctx) {
    return `<section class="hero hero-english" id="sec-hero" data-builder-section-key="sec-hero" data-design-preset="ENGLISH" data-reveal>
        <div class="brand-glow" aria-hidden="true"></div>
        <div class="hero-english-grid">
          <div class="hero-english-text">
            ${heroEyebrowHtml(view, ctx)}
            ${heroTitleHtml(view, ctx)}
            ${heroIntroHtml(view, ctx)}
            <div class="hero-english-actions" data-reveal-item>
              <button type="button" class="btn btn-rounded" onclick="requestConsult()">무료 레벨테스트 신청</button>
            </div>
            ${ctx.heroBadges(view)}
          </div>
          <div class="hero-english-visual" data-reveal-item>
            ${principalPhotoHtml(view, ctx, 'principal-frame-circle-bright')}
          </div>
        </div>
      </section>`;
  }

  // ARTS — 풀스크린 배경 이미지 + 오버레이 텍스트 + 갤러리 스트립
  function renderHeroArts(view, ctx) {
    const bgUrl = ctx.hasHeroMedia(view, 'principal') ? ctx.heroMediaUrl('principal') : null;
    const galleryItems = (view.lessons || []).slice(0, 3).map((lesson, i) =>
      `<div class="hero-arts-gallery-item"><span class="hero-arts-gallery-num">0${i + 1}</span><span>${escapeHtml(lesson.title || lesson.name || '작품')}</span></div>`
    ).join('') || '<div class="hero-arts-gallery-item"><span>갤러리 준비 중</span></div>';
    return `<section class="hero hero-arts" id="sec-hero" data-builder-section-key="sec-hero" data-design-preset="ARTS" data-reveal>
        ${bgUrl ? `<div class="hero-arts-bg" style="background-image:url('${escapeAttr(bgUrl)}')" aria-hidden="true"></div>` : '<div class="hero-arts-bg hero-arts-bg-empty" aria-hidden="true"></div>'}
        <div class="hero-arts-overlay">
          ${heroEyebrowHtml(view, ctx)}
          ${heroTitleHtml(view, ctx)}
          ${heroIntroHtml(view, ctx)}
          <div class="hero-arts-actions" data-reveal-item>
            <button type="button" class="btn btn-ghost" onclick="scrollToId('sec-videos')">작품 보기</button>
          </div>
        </div>
        <div class="hero-arts-gallery" data-reveal-item>${galleryItems}</div>
      </section>`;
  }

  // STUDY — 원형 원장 사진 + 소개 나란히, 손글씨 캡션
  function renderHeroStudy(view, ctx) {
    const rawName = draftText(view, 'principalName', '원장');
    // "김원장"처럼 이미 원장이 포함된 경우 중복 방지
    const name = rawName.replace(/\s*원장\s*$/, '');
    const caption = `<b class="handwritten">${escapeHtml(name)} 원장</b> <span>${escapeHtml(view.name)}에서 직접 지도합니다</span>`;
    return `<section class="hero hero-study" id="sec-hero" data-builder-section-key="sec-hero" data-design-preset="STUDY" data-reveal>
        <div class="brand-glow" aria-hidden="true"></div>
        <div class="hero-study-wrap">
          <div class="hero-study-photo" data-reveal-item>
            ${principalPhotoHtml(view, ctx, 'principal-frame-circle-warm', caption)}
          </div>
          <div class="hero-study-text">
            ${heroEyebrowHtml(view, ctx)}
            ${heroTitleHtml(view, ctx)}
            ${heroIntroHtml(view, ctx)}
            <div class="hero-study-actions" data-reveal-item>
              <button type="button" class="btn" onclick="requestConsult()">원장과 상담하기</button>
            </div>
            ${ctx.heroBadges(view)}
          </div>
        </div>
      </section>`;
  }

  // ONLINE — 그라데이션 배경 + 강의 썸네일 카드 3개 + 영상 재생 버튼
  function renderHeroOnline(view, ctx) {
    const cards = (view.lessons || []).slice(0, 3).map((lesson, i) =>
      `<button type="button" class="hero-online-card" onclick="scrollToId('sec-lessons')">
        <span class="hero-online-card-thumb">${escapeHtml(String(i + 1).padStart(2, '0'))}</span>
        <span class="hero-online-card-title">${escapeHtml(lesson.title || lesson.name || '강의 ' + (i + 1))}</span>
        <span class="hero-online-card-play" aria-hidden="true">▶</span>
      </button>`
    ).join('') || '<div class="hero-online-card hero-online-card-empty"><span>강의 준비 중</span></div>';
    return `<section class="hero hero-online" id="sec-hero" data-builder-section-key="sec-hero" data-design-preset="ONLINE" data-reveal>
        <div class="hero-online-gradient" aria-hidden="true"></div>
        <div class="hero-online-head">
          ${heroEyebrowHtml(view, ctx)}
          ${heroTitleHtml(view, ctx)}
          ${heroIntroHtml(view, ctx)}
        </div>
        <div class="hero-online-cards" data-reveal-item>${cards}</div>
        <div class="hero-online-actions" data-reveal-item>
          <button type="button" class="btn btn-play" onclick="scrollToId('sec-videos')"><span aria-hidden="true">▶</span> 소개 영상 보기</button>
          <button type="button" class="btn outline" onclick="requestConsult()">수강 신청</button>
        </div>
      </section>`;
  }

  function renderHeroDefault(view, ctx) {
    const eyebrow = draftText(view, 'principalName', '');
    const stats = HERO_STATS.map((stat) =>
      `<div><b>${ctx.heroStatValue(stat.draftKey, draftText(view, stat.draftKey, '—'))}</b><span>${stat.label}</span></div>`
    ).join('');
    return `<section class="hero" id="sec-hero" data-builder-section-key="sec-hero" data-reveal>
        <div class="brand-glow" aria-hidden="true"></div>
        <div class="hero-grid">
          <div>
            ${eyebrow ? `<div class="eyebrow" data-reveal-item><span class="stroke"></span>${ctx.heroEyebrow(eyebrow)}</div>` : ''}
            <h1 data-reveal-item>${ctx.heroTitleInner(view.name)}</h1>
            ${ctx.editableText({ tag: 'blockquote', className: 'hero-quote', anchor: 'text', draftKey: 'intro', value: view.intro, builderClass: 'builder-multiline-text', extraAttrs: 'data-reveal-item' })}
            <div class="hero-actions" data-reveal-item>
              <button type="button" class="btn" onclick="requestConsult()">상담 신청하기</button>
              <button type="button" class="btn outline" onclick="scrollToId('sec-lessons')">대표 수업 보기</button>
            </div>
            <div class="hero-stats" data-reveal-item>${stats}</div>
            ${ctx.heroBadges(view)}
          </div>
          ${renderPortraitTablet(view, ctx)}
        </div>
      </section>`;
  }

  function renderHero(view, ctx) {
    const preset = designPresetOf(view);
    if (preset === 'EXAM') return renderHeroExam(view, ctx);
    if (preset === 'ENGLISH') return renderHeroEnglish(view, ctx);
    if (preset === 'ARTS') return renderHeroArts(view, ctx);
    if (preset === 'STUDY') return renderHeroStudy(view, ctx);
    if (preset === 'ONLINE') return renderHeroOnline(view, ctx);
    return renderHeroDefault(view, ctx);
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

  // ── Horizontal Content Collection (PHASE 3-B) ─────────────────────────────
  // 재사용 가능한 가로 컬렉션 한 벌. sec-lessons(Program) / sec-videos(Video)가 공유한다.
  //   구조:  Collection → head(hint + nav) → viewport → track → card
  //   Program Card / Video Card 는 이 위의 card *type* 이다(별도 캐러셀을 만들지 않는다).
  // 렌더는 순수 문자열이고, 배선(화살표 스크롤·카드 클릭 재생)은 academy_collection.js가 담당한다.
  // 다음 단계에서 Mini Lectures / School Exam Contents 등도 같은 shell + card type으로 올린다.

  /**
   * 섹션 레이아웃 선택. Phase 2부터 가로 컬렉션이 기본이다 — draft 값이 명시적으로
   * 'vertical'일 때만 기존(세로) 레이아웃으로 되돌아간다. 미설정/'horizontal' 모두 가로.
   * (Phase 3-B 도입 당시엔 반대로 'horizontal'만 opt-in이었다 — Eduholic 한 곳만 검증한
   * 뒤 전체 기본값으로 승격했다.)
   */
  function isHorizontalCollection(view, key) {
    return String(draftText(view, key, '') || '').trim().toLowerCase() !== 'vertical';
  }

  function textValue(value) {
    return value == null ? '' : String(value).trim();
  }

  // ── Common Video Playback (V2-8): Media Viewport 계약 ─────────────────────
  // 하나의 재사용 primitive. Actual Class(Phone Card)와 대표 수업(Program Card) 영상이
  // 모두 이 계약을 쓴다.
  //
  //   Media Viewport (.f2r-media-viewport)  — 크기·비율·overflow·radius 를 소유. 재생 중에도 유지.
  //   ├ Thumbnail + ▶  (.f2r-media-play button, position:absolute inset:0)   재생 전
  //   └ Player          (.f2r-media-player,       position:absolute inset:0)   재생 후 (academy_collection.js)
  //
  // 배선(academy_collection.js onPlayClick)은 트리거 버튼을 "치환"하지 않고, 뷰포트 "안에서"
  // 플레이어를 덧붙인 뒤 트리거를 숨긴다 → 외곽 크기가 절대 바뀌지 않는다.
  // 원본 비율은 CSS object-fit:contain 으로 보존한다(letterbox, crop 금지).

  /** 업로드 영상의 width/height 로 뷰포트 비율을 정한다. 모르면 null(어댑터 기본 비율 사용). */
  function mediaAspectRatio(width, height) {
    const w = Number(width);
    const h = Number(height);
    if (!(w > 0) || !(h > 0)) return null;
    // 9:16(세로) ~ 16:9(가로) 범위로 클램프 — 카드가 극단적으로 길거나 납작해지지 않게.
    const ratio = Math.max(9 / 16, Math.min(16 / 9, w / h));
    return `${Math.round(ratio * 1000)} / 1000`;
  }

  /**
   * 대표 수업 IMAGE 미디어는 업로드 시점에 width/height 를 저장하지 않으므로
   * 렌더 후 <img> 가 로드된 시점에 naturalWidth/Height 를 측정해 컨테이너의
   * --f2r-media-aspect 를 채운다(영상 stage와 동일한 클램프 로직 재사용).
   * 이미 처리된 박스는 data-media-fit-done 으로 건너뛴다(재렌더 시 중복 바인딩 방지).
   */
  function applyImageMediaAspect(scopeEl) {
    const root = scopeEl || (typeof document !== 'undefined' ? document : null);
    if (!root || !root.querySelectorAll) return;
    root.querySelectorAll('.f2r-program-card-media[data-media-fit="image"]:not([data-media-fit-done])')
      .forEach((box) => {
        const img = box.querySelector('img');
        if (!img) return;
        const apply = () => {
          const aspect = mediaAspectRatio(img.naturalWidth, img.naturalHeight);
          if (aspect) box.style.setProperty('--f2r-media-aspect', aspect);
          box.setAttribute('data-media-fit-done', '1');
        };
        if (img.complete && img.naturalWidth) apply();
        else img.addEventListener('load', apply, { once: true });
      });
  }

  /**
   * Media Viewport 마크업.
   *   viewportClass : 섹션 어댑터 클래스(.f2r-phone-screen / .f2r-program-card-media …)
   *   face          : 썸네일 + ▶ + 길이 배지 HTML
   *   playable      : false 면 정적(빌더 캔버스) — 버튼 없이 face 만
   *   aspect        : "W / H" 문자열이면 뷰포트 aspect-ratio 를 그 값으로(어댑터 기본 대신)
   */
  function mediaViewportHtml(opts) {
    const o = opts || {};
    const cls = `${o.viewportClass} f2r-media-viewport`;
    const style = o.aspect ? ` style="--f2r-media-aspect:${escapeAttr(o.aspect)}"` : '';
    if (!o.playable || (!o.src && !o.embed)) {
      return `<div class="${cls}"${style}>${o.face || ''}</div>`;
    }
    const data = [
      o.src ? `data-video-src="${escapeAttr(o.src)}"` : '',
      o.embed ? `data-video-embed="${escapeAttr(o.embed)}"` : '',
      o.poster ? `data-video-poster="${escapeAttr(o.poster)}"` : ''
    ].filter(Boolean).join(' ');
    return `<div class="${cls}"${style}>`
      + `<button type="button" class="f2r-media-play" data-collection-play ${data} aria-label="${escapeAttr(o.label || '영상 재생')}">`
      + `${o.face || ''}</button></div>`;
  }

  /**
   * 컬렉션 공통 shell. cards는 카드 *내용* HTML 문자열 배열이고 여기서 <li>로 감싼다.
   * staticMode(빌더 캔버스): 화살표 없음 + track이 가로 스크롤 대신 wrap(편집 UX / public 분리).
   */
  function pad2(n) { return n < 10 ? '0' + n : String(n); }

  function renderCollectionShell(options) {
    const o = options || {};
    const cards = Array.isArray(o.cards) ? o.cards : [];
    const label = escapeAttr(o.navLabel || '카드');
    // 진행 카운터(NN / MM). 배선(academy_collection.js)이 스크롤 위치로 current 를 갱신한다.
    // staticMode(빌더 캔버스)에는 넣지 않는다 — 스크롤이 없어 의미가 없다.
    const progress = o.staticMode
      ? ''
      : `<span class="f2r-collection-progress" aria-hidden="true"><span data-collection-current>01</span><span class="f2r-collection-progress-sep">/</span><span data-collection-total>${pad2(cards.length)}</span></span>`;
    const nav = o.staticMode
      ? ''
      : `<div class="f2r-collection-nav" data-collection-nav>
          <button type="button" class="f2r-collection-arrow" data-collection-scroll="prev" aria-label="${label} 이전으로" disabled>&#8249;</button>
          <button type="button" class="f2r-collection-arrow" data-collection-scroll="next" aria-label="${label} 다음으로">&#8250;</button>
        </div>`;
    const hint = o.hint ? escapeHtml(o.hint) : '';
    const variant = o.variant ? ` f2r-collection--${escapeAttr(o.variant)}` : '';
    // 자동 이동 없음. 데스크톱에서 카드가 다 들어가면 CSS 가 grid 로(head/화살표 숨김),
    // 모바일에서는 네이티브 scroll-snap + swipe + 다음 카드 peek + 진행 카운터가 어포던스.
    return `<div class="f2r-collection${variant}"${o.staticMode ? ' data-collection-static' : ''} data-collection>
        <div class="f2r-collection-head">
          <span class="f2r-collection-hint">${hint}</span>
          <div class="f2r-collection-controls">${progress}${nav}</div>
        </div>
        <div class="f2r-collection-viewport">
          <ul class="f2r-collection-track" data-collection-track role="list">
            ${cards.map((card) => `<li class="f2r-collection-card">${card}</li>`).join('\n            ')}
          </ul>
        </div>
      </div>`;
  }

  /**
   * Program Card (V2). 하나의 작은 Web Experience 처럼 보이게 한다:
   *   VISUAL STAGE(대표 미디어) → 프로그램 라벨(tabShortName) → 이름 → 한 줄 설명(kicker)
   *   → 이런 학생에게(learnerNeed) → 핵심 특징(highlight) → 대상(gradeLabel/tabGuidanceLabel) → 상담 CTA
   * 존재하지 않는 필드는 렌더하지 않는다(가짜 target/student 데이터를 만들지 않는다).
   * 대표 미디어는 ctx.programCardMedia 가 화면별(공개/빌더) 경로로 조립한다 —
   * lesson_products.media_image_key / media_video_id 로 이미 등록된 자료를 그대로 쓴다.
   */
  function programCardHtml(lesson, ctx) {
    const data = lesson || {};
    const media = ctx.programCardMedia ? ctx.programCardMedia(data) : '';
    const eyebrow = textValue(data.tabShortName);
    const name = textValue(data.name) || eyebrow;
    const kicker = textValue(data.kicker);
    const need = textValue(data.learnerNeed);
    const highlight = textValue(data.highlight);
    const grade = textValue(data.gradeLabel) || textValue(data.tabGuidanceLabel);
    const body = [];
    if (eyebrow && eyebrow !== name) body.push(`<div class="f2r-program-card-eyebrow">${escapeHtml(eyebrow)}</div>`);
    if (name) body.push(`<div class="f2r-program-card-name">${escapeHtml(name)}</div>`);
    if (kicker) body.push(`<div class="f2r-program-card-kicker">${escapeHtml(kicker)}</div>`);
    if (need) body.push(`<div class="f2r-program-card-need">${escapeHtml(need)}</div>`);
    if (highlight) body.push(`<div class="f2r-program-card-highlight">${escapeHtml(highlight)}</div>`);
    if (grade) body.push(`<div class="f2r-program-card-grade">${escapeHtml(grade)}</div>`);
    body.push(ctx.collectionConsultCta('상담 신청하기'));
    return `<article class="f2r-program-card${media ? ' has-media' : ''}">${media}<div class="f2r-program-card-body">${body.join('')}</div></article>`;
  }

  /**
   * Video Card = Phone Screen (V2-3). 기존 콘텐츠(업로드된 세로 숏폼)를 보존하되,
   * 일반 video 카드가 아니라 "휴대폰 화면을 넘겨본다"는 경험으로 프레임에 담는다.
   * 세로 영상은 화면을 채우고(object-fit:cover), 가로 영상은 letterbox(is-landscape).
   * 클릭 시 그 자리에서 재생(sec-videos 카드와 같은 data-collection-play 배선).
   */
  function videoCardHtml(video, ctx) {
    const data = video || {};
    const landscape = Number(data.width) > 0 && Number(data.height) > 0 && Number(data.width) > Number(data.height);
    const thumb = data.thumbnailUrl
      ? `<img src="${escapeAttr(data.thumbnailUrl)}" alt="" loading="lazy">`
      : '<span class="f2r-phone-screen-ph" aria-hidden="true">🎬</span>';
    const dur = data.durationSec != null
      ? `<span class="f2r-video-card-dur">${formatDuration(data.durationSec)}</span>`
      : '';
    const face = `${thumb}<span class="f2r-video-play" aria-hidden="true">▶</span>${dur}`;
    return `<article class="f2r-phone-card${landscape ? ' is-landscape' : ''}">
        <div class="f2r-phone-frame">
          <span class="f2r-phone-cam" aria-hidden="true"></span>
          ${ctx.collectionVideoMedia(data, face)}
        </div>
      </article>`;
  }

  function lessonCollectionHtml(lessons, ctx, opts) {
    const items = (lessons || []).filter(Boolean);
    if (!items.length) return `<p class="lesson-empty">${escapeHtml((opts && opts.emptyMessage) || '아직 등록된 수업이 없습니다.')}</p>`;
    return renderCollectionShell({
      navLabel: '프로그램',
      variant: 'programs',
      hint: (opts && opts.hint) || '좌우로 밀어 프로그램을 둘러보세요',
      staticMode: !!(opts && opts.staticMode),
      cards: items.map((lesson) => programCardHtml(lesson, ctx))
    });
  }

  function videoCollectionHtml(videos, ctx, opts) {
    const items = (videos || []).filter(Boolean);
    if (!items.length) return VIDEO_EMPTY_PLACEHOLDER;
    return renderCollectionShell({
      navLabel: '수업 영상',
      variant: 'videos',
      hint: (opts && opts.hint) || '좌우로 밀어 영상을 둘러보세요',
      staticMode: !!(opts && opts.staticMode),
      cards: items.map((video) => videoCardHtml(video, ctx))
    });
  }

  /** 학원마다 달라야 하는 제목이라 기본값도 학원명에서 파생한다(고정 문구 금지). */
  function videosTitleDefault(view) {
    const name = String((view && view.name) || '').trim();
    return name ? name + '의 수업 현장' : '수업 현장';
  }

  function renderVideos(view, ctx) {
    if (!view.sectionVisible('sec-videos')) return '';
    // sec-videos(수업 현장)는 명시적으로 LESSON 분류된 영상만 노출한다(Phase 3). 미지정
    // (purpose 도입 이전 레거시 포함)은 "아직 분류 안 됨"으로 취급해 공개 화면에 자동
    // 노출하지 않는다 — 영상 관리에서 분류해야 나타난다.
    const videos = (ctx.videoList() || []).filter(isLessonVideo);
    const body = isHorizontalCollection(view, 'videosLayout')
      ? ctx.videoCollection(videos)
      : (videos.length ? videoPlaylist(videos, ctx) : VIDEO_EMPTY_PLACEHOLDER);
    const title = draftText(view, 'videosSectionTitle', videosTitleDefault(view));
    return `<section id="sec-videos" data-builder-section-key="sec-videos" class="section-wrap">
      <div class="section-inner">
        <div class="section-header">
          ${ctx.editableText({ tag: 'h2', className: 'section-title', draftKey: 'videosSectionTitle', value: title, omitWhenEmpty: true })}
        </div>
        ${body}
        ${ctx.videosFooter(view)}
      </div>
    </section>`;
  }

  // ── sec-minilectures (V2-9) ──────────────────────────────────────────────
  // Actual Class(sec-videos) = "실제 수업 현장/분위기/신뢰".
  // Mini Lectures = "교육자가 직접 설명하는 짧은 학습 콘텐츠 → 전문성/설명력/교육 철학".
  // 두 컬렉션의 역할을 섞지 않는다. opt-in(기본 OFF). 콘텐츠는 academy_videos 재사용 —
  // 강의별 제목/학습포인트/대상만 builderDraft.miniLectures JSON 배열에 담는다(스키마 변경 없음).
  // 재생은 V2-8 공통 Media Viewport primitive(mediaViewportHtml) 를 그대로 쓴다.

  /** builderDraft.miniLectures(JSON 배열 문자열) → [{v,t,n,g}] 정규화. 깨진 항목은 버린다. */
  function miniLectureItems(view) {
    const raw = draftText(view, 'miniLectures', '');
    if (!raw) return [];
    let parsed;
    try { parsed = JSON.parse(raw); } catch (_) { return []; }
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((e) => (e && typeof e === 'object'
        ? { v: Number(e.v), t: textValue(e.t), n: textValue(e.n), g: textValue(e.g) }
        : null))
      .filter((e) => e && e.v > 0)
      .slice(0, 12);
  }

  function miniLecturesTitleDefault(view) {
    const name = String((view && view.name) || '').trim();
    return name ? name + ' 미니 강의' : '미니 강의';
  }

  /** Mini Lecture Card = 미디어(재생) + 대상(g) + 제목(t) + 학습 포인트(n). CTA 없음. */
  function miniLectureCardHtml(item, video, ctx) {
    const v = video || {};
    const dur = v.durationSec != null
      ? `<span class="f2r-video-card-dur">${formatDuration(v.durationSec)}</span>` : '';
    const thumb = v.thumbnailUrl
      ? `<img src="${escapeAttr(v.thumbnailUrl)}" alt="" loading="lazy">`
      : '<span class="f2r-lecture-media-ph" aria-hidden="true">🎓</span>';
    const face = `${thumb}<span class="f2r-video-play" aria-hidden="true">▶</span>${dur}`;
    const media = ctx.miniLectureMedia(v, face, mediaAspectRatio(v.width, v.height));
    const body = [];
    if (item.g) body.push(`<div class="f2r-lecture-card-tag">${escapeHtml(item.g)}</div>`);
    if (item.t) body.push(`<div class="f2r-lecture-card-title">${escapeHtml(item.t)}</div>`);
    if (item.n) body.push(`<div class="f2r-lecture-card-note">${escapeHtml(item.n)}</div>`);
    return `<article class="f2r-lecture-card">${media}<div class="f2r-lecture-card-body">${body.join('')}</div></article>`;
  }

  function miniLecturesCollectionHtml(items, videosById, ctx, opts) {
    return renderCollectionShell({
      navLabel: '미니 강의',
      variant: 'lectures',
      hint: (opts && opts.hint) || '좌우로 밀어 미니 강의를 둘러보세요',
      staticMode: !!(opts && opts.staticMode),
      cards: items.map((item) => miniLectureCardHtml(item, videosById.get(String(item.v)), ctx))
    });
  }

  function renderMiniLectures(view, ctx) {
    if (!view.sectionVisible('sec-minilectures')) return '';
    // Mini Lectures 풀도 LESSON 분류 영상만 허용한다(Phase 3) — 저장된 항목이 이후 DIRECTOR로
    // 재지정되거나 미지정으로 되돌아갈 일은 없지만(재지정은 DIRECTOR/LESSON 사이에서만
    // 일어난다), 방어적으로 여기서도 동일 규칙을 적용해 taxonomy 단일 소스를 유지한다.
    const videosById = new Map(
      (ctx.videoList() || []).filter(isLessonVideo).map((v) => [String(v.videoId), v]));
    // 영상이 삭제된 항목은 렌더하지 않는다(가짜 카드 만들지 않음).
    const usable = miniLectureItems(view).filter((it) => videosById.has(String(it.v)));
    const body = usable.length
      ? ctx.miniLectureCollection(usable, videosById)
      : ctx.miniLecturesEmpty();
    if (!body) return ''; // 공개 + 콘텐츠 없음 → 섹션 자체를 내지 않는다
    const title = draftText(view, 'miniLecturesSectionTitle', miniLecturesTitleDefault(view));
    return `<section id="sec-minilectures" data-builder-section-key="sec-minilectures" class="section-wrap">
      <div class="section-inner">
        <div class="section-header">
          <div class="eyebrow"><span class="stroke"></span>미니 강의</div>
          ${ctx.editableText({ tag: 'h2', className: 'section-title', draftKey: 'miniLecturesSectionTitle', value: title, omitWhenEmpty: true })}
        </div>
        ${body}
        ${ctx.miniLecturesFooter(view)}
      </div>
    </section>`;
  }

  // ── sec-teachers (강사진) ────────────────────────────────────────────────
  // 회원이 입력한 강사만 보여 준다. 데이터 출처는 builderDraft.teachersJson(JSON 배열 문자열) 하나다:
  //   [{"n":"이름","r":"역할/과목","c":["경력"],"p":"https://사진"}]
  // 값이 없거나 유효한 강사가 한 명도 없으면 섹션 자체를 내지 않는다(플레이스홀더·가짜 강사 금지).
  // 새 렌더러가 아니라 다른 sec-* 와 같은 방식의 섹션 함수 하나다. 기존 학원은 이 키가 없어 변화 없음.
  // 서버(TeachersDraft)와 같은 한도를 쓰되, 저장 값은 신뢰하지 않고 렌더 때 다시 거른다.
  const TEACHERS_MAX = 8;
  const TEACHER_CAREERS_MAX = 5;

  /** 사진 주소는 https만 통과(javascript:/data:/http: 차단). 서버 검증과 다층 방어. */
  function teacherPhotoUrl(url) {
    const u = String(url == null ? '' : url).trim();
    return /^https:\/\/[^\s"'<>`\\]+$/i.test(u) ? u : '';
  }

  /** builderDraft.teachersJson → [{n,r,c[],p}]. 깨진 JSON·이름 없는 항목은 버린다. */
  function teacherItems(view) {
    const raw = draftText(view, 'teachersJson', '');
    if (!raw) return [];
    let parsed;
    try { parsed = JSON.parse(raw); } catch (_) { return []; }
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((e) => {
        if (!e || typeof e !== 'object') return null;
        const careers = Array.isArray(e.c)
          ? e.c.map(textValue).filter(Boolean).slice(0, TEACHER_CAREERS_MAX)
          : [];
        return { n: textValue(e.n), r: textValue(e.r), c: careers, p: teacherPhotoUrl(e.p) };
      })
      .filter((e) => e && e.n)
      .slice(0, TEACHERS_MAX);
  }

  /** 아바타 글자는 실제 이름의 첫 글자다(사진이 없을 때만, 가짜 사진을 만들지 않는다). */
  function teacherInitial(name) {
    return Array.from(String(name || '').trim())[0] || '';
  }

  function teacherCardHtml(item) {
    const photo = item.p
      ? `<img class="bi-teachers__img" src="${escapeAttr(item.p)}" alt="${escapeAttr(item.n)} 강사 사진" loading="lazy" decoding="async">`
      : `<span class="bi-teachers__initial" aria-hidden="true">${escapeHtml(teacherInitial(item.n))}</span>`;
    const role = item.r ? `<span class="bi-teachers__role">${escapeHtml(item.r)}</span>` : '';
    const careers = item.c.length
      ? `<ul class="bi-teachers__careers">${item.c.map((c) => `<li>${escapeHtml(c)}</li>`).join('')}</ul>`
      : '';
    return `<li class="bi-teachers__card">
        <div class="bi-teachers__photo">${photo}</div>
        <h3 class="bi-teachers__name">${escapeHtml(item.n)}</h3>
        ${role}${careers}
      </li>`;
  }

  function renderTeachers(view) {
    if (!view.sectionVisible('sec-teachers')) return '';
    const items = teacherItems(view);
    if (!items.length) return ''; // 입력된 강사가 없으면 섹션을 내지 않는다
    const title = draftText(view, 'teachersSectionTitle', '강사진');
    return `<section id="sec-teachers" data-builder-section-key="sec-teachers" class="section-wrap bi-teachers" data-reveal data-reveal-item>
      <div class="section-inner">
        <div class="section-header">
          <div class="eyebrow"><span class="stroke"></span>강사진</div>
          <h2 class="section-title">${escapeHtml(title)}</h2>
        </div>
        <ul class="bi-teachers__grid">${items.map(teacherCardHtml).join('')}</ul>
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
    const slot = isHorizontalCollection(view, 'lessonsLayout')
      ? ctx.lessonCollection(view.lessons || [])
      : ctx.renderLessonShowcase(view.lessons || []);
    return `<section id="sec-lessons" data-builder-section-key="sec-lessons" class="section-wrap classes-wrap">
        <div class="section-inner classes-container">
          <div class="section-head classes-head">
            <div class="eyebrow"><span class="stroke"></span>대표 수업</div>
            ${ctx.editableText({ tag: 'h2', className: 'classes-title', draftKey: 'curriculumTitle', value: title, builderClass: 'builder-multiline-text', omitWhenEmpty: true })}
          </div>
          <div class="lesson-product-slot">${slot}</div>
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
    return `<figure class="schedule-calendar"${ctx.scheduleCalendarAttrs()} data-reveal data-reveal-item>${body}${ctx.scheduleCalendarExtra()}</figure>`;
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

      // Horizontal Content Collection — 공개 페이지는 실제 가로 shelf + 카드 상호작용.
      lessonCollection(lessons) {
        return lessonCollectionHtml(lessons, this, { emptyMessage: '아직 공개된 수업 상품이 없습니다.' });
      },
      // AI Native 모바일 — 단계별 횡스크롤 Story(공개 전용). 배선: academy_cinematic.js wireAiNativeScenes.
      aiScenesMobile() { return aiScenesMobileHtml(); },
      videoCollection(videos) {
        return videoCollectionHtml(videos, this);
      },
      // Mini Lectures (V2-9) — 공개: 실제 가로 shelf + 카드 재생. 콘텐츠 없으면 섹션 생략(''→renderMiniLectures).
      miniLectureCollection(items, videosById) {
        return miniLecturesCollectionHtml(items, videosById, this);
      },
      miniLectureMedia(video, face, aspect) {
        return mediaViewportHtml({
          viewportClass: 'f2r-lecture-media',
          face,
          playable: true,
          src: video.videoUrl || '',
          poster: video.thumbnailUrl || '',
          aspect,
          label: '미니 강의 재생'
        });
      },
      miniLecturesEmpty() { return ''; },
      miniLecturesFooter() { return ''; },
      collectionConsultCta(label) {
        const text = String(label == null ? '' : label).trim() || '상담 신청하기';
        return `<button type="button" class="f2r-program-card-cta" onclick="requestConsult()">${escapeHtml(text)}</button>`;
      },
      // Actual Class Phone Card — 프레임(9:19)은 유지, 그 안에서 재생. 공통 Media Viewport 계약.
      collectionVideoMedia(video, face) {
        return mediaViewportHtml({
          viewportClass: 'f2r-phone-screen',
          face,
          playable: true,
          src: video.videoUrl || '',
          poster: video.thumbnailUrl || '',
          label: '수업 영상 재생'
        });
      },
      // Program Card 대표 미디어 — 공개 페이지는 발행 학원의 공개 바이트 경로를 쓴다.
      // 이미지: 그대로 표시. 영상: 실제 영상 비율의 stage + 클릭 시 그 안에서 재생.
      programCardMedia(lesson) {
        const id = lesson.lessonId;
        if (lesson.mediaType === 'IMAGE' && id != null) {
          const slug = encodeURIComponent(resolve(options.slug));
          return `<div class="f2r-program-card-media" data-media-fit="image"><img src="/api/academies/${slug}/lessons/${encodeURIComponent(id)}/hero-image" alt="" loading="lazy"></div>`;
        }
        if (lesson.mediaType === 'VIDEO' && lesson.mediaVideoId != null) {
          const slug = encodeURIComponent(resolve(options.slug));
          const base = `/api/academies/${slug}/videos/${encodeURIComponent(lesson.mediaVideoId)}`;
          // 영상 stage 는 실제 업로드 영상 비율을 따른다(pillarbox 최소화). 목록에서 width/height 조회.
          const found = (resolve(options.videos) || [])
            .find((v) => String(v.videoId) === String(lesson.mediaVideoId));
          const aspect = found ? mediaAspectRatio(found.width, found.height) : null;
          const face = `<img src="${base}/thumbnail" alt="" loading="lazy"><span class="f2r-video-play" aria-hidden="true">▶</span>`;
          return mediaViewportHtml({
            viewportClass: 'f2r-program-card-media f2r-program-card-media-video',
            face,
            playable: true,
            src: `${base}/stream`,
            poster: `${base}/thumbnail`,
            aspect,
            label: '수업 영상 재생'
          });
        }
        return '';
      },

      /**
       * 텍스트 노드. 값이 비면 요소 자체를 생략해 빈 자리가 남지 않게 한다
       * (빌더는 반대로 항상 렌더해야 원장이 클릭해 채울 수 있다).
       * quoted는 인용문 앞뒤에 붙는 장식 따옴표다.
       */
      editableText({ tag, className, value, publicClass, omitWhenEmpty, quoted, extraAttrs }) {
        if (omitWhenEmpty && !value) return '';
        const cls = publicClass ? `${className} ${publicClass}` : className;
        const inner = quoted
          ? `<span class="qm">"</span>${escapeHtml(value)}<span class="qm">"</span>`
          : escapeHtml(value);
        return `<${tag} class="${cls}"${extraAttrs ? ' ' + extraAttrs : ''}>${inner}</${tag}>`;
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
        return `<article class="note-card intro-feature-item" data-reveal-item><h3 class="intro-feature-text">${escapeHtml(parts.title)}</h3>${parts.body ? `<p>${escapeHtml(parts.body)}</p>` : ''}</article>`;
      },
      introFooter() { return ''; },
      philosophyCard(text, index) {
        return `<div class="p-card" data-reveal-item><span class="n">원칙 ${String(index + 1).padStart(2, '0')}</span><p>${escapeHtml(text)}</p></div>`;
      },
      // 학습 철학 — 공개 페이지는 step progression(단일 카드 + 진행바 + 인디케이터 + prev/next + 스와이프).
      // 빌더 캔버스는 편집 UX를 위해 정적 그리드 프리뷰를 유지.
      philosophyCards(items) {
        return '';
      },
      // 학교별 독립 카드 횡스크롤. 각 카드 = 그 학교의 실제 /api/analyzes 출제자 Digital Twin.
      // 카드 본문은 빈 상태로 내고 academy_public.js wireFeaturedSchoolCards 가 학교별로 fetch 해 채운다.
      // #sec-featured 안에서만 배선된다 — 대표수업(sec-lessons)과 무관.
      featuredSchoolCards(schools) {
        return '';
      },
      featuredCta(label) {
        const text = String(label == null ? '' : label).trim();
        return text ? `<button type="button" class="btn featured-cta-btn" data-featured-cta data-featured-cta-base="${escapeAttr(text)}" onclick="requestConsult()">${escapeHtml(text)}</button>` : '';
      },
      aiStepName(index, name) { return escapeHtml(name); },

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
      principalCareerFooter() { return ''; },
      // 공개 페이지에는 편집 진입점이 나가면 안 된다(방문자에게 "AI로 다듬기" 버튼이 보이면 안 됨).
      aiAssistButton() { return ''; }
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

      // Horizontal Content Collection — 캔버스는 편집 UX 우선이라 정적 wrap(가로 스크롤 없음).
      // 실제 발행 페이지는 좌우로 둘러보는 shelf 라는 hint 를 남긴다.
      lessonCollection(lessons) {
        return lessonCollectionHtml(lessons, this, {
          emptyMessage: '아직 등록된 수업이 없습니다.',
          staticMode: true,
          hint: '공개 페이지에서는 좌우로 둘러보는 가로 컬렉션으로 표시됩니다'
        });
      },
      // AI Native 모바일 횡스크롤 Story 는 공개 전용 — 빌더 캔버스는 5 세로 블록 프리뷰를 그대로 쓴다.
      aiScenesMobile() { return ''; },
      videoCollection(videos) {
        return videoCollectionHtml(videos, this, {
          staticMode: true,
          hint: '공개 페이지에서는 좌우로 둘러보는 가로 컬렉션으로 표시됩니다'
        });
      },
      // Mini Lectures (V2-9) — 빌더 캔버스: 정적 wrap + 편집 진입점(data-mini-lectures-edit).
      miniLectureCollection(items, videosById) {
        return miniLecturesCollectionHtml(items, videosById, this, {
          staticMode: true,
          hint: '공개 페이지에서는 좌우로 둘러보는 가로 컬렉션으로 표시됩니다'
        });
      },
      miniLectureMedia(video, face, aspect) {
        return mediaViewportHtml({ viewportClass: 'f2r-lecture-media', face, playable: false, aspect });
      },
      miniLecturesEmpty() {
        return '<div class="f2r-lecture-empty" data-mini-lectures-edit role="button" tabindex="0">'
          + '<strong>미니 강의를 추가해 주세요</strong>'
          + '<span>영상 보관함에서 강의를 고르고 제목·학습 포인트를 적습니다.</span></div>';
      },
      miniLecturesFooter() {
        return '<div style="text-align:center;margin-top:14px;">'
          + '<button type="button" class="cv-btn-primary" data-mini-lectures-edit>🎓 미니 강의 편집</button></div>';
      },
      collectionConsultCta(label) {
        const text = String(label == null ? '' : label).trim() || '상담 신청하기';
        return `<span class="f2r-program-card-cta" aria-hidden="true">${escapeHtml(text)}</span>`;
      },
      collectionVideoMedia(video, face) {
        return mediaViewportHtml({ viewportClass: 'f2r-phone-screen', face, playable: false });
      },
      // 빌더 캔버스: 정적 표시. 이미지는 소유자 전용 경로, 영상은 목록의 썸네일 URL 을 재사용한다.
      programCardMedia(lesson) {
        const id = lesson.lessonId;
        if (lesson.mediaType === 'IMAGE' && id != null) {
          return `<div class="f2r-program-card-media" data-media-fit="image"><img src="/api/tutor/academy/lessons/${encodeURIComponent(id)}/hero-image" alt="" loading="lazy"></div>`;
        }
        if (lesson.mediaType === 'VIDEO' && lesson.mediaVideoId != null) {
          const found = (resolve(options.videos) || []).find((v) => String(v.videoId) === String(lesson.mediaVideoId));
          if (found && found.thumbnailUrl) {
            return `<div class="f2r-program-card-media f2r-program-card-media-video"><img src="${escapeAttr(found.thumbnailUrl)}" alt="" loading="lazy"><span class="f2r-video-play" aria-hidden="true">▶</span></div>`;
          }
        }
        return '';
      },

      /**
       * 텍스트 노드. 값이 비어도 항상 렌더한다 — 요소가 없으면 원장이 클릭해 채울 수 없다.
       * contenteditable은 false로 두고 섹션 편집 모드에 들어갈 때 true로 바뀐다.
       */
      // anchor='text'는 Academy 본체 필드(name/intro), 기본값 'draft'는 builderDraft 키다.
      // 커밋 경로가 달라 속성 이름도 다르다.
      editableText({ tag, className, draftKey, value, builderClass, anchor, extraAttrs }) {
        const cls = builderClass ? `${className} ${builderClass}` : className;
        const attr = anchor === 'text' ? 'data-builder-text' : 'data-builder-draft';
        return `<${tag} class="${cls}"${extraAttrs ? ' ' + extraAttrs : ''} ${attr}="${escapeAttr(draftKey)}" contenteditable="false" spellcheck="false">${escapeHtml(value)}</${tag}>`;
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
        return `<article class="note-card intro-feature-item" data-reveal-item><h3 class="intro-feature-text" data-intro-feature-item contenteditable="false" spellcheck="false">${escapeHtml(parts.title)}</h3><p data-intro-feature-body contenteditable="false" spellcheck="false">${escapeHtml(parts.body)}</p><button type="button" class="intro-feature-remove" data-intro-feature-remove aria-label="노트 삭제">✕</button></article>`;
      },
      introFooter() {
        return '<button type="button" class="intro-feature-add" data-intro-feature-add>＋ 특징 추가</button>';
      },
      philosophyCard(text, index) {
        const draftKey = 'philosophyItem' + (index + 1);
        return `<div class="p-card" data-reveal-item><span class="n">원칙 ${String(index + 1).padStart(2, '0')}</span><p data-builder-draft="${draftKey}" contenteditable="false" spellcheck="false">${escapeHtml(text)}</p>${this.aiAssistButton('PHILOSOPHY_ITEM', draftKey)}</div>`;
      },
      // 빌더 캔버스: 편집 UX 우선 — 정적 그리드로 프리뷰(공개 페이지는 횡스크롤 컬렉션).
      philosophyCards(items) {
        return `<div class="philosophy-grid">${(items || []).map((t, i) => this.philosophyCard(t, i)).join('')}</div>`;
      },
      // 빌더 캔버스: 학교별 카드(4칸 항상 렌더 — 빈 값은 CSS placeholder). 학교명만 편집.
      // 실제 출제자 Digital Twin(/api/analyzes)은 공개 페이지 전용 — 정적 안내만.
      featuredSchoolCards(schools) {
        const list = schools && schools.length ? schools : ['', '', '', ''];
        const cards = list.map((text, index) =>
          `<article class="featured-schoolcard featured-schoolcard--builder">`
          + `<div class="featured-schoolcard-head">`
          + `<b class="featured-schoolcard-name"><span class="featured-chip-label" data-builder-draft="featuredItem${index + 1}" contenteditable="false" spellcheck="false">${escapeHtml(String(text == null ? '' : text))}</span></b>`
          + `<span class="featured-schoolcard-kind">출제자 Digital Twin</span></div>`
          + `<div class="featured-schoolcard-body"><p class="featured-schoolcard-loading">공개 페이지에서 이 학교의 실제 출제 분석(<code>/analyzes</code>)이 표시됩니다.</p></div>`
          + `</article>`).join('');
        return `<div class="featured-schoolcards-wrap"><div class="featured-schoolcards">${cards}</div></div>`;
      },
      featuredCta(label) {
        return `<span class="btn featured-cta-btn" data-builder-draft="featuredCtaLabel" contenteditable="false" spellcheck="false">${escapeHtml(String(label == null ? '' : label))}</span>`;
      },
      aiStepName(index, name) {
        return `<span class="ai-native-step-label" data-builder-draft="aiProcessStep${index + 1}" contenteditable="false" spellcheck="false">${escapeHtml(String(name == null ? '' : name))}</span>`;
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
      },
      /**
       * Phase 4 버그 회귀 수정: 이전에는 섹션을 클릭해 "편집 모드"에 들어가야만 나타나는
       * 플로팅 툴바(renderSectionTextApply)에만 AI 버튼을 달았다 — 발견하기 어렵고, 그
       * 진입 자체가 막히는 경로(직전 섹션 검증 실패 등)가 있으면 아예 나타나지 않았다.
       * 이제는 정적 마크업에 항상 렌더한다 — 클릭 한 번으로 바로 보이고 항상 클릭 가능하다.
       * task/target은 data 속성에 그대로 실어 보내 academy_builder.js가 별도 매핑 없이
       * 클릭된 버튼에서 직접 읽는다(AI_ASSIST_SECTIONS 같은 간접 조회를 없앰).
       */
      aiAssistButton(task, target) {
        return `<button type="button" class="builder-ai-assist-inline" data-ai-suggest-trigger data-ai-suggest-task="${escapeAttr(task)}" data-ai-suggest-target="${escapeAttr(target)}" aria-label="AI로 다듬기">✨ AI로 다듬기</button>`;
      }
    });
  }

  global.F2RAcademySections = Object.freeze({
    FUTURE_SECTION_IDS,
    OPT_IN_SECTION_IDS,
    isDirectorVideo,
    isLessonVideo,
    isUnclassifiedVideo,
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
    featuredSchoolLabels,
    aiProcessSteps,
    isHorizontalCollection,
    renderCollectionShell,
    mediaViewportHtml,
    mediaAspectRatio,
    applyImageMediaAspect,
    programCardHtml,
    videoCardHtml,
    miniLectureItems,
    miniLectureCardHtml,
    lessonPanelTitle,
    renderHero,
    renderIntro,
    renderPhilosophy,
    renderPrincipal,
    renderVideos,
    renderMiniLectures,
    teacherItems,
    renderTeachers,
    renderExternalServices,
    renderCta,
    renderLessons,
    renderFeatured,
    renderAiProcess
  });
})(typeof window !== 'undefined' ? window : globalThis);
