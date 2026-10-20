/**
 * /builder 페이지 발행 배선 + 실제 공개 페이지 미리보기.
 *
 * 기존 Academy 초기값은 Thymeleaf SSR data-* 모델에서 읽고, JS가 /api/tutor/academy/me를
 * 재조회하지 않는다. 랜딩에서 넘어온 신규 발행 요청(sessionStorage 'pendingAcademyPublish')만
 * API body로 쓰고, Builder 내부 DOM에 공개 페이지 모양을 직접 렌더한다(iframe 없음).
 *
 * ⚠️ 실제 서버 동작은 신규 발행 POST, 기존 Academy 텍스트 저장 PUT, 섹션 표시 설정 PUT뿐이다.
 * 드래그·스타일·결제 등 나머지 빌더 UI는 데모 인라인 스크립트의 mock(showToast) 그대로 둔다(범위 밖).
 */
(function () {
  const PENDING_KEY = 'pendingAcademyPublish';
  const PENDING_PREVIEW_KEY = 'pendingAcademyPreview';
  const PENDING_LOGO_KEY = 'pendingAcademyLogo';
  const BUILDER_URL = '/builder';
  const FIXED_FOOTER_SECTION_ID = 'sec-footer';
  // 헤더도 푸터처럼 순서 편집 대상이 아닌 고정 영역이다. DEFAULT_SECTION_ORDER에 넣지 않아야
  // 서버의 sectionOrder size-lock(AcademyService.validateSectionOrder)을 건드리지 않는다.
  const FIXED_HEADER_SECTION_ID = 'sec-header';
  // sec-videos 미리보기 소스. 발행 전 Academy는 조회할 대상이 없으므로 빈 배열로 둔다.
  let builderVideos = [];
  if (!window.F2RAcademySections) {
    throw new Error('F2RAcademySections must be loaded before academy_builder.js');
  }
  // 섹션 마크업 정본. 공개 페이지와 같은 모듈을 쓰고 편집 크롬만 ctx가 얹는다.
  const SECTIONS = window.F2RAcademySections;
  const BRAND = typeof window !== 'undefined' ? window.F2RAcademyBrand : null;
  // 쇼케이스는 지연 평가한다 — 정본 모듈이 소비자와 다른 컨텍스트에 있을 수 있다.
  const BUILDER_CTX = SECTIONS.createBuilderContext({
    showcase: () => window.F2RLessonProductShowcase,
    videos: () => builderVideos,
    pendingHeroMedia: () => pendingHeroMedia,
    heroMediaVersion: () => heroMediaVersion
  });
  const DEFAULT_SECTION_ORDER = [
    'sec-hero',
    'sec-featured',
    'sec-intro',
    'sec-principal',
    'sec-teachers',
    'sec-philosophy',
    'sec-videos',
    'sec-minilectures',
    'sec-reviews',
    'sec-lessons',
    'sec-curriculum',
    'sec-effect',
    'sec-digitaltwin',
    'sec-market',
    'sec-payment',
    'sec-cta',
    'sec-social'
  ];
  const FUTURE_SECTION_IDS = Object.freeze([
    'sec-reviews',
    'sec-curriculum',
    'sec-effect',
    'sec-market',
    'sec-payment'
  ]);
  const FUTURE_SECTIONS = new Set(FUTURE_SECTION_IDS);
  // Education Storefront 신규 섹션 — 기본 OFF(opt-in). 명시적으로 ON 하기 전에는 캔버스·레이아웃·
  // 공개 페이지 어디에도 렌더되지 않아, 기존 학원 사이트가 그대로 유지된다.
  const OPT_IN_SECTION_IDS = Object.freeze([
    'sec-featured',
    'sec-digitaltwin',
    'sec-minilectures',
    'sec-cta'
  ]);
  const OPT_IN_SECTIONS = new Set(OPT_IN_SECTION_IDS);
  const MANAGED_SECTION_IDS = [
    'sec-featured',
    'sec-intro',
    'sec-principal',
    'sec-teachers',
    'sec-philosophy',
    'sec-videos',
    'sec-minilectures',
    'sec-reviews',
    'sec-lessons',
    'sec-curriculum',
    'sec-effect',
    'sec-digitaltwin',
    'sec-market',
    'sec-payment',
    'sec-cta',
    'sec-social'
  ];
  const MANAGED_SECTIONS = new Set(MANAGED_SECTION_IDS);
  const ORDERED_SECTIONS = new Set(DEFAULT_SECTION_ORDER);
  if (!window.F2RBuilderTextUtils) {
    throw new Error('F2RBuilderTextUtils must be loaded before academy_builder.js');
  }
  if (!window.F2RAcademyBusinessInfo) {
    throw new Error('F2RAcademyBusinessInfo must be loaded before academy_builder.js');
  }
  if (!window.F2RAcademyBusinessInfoEditor) {
    throw new Error('F2RAcademyBusinessInfoEditor must be loaded before academy_builder.js');
  }
  if (!window.F2RBuilderEditableGuide) {
    throw new Error('F2RBuilderEditableGuide must be loaded before academy_builder.js');
  }
  const TEXT_UTILS = window.F2RBuilderTextUtils;
  const BUSINESS_INFO = window.F2RAcademyBusinessInfo;
  const BUSINESS_INFO_EDITOR = window.F2RAcademyBusinessInfoEditor;
  const EDITABLE_GUIDE = window.F2RBuilderEditableGuide;
  const BUSINESS_INFO_FIELD_OPTIONS = Object.freeze({
    businessName: { label: '상호명', maxlength: 100 },
    representative: { label: '대표', maxlength: 100 },
    address: { label: '주소', maxlength: 300 },
    businessRegistrationNumber: { label: '사업자등록번호', maxlength: 50 },
    mailOrderRegistrationNumber: { label: '통신판매업신고번호', maxlength: 50 },
    phone: { label: '전화', maxlength: 30 },
    email: { label: '이메일', maxlength: 254 }
  });
  const BUSINESS_INFO_FIELDS = Object.freeze(BUSINESS_INFO.EDITABLE_KEYS.map((key) => {
    const options = BUSINESS_INFO_FIELD_OPTIONS[key];
    if (!options) throw new Error('Missing Builder field metadata for ' + key);
    return Object.freeze(Object.assign({ key }, options));
  }));

  const STYLE_PANEL_SECTION_NAMES = {
    'sec-hero': '히어로 배너',
    'sec-featured': 'Featured · 시즌 프로그램',
    'sec-intro': '학원 소개',
    'sec-principal': '원장 소개',
    'sec-teachers': '강사진',
    'sec-philosophy': '학습 철학',
    'sec-videos': '수업 영상',
    'sec-minilectures': '미니 강의',
    'sec-reviews': '수강 후기',
    'sec-lessons': '수업상품 · 강의 목록',
    'sec-curriculum': '커리큘럼 관리',
    'sec-effect': '학습 효과 대시보드',
    'sec-digitaltwin': 'AI Native 교육 과정',
    'sec-market': '마켓플레이스',
    'sec-payment': '수강료 결제',
    'sec-cta': '상담 신청 CTA',
    'sec-social': '외부 연동',
    'sec-footer': 'Footer 사업자 정보'
  };

  const TARGET_GROUP_OPTIONS = [
    ['ELEMENTARY', '초등'],
    ['MIDDLE', '중등'],
    ['HIGH', '고등'],
    ['UNIVERSITY', '대학'],
    ['ADULT', '성인']
  ];
  const REGION_OPTIONS = {
    '서울시': ['종로구', '중구', '용산구', '성동구', '광진구', '동대문구', '중랑구', '성북구', '강북구', '도봉구', '노원구', '은평구', '서대문구', '마포구', '양천구', '강서구', '구로구', '금천구', '영등포구', '동작구', '관악구', '서초구', '강남구', '송파구', '강동구'],
    '부산시': ['중구', '서구', '동구', '영도구', '부산진구', '동래구', '남구', '북구', '해운대구', '사하구', '금정구', '강서구', '연제구', '수영구', '사상구', '기장군'],
    '대구시': ['중구', '동구', '서구', '남구', '북구', '수성구', '달서구', '달성군', '군위군'],
    '인천시': ['중구', '동구', '미추홀구', '연수구', '남동구', '부평구', '계양구', '서구', '강화군', '옹진군'],
    '광주시': ['동구', '서구', '남구', '북구', '광산구'],
    '대전시': ['동구', '중구', '서구', '유성구', '대덕구'],
    '울산시': ['중구', '남구', '동구', '북구', '울주군'],
    '세종시': ['전 지역'],
    '경기도': ['수원시', '성남시', '의정부시', '안양시', '부천시', '광명시', '평택시', '동두천시', '안산시', '고양시', '과천시', '구리시', '남양주시', '오산시', '시흥시', '군포시', '의왕시', '하남시', '용인시', '파주시', '이천시', '안성시', '김포시', '화성시', '광주시', '양주시', '포천시', '여주시'],
    '강원도': ['춘천시', '원주시', '강릉시', '동해시', '태백시', '속초시', '삼척시'],
    '충청북도': ['청주시', '충주시', '제천시'],
    '충청남도': ['천안시', '공주시', '보령시', '아산시', '서산시', '논산시', '계룡시', '당진시'],
    '전라북도': ['전주시', '군산시', '익산시', '정읍시', '남원시', '김제시'],
    '전라남도': ['목포시', '여수시', '순천시', '나주시', '광양시'],
    '경상북도': ['포항시', '경주시', '김천시', '안동시', '구미시', '영주시', '영천시', '상주시', '문경시', '경산시'],
    '경상남도': ['창원시', '진주시', '통영시', '사천시', '김해시', '밀양시', '거제시', '양산시'],
    '제주도': ['제주시', '서귀포시']
  };
  let currentPreview = null;
  let subjectOptionsCache = null;
  let activeTextField = null;
  let activeTextEditSection = null;
  let dirtyTextChanges = false;
  let latestPublicHandleCheck = null;
  let pendingLogoFile = null;
  /**
   * 발행 전에 고른 히어로 이미지(원장사진·교재표지). slot -> { file, objectUrl }.
   *
   * <p>히어로 업로드 API는 Academy row가 있어야 해서 발행 전에는 404가 난다. 로고와 같은
   * "발행 시 함께 저장" 방식으로 미뤄 둔다.
   *
   * <p>로고와 달리 sessionStorage에 담지 않는다 — 움짤은 十MB 단위라 base64로 넣으면 용량
   * 한도를 넘긴다. 대신 메모리에만 들고 objectURL로 미리보기를 만든다(새로고침하면 사라진다).
   */
  let pendingHeroMedia = {};
  let businessInfoEditor = BUSINESS_INFO_EDITOR.idle();
  let editableGuideActive = false;

  document.addEventListener('DOMContentLoaded', init);
  document.addEventListener('DOMContentLoaded', function () { window.setTimeout(showAfterAiCreateHint, 600); });

  /** AI 생성 직후 한 번만: 다음 단계(디자인 → 편집 → 게시)와 시각 편집기 진입을 안내한다. */
  function showAfterAiCreateHint() {
    let flagged = false;
    try {
      flagged = sessionStorage.getItem('builditAfterAiCreate') === '1';
      if (flagged) sessionStorage.removeItem('builditAfterAiCreate');
    } catch (_) { /* 무시 */ }
    if (!flagged) return;
    toast('AI가 홈페이지 초안을 만들었습니다. 디자인을 고르고 내용을 다듬은 뒤 게시하세요. 더 자유롭게 고치려면 상단 「시각 편집기」로 이어서 편집할 수 있습니다.', 'fa-wand-magic-sparkles');
    const link = document.getElementById('builderVisualEditorLink');
    if (link) link.classList.add('is-next-step');
  }

  async function init() {
    let request = readJson(PENDING_KEY);
    const ssrAcademy = readSsrAcademy();
    if (!request && !ssrAcademy && !readJson(PENDING_PREVIEW_KEY)) {
      request = await starterDraftForDirectBuilder();
    }
    const storedPendingPreview = readJson(PENDING_PREVIEW_KEY) || previewFromRequest(request);
    const pendingPreview = request
      ? mergePendingBusinessInfoIntoPreview(storedPendingPreview, request)
      : storedPendingPreview;
    const preview = request ? pendingPreview : (ssrAcademy || pendingPreview);
    applyStoredDraftLogo(preview);
    currentPreview = preview;

    builderVideos = await loadBuilderVideos(preview);
    loadCompletionCheck(); // fire-and-forget — 실패해도 Builder 진입을 막지 않는다.
    populatePreview(preview);
    renderPublicPreview(preview);
    wireSectionVisibility(preview);
    wireLayoutSectionOrder(preview);
    wirePanelTabs();
    wireFixedSectionNavigation();
    wireTextEditing(preview);
    wireHeroVideoEditing(preview);
    wireHeroMediaEditing();
    wireFeaturedLessonsEditing();
    wireMiniLecturesEditing();
    wireAiSuggestionEditing();
    wireAiAssistTriggers();
    wireAcademyComposeModal();
    wirePrincipalVideoEditing(preview);
    wirePrincipalCareerEditing(preview);
    wireIntroFeatureEditing(preview);
    wirePublicHandleEditing(preview);
    wireSocialUrlEditing(preview);
    wireConsultContactEditing();
    wireCtaEditing();
    wireBusinessInfoEditing();
    wireLogoEditing(preview);
    wireBasicDataEditing(preview);
    wireVideoManagement();
    wireLessonScheduleToggle();
    wirePreviewSectionSelection();
    wireEditableGuide();
    wireBrandDesign(preview);
    wireAiGuide(preview);
    wireHeaderSectionApply();
    wireMotionControls();
    if (preview && preview.name) toast(`👋 ${preview.name} 빌더에 오신 걸 환영합니다!`, 'fa-graduation-cap');
    wirePublish(request);
  }


  /** 발행 전 Academy는 영상이 있을 수 없고, 조회 실패도 빌더 진입을 막으면 안 되므로 빈 배열로 떨어뜨린다. */
  async function loadBuilderVideos(preview) {
    if (!preview || !preview.publicPathUrl) return [];
    try {
      const res = await fetch('/api/tutor/academy/videos', { credentials: 'same-origin' });
      if (!res.ok) return [];
      const videos = await res.json();
      return Array.isArray(videos) ? videos.filter((video) => video.status === 'READY') : [];
    } catch (_) {
      return [];
    }
  }

  /**
   * Academy 완성도 진단(deterministic, AI 아님) — GET /api/tutor/academy/ai/completion.
   * 신규(아직 서버에 저장 전) Academy는 조회가 실패할 수 있는데(§starterDraftForDirectBuilder),
   * 그 경우 패널을 조용히 숨긴다 — Builder 진입 자체를 막으면 안 된다.
   */
  async function loadCompletionCheck() {
    const panel = document.getElementById('builderCompletionPanel');
    if (!panel) return;
    try {
      const res = await fetch('/api/tutor/academy/ai/completion', { credentials: 'same-origin' });
      if (!res.ok) {
        panel.hidden = true;
        return;
      }
      renderCompletionCheck(await res.json());
    } catch (_) {
      panel.hidden = true;
    }
  }

  function renderCompletionCheck(result) {
    const panel = document.getElementById('builderCompletionPanel');
    const scoreEl = document.getElementById('builderCompletionScore');
    const listEl = document.getElementById('builderCompletionList');
    if (!panel || !scoreEl || !listEl || !result) return;
    scoreEl.textContent = result.scorePercent + '%';
    listEl.innerHTML = (result.items || []).map((item) => {
      const mark = item.done ? '✓' : '△';
      return `<li class="builder-completion-item${item.done ? ' is-done' : ''}">`
        + `<span class="builder-completion-mark">${mark}</span>${escapeHtml(item.label)}</li>`;
    }).join('');
    panel.hidden = false;
  }

  async function starterDraftForDirectBuilder() {
    const options = await loadSubjectOptions();
    const firstSubject = options[0] || { subjectId: 1, nameKo: '영어', nameEn: 'English' };
    const request = {
      name: '새 학원',
      intro: '학원 소개를 입력해 주세요.',
      subjectIds: [Number(firstSubject.subjectId || 1)],
      targetGroups: ['MIDDLE'],
      regions: [{ city: '서울시', district: null }],
      pageSettings: {
        builderDraft: { monthlyTuition: '—', scoreImprovement: '—', operationYears: '—' },
        sectionVisibility: normalizeSectionVisibility()
      }
    };
    const preview = {
      name: request.name,
      intro: request.intro,
      subjects: [{
        subjectId: Number(firstSubject.subjectId || 1),
        nameKo: firstSubject.nameKo || firstSubject.nameEn || '영어',
        nameEn: firstSubject.nameEn || firstSubject.nameKo || 'English'
      }],
      targetGroups: request.targetGroups,
      regions: [{ city: '서울시', district: null, displayName: '서울시' }],
      lessons: [],
      pageSettings: request.pageSettings
    };
    writeJson(PENDING_KEY, request);
    writeJson(PENDING_PREVIEW_KEY, preview);
    return request;
  }

  /** 보관된 폼값을 빌더 chrome과 canvas preview에 반영. 없으면 빈 상태 안내. */
  function populatePreview(preview) {
    if (!preview) return;
    const name = preview.name || '새 학원';
    setText('.tb-site-name span', name);
    setText('.canvas-breadcrumb span', name);
    document.title = '디지털 학원 빌더 — ' + name;
    const url = document.querySelector('.browser-url');
    if (url) {
      url.textContent = displayUrlForPreview(preview);
      url.setAttribute('title', '클릭하여 공개 주소(handle)를 수정합니다');
      url.setAttribute('role', 'button');
      url.setAttribute('tabindex', '0');
    }
  }

  function displayUrlForPreview(preview) {
    const key = publicAddressKey(preview);
    if (key) return key + '.feel2real.co.kr';
    return preview && preview.displayUrl || 'handle.feel2real.co.kr';
  }

  function publicAddressKey(preview) {
    const handle = normalizeHandle(preview && preview.handle);
    if (handle) return handle;
    const slug = normalizeHandle(preview && preview.slug);
    if (slug) return slug;
    const display = String(preview && preview.displayUrl || '').replace(/\.feel2real\.co\.kr\/?$/i, '');
    return normalizeHandle(display);
  }

  /**
   * 빌더 캔버스용 전역 배경. 발행 화면은 body에 붙이고 10초마다 크로스페이드하지만,
   * 캔버스는 편집할 때마다 통째로 재렌더되므로 타이머 없이 첫 장만 고정으로 깐다
   * (재렌더가 페이드를 계속 끊어 오히려 깜빡거린다). 마크업 구조는 발행과 동일하다.
   */

  function renderPublicPreview(preview, scrollToSection, highlightField) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    if (!preview) {
      pageContent.innerHTML = `<div class="builder-preview-empty"><strong>미리볼 학원 정보가 없습니다</strong>랜딩에서 학원 정보를 입력하거나, 기존 Academy가 있는 강사 계정으로 진입하면 실제 공개 페이지 형태로 이 영역에 표시됩니다.</div>`;
      return;
    }

    const academy = normalizePreview(preview);
    // innerHTML 교체는 캔버스의 모든 노드를 detach한다. activeTextEditSection이 detach된
    // 노드를 계속 가리키면 두 가지가 터진다.
    //   1) 유령 쓰기 — 다음 섹션 선택 시 applySectionTextChanges가 detach된 노드의 옛 값을
    //      읽어 currentPreview에 커밋하고, 사라진 줄 알았던 값이 화면에 되살아난다.
    //   2) 소프트락 — 값이 빈 문자열이면 focusEditableTarget(detached)이 무효라
    //      enterSectionTextEditMode가 false를 반환하고, 이후 어떤 섹션도 선택할 수 없다.
    //      새로고침 전까지 복구 불가.
    // 편집 중이던 미적용 입력은 어차피 화면에서 사라지므로, 참조만 끊어 두 증상을 막는다.
    // 편집 대상이 사라졌으니 헤더 '적용' 버튼도 함께 내려간다(setter가 동기화).
    setActiveTextEditSection(null);
    pageContent.innerHTML = `
      <div class="builder-public-preview">
        <div class="builder-public-preview-ribbon">빌더 미리보기 — 저장된 입력값 기반으로 공개 페이지와 같은 레이아웃을 표시합니다</div>
        <main class="page"><section id="publicView">${renderPublicPage(academy)}</section></main>
      </div>`;
    SECTIONS.applyImageMediaAspect(pageContent);
    if (BRAND) BRAND.apply(pageContent.querySelector('#publicView'), preview);
    // Phase 5B.1: 공개 페이지와 같은 staged reveal(F2RReveal)을 프리뷰에도 건다 — Bildi가 섹션에
    // 도착 = 섹션이 뷰포트 진입 = reveal 발화가 자연히 동기화된다(§8). 1회성·reduced-motion 즉시·
    // 3초 failsafe이므로 편집을 방해하지 않는다. 모듈 미로드 시 no-op(콘텐츠는 정적으로 완전 노출).
    if (window.F2RReveal && typeof window.F2RReveal.wire === 'function') {
      try { window.F2RReveal.wire(pageContent); } catch (_) { /* 콘텐츠 노출에 영향 없음 */ }
    }
    // Academy Motion(숫자 카운트업 + 섹션 펼치기/접기)도 공개 페이지와 동일하게 배선한다.
    // 모듈 미로드 시 no-op(콘텐츠는 정적으로 완전 노출).
    if (window.AcademyMotion && typeof window.AcademyMotion.wire === 'function') {
      try {
        var motionDraft = (currentPreview.pageSettings && currentPreview.pageSettings.builderDraft) || {};
        window.AcademyMotion.applyPrefs({ effect: motionDraft['motion.effect'], speed: motionDraft['motion.speed'] });
        window.AcademyMotion.wire(pageContent);
      } catch (_) { /* 콘텐츠 노출에 영향 없음 */ }
    }
    if (highlightField || activeTextField) highlightTextTargets(highlightField || activeTextField);
    if (scrollToSection) scrollPreviewToSection(scrollToSection);
    if (bildiActive()) {
      // DOM 교체로 섹션 노드가 새로 생겼다 — waypoint reference 를 다시 잡고 위치를 재계산한다.
      if (bildiStory && typeof bildiStory.refresh === 'function') bildiStory.refresh();
      refreshBildiFlight();
    }
  }

  function renderPublicPage(academy) {
    // 발행(academy_public.js)은 대표 과목 1개만 쓴다. 전 과목을 이어붙이면 캔버스가
    // 실제 공개 화면과 다른 문구를 보여주게 되므로 같은 규칙을 쓴다.
    const primarySubject = academy.subjects[0]
      ? (academy.subjects[0].nameKo || academy.subjects[0].nameEn) : '';
    const regionText = academy.regions[0] ? academy.regions[0].displayName : '';
    // 미리보기의 상담 신청은 방문자 동작 대신 연락처 편집 모달을 연다(공개 페이지의 실제 모달과 구분).
    const topBtn = '<button type="button" class="cv-btn-primary builder-consult-edit" data-consult-contact-edit>상담 신청</button>';
    return renderGnb(academy, topBtn)
      + renderOrderedSections(academy, primarySubject, regionText)
      + renderFooter(academy);
  }

  function renderOrderedSections(academy, primarySubject, regionText) {
    return orderedSectionIds(academy)
      .map((id) => renderSectionById(id, academy, primarySubject, regionText))
      .join('');
  }

  function renderSectionById(id, academy, primarySubject, regionText) {
    if (id === 'sec-hero') return renderHero(academy, primarySubject, regionText);
    if (id === 'sec-featured') return renderFeatured(academy);
    if (id === 'sec-intro') return renderIntro(academy);
    if (id === 'sec-principal') return renderPrincipal(academy);
    if (id === 'sec-philosophy') return renderPhilosophy(academy);
    if (id === 'sec-videos') return renderVideos(academy);
    if (id === 'sec-teachers') return renderTeachers(academy);
    if (id === 'sec-minilectures') return renderMiniLectures(academy);
    if (id === 'sec-digitaltwin') return renderAiProcess(academy);
    if (id === 'sec-lessons') return renderLessons(academy);
    if (id === 'sec-cta') return renderCta(academy);
    if (id === 'sec-social') return renderExternalServices(academy);
    return '';
  }

  // Education Storefront 신규 섹션 — 마크업 정본은 academy_sections.js, 빌더는 편집 ctx만 얹는다.
  function renderFeatured(academy) {
    return SECTIONS.renderFeatured(sectionView(academy), BUILDER_CTX);
  }

  function renderAiProcess(academy) {
    return SECTIONS.renderAiProcess(sectionView(academy), BUILDER_CTX);
  }

  function renderGnb(academy, topBtn) {
    return `<nav class="cv-gnb" id="${FIXED_HEADER_SECTION_ID}" data-builder-section-key="${FIXED_HEADER_SECTION_ID}" tabindex="0" aria-label="상단 헤더 편집">
      <a class="eh-logo-component" href="#">
        ${renderLogoSymbol(academy, 'gnb')}
        <span class="eh-logo-text-area"><b class="eh-logo-title" data-builder-draft="headerTitle" contenteditable="false" spellcheck="false">${escapeHtml(draftText(academy, 'headerTitle', academy.name))}</b></span>
      </a>
      <div class="cv-nav">${topBtn}</div>
    </nav>`;
  }


  function renderLogoSymbol(academy, context) {
    const logoUrl = cacheBustedLogoUrl(academy.logoUrl);
    const fallback = `<span class="eh-logo-symbol-inner"><span class="eh-book"><span class="eh-letters">${escapeHtml(initials(academy.name))}</span></span></span>`;
    // 이미지 로드 실패 시 깨진 아이콘 대신 기본 로고(학원명 이니셜)로 폴백
    const body = logoUrl
      ? `<img src="${escapeHtml(logoUrl)}" alt="${escapeHtml(academy.name)} 로고" onerror="this.style.display='none';this.nextElementSibling.style.display='';">` + `<span class="eh-logo-symbol-inner" style="display:none;"><span class="eh-book"><span class="eh-letters">${escapeHtml(initials(academy.name))}</span></span></span>`
      : fallback;
    // 게시 페이지와 동일한 학원 홈 URL 규칙 사용 (/academies/{slug})
    const homeUrl = academy.slug ? '/academies/' + encodeURIComponent(academy.slug) : '/';
    return `<span class="eh-logo-symbol" data-builder-logo="${context}" tabindex="0"><a href="${homeUrl}" data-builder-logo-home title="홈으로 이동" style="display:inline-flex;align-items:center;text-decoration:none;color:inherit;">${body}</a><button type="button" class="builder-logo-edit-chip" data-builder-logo-edit title="로고 이미지 수정"><i class="fa-solid fa-image"></i> 로고</button></span>`;
  }

  function cacheBustedLogoUrl(url) {
    if (!url) return null;
    if (String(url).startsWith('data:')) return url;
    const sep = String(url).includes('?') ? '&' : '?';
    return url + sep + 'v=' + encodeURIComponent(currentPreview && currentPreview.logoVersion || '1');
  }

  function renderHero(academy) {
    return SECTIONS.renderHero(sectionView(academy), BUILDER_CTX);
  }

  // 패널은 수업 탭과 같은 짧은 이름을 쓴다. tabShortName이 비면 정식 수업명으로 떨어진다.

  // 대표 수업 패널. 상세는 sec-lessons가 담당하고 여기서는 네비게이션만 한다(클릭 → 스크롤).



  /** 채워진 히어로 슬롯인지. 비어 있으면 시안 placeholder로 렌더한다. */
  /**
   * 빌더는 미발행(DRAFT) 학원도 렌더한다. 공개 경로는 발행 상태를 요구해 404가 나므로
   * 소유자 전용 경로를 쓴다. v 파라미터는 업로드 직후 브라우저 캐시를 무효화한다.
   *
   * <p>아직 학원이 없는(발행 전) 상태에서는 서버에 올릴 곳이 없으므로 로컬 objectURL을 쓴다.
   */
  let heroMediaVersion = 1;




  // 학원 소개 좌측 컬럼은 nav·hero가 이미 보여준 로고/학원명을 반복하지 않고, 과목과 학원 특징을
  // 보여준다. 과목 뱃지 row는 hero와 같은 data-builder-basic="subjects" 모달로, 특징은 원장 약력과
  // 같은 가변 리스트 패턴(builderDraft.introFeatures)으로 편집한다.
  function renderIntro(academy) {
    return SECTIONS.renderIntro(sectionView(academy), BUILDER_CTX);
  }

  function draftText(academy, key, fallback) {
    const draft = academy && academy.pageSettings && academy.pageSettings.builderDraft;
    const value = draft && draft[key];
    return value == null || value === '' ? fallback : value;
  }

  // 원장 소개 — 시안(sec-principal) 이식. 좌 카드(이름/직함/소개/약력/인용) + 우 원장 소개 영상.
  // 이름·직함·소개·인용구는 scalar builderDraft(principalName/principalRole/principalDesc/principalQuote),
  // 약력은 가변 배열 builderDraft.principalCareer로 저장한다. 영상은 학원 영상 보관함의
  // principalVideoId 또는 principalYoutubeUrl 중 하나를 쓰며, intro 섹션과 동일한
  // data-builder-draft 인라인 편집 패턴을 따른다.
  function renderPrincipal(academy) {
    return SECTIONS.renderPrincipal(sectionView(academy), BUILDER_CTX);
  }

  function renderPhilosophy(academy) {
    return SECTIONS.renderPhilosophy(sectionView(academy), BUILDER_CTX);
  }

  function findUploadedVideo(videoId) {
    const id = String(videoId || '').trim();
    return id ? builderVideos.find((video) => String(video.videoId) === id) || null : null;
  }

  /**
   * 원장 소개/히어로 영상 picker와 대표 수업(미니 강의) 영상 picker가 섞이던 버그 수정.
   * Phase 3: purpose가 없는 레거시 업로드("용도 미지정")는 더 이상 두 picker 모두에 노출하지
   * 않는다 — 영상 관리 화면에서 명시적으로 분류해야 어느 picker에든 나타난다. 판정은
   * academy_sections.js(taxonomy 단일 소스)의 predicate를 그대로 쓴다.
   */
  function videosForPurpose(purpose) {
    const predicate = purpose === 'DIRECTOR' ? SECTIONS.isDirectorVideo : SECTIONS.isLessonVideo;
    return builderVideos.filter(predicate);
  }

  function uploadedVideoPlayer(video, className, title) {
    const poster = video.thumbnailUrl ? ` poster="${escapeAttr(video.thumbnailUrl)}"` : '';
    return `<video class="${escapeAttr(className)}" controls playsinline preload="metadata"${poster} src="${escapeAttr(video.videoUrl || '')}" aria-label="${escapeAttr(title)}"></video>`;
  }

  function renderLessons(academy) {
    return SECTIONS.renderLessons(sectionView(academy), BUILDER_CTX);
  }


  // 정원·대상은 편집기가 받지 않아 비어 있을 수 있다. 값이 있는 것만 한 줄로 모으고,
  // 없으면 줄 자체를 그린다. 기본값을 채우면 강사가 지정한 적 없는 값이 빌더에 보인다.
  function builderLessonMeta(lesson) {
    const parts = [];
    if (lesson.maxCapacity != null) {
      parts.push(`정원 ${lesson.currentCount || 0}/${lesson.maxCapacity}명`);
    }
    if (lesson.targetGroup) parts.push(targetLabel(lesson.targetGroup));
    return parts.length ? `<p>${escapeHtml(parts.join(' · '))}</p>` : '';
  }

  /**
   * 수업 영상 섹션. 빌더는 섹션 ON/OFF와 순서만 다루고 영상 자체의 업로드·삭제는 대시보드가 맡는다
   * (spec: 빌더 내 개별 영상 선택/정렬 UI 없음). 그래서 여기서는 미리보기 + 관리 진입점만 둔다.
   */
  // 발행(academy_public.js)과 같은 표기를 써야 캔버스가 실제 페이지와 어긋나지 않는다.

  function renderVideos(academy) {
    return SECTIONS.renderVideos(sectionView(academy), BUILDER_CTX);
  }

  function renderMiniLectures(academy) {
    return SECTIONS.renderMiniLectures(sectionView(academy), BUILDER_CTX);
  }

  function renderTeachers(academy) {
    return SECTIONS.renderTeachers(sectionView(academy));
  }

  function wireVideoManagement() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent || pageContent.dataset.videoManagementWired === 'true') return;
    pageContent.dataset.videoManagementWired = 'true';
    pageContent.addEventListener('click', (event) => {
      const target = event.target && event.target.closest
        ? event.target.closest('[data-video-publish-required], [data-lesson-publish-required]')
        : null;
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      const lessonManagement = target.matches('[data-lesson-publish-required]');
      toast(
        lessonManagement
          ? '학원 홈페이지를 먼저 발행한 후 수업을 등록할 수 있습니다.'
          : '학원 홈페이지를 먼저 발행한 후 영상을 등록할 수 있습니다.',
        'fa-circle-info'
      );
    });
  }

  // 마크업 정본은 academy_sections.js에 있다. 빌더 전용 편집 크롬(카드 컨테이너 자체가
  // URL 편집 진입점)은 BUILDER_CTX가 얹는다.
  function renderExternalServices(academy) {
    return SECTIONS.renderExternalServices(sectionView(academy), BUILDER_CTX);
  }

  // 상담 신청 CTA — 마크업 정본은 academy_sections.js, 빌더는 편집 ctx만 얹는다.
  // 버튼 클릭은 data-cta-edit 진입점으로 연락처/URL 편집 모달을 연다.
  function renderCta(academy) {
    return SECTIONS.renderCta(sectionView(academy), BUILDER_CTX);
  }

  function safeUrl(url) {
    return /^https?:\/\//i.test(String(url || '')) ? url : null;
  }

  function footerContactLine(businessInfo) {
    const raw = BUSINESS_INFO.normalizeRaw(businessInfo);
    const defaults = BUSINESS_INFO.MASKED_DEFAULTS || {};
    const address = raw.address || defaults.address || '00시 00구 00로 00';
    const phone = raw.phone || defaults.phone || '000-0000-0000';
    return `${escapeHtml(address)} · 상담 문의 ${escapeHtml(phone)}`;
  }

  function renderFooter(academy) {
    const year = new Date().getFullYear();
    const businessInfo = academy.pageSettings && academy.pageSettings.businessInfo;
    const editing = businessInfoEditor.phase !== 'idle';
    const rows = editing
      ? renderEditableBusinessInfoRows(
        businessInfoEditor.draftRaw || BUSINESS_INFO.normalizeRaw(businessInfo))
      : BUSINESS_INFO.renderRows(businessInfo, escapeHtml);
    const saving = businessInfoEditor.phase === 'saving';
    return `<footer class="cv-footer academy-business-footer${editing ? ' builder-section-style-selected builder-footer-section-editing' : ''}" id="${FIXED_FOOTER_SECTION_ID}" data-builder-section-key="${FIXED_FOOTER_SECTION_ID}" data-business-info-footer tabindex="0" aria-label="Footer 사업자 정보${editing ? ' 편집 중' : ' 편집'}" aria-busy="${saving}">
      ${editing ? renderBusinessInfoControls(saving) : ''}
      <div class="foot-top">
        <span class="eh-logo-component footer-logo">${renderLogoSymbol(academy, 'footer')}</span>
        <div class="foot-meta">
          <span class="counter"><span class="d"></span>오늘 - · 누적 -</span>
          <span class="madeby">Made with <span class="bi"><b>Build</b><i>It</i></span></span>
        </div>
      </div>
      <p class="f-addr">${footerContactLine(businessInfo)}</p>
      <div class="academy-business-footer__rows">${rows}</div>
      ${editing ? `<div class="academy-business-footer__error" id="builderBusinessInfoError" role="alert" aria-live="assertive" tabindex="-1">${escapeHtml(businessInfoEditor.error || '')}</div>` : ''}
      <p class="f-copy">© ${year} ${escapeHtml(academy.name)}. Powered by F2R.</p>
    </footer>`;
  }

  function renderEditableBusinessInfoRows(raw) {
    const normalized = BUSINESS_INFO.normalizeRaw(raw);
    const saving = businessInfoEditor.phase === 'saving';
    return BUSINESS_INFO.rows(normalized).map((group) => {
      const items = group.map((item) => {
        if (item.url) {
          return `<a class="academy-business-footer__link" href="${escapeHtml(item.url)}" target="_blank" rel="noopener">${escapeHtml(item.label)}</a>`;
        }
        if (item.disabled) {
          return `<span class="academy-business-footer__link is-disabled" aria-disabled="true">${escapeHtml(item.label)}</span>`;
        }
        const field = BUSINESS_INFO_FIELD_OPTIONS[item.key];
        const invalid = businessInfoEditor.errorField === item.key;
        const multiline = item.key === 'address';
        return `<span class="academy-business-footer__item" data-business-info-field="${escapeHtml(item.key)}"><b>${escapeHtml(item.label)}:</b> <span class="academy-business-footer__editable-value${invalid ? ' is-invalid' : ''}" data-business-info-input="${escapeHtml(item.key)}" data-business-info-placeholder="${escapeHtml(item.value)}" contenteditable="${saving ? 'false' : 'true'}" role="textbox" aria-label="${escapeHtml(field.label)}" aria-multiline="${multiline}" aria-describedby="builderBusinessInfoError" aria-invalid="${invalid}" spellcheck="false">${escapeHtml(normalized[item.key])}</span></span>`;
      }).join('');
      return `<div class="academy-business-footer__row">${items}</div>`;
    }).join('');
  }

  function renderBusinessInfoControls(saving) {
    return `<div class="block-controls builder-section-controls builder-footer-controls">
      <button type="button" class="block-ctrl-btn builder-section-ctrl-btn" data-business-info-action="apply" aria-busy="${saving}"${saving ? ' disabled' : ''}><i class="fa-solid fa-check"></i> ${saving ? '저장 중…' : '적용'}</button>
    </div>`;
  }

  function normalizePreview(preview) {
    return {
      name: (preview && preview.name) || '새 학원',
      intro: (preview && preview.intro) || '학원 소개를 입력해 주세요.',
      subjects: Array.isArray(preview && preview.subjects) ? preview.subjects : [],
      targetGroups: Array.isArray(preview && preview.targetGroups) ? preview.targetGroups : [],
      lessons: Array.isArray(preview && preview.lessons) ? preview.lessons : [],
      regions: normalizeRegions(preview && preview.regions),
      handle: preview && preview.handle,
      slug: preview && preview.slug,
      publicUrl: preview && preview.publicUrl,
      publicPathUrl: preview && preview.publicPathUrl,
      displayUrl: preview && preview.displayUrl,
      pageSettings: Object.assign({}, (preview && preview.pageSettings) || {}, {
        sectionVisibility: normalizeSectionVisibility(
          preview && (preview.sectionVisibility || (preview.pageSettings && preview.pageSettings.sectionVisibility))
        )
      }),
      logoUrl: preview && preview.logoUrl,
      logoVersion: preview && preview.logoVersion,
      // 이 화이트리스트에서 빠지면 렌더 직전에 값이 사라진다. heroMediaSlots가 없으면
      // hasHeroMedia()가 항상 false라 업로드한 원장사진·교재표지가 영영 placeholder로만 보인다.
      heroMediaSlots: Array.isArray(preview && preview.heroMediaSlots) ? preview.heroMediaSlots : [],
      sectionVisibility: normalizeSectionVisibility(
        preview && (preview.sectionVisibility || (preview.pageSettings && preview.pageSettings.sectionVisibility))
      ),
      sectionOrder: sectionOrderFrom(preview)
    };
  }

  function normalizeRegions(regions) {
    if (!Array.isArray(regions)) return [];
    return regions.map((r) => ({
      city: r.city,
      district: r.district,
      displayName: r.displayName || [r.city, r.district].filter(Boolean).join(' ')
    })).filter((r) => r.displayName);
  }

  function readSsrAcademy() {
    const source = document.getElementById('pageContent');
    if (!source || !source.dataset || !source.dataset.academySlug) return null;
    const academy = readJsonDataset(source.dataset.academyJson);
    if (academy && academy.slug) {
      academy.publicPathUrl = source.dataset.academyPathUrl;
      academy.displayUrl = source.dataset.academyDisplayUrl;
      academy.sectionVisibility = normalizeSectionVisibility(
        (academy.pageSettings && academy.pageSettings.sectionVisibility) || academy.sectionVisibility
      );
      academy.sectionOrder = sectionOrderFrom(academy);
      return academy;
    }
    return {
      name: source.dataset.academyName,
      slug: source.dataset.academySlug,
      publicUrl: source.dataset.academyPublicUrl,
      publicPathUrl: source.dataset.academyPathUrl,
      displayUrl: source.dataset.academyDisplayUrl,
      sectionVisibility: normalizeSectionVisibility(readJsonDataset(source.dataset.sectionVisibility)),
      sectionOrder: DEFAULT_SECTION_ORDER.slice(),
      subjects: [],
      targetGroups: [],
      regions: []
    };
  }

  function previewFromRequest(request) {
    if (!request) return null;
    return {
      name: request.name,
      intro: request.intro,
      subjects: subjectPreviewsFromRequest(request),
      targetGroups: request.targetGroups || [],
      regions: normalizeRegions(request.regions),
      handle: request.handle,
      pageSettings: Object.assign({}, request.pageSettings || {}, {
        sectionVisibility: normalizeSectionVisibility(request.pageSettings && request.pageSettings.sectionVisibility)
      }),
      displayUrl: request.handle ? request.handle + '.feel2real.co.kr' : null,
      sectionVisibility: normalizeSectionVisibility(request.pageSettings && request.pageSettings.sectionVisibility),
      sectionOrder: sectionOrderFrom(request)
    };
  }

  function wireSectionVisibility(preview) {
    const buttons = Array.from(document.querySelectorAll('[data-section-toggle]'));
    if (!buttons.length) return;
    const enabled = !!preview;
    buttons.forEach((button) => {
      const requestedSectionId = canonicalSectionId(button.dataset.sectionToggle || button.dataset.sectionId);
      if (FUTURE_SECTIONS.has(requestedSectionId)) {
        button.setAttribute('aria-disabled', 'true');
        button.removeAttribute('tabindex');
        return;
      }
      if ('disabled' in button) button.disabled = !enabled;
      button.setAttribute('aria-disabled', String(!enabled));
      if (!enabled) button.title = '랜딩에서 학원 정보를 입력한 뒤 섹션 표시를 변경할 수 있습니다.';
      else if (!preview.publicPathUrl) button.title = '발행 전 변경값은 발행 버튼을 누를 때 함께 저장됩니다.';
      const sectionId = requestedSectionId;
      if (sectionId) button.dataset.sectionKey = sectionId;
      else button.setAttribute('aria-disabled', 'true');
      button.addEventListener('click', () => toggleSectionVisibility(button.dataset.sectionKey, button));
      button.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        event.preventDefault();
        toggleSectionVisibility(button.dataset.sectionKey, button);
      });
    });
    applySectionVisibilityState(normalizeSectionVisibility(preview && preview.sectionVisibility));
  }

  async function toggleSectionVisibility(sectionId, button) {
    sectionId = canonicalSectionId(sectionId);
    if (!sectionId || sectionId === 'sec-hero' || FUTURE_SECTIONS.has(sectionId) || !currentPreview) {
      toast(sectionId === 'sec-hero' ? '히어로 배너는 기본 고정 섹션이라 항상 ON입니다.' : '랜딩에서 학원 정보를 입력한 뒤 섹션 표시를 변경할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const previousVisibility = normalizeSectionVisibility(currentPreview.sectionVisibility);
    const visibility = normalizeSectionVisibility(previousVisibility);
    // opt-in 섹션(기본 OFF)은 명시적 true일 때만 보인다. opt-out 섹션(기본 ON)은 명시적 false일 때만 숨는다.
    // 어느 쪽이든 토글은 "현재 유효 상태"의 반대를 명시적 boolean으로 저장한다.
    const currentlyVisible = OPT_IN_SECTIONS.has(sectionId)
      ? visibility[sectionId] === true
      : visibility[sectionId] !== false;
    visibility[sectionId] = !currentlyVisible;
    currentPreview.sectionVisibility = visibility;
    applySectionVisibilityState(visibility);

    if (!currentPreview.publicPathUrl) {
      persistPendingSectionVisibility(visibility);
      renderPublicPreview(currentPreview, visibility[sectionId] === false ? null : sectionId);
      applySectionVisibilityState(visibility);
      toast(visibility[sectionId] === false ? '발행 전 미리보기에서 섹션을 숨겼습니다.' : '발행 전 미리보기에 섹션을 다시 표시합니다.', 'fa-eye');
      return;
    }

    setSaving(button, true);
    try {
      const res = await fetch('/api/tutor/academy/page-settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(pageSettingsWriteBody(currentPreview.pageSettings, { sectionVisibility: visibility }))
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      currentPreview.sectionVisibility = normalizeSectionVisibility(
        (academy.pageSettings && academy.pageSettings.sectionVisibility) || visibility
      );
      applySectionVisibilityState(currentPreview.sectionVisibility);
      applyAcademyToLivePreview(currentPreview, visibility[sectionId] === false ? null : sectionId);
      toast(visibility[sectionId] === false ? '섹션을 숨겼습니다.' : '섹션을 다시 표시합니다.', 'fa-eye');
    } catch (_) {
      currentPreview.sectionVisibility = previousVisibility;
      applySectionVisibilityState(previousVisibility);
      toast('섹션 표시 설정 저장에 실패했습니다.', 'fa-triangle-exclamation');
    } finally {
      setSaving(button, false);
    }
  }

  function persistPendingSectionVisibility(visibility) {
    const nextVisibility = normalizeSectionVisibility(visibility);
    const request = readJson(PENDING_KEY);
    if (request) {
      request.pageSettings = Object.assign({}, request.pageSettings || {}, { sectionVisibility: nextVisibility });
      writeJson(PENDING_KEY, request);
    }
    const preview = readJson(PENDING_PREVIEW_KEY) || currentPreview;
    if (preview) {
      preview.sectionVisibility = nextVisibility;
      preview.pageSettings = Object.assign({}, preview.pageSettings || {}, { sectionVisibility: nextVisibility });
      writeJson(PENDING_PREVIEW_KEY, preview);
    }
  }

  function wirePanelTabs() {
    const tabs = Array.from(document.querySelectorAll('[data-builder-panel-tab]'));
    if (!tabs.length) return;
    tabs.forEach((tab) => {
      tab.addEventListener('click', () => activatePanelTab(tab.dataset.builderPanelTab));
    });
  }

  function activatePanelTab(name) {
    document.querySelectorAll('[data-builder-panel-tab]').forEach((tab) => {
      const active = tab.dataset.builderPanelTab === name;
      tab.classList.toggle('active', active);
      tab.setAttribute('aria-selected', String(active));
    });
    document.querySelectorAll('[data-builder-panel-pane]').forEach((pane) => {
      pane.hidden = pane.dataset.builderPanelPane !== name;
    });
  }

  function wireLayoutSectionOrder(preview) {
    renderLayoutSectionOrder(preview);
    const list = document.querySelector('[data-section-order-list]');
    if (!list) return;
    list.addEventListener('click', (event) => {
      const button = event.target.closest('[data-section-order-move]');
      if (!button || !list.contains(button)) return;
      moveSectionOrder(button.dataset.sectionId, button.dataset.sectionOrderMove, button);
    });
  }

  function renderLayoutSectionOrder(preview) {
    const list = document.querySelector('[data-section-order-list]');
    if (!list) return;
    const order = layoutSectionIds(preview || currentPreview);
    list.innerHTML = order.map((id, index) => layoutRowHtml(id, index, order.length)).join('');
  }

  function layoutRowHtml(id, index, total) {
    const locked = id === 'sec-hero' || id === FIXED_FOOTER_SECTION_ID;
    const fixedNavigation = locked
      ? ` data-fixed-section-nav="${escapeHtml(id)}" role="button" tabindex="0"`
      : '';
    return `<div class="layout-section-row${locked ? ' is-locked' : ''}" data-section-order-row="${escapeHtml(id)}"${fixedNavigation}>
      <span class="layout-section-lock">${locked ? 'LOCK' : String(index + 1).padStart(2, '0')}</span>
      <div class="layout-section-main"><strong>${escapeHtml(STYLE_PANEL_SECTION_NAMES[id] || id)}</strong><span>${escapeHtml(id)}</span></div>
      <div class="layout-section-actions">
        <button type="button" data-section-order-move="up" data-section-id="${escapeHtml(id)}" ${locked || index <= 1 ? 'disabled' : ''} aria-label="위로 이동"><i class="fa-solid fa-arrow-up"></i></button>
        <button type="button" data-section-order-move="down" data-section-id="${escapeHtml(id)}" ${locked || index >= total - 1 ? 'disabled' : ''} aria-label="아래로 이동"><i class="fa-solid fa-arrow-down"></i></button>
      </div>
    </div>`;
  }

  function wireFixedSectionNavigation() {
    if (document.documentElement.dataset.fixedSectionNavigationWired === 'true') return;
    document.documentElement.dataset.fixedSectionNavigationWired = 'true';
    document.addEventListener('click', (event) => {
      const item = event.target.closest('[data-fixed-section-nav]');
      if (!item) return;
      event.preventDefault();
      focusFixedBuilderSection(item.dataset.fixedSectionNav);
    });
    document.addEventListener('keydown', (event) => {
      const item = event.target.closest('[data-fixed-section-nav]');
      if (!item || (event.key !== 'Enter' && event.key !== ' ')) return;
      event.preventDefault();
      focusFixedBuilderSection(item.dataset.fixedSectionNav);
    });
  }

  function focusFixedBuilderSection(sectionId) {
    const target = previewSectionTarget(sectionId);
    if (!target) return;
    scrollPreviewToSection(sectionId);
    target.classList.remove('builder-fixed-section-highlight');
    void target.offsetWidth;
    target.classList.add('builder-fixed-section-highlight');
    setTimeout(() => target.classList.remove('builder-fixed-section-highlight'), 1400);
    toast(sectionId === FIXED_FOOTER_SECTION_ID
      ? 'Footer는 페이지 끝에 고정되며 항상 표시됩니다.'
      : '히어로 배너는 페이지 처음에 고정되며 항상 표시됩니다.', 'fa-lock');
  }

  async function moveSectionOrder(sectionId, direction, button) {
    sectionId = canonicalSectionId(sectionId);
    if (!sectionId || sectionId === 'sec-hero' || !currentPreview) {
      toast('히어로 배너는 첫 번째에 고정됩니다.', 'fa-lock');
      return;
    }
    const previous = sectionOrderFrom(currentPreview);
    const order = nextSectionOrder(previous, sectionId, direction, currentPreview && (currentPreview.sectionVisibility || (currentPreview.pageSettings && currentPreview.pageSettings.sectionVisibility)));
    if (!order) return;
    currentPreview.sectionOrder = order;
    currentPreview.pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { sectionOrder: order });
    renderLayoutSectionOrder(currentPreview);

    if (!currentPreview.publicPathUrl) {
      persistPendingSectionOrder(order);
      renderPublicPreview(currentPreview, sectionId);
      toast('발행 전 레이아웃 순서를 미리보기에 반영했습니다.', 'fa-arrow-up-wide-short');
      return;
    }

    setSaving(button, true);
    try {
      const res = await fetch('/api/tutor/academy/page-settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(pageSettingsWriteBody(currentPreview.pageSettings, { sectionOrder: order }))
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      applyAcademyToLivePreview(currentPreview, sectionId);
      renderLayoutSectionOrder(currentPreview);
      toast('레이아웃 순서를 저장했습니다.', 'fa-arrow-up-wide-short');
    } catch (_) {
      currentPreview.sectionOrder = previous;
      currentPreview.pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { sectionOrder: previous });
      renderLayoutSectionOrder(currentPreview);
      toast('레이아웃 순서 저장에 실패했습니다.', 'fa-triangle-exclamation');
    } finally {
      setSaving(button, false);
    }
  }

  function nextSectionOrder(order, sectionId, direction, visibility) {
    const normalized = normalizeSectionOrder(order);
    const id = canonicalSectionId(sectionId);
    if (!id || id === 'sec-hero') return null;
    const visibleOrder = normalized.filter((candidate) => candidate === 'sec-hero' || !(visibility && visibility[candidate] === false));
    const visibleIndex = visibleOrder.indexOf(id);
    const delta = direction === 'up' ? -1 : 1;
    const targetVisibleIndex = visibleIndex + delta;
    if (visibleIndex <= 0 || targetVisibleIndex <= 0 || targetVisibleIndex >= visibleOrder.length) return null;
    const targetId = visibleOrder[targetVisibleIndex];
    const index = normalized.indexOf(id);
    const targetIndex = normalized.indexOf(targetId);
    if (index <= 0 || targetIndex <= 0) return null;
    const next = normalized.slice();
    [next[index], next[targetIndex]] = [next[targetIndex], next[index]];
    return next;
  }

  function persistPendingSectionOrder(order) {
    const nextOrder = normalizeSectionOrder(order);
    const request = readJson(PENDING_KEY);
    if (request) {
      request.pageSettings = Object.assign({}, request.pageSettings || {}, { sectionOrder: nextOrder });
      writeJson(PENDING_KEY, request);
    }
    const preview = readJson(PENDING_PREVIEW_KEY) || currentPreview;
    if (preview) {
      preview.sectionOrder = nextOrder;
      preview.pageSettings = Object.assign({}, preview.pageSettings || {}, { sectionOrder: nextOrder });
      writeJson(PENDING_PREVIEW_KEY, preview);
    }
  }


  function toggleLessonSchedule(button) {
    const wrap = button.closest('.lesson-schedule-wrap');
    const nextChecked = button.getAttribute('aria-checked') !== 'true';
    button.setAttribute('aria-checked', String(nextChecked));
    button.classList.toggle('on', nextChecked);
    if (wrap) wrap.classList.toggle('is-schedule-hidden', !nextChecked);
  }

  function wireLessonScheduleToggle() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent || pageContent.dataset.scheduleToggleWired === 'true') return;
    pageContent.dataset.scheduleToggleWired = 'true';
    pageContent.addEventListener('click', (event) => {
      const toggle = event.target && event.target.closest
        ? event.target.closest('[data-schedule-toggle]')
        : null;
      if (!toggle || !pageContent.contains(toggle)) return;
      event.preventDefault();
      event.stopPropagation();
      toggleLessonSchedule(toggle);
    });
  }

  function wirePreviewSectionSelection() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent || pageContent.dataset.sectionSelectionWired === 'true') return;
    pageContent.dataset.sectionSelectionWired = 'true';
    pageContent.addEventListener('click', (event) => {
      if (isActualEditTarget(event.target)) return;
      const section = event.target.closest('[data-builder-section-key]');
      // 리본/빈 영역 등 관리 섹션 밖을 클릭하면 선택을 해제한다(컨트롤이 sticky nav에 남는 버그 방지).
      // GNB는 sec-header로 선택 가능하다 — 상단 학원명을 히어로와 따로 고치기 위한 영역이다.
      if (!section || !pageContent.contains(section)) {
        clearActiveSectionSelection();
        return;
      }
      enterSectionTextEditMode(section);
    });
  }

  function wireEditableGuide() {
    const button = document.getElementById('builderEditableGuideBtn');
    const frame = document.getElementById('canvasFrame');
    const coachmark = document.getElementById('builderEditableGuideCoachmark');
    if (!button || !frame || button.dataset.wired === 'true') return;
    button.dataset.wired = 'true';

    button.addEventListener('click', () => {
      hideEditableGuideCoachmark();
      setEditableGuideActive(!editableGuideActive);
    });
    document.addEventListener('click', (event) => {
      if (!editableGuideActive) return;
      if (event.target.closest('#builderEditableGuideBtn,#builderEditableGuideCoachmark')) return;
      // 안내 상태만 먼저 닫고 클릭은 소비하지 않아 기존 텍스트/로고/영상/기본정보 핸들러가
      // 같은 첫 클릭으로 그대로 실행되게 한다.
      setEditableGuideActive(false);
    }, true);
    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') return;
      if (editableGuideActive) setEditableGuideActive(false);
      if (coachmark && !coachmark.hidden) hideEditableGuideCoachmark();
    });

    const start = coachmark && coachmark.querySelector('[data-editable-guide-coachmark-start]');
    const close = coachmark && coachmark.querySelector('[data-editable-guide-coachmark-close]');
    if (start) start.addEventListener('click', () => {
      hideEditableGuideCoachmark();
      setEditableGuideActive(true);
      button.focus();
    });
    if (close) close.addEventListener('click', () => {
      hideEditableGuideCoachmark();
      button.focus();
    });

    if (coachmark && EDITABLE_GUIDE.shouldShowCoachmark(editableGuideStorage())) {
      EDITABLE_GUIDE.markCoachmarkSeen(editableGuideStorage());
      setTimeout(() => { coachmark.hidden = false; }, 500);
    }
  }

  function setEditableGuideActive(active) {
    const frame = document.getElementById('canvasFrame');
    const button = document.getElementById('builderEditableGuideBtn');
    const label = button && button.querySelector('[data-editable-guide-label]');
    editableGuideActive = Boolean(active);
    if (frame) frame.classList.toggle('builder-editable-guide-active', editableGuideActive);
    if (button) button.setAttribute('aria-pressed', String(editableGuideActive));
    if (label) label.textContent = editableGuideActive ? '표시 종료' : '수정할 곳 보기';
  }

  function hideEditableGuideCoachmark() {
    const coachmark = document.getElementById('builderEditableGuideCoachmark');
    if (coachmark) coachmark.hidden = true;
    EDITABLE_GUIDE.markCoachmarkSeen(editableGuideStorage());
  }

  function editableGuideStorage() {
    try {
      return window.localStorage;
    } catch (_) {
      return null;
    }
  }

  /**
   * 헤더 '적용' 버튼은 activeTextEditSection을 그대로 비추는 미러다. 대입 지점이 여러 곳
   * (편집 진입 / 선택 해제 / 캔버스 재렌더)에 흩어져 있어 한 곳만 빠뜨려도 편집이 끝난 뒤에도
   * 버튼이 남는다. 그래서 대입은 반드시 이 setter만 쓴다.
   */
  function setActiveTextEditSection(section) {
    activeTextEditSection = section || null;
    syncHeaderSectionApply();
  }

  function syncHeaderSectionApply() {
    const button = document.getElementById('builderSectionApplyBtn');
    if (!button) return;
    button.hidden = !activeTextEditSection;
    if (!activeTextEditSection) return;
    const label = button.querySelector('[data-section-apply-label]');
    // 히어로는 canonicalSectionId가 null이므로 섹션 키를 그대로 조회한다.
    const name = STYLE_PANEL_SECTION_NAMES[activeTextEditSection.dataset.builderSectionKey];
    if (label) label.textContent = name ? `${name} 적용` : '적용';
  }

  function wireHeaderSectionApply() {
    const button = document.getElementById('builderSectionApplyBtn');
    if (!button || button.dataset.wired === 'true') return;
    button.dataset.wired = 'true';
    button.addEventListener('click', () => {
      if (activeTextEditSection) applySectionTextChanges(activeTextEditSection);
    });
  }

  function clearActiveSectionSelection() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return true;
    if (activeTextEditSection) {
      // 다른 섹션으로 전환할 때와 동일하게 보류 중인 텍스트 변경을 먼저 커밋한다.
      if (!applySectionTextChanges(activeTextEditSection, { reason: 'section-switch', suppressNoChangeToast: true })) return false;
      setTextTargetsEditable(activeTextEditSection, false);
      removeSectionTextApply(activeTextEditSection);
      activeTextEditSection.classList.remove('builder-text-section-editing');
      setActiveTextEditSection(null);
    }
    pageContent.querySelectorAll('.builder-section-style-selected')
      .forEach((el) => el.classList.remove('builder-section-style-selected'));
    return true;
  }

  function isActualEditTarget(target) {
    if (!target) return false;
    return !!target.closest([
      '[contenteditable="true"]',
      '[data-builder-text]',
      '[data-builder-draft]',
      '[data-builder-logo]',
      '[data-builder-basic]',
      '[data-hero-video-edit]',
      '[data-principal-video-edit]',
      '[data-principal-career-item]',
      '[data-principal-career-add]',
      '[data-principal-career-remove]',
      '[data-intro-feature-item]',
      '[data-intro-feature-body]',
      'button',
      'a',
      'input',
      'select',
      'textarea',
      'label',
      '[role="button"]'
    ].join(','));
  }

  function markSelectedSection(section) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent || !section) return;
    ensureSectionStyleSelectionStyle();
    pageContent.querySelectorAll('.builder-section-style-selected')
      .forEach((el) => el.classList.remove('builder-section-style-selected'));
    section.classList.add('builder-section-style-selected');
  }

  function ensureSectionStyleSelectionStyle() {
    if (document.getElementById('f2rBuilderSectionStyleSelectionStyle')) return;
    const style = document.createElement('style');
    style.id = 'f2rBuilderSectionStyleSelectionStyle';
    style.textContent = '.builder-public-preview{overflow:visible;} .builder-public-preview [data-builder-section-key]{cursor:pointer;} .builder-public-preview [contenteditable="true"],.builder-public-preview [data-builder-logo],.builder-public-preview [data-builder-basic],.builder-public-preview [data-hero-video-edit],.builder-public-preview button,.builder-public-preview a{cursor:auto;} .builder-public-preview [data-builder-text][contenteditable="false"],.builder-public-preview [data-builder-draft][contenteditable="false"]{cursor:pointer;} .builder-section-style-selected{position:relative!important;z-index:900!important;overflow:visible!important;outline:3px solid rgba(74,127,212,.72)!important;outline-offset:-3px!important;box-shadow:inset 0 0 0 9999px rgba(74,127,212,.035)!important;} .builder-section-controls.block-controls{position:absolute;top:-14px;right:8px;display:flex;gap:4px;z-index:2000;pointer-events:auto;} .builder-section-controls.builder-header-controls{top:auto;bottom:-14px;} .builder-section-controls .block-ctrl-btn{background:#0C238A;border:1px solid rgba(255,255,255,.2);color:#fff;font-size:.65rem;padding:3px 7px;border-radius:5px;cursor:pointer;white-space:nowrap;transition:background .15s;box-shadow:0 2px 8px rgba(12,35,138,.35);} .builder-section-controls .block-ctrl-btn:hover{background:#1a3ab0;} .builder-section-controls .block-ctrl-btn.danger:hover{background:#dc2626;} .builder-text-section-editing [data-builder-text],.builder-text-section-editing [data-builder-draft]{outline:1px dashed rgba(250,204,21,.65);outline-offset:2px;border-radius:6px;} .builder-text-invalid{outline:3px solid #ef4444!important;outline-offset:3px!important;border-radius:8px!important;} .builder-multiline-text{white-space:pre-line;}';
    document.head.appendChild(style);
  }


  function wireTextEditing(preview) {
    const buttons = Array.from(document.querySelectorAll('[data-text-edit]'));
    const enabled = !!preview;
    buttons.forEach((button) => {
      if ('disabled' in button) button.disabled = !enabled;
      button.setAttribute('aria-disabled', String(!enabled));
      button.title = enabled ? '미리보기 텍스트 위치로 이동해 섹션 편집 모드로 수정합니다.' : '랜딩에서 학원 정보를 입력한 뒤 텍스트를 편집할 수 있습니다.';
      button.addEventListener('click', () => focusInlineText(button.dataset.textEdit));
    });

    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    setTextTargetsEditable(pageContent, false);

    pageContent.addEventListener('click', (event) => {
      const target = editableTextTarget(event.target);
      if (!target || !pageContent.contains(target)) return;
      const section = target.closest('[data-builder-section-key]');
      if (!section) return;
      // 이미 편집 중인 대상을 다시 클릭한 경우: preventDefault 하지 않고 브라우저가
      // 클릭 위치에 caret을 놓게 그대로 둔다. 여기서 재진입 처리를 하면 caret이 매번
      // 문장 끝/전체로 튀어 "원하는 글자 위치에서 못 고치는" 기존 버그가 재현된다.
      if (target.getAttribute('contenteditable') === 'true' && activeTextEditSection === section) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      const point = { x: event.clientX, y: event.clientY };
      if (!enterSectionTextEditMode(section)) return;
      focusEditableTarget(target, point);
    });
    pageContent.addEventListener('focusin', (event) => {
      const target = editableTextTarget(event.target);
      if (!target) return;
      activeTextField = textFieldIdentity(target);
      target.dataset.beforeEdit = TEXT_UTILS.extractEditableText(target);
      highlightTextTargets(activeTextField);
    });
    pageContent.addEventListener('beforeinput', (event) => {
      const target = editableTextTarget(event.target);
      if (!target || !TEXT_UTILS.isSingleLineTextTarget(target)) return;
      if (event.inputType === 'insertParagraph' || event.inputType === 'insertLineBreak') event.preventDefault();
    });
    pageContent.addEventListener('paste', (event) => {
      const target = editableTextTarget(event.target);
      if (!target || !TEXT_UTILS.isSingleLineTextTarget(target)) return;
      const text = event.clipboardData && event.clipboardData.getData('text/plain');
      if (!text || !/[\r\n\t]/.test(text)) return;
      event.preventDefault();
      insertPlainText(TEXT_UTILS.normalizeSingleLineText(text));
    });
    pageContent.addEventListener('keydown', (event) => {
      const target = editableTextTarget(event.target);
      if (!target) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        target.textContent = target.dataset.beforeEdit || currentTextValue(target) || '';
        target.blur();
        return;
      }
      if (event.key === 'Enter' && TEXT_UTILS.isSingleLineTextTarget(target)) {
        event.preventDefault();
      }
    });
  }


  function wireBasicDataEditing(preview) {
    const pageContent = document.getElementById('pageContent');
    if (pageContent) {
      pageContent.addEventListener('click', (event) => {
        const target = event.target && event.target.closest ? event.target.closest('[data-builder-basic]') : null;
        if (!target) return;
        event.preventDefault();
        event.stopPropagation();
        openBasicDataModal(target.dataset.builderBasic);
      });
      pageContent.addEventListener('keydown', (event) => {
        const target = event.target && event.target.closest ? event.target.closest('[data-builder-basic]') : null;
        if (!target || (event.key !== 'Enter' && event.key !== ' ')) return;
        event.preventDefault();
        openBasicDataModal(target.dataset.builderBasic);
      });
    }
    wireTargetGroupsModal();
    wireSubjectsModal();
    wireRegionsModal();
  }

  function openBasicDataModal(kind) {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 수정할 수 있습니다.', 'fa-circle-info');
      return;
    }
    if (kind === 'targetGroups') openTargetGroupsModal();
    if (kind === 'subjects') openSubjectsModal();
    if (kind === 'regions') openRegionsModal();
  }

  function wireTargetGroupsModal() {
    const modal = document.getElementById('builderTargetGroupsModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-basic-close]').forEach((button) => button.addEventListener('click', closeBasicDataModals));
    modal.addEventListener('click', (event) => { if (event.target === modal) closeBasicDataModals(); });
    const apply = document.getElementById('builderTargetGroupsApplyBtn');
    if (apply) apply.addEventListener('click', applyTargetGroupsFromModal);
  }


  function wireSubjectsModal() {
    const modal = document.getElementById('builderSubjectsModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-basic-close]').forEach((button) => button.addEventListener('click', closeBasicDataModals));
    modal.addEventListener('click', (event) => { if (event.target === modal) closeBasicDataModals(); });
    const apply = document.getElementById('builderSubjectsApplyBtn');
    if (apply) apply.addEventListener('click', applySubjectsFromModal);
  }

  async function openSubjectsModal() {
    const modal = document.getElementById('builderSubjectsModal');
    const box = document.getElementById('builderSubjectsOptions');
    if (!modal || !box) return;
    box.innerHTML = '<p class="lesson-empty">과목을 불러오는 중...</p>';
    clearBasicDataError('builderSubjectsError');
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    const options = await loadSubjectOptions();
    const selected = new Set((currentPreview.subjects || []).map((s) => String(s.subjectId)).filter(Boolean));
    box.innerHTML = options.map((subject) => `<label class="builder-option-check"><input type="checkbox" value="${subject.subjectId}" ${selected.has(String(subject.subjectId)) ? 'checked' : ''}> ${escapeHtml(subject.nameKo || subject.nameEn || '과목')}</label>`).join('');
  }

  async function loadSubjectOptions() {
    if (subjectOptionsCache) return subjectOptionsCache;
    try {
      const res = await fetch('/api/subjects', { credentials: 'same-origin' });
      subjectOptionsCache = res.ok ? await res.json() : [];
    } catch (_) {
      subjectOptionsCache = [];
    }
    return subjectOptionsCache;
  }

  async function applySubjectsFromModal() {
    const apply = document.getElementById('builderSubjectsApplyBtn');
    const options = await loadSubjectOptions();
    const byId = new Map(options.map((subject) => [String(subject.subjectId), subject]));
    const ids = Array.from(document.querySelectorAll('#builderSubjectsOptions input:checked')).map((input) => input.value);
    if (!ids.length) {
      setBasicDataError('builderSubjectsError', '과목은 1개 이상 선택해 주세요.');
      return;
    }
    const previous = Array.isArray(currentPreview.subjects) ? currentPreview.subjects.slice() : [];
    currentPreview.subjects = ids.map((id) => byId.get(String(id))).filter(Boolean);
    const ok = await saveBasicDataChanges(apply, '과목을 저장했습니다.', 'subjects');
    if (ok) closeBasicDataModals();
    else currentPreview.subjects = previous;
  }

  function openTargetGroupsModal() {
    const modal = document.getElementById('builderTargetGroupsModal');
    const box = document.getElementById('builderTargetGroupsOptions');
    if (!modal || !box) return;
    const selected = new Set(currentPreview.targetGroups || []);
    box.innerHTML = TARGET_GROUP_OPTIONS.map(([value, label]) => `<label class="builder-option-check"><input type="checkbox" value="${value}" ${selected.has(value) ? 'checked' : ''}> ${escapeHtml(label)}</label>`).join('');
    clearBasicDataError('builderTargetGroupsError');
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
  }

  async function applyTargetGroupsFromModal() {
    const modal = document.getElementById('builderTargetGroupsModal');
    const apply = document.getElementById('builderTargetGroupsApplyBtn');
    const values = Array.from(document.querySelectorAll('#builderTargetGroupsOptions input:checked')).map((input) => input.value);
    if (!values.length) {
      setBasicDataError('builderTargetGroupsError', '수업대상은 1개 이상 선택해 주세요.');
      return;
    }
    const previous = Array.from(currentPreview.targetGroups || []);
    currentPreview.targetGroups = values;
    const ok = await saveBasicDataChanges(apply, '수업대상을 저장했습니다.', 'targetGroups');
    if (ok) closeBasicDataModals();
    else currentPreview.targetGroups = previous;
    if (!modal || !modal.classList.contains('is-open')) return;
  }

  function wireRegionsModal() {
    const modal = document.getElementById('builderRegionsModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-basic-close]').forEach((button) => button.addEventListener('click', closeBasicDataModals));
    modal.addEventListener('click', (event) => { if (event.target === modal) closeBasicDataModals(); });
    const apply = document.getElementById('builderRegionsApplyBtn');
    if (apply) apply.addEventListener('click', applyRegionsFromModal);
  }

  function openRegionsModal() {
    const modal = document.getElementById('builderRegionsModal');
    const rows = document.getElementById('builderRegionRows');
    if (!modal || !rows) return;
    const regions = normalizeRegions(currentPreview.regions || []);
    rows.innerHTML = [0, 1, 2].map((idx) => regionRowHtml(idx, regions[idx])).join('');
    rows.querySelectorAll('[data-region-city]').forEach((select) => {
      select.addEventListener('change', () => populateBuilderDistrict(select.dataset.regionCity));
      populateBuilderDistrict(select.dataset.regionCity, regions[Number(select.dataset.regionCity)] && regions[Number(select.dataset.regionCity)].district);
    });
    clearBasicDataError('builderRegionsError');
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
  }

  function regionRowHtml(idx, region) {
    const city = region && region.city;
    return `<div class="builder-region-row">
      <select data-region-city="${idx}" aria-label="지역 ${idx + 1} 시/도">
        <option value="">${idx === 0 ? '시/도 선택' : '선택 안 함'}</option>
        ${Object.keys(REGION_OPTIONS).map((name) => `<option value="${escapeHtml(name)}" ${name === city ? 'selected' : ''}>${escapeHtml(name)}</option>`).join('')}
      </select>
      <select data-region-district="${idx}" aria-label="지역 ${idx + 1} 구/군"></select>
    </div>`;
  }

  function populateBuilderDistrict(idx, selectedDistrict) {
    const city = document.querySelector('[data-region-city="' + idx + '"]');
    const district = document.querySelector('[data-region-district="' + idx + '"]');
    if (!city || !district) return;
    const districts = city.value ? REGION_OPTIONS[city.value] || [] : [];
    district.disabled = !city.value;
    district.innerHTML = '<option value="">전 지역</option>' + districts
      .filter((name) => name !== '전 지역')
      .map((name) => `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`)
      .join('');
    if (selectedDistrict && selectedDistrict !== 'ALL' && !district.querySelector('option[value="' + cssAttrEscape(selectedDistrict) + '"]')) {
      district.insertAdjacentHTML('beforeend', `<option value="${escapeHtml(selectedDistrict)}">${escapeHtml(selectedDistrict)}</option>`);
    }
    district.value = selectedDistrict && selectedDistrict !== 'ALL' ? selectedDistrict : '';
  }

  async function applyRegionsFromModal() {
    const apply = document.getElementById('builderRegionsApplyBtn');
    const regions = [0, 1, 2].map((idx) => {
      const city = document.querySelector('[data-region-city="' + idx + '"]');
      const district = document.querySelector('[data-region-district="' + idx + '"]');
      return { city: city && city.value, district: district && district.value ? district.value : null };
    }).filter((region) => region.city);
    if (!regions.length) {
      setBasicDataError('builderRegionsError', '지역은 1개 이상 선택해 주세요.');
      return;
    }
    const keySet = new Set();
    for (const region of regions) {
      const key = region.city + '|' + (region.district || 'ALL');
      if (keySet.has(key)) {
        setBasicDataError('builderRegionsError', '같은 지역은 중복 선택할 수 없습니다.');
        return;
      }
      keySet.add(key);
    }
    const previous = normalizeRegions(currentPreview.regions || []);
    currentPreview.regions = normalizeRegions(regions);
    const ok = await saveBasicDataChanges(apply, '지역을 저장했습니다.', 'regions');
    if (ok) closeBasicDataModals();
    else currentPreview.regions = previous;
  }

  async function saveBasicDataChanges(button, successMessage, scrollToSection) {
    if (!currentPreview) return false;
    if (!currentPreview.publicPathUrl) {
      persistPendingBasicData(currentPreview);
      populatePreview(currentPreview);
      renderPublicPreview(currentPreview, scrollToSection === 'regions' ? 'sec-hero' : 'sec-hero');
      toast('발행 전 미리보기에 반영했습니다. 발행 시 함께 저장됩니다.', 'fa-check');
      return true;
    }
    const request = academyUpdateRequest(currentPreview);
    if (!request) {
      toast('필수값을 읽지 못해 저장할 수 없습니다. 설정 화면에서 수정해 주세요.', 'fa-triangle-exclamation');
      return false;
    }
    setSaving(button, true);
    try {
      const res = await fetch('/api/tutor/academy', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(request)
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      applyAcademyToLivePreview(currentPreview, 'sec-hero');
      toast(successMessage, 'fa-check');
      return true;
    } catch (_) {
      toast('기본 데이터 저장에 실패했습니다.', 'fa-triangle-exclamation');
      return false;
    } finally {
      setSaving(button, false);
    }
  }

  function persistPendingBasicData(preview) {
    const regions = (preview.regions || []).map((r) => ({ city: r.city, district: r.district && r.district !== 'ALL' ? r.district : null }));
    const subjects = Array.isArray(preview.subjects) ? preview.subjects : [];
    const request = readJson(PENDING_KEY);
    if (request) {
      request.subjectIds = subjects.map((s) => Number(s.subjectId)).filter(Boolean);
      request.subjects = subjects;
      request.targetGroups = Array.from(preview.targetGroups || []);
      request.regions = regions;
      writeJson(PENDING_KEY, request);
    }
    const storedPreview = readJson(PENDING_PREVIEW_KEY) || preview;
    storedPreview.subjects = subjects;
    storedPreview.targetGroups = Array.from(preview.targetGroups || []);
    storedPreview.regions = normalizeRegions(regions);
    writeJson(PENDING_PREVIEW_KEY, storedPreview);
  }

  function closeBasicDataModals() {
    ['builderTargetGroupsModal', 'builderSubjectsModal', 'builderRegionsModal'].forEach((id) => {
      const modal = document.getElementById(id);
      if (!modal) return;
      modal.classList.remove('is-open');
      modal.setAttribute('aria-hidden', 'true');
    });
    clearBasicDataError('builderTargetGroupsError');
    clearBasicDataError('builderSubjectsError');
    clearBasicDataError('builderRegionsError');
  }

  function setBasicDataError(id, message) {
    const error = document.getElementById(id);
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearBasicDataError(id) {
    const error = document.getElementById(id);
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }


  function wireLogoEditing(preview) {
    const pageContent = document.getElementById('pageContent');
    if (pageContent) {
      // 편집 모드: 편집 칩 클릭 시에만 로고 수정 모달 열기 (홈 이동과 명확히 구분)
      pageContent.addEventListener('click', (event) => {
        const editBtn = event.target && event.target.closest ? event.target.closest('[data-builder-logo-edit]') : null;
        if (!editBtn) return;
        event.preventDefault();
        event.stopPropagation();
        openLogoModal();
      });
      pageContent.addEventListener('keydown', (event) => {
        const editBtn = event.target && event.target.closest ? event.target.closest('[data-builder-logo-edit]') : null;
        if (!editBtn || (event.key !== 'Enter' && event.key !== ' ')) return;
        event.preventDefault();
        openLogoModal();
      });
      // 로고 홈 링크는 기본 동작 유지 (홈 이동) — preventDefault 하지 않음
    }
    wireLogoModal();
  }

  function wireLogoModal() {
    const modal = document.getElementById('builderLogoModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-logo-close]').forEach((button) => button.addEventListener('click', closeLogoModal));
    modal.addEventListener('click', (event) => { if (event.target === modal) closeLogoModal(); });
    const apply = document.getElementById('builderLogoApplyBtn');
    if (apply) apply.addEventListener('click', applyLogoFromModal);
  }

  function openLogoModal() {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 로고를 수정할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const modal = document.getElementById('builderLogoModal');
    const input = document.getElementById('builderLogoInput');
    if (!modal || !input) return;
    input.value = '';
    clearLogoError();
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(() => input.focus(), 30);
  }

  function closeLogoModal() {
    const modal = document.getElementById('builderLogoModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    clearLogoError();
  }

  async function applyLogoFromModal() {
    const input = document.getElementById('builderLogoInput');
    const apply = document.getElementById('builderLogoApplyBtn');
    const file = input && input.files && input.files[0];
    if (!file) {
      setLogoError('업로드할 로고 이미지를 선택해 주세요.');
      return;
    }
    const validation = validateLogoFile(file);
    if (validation) {
      setLogoError(validation);
      return;
    }
    if (!currentPreview.publicPathUrl) {
      setSaving(apply, true);
      try {
        const draft = await logoFileToDraft(file);
        pendingLogoFile = file;
        writeJson(PENDING_LOGO_KEY, draft);
        currentPreview.logoUrl = draft.dataUrl;
        currentPreview.logoVersion = Date.now();
        const storedPreview = readJson(PENDING_PREVIEW_KEY) || currentPreview;
        storedPreview.logoUrl = draft.dataUrl;
        storedPreview.logoVersion = currentPreview.logoVersion;
        writeJson(PENDING_PREVIEW_KEY, storedPreview);
        applyAcademyToLivePreview(currentPreview, 'sec-hero');
        closeLogoModal();
        toast('로고 이미지를 미리보기에 반영했습니다. 발행 시 함께 저장됩니다.', 'fa-image');
      } catch (_) {
        setLogoError('로고 이미지를 읽지 못했습니다. 다른 파일로 다시 시도해 주세요.');
      } finally {
        setSaving(apply, false);
      }
      return;
    }
    const request = academyUpdateRequest(currentPreview);
    if (!request) {
      setLogoError('필수값을 읽지 못해 저장할 수 없습니다.');
      return;
    }
    const form = new FormData();
    form.append('academy', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    form.append('logo', file);
    setSaving(apply, true);
    try {
      const res = await fetch('/api/tutor/academy', {
        method: 'PUT',
        credentials: 'same-origin',
        body: form
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      currentPreview.logoVersion = Date.now();
      applyAcademyToLivePreview(currentPreview, 'sec-hero');
      closeLogoModal();
      toast('로고 이미지를 저장했습니다.', 'fa-image');
    } catch (_) {
      setLogoError('로고 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSaving(apply, false);
    }
  }

  function validateLogoFile(file) {
    const allowed = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
    if (!allowed.includes(file.type)) return 'JPG, PNG, WebP, GIF 이미지만 업로드할 수 있습니다.';
    if (file.size > 2 * 1024 * 1024) return '로고 이미지는 2MB 이하로 업로드해 주세요.';
    return null;
  }

  function logoFileToDraft(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve({ name: file.name || 'logo', type: file.type, dataUrl: reader.result });
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  function setLogoError(message) {
    const error = document.getElementById('builderLogoError');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearLogoError() {
    const error = document.getElementById('builderLogoError');
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }

  /**
   * 히어로 이미지 슬롯(원장사진/교재표지) 편집 배선.
   *
   * <p>영상처럼 모달을 띄우지 않는다 — 고를 것이 파일 하나뿐이라 OS 파일 선택창이면 충분하다.
   * 이미 이미지가 있는 슬롯은 교체/삭제를 물어보고, 빈 슬롯은 바로 파일 선택으로 간다.
   */
  function wireHeroMediaEditing() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent || pageContent.dataset.heroMediaWired === 'true') return;
    pageContent.dataset.heroMediaWired = 'true';

    const openSlot = (target) => {
      const slot = target.getAttribute('data-hero-media-edit');
      if (!slot) return;
      if (!currentPreview) {
        toast('랜딩에서 학원 정보를 입력한 뒤 이미지를 등록할 수 있습니다.', 'fa-circle-info');
        return;
      }
      const filled = (currentPreview.heroMediaSlots || []).indexOf(slot) !== -1;
      if (filled && confirm('등록된 이미지를 삭제할까요?\n취소를 누르면 새 이미지로 교체합니다.')) {
        removeHeroMedia(slot);
        return;
      }
      pickHeroMediaFile(slot);
    };

    pageContent.addEventListener('click', (event) => {
      const target = event.target && event.target.closest
        ? event.target.closest('[data-hero-media-edit]') : null;
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      openSlot(target);
    });
    pageContent.addEventListener('keydown', (event) => {
      const target = event.target && event.target.closest
        ? event.target.closest('[data-hero-media-edit]') : null;
      if (!target || (event.key !== 'Enter' && event.key !== ' ')) return;
      event.preventDefault();
      openSlot(target);
    });
  }

  function pickHeroMediaFile(slot) {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      if (file) uploadHeroMedia(slot, file);
    });
    input.click();
  }

  async function uploadHeroMedia(slot, file) {
    // 발행 전에는 Academy row가 없어 업로드 API가 404다. 로고와 같이 미뤄 두고 발행 때 올린다.
    if (!currentPreview || !currentPreview.publicPathUrl) {
      stashHeroMedia(slot, file);
      afterHeroMediaChange(slot, true);
      toast('이미지를 미리보기에 반영했습니다. 발행 시 함께 저장됩니다.', 'fa-image');
      return;
    }
    try {
      const res = await fetch(`/api/tutor/academy/media/hero/${slot}`, {
        method: 'POST',
        credentials: 'same-origin',
        body: heroMediaForm(file)
      });
      if (!res.ok) throw new Error(await res.text());
      afterHeroMediaChange(slot, true);
      toast('이미지를 등록했습니다.', 'fa-circle-check');
    } catch (err) {
      toast(heroMediaErrorMessage(err, '이미지 등록에 실패했습니다.'), 'fa-triangle-exclamation');
    }
  }

  async function removeHeroMedia(slot) {
    // 아직 서버에 올라가지 않은 이미지는 로컬에서만 지우면 된다.
    if (pendingHeroMedia[slot]) {
      dropPendingHeroMedia(slot);
      afterHeroMediaChange(slot, false);
      toast('이미지를 삭제했습니다.', 'fa-circle-check');
      return;
    }
    try {
      const res = await fetch(`/api/tutor/academy/media/hero/${slot}`, {
        method: 'DELETE',
        credentials: 'same-origin'
      });
      if (!res.ok) throw new Error(await res.text());
      afterHeroMediaChange(slot, false);
      toast('이미지를 삭제했습니다.', 'fa-circle-check');
    } catch (err) {
      toast(heroMediaErrorMessage(err, '이미지 삭제에 실패했습니다.'), 'fa-triangle-exclamation');
    }
  }

  function heroMediaForm(file) {
    const form = new FormData();
    form.append('image', file, file.name || 'hero-image');
    return form;
  }

  /** 서버가 본문 없이 상태코드만 주는 경우가 있어(예: 404) 빈 메시지가 그대로 토스트에 뜨는 걸 막는다. */
  function heroMediaErrorMessage(err, fallback) {
    const message = err && typeof err.message === 'string' ? err.message.trim() : '';
    return message || fallback;
  }

  function stashHeroMedia(slot, file) {
    dropPendingHeroMedia(slot);
    pendingHeroMedia[slot] = { file, objectUrl: URL.createObjectURL(file) };
  }

  function dropPendingHeroMedia(slot) {
    const pending = pendingHeroMedia[slot];
    if (!pending) return;
    try { URL.revokeObjectURL(pending.objectUrl); } catch (_) {}
    delete pendingHeroMedia[slot];
  }

  /**
   * 발행 직후, 미뤄 둔 히어로 이미지를 올린다.
   *
   * <p>발행 응답이 성공한 뒤에야 Academy row가 생기므로 이 시점이 가장 이르다. 일부가 실패해도
   * 발행 자체는 이미 끝났으므로 사용자에게 알리기만 하고 발행 플로우를 되돌리지 않는다.
   */
  async function flushPendingHeroMedia() {
    const slots = Object.keys(pendingHeroMedia);
    if (!slots.length) return;
    const failed = [];
    for (const slot of slots) {
      try {
        const res = await fetch(`/api/tutor/academy/media/hero/${slot}`, {
          method: 'POST',
          credentials: 'same-origin',
          body: heroMediaForm(pendingHeroMedia[slot].file)
        });
        if (!res.ok) throw new Error(await res.text());
      } catch (_) {
        failed.push(slot);
      }
    }
    slots.forEach(dropPendingHeroMedia);
    if (failed.length) {
      toast(`이미지 ${failed.length}건을 저장하지 못했습니다. 학원 관리에서 다시 등록해 주세요.`, 'fa-triangle-exclamation');
    }
  }

  /**
   * 서버 상태를 로컬 preview에 반영하고 캔버스를 다시 그린다. v 증가로 브라우저 캐시를 끊는다.
   *
   * <p>스크롤 대상은 슬롯이 실제로 그려지는 섹션이어야 한다. 시간표 달력(V948)은 히어로가 아니라
   * sec-lessons 하단에 있어 'sec-hero'로 보내면 방금 올린 이미지가 화면 밖으로 밀린다.
   */
  function afterHeroMediaChange(slot, filled) {
    if (!currentPreview) return;
    const slots = (currentPreview.heroMediaSlots || []).filter((s) => s !== slot);
    if (filled) slots.push(slot);
    currentPreview.heroMediaSlots = slots;
    heroMediaVersion += 1;
    renderPublicPreview(currentPreview, slot === 'schedule' ? 'sec-lessons' : 'sec-hero');
  }

  /** 히어로 대표 수업은 전체 수업과 자동 동기화하지 않고 원장이 고른 최대 4개만 저장한다. */
  function wireFeaturedLessonsEditing() {
    const pageContent = document.getElementById('pageContent');
    if (pageContent && pageContent.dataset.featuredLessonsWired !== 'true') {
      pageContent.dataset.featuredLessonsWired = 'true';
      pageContent.addEventListener('click', (event) => {
        const panel = event.target && event.target.closest ? event.target.closest('[data-featured-lessons-edit]') : null;
        if (!panel) return;
        event.preventDefault();
        event.stopPropagation();
        openFeaturedLessonsModal();
      }, true);
      pageContent.addEventListener('keydown', (event) => {
        const panel = event.target && event.target.closest ? event.target.closest('[data-featured-lessons-edit]') : null;
        if (!panel || (event.key !== 'Enter' && event.key !== ' ')) return;
        event.preventDefault();
        openFeaturedLessonsModal();
      });
    }

    const modal = document.getElementById('builderFeaturedLessonsModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-featured-lessons-close]').forEach((button) => {
      button.addEventListener('click', closeFeaturedLessonsModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeFeaturedLessonsModal();
    });
    const apply = document.getElementById('builderFeaturedLessonsApplyBtn');
    if (apply) apply.addEventListener('click', applyFeaturedLessonsFromModal);
  }

  function openFeaturedLessonsModal() {
    const modal = document.getElementById('builderFeaturedLessonsModal');
    const options = document.getElementById('builderFeaturedLessonsOptions');
    if (!modal || !options || !currentPreview) return;
    const selected = new Set(SECTIONS.featuredLessonIds(currentPreview));
    const lessons = (currentPreview.lessons || []).filter((lesson) => lesson.lessonId != null);
    options.innerHTML = lessons.length
      ? lessons.map((lesson) => `<label class="builder-option-check"><input type="checkbox" value="${escapeHtml(lesson.lessonId)}" ${selected.has(String(lesson.lessonId)) ? 'checked' : ''}> <span>${escapeHtml(SECTIONS.lessonPanelTitle(lesson))}</span></label>`).join('')
      : '<p class="lesson-empty">먼저 수업을 등록해 주세요.</p>';
    clearFeaturedLessonsError();
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
  }

  function closeFeaturedLessonsModal() {
    const modal = document.getElementById('builderFeaturedLessonsModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    clearFeaturedLessonsError();
  }

  async function applyFeaturedLessonsFromModal() {
    const apply = document.getElementById('builderFeaturedLessonsApplyBtn');
    const ids = Array.from(document.querySelectorAll('#builderFeaturedLessonsOptions input:checked'))
      .map((input) => input.value);
    if (!ids.length) {
      setFeaturedLessonsError('대표 수업을 1개 이상 선택해 주세요.');
      return;
    }
    if (ids.length > 4) {
      setFeaturedLessonsError('대표 수업은 최대 4개까지 선택할 수 있습니다.');
      return;
    }

    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    const previousDraft = Object.assign({}, pageSettings.builderDraft || {});
    const nextDraft = Object.assign({}, previousDraft, { featuredLessonIds: ids.join(',') });
    pageSettings.builderDraft = nextDraft;
    currentPreview.pageSettings = pageSettings;

    if (!currentPreview.publicPathUrl) {
      persistPendingTextChanges(currentPreview);
      renderPublicPreview(currentPreview, 'sec-hero');
      closeFeaturedLessonsModal();
      toast('발행 전 미리보기에 대표 수업을 반영했습니다.', 'fa-list-check');
      return;
    }

    const request = academyUpdateRequest(currentPreview);
    if (!request) {
      currentPreview.pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { builderDraft: previousDraft });
      setFeaturedLessonsError('기존 Academy 필수값을 읽지 못해 저장할 수 없습니다.');
      return;
    }
    setSaving(apply, true);
    try {
      const res = await fetch('/api/tutor/academy', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(request)
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      applyAcademyToLivePreview(currentPreview, 'sec-hero');
      closeFeaturedLessonsModal();
      toast('대표 수업을 저장했습니다.', 'fa-list-check');
    } catch (_) {
      currentPreview.pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { builderDraft: previousDraft });
      setFeaturedLessonsError('대표 수업 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSaving(apply, false);
    }
  }

  function setFeaturedLessonsError(message) {
    const error = document.getElementById('builderFeaturedLessonsError');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearFeaturedLessonsError() {
    const error = document.getElementById('builderFeaturedLessonsError');
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }

  function wireHeroVideoEditing(preview) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    pageContent.addEventListener('click', (event) => {
      const target = event.target && event.target.closest ? event.target.closest('[data-hero-video-edit]') : null;
      if (!target) return;
      event.preventDefault();
      openHeroVideoModal();
    });
    pageContent.addEventListener('keydown', (event) => {
      const target = event.target && event.target.closest ? event.target.closest('[data-hero-video-edit]') : null;
      if (!target || (event.key !== 'Enter' && event.key !== ' ')) return;
      event.preventDefault();
      openHeroVideoModal();
    });
    wireHeroVideoModal();
  }

  function wireHeroVideoModal() {
    const modal = document.getElementById('builderHeroVideoModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-hero-video-close]').forEach((button) => {
      button.addEventListener('click', closeHeroVideoModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeHeroVideoModal();
    });
    const input = document.getElementById('builderHeroVideoInput');
    if (input) {
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') closeHeroVideoModal();
        if (event.key === 'Enter') {
          event.preventDefault();
          applyHeroVideoFromModal();
        }
      });
    }
    const apply = document.getElementById('builderHeroVideoApplyBtn');
    if (apply) apply.addEventListener('click', applyHeroVideoFromModal);
    wireHomeVideoList('hero');
  }

  function openHeroVideoModal() {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 영상을 편집할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const modal = document.getElementById('builderHeroVideoModal');
    const input = document.getElementById('builderHeroVideoInput');
    if (!modal || !input) return;
    input.value = (currentPreview.pageSettings && currentPreview.pageSettings.heroYoutubeUrl) || '';
    renderHomeVideoList('hero', draftText(currentPreview, 'heroVideoId', ''));
    clearHeroVideoError();
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(() => input.focus(), 30);
  }

  function closeHeroVideoModal() {
    const modal = document.getElementById('builderHeroVideoModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    clearHeroVideoError();
  }

  function applyHeroVideoFromModal() {
    const input = document.getElementById('builderHeroVideoInput');
    if (!input || !currentPreview) return;
    const rawUrl = input.value.trim();
    if (!rawUrl) {
      setHeroVideoError('YouTube 링크를 입력해 주세요.');
      return;
    }
    const normalized = normalizedYoutubeUrl(rawUrl);
    if (!normalized) {
      setHeroVideoError('YouTube watch/youtu.be/embed 링크만 사용할 수 있습니다.');
      return;
    }
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { heroYoutubeUrl: normalized });
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, { heroVideoId: '' });
    currentPreview.pageSettings = pageSettings;
    stageHomepageVideoChange('sec-hero');
    closeHeroVideoModal();
    toast(currentPreview.publicPathUrl
      ? 'YouTube 영상을 반영했습니다. 상단 발행 버튼으로 저장하세요.'
      : '발행 전 미리보기에 YouTube 영상을 반영했습니다.', 'fa-video');
  }

  function setHeroVideoError(message) {
    const error = document.getElementById('builderHeroVideoError');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearHeroVideoError() {
    const error = document.getElementById('builderHeroVideoError');
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }

  // ── 원장 소개 영상 편집 ── 히어로 영상과 같은 학원 영상 보관함을 공유하며,
  // 업로드 영상 ID와 YouTube URL은 서로 배타적으로 저장한다.
  function wirePrincipalVideoEditing(preview) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    pageContent.addEventListener('click', (event) => {
      const target = event.target && event.target.closest ? event.target.closest('[data-principal-video-edit]') : null;
      if (!target) return;
      event.preventDefault();
      openPrincipalVideoModal();
    });
    pageContent.addEventListener('keydown', (event) => {
      const target = event.target && event.target.closest ? event.target.closest('[data-principal-video-edit]') : null;
      if (!target || (event.key !== 'Enter' && event.key !== ' ')) return;
      event.preventDefault();
      openPrincipalVideoModal();
    });
    wirePrincipalVideoModal();
  }

  function wirePrincipalVideoModal() {
    const modal = document.getElementById('builderPrincipalVideoModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-principal-video-close]').forEach((button) => {
      button.addEventListener('click', closePrincipalVideoModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closePrincipalVideoModal();
    });
    const input = document.getElementById('builderPrincipalVideoInput');
    if (input) {
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') closePrincipalVideoModal();
        if (event.key === 'Enter') {
          event.preventDefault();
          applyPrincipalVideoFromModal();
        }
      });
    }
    const apply = document.getElementById('builderPrincipalVideoApplyBtn');
    if (apply) apply.addEventListener('click', applyPrincipalVideoFromModal);
    wireHomeVideoList('principal');
    document.querySelectorAll('#builderPrincipalVideoOrient [data-principal-video-orientation]').forEach((button) => {
      button.addEventListener('click', () => setPrincipalVideoOrientationUI(button.dataset.principalVideoOrientation));
    });
  }

  // 가로/세로 토글 UI: 선택된 버튼만 강조한다(data-active로 상태 추적).
  function setPrincipalVideoOrientationUI(orientation) {
    const value = orientation === 'portrait' ? 'portrait' : 'landscape';
    document.querySelectorAll('#builderPrincipalVideoOrient [data-principal-video-orientation]').forEach((button) => {
      const active = button.dataset.principalVideoOrientation === value;
      button.dataset.active = active ? 'true' : 'false';
      button.style.borderColor = active ? '#4A7FD4' : '#dbe5ff';
      button.style.background = active ? 'rgba(74,127,212,.12)' : '#fbfdff';
      button.style.color = active ? '#0C238A' : '#334155';
    });
  }

  function selectedPrincipalVideoOrientation() {
    const active = document.querySelector('#builderPrincipalVideoOrient [data-principal-video-orientation][data-active="true"]');
    return active ? active.dataset.principalVideoOrientation : 'landscape';
  }

  function openPrincipalVideoModal() {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 영상을 편집할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const modal = document.getElementById('builderPrincipalVideoModal');
    const input = document.getElementById('builderPrincipalVideoInput');
    if (!modal || !input) return;
    input.value = draftText(currentPreview, 'principalYoutubeUrl', '');
    renderHomeVideoList('principal', draftText(currentPreview, 'principalVideoId', ''));
    setPrincipalVideoOrientationUI(draftText(currentPreview, 'principalVideoOrientation', 'landscape'));
    clearPrincipalVideoError();
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(() => input.focus(), 30);
  }

  function closePrincipalVideoModal() {
    const modal = document.getElementById('builderPrincipalVideoModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    clearPrincipalVideoError();
  }

  function applyPrincipalVideoFromModal() {
    const input = document.getElementById('builderPrincipalVideoInput');
    if (!input || !currentPreview) return;
    const rawUrl = input.value.trim();
    if (!rawUrl) {
      setPrincipalVideoError('YouTube 링크를 입력해 주세요.');
      return;
    }
    const normalized = normalizedYoutubeUrl(rawUrl);
    if (!normalized) {
      setPrincipalVideoError('YouTube watch/youtu.be/embed 링크만 사용할 수 있습니다.');
      return;
    }
    // 영상 링크는 builderDraft.principalYoutubeUrl에 저장한다. page-settings 엔드포인트는 builderDraft를
    // 받지 않으므로(기존 값 보존만), 원장 텍스트/약력과 동일하게 dirty→발행(기존) / pending(신규 발행) 경로로 저장한다.
    // orientation은 모달의 가로/세로 토글에서 사용자가 직접 고른 값을 저장한다.
    const orientation = selectedPrincipalVideoOrientation();
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, {
      principalVideoId: '',
      principalYoutubeUrl: normalized,
      principalVideoOrientation: orientation
    });
    currentPreview.pageSettings = pageSettings;
    stageHomepageVideoChange('sec-principal');
    closePrincipalVideoModal();
    toast(currentPreview.publicPathUrl
      ? '원장 영상 링크를 반영했습니다. 상단 발행 버튼으로 저장하세요.'
      : '발행 전 미리보기에 원장 영상 링크를 반영했습니다.', 'fa-video');
  }

  function wireHomeVideoList(kind) {
    const list = document.querySelector(`[data-home-video-list="${kind}"]`);
    if (!list || list.dataset.wired === 'true') return;
    list.dataset.wired = 'true';
    list.addEventListener('click', (event) => {
      const button = event.target && event.target.closest ? event.target.closest('[data-home-video-id]') : null;
      if (!button || !list.contains(button)) return;
      selectUploadedHomepageVideo(kind, Number(button.dataset.homeVideoId));
    });
    const clear = document.querySelector(`[data-home-video-clear="${kind}"]`);
    if (clear) clear.addEventListener('click', () => clearHomepageVideo(kind));
  }

  function renderHomeVideoList(kind, selectedId) {
    const list = document.querySelector(`[data-home-video-list="${kind}"]`);
    if (!list) return;
    // hero(학원 소개)와 principal(원장 소개)은 둘 다 "원장/학원 브랜딩" 용도라 DIRECTOR로 묶는다.
    const videos = videosForPurpose('DIRECTOR');
    if (!videos.length) {
      list.innerHTML = '<p class="builder-home-video-empty">업로드된 영상이 없습니다. 영상 관리에서 대표 원장 소개 영상을 먼저 업로드해 주세요.</p>';
      return;
    }
    list.innerHTML = videos.map((video) => {
      const selected = String(video.videoId) === String(selectedId || '');
      const preview = video.thumbnailUrl
        ? `<img src="${escapeAttr(video.thumbnailUrl)}" alt="" loading="lazy">`
        : `<video src="${escapeAttr(video.videoUrl || '')}" preload="metadata" muted playsinline></video>`;
      return `<button type="button" class="builder-home-video-item${selected ? ' is-selected' : ''}" data-home-video-id="${escapeAttr(video.videoId)}">${preview}<span>${escapeHtml(video.originalFilename || `영상 ${video.videoId}`)}</span></button>`;
    }).join('');
  }

  function selectUploadedHomepageVideo(kind, videoId) {
    if (!currentPreview || !findUploadedVideo(videoId)) return;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    if (kind === 'hero') {
      pageSettings.heroYoutubeUrl = null;
      pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, {
        heroVideoId: String(videoId)
      });
    } else {
      const video = findUploadedVideo(videoId);
      const orientation = Number(video && video.height) > Number(video && video.width) ? 'portrait' : 'landscape';
      pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, {
        principalVideoId: String(videoId),
        principalYoutubeUrl: '',
        principalVideoOrientation: orientation
      });
    }
    currentPreview.pageSettings = pageSettings;
    stageHomepageVideoChange(kind === 'hero' ? 'sec-hero' : 'sec-principal');
    if (kind === 'hero') closeHeroVideoModal();
    else closePrincipalVideoModal();
    toast(currentPreview.publicPathUrl
      ? '업로드 영상을 반영했습니다. 상단 발행 버튼으로 저장하세요.'
      : '발행 전 미리보기에 업로드 영상을 반영했습니다.', 'fa-video');
  }

  function clearHomepageVideo(kind) {
    if (!currentPreview) return;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    if (kind === 'hero') {
      pageSettings.heroYoutubeUrl = null;
      pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, { heroVideoId: '' });
    } else {
      pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, {
        principalVideoId: '',
        principalYoutubeUrl: ''
      });
    }
    currentPreview.pageSettings = pageSettings;
    stageHomepageVideoChange(kind === 'hero' ? 'sec-hero' : 'sec-principal');
    if (kind === 'hero') closeHeroVideoModal();
    else closePrincipalVideoModal();
    toast(currentPreview.publicPathUrl
      ? '영상을 제거했습니다. 상단 발행 버튼으로 저장하세요.'
      : '발행 전 미리보기에서 영상을 제거했습니다.', 'fa-video');
  }

  function stageHomepageVideoChange(sectionId) {
    if (currentPreview.publicPathUrl) dirtyTextChanges = true;
    else persistPendingTextChanges(currentPreview);
    renderPublicPreview(currentPreview, sectionId);
  }

  function setPrincipalVideoError(message) {
    const error = document.getElementById('builderPrincipalVideoError');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearPrincipalVideoError() {
    const error = document.getElementById('builderPrincipalVideoError');
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }

  // ── 원장 약력(가변 리스트) 편집 ── 각 약력 줄은 클릭하면 편집되는 span + 삭제 버튼이고, "약력 추가"
  // 버튼으로 줄을 늘린다. 텍스트 값은 섹션 "적용" 시 applySectionTextChanges가 DOM에서 배열로 수집해
  // builderDraft.principalCareer에 커밋한다. 추가/삭제는 즉시 커밋·저장한다.
  function wirePrincipalCareerEditing(preview) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    pageContent.addEventListener('click', (event) => {
      const removeBtn = event.target && event.target.closest ? event.target.closest('[data-principal-career-remove]') : null;
      if (removeBtn && pageContent.contains(removeBtn)) {
        event.preventDefault();
        event.stopPropagation();
        removePrincipalCareerItem(removeBtn.closest('li'));
        return;
      }
      const addBtn = event.target && event.target.closest ? event.target.closest('[data-principal-career-add]') : null;
      if (addBtn && pageContent.contains(addBtn)) {
        event.preventDefault();
        event.stopPropagation();
        addPrincipalCareerItem(addBtn);
        return;
      }
      const item = event.target && event.target.closest ? event.target.closest('[data-principal-career-item]') : null;
      if (item && pageContent.contains(item)) {
        event.preventDefault();
        event.stopPropagation();
        editPrincipalCareerItem(item);
      }
    });
  }

  function editPrincipalCareerItem(item) {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 약력을 편집할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const section = item.closest('[data-builder-section-key]');
    if (section && !enterSectionTextEditMode(section)) return;
    item.setAttribute('contenteditable', 'true');
    item.focus();
    selectEditableText(item);
  }

  function addPrincipalCareerItem(button) {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 약력을 추가할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const info = button.closest('.principal-info');
    const list = info && info.querySelector('[data-principal-career-list]');
    if (!list) return;
    const section = button.closest('[data-builder-section-key]');
    if (section && !enterSectionTextEditMode(section)) return;
    list.insertAdjacentHTML('beforeend', BUILDER_CTX.principalCareerItem('새 약력'));
    const span = list.querySelector('li:last-child [data-principal-career-item]');
    if (span) {
      span.setAttribute('contenteditable', 'true');
      span.focus();
      selectEditableText(span);
    }
  }

  function removePrincipalCareerItem(li) {
    if (!li || !currentPreview) return;
    const section = li.closest('[data-builder-section-key]');
    li.remove();
    if (section && commitPrincipalCareer(section)) {
      populatePreview(currentPreview);
      persistVariableListChange();
      toast('약력을 삭제했습니다.', 'fa-trash');
    }
  }

  function collectPrincipalCareer(section) {
    return Array.from(section.querySelectorAll('[data-principal-career-item]'))
      .map((el) => TEXT_UTILS.normalizeSingleLineText(TEXT_UTILS.extractEditableText(el)))
      .filter(Boolean);
  }

  function commitPrincipalCareer(section) {
    if (!currentPreview || !section) return false;
    const list = collectPrincipalCareer(section);
    const previous = SECTIONS.principalCareerList(currentPreview);
    if (list.length === previous.length && list.every((v, i) => v === previous[i])) return false;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, { principalCareer: list.join('\n') });
    currentPreview.pageSettings = pageSettings;
    return true;
  }

  function persistVariableListChange() {
    if (currentPreview.publicPathUrl) {
      dirtyTextChanges = true;
      return;
    }
    persistPendingTextChanges(currentPreview);
  }

  // ── 학원 특징(가변 리스트) 편집 ── 원장 약력과 같은 패턴이다. 각 줄은 클릭하면 편집되는 span +
  // 삭제 버튼이고 "특징 추가"로 줄을 늘린다. 텍스트 값은 섹션 "적용" 시 applySectionTextChanges가
  // DOM에서 배열로 수집해 builderDraft.introFeatures에 커밋한다. 추가/삭제는 즉시 커밋·저장한다.
  function wireIntroFeatureEditing(preview) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    pageContent.addEventListener('click', (event) => {
      const removeBtn = event.target && event.target.closest ? event.target.closest('[data-intro-feature-remove]') : null;
      if (removeBtn && pageContent.contains(removeBtn)) {
        event.preventDefault();
        event.stopPropagation();
        removeIntroFeatureItem(removeBtn.closest('.intro-feature-item'));
        return;
      }
      const addBtn = event.target && event.target.closest ? event.target.closest('[data-intro-feature-add]') : null;
      if (addBtn && pageContent.contains(addBtn)) {
        event.preventDefault();
        event.stopPropagation();
        addIntroFeatureItem(addBtn);
        return;
      }
      // 제목(h3)과 본문(p)이 각각 독립적인 편집 대상이다.
      const item = event.target && event.target.closest
        ? event.target.closest('[data-intro-feature-item],[data-intro-feature-body]') : null;
      if (item && pageContent.contains(item)) {
        event.preventDefault();
        event.stopPropagation();
        editIntroFeatureItem(item);
      }
    });
  }

  function editIntroFeatureItem(item) {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 특징을 편집할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const section = item.closest('[data-builder-section-key]');
    if (section && !enterSectionTextEditMode(section)) return;
    item.setAttribute('contenteditable', 'true');
    item.focus();
    selectEditableText(item);
  }

  function addIntroFeatureItem(button) {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 특징을 추가할 수 있습니다.', 'fa-circle-info');
      return;
    }
    // V8 시안 교체로 구 요약 래퍼가 사라져 섹션 기준으로 목록을 찾는다.
    const list = button.closest('[data-builder-section-key]');
    const listEl = list && list.querySelector('[data-intro-feature-list]');
    if (!listEl) return;
    const section = button.closest('[data-builder-section-key]');
    if (section && !enterSectionTextEditMode(section)) return;
    listEl.insertAdjacentHTML('beforeend', BUILDER_CTX.introFeatureItem('새 특징'));
    const span = listEl.querySelector('.intro-feature-item:last-child [data-intro-feature-item]');
    if (span) {
      span.setAttribute('contenteditable', 'true');
      span.focus();
      selectEditableText(span);
    }
  }

  function removeIntroFeatureItem(item) {
    if (!item || !currentPreview) return;
    const section = item.closest('[data-builder-section-key]');
    item.remove();
    if (section && commitIntroFeatures(section)) {
      populatePreview(currentPreview);
      persistVariableListChange();
      toast('특징을 삭제했습니다.', 'fa-trash');
    }
  }

  // 한 줄은 "제목|본문" 형식으로 저장된다. 편집값에 '|'가 들어가면 저장/재파싱에서
  // 칸이 밀리므로(noteParts가 3칸이면 첫 칸을 날짜로 읽는다) 구분자만 걷어낸다.
  function notePartText(element) {
    return TEXT_UTILS.normalizeSingleLineText(TEXT_UTILS.extractEditableText(element)).replace(/\|/g, ' ').trim();
  }

  function collectIntroFeatures(section) {
    return Array.from(section.querySelectorAll('.intro-feature-item'))
      .map((card) => {
        const titleEl = card.querySelector('[data-intro-feature-item]');
        const bodyEl = card.querySelector('[data-intro-feature-body]') || card.querySelector('p');
        const title = notePartText(titleEl);
        const body = notePartText(bodyEl);
        if (!title) return '';
        return body ? `${title}|${body}` : title;
      })
      .filter(Boolean);
  }

  function commitIntroFeatures(section) {
    if (!currentPreview || !section) return false;
    const list = collectIntroFeatures(section);
    const previous = SECTIONS.introFeatureList(currentPreview);
    if (list.length === previous.length && list.every((v, i) => v === previous[i])) return false;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, { introFeatures: list.join('\n') });
    currentPreview.pageSettings = pageSettings;
    return true;
  }


  function wirePublicHandleEditing(preview) {
    const url = document.querySelector('.browser-url');
    if (url && url.dataset.publicHandleWired !== 'true') {
      url.dataset.publicHandleWired = 'true';
      url.addEventListener('click', openPublicHandleModal);
      url.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        event.preventDefault();
        openPublicHandleModal();
      });
    }
    wirePublicHandleModal();
  }

  function wirePublicHandleModal() {
    const modal = document.getElementById('builderPublicHandleModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-public-handle-close]').forEach((button) => {
      button.addEventListener('click', closePublicHandleModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closePublicHandleModal();
    });
    const input = document.getElementById('builderPublicHandleInput');
    if (input) {
      input.addEventListener('input', syncPublicHandlePreview);
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') closePublicHandleModal();
        if (event.key === 'Enter') {
          event.preventDefault();
          applyPublicHandleFromModal();
        }
      });
    }
    const check = document.getElementById('builderPublicHandleCheckBtn');
    if (check) check.addEventListener('click', checkPublicHandleAvailabilityFromButton);
    const apply = document.getElementById('builderPublicHandleApplyBtn');
    if (apply) apply.addEventListener('click', applyPublicHandleFromModal);
  }

  function openPublicHandleModal() {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 공개 주소를 수정할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const modal = document.getElementById('builderPublicHandleModal');
    const input = document.getElementById('builderPublicHandleInput');
    if (!modal || !input) return;
    input.value = normalizeHandle(currentPreview.handle) || '';
    clearPublicHandleError();
    syncPublicHandlePreview();
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(() => input.focus(), 30);
  }

  function closePublicHandleModal() {
    const modal = document.getElementById('builderPublicHandleModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    clearPublicHandleError();
    clearPublicHandleStatus();
  }

  function syncPublicHandlePreview() {
    const input = document.getElementById('builderPublicHandleInput');
    const preview = document.getElementById('builderPublicHandlePreview');
    if (!input || !preview) return;
    const handle = normalizeHandle(input.value) || 'handle';
    preview.textContent = handle + '.feel2real.co.kr';
    latestPublicHandleCheck = null;
    if (!input.value.trim()) {
      setPublicHandleStatus('공개 주소를 입력한 뒤 중복확인을 눌러 주세요.', 'muted');
    } else if (!isValidHandle(normalizeHandle(input.value))) {
      setPublicHandleStatus('3~30자 소문자/숫자/하이픈 형식을 확인해 주세요.', 'error');
    } else {
      setPublicHandleStatus('중복확인을 눌러 사용 가능 여부를 확인해 주세요.', 'muted');
    }
  }

  async function applyPublicHandleFromModal() {
    const input = document.getElementById('builderPublicHandleInput');
    const apply = document.getElementById('builderPublicHandleApplyBtn');
    if (!input || !currentPreview) return;
    const handle = normalizeHandle(input.value);
    if (!isValidHandle(handle)) {
      setPublicHandleError('3~30자 소문자/숫자/하이픈으로 입력하고, 시작/끝은 소문자 또는 숫자여야 합니다.');
      return;
    }
    const previous = currentPreview.handle || '';
    if (handle === previous) {
      closePublicHandleModal();
      toast('변경 사항이 없습니다.', 'fa-circle-info');
      return;
    }
    if (latestPublicHandleCheck && latestPublicHandleCheck.handle === handle && latestPublicHandleCheck.available === false) {
      setPublicHandleError('이미 사용 중인 공개 주소입니다.');
      return;
    }
    setSaving(apply, true);
    try {
      const available = await confirmHandleAvailableForSave(handle);
      if (!available) {
        setPublicHandleError('이미 사용 중인 공개 주소입니다.');
        return;
      }
      currentPreview.handle = handle;
      currentPreview.displayUrl = handle + '.feel2real.co.kr';
      populatePreview(currentPreview);
      if (currentPreview.publicPathUrl) {
        dirtyTextChanges = true;
        toast('공개 주소가 미리보기에 반영되었습니다. 상단 발행 버튼으로 저장해 주세요.', 'fa-link');
      } else {
        persistPendingHandle(handle);
        toast('발행 전 공개 주소를 임시 저장했습니다.', 'fa-link');
      }
      closePublicHandleModal();
    } catch (_) {
      setPublicHandleError('공개 주소 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSaving(apply, false);
    }
  }

  async function checkPublicHandleAvailabilityFromButton() {
    const input = document.getElementById('builderPublicHandleInput');
    const button = document.getElementById('builderPublicHandleCheckBtn');
    if (!input) return;
    const handle = normalizeHandle(input.value);
    if (!isValidHandle(handle)) {
      latestPublicHandleCheck = null;
      setPublicHandleStatus('3~30자 소문자/숫자/하이픈 형식을 확인해 주세요.', 'error');
      return;
    }
    if (handle === normalizeHandle(currentPreview && currentPreview.handle)) {
      latestPublicHandleCheck = { handle, available: true };
      setPublicHandleStatus('현재 사용 중인 공개 주소입니다.', 'ok');
      return;
    }
    setSaving(button, true);
    setPublicHandleStatus('중복 확인 중…', 'checking');
    try {
      const shouldCheckAvailability = !!(currentPreview && currentPreview.publicPathUrl) || !!(await getUser());
      if (!shouldCheckAvailability) {
        latestPublicHandleCheck = { handle, available: null };
        setPublicHandleStatus('로그인 후 발행 시 서버에서 중복 확인합니다.', 'muted');
        return;
      }
      const available = await isHandleAvailable(handle);
      latestPublicHandleCheck = { handle, available };
      setPublicHandleStatus(available ? '사용 가능한 공개 주소입니다.' : '이미 사용 중인 공개 주소입니다.', available ? 'ok' : 'error');
    } catch (_) {
      latestPublicHandleCheck = { handle, available: null };
      setPublicHandleStatus('중복 확인에 실패했습니다. 잠시 후 다시 눌러 주세요.', 'error');
    } finally {
      setSaving(button, false);
    }
  }

  async function confirmHandleAvailableForSave(handle) {
    const shouldCheckAvailability = !!currentPreview.publicPathUrl || !!(await getUser());
    if (!shouldCheckAvailability) return true;
    if (latestPublicHandleCheck && latestPublicHandleCheck.handle === handle && latestPublicHandleCheck.available === true) return true;
    return isHandleAvailable(handle);
  }

  async function isHandleAvailable(handle) {
    const res = await fetch('/api/tutor/academy/handle-available?handle=' + encodeURIComponent(handle), { credentials: 'same-origin' });
    if (!res.ok) throw new Error('handle check failed');
    const body = await res.json();
    return !!body.available;
  }

  function persistPendingHandle(handle) {
    const request = readJson(PENDING_KEY);
    if (request) {
      request.handle = handle;
      writeJson(PENDING_KEY, request);
    }
    const preview = readJson(PENDING_PREVIEW_KEY) || currentPreview;
    if (preview) {
      preview.handle = handle;
      preview.displayUrl = handle + '.feel2real.co.kr';
      writeJson(PENDING_PREVIEW_KEY, preview);
    }
  }

  function normalizeHandle(value) {
    return String(value || '').trim().toLowerCase();
  }

  function isValidHandle(handle) {
    return /^[a-z0-9](?:[a-z0-9-]{1,28}[a-z0-9])?$/.test(String(handle || ''));
  }

  function setPublicHandleError(message) {
    const error = document.getElementById('builderPublicHandleError');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearPublicHandleError() {
    const error = document.getElementById('builderPublicHandleError');
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }

  function setPublicHandleStatus(message, tone) {
    const status = document.getElementById('builderPublicHandleStatus');
    if (!status) return;
    status.textContent = message;
    status.dataset.tone = tone || 'muted';
    status.hidden = false;
  }

  function clearPublicHandleStatus() {
    latestPublicHandleCheck = null;
    const status = document.getElementById('builderPublicHandleStatus');
    if (!status) return;
    status.textContent = '';
    status.hidden = true;
    status.dataset.tone = 'muted';
  }

  function wireSocialUrlEditing(preview) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    pageContent.addEventListener('click', (event) => {
      const target = event.target && event.target.closest ? event.target.closest('[data-social-url-edit]') : null;
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      openSocialUrlModal(target.dataset.socialUrlEdit);
    });
    pageContent.addEventListener('keydown', (event) => {
      const target = event.target && event.target.closest ? event.target.closest('[data-social-url-edit]') : null;
      if (!target || (event.key !== 'Enter' && event.key !== ' ')) return;
      event.preventDefault();
      event.stopPropagation();
      openSocialUrlModal(target.dataset.socialUrlEdit);
    });
    wireSocialUrlModal();
  }

  function wireSocialUrlModal() {
    const modal = document.getElementById('builderSocialUrlModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-social-url-close]').forEach((button) => {
      button.addEventListener('click', closeSocialUrlModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeSocialUrlModal();
    });
    const input = document.getElementById('builderSocialUrlInput');
    if (input) {
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') closeSocialUrlModal();
        if (event.key === 'Enter') {
          event.preventDefault();
          applySocialUrlFromModal();
        }
      });
    }
    const apply = document.getElementById('builderSocialUrlApplyBtn');
    if (apply) apply.addEventListener('click', applySocialUrlFromModal);
  }

  function socialUrlMeta(field) {
    if (field === 'blogUrl') return { field, title: '네이버 블로그 URL' };
    if (field === 'naverMapUrl') return { field, title: '네이버 지도 URL' };
    return null;
  }

  function openSocialUrlModal(field) {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 URL을 편집할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const meta = socialUrlMeta(field);
    const modal = document.getElementById('builderSocialUrlModal');
    const input = document.getElementById('builderSocialUrlInput');
    if (!meta || !modal || !input) return;
    const title = document.getElementById('builderSocialUrlTitle');
    const label = document.getElementById('builderSocialUrlLabel');
    const note = document.getElementById('builderSocialUrlNoteText');
    if (title) title.textContent = meta.title + ' 수정';
    if (label) label.textContent = meta.title;
    if (note) note.textContent = 'http:// 또는 https://로 시작하는 URL 하나만 등록할 수 있습니다.';
    modal.dataset.socialUrlField = meta.field;
    const settings = currentPreview.pageSettings || {};
    input.value = settings[meta.field] || '';
    clearSocialUrlError();
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(() => input.focus(), 30);
  }

  function closeSocialUrlModal() {
    const modal = document.getElementById('builderSocialUrlModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    delete modal.dataset.socialUrlField;
    clearSocialUrlError();
  }

  async function applySocialUrlFromModal() {
    const modal = document.getElementById('builderSocialUrlModal');
    const input = document.getElementById('builderSocialUrlInput');
    const apply = document.getElementById('builderSocialUrlApplyBtn');
    const field = modal && modal.dataset.socialUrlField;
    if (!input || !currentPreview || !socialUrlMeta(field)) return;
    const rawUrl = input.value.trim();
    if (!rawUrl) {
      setSocialUrlError('URL을 입력해 주세요.');
      return;
    }
    const normalized = safeUrl(rawUrl);
    if (!normalized) {
      setSocialUrlError('http:// 또는 https://로 시작하는 URL을 입력해 주세요.');
      return;
    }
    const previousSettings = Object.assign({}, currentPreview.pageSettings || {});
    currentPreview.pageSettings = Object.assign({}, previousSettings, { [field]: normalized });

    if (!currentPreview.publicPathUrl) {
      persistPendingPageSetting(field, normalized);
      renderPublicPreview(currentPreview, 'sec-social');
      closeSocialUrlModal();
      toast('발행 전 미리보기에 외부 URL을 반영했습니다.', 'fa-link');
      return;
    }

    setSaving(apply, true);
    try {
      const body = {};
      body[field] = normalized;
      const res = await fetch('/api/tutor/academy/page-settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(body)
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      applyAcademyToLivePreview(currentPreview, 'sec-social');
      closeSocialUrlModal();
      toast('외부 URL을 저장했습니다.', 'fa-link');
    } catch (_) {
      currentPreview.pageSettings = previousSettings;
      setSocialUrlError('URL 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSaving(apply, false);
    }
  }

  function persistPendingPageSetting(field, value) {
    const request = readJson(PENDING_KEY);
    if (request) {
      request.pageSettings = Object.assign({}, request.pageSettings || {}, { [field]: value });
      writeJson(PENDING_KEY, request);
    }
    const preview = readJson(PENDING_PREVIEW_KEY) || currentPreview;
    if (preview) {
      preview.pageSettings = Object.assign({}, preview.pageSettings || {}, { [field]: value });
      writeJson(PENDING_PREVIEW_KEY, preview);
    }
  }

  function setSocialUrlError(message) {
    const error = document.getElementById('builderSocialUrlError');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearSocialUrlError() {
    const error = document.getElementById('builderSocialUrlError');
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }

  // ── 상담 신청 연락처 편집 ─────────────────────────────────────────────────
  // 저장 위치는 footer와 같은 pageSettings.businessInfo다. phone/email만 patch하고 나머지
  // 5개 필드는 현재 preview 값을 그대로 실어 보내야 서버에서 지워지지 않는다.

  function wireConsultContactEditing() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    pageContent.addEventListener('click', (event) => {
      const target = event.target && event.target.closest
        ? event.target.closest('[data-consult-contact-edit]')
        : null;
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      openConsultContactModal();
    });
    wireConsultContactModal();
  }

  function wireConsultContactModal() {
    const modal = document.getElementById('builderConsultContactModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-consult-contact-close]').forEach((button) => {
      button.addEventListener('click', closeConsultContactModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeConsultContactModal();
    });
    const phone = document.getElementById('builderConsultPhoneInput');
    if (phone) {
      phone.addEventListener('input', () => {
        const formatted = BUSINESS_INFO.formatPhoneNumber(phone.value);
        if (formatted !== phone.value) phone.value = formatted;
        clearConsultContactError();
      });
    }
    const domain = document.getElementById('builderConsultEmailDomainSelect');
    if (domain) domain.addEventListener('change', syncConsultCustomDomainField);
    [phone,
      document.getElementById('builderConsultEmailLocalInput'),
      document.getElementById('builderConsultEmailCustomInput')].forEach((input) => {
      if (!input) return;
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') closeConsultContactModal();
        if (event.key === 'Enter') {
          event.preventDefault();
          applyConsultContactFromModal();
        }
      });
    });
    const apply = document.getElementById('builderConsultContactApplyBtn');
    if (apply) apply.addEventListener('click', applyConsultContactFromModal);
  }

  /** 저장된 도메인이 preset에 없으면 `직접입력`을 고른 상태로 복원한다. */
  function renderConsultDomainOptions(selected) {
    const select = document.getElementById('builderConsultEmailDomainSelect');
    if (!select) return;
    const presets = BUSINESS_INFO.EMAIL_DOMAIN_PRESETS;
    const options = presets.map((domain) =>
      `<option value="${escapeHtml(domain)}"${domain === selected ? ' selected' : ''}>${escapeHtml(domain)}</option>`);
    const customSelected = selected !== '' && !presets.includes(selected);
    options.push(`<option value="${escapeHtml(BUSINESS_INFO.EMAIL_DOMAIN_CUSTOM)}"${customSelected ? ' selected' : ''}>직접입력</option>`);
    select.innerHTML = options.join('');
  }

  function syncConsultCustomDomainField() {
    const select = document.getElementById('builderConsultEmailDomainSelect');
    const field = document.getElementById('builderConsultEmailCustomField');
    if (!select || !field) return;
    const custom = select.value === BUSINESS_INFO.EMAIL_DOMAIN_CUSTOM;
    field.hidden = !custom;
    if (!custom) return;
    const input = document.getElementById('builderConsultEmailCustomInput');
    if (input) setTimeout(() => input.focus(), 0);
  }

  function consultSelectedDomain() {
    const select = document.getElementById('builderConsultEmailDomainSelect');
    if (!select) return '';
    if (select.value !== BUSINESS_INFO.EMAIL_DOMAIN_CUSTOM) return select.value;
    const custom = document.getElementById('builderConsultEmailCustomInput');
    return custom ? custom.value.trim() : '';
  }

  function openConsultContactModal() {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 상담 연락처를 편집할 수 있습니다.', 'fa-circle-info');
      return;
    }
    // footer 인라인 편집이 진행 중이면 그 draft를 덮어쓸 수 있어 열지 않는다(저장 후 다시 클릭).
    if (businessInfoEditor.phase !== 'idle') return;
    const modal = document.getElementById('builderConsultContactModal');
    const phone = document.getElementById('builderConsultPhoneInput');
    const local = document.getElementById('builderConsultEmailLocalInput');
    const customInput = document.getElementById('builderConsultEmailCustomInput');
    if (!modal || !phone || !local || !customInput) return;
    const raw = businessInfoRawFromPreview(currentPreview);
    const email = BUSINESS_INFO.splitEmail(raw.email);
    phone.value = BUSINESS_INFO.formatPhoneNumber(raw.phone);
    local.value = email.local;
    renderConsultDomainOptions(email.domain);
    customInput.value = BUSINESS_INFO.EMAIL_DOMAIN_PRESETS.includes(email.domain) ? '' : email.domain;
    syncConsultCustomDomainField();
    clearConsultContactError();
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(() => phone.focus(), 30);
  }

  function closeConsultContactModal() {
    const modal = document.getElementById('builderConsultContactModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    clearConsultContactError();
  }

  async function applyConsultContactFromModal() {
    const phoneInput = document.getElementById('builderConsultPhoneInput');
    const localInput = document.getElementById('builderConsultEmailLocalInput');
    const apply = document.getElementById('builderConsultContactApplyBtn');
    if (!phoneInput || !localInput || !currentPreview) return;
    const phone = phoneInput.value.trim();
    // local이 비면 도메인 선택과 무관하게 이메일을 지운 것으로 본다(`@naver.com` 저장 방지).
    const localPart = localInput.value.trim();
    const email = localPart ? BUSINESS_INFO.joinEmail(localPart, consultSelectedDomain()) : '';
    const invalid = validateContactPair(phone, email);
    if (invalid) {
      setConsultContactError(invalid.message);
      (invalid.key === 'phone' ? phoneInput : localInput).focus();
      return;
    }
    const businessInfo = Object.assign(
      businessInfoRawFromPreview(currentPreview),
      { phone, email });

    if (!currentPreview.publicPathUrl) {
      try {
        currentPreview = persistPendingBusinessInfo(businessInfo);
        replaceBusinessInfoFooter();
        closeConsultContactModal();
        toast('발행 전 미리보기에 상담 연락처를 반영했습니다.', 'fa-headset');
      } catch (_) {
        setConsultContactError('브라우저 저장 공간에 반영하지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
      return;
    }

    setSaving(apply, true);
    try {
      const res = await fetch('/api/tutor/academy/page-settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(pageSettingsWriteBody(currentPreview.pageSettings, { businessInfo: businessInfo }))
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      replaceBusinessInfoFooter();
      closeConsultContactModal();
      toast('상담 연락처를 저장했습니다.', 'fa-headset');
    } catch (_) {
      setConsultContactError('상담 연락처 저장에 실패했습니다. 입력값은 유지됩니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSaving(apply, false);
    }
  }

  /** 모달과 footer 인라인 편집이 같은 규칙을 쓰도록 전화/이메일 검증을 한 곳에 둔다. */
  function validateContactPair(phone, email) {
    if (phone && !BUSINESS_INFO.isValidPhone(phone)) {
      return { key: 'phone', message: '전화번호는 000-0000-0000 또는 000-000-0000 형태로 입력해 주세요.' };
    }
    if (email && !BUSINESS_INFO.isValidEmail(email)) {
      return { key: 'email', message: '올바른 이메일 형식을 입력해 주세요.' };
    }
    return null;
  }

  function setConsultContactError(message) {
    const error = document.getElementById('builderConsultContactError');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearConsultContactError() {
    const error = document.getElementById('builderConsultContactError');
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }

  function wireBusinessInfoEditing() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent || pageContent.dataset.businessInfoWired === 'true') return;
    pageContent.dataset.businessInfoWired = 'true';
    if (document.documentElement.dataset.businessInfoSelectionWired !== 'true') {
      document.documentElement.dataset.businessInfoSelectionWired = 'true';
      document.addEventListener('click', coordinateBusinessInfoSelection, true);
    }
    pageContent.addEventListener('input', (event) => {
      const input = event.target.closest('[data-business-info-input]');
      if (!input || businessInfoEditor.phase !== 'editing') return;
      const fieldKey = input.dataset.businessInfoInput;
      let value = TEXT_UTILS.extractEditableText(input);
      if (fieldKey === 'businessRegistrationNumber' || fieldKey === 'phone') {
        const formatted = fieldKey === 'phone'
          ? BUSINESS_INFO.formatPhoneNumber(value)
          : BUSINESS_INFO.formatRegistrationNumber(value);
        if (formatted !== value) {
          input.textContent = formatted;
          moveCaretToEnd(input);
        }
        value = formatted;
      }
      businessInfoEditor = BUSINESS_INFO_EDITOR.withFieldValue(
        businessInfoEditor,
        fieldKey,
        value);
      input.setAttribute('aria-invalid', 'false');
      input.classList.remove('is-invalid');
      const error = document.getElementById('builderBusinessInfoError');
      if (error) error.textContent = '';
    });
    pageContent.addEventListener('beforeinput', (event) => {
      const input = event.target.closest('[data-business-info-input]');
      if (!input || input.dataset.businessInfoInput === 'address') return;
      if (event.inputType === 'insertParagraph' || event.inputType === 'insertLineBreak') event.preventDefault();
    });
    pageContent.addEventListener('paste', (event) => {
      const input = event.target.closest('[data-business-info-input]');
      if (!input || input.dataset.businessInfoInput === 'address') return;
      const text = event.clipboardData && event.clipboardData.getData('text/plain');
      if (!text || !/[\r\n\t]/.test(text)) return;
      event.preventDefault();
      insertPlainText(TEXT_UTILS.normalizeSingleLineText(text));
    });
    pageContent.addEventListener('keydown', (event) => {
      const footer = event.target.closest('[data-business-info-footer]');
      if (!footer) return;
      const input = event.target.closest('[data-business-info-input]');
      if (input && event.key === 'Escape' && businessInfoEditor.phase === 'editing') {
        event.preventDefault();
        revertBusinessInfoField(input);
        return;
      }
      const modified = event.ctrlKey || event.metaKey;
      if (input && event.key === 'Enter' && input.dataset.businessInfoInput === 'address' && !modified) return;
      const decision = BUSINESS_INFO_EDITOR.keyDecision(
        businessInfoEditor,
        event.key,
        modified,
        Boolean(event.target.closest('a.academy-business-footer__link')));
      if (decision === 'allow') return;
      event.preventDefault();
      if (decision === 'enter') enterBusinessInfoEditing(businessInfoFieldKey(event.target));
      else if (decision === 'cancel') cancelBusinessInfoEditing();
      else if (decision === 'apply') applyBusinessInfoDraft();
    });
  }

  function coordinateBusinessInfoSelection(event) {
    const target = event.target && event.target.closest ? event.target : null;
    if (!target) return;
    const action = target.closest('[data-business-info-action]');
    if (action) {
      event.preventDefault();
      event.stopImmediatePropagation();
      if (action.dataset.businessInfoAction === 'apply') applyBusinessInfoDraft();
      return;
    }
    const footer = target.closest('[data-business-info-footer]');
    if (footer) {
      const publicLink = target.closest('a.academy-business-footer__link');
      const decision = BUSINESS_INFO_EDITOR.footerDecision(businessInfoEditor, Boolean(publicLink));
      if (decision !== 'enter') return;
      event.preventDefault();
      event.stopImmediatePropagation();
      enterBusinessInfoEditing(businessInfoFieldKey(target));
      return;
    }
    const outsideDecision = BUSINESS_INFO_EDITOR.outsideDecision(businessInfoEditor);
    if (outsideDecision === 'block-saving') {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }
    if (outsideDecision === 'apply-allow') {
      const accepted = applyBusinessInfoDraft();
      if (accepted) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }
    if (outsideDecision === 'cancel-allow') cancelBusinessInfoEditing({ silent: true });
  }

  function businessInfoFieldKey(target) {
    const field = target && target.closest && target.closest('[data-business-info-input],[data-business-info-field]');
    return field && (field.dataset.businessInfoInput || field.dataset.businessInfoField) || null;
  }

  function enterBusinessInfoEditing(fieldKey) {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 사업자 정보를 편집할 수 있습니다.', 'fa-circle-info');
      return;
    }
    if (!clearActiveSectionSelection()) return;
    ensureSectionStyleSelectionStyle();
    const raw = businessInfoRawFromPreview(currentPreview);
    businessInfoEditor = BUSINESS_INFO_EDITOR.begin(raw);
    replaceBusinessInfoFooter();
    if (fieldKey) focusBusinessInfoField(fieldKey);
  }

  function cancelBusinessInfoEditing(options) {
    if (businessInfoEditor.phase === 'idle' || businessInfoEditor.phase === 'saving') return false;
    businessInfoEditor = BUSINESS_INFO_EDITOR.idle();
    replaceBusinessInfoFooter();
    if (!(options && options.silent)) toast('Footer 사업자 정보 수정을 취소했습니다.', 'fa-rotate-left');
    return true;
  }

  function applyBusinessInfoDraft() {
    if (!currentPreview || businessInfoEditor.phase !== 'editing') return false;
    const businessInfo = readBusinessInfoDraftFromInlineValues();
    const invalid = validateBusinessInfoDraft(businessInfo);
    businessInfoEditor = BUSINESS_INFO_EDITOR.withDraft(
      businessInfoEditor,
      businessInfo,
      !sameBusinessInfoRaw(businessInfo, businessInfoEditor.snapshotRaw));
    if (invalid) {
      showBusinessInfoError(invalid.message, invalid.key);
      return false;
    }
    const savingTransition = BUSINESS_INFO_EDITOR.beginSaving(businessInfoEditor);
    if (!savingTransition.accepted) return false;
    businessInfoEditor = savingTransition.state;
    replaceBusinessInfoFooter();
    persistBusinessInfoDraft(businessInfo);
    return true;
  }

  async function persistBusinessInfoDraft(businessInfo) {
    if (!currentPreview.publicPathUrl) {
      try {
        const nextPreview = persistPendingBusinessInfo(businessInfo);
        currentPreview = nextPreview;
        businessInfoEditor = BUSINESS_INFO_EDITOR.idle();
        replaceBusinessInfoFooter();
        toast('발행 전 미리보기에 사업자 정보를 반영했습니다.', 'fa-building');
      } catch (_) {
        showBusinessInfoError('브라우저 저장 공간에 반영하지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.');
      }
      return;
    }

    try {
      const res = await fetch('/api/tutor/academy/page-settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(pageSettingsWriteBody(currentPreview.pageSettings, { businessInfo: businessInfo }))
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      businessInfoEditor = BUSINESS_INFO_EDITOR.idle();
      replaceBusinessInfoFooter();
      toast('사업자 정보를 저장했습니다.', 'fa-building');
    } catch (_) {
      showBusinessInfoError('사업자 정보 저장에 실패했습니다. 입력값은 유지됩니다. 잠시 후 다시 시도해 주세요.');
    }
  }

  function businessInfoRawFromPreview(preview) {
    const settings = preview && preview.pageSettings || {};
    return BUSINESS_INFO.normalizeRaw(settings.businessInfo);
  }

  function readBusinessInfoDraftFromInlineValues() {
    const raw = {};
    BUSINESS_INFO.EDITABLE_KEYS.forEach((key) => {
      const input = document.querySelector('[data-business-info-input="' + key + '"]');
      raw[key] = input ? TEXT_UTILS.extractEditableText(input) : businessInfoEditor.draftRaw[key] || '';
    });
    return BUSINESS_INFO.normalizeRaw(raw);
  }

  function validateBusinessInfoDraft(raw) {
    const oversized = BUSINESS_INFO_FIELDS.find((field) => (raw[field.key] || '').length > field.maxlength);
    if (oversized) {
      return { key: oversized.key, message: `${oversized.label}은(는) ${oversized.maxlength}자 이내로 입력해 주세요.` };
    }
    if (raw.businessRegistrationNumber
        && BUSINESS_INFO.registrationDigits(raw.businessRegistrationNumber).length !== 10) {
      return { key: 'businessRegistrationNumber', message: '사업자등록번호는 숫자 10자리여야 합니다.' };
    }
    return validateContactPair(raw.phone, raw.email);
  }

  function sameBusinessInfoRaw(left, right) {
    return BUSINESS_INFO.EDITABLE_KEYS.every((key) => (left && left[key] || '') === (right && right[key] || ''));
  }

  function showBusinessInfoError(message, key) {
    businessInfoEditor = BUSINESS_INFO_EDITOR.fail(businessInfoEditor, message, key);
    replaceBusinessInfoFooter();
    focusBusinessInfoField(key);
  }

  function focusBusinessInfoField(key) {
    const selector = key
      ? '[data-business-info-input="' + key + '"]'
      : '[data-business-info-input]';
    setTimeout(() => {
      const target = document.querySelector(selector);
      if (target) {
        target.focus();
        selectEditableText(target);
      }
      else document.getElementById('builderBusinessInfoError')?.focus();
    }, 0);
  }

  function revertBusinessInfoField(target) {
    const key = target && target.dataset.businessInfoInput;
    if (!key || businessInfoEditor.phase !== 'editing') return;
    businessInfoEditor = BUSINESS_INFO_EDITOR.revertField(businessInfoEditor, key);
    target.textContent = businessInfoEditor.draftRaw[key] || '';
    target.setAttribute('aria-invalid', 'false');
    target.classList.remove('is-invalid');
    const error = document.getElementById('builderBusinessInfoError');
    if (error) error.textContent = '';
    target.blur();
  }

  function replaceBusinessInfoFooter() {
    if (!currentPreview) return;
    const footer = document.querySelector('#pageContent [data-business-info-footer]');
    if (!footer) return;
    footer.outerHTML = renderFooter(normalizePreview(currentPreview));
  }

  function mergePendingBusinessInfoIntoPreview(preview, request) {
    const merged = cloneJson(preview || previewFromRequest(request));
    const requestSettings = request && request.pageSettings;
    if (!merged || !requestSettings || !Object.prototype.hasOwnProperty.call(requestSettings, 'businessInfo')) {
      return merged;
    }
    merged.pageSettings = Object.assign({}, merged.pageSettings || {}, {
      businessInfo: cloneJson(requestSettings.businessInfo)
    });
    return merged;
  }

  function persistPendingBusinessInfo(raw) {
    const request = readJson(PENDING_KEY);
    const result = BUSINESS_INFO_EDITOR.persistPending({
      request,
      cachedPreview: readJson(PENDING_PREVIEW_KEY),
      currentPreview,
      raw,
      previewFromRequest,
      mergePreview: mergePendingBusinessInfoIntoPreview,
      writeRequest: (nextRequest) => writeJsonRequired(PENDING_KEY, nextRequest),
      writePreview: (nextPreview) => writeJsonRequired(PENDING_PREVIEW_KEY, nextPreview)
    });
    return result.nextPreview;
  }

  function writeJsonRequired(key, value) {
    sessionStorage.setItem(key, JSON.stringify(value));
  }

  function cloneJson(value) {
    if (value == null) return value;
    return JSON.parse(JSON.stringify(value));
  }

  function editableTextTarget(node) {
    return node && node.closest ? node.closest('[data-builder-text],[data-builder-draft]') : null;
  }

  function focusInlineText(field) {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 텍스트를 편집할 수 있습니다.', 'fa-circle-info');
      return;
    }
    if (!['name', 'intro'].includes(field)) return;
    const pageContent = document.getElementById('pageContent');
    const target = pageContent && pageContent.querySelector('[data-builder-text="' + field + '"]');
    if (!target) {
      toast(field === 'intro' ? '소개 섹션이 숨김 상태입니다. 먼저 섹션을 표시해 주세요.' : '편집할 텍스트를 찾지 못했습니다.', 'fa-circle-info');
      return;
    }
    const section = target.closest('[data-builder-section-key]');
    if (section && !enterSectionTextEditMode(section)) return;
    scrollPreviewToSection(field === 'intro' ? 'sec-intro' : 'sec-hero');
    setTimeout(() => focusEditableTarget(target), 120);
  }

  function selectEditableText(target) {
    const range = document.createRange();
    range.selectNodeContents(target);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
  }

  /**
   * 클릭 좌표(x,y)를 그 지점의 caret 위치로 변환한다. 브라우저마다 API가 갈려
   * (Chrome/Safari는 caretRangeFromPoint, Firefox는 caretPositionFromPoint) 둘 다 시도한다.
   * 좌표 정보가 없거나 지원되지 않으면 null.
   */
  function caretRangeFromPoint(x, y) {
    if (typeof x !== 'number' || typeof y !== 'number') return null;
    if (document.caretRangeFromPoint) {
      return document.caretRangeFromPoint(x, y);
    }
    if (document.caretPositionFromPoint) {
      const pos = document.caretPositionFromPoint(x, y);
      if (!pos || !pos.offsetNode) return null;
      const range = document.createRange();
      range.setStart(pos.offsetNode, pos.offset);
      range.collapse(true);
      return range;
    }
    return null;
  }

  /**
   * Direct Text Editing UX(Phase 5B): 편집 진입 시 기존 문구 "전체"를 selection 하지 않는다.
   * 사용자가 클릭한 글자 위치에 caret 하나만 놓아, 일반 문서 편집기처럼 그 자리에서 바로
   * 수정할 수 있게 한다. 클릭 좌표가 대상 밖이거나 좌표가 없으면 문장 끝으로 collapse 한다
   * (여전히 전체 선택은 아니다).
   */
  function placeCaretAtPoint(target, point) {
    const selection = window.getSelection && window.getSelection();
    if (!selection || !target) return;
    let range = point ? caretRangeFromPoint(point.x, point.y) : null;
    if (range && (!range.startContainer || !target.contains(range.startContainer))) {
      range = null;
    }
    if (!range) {
      range = document.createRange();
      range.selectNodeContents(target);
      range.collapse(false);
    }
    selection.removeAllRanges();
    selection.addRange(range);
  }

  function enterSectionTextEditMode(section) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent || !section) return false;
    ensureSectionStyleSelectionStyle();
    if (!applyActiveTextSectionBeforeSwitch(section)) return false;
    if (activeTextEditSection && activeTextEditSection !== section) {
      setTextTargetsEditable(activeTextEditSection, false);
      removeSectionTextApply(activeTextEditSection);
      activeTextEditSection.classList.remove('builder-text-section-editing');
    }
    setActiveTextEditSection(section);
    markSelectedSection(section);
    section.classList.add('builder-text-section-editing');
    if (getSectionTextTargets(section).length) {
      setTextTargetsEditable(section, true);
    }
    renderSectionTextApply(section);
    return true;
  }

  function applyActiveTextSectionBeforeSwitch(nextSection) {
    if (!activeTextEditSection || activeTextEditSection === nextSection) return true;
    return applySectionTextChanges(activeTextEditSection, {
      reason: 'section-switch',
      suppressNoChangeToast: true
    });
  }

  function getSectionTextTargets(section) {
    return Array.from(section.querySelectorAll('[data-builder-text],[data-builder-draft]'));
  }

  function setTextTargetsEditable(root, editable) {
    if (!root) return;
    getSectionTextTargets(root).forEach((target) => target.setAttribute('contenteditable', String(editable)));
  }

  function renderSectionTextApply(section) {
    if (!section || section.querySelector('.builder-section-controls')) return;
    const previousPosition = window.getComputedStyle(section).position;
    if (!previousPosition || previousPosition === 'static') section.style.position = 'relative';
    // 헤더는 숨길 수 없는 고정 영역이라 삭제 버튼을 달지 않는다. 또 sticky top:0이라
    // 기본 위치(top:-14px)면 컨트롤이 화면 위로 잘려 나가므로 아래쪽에 건다.
    const isHeader = section.dataset.builderSectionKey === FIXED_HEADER_SECTION_ID;
    const controls = document.createElement('div');
    controls.className = 'block-controls builder-section-controls' + (isHeader ? ' builder-header-controls' : '');
    controls.appendChild(sectionControlButton('fa-check', '적용', '', () => applySectionTextChanges(section)));
    appendCollectionLayoutControl(controls, section);
    appendMiniLecturesControl(controls, section);
    if (!isHeader) {
      controls.appendChild(sectionControlButton('fa-trash', '삭제', 'danger', () => hideSelectedSection(section)));
    }
    section.appendChild(controls);
  }

  // ── Horizontal Content Collection 레이아웃 선택 (PHASE 3-B) ──────────────
  // sec-lessons(Program) / sec-videos(Video)는 "기존 레이아웃"과 "가로 컬렉션 shelf" 중
  // 하나를 고를 수 있다. 값은 builderDraft.lessonsLayout / videosLayout 이며 'horizontal' 일 때만
  // opt-in(그 외/미설정이면 기존 레이아웃). 다른 학원 사이트는 이 키가 없으므로 그대로 유지된다.
  const COLLECTION_LAYOUT_KEYS = { 'sec-lessons': 'lessonsLayout', 'sec-videos': 'videosLayout' };

  // Phase 2부터 가로 컬렉션이 기본이다. 미설정/'horizontal'은 가로, 명시적 'vertical'만
  // 기존 세로 레이아웃으로 되돌아간다(academy_sections.js isHorizontalCollection과 동일 규칙).
  function isHorizontalLayout(draftKey) {
    return String(draftText(currentPreview, draftKey, '') || '').trim().toLowerCase() !== 'vertical';
  }

  function appendCollectionLayoutControl(controls, section) {
    const sectionId = canonicalSectionId(section.dataset.builderSectionKey);
    const draftKey = COLLECTION_LAYOUT_KEYS[sectionId];
    if (!draftKey || !currentPreview) return;
    const label = isHorizontalLayout(draftKey) ? '레이아웃: 가로(기본)' : '레이아웃: 세로';
    controls.appendChild(sectionControlButton('fa-table-columns', label, '', () => toggleCollectionLayout(sectionId, draftKey)));
  }

  function toggleCollectionLayout(sectionId, draftKey) {
    if (!currentPreview) return;
    const next = isHorizontalLayout(draftKey) ? 'vertical' : '';
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, { [draftKey]: next });
    currentPreview.pageSettings = pageSettings;
    populatePreview(currentPreview);
    persistVariableListChange();
    toast(
      next === 'vertical'
        ? '세로 레이아웃으로 되돌렸습니다. 발행 시 저장됩니다.'
        : '가로 컬렉션 레이아웃(기본)으로 전환했습니다. 발행 시 저장됩니다.',
      'fa-table-columns'
    );
  }

  // ── Mini Lectures 편집 (V2-9) ──────────────────────────────────────────────
  // sec-minilectures = "교육자가 직접 설명하는 짧은 학습 콘텐츠". Actual Class 와 역할이 다르다.
  // 콘텐츠는 academy_videos 재사용 + 강의별 제목/학습포인트/대상은 builderDraft.miniLectures
  // (JSON 배열 문자열)에 담는다. 스키마 변경 없음. 실제 콘텐츠가 없으면 섹션은 OFF·빈 상태를 유지한다.
  const MINI_LECTURES_MAX = 12;

  function parsedMiniLectures() {
    const raw = draftText(currentPreview, 'miniLectures', '');
    if (!raw) return [];
    try {
      const arr = JSON.parse(raw);
      return Array.isArray(arr)
        ? arr.filter((e) => e && Number(e.v) > 0).map((e) => ({
            v: Number(e.v),
            t: String(e.t == null ? '' : e.t),
            n: String(e.n == null ? '' : e.n),
            g: String(e.g == null ? '' : e.g)
          }))
        : [];
    } catch (_) {
      return [];
    }
  }

  function appendMiniLecturesControl(controls, section) {
    const sectionId = canonicalSectionId(section.dataset.builderSectionKey);
    if (sectionId !== 'sec-minilectures' || !currentPreview) return;
    controls.appendChild(sectionControlButton('fa-graduation-cap', '미니 강의 편집', '', openMiniLecturesModal));
  }

  // ── AI Suggestion (Phase 4, 마크업 회귀 수정 Phase 4.1) ────────────────────
  // 절대 원칙: 여기서 만드는 건 "제안"뿐이다. 생성만으로는 currentPreview.pageSettings도,
  // Academy DB도 절대 바뀌지 않는다 — 사용자가 이 모달의 "적용"을 눌러야만 기존 인라인 텍스트
  // 편집과 완전히 같은 경로(pageSettings.builderDraft 갱신 → populatePreview →
  // persistTextChangesAfterApply, 즉 발행 전이면 미리보기 반영, 발행 후면 "발행" 버튼으로
  // 저장 안내)로 반영된다. AI 전용 저장 경로를 새로 만들지 않는다.
  //
  // Phase 4.1 회귀 수정: 원래는 섹션을 클릭해 "텍스트 편집 모드"에 들어가야만 나타나는
  // 플로팅 툴바(renderSectionTextApply → appendAiAssistControl)에만 AI 버튼을 달았다.
  // 로직 자체(canonicalSectionId/AI_ASSIST_SECTIONS 연결)는 정상이었지만 — 편집 모드 진입은
  // 직전 섹션의 검증 실패 등으로 막힐 수 있고, 무엇보다 클릭하지 않으면 버튼 존재 자체를 알
  // 방법이 없었다. 이제 academy_sections.js(ctx.aiAssistButton)가 정적 마크업에 항상
  // 렌더한다 — 편집 모드 진입 여부와 무관하게 항상 보이고 항상 클릭 가능하다. task/target도
  // 버튼의 data 속성에서 직접 읽어 activeTextField 추론(activeAiTarget) 같은 간접 로직을
  // 없앴다.
  let aiSuggestionState = null;

  /** pageContent 전체에 위임 — innerHTML이 통째로 재렌더돼도(발행/섹션전환) 다시 배선할 필요 없다. */
  function wireAiAssistTriggers() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent || pageContent.dataset.aiAssistWired === 'true') return;
    pageContent.dataset.aiAssistWired = 'true';
    pageContent.addEventListener('click', (event) => {
      const trigger = event.target && event.target.closest ? event.target.closest('[data-ai-suggest-trigger]') : null;
      if (!trigger || !pageContent.contains(trigger)) return;
      event.preventDefault();
      event.stopPropagation();
      openAiSuggestionModal(trigger.dataset.aiSuggestTask, trigger.dataset.aiSuggestTarget);
    });
  }

  /**
   * 모달을 열어도 곧바로 생성하지 않는다(Phase 4.2) — 사용자가 "AI에게 요청할 내용"을
   * 먼저 적을 기회를 준다. 실제 요청은 [제안 생성] 클릭(requestAiSuggestion)에서만 나간다.
   */
  function openAiSuggestionModal(task, target) {
    const modal = document.getElementById('builderAiSuggestionModal');
    if (!modal || !currentPreview || !task || !target) return;
    const currentValue = draftText(currentPreview, target, '');
    aiSuggestionState = { task, target, currentValue, suggestedValue: null };
    setAiSuggestionCurrentText(currentValue);
    setAiSuggestionResultText('');
    clearAiSuggestionError();
    setAiSuggestionApplyEnabled(false);
    resetAiSuggestionInstructions();
    setAiSuggestionGenerateLabel('제안 생성');
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
  }

  function closeAiSuggestionModal() {
    const modal = document.getElementById('builderAiSuggestionModal');
    if (modal) {
      modal.classList.remove('is-open');
      modal.setAttribute('aria-hidden', 'true');
    }
    aiSuggestionState = null;
  }

  function setAiSuggestionCurrentText(text) {
    const el = document.getElementById('builderAiSuggestionCurrent');
    if (el) el.textContent = text || '(비어 있음)';
  }

  function setAiSuggestionResultText(text) {
    const el = document.getElementById('builderAiSuggestionResult');
    if (el) el.textContent = text || '';
  }

  function setAiSuggestionApplyEnabled(enabled) {
    const btn = document.getElementById('builderAiSuggestionApplyBtn');
    if (btn) btn.disabled = !enabled;
  }

  /** 모달을 새로 열 때마다 이전 세션의 요구사항이 남지 않도록 비운다. */
  function resetAiSuggestionInstructions() {
    const el = document.getElementById('builderAiSuggestionInstructions');
    if (el) el.value = '';
  }

  function aiSuggestionInstructions() {
    const el = document.getElementById('builderAiSuggestionInstructions');
    return el ? String(el.value || '').trim() : '';
  }

  /** [제안 생성]→[다시 생성] 라벨 전환. 버튼 자체(및 클릭 핸들러)는 하나만 쓴다. */
  function setAiSuggestionGenerateLabel(text) {
    const btn = document.getElementById('builderAiSuggestionRegenerateBtn');
    if (btn) btn.textContent = text;
  }

  function setAiSuggestionLoading(loading) {
    const loadingEl = document.getElementById('builderAiSuggestionLoading');
    const regenBtn = document.getElementById('builderAiSuggestionRegenerateBtn');
    if (loadingEl) loadingEl.hidden = !loading;
    if (regenBtn) regenBtn.disabled = loading;
    // 로딩 시작(loading=true) 때만 강제로 잠근다. 로딩이 끝난 뒤(loading=false)는 여기서
    // 건드리지 않는다 — 실제 활성화 여부는 호출부(requestAiSuggestion 성공/실패 분기)가
    // setAiSuggestionApplyEnabled로 이미 정확히 결정했다. 예전에는 여기서 무조건 false로
    // 되돌려, 생성 성공 직후 켠 적용 버튼을 finally에서 곧바로 다시 꺼버렸다(버튼은
    // 그대로 보여도 disabled라 실제 클릭은 브라우저가 아예 막는다 — 콘솔 에러도 없이
    // "눌러도 반응 없음"으로 보였다).
    if (loading) setAiSuggestionApplyEnabled(false);
  }

  function setAiSuggestionError(message) {
    const error = document.getElementById('builderAiSuggestionError');
    if (!error) return;
    error.textContent = message || '';
    error.hidden = !message;
  }

  function clearAiSuggestionError() {
    setAiSuggestionError('');
  }

  /**
   * [제안 생성]/[다시 생성] 둘 다 이 함수를 그대로 호출한다 — 새 suggestion으로 교체될 뿐
   * 어떤 데이터도 저장하지 않는다. "다시 생성"을 눌러도 textarea는 비우지 않으므로(모달을
   * 새로 열 때만 resetAiSuggestionInstructions가 비운다) 사용자가 입력한 요구사항은 그대로
   * 유지된 채 재요청에 실린다.
   */
  async function requestAiSuggestion() {
    if (!aiSuggestionState) return;
    const requested = aiSuggestionState;
    const instructions = aiSuggestionInstructions();
    setAiSuggestionLoading(true);
    clearAiSuggestionError();
    setAiSuggestionResultText('');
    try {
      const res = await fetch('/api/tutor/academy/ai/suggestions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({
          task: requested.task,
          target: requested.target,
          currentValue: requested.currentValue,
          instructions: instructions
        })
      });
      if (aiSuggestionState !== requested) return; // 응답이 오기 전에 모달이 닫힘/전환됨
      if (!res.ok) {
        setAiSuggestionError(await aiErrorMessage(res, 'AI 제안을 만들지 못했습니다.'));
        return;
      }
      const suggestion = await res.json();
      if (aiSuggestionState !== requested) return;
      aiSuggestionState.suggestedValue = suggestion.suggestedValue;
      setAiSuggestionResultText(suggestion.suggestedValue);
      setAiSuggestionApplyEnabled(true);
    } catch (_) {
      if (aiSuggestionState === requested) {
        setAiSuggestionError('네트워크 연결이 끊겼습니다. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      if (aiSuggestionState === requested) setAiSuggestionLoading(false);
    }
  }

  async function aiErrorMessage(res, fallback) {
    try {
      const body = await res.json();
      if (body && body.message) return body.message;
    } catch (_) { /* JSON 오류가 아니면 기본 문구를 사용한다. */ }
    return fallback;
  }

  /**
   * "적용" — 기존 인라인 텍스트 편집(commitDraftText)과 완전히 같은 방식으로
   * currentPreview.pageSettings.builderDraft만 갱신한다. 별도 AI 적용 API가 없다: 이후
   * populatePreview + persistTextChangesAfterApply는 사람이 직접 타이핑했을 때와 동일하게
   * 발행 여부에 따라 "미리보기 반영" 또는 "발행 버튼으로 저장 안내"로 갈린다 — AI가 기존
   * Publish 절차를 우회하지 않는다.
   */
  function applyAiSuggestionToBuilder() {
    if (!aiSuggestionState || !aiSuggestionState.suggestedValue || !currentPreview) return;
    const key = aiSuggestionState.target;
    const value = aiSuggestionState.suggestedValue;
    const previous = draftText(currentPreview, key, '');
    if (value === previous) {
      closeAiSuggestionModal();
      return;
    }
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, { [key]: value });
    currentPreview.pageSettings = pageSettings;
    const target = document.querySelector('[data-builder-draft="' + cssAttrEscape(key) + '"]');
    if (target) {
      target.textContent = value;
      target.dataset.beforeEdit = value;
    }
    populatePreview(currentPreview);
    persistTextChangesAfterApply({});
    closeAiSuggestionModal();
    toast('AI 제안을 적용했습니다.', 'fa-wand-magic-sparkles');
  }

  function wireAiSuggestionEditing() {
    const modal = document.getElementById('builderAiSuggestionModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-ai-suggestion-close]').forEach((button) => {
      button.addEventListener('click', closeAiSuggestionModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeAiSuggestionModal();
    });
    const regenerate = document.getElementById('builderAiSuggestionRegenerateBtn');
    if (regenerate) regenerate.addEventListener('click', () => {
      setAiSuggestionGenerateLabel('다시 생성');
      requestAiSuggestion();
    });
    const apply = document.getElementById('builderAiSuggestionApplyBtn');
    if (apply) apply.addEventListener('click', applyAiSuggestionToBuilder);
  }

  // ── AI Academy Builder (Phase 5A) ────────────────────────────────────────
  // "✨ AI로 Academy 구성하기" — AI가 Academy 전체를 진단하고 안전한 개선안(문구 개선 · 섹션
  // 순서 변경)을 구조화된 proposal로 제안한다. 절대 원칙(Phase 4와 동일):
  //   1) [Academy 분석하기]는 읽기 전용이다 — currentPreview도 Academy DB도 절대 바뀌지 않는다.
  //   2) proposal "적용"을 눌러야만 기존 인라인 편집과 완전히 같은 경로
  //      (currentPreview.pageSettings.builderDraft / sectionOrder → applyAcademyToLivePreview →
  //      persistTextChangesAfterApply)로 반영된다. 별도 AI 저장 API가 없다.
  //   3) Publish는 AI가 절대 실행하지 않는다 — 사용자가 기존 [발행] 버튼을 눌러야 한다.
  //   4) 서버가 이미 allowlist를 검증하지만, 여기서도 target/섹션 순서를 한 번 더 검증해
  //      손상·악의적 응답이 임의 key를 쓰지 못하게 한다.
  const COMPOSE_TEXT_TARGETS = [
    'principalDesc', 'principalQuote',
    'philosophyItem1', 'philosophyItem2', 'philosophyItem3', 'philosophyItem4',
    'philosophyKeyword', 'philosophyTitle',
    'introHeading', 'introMotto', 'introBodySecondary',
    'featuredEyebrow', 'featuredTitle', 'featuredDescription', 'featuredCtaLabel',
    'videosSectionTitle', 'socialSectionTitle', 'miniLecturesSectionTitle',
    'ctaTitle', 'ctaSubtitle'
  ];
  // { proposals: [], applied: Set<id>, textKeys: Set<key>, orderTouched: bool, snapshot: {...}|null }
  let academyComposeState = null;

  function wireAcademyComposeModal() {
    const trigger = document.getElementById('builderComposeTrigger');
    if (trigger && trigger.dataset.wired !== 'true') {
      trigger.dataset.wired = 'true';
      trigger.addEventListener('click', openAcademyComposeModal);
    }
    const modal = document.getElementById('builderAcademyComposeModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-compose-close]').forEach((button) => {
      button.addEventListener('click', closeAcademyComposeModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeAcademyComposeModal();
    });
    const analyze = document.getElementById('builderComposeAnalyzeBtn');
    if (analyze) analyze.addEventListener('click', requestAcademyProposal);
    const applyAll = document.getElementById('builderComposeApplyAllBtn');
    if (applyAll) applyAll.addEventListener('click', applyAllComposeProposals);
    const revert = document.getElementById('builderComposeRevertBtn');
    if (revert) revert.addEventListener('click', revertComposeChanges);
    const cards = document.getElementById('builderComposeCards');
    if (cards) cards.addEventListener('click', (event) => {
      const button = event.target && event.target.closest
        ? event.target.closest('[data-compose-apply]') : null;
      if (!button || !cards.contains(button)) return;
      applyComposeProposalById(button.dataset.composeApply);
    });
  }

  function openAcademyComposeModal() {
    const modal = document.getElementById('builderAcademyComposeModal');
    if (!modal || !currentPreview) return;
    academyComposeState = {
      proposals: [], applied: new Set(), textKeys: new Set(), orderTouched: false, snapshot: null
    };
    const instructions = document.getElementById('builderComposeInstructions');
    if (instructions) instructions.value = '';
    setComposeError('');
    setComposeLoading(false);
    setComposeAnalyzeLabel('Academy 분석하기');
    const results = document.getElementById('builderComposeResults');
    if (results) results.hidden = true;
    const cards = document.getElementById('builderComposeCards');
    if (cards) cards.innerHTML = '';
    const toolbar = document.getElementById('builderComposeToolbar');
    if (toolbar) toolbar.hidden = true;
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
  }

  function closeAcademyComposeModal() {
    const modal = document.getElementById('builderAcademyComposeModal');
    if (modal) {
      modal.classList.remove('is-open');
      modal.setAttribute('aria-hidden', 'true');
    }
    academyComposeState = null;
  }

  function setComposeLoading(loading) {
    const el = document.getElementById('builderComposeLoading');
    const analyze = document.getElementById('builderComposeAnalyzeBtn');
    if (el) el.hidden = !loading;
    if (analyze) analyze.disabled = loading;
  }

  function setComposeError(message) {
    const el = document.getElementById('builderComposeError');
    if (!el) return;
    el.textContent = message || '';
    el.hidden = !message;
  }

  function setComposeAnalyzeLabel(text) {
    const btn = document.getElementById('builderComposeAnalyzeBtn');
    if (btn) btn.textContent = text;
  }

  /** [Academy 분석하기]/[다시 분석하기] — 읽기 전용. 어떤 데이터도 저장하지 않는다. */
  async function requestAcademyProposal() {
    if (!academyComposeState) return;
    const requested = academyComposeState;
    const el = document.getElementById('builderComposeInstructions');
    const instructions = el ? String(el.value || '').trim() : '';
    setComposeLoading(true);
    setComposeError('');
    try {
      const res = await fetch('/api/tutor/academy/ai/academy-proposal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ instructions: instructions })
      });
      if (academyComposeState !== requested) return;
      if (!res.ok) {
        setComposeError(await aiErrorMessage(res, 'AI 분석에 실패했습니다.'));
        return;
      }
      const proposal = await res.json();
      if (academyComposeState !== requested) return;
      academyComposeState.proposals = Array.isArray(proposal.proposals) ? proposal.proposals : [];
      renderAcademyProposal(proposal.diagnosis, academyComposeState.proposals);
      setComposeAnalyzeLabel('다시 분석하기');
    } catch (_) {
      if (academyComposeState === requested) {
        setComposeError('네트워크 연결이 끊겼습니다. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      if (academyComposeState === requested) setComposeLoading(false);
    }
  }

  function renderAcademyProposal(diagnosis, proposals) {
    const results = document.getElementById('builderComposeResults');
    const diagEl = document.getElementById('builderComposeDiagnosis');
    const cardsEl = document.getElementById('builderComposeCards');
    const toolbar = document.getElementById('builderComposeToolbar');
    if (!results || !diagEl || !cardsEl) return;
    diagEl.textContent = diagnosis || '';
    const valid = (proposals || []).filter(isApplicableComposeProposal);
    if (!valid.length) {
      cardsEl.innerHTML = '<p class="builder-compose-empty">지금 바로 적용할 만한 개선안은 없습니다. 현재 구성이 안정적입니다.</p>';
      if (toolbar) toolbar.hidden = true;
    } else {
      cardsEl.innerHTML = valid.map(composeCardHtml).join('');
      if (toolbar) toolbar.hidden = false;
      syncComposeToolbar();
    }
    results.hidden = false;
  }

  function composeCardHtml(item) {
    const applied = !!(academyComposeState && academyComposeState.applied.has(item.id));
    const typeLabel = item.type === 'SECTION_ORDER' ? '섹션 순서' : '문구';
    const newTextLine = item.type === 'TEXT' && item.textValue
      ? `<p class="builder-compose-card-line"><b>새 문구 →</b> ${escapeHtml(item.textValue)}</p>` : '';
    return `<div class="builder-compose-card${applied ? ' is-applied' : ''}" data-compose-card="${escapeAttr(item.id)}">`
      + `<p class="builder-compose-card-title">${escapeHtml(item.title || typeLabel)}</p>`
      + `<p class="builder-compose-card-line"><b>현재 →</b> ${escapeHtml(item.current || '-')}</p>`
      + `<p class="builder-compose-card-line"><b>제안 →</b> ${escapeHtml(item.suggestion || '-')}</p>`
      + newTextLine
      + '<div class="builder-compose-card-actions">'
      + `<button type="button" class="${applied ? 'secondary' : 'primary'}" data-compose-apply="${escapeAttr(item.id)}"${applied ? ' disabled' : ''}>${applied ? '적용됨' : '적용'}</button>`
      + '</div></div>';
  }

  /** 서버 검증과 별개로 프론트에서도 target/섹션 순서를 다시 확인한다(방어적 이중화). */
  function isApplicableComposeProposal(item) {
    if (!item || !item.id) return false;
    if (item.type === 'TEXT') {
      return COMPOSE_TEXT_TARGETS.indexOf(item.target) >= 0
        && typeof item.textValue === 'string' && item.textValue.trim() !== '';
    }
    if (item.type === 'SECTION_ORDER') return isValidComposeSectionOrder(item.sectionOrder);
    return false;
  }

  function isValidComposeSectionOrder(order) {
    if (!Array.isArray(order) || order.length !== DEFAULT_SECTION_ORDER.length) return false;
    if (order[0] !== 'sec-hero') return false;
    const seen = new Set(order);
    if (seen.size !== order.length) return false;
    return DEFAULT_SECTION_ORDER.every((id) => seen.has(id));
  }

  /** 이 compose 세션에서 처음 "적용"하기 직전에 원상복구용 스냅샷을 1번 찍는다. */
  function captureComposeSnapshot() {
    if (!academyComposeState || academyComposeState.snapshot || !currentPreview) return;
    const pageSettings = currentPreview.pageSettings || {};
    academyComposeState.snapshot = {
      builderDraft: Object.assign({}, pageSettings.builderDraft || {}),
      sectionOrder: sectionOrderFrom(currentPreview).slice()
    };
  }

  /**
   * proposal 적용 = 기존 인라인 편집과 같은 경로. builderDraft(문구) / sectionOrder(섹션 순서)만
   * currentPreview에 반영하고 미리보기를 즉시 재렌더한 뒤, 발행 여부에 따라
   * persistTextChangesAfterApply가 "발행 필요" 표시(발행됨) 또는 pending 저장(미발행)을 한다.
   */
  function applyComposeChanges(items) {
    if (!currentPreview || !items.length) return;
    captureComposeSnapshot();
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {});
    let orderChanged = false;
    items.forEach((item) => {
      if (item.type === 'TEXT') {
        pageSettings.builderDraft[item.target] = item.textValue;
        if (academyComposeState) academyComposeState.textKeys.add(item.target);
      } else if (item.type === 'SECTION_ORDER') {
        pageSettings.sectionOrder = item.sectionOrder.slice();
        orderChanged = true;
        if (academyComposeState) academyComposeState.orderTouched = true;
      }
      if (academyComposeState) academyComposeState.applied.add(item.id);
    });
    currentPreview.pageSettings = pageSettings;
    if (orderChanged) currentPreview.sectionOrder = pageSettings.sectionOrder.slice();
    applyAcademyToLivePreview(currentPreview, 'sec-hero');
    persistTextChangesAfterApply({});
    refreshComposeCards();
  }

  function applyComposeProposalById(id) {
    if (!academyComposeState) return;
    const item = academyComposeState.proposals.find((proposal) => proposal.id === id);
    if (!item || !isApplicableComposeProposal(item) || academyComposeState.applied.has(id)) return;
    applyComposeChanges([item]);
  }

  function applyAllComposeProposals() {
    if (!academyComposeState) return;
    const pending = academyComposeState.proposals.filter((proposal) =>
      isApplicableComposeProposal(proposal)
      && proposal.safeToApply === true
      && !academyComposeState.applied.has(proposal.id));
    if (!pending.length) {
      toast('적용할 추천안이 없습니다.', 'fa-circle-info');
      return;
    }
    applyComposeChanges(pending);
  }

  /** 이 세션에서 적용한 변경만 스냅샷 기준으로 되돌린다(그 사이 사용자가 직접 한 편집은 보존). */
  function revertComposeChanges() {
    if (!academyComposeState || !academyComposeState.snapshot || !currentPreview) return;
    const snap = academyComposeState.snapshot;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    const draft = Object.assign({}, pageSettings.builderDraft || {});
    academyComposeState.textKeys.forEach((key) => {
      if (Object.prototype.hasOwnProperty.call(snap.builderDraft, key)) {
        draft[key] = snap.builderDraft[key];
      } else {
        delete draft[key];
      }
    });
    pageSettings.builderDraft = draft;
    if (academyComposeState.orderTouched) {
      pageSettings.sectionOrder = snap.sectionOrder.slice();
      currentPreview.sectionOrder = snap.sectionOrder.slice();
    }
    currentPreview.pageSettings = pageSettings;
    academyComposeState.applied.clear();
    academyComposeState.textKeys.clear();
    academyComposeState.orderTouched = false;
    academyComposeState.snapshot = null;
    applyAcademyToLivePreview(currentPreview, 'sec-hero');
    persistTextChangesAfterApply({});
    refreshComposeCards();
    toast('AI 추천 적용을 되돌렸습니다.', 'fa-rotate-left');
  }

  function refreshComposeCards() {
    if (!academyComposeState) return;
    const cardsEl = document.getElementById('builderComposeCards');
    const valid = academyComposeState.proposals.filter(isApplicableComposeProposal);
    if (cardsEl && valid.length) cardsEl.innerHTML = valid.map(composeCardHtml).join('');
    syncComposeToolbar();
  }

  function syncComposeToolbar() {
    const revert = document.getElementById('builderComposeRevertBtn');
    const tag = document.getElementById('builderComposeAppliedTag');
    const applyAll = document.getElementById('builderComposeApplyAllBtn');
    const appliedCount = academyComposeState ? academyComposeState.applied.size : 0;
    if (revert) revert.hidden = appliedCount === 0;
    if (tag) {
      tag.hidden = appliedCount === 0;
      tag.textContent = appliedCount ? `${appliedCount}개 적용됨 · 발행 전` : '';
    }
    const pending = academyComposeState
      ? academyComposeState.proposals.filter((proposal) =>
          isApplicableComposeProposal(proposal)
          && proposal.safeToApply === true
          && !academyComposeState.applied.has(proposal.id)).length
      : 0;
    if (applyAll) applyAll.disabled = pending === 0;
  }

  // ── AI Guided Academy Builder (Phase 5B) ─────────────────────────────────
  // "Academy 홈페이지 제작 전용 AI Guide". 기존 [AI로 다듬기](문장 편집자) / [AI로 Academy
  // 구성하기](전체 진단자)와 역할이 겹치지 않는 세 번째 기능 = 제작 진행자다.
  //
  // 절대 원칙(Phase 4 / 5A와 동일):
  //   1) Guide는 자동 Publish 하지 않는다. 자동 section visibility 변경도 안 한다. section 삭제 안 한다.
  //   2) 문구 "적용"은 사용자가 눌러야만, 기존 인라인 편집과 완전히 같은 경로
  //      (currentPreview.pageSettings.builderDraft / intro → applyAcademyToLivePreview →
  //       persistTextChangesAfterApply)로 반영된다. 별도 AI 저장 API가 없다.
  //   3) 문구 추천은 새 AI 아키텍처가 아니라 기존 파이프라인(Context→Prompt→Provider)을 재사용한
  //      POST /api/tutor/academy/ai/guide-suggestions 하나만 쓴다.
  //   4) 순수 로직(스텝 정의·세션 상태·진행률·후보 파싱)은 academy_builder_ai_guide.js가 갖는다.
  const AI_GUIDE = typeof window !== 'undefined' ? window.F2RAcademyAiGuide : null;
  // Phase 5B.1 — Bildi(빌디) Sidekick. Guide state를 정적 패널이 아니라 살아 움직이는 캐릭터의
  // 대화·행동으로 표현한다. 엔진(하늘/캐릭터/스크롤스토리/말풍선)은 academy_world.js가 소유한다.
  // 미로드/reduced-motion → 패널(#builderAiGuidePanel)로 안전하게 폴백(모든 step 동작 동일).
  const WORLD = typeof window !== 'undefined' ? window.F2RAcademyWorld : null;
  let aiGuideSession = null;
  let brandProposal = null;
  let brandUndoSnapshot = null;
  let designPresetUndoVisibility = null;
  let aiGuidePending = null; // { target, candidates: [] } — 현재 스텝의 마지막 추천 결과
  let aiGuideCandidateIndex = 0; // 추천안 캐러셀 — 한 번에 1개만 보여준다(§2). 말풍선 안에 스크롤을 쌓지 않는다.
  let aiGuideError = '';
  let aiGuideBusy = false;
  let aiGuideFocusTimer = null;
  let aiGuideExistingSteps = [];
  let bildi = null;        // { setState, moveToXY, react, ... }
  let bildiBubble = null;  // { say, moveToXY, el, body }
  let bildiSky = null;     // FRONT 날씨 연출(스테이지 vignette/파티클) { setScene, setProgress }
  let bildiWorldSky = null; // BACK 실제 하늘(#builderWorldSky) { setScene, setProgress }
  let bildiStory = null;   // scroll story controller
  let bildiStopTimer = null;
  let bildiSettleTimer = null;
  let bildiGuideTargetKey = null; // Guide가 "이 섹션을 보라"고 한 섹션 — 착지 시 POINT/TALK
  let lastBildiFlightPos = null;
  let aiGuideEnabled = true;      // UI visibility only — aiGuideSession과 절대 합치지 않는다.
  let aiGuidePanelOpen = false;   // enabled 상태 안의 OPEN / COLLAPSED

  // 섹션별 waypoint(Preview safe area 기준 프랙션). 좌우를 번갈아 두고 y를 흩어, 스크롤하면
  // Bildi의 화면 X/Y가 눈에 띄게 이동한다(§목표). 없는 섹션은 index 기반 교차 패턴으로 폴백.
  const BILDI_WAYPOINTS = {
    'sec-hero':         { x: 0.14, y: 0.24 },
    'sec-featured':     { x: 0.74, y: 0.46 },
    'sec-intro':        { x: 0.22, y: 0.58 },
    'sec-principal':    { x: 0.72, y: 0.28 },
    'sec-philosophy':   { x: 0.18, y: 0.52 },
    'sec-videos':       { x: 0.68, y: 0.62 },
    'sec-minilectures': { x: 0.30, y: 0.32 },
    'sec-lessons':      { x: 0.74, y: 0.50 },
    'sec-social':       { x: 0.22, y: 0.40 }
  };
  function bildiWaypoint(key, index, total) {
    if (BILDI_WAYPOINTS[key]) return BILDI_WAYPOINTS[key];
    return { x: index % 2 === 0 ? 0.20 : 0.72, y: 0.26 + (index % 3) * 0.16 };
  }

  function readAiGuideSession() {
    if (!AI_GUIDE) return null;
    try { return JSON.parse(sessionStorage.getItem(AI_GUIDE.SESSION_STORAGE_KEY) || 'null'); }
    catch (_) { return null; }
  }
  function writeAiGuideSession() {
    if (!AI_GUIDE || !aiGuideSession) return;
    try { sessionStorage.setItem(AI_GUIDE.SESSION_STORAGE_KEY, JSON.stringify(aiGuideSession)); }
    catch (_) { /* privacy 모드 등 — Guide는 저장 실패해도 계속 동작한다 */ }
  }
  function readAiGuideUiState() {
    if (!AI_GUIDE) return null;
    try {
      const raw = JSON.parse(sessionStorage.getItem(AI_GUIDE.PANEL_STATE_STORAGE_KEY) || 'null');
      if (!raw) return null;
      if (typeof raw.enabled !== 'boolean' && typeof raw.open === 'boolean') {
        return { enabled: true, panelOpen: raw.open };
      }
      return typeof raw.enabled === 'boolean'
        ? { enabled: raw.enabled, panelOpen: !!raw.panelOpen }
        : null;
    } catch (_) { return null; }
  }
  function writeAiGuideUiState() {
    if (!AI_GUIDE) return;
    try {
      sessionStorage.setItem(AI_GUIDE.PANEL_STATE_STORAGE_KEY, JSON.stringify({
        enabled: !!aiGuideEnabled,
        panelOpen: !!aiGuidePanelOpen
      }));
    } catch (_) { /* no-op */ }
  }
  function wideDesktopGuideDefault() {
    return !!(typeof window !== 'undefined' && window.matchMedia
      && window.matchMedia('(min-width: 1281px)').matches);
  }

  function wireAiGuide(preview) {
    if (!AI_GUIDE) return; // 모듈 미로드 시 조용히 비활성 — Builder 자체는 계속 동작한다.
    const panel = document.getElementById('builderAiGuidePanel');
    const toggle = document.getElementById('builderAiGuideToggle');
    if (!panel || !toggle || panel.dataset.wired === 'true') return;
    panel.dataset.wired = 'true';

    aiGuideSession = AI_GUIDE.createSession(readAiGuideSession());
    aiGuideExistingSteps = AI_GUIDE.stepsWithExistingContent(preview || currentPreview);
    let forcedNamingStart = false;
    // 이미 이름/주소가 확정된 Academy는 naming 온보딩을 건너뛴다(신규/미설정만 첫 단계).
    // 진행 중이던 사용자는 건드리지 않는다.
    if (typeof AI_GUIDE.isNamingComplete === 'function'
        && AI_GUIDE.isNamingComplete(preview || currentPreview)
        && aiGuideSession.currentStepId === 'naming'
        && aiGuideSession.completedStepIds.indexOf('naming') < 0
        && aiGuideSession.skippedStepIds.indexOf('naming') < 0
        && !aiGuideSession.onboarded) {
      aiGuideSession = AI_GUIDE.goToStep(AI_GUIDE.markCompleted(aiGuideSession, 'naming'), 'style');
      writeAiGuideSession();
    } else if (typeof AI_GUIDE.shouldStartAtNaming === 'function'
        && AI_GUIDE.shouldStartAtNaming(preview || currentPreview, aiGuideSession)) {
      // §7·§8 — 이름/주소 미설정인데 (구 세션 마이그레이션·academy 미스코프 등으로) 세션이
      // naming 을 지나쳐 있으면 STEP 1 로 되돌린다. CSS 강제 노출이 아니라 진행 상태 자체를 교정한다.
      aiGuideSession = AI_GUIDE.goToStep(aiGuideSession, 'naming');
      writeAiGuideSession();
      forcedNamingStart = true;
    }

    toggle.addEventListener('click', () => setAiGuideEnabled(!aiGuideEnabled));
    bindClick('builderAiGuideClose', () => setAiGuidePanelOpen(false));
    bindClick('builderBildiLauncher', () => setAiGuidePanelOpen(true));
    bindClick('builderAiGuideRestart', restartAiGuide);
    bindClick('builderAiGuidePrev', () => moveAiGuideStep('prev'));
    bindClick('builderAiGuideNext', () => moveAiGuideStep('next'));
    bindClick('builderAiGuideSkip', skipAiGuideStep);

    const steps = document.getElementById('builderAiGuideSteps');
    if (steps) steps.addEventListener('click', (event) => {
      const chip = event.target && event.target.closest ? event.target.closest('[data-guide-step]') : null;
      if (chip) goAiGuideStep(chip.dataset.guideStep);
    });
    const body = document.getElementById('builderAiGuideBody');
    if (body) {
      body.addEventListener('click', onAiGuideBodyClick);
      body.addEventListener('input', onAiGuideBodyInput);
    }

    mountBildiSidekick();

    // UI state는 Guide 진행 상태와 별도 보존한다. 넓은 desktop만 최초 OPEN, tablet/mobile은
    // 기본 COLLAPSED로 Preview 면적을 우선한다. naming 강제도 좁은 화면에서 자동 overlay하지 않는다.
    const savedUi = readAiGuideUiState();
    aiGuideEnabled = savedUi ? savedUi.enabled : true;
    aiGuidePanelOpen = savedUi ? savedUi.panelOpen : wideDesktopGuideDefault();
    if (!bildi && aiGuideEnabled) aiGuidePanelOpen = true;
    if (forcedNamingStart && wideDesktopGuideDefault()) aiGuidePanelOpen = true;
    setAiGuideEnabled(aiGuideEnabled, { skipPersist: savedUi == null, panelOpen: aiGuidePanelOpen });
    renderAiGuide();
    const step = currentAiGuideStep();
    syncBildiSceneToStep();
    if (bildiActive() && step.sectionKey) focusAiGuideSection(step.sectionKey);
  }

  /** Bildi 캐릭터를 Preview 밖 collapsed launcher에 mount. WORLD 미로드 시 panel만 동작한다. */
  function mountBildiSidekick() {
    if (!WORLD || bildi) return;
    const stage = document.getElementById('builderBildiStage');
    if (!stage) return;
    try {
      // World 배경은 기존 Preview 장식으로 유지하되, Guide 대화와 캐릭터는 Preview에 올리지 않는다.
      const worldHost = document.getElementById('builderWorldSky');
      bildiWorldSky = (worldHost && typeof WORLD.createSky === 'function')
        ? WORLD.createSky(worldHost, { className: 'world-sky-back' }) : null;
      bildiSky = null;
      bildi = typeof WORLD.createBildi === 'function' ? WORLD.createBildi(stage) : null;
      bildiBubble = null;
    } catch (_) {
      bildi = null; bildiBubble = null; bildiSky = null; bildiWorldSky = null;
      return;
    }
   }

  function bildiActive() {
    return !!(bildi && aiGuideEnabled);
  }

  /**
   * #pageContent 스크롤 = 세계 진행. Bildi는 섹션 waypoint 사이를 실제로 날아 이동하고(§목표),
   * 하늘 scene은 progress에 따라 바뀐다. 이 둘은 별개로 계산한다.
   */
  function attachBildiStory() {
    if (!WORLD || !bildi || bildiStory || typeof WORLD.createScrollStory !== 'function') return;
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    bildiStory = WORLD.createScrollStory({
      scrollEl: pageContent,
      window: typeof window !== 'undefined' ? window : undefined,
      sections: () => orderedBildiSectionEls(),
      onUpdate: (state) => {
        // Environment scene = scroll progress (viewport 고정, 상태만 변화)
        if (bildiSky && typeof bildiSky.setProgress === 'function') bildiSky.setProgress(state.progress);
        if (bildiWorldSky && typeof bildiWorldSky.setProgress === 'function') bildiWorldSky.setProgress(state.progress);
        // Bildi position = scroll 에 따라 실제 이동
        updateBildiFlight(state);
        if (bildiSky && typeof bildi.react === 'function') {
          bildi.react(WORLD.reactionForScene ? WORLD.reactionForScene(bildiSky.getScene && bildiSky.getScene()) : null);
        }
      }
    });
    // 레이아웃이 아직 안 잡혀 safe area 계산이 실패할 수 있으므로 초기 위치를 몇 번 재시도한다.
    refreshBildiFlight();
    var w = typeof window !== 'undefined' ? window : null;
    if (w && w.requestAnimationFrame) w.requestAnimationFrame(refreshBildiFlight);
    setTimeout(refreshBildiFlight, 120);
    setTimeout(refreshBildiFlight, 400);
  }

  function detachBildiStory() {
    if (bildiStory && typeof bildiStory.detach === 'function') bildiStory.detach();
    bildiStory = null;
    if (bildiStopTimer) { clearTimeout(bildiStopTimer); bildiStopTimer = null; }
    if (bildiSettleTimer) { clearTimeout(bildiSettleTimer); bildiSettleTimer = null; }
  }

  function orderedBildiSectionEls() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return [];
    return Array.prototype.slice.call(pageContent.querySelectorAll('[data-builder-section-key]'));
  }
  function orderedBildiSectionKeys() {
    return orderedBildiSectionEls().map((el) => el.getAttribute('data-builder-section-key') || '');
  }

  /**
   * Bildi가 이동할 수 있는 안전 영역(stage 좌표계 px). Preview 콘텐츠 뷰포트(#pageContent) 안쪽으로
   * 제한하고, sticky 리본과 브라우저 목업 바 아래로 top inset을 둔다 — 상단 UI에 겹치거나 잘리지 않는다.
   * z-index로 해결하지 않고 좌표 자체를 유효 콘텐츠 영역 안에 둔다.
   */
  function bildiSafeArea() {
    const stage = document.getElementById('builderBildiStage');
    const pc = document.getElementById('pageContent');
    if (!stage || !pc || typeof stage.getBoundingClientRect !== 'function' || typeof pc.getBoundingClientRect !== 'function') return null;
    const sR = stage.getBoundingClientRect();
    const pR = pc.getBoundingClientRect();
    if (!pR.width || !pR.height) return null;
    const ribbon = pc.querySelector('.builder-public-preview-ribbon');
    const ribbonBottom = ribbon && typeof ribbon.getBoundingClientRect === 'function'
      ? ribbon.getBoundingClientRect().bottom : pR.top;
    const PAD = 14;
    return {
      x0: (pR.left - sR.left) + PAD,
      y0: (Math.max(pR.top, ribbonBottom) - sR.top) + PAD,
      x1: (pR.right - sR.left) - PAD,
      y1: (pR.bottom - sR.top) - PAD
    };
  }

  function bildiSize() {
    const el = bildi && bildi.el;
    const w = el && el.offsetWidth ? el.offsetWidth : 84;
    const h = el && el.offsetHeight ? el.offsetHeight : 84;
    return { w: w, h: h };
  }

  /** 현재 스크롤 상태를 합성해 flight 위치/상태를 다시 계산(DOM 교체·스텝 이동·초기 진입 시). */
  function refreshBildiFlight() {
    if (!bildi || !bildi.el) return;
    const pc = document.getElementById('pageContent');
    let progress = 0;
    let direction = 'none';
    if (pc && typeof pc.scrollTop === 'number') {
      const extent = Math.max(1, (pc.scrollHeight || 0) - (pc.clientHeight || 0));
      progress = Math.max(0, Math.min(1, pc.scrollTop / extent));
    }
    updateBildiFlight({ progress: progress, direction: direction, speed: 'slow', activeSection: null });
  }

  /** onUpdate 핸들러: scroll progress → Bildi 화면 위치(waypoint 보간) + 비행 상태머신. */
  function updateBildiFlight(story) {
    if (!bildi || !bildi.el || typeof bildi.moveToXY !== 'function') return;
    const safe = bildiSafeArea();
    if (!safe || safe.x1 <= safe.x0 || safe.y1 <= safe.y0) return;
    const keys = orderedBildiSectionKeys();
    const n = Math.max(1, keys.length);
    const waypoints = keys.length
      ? keys.map((k, i) => bildiWaypoint(k, i, n))
      : [bildiWaypoint('sec-hero', 0, 1)];
    const size = bildiSize();
    const reduced = typeof bildi.isReduced === 'function' && bildi.isReduced();
    const pos = WORLD.flightPosition({
      progress: story.progress,
      count: waypoints.length,
      waypoints: waypoints,
      safe: safe,
      size: size,
      arc: reduced ? 0 : 0.12
    });
    bildi.moveToXY(pos.x, pos.y);
    positionBildiBubble(pos, safe, size);
    if (typeof bildi.face === 'function') bildi.face(story.direction === 'up' ? 'up' : 'down');
    updateBildiFlightState(pos, story);
    lastBildiFlightPos = pos;
  }

  /** 이동/정지에 따라 fly ↔ land ↔ idle/point/talk 를 전이한다(상태머신은 academy_world.js). */
  function updateBildiFlightState(pos, story) {
    const scrolling = story && story.direction && story.direction !== 'none';
    const moving = scrolling || (pos && pos.moving);
    if (moving) {
      if (bildiSettleTimer) { clearTimeout(bildiSettleTimer); bildiSettleTimer = null; }
      const st = bildi.getState && bildi.getState();
      if (st !== 'fly' && st !== 'wings_open') {
        if (typeof bildi.dispatch === 'function') { bildi.dispatch('scrollStart'); bildi.dispatch('move'); }
        else if (typeof bildi.setState === 'function') bildi.setState('fly');
      } else if (st === 'wings_open' && typeof bildi.dispatch === 'function') {
        bildi.dispatch('move');
      }
    }
    if (bildiStopTimer) clearTimeout(bildiStopTimer);
    bildiStopTimer = setTimeout(onBildiScrollStop, 180);
  }

  function onBildiScrollStop() {
    bildiStopTimer = null;
    if (!bildi) return;
    const st = bildi.getState && bildi.getState();
    if (st === 'fly' || st === 'wings_open') {
      if (typeof bildi.dispatch === 'function') bildi.dispatch('arrive'); // → land
      else if (typeof bildi.setState === 'function') bildi.setState('land');
    }
    if (bildiSettleTimer) clearTimeout(bildiSettleTimer);
    bildiSettleTimer = setTimeout(() => {
      bildiSettleTimer = null;
      if (!bildi || !bildi.getState) return;
      const atGuide = bildiGuideTargetKey && bildiNearestSectionKey() === bildiGuideTargetKey;
      if (typeof bildi.setState === 'function') bildi.setState(atGuide ? 'point' : 'idle');
      if (atGuide) {
        setTimeout(() => {
          if (bildi && bildi.getState && bildi.getState() === 'point' && typeof bildi.setState === 'function') {
            bildi.setState('talk');
          }
        }, 380);
      }
    }, 320);
  }

  /** 현재 pageContent 뷰포트 중앙(약 42%)에 가장 가까운 섹션 key. */
  function bildiNearestSectionKey() {
    const pc = document.getElementById('pageContent');
    if (!pc || typeof pc.getBoundingClientRect !== 'function') return null;
    const pR = pc.getBoundingClientRect();
    const focusY = pR.top + pR.height * 0.42;
    let bestKey = null;
    let bestDist = Infinity;
    orderedBildiSectionEls().forEach((el) => {
      if (typeof el.getBoundingClientRect !== 'function') return;
      const r = el.getBoundingClientRect();
      const mid = r.top + r.height / 2;
      const d = Math.abs(mid - focusY);
      if (d < bestDist) { bestDist = d; bestKey = el.getAttribute('data-builder-section-key'); }
    });
    return bestKey;
  }

  /**
   * 말풍선을 Bildi 옆에 두되 safe area(=뷰포트 콘텐츠 영역)를 벗어나면 반대쪽/안쪽으로 당긴다.
   * §2·§4 — 말풍선의 실제 렌더 높이를 재서 아래가 프레임 밖으로 내려가 잘리지 않게 y 를 clamp 한다.
   * 말풍선이 safe area 보다 크면 위에 붙이고(스크롤은 CSS max-height 가 담당), 그 외에는 통째로 안에 넣는다.
   */
  function positionBildiBubble(pos, safe, size) {
    if (!bildiBubble || typeof bildiBubble.moveToXY !== 'function') return;
    const el = bildiBubble.el;
    const BUBBLE_W = (el && el.offsetWidth) ? el.offsetWidth : 264;
    const BUBBLE_H = (el && el.offsetHeight) ? el.offsetHeight : 160;
    const GAP = 12;
    let bx = pos.x + size.w + GAP;
    let side = 'right';
    if (bx + BUBBLE_W > safe.x1) { bx = pos.x - GAP - BUBBLE_W; side = 'left'; }
    if (bx < safe.x0) bx = safe.x0;
    if (bx + BUBBLE_W > safe.x1) bx = Math.max(safe.x0, safe.x1 - BUBBLE_W);
    const safeH = Math.max(0, safe.y1 - safe.y0);
    let by = pos.y - 8;
    if (BUBBLE_H >= safeH) {
      by = safe.y0;
    } else {
      by = Math.max(safe.y0, Math.min(by, safe.y1 - BUBBLE_H));
    }
    if (el && typeof el.setAttribute === 'function') {
      el.setAttribute('data-side', side === 'left' ? 'right' : 'left');
    }
    bildiBubble.moveToXY(bx, by);
  }

  function bindClick(id, handler) {
    const el = document.getElementById(id);
    if (el) el.addEventListener('click', handler);
  }

  /** ON/OFF. OFF는 표시만 숨기며 aiGuideSession/currentGuideStep을 삭제하지 않는다. */
  function setAiGuideEnabled(enabled, options) {
    const opts = options || {};
    const toggle = document.getElementById('builderAiGuideToggle');
    const label = document.getElementById('builderAiGuideToggleLabel');
    const launcher = document.getElementById('builderBildiLauncher');
    aiGuideEnabled = !!enabled;
    if (toggle) {
      toggle.setAttribute('aria-pressed', aiGuideEnabled ? 'true' : 'false');
      toggle.setAttribute('aria-label', aiGuideEnabled ? '빌디 끄기' : '빌디 켜기');
    }
    if (label) label.textContent = aiGuideEnabled ? '빌디 ON' : '빌디 OFF';
    if (document.body) document.body.classList.toggle('bildi-enabled', aiGuideEnabled);
    if (!aiGuideEnabled) aiGuidePanelOpen = false;
    else if (typeof opts.panelOpen === 'boolean') aiGuidePanelOpen = opts.panelOpen;
    setAiGuidePanelOpen(aiGuidePanelOpen, { skipPersist: true });
    if (launcher) launcher.hidden = !aiGuideEnabled || aiGuidePanelOpen;
    if (!opts.skipPersist) writeAiGuideUiState();
  }

  /** ON 안의 OPEN/COLLAPSED. 패널을 접어도 Guide 데이터와 현재 step은 그대로다. */
  function setAiGuidePanelOpen(open, options) {
    const opts = options || {};
    const panel = document.getElementById('builderAiGuidePanel');
    const launcher = document.getElementById('builderBildiLauncher');
    if (!panel) return;
    aiGuidePanelOpen = !!open && aiGuideEnabled;
    panel.classList.toggle('is-open', aiGuidePanelOpen);
    panel.setAttribute('aria-hidden', aiGuidePanelOpen ? 'false' : 'true');
    if (launcher) {
      launcher.hidden = !aiGuideEnabled || aiGuidePanelOpen;
      launcher.setAttribute('aria-expanded', aiGuidePanelOpen ? 'true' : 'false');
    }
    if (document.body) {
      document.body.classList.toggle('bildi-active', aiGuideEnabled && !!bildi);
      document.body.classList.toggle('ai-guide-open', aiGuidePanelOpen);
    }
    if (aiGuidePanelOpen) {
      renderAiGuide();
      const step = currentAiGuideStep();
      if (!opts.skipFocus && step.sectionKey) focusAiGuideSection(step.sectionKey);
    } else {
      clearAiGuideSectionFocus();
    }
    if (!opts.skipPersist) writeAiGuideUiState();
  }

  /** 기존 내부 호출/테스트 호환 alias. open=false는 OFF가 아니라 COLLAPSED다. */
  function setAiGuideOpen(open, options) {
    const opts = options || {};
    if (open && !aiGuideEnabled) setAiGuideEnabled(true, { skipPersist: true });
    setAiGuidePanelOpen(!!open, opts);
  }

  function currentAiGuideStep() {
    return AI_GUIDE.stepById(aiGuideSession && aiGuideSession.currentStepId) || AI_GUIDE.STEPS[0];
  }

  function restartAiGuide() {
    aiGuideSession = AI_GUIDE.createSession(null);
    aiGuidePending = null;
    aiGuideCandidateIndex = 0;
    aiGuideError = '';
    writeAiGuideSession();
    renderAiGuide();
    const step = currentAiGuideStep();
    if (step.sectionKey) focusAiGuideSection(step.sectionKey);
    toast('가이드를 처음부터 다시 시작합니다.', 'fa-compass');
  }

  function goAiGuideStep(stepId) {
    aiGuideSession = AI_GUIDE.goToStep(aiGuideSession, stepId);
    aiGuidePending = null;
    aiGuideCandidateIndex = 0;
    aiGuideError = '';
    writeAiGuideSession();
    renderAiGuide();
    const step = currentAiGuideStep();
    syncBildiSceneToStep();
    if (step.sectionKey) focusAiGuideSection(step.sectionKey);
  }

  /** step이 암시하는 World scene으로 하늘을 옮긴다(섹션이 없는 finish step 포함). */
  function syncBildiSceneToStep() {
    if (!AI_GUIDE || typeof AI_GUIDE.sceneForStep !== 'function') return;
    const step = currentAiGuideStep();
    const scene = AI_GUIDE.sceneForStep(step && step.id);
    if (bildiSky && typeof bildiSky.setScene === 'function') bildiSky.setScene(scene);
    if (bildiWorldSky && typeof bildiWorldSky.setScene === 'function') bildiWorldSky.setScene(scene);
  }

  function moveAiGuideStep(direction) {
    const current = currentAiGuideStep();
    const targetId = direction === 'prev'
      ? AI_GUIDE.prevStepId(current.id)
      : AI_GUIDE.nextStepId(current.id);
    if (!targetId) {
      if (direction === 'next') {
        aiGuideSession = AI_GUIDE.markCompleted(aiGuideSession, current.id);
        writeAiGuideSession();
        toast('가이드를 모두 마쳤습니다. 상단 발행 버튼으로 저장하세요.', 'fa-flag-checkered');
        renderAiGuide();
      }
      return;
    }
    if (direction === 'next') {
      aiGuideSession = AI_GUIDE.markCompleted(aiGuideSession, current.id);
    }
    goAiGuideStep(targetId);
  }

  function skipAiGuideStep() {
    const current = currentAiGuideStep();
    aiGuideSession = AI_GUIDE.markSkipped(aiGuideSession, current.id);
    const nextId = AI_GUIDE.nextStepId(current.id);
    aiGuidePending = null;
    aiGuideCandidateIndex = 0;
    if (nextId) {
      goAiGuideStep(nextId);
    } else {
      writeAiGuideSession();
      renderAiGuide();
    }
  }

  function clearAiGuideSectionFocus() {
    if (aiGuideFocusTimer) { clearTimeout(aiGuideFocusTimer); aiGuideFocusTimer = null; }
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    pageContent.querySelectorAll('.ai-guide-section-focus')
      .forEach((el) => el.classList.remove('ai-guide-section-focus'));
  }

  /**
   * Guide가 "이제 이 영역입니다"라고 하면: 프리뷰를 그 섹션으로 스크롤하고(→ scroll story가
   * Bildi를 waypoint 사이로 실제 비행시킨다), 도착 후 POINT/TALK 하도록 목표 섹션을 표시한다.
   */
  function focusAiGuideSection(sectionKey) {
    if (!sectionKey) return;
    clearAiGuideSectionFocus();
    bildiGuideTargetKey = sectionKey;
    syncBildiSceneToStep();
    scrollPreviewToSection(sectionKey);
    // 스크롤이 짧아 이벤트가 거의 안 나오는 경우를 대비해 한 번 직접 갱신한다(스크롤이 나면
    // scroll story가 이어받아 부드럽게 비행시킨다).
    setTimeout(refreshBildiFlight, 60);
    setTimeout(onBildiScrollStop, 520);
    const target = previewSectionTarget(sectionKey);
    if (!target) return;
    target.classList.add('ai-guide-section-focus');
    aiGuideFocusTimer = setTimeout(() => {
      target.classList.remove('ai-guide-section-focus');
      aiGuideFocusTimer = null;
    }, 2600);
  }

  function clampNumber(n, min, max) {
    n = Number(n) || 0;
    return n < min ? min : (n > max ? max : n);
  }

  function onAiGuideBodyClick(event) {
    const el = event.target && event.target.closest ? event.target.closest('[data-guide-action]') : null;
    if (!el) return;
    const action = el.dataset.guideAction;
    if (action === 'prev') {
      moveAiGuideStep('prev');
    } else if (action === 'next') {
      moveAiGuideStep('next');
    } else if (action === 'skip') {
      skipAiGuideStep();
    } else if (action === 'panel') {
      setAiGuideOpen(true, { panel: true });
    } else if (action === 'naming') {
      applyAiGuideNaming();
    } else if (action === 'style') {
      selectAiGuideStyle(el.dataset.guideValue || '');
    } else if (action === 'brand-preview') {
      previewBrandProposal();
    } else if (action === 'brand-apply') {
      applyBrandProposal();
    } else if (action === 'brand-other') {
      cycleBrandProposal();
    } else if (action === 'brand-undo') {
      undoBrandDesign();
    } else if (action === 'recommend') {
      requestAiGuideRecommendations();
    } else if (action === 'apply') {
      applyAiGuideCandidate(Number(el.dataset.guideIndex));
    } else if (action === 'cand-prev') {
      stepAiGuideCandidate(-1);
    } else if (action === 'cand-next') {
      stepAiGuideCandidate(1);
    } else if (action === 'manual-focus') {
      const step = currentAiGuideStep();
      setAiGuideOpen(true);
      focusAiGuideSection(step.sectionKey);
      toast('미리보기에서 해당 영역을 직접 클릭해 수정할 수 있습니다.', 'fa-pen');
    } else if (action === 'open-compose') {
      const trigger = document.getElementById('builderComposeTrigger');
      if (trigger) trigger.click();
    } else if (action === 'keep-existing') {
      const step = currentAiGuideStep();
      aiGuideSession = AI_GUIDE.markCompleted(aiGuideSession, step.id);
      const nextId = AI_GUIDE.nextStepId(step.id);
      if (nextId) goAiGuideStep(nextId); else { writeAiGuideSession(); renderAiGuide(); }
    } else if (action === 'review-existing') {
      const step = currentAiGuideStep();
      aiGuideExistingSteps = aiGuideExistingSteps.filter((id) => id !== step.id);
      renderAiGuide();
    }
  }

  function selectAiGuideStyle(value) {
    const input = document.getElementById('builderAiGuideStyleInput');
    const typed = input ? String(input.value || '').trim() : '';
    const chosen = value || typed;
    if (!chosen) { toast('원하는 느낌을 골라주시거나 직접 적어주세요.', 'fa-circle-info'); return; }
    aiGuideSession.desiredStyle = chosen.slice(0, 200);
    aiGuideSession.tone = chosen.slice(0, 200);
    brandProposal = BRAND ? BRAND.recommendation(chosen) : null;
    writeAiGuideSession();
    renderAiGuide();
  }

  function currentBrandDraft() {
    return Object.assign({}, currentPreview && currentPreview.pageSettings && currentPreview.pageSettings.builderDraft || {});
  }
  function replaceBrandDraft(next) {
    if (!currentPreview) return;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, next || {});
    currentPreview.pageSettings = pageSettings;
  }
  function previewBrandProposal() {
    if (!BRAND || !brandProposal || !currentPreview) return;
    const draft = BRAND.presetDraft(brandProposal.presetId, currentBrandDraft());
    const root = document.querySelector('#pageContent #publicView');
    if (root) BRAND.apply(root, draft);
    toast('모바일 미리보기에 임시 적용했습니다. 적용을 눌러야 설정에 반영됩니다.', 'fa-mobile-screen');
  }
  function applyBrandProposal() {
    if (!BRAND || !brandProposal || !currentPreview) return;
    brandUndoSnapshot = currentBrandDraft();
    replaceBrandDraft(BRAND.presetDraft(brandProposal.presetId, currentBrandDraft()));
    renderPublicPreview(currentPreview);
    persistTextChangesAfterApply({});
    aiGuideSession = AI_GUIDE.markCompleted(aiGuideSession, 'style');
    writeAiGuideSession();
    brandProposal = null;
    toast('Typography preset을 반영했습니다. 상단 발행 버튼으로 저장하세요.', 'fa-wand-magic-sparkles');
    const nextId = AI_GUIDE.nextStepId('style');
    if (nextId) goAiGuideStep(nextId); else renderAiGuide();
  }
  function cycleBrandProposal() {
    if (!BRAND || !brandProposal) return;
    const ids = Object.keys(BRAND.PRESETS);
    const at = ids.indexOf(brandProposal.presetId);
    const id = ids[(at + 1) % ids.length];
    brandProposal = { presetId:id, label:BRAND.PRESETS[id].label, description:BRAND.PRESETS[id].description, message:BRAND.PRESETS[id].label + ' 스타일을 제안합니다.' };
    renderAiGuide();
  }
  function undoBrandDesign() {
    if (!brandUndoSnapshot || !currentPreview) return;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, brandUndoSnapshot);
    currentPreview.pageSettings = pageSettings;
    brandUndoSnapshot = null;
    if (designPresetUndoVisibility) {
      currentPreview.sectionVisibility = normalizeSectionVisibility(designPresetUndoVisibility);
      designPresetUndoVisibility = null;
      applySectionVisibilityState(currentPreview.sectionVisibility);
      persistPendingSectionVisibility(currentPreview.sectionVisibility);
    }
    renderPublicPreview(currentPreview);
    persistTextChangesAfterApply({});
    renderAiGuide();
  }
  function brandProposalHtml() {
    if (!brandProposal) return '';
    return '<div class="ai-guide-candidate"><p><b>' + escapeHtml(brandProposal.label) + '</b><br>' + escapeHtml(brandProposal.message) + '<br><small>' + escapeHtml(brandProposal.description) + '</small></p>'
      + '<div class="ai-guide-actions"><button type="button" class="ai-guide-btn ai-guide-btn-primary" data-guide-action="brand-apply">스타일 적용</button>'
      + '<button type="button" class="ai-guide-btn ai-guide-btn-ghost" data-guide-action="brand-preview">미리보기</button>'
      + '<button type="button" class="ai-guide-btn ai-guide-btn-ghost" data-guide-action="brand-other">다른 느낌 보기</button></div></div>';
  }

  async function requestAiGuideRecommendations() {
    if (aiGuideBusy) return;
    const step = currentAiGuideStep();
    if (!step.target || !currentPreview) return;
    const answerInput = document.getElementById('builderAiGuideAnswer');
    const answer = answerInput ? String(answerInput.value || '').trim() : '';
    if (step.id !== 'style') aiGuideSession.strongestPoint = answer.slice(0, 400);
    writeAiGuideSession();
    aiGuideBusy = true;
    aiGuidePending = null;
    aiGuideError = '';
    renderAiGuide();
    try {
      const res = await fetch('/api/tutor/academy/ai/guide-suggestions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({
          target: step.target,
          answer: answer,
          style: aiGuideSession.desiredStyle || ''
        })
      });
      if (!res.ok) {
        aiGuideRenderError(await aiErrorMessage(res, 'AI 추천을 만들지 못했습니다.'));
        return;
      }
      const payload = await res.json();
      const list = AI_GUIDE.parseCandidates(payload && payload.candidates, {
        maxLength: step.long ? 220 : 70,
        limit: 3
      });
      if (!list.length) {
        aiGuideRenderError('추천 문구를 이해하지 못했습니다. 다시 시도해 주세요.');
        return;
      }
      aiGuidePending = { target: step.target, candidates: list };
      aiGuideCandidateIndex = 0;
      renderAiGuide();
    } catch (_) {
      aiGuideRenderError('네트워크 연결이 끊겼습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      aiGuideBusy = false;
    }
  }

  function aiGuideRenderError(message) {
    aiGuideBusy = false;
    aiGuideError = message || 'AI 추천을 만들지 못했습니다.';
    renderAiGuide();
  }

  /**
   * 후보 "적용" = 기존 인라인 편집과 완전히 같은 경로. builderDraft(또는 top-level intro)만
   * currentPreview에 반영하고 미리보기를 재렌더한 뒤, 발행 여부에 따라 persistTextChangesAfterApply가
   * "발행 필요" 안내 / pending 저장으로 갈린다. Publish는 절대 자동 실행하지 않는다.
   */
  function applyAiGuideCandidate(index) {
    if (!aiGuidePending || !currentPreview) return;
    const value = aiGuidePending.candidates[index];
    const target = aiGuidePending.target;
    if (!value || !target) return;
    // 서버 allowlist와 별개로 프론트에서도 target을 한 번 더 확인한다(방어적 이중화).
    if (target !== 'intro' && !AI_GUIDE.isGuideTextTarget(target)) {
      toast('이 영역에는 자동 적용할 수 없습니다.', 'fa-triangle-exclamation');
      return;
    }
    const changed = target === 'intro'
      ? setGuideInlineText('intro', value)
      : setGuideDraftText(target, value);
    if (!changed) {
      toast('이미 같은 문구입니다.', 'fa-circle-info');
      return;
    }
    const step = currentAiGuideStep();
    applyAcademyToLivePreview(currentPreview, step.sectionKey || 'sec-hero');
    persistTextChangesAfterApply({});
    aiGuideSession = AI_GUIDE.markCompleted(aiGuideSession, step.id);
    writeAiGuideSession();
    aiGuideExistingSteps = AI_GUIDE.stepsWithExistingContent(currentPreview);
    aiGuidePending = null;
    aiGuideCandidateIndex = 0;
    focusAiGuideSection(step.sectionKey);
    toast('추천 문구를 미리보기에 반영했습니다. 상단 발행 버튼으로 저장하세요.', 'fa-wand-magic-sparkles');
    renderAiGuide();
  }

  function setGuideInlineText(field, value) {
    if (String(currentPreview[field] == null ? '' : currentPreview[field]) === value) return false;
    currentPreview[field] = value;
    return true;
  }

  function setGuideDraftText(key, value) {
    if (draftText(currentPreview, key, '') === value) return false;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, { [key]: value });
    currentPreview.pageSettings = pageSettings;
    return true;
  }

  function renderAiGuide() {
    if (!AI_GUIDE || !aiGuideSession) return;
    const body = document.getElementById('builderAiGuideBody');
    const stepsEl = document.getElementById('builderAiGuideSteps');
    if (!body || !stepsEl) return;
    const step = currentAiGuideStep();
    const info = AI_GUIDE.progress(aiGuideSession);

    const fill = document.getElementById('builderAiGuideProgressFill');
    if (fill) fill.style.width = Math.round((info.doneCount / info.total) * 100) + '%';
    setText('#builderAiGuideProgressText', info.position + ' / ' + info.total);
    setText('#builderAiGuideProgressStep', step.title);

    stepsEl.innerHTML = AI_GUIDE.STEPS.map((s) => {
      let cls = 'ai-guide-step-chip';
      if (s.id === step.id) cls += ' is-current';
      else if (aiGuideSession.completedStepIds.indexOf(s.id) >= 0) cls += ' is-done';
      else if (aiGuideSession.skippedStepIds.indexOf(s.id) >= 0) cls += ' is-skipped';
      return `<button type="button" class="${cls}" data-guide-step="${escapeAttr(s.id)}">${escapeHtml(s.title)}</button>`;
    }).join('');

    const onboardingBubble = aiGuideSession.onboarded ? '' :
      `<div class="ai-guide-bubble">Academy 홈페이지를 함께 만들어 보겠습니다. 완성된 Academy처럼 첫 화면 → 원장 소개 → 교육철학 → 프로그램 → 수업 콘텐츠 순서로 구성할 수 있습니다. 왼쪽 미리보기를 보면서 한 단계씩 진행해요.</div>`;
    if (!aiGuideSession.onboarded) {
      aiGuideSession.onboarded = true;
      writeAiGuideSession();
    }

    const stepInner = guideStepInnerHtml(step);
     body.innerHTML = onboardingBubble
      + `<div class="ai-guide-step-title">STEP ${info.position} · ${escapeHtml(step.title)}</div>`
      + `<div class="ai-guide-bubble">${escapeHtml(step.guidance)}</div>`
      + stepInner;
    renderBildiBubble(step, info, stepInner);
    // 스크롤/강조는 스텝이 바뀔 때만 한다(renderAiGuide는 타이핑·로딩 토글로도 불려서,
    // 매번 미리보기를 스크롤하면 편집을 방해한다).
  }

  /** style/text/guidance/finish + busy/error 공통 마크업(패널·Bildi 말풍선이 같이 쓴다). */
  function guideStepInnerHtml(step) {
    let html = '';
    if (step.kind === 'naming') {
      html += renderAiGuideNamingStep();
    } else if (step.kind === 'style') {
      html += renderAiGuideStyleStep();
    } else if (step.kind === 'text') {
      html += renderAiGuideTextStep(step);
    } else if (step.kind === 'guidance') {
      html += `<div class="ai-guide-actions">`
        + `<button type="button" class="ai-guide-btn ai-guide-btn-ghost" data-guide-action="manual-focus">이 영역 보기</button>`
        + `</div><p class="ai-guide-note">이 영역은 전용 관리 화면에서 편집합니다. 준비되면 [다음]으로 넘어가세요.</p>`;
    } else if (step.kind === 'finish') {
      html += `<div class="ai-guide-actions">`
        + `<button type="button" class="ai-guide-btn ai-guide-btn-primary" data-guide-action="open-compose">AI로 Academy 전체 점검</button>`
        + `</div><p class="ai-guide-note">전체 진단은 기존 [AI로 Academy 구성하기] 기능을 그대로 사용합니다. 발행은 상단 [발행] 버튼으로 직접 하셔야 합니다.</p>`;
    }
    if (aiGuideBusy) html += `<div class="ai-guide-loading">AI가 문구를 만들고 있습니다…</div>`;
    if (aiGuideError) html += `<div class="ai-guide-error">${escapeHtml(aiGuideError)}</div>`;
    return html;
  }

  /** Preview overlay 말풍선은 사용하지 않는다. 모든 Guide 대화/입력은 side panel에서 렌더한다. */
  function renderBildiBubble(step, info, stepInner) {
    syncBildiSceneToStep();
    if (bildiBubble && typeof bildiBubble.hide === 'function') bildiBubble.hide();
    if (bildi && typeof bildi.setState === 'function') bildi.setState(aiGuideBusy ? 'think' : 'idle');
  }

  /**
   * STEP 1 (신규 Academy 온보딩): 학원명 + 홈페이지 주소(handle). 별도 저장 기능을 새로 만들지
   * 않고 기존 name 인라인 편집 + handle 편집 경로(/handle-available, persistPendingHandle,
   * dirty 플래그)를 그대로 재사용한다. 발행 자동 실행 없음.
   */
  function renderAiGuideNamingStep() {
    const name = (currentPreview && currentPreview.name) || '';
    const rawHandle = normalizeHandle(currentPreview && currentPreview.handle) || '';
    // 임시 handle(academy-xxxxxx)은 비워서 사용자가 직접 정하게 유도한다.
    const handle = /^academy-[a-z0-9]{6,24}$/.test(rawHandle) ? '' : rawHandle;
    const previewHost = handle || 'apgujeong-eduholic';
    return `<div class="ai-guide-question">${escapeHtml(currentAiGuideStep().question)}</div>`
      + `<label class="ai-guide-field-label" for="builderAiGuideName">학원명</label>`
      + `<input type="text" id="builderAiGuideName" class="ai-guide-input" style="min-height:auto" maxlength="100" placeholder="예: 압구정에듀홀릭학원" value="${escapeAttr(name)}">`
      + `<label class="ai-guide-field-label" for="builderAiGuideHandle">홈페이지 주소</label>`
      + `<div class="ai-guide-handle-row">`
      +   `<input type="text" id="builderAiGuideHandle" class="ai-guide-input" style="min-height:auto" maxlength="30" inputmode="url" autocomplete="off" spellcheck="false" placeholder="apgujeong-eduholic" value="${escapeAttr(handle)}">`
      +   `<span class="ai-guide-handle-suffix">.feel2real.co.kr</span>`
      + `</div>`
      + `<p class="ai-guide-handle-preview">예: <b id="builderAiGuideHandlePreview">${escapeHtml(previewHost + '.feel2real.co.kr')}</b></p>`
      + `<p class="ai-guide-handle-status" id="builderAiGuideHandleStatus" hidden></p>`
      + `<div class="ai-guide-actions">`
      +   `<button type="button" class="ai-guide-btn ai-guide-btn-primary" data-guide-action="naming"${aiGuideBusy ? ' disabled' : ''}>이 이름과 주소로 시작하기</button>`
      + `</div>`
      + `<p class="ai-guide-note">주소는 소문자·숫자·하이픈 3~30자입니다. 발행 전까지는 언제든 바꿀 수 있어요.</p>`;
  }

  function setAiGuideNamingStatus(message, tone) {
    const el = document.getElementById('builderAiGuideHandleStatus');
    if (!el) return;
    el.textContent = message || '';
    el.hidden = !message;
    el.dataset.tone = tone || 'muted';
  }

  /** 말풍선 입력 위임 — handle 을 타이핑하면 미리보기 주소를 즉시 갱신한다. */
  function onAiGuideBodyInput(event) {
    const t = event.target;
    if (!t || t.id !== 'builderAiGuideHandle') return;
    const normalized = normalizeHandle(t.value);
    if (t.value !== normalized) {
      const pos = t.selectionStart;
      t.value = normalized;
      try { t.setSelectionRange(pos, pos); } catch (_) { /* noop */ }
    }
    const prev = document.getElementById('builderAiGuideHandlePreview');
    if (prev) prev.textContent = (normalized || 'apgujeong-eduholic') + '.feel2real.co.kr';
    setAiGuideNamingStatus('', 'muted');
  }

  async function namingHandleAvailable(handle) {
    const shouldCheck = !!(currentPreview && currentPreview.publicPathUrl) || !!(await getUser());
    if (!shouldCheck) return null; // 미로그인/발행 전 — 발행 시 서버가 확인
    try { return await isHandleAvailable(handle); } catch (_) { return null; }
  }

  async function applyAiGuideNaming() {
    if (aiGuideBusy || !currentPreview) return;
    const nameEl = document.getElementById('builderAiGuideName');
    const handleEl = document.getElementById('builderAiGuideHandle');
    const name = nameEl ? String(nameEl.value || '').trim() : '';
    const handle = normalizeHandle(handleEl ? handleEl.value : '');
    if (name.length < 2) { setAiGuideNamingStatus('학원명을 2자 이상 입력해 주세요.', 'error'); return; }
    if (!isValidHandle(handle)) {
      setAiGuideNamingStatus('주소는 소문자·숫자·하이픈 3~30자, 앞뒤는 하이픈이 될 수 없어요.', 'error');
      return;
    }
    aiGuideBusy = true;
    setAiGuideNamingStatus('주소를 확인하고 있어요…', 'checking');
    try {
      const sameHandle = handle === normalizeHandle(currentPreview.handle);
      if (!sameHandle) {
        const available = await namingHandleAvailable(handle);
        if (available === false) {
          setAiGuideNamingStatus('이미 사용 중인 주소예요. 다른 주소를 입력해 주세요.', 'error');
          return;
        }
      }
      let changed = false;
      if (name !== (currentPreview.name || '')) {
        currentPreview.name = name;
        syncInlineTextMirrors('name', name);
        syncInlineInitials();
        changed = true;
      }
      if (!sameHandle) {
        currentPreview.handle = handle;
        currentPreview.displayUrl = handle + '.feel2real.co.kr';
        changed = true;
      }
      if (changed) {
        populatePreview(currentPreview);
        if (currentPreview.publicPathUrl) {
          dirtyTextChanges = true;
          toast('학원 이름과 주소를 미리보기에 반영했어요. 상단 발행 버튼으로 저장하세요.', 'fa-link');
        } else {
          persistPendingHandle(handle);
          persistPendingTextChanges(currentPreview);
        }
      }
      aiGuideSession = AI_GUIDE.markCompleted(aiGuideSession, 'naming');
      aiGuideSession.academyName = name.slice(0, 100);
      writeAiGuideSession();
      const nextId = AI_GUIDE.nextStepId('naming');
      aiGuideBusy = false;
      if (nextId) goAiGuideStep(nextId); else renderAiGuide();
    } catch (_) {
      setAiGuideNamingStatus('주소 확인에 실패했어요. 잠시 후 다시 시도해 주세요.', 'error');
    } finally {
      aiGuideBusy = false;
    }
  }

  function renderAiGuideStyleStep() {
    const selected = aiGuideSession.desiredStyle || '';
    const options = AI_GUIDE.STYLE_OPTIONS.map((label) => {
      const isSel = label === selected ? ' is-selected' : '';
      return `<button type="button" class="ai-guide-style-option${isSel}" data-guide-action="style" data-guide-value="${escapeAttr(label)}">${escapeHtml(label)}</button>`;
    }).join('');
    return `<div class="ai-guide-question">${escapeHtml(currentAiGuideStep().question)}</div>`
      + `<div class="ai-guide-style-options">${options}</div>`
      + `<input type="text" id="builderAiGuideStyleInput" class="ai-guide-input" style="min-height:auto" maxlength="200" placeholder="예: 전문적인데 너무 딱딱하지 않게" value="${escapeAttr(selected)}">`
      + `<div class="ai-guide-actions"><button type="button" class="ai-guide-btn ai-guide-btn-primary" data-guide-action="style">이 방향으로 제안받기</button></div>`
      + brandProposalHtml()
      + (brandUndoSnapshot ? `<div class="ai-guide-actions"><button type="button" class="ai-guide-btn ai-guide-btn-ghost" data-guide-action="brand-undo">이전 스타일 되돌리기</button></div>` : '');
  }

  function renderAiGuideTextStep(step) {
    // 이미 채워진 영역이면 먼저 "기존 유지 / 다시 검토"를 물어본다(§15) — AI가 덮어쓰지 않는다.
    if (aiGuideExistingSteps.indexOf(step.id) >= 0) {
      return `<div class="ai-guide-bubble">이 영역에는 이미 작성된 내용이 있습니다. 기존 내용을 유지하고 다음으로 넘어가거나, 다시 검토할 수 있습니다.</div>`
        + `<div class="ai-guide-actions">`
        + `<button type="button" class="ai-guide-btn ai-guide-btn-primary" data-guide-action="keep-existing">기존 유지하고 다음</button>`
        + `<button type="button" class="ai-guide-btn ai-guide-btn-ghost" data-guide-action="review-existing">다시 검토</button>`
        + `<button type="button" class="ai-guide-btn ai-guide-btn-ghost" data-guide-action="manual-focus">직접 수정</button>`
        + `</div>`;
    }
    const answer = aiGuideSession.strongestPoint || '';
    let html = `<div class="ai-guide-question">${escapeHtml(step.question)}</div>`
      + `<textarea id="builderAiGuideAnswer" class="ai-guide-input" maxlength="400" placeholder="답을 적으면 AI가 문구를 추천합니다">${escapeHtml(answer)}</textarea>`
      + `<div class="ai-guide-actions">`
      + `<button type="button" class="ai-guide-btn ai-guide-btn-primary" data-guide-action="recommend"${aiGuideBusy ? ' disabled' : ''}>${aiGuidePending ? '다시 추천' : '문구 추천받기'}</button>`
      + `<button type="button" class="ai-guide-btn ai-guide-btn-ghost" data-guide-action="manual-focus">직접 작성</button>`
      + `</div>`;
    if (aiGuidePending && aiGuidePending.target === step.target && aiGuidePending.candidates.length) {
      html += renderAiGuideCandidateCarousel(aiGuidePending.candidates)
      + `<p class="ai-guide-note">적용해도 발행되지 않습니다. 미리보기에만 반영되고, 저장은 상단 [발행] 버튼으로 직접 하셔야 합니다.</p>`;
    }
    return html;
  }

  /**
   * §2 — 추천안 캐러셀. 여러 추천안을 세로로 길게 쌓지 않고 현재 1개만 보여준다. 읽기 전용
   * 문구라 textarea 가 아니라 일반 text block 이며(§3), [적용] 은 항상 카드 안에 노출된다(§2).
   * 말풍선/패널 공통 마크업이라 여기서 스크롤 컨테이너를 만들지 않는다 — "스크롤 안의 스크롤"(§2·§5) 금지.
   */
  function renderAiGuideCandidateCarousel(candidates) {
    const total = candidates.length;
    if (aiGuideCandidateIndex >= total) aiGuideCandidateIndex = total - 1;
    if (aiGuideCandidateIndex < 0) aiGuideCandidateIndex = 0;
    const index = aiGuideCandidateIndex;
    const text = candidates[index];
    const counter = total > 1 ? `<div class="ai-guide-candidate-counter">추천안 ${index + 1} / ${total}</div>` : '';
    const nav = total > 1
      ? `<div class="ai-guide-candidate-nav">`
        + `<button type="button" class="ai-guide-btn ai-guide-btn-ghost" data-guide-action="cand-prev"${index === 0 ? ' disabled' : ''}>← 이전 추천</button>`
        + `<button type="button" class="ai-guide-btn ai-guide-btn-ghost" data-guide-action="cand-next"${index === total - 1 ? ' disabled' : ''}>다음 추천 →</button>`
        + `</div>`
      : '';
    return `<div class="ai-guide-candidates" data-guide-candidate-carousel>`
      + counter
      + `<div class="ai-guide-candidate">`
      +   `<p>${escapeHtml(text)}</p>`
      +   `<button type="button" class="ai-guide-btn ai-guide-btn-primary" data-guide-action="apply" data-guide-index="${index}">적용</button>`
      + `</div>`
      + nav
      + `</div>`;
  }

  function stepAiGuideCandidate(delta) {
    if (!aiGuidePending || !aiGuidePending.candidates.length) return;
    const total = aiGuidePending.candidates.length;
    const next = Math.max(0, Math.min(total - 1, aiGuideCandidateIndex + delta));
    if (next === aiGuideCandidateIndex) return;
    aiGuideCandidateIndex = next;
    renderAiGuide();
  }

  function wireMiniLecturesEditing() {
    const pageContent = document.getElementById('pageContent');
    if (pageContent && pageContent.dataset.miniLecturesWired !== 'true') {
      pageContent.dataset.miniLecturesWired = 'true';
      const open = (event) => {
        const hit = event.target && event.target.closest ? event.target.closest('[data-mini-lectures-edit]') : null;
        if (!hit) return;
        event.preventDefault();
        event.stopPropagation();
        openMiniLecturesModal();
      };
      pageContent.addEventListener('click', open, true);
      pageContent.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        open(event);
      }, true);
    }

    const modal = document.getElementById('builderMiniLecturesModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-mini-lectures-close]').forEach((button) => {
      button.addEventListener('click', closeMiniLecturesModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeMiniLecturesModal();
    });
    const apply = document.getElementById('builderMiniLecturesApplyBtn');
    if (apply) apply.addEventListener('click', applyMiniLecturesFromModal);
    const list = document.getElementById('builderMiniLecturesOptions');
    if (list) {
      list.addEventListener('change', (event) => {
        const row = event.target && event.target.closest ? event.target.closest('.builder-lecture-row') : null;
        if (row && event.target.matches('input[type="checkbox"]')) {
          row.classList.toggle('is-selected', event.target.checked);
        }
      });
      // 노출 순서 변경: 저장 순서는 collectMiniLecturesFromModal()이 DOM 순서를 그대로 읽으므로
      // 행을 실제로 앞뒤로 옮기는 것만으로 재정렬이 끝난다(별도 순서 상태를 두지 않는다).
      list.addEventListener('click', (event) => {
        const button = event.target && event.target.closest ? event.target.closest('[data-lecture-move]') : null;
        if (!button) return;
        const row = button.closest('.builder-lecture-row');
        if (!row) return;
        if (button.dataset.lectureMove === 'up') {
          const prev = row.previousElementSibling;
          if (prev) row.parentNode.insertBefore(row, prev);
        } else {
          const next = row.nextElementSibling;
          if (next) row.parentNode.insertBefore(next, row);
        }
      });
    }
  }

  function openMiniLecturesModal() {
    const modal = document.getElementById('builderMiniLecturesModal');
    const options = document.getElementById('builderMiniLecturesOptions');
    if (!modal || !options || !currentPreview) return;
    const saved = parsedMiniLectures();
    const savedById = new Map(saved.map((e) => [String(e.v), e]));
    // 원장 소개 영상이 미니 강의 목록에 섞이지 않도록 LESSON(+용도 미상 레거시)만 보여준다.
    const videos = videosForPurpose('LESSON').filter((video) => video && video.videoId != null);
    // 초기 나열은 저장된 순서 → 그 외 영상 순이다. 이후 ▲▼ 버튼으로 행을 옮기면
    // 그 순서 그대로 저장된다(Phase 3: 모달 안 재정렬 지원).
    const ordered = saved
      .map((e) => videos.find((v) => String(v.videoId) === String(e.v)))
      .filter(Boolean)
      .concat(videos.filter((v) => !savedById.has(String(v.videoId))));

    options.innerHTML = ordered.length
      ? ordered.map((video) => {
          const entry = savedById.get(String(video.videoId));
          const checked = entry ? 'checked' : '';
          const thumb = video.thumbnailUrl
            ? `<img src="${escapeHtml(video.thumbnailUrl)}" alt="">`
            : '<span class="builder-lecture-thumb-ph">🎓</span>';
          return `<div class="builder-lecture-row${entry ? ' is-selected' : ''}" data-video-id="${escapeHtml(String(video.videoId))}">
            <div class="builder-lecture-row-top">
              <label class="builder-lecture-pick">
                <input type="checkbox" ${checked}>
                ${thumb}
                <span class="builder-lecture-file">${escapeHtml(video.originalFilename || '이름 없는 영상')}</span>
              </label>
              <div class="builder-lecture-order">
                <button type="button" class="builder-lecture-move" data-lecture-move="up" aria-label="노출 순서 위로 이동">▲</button>
                <button type="button" class="builder-lecture-move" data-lecture-move="down" aria-label="노출 순서 아래로 이동">▼</button>
              </div>
            </div>
            <div class="builder-lecture-fields">
              <input type="text" data-lecture-field="t" maxlength="80" placeholder="강의 제목 (예: 관계대명사 한 번에 정리)" value="${escapeHtml(entry ? entry.t : '')}">
              <input type="text" data-lecture-field="n" maxlength="160" placeholder="학습 포인트 (예: which·that 헷갈릴 때 판단 기준)" value="${escapeHtml(entry ? entry.n : '')}">
              <input type="text" data-lecture-field="g" maxlength="40" placeholder="대상·분류 (선택) (예: 중등 내신)" value="${escapeHtml(entry ? entry.g : '')}">
            </div>
          </div>`;
        }).join('')
      : '<p class="lesson-empty">먼저 <a href="/tutor/academy/videos">영상 관리</a>에서 강의 영상을 업로드해 주세요.</p>';
    clearMiniLecturesError();
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
  }

  function closeMiniLecturesModal() {
    const modal = document.getElementById('builderMiniLecturesModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    clearMiniLecturesError();
  }

  function collectMiniLecturesFromModal() {
    return Array.from(document.querySelectorAll('#builderMiniLecturesOptions .builder-lecture-row'))
      .filter((row) => {
        const checkbox = row.querySelector('input[type="checkbox"]');
        return checkbox && checkbox.checked;
      })
      .map((row) => ({
        v: Number(row.dataset.videoId),
        t: (row.querySelector('[data-lecture-field="t"]').value || '').trim(),
        n: (row.querySelector('[data-lecture-field="n"]').value || '').trim(),
        g: (row.querySelector('[data-lecture-field="g"]').value || '').trim()
      }))
      .filter((entry) => entry.v > 0);
  }

  async function applyMiniLecturesFromModal() {
    const apply = document.getElementById('builderMiniLecturesApplyBtn');
    const entries = collectMiniLecturesFromModal();
    if (entries.length > MINI_LECTURES_MAX) {
      setMiniLecturesError('미니 강의는 최대 ' + MINI_LECTURES_MAX + '개까지 담을 수 있습니다.');
      return;
    }
    if (entries.some((e) => !e.t)) {
      setMiniLecturesError('선택한 강의에는 제목을 입력해 주세요.');
      return;
    }
    const serialized = entries.length ? JSON.stringify(entries) : '';

    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    const previousDraft = Object.assign({}, pageSettings.builderDraft || {});
    pageSettings.builderDraft = Object.assign({}, previousDraft, { miniLectures: serialized });
    currentPreview.pageSettings = pageSettings;

    if (!currentPreview.publicPathUrl) {
      persistPendingTextChanges(currentPreview);
      renderPublicPreview(currentPreview, 'sec-minilectures');
      closeMiniLecturesModal();
      toast('발행 전 미리보기에 미니 강의를 반영했습니다.', 'fa-graduation-cap');
      return;
    }

    const request = academyUpdateRequest(currentPreview);
    if (!request) {
      currentPreview.pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { builderDraft: previousDraft });
      setMiniLecturesError('기존 Academy 필수값을 읽지 못해 저장할 수 없습니다.');
      return;
    }
    setSaving(apply, true);
    try {
      const res = await fetch('/api/tutor/academy', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(request)
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      applyAcademyToLivePreview(currentPreview, 'sec-minilectures');
      closeMiniLecturesModal();
      toast('미니 강의를 저장했습니다.', 'fa-graduation-cap');
    } catch (_) {
      currentPreview.pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { builderDraft: previousDraft });
      setMiniLecturesError('미니 강의 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSaving(apply, false);
    }
  }

  function setMiniLecturesError(message) {
    const error = document.getElementById('builderMiniLecturesError');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearMiniLecturesError() {
    const error = document.getElementById('builderMiniLecturesError');
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }

  function sectionControlButton(icon, label, extraClass, action) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'block-ctrl-btn builder-section-ctrl-btn' + (extraClass ? ' ' + extraClass : '');
    button.innerHTML = '<i class="fa-solid ' + icon + '"></i> ' + label;
    button.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      action();
    });
    return button;
  }

  function hideSelectedSection(section) {
    if (!section) return;
    const sectionId = canonicalSectionId(section.dataset.builderSectionKey);
    if (!sectionId) {
      toast('히어로 섹션은 필수 영역이라 삭제할 수 없습니다.', 'fa-lock');
      return;
    }
    const toggleButton = document.querySelector('[data-section-key="' + cssAttrEscape(sectionId) + '"]');
    toggleSectionVisibility(sectionId, toggleButton);
  }

  function removeSectionTextApply(section) {
    if (!section) return;
    const controls = section.querySelector('.builder-section-controls');
    if (controls) controls.remove();
  }

  /**
   * @param point (선택) 사용자가 클릭한 좌표 {x, y}. 주면 그 지점에 caret 하나만 놓는다.
   *     없으면(툴바 버튼 등 좌표가 없는 진입) 문장 끝으로 collapse 한다. 어느 경우에도
   *     기존 문구 전체를 selection 하지 않는다(Phase 5B Direct Text Editing UX).
   */
  function focusEditableTarget(target, point) {
    if (!target) return;
    target.classList.remove('builder-text-invalid');
    target.setAttribute('contenteditable', 'true');
    try { target.focus({ preventScroll: true }); } catch (_) { target.focus(); }
    placeCaretAtPoint(target, point);
    activeTextField = textFieldIdentity(target);
    highlightTextTargets(activeTextField);
  }

  function textFieldIdentity(target) {
    return target.dataset.builderText || ('draft:' + target.dataset.builderDraft);
  }

  function insertPlainText(text) {
    if (document.queryCommandSupported && document.queryCommandSupported('insertText')) {
      document.execCommand('insertText', false, text);
      return;
    }
    const selection = window.getSelection && window.getSelection();
    if (!selection || !selection.rangeCount) return;
    selection.deleteFromDocument();
    selection.getRangeAt(0).insertNode(document.createTextNode(text));
    selection.collapseToEnd();
  }

  function moveCaretToEnd(target) {
    const selection = window.getSelection && window.getSelection();
    if (!selection || !target) return;
    const range = document.createRange();
    range.selectNodeContents(target);
    range.collapse(false);
    selection.removeAllRanges();
    selection.addRange(range);
  }

  function normalizedEditableText(target) {
    const raw = TEXT_UTILS.extractEditableText(target);
    return TEXT_UTILS.isSingleLineTextTarget(target)
      ? TEXT_UTILS.normalizeSingleLineText(raw)
      : TEXT_UTILS.normalizeMultilineText(raw);
  }

  function currentTextValue(target) {
    if (!currentPreview || !target) return '';
    if (target.dataset.builderDraft) return draftText(currentPreview, target.dataset.builderDraft, '');
    return currentPreview[target.dataset.builderText] || '';
  }

  function applySectionTextChanges(section, options) {
    const opts = options || {};
    if (!currentPreview || !section) return false;
    const targets = getSectionTextTargets(section);
    if (!targets.length) return true;
    const changes = [];
    for (const target of targets) {
      const value = normalizedEditableText(target);
      if (!value) {
        target.classList.add('builder-text-invalid');
        toast(opts.reason === 'section-switch' ? '빈 텍스트가 있어 섹션을 이동할 수 없습니다. 값을 입력하거나 Esc로 되돌려 주세요.' : '빈 값으로는 수정할 수 없습니다.', 'fa-triangle-exclamation');
        focusEditableTarget(target);
        target.classList.add('builder-text-invalid');
        return false;
      }
      target.classList.remove('builder-text-invalid');
      changes.push({ target, value });
    }
    let changed = false;
    changes.forEach(({ target, value }) => {
      if (target.dataset.builderDraft) changed = commitDraftText(target, value) || changed;
      else changed = commitInlineText(target, value) || changed;
      target.dataset.beforeEdit = value;
      target.textContent = value;
    });
    // 원장 약력(가변 배열)은 scalar draft 대상이 아니므로 섹션 적용 시 DOM에서 직접 수집해
    // builderDraft.principalCareer로 커밋한다. builderDraft는 persistPendingTextChanges/
    // academyUpdateRequest가 pageSettings 통째로 복사하므로 이 값도 함께 저장·발행된다.
    if (section.querySelector('[data-principal-career-list]')) {
      if (commitPrincipalCareer(section)) changed = true;
      section.querySelectorAll('[data-principal-career-item]').forEach((el) => {
        el.textContent = TEXT_UTILS.normalizeSingleLineText(TEXT_UTILS.extractEditableText(el));
        el.setAttribute('contenteditable', 'false');
      });
    }
    // 학원 특징(가변 배열)도 원장 약력과 같은 이유로 섹션 적용 시 DOM에서 직접 수집해 커밋한다.
    if (section.querySelector('[data-intro-feature-list]')) {
      if (commitIntroFeatures(section)) changed = true;
      section.querySelectorAll('[data-intro-feature-item],[data-intro-feature-body]').forEach((el) => {
        el.textContent = notePartText(el);
        el.setAttribute('contenteditable', 'false');
      });
    }
    if (!changed) {
      if (!opts.suppressNoChangeToast) toast('변경 사항이 없습니다.', 'fa-circle-info');
      return true;
    }
    populatePreview(currentPreview);
    persistTextChangesAfterApply(opts);
    clearTextHighlights();
    activeTextField = null;
    return true;
  }

  function commitInlineText(target, next) {
    if (!currentPreview || !target) return false;
    if (target.dataset.builderDraft) return commitDraftText(target, next);
    const field = target.dataset.builderText;
    if (!['name', 'intro'].includes(field)) return false;
    if (next === currentPreview[field]) return false;
    currentPreview[field] = next;
    syncInlineTextMirrors(field, next);
    if (field === 'name') syncInlineInitials();
    return true;
  }

  function commitDraftText(target, next) {
    if (!currentPreview || !target) return false;
    const key = target.dataset.builderDraft;
    if (!key) return false;
    const previous = currentTextValue(target);
    if (next === previous) return false;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    pageSettings.builderDraft = Object.assign({}, pageSettings.builderDraft || {}, { [key]: next });
    currentPreview.pageSettings = pageSettings;
    return true;
  }

  function persistTextChangesAfterApply(options) {
    const opts = options || {};
    if (currentPreview.publicPathUrl) {
      dirtyTextChanges = true;
      toast(opts.reason === 'section-switch' ? '이전 섹션 변경사항을 임시 적용했습니다. 상단 발행 버튼으로 저장해 주세요.' : '미리보기에 반영했습니다. 상단 발행 버튼으로 저장해 주세요.', 'fa-pen-to-square');
      return;
    }
    persistPendingTextChanges(currentPreview);
    toast(opts.reason === 'section-switch' ? '이전 섹션 변경사항을 발행 전 임시 적용했습니다.' : '발행 전 미리보기에 반영했습니다. 발행 시 함께 저장됩니다.', 'fa-pen-to-square');
  }

  function syncInlineTextMirrors(field, value) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    pageContent.querySelectorAll('[data-builder-text="' + field + '"]').forEach((el) => {
      if (el.textContent !== value) el.textContent = value;
    });
  }

  function syncInlineInitials() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    pageContent.querySelectorAll('.eh-letters').forEach((el) => {
      el.textContent = initials(currentPreview.name);
    });
  }

  async function saveTextChanges(button) {
    if (!currentPreview || !currentPreview.publicPathUrl) return false;
    const request = academyUpdateRequest(currentPreview);
    if (!request) {
      toast('기존 Academy 필수값을 읽지 못해 저장할 수 없습니다. 설정 화면에서 수정해 주세요.', 'fa-triangle-exclamation');
      return false;
    }
    setSaving(button, true);
    try {
      const res = await fetch('/api/tutor/academy', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(request)
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      dirtyTextChanges = false;
      applyAcademyToLivePreview(currentPreview, 'sec-hero');
      toast('텍스트 수정 사항을 저장했습니다.', 'fa-check');
      return true;
    } catch (_) {
      toast('텍스트 저장에 실패했습니다. 설정 화면에서 다시 시도해 주세요.', 'fa-triangle-exclamation');
      return false;
    } finally {
      setSaving(button, false);
    }
  }

  function academyUpdateRequest(academy) {
    const subjectIds = (academy.subjects || []).map((s) => s.subjectId).filter((id) => id != null);
    const targetGroups = (academy.targetGroups || []).filter(Boolean);
    const regions = (academy.regions || []).map((r) => ({ city: r.city, district: r.district || null })).filter((r) => r.city);
    if (!academy.name || !academy.intro || !subjectIds.length || !targetGroups.length || !regions.length) return null;
    // pageSettings를 통째로 복사하므로 builderDraft(원장 principalName/Role/Desc/Quote + principalCareer 배열)와
    // principalYoutubeUrl 등 모든 빌더 저장값이 그대로 발행/저장 요청에 포함된다.
    const pageSettings = Object.assign({}, academy.pageSettings || {});
    pageSettings.sectionVisibility = normalizeSectionVisibility(
      academy.sectionVisibility || pageSettings.sectionVisibility
    );
    pageSettings.sectionOrder = sectionOrderFrom(academy);
    return {
      name: academy.name,
      handle: academy.handle,
      intro: academy.intro,
      subjectIds,
      targetGroups,
      regions,
      pageSettings
    };
  }

  function syncPreviewFromAcademy(academy) {
    if (!academy) return;
    const previous = currentPreview || {};
    currentPreview = academy;
    currentPreview.publicPathUrl = academy.slug ? '/academies/' + academy.slug : previous.publicPathUrl || null;
    currentPreview.displayUrl = displayUrlForPreview(academy);
    currentPreview.pageSettings = academy.pageSettings || previous.pageSettings || {};
    currentPreview.sectionVisibility = normalizeSectionVisibility(
      (currentPreview.pageSettings && currentPreview.pageSettings.sectionVisibility) || previous.sectionVisibility
    );
    currentPreview.sectionOrder = sectionOrderFrom(currentPreview);
    populatePreview(currentPreview);
    syncMotionControls();
  }

  function textHighlightSelectors(field) {
    if (['name', 'intro'].includes(field)) {
      return ['[data-builder-text="' + cssAttrEscape(field) + '"]'];
    }
    if (String(field || '').startsWith('draft:')) {
      const key = String(field).slice('draft:'.length);
      return ['[data-builder-draft="' + cssAttrEscape(key) + '"]'];
    }
    return [];
  }

  function highlightTextTargets(field) {
    clearTextHighlights();
    ensureHighlightStyle();
    const root = document.getElementById('pageContent');
    if (!root) return;
    textHighlightSelectors(field).forEach((selector) => {
      root.querySelectorAll(selector).forEach((el) => el.classList.add('f2r-builder-text-highlight'));
    });
  }

  function clearTextHighlights() {
    const root = document.getElementById('pageContent');
    if (!root) return;
    root.querySelectorAll('.f2r-builder-text-highlight').forEach((el) => el.classList.remove('f2r-builder-text-highlight'));
  }

  function ensureHighlightStyle() {
    if (document.getElementById('f2rBuilderTextHighlightStyle')) return;
    const style = document.createElement('style');
    style.id = 'f2rBuilderTextHighlightStyle';
    style.textContent = '.f2r-builder-text-highlight{outline:3px solid #facc15!important;outline-offset:4px!important;box-shadow:0 0 0 6px rgba(250,204,21,.24)!important;border-radius:10px!important;transition:outline .15s ease,box-shadow .15s ease;}';
    document.head.appendChild(style);
  }

  function sectionOrderFrom(source) {
    const settings = source && source.pageSettings;
    const raw = Array.isArray(source && source.sectionOrder) ? source.sectionOrder
      : (settings && Array.isArray(settings.sectionOrder) ? settings.sectionOrder : null);
    return normalizeSectionOrder(raw);
  }

  function normalizeSectionOrder(raw) {
    if (!Array.isArray(raw)) return DEFAULT_SECTION_ORDER.slice();
    const seen = new Set();
    const filtered = raw.filter((id) => ORDERED_SECTIONS.has(id) && !seen.has(id) && seen.add(id));
    if (filtered[0] !== 'sec-hero') {
      return DEFAULT_SECTION_ORDER.slice();
    }
    // 빠진 관리 섹션은 canonical 위치(그 다음 순위 섹션 앞)에 삽입한다.
    // 서버 AcademyService.normalizeSectionOrderForWrite와 같은 규칙이라
    // 렌더 순서와 저장 순서가 어긋나지 않는다. 기존 ID의 상대 순서는 그대로 둔다.
    const normalized = filtered.slice();
    for (const id of DEFAULT_SECTION_ORDER) {
      if (seen.has(id)) continue;
      let insertionIndex = normalized.length;
      const defaultIndex = DEFAULT_SECTION_ORDER.indexOf(id);
      for (let next = defaultIndex + 1; next < DEFAULT_SECTION_ORDER.length; next++) {
        const existingIndex = normalized.indexOf(DEFAULT_SECTION_ORDER[next]);
        if (existingIndex >= 0) { insertionIndex = existingIndex; break; }
      }
      normalized.splice(insertionIndex, 0, id);
      seen.add(id);
    }
    return normalized;
  }

  function orderedSectionIds(academy) {
    return normalizeSectionOrder(academy && (academy.sectionOrder || (academy.pageSettings && academy.pageSettings.sectionOrder)));
  }

  function layoutSectionIds(academy) {
    return orderedSectionIds(academy)
      .filter((id) => id === 'sec-hero' || academySectionVisible(academy, id))
      .concat(FIXED_FOOTER_SECTION_ID);
  }

  /**
   * 공용 섹션 모듈에 넘길 view를 만든다.
   * 빌더 렌더는 인자로 받은 academy가 아니라 전역 currentPreview에서 가시성을 읽는다
   * (섹션 토글이 currentPreview를 먼저 갱신하고 재렌더하는 순서라 그렇다).
   * pageSettings도 academy에 없으면 currentPreview로 떨어진다. 그 규칙을 그대로 옮긴다.
   */
  function sectionView(academy) {
    const source = academy || currentPreview || {};
    const preview = currentPreview || {};
    return SECTIONS.toSectionView(source, {
      pageSettings: source.pageSettings || preview.pageSettings || {},
      sectionVisibility: preview.sectionVisibility
        || (preview.pageSettings && preview.pageSettings.sectionVisibility)
        || {}
    });
  }

  function academySectionVisible(preview, sectionId) {
    const key = canonicalSectionId(sectionId);
    if (FUTURE_SECTIONS.has(key)) return false;
    const visibility = preview && (preview.sectionVisibility || (preview.pageSettings && preview.pageSettings.sectionVisibility));
    if (OPT_IN_SECTIONS.has(key)) return !!(visibility && visibility[key] === true);
    return !(key && visibility && visibility[key] === false);
  }

  function normalizeSectionVisibility(visibility) {
    const normalized = Object.assign({}, visibility || {});
    FUTURE_SECTION_IDS.forEach((id) => {
      normalized[id] = false;
    });
    return normalized;
  }

  function pageSettingsWriteBody(currentSettings, patch) {
    const body = Object.assign({}, patch || {});
    const currentVisibility = currentSettings && currentSettings.sectionVisibility;
    const patchedVisibility = patch && patch.sectionVisibility;
    body.sectionVisibility = normalizeSectionVisibility(
      Object.assign({}, currentVisibility || {}, patchedVisibility || {})
    );
    return body;
  }

  function canonicalSectionId(value) {
    const key = String(value || '').trim();
    return ORDERED_SECTIONS.has(key) ? key : null;
  }

  function applySectionVisibilityState(visibility) {
    document.querySelectorAll('[data-section-toggle]').forEach((button) => {
      const sectionId = canonicalSectionId(button.dataset.sectionKey || button.dataset.sectionToggle || button.dataset.sectionId);
      if (sectionId) button.dataset.sectionKey = sectionId;
      const hidden = FUTURE_SECTIONS.has(sectionId)
        || (OPT_IN_SECTIONS.has(sectionId)
            ? !(visibility && visibility[sectionId] === true)
            : (sectionId && visibility && visibility[sectionId] === false));
      button.classList.toggle('is-hidden', hidden);
      button.classList.toggle('is-toggle-on', !hidden);
      button.classList.toggle('is-toggle-off', hidden);
      button.setAttribute('aria-pressed', String(!hidden));
      const state = button.querySelector('.section-toggle-state');
      if (state) state.textContent = button.classList.contains('block-item')
          ? (hidden ? 'OFF' : 'ON')
          : (hidden ? '숨김' : '표시');
    });
    renderLayoutSectionOrder(currentPreview);
  }

  function setSaving(button, saving) {
    if (!button) return;
    button.classList.toggle('is-saving', saving);
    if ('disabled' in button) button.disabled = saving;
    button.setAttribute('aria-busy', String(saving));
  }

  /* ── 모션 설정(효과·속도) ──────────────────────────────────────────────
     builderDraft["motion.effect"|"motion.speed"] 에 저장. DB 스키마 변경 없음.
     미리보기에는 AcademyMotion.applyPrefs 로 즉시 반영하고,
     공개 페이지는 academy_public.js 가 같은 draft 키를 읽는다. */

  function motionDraftOf(preview) {
    return (preview && preview.pageSettings && preview.pageSettings.builderDraft) || {};
  }

  function syncMotionControls() {
    const draft = motionDraftOf(currentPreview);
    const effectSel = document.getElementById('motionEffectSel');
    const speedSel = document.getElementById('motionSpeedSel');
    if (effectSel) effectSel.value = draft['motion.effect'] || '';
    if (speedSel) speedSel.value = draft['motion.speed'] || '';
  }

  function applyMotionPrefsToPreview() {
    if (!window.AcademyMotion || typeof window.AcademyMotion.applyPrefs !== 'function') return;
    const draft = motionDraftOf(currentPreview);
    try {
      window.AcademyMotion.applyPrefs({ effect: draft['motion.effect'], speed: draft['motion.speed'] });
    } catch (_) { /* 미리보기 노출에 영향 없음 */ }
  }

  async function saveMotionPrefs(effect, speed) {
    if (!currentPreview) return;
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {});
    const previousDraft = Object.assign({}, pageSettings.builderDraft || {});
    const nextDraft = Object.assign({}, previousDraft);
    if (effect) nextDraft['motion.effect'] = effect; else delete nextDraft['motion.effect'];
    if (speed) nextDraft['motion.speed'] = speed; else delete nextDraft['motion.speed'];
    pageSettings.builderDraft = nextDraft;
    currentPreview.pageSettings = pageSettings;
    applyMotionPrefsToPreview();
    syncMotionControls();

    if (!currentPreview.publicPathUrl) {
      // 미발행(작업본) — 미리보기만 갱신하고 저장은 다음 저장 시점에 포함된다.
      toast('모션 설정을 미리보기에 반영했습니다.', 'fa-wand-magic-sparkles');
      return;
    }
    const request = academyUpdateRequest(currentPreview);
    if (!request) {
      currentPreview.pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { builderDraft: previousDraft });
      toast('모션 설정 저장에 실패했습니다.', 'fa-triangle-exclamation');
      return;
    }
    try {
      const res = await fetch('/api/tutor/academy', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(request)
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      toast('모션 설정을 저장했습니다.', 'fa-check');
    } catch (_) {
      currentPreview.pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { builderDraft: previousDraft });
      applyMotionPrefsToPreview();
      syncMotionControls();
      toast('모션 설정 저장에 실패했습니다.', 'fa-triangle-exclamation');
    }
  }

  function wireMotionControls() {
    const effectSel = document.getElementById('motionEffectSel');
    const speedSel = document.getElementById('motionSpeedSel');
    const onChange = () => {
      const effect = effectSel ? effectSel.value : '';
      const speed = speedSel ? speedSel.value : '';
      saveMotionPrefs(effect, speed);
    };
    if (effectSel && !effectSel.dataset.motionWired) {
      effectSel.dataset.motionWired = 'true';
      effectSel.addEventListener('change', onChange);
    }
    if (speedSel && !speedSel.dataset.motionWired) {
      speedSel.dataset.motionWired = 'true';
      speedSel.addEventListener('change', onChange);
    }
    syncMotionControls();
  }

  function applyAcademyToLivePreview(academy, sectionId) {
    if (!academy) return;
    renderPublicPreview(academy, sectionId, activeTextField);
  }

  function readJsonDataset(value) {
    if (!value) return {};
    try { return JSON.parse(value); } catch (_) { return {}; }
  }

  function subjectPreviewsFromRequest(request) {
    if (Array.isArray(request && request.subjects)) return request.subjects;
    const ids = Array.isArray(request && request.subjectIds) ? request.subjectIds : [];
    return ids.map((id) => ({ subjectId: id, nameKo: '과목 ' + id, nameEn: 'Subject ' + id }));
  }

  function persistPendingTextChanges(preview) {
    const request = readJson(PENDING_KEY);
    if (request) {
      request.name = preview.name;
      request.intro = preview.intro;
      request.pageSettings = Object.assign({}, request.pageSettings || {}, preview.pageSettings || {});
      writeJson(PENDING_KEY, request);
    }
    const storedPreview = readJson(PENDING_PREVIEW_KEY) || preview;
    storedPreview.name = preview.name;
    storedPreview.intro = preview.intro;
    storedPreview.sectionVisibility = preview.sectionVisibility || storedPreview.sectionVisibility || {};
    storedPreview.sectionOrder = sectionOrderFrom(preview);
    storedPreview.pageSettings = Object.assign({}, storedPreview.pageSettings || {}, preview.pageSettings || {}, { sectionOrder: storedPreview.sectionOrder });
    writeJson(PENDING_PREVIEW_KEY, storedPreview);
  }

  function applyStoredDraftLogo(preview) {
    if (!preview || preview.publicPathUrl) return;
    const draft = readJson(PENDING_LOGO_KEY);
    if (!draft || !draft.dataUrl) return;
    preview.logoUrl = draft.dataUrl;
    preview.logoVersion = Date.now();
  }

  function draftLogoBlob(draft) {
    if (!draft || !draft.dataUrl) return null;
    const parts = String(draft.dataUrl).split(',');
    if (parts.length !== 2) return null;
    const header = parts[0];
    const binary = atob(parts[1]);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
    const type = draft.type || (header.match(/data:([^;]+)/) || [])[1] || 'application/octet-stream';
    return { blob: new Blob([bytes], { type }), name: draft.name || 'logo' };
  }

  function writeJson(key, value) {
    try { sessionStorage.setItem(key, JSON.stringify(value)); } catch (_) {}
  }


  function targetLabel(value) {
    return ({ ELEMENTARY: '초등', MIDDLE: '중등', HIGH: '고등', UNIVERSITY: '대학', ADULT: '성인' })[value] || value;
  }

  function previewUrl(name) {
    return (slugify(name) || 'academy') + '.feel2real.co.kr';
  }

  function slugify(text) {
    return String(text || '').trim().toLowerCase().replace(/[^0-9a-z가-힣]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 36);
  }

  function initials(name) {
    const chars = String(name || '').trim().replace(/\s+/g, '');
    return chars ? chars.slice(0, 2).toUpperCase() : 'AC';
  }

  function scrollPreviewToSection(sectionId) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return;
    const target = previewSectionTarget(sectionId)
      || pageContent.querySelector('.section-wrap:not([hidden])')
      || pageContent.querySelector('#sec-hero');
    if (!target) return;
    const top = Math.max(0, target.offsetTop - 70);
    try { pageContent.scrollTo({ top, behavior: 'smooth' }); } catch (_) { pageContent.scrollTop = top; }
  }

  function previewSectionTarget(sectionId) {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent) return null;
    const requested = String(sectionId || '').trim();
    if (requested === FIXED_FOOTER_SECTION_ID) {
      return pageContent.querySelector('[data-business-info-footer]');
    }
    const canonical = requested === 'sec-hero' ? 'sec-hero' : canonicalSectionId(requested);
    return canonical && pageContent.querySelector('[data-builder-section-key="' + cssAttrEscape(canonical) + '"]')
      || (canonical && pageContent.querySelector('#' + cssEscape(canonical)));
  }

  function cssEscape(value) {
    if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(value);
    return String(value || '').replace(/[^a-zA-Z0-9_-]/g, '\\$&');
  }

  function cssAttrEscape(value) {
    return String(value || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  }

  function wirePublish(request) {
    const btn = document.getElementById('builderPublishBtn');
    if (btn) btn.addEventListener('click', () => onPublish(request, btn));
  }

  function publishRequestFromPending() {
    const request = readJson(PENDING_KEY);
    if (!request) return null;
    const normalized = Object.assign({}, request);
    normalized.pageSettings = Object.assign({}, request.pageSettings || {}, {
      sectionVisibility: normalizeSectionVisibility(request.pageSettings && request.pageSettings.sectionVisibility)
    });
    return normalized;
  }

  async function onPublish(request, button) {
    if (!request) {
      if (currentPreview && currentPreview.publicPathUrl) {
        if (dirtyTextChanges) {
          const saved = await saveTextChanges(button);
          if (!saved) return;
        }
        toast('공개 페이지로 이동합니다.', 'fa-rocket');
        setTimeout(() => { location.href = currentPreview.publicUrl || currentPreview.publicPathUrl; }, 500);
        return;
      }
      toast('발행할 학원 정보가 없습니다. 랜딩에서 다시 시작해 주세요.', 'fa-triangle-exclamation');
      return;
    }
    const user = await getUser();
    if (!user) {
      location.href = '/register?returnUrl=' + encodeURIComponent(BUILDER_URL);
      return;
    }
    if (user.role !== 'TUTOR') {
      toast('학원 발행은 강사(TUTOR) 계정만 가능합니다.', 'fa-circle-info');
      return;
    }
    try {
      const publishRequest = publishRequestFromPending() || academyUpdateRequest(currentPreview) || request;
      publishRequest.pageSettings = Object.assign({}, publishRequest.pageSettings || {}, {
        sectionVisibility: normalizeSectionVisibility(
          publishRequest.pageSettings && publishRequest.pageSettings.sectionVisibility
        )
      });
      // P0-4: 발행 전 검증 — 학원명·전화번호 필수, CTA ON 시 전화번호/카카오톡 URL 필수.
      const validation = validateBeforePublish(publishRequest);
      if (!validation.ok) {
        toast(validation.errors[0], 'fa-triangle-exclamation');
        if (validation.errors.length > 1) {
          setTimeout(() => toast(`외 ${validation.errors.length - 1}건: ${validation.errors.slice(1).join(' ')}`, 'fa-circle-info'), 2600);
        }
        return;
      }
      const form = new FormData();
      form.append('academy', new Blob([JSON.stringify(publishRequest)], { type: 'application/json' }));
      const draftLogo = pendingLogoFile ? { blob: pendingLogoFile, name: pendingLogoFile.name || 'logo' } : draftLogoBlob(readJson(PENDING_LOGO_KEY));
      if (draftLogo && draftLogo.blob) form.append('logo', draftLogo.blob, draftLogo.name || 'logo');
      const res = await fetch('/api/tutor/academy', {
        method: 'POST',
        credentials: 'same-origin',
        body: form
      });
      if (res.status === 409) {
        clearPending();
        toast('이미 학원이 있습니다. 학원 관리로 이동합니다.', 'fa-circle-info');
        setTimeout(() => location.href = '/tutor/academy', 800);
        return;
      }
      if (res.status === 403) {
        toast('강사(TUTOR) 계정만 발행할 수 있습니다.', 'fa-triangle-exclamation');
        return;
      }
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      // Academy row가 생긴 직후에야 히어로 업로드가 가능하다. 리다이렉트 전에 마무리한다.
      await flushPendingHeroMedia();
      clearPending();
      toast('🎉 학원 페이지가 발행되었습니다!', 'fa-rocket');
      const target = academy.publicUrl || ('/academies/' + encodeURIComponent(academy.slug));
      setTimeout(() => location.href = target, 700);
    } catch (_) {
      toast('발행 처리 중 문제가 발생했습니다. 설정 화면에서 마저 진행해 주세요.', 'fa-triangle-exclamation');
      setTimeout(() => location.href = '/tutor/academy/setup', 1200);
    }
  }

  async function getUser() {
    try {
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (!res.ok) return null;
      return await res.json();
    } catch (_) {
      return null;
    }
  }


  function wireBrandDesign() {
    if (!BRAND) return;
    const pane = document.querySelector('[data-builder-panel-pane="brand"]');
    if (!pane || pane.dataset.wired === 'true') return;
    pane.dataset.wired = 'true';
    const sync = function () {
      syncBrandPane(pane);
    };
    pane.addEventListener('click', function (event) {
      const designPreset = event.target.closest && event.target.closest('[data-design-preset]');
      if (designPreset) {
        openDesignPresetModal(designPreset.dataset.designPreset);
        return;
      }
      const preset = event.target.closest && event.target.closest('[data-brand-preset]');
      const font = event.target.closest && event.target.closest('[data-brand-font]');
      if (!preset && !font) return;
      brandUndoSnapshot = currentBrandDraft();
      let next = currentBrandDraft();
      if (preset) next = BRAND.presetDraft(preset.dataset.brandPreset, next);
      if (font) next[font.dataset.brandRole === 'body' ? 'bodyFont' : 'headingFont'] = font.dataset.brandFont;
      replaceBrandDraft(next); renderPublicPreview(currentPreview); persistTextChangesAfterApply({}); sync();
    });
    pane.addEventListener('input', function (event) {
      const colorInput = event.target.closest && event.target.closest('[data-brand-color]');
      if (colorInput) {
        onBrandColorInput(colorInput);
        return;
      }
      const input = event.target.closest && event.target.closest('[data-brand-key]');
      if (!input) return;
      if (!brandUndoSnapshot) brandUndoSnapshot = currentBrandDraft();
      const next = {}; next[input.dataset.brandKey] = String(input.value);
      replaceBrandDraft(next);
      const output = pane.querySelector('[data-brand-output="'+input.dataset.brandKey+'"]');
      if (output) output.textContent = input.value + 'px';
      const root = document.querySelector('#pageContent #publicView');
      if (root) BRAND.apply(root, currentPreview);
      persistTextChangesAfterApply({});
    });
    BRAND.apply(document.querySelector('#pageContent #publicView'), currentPreview);
    sync();
    wireDesignPresetModal();
  }

  function syncBrandPane(pane) {
    const draft = currentBrandDraft();
    pane.querySelectorAll('[data-brand-preset]').forEach(function (el) { el.classList.toggle('is-selected', el.dataset.brandPreset === (draft.typographyPreset || 'MODERN')); });
    pane.querySelectorAll('[data-design-preset]').forEach(function (el) { el.classList.toggle('is-selected', el.dataset.designPreset === (draft.designPreset || '')); });
    pane.querySelectorAll('[data-brand-key]').forEach(function (input) { if (draft[input.dataset.brandKey] != null) input.value = draft[input.dataset.brandKey]; });
    pane.querySelectorAll('[data-brand-color]').forEach(function (input) {
      const key = input.dataset.brandColor;
      const value = draft[key];
      if (value) input.value = value;
      else {
        const preset = BRAND.DESIGN_PRESETS[draft.designPreset];
        input.value = (preset && preset.colors[brandColorKeyName(key)]) || input.getAttribute('value') || '#000000';
      }
    });
  }

  function brandColorKeyName(draftKey) {
    const map = BRAND.COLOR_DRAFT_KEYS || {};
    for (const name in map) if (map[name] === draftKey) return name;
    return null;
  }

  /** 색상 피커 직접 조정 — draft에 커스텀값으로 저장해 프리셋 전환 후에도 유지된다. */
  function onBrandColorInput(input) {
    if (!currentPreview) return;
    if (!brandUndoSnapshot) brandUndoSnapshot = currentBrandDraft();
    const value = String(input.value || '');
    if (!/^#[0-9a-fA-F]{6}$/.test(value)) return;
    const next = {}; next[input.dataset.brandColor] = value;
    replaceBrandDraft(next);
    const root = document.querySelector('#pageContent #publicView');
    if (root) BRAND.apply(root, currentPreview);
    persistTextChangesAfterApply({});
  }

  // ── 디자인 프리셋 적용 확인 모달 (P0-1) ────────────────────────────────
  // 적용 전 무엇이 바뀌는지 보여준다. 이미 직접 바꾼 값은 유지된다는 점을 명시한다.
  const DESIGN_PRESET_KEY_LABELS = {
    designPreset: '디자인 프리셋', typographyPreset: '타이포 프리셋',
    heroLayout: '히어로 레이아웃', cardStyle: '카드 스타일',
    colorPrimary: '주색', colorSecondary: '보조색', colorAccent: '강조색',
    colorBg: '배경', colorBgAlt: '대체 배경', colorText: '본문 텍스트',
    colorTextLight: '연한 텍스트', colorBorder: '테두리',
    colorHeroBg: '히어로 배경', colorHeroText: '히어로 텍스트'
  };
  const HERO_LAYOUT_LABELS = { split: '좌우 분할', center: '중앙 정렬', visual: '사진 중심', gradient: '그라데이션' };
  const CARD_STYLE_LABELS = { bordered: '테두리 카드', rounded: '둥근 카드', media: '이미지 중심', minimal: '미니멀' };

  function designPresetChangeSummary(presetId) {
    if (!BRAND || !BRAND.DESIGN_PRESETS[presetId]) return null;
    const preset = BRAND.DESIGN_PRESETS[presetId];
    const draft = currentBrandDraft();
    const visibility = normalizeSectionVisibility(currentPreview && currentPreview.sectionVisibility);
    const result = BRAND.designPresetDraft(presetId, draft, visibility);
    const changes = [];
    Object.keys(result.draft).forEach((key) => {
      if (!(key in DESIGN_PRESET_KEY_LABELS)) return;
      const before = draft[key];
      const after = result.draft[key];
      if (before === after) return;
      changes.push({ label: DESIGN_PRESET_KEY_LABELS[key], before: humanizeDesignValue(key, before), after: humanizeDesignValue(key, after) });
    });
    result.sectionChanges.forEach((change) => {
      const name = STYLE_PANEL_SECTION_NAMES[change.sectionId] || change.sectionId;
      changes.push({ label: '섹션 표시', before: 'OFF', after: name + ' ON' });
    });
    return { preset, changes, result };
  }

  function humanizeDesignValue(key, value) {
    if (value == null || value === '') return '(기본값)';
    if (key === 'heroLayout') return HERO_LAYOUT_LABELS[value] || value;
    if (key === 'cardStyle') return CARD_STYLE_LABELS[value] || value;
    if (key === 'typographyPreset' && BRAND.PRESETS[value]) return BRAND.PRESETS[value].label;
    if (key === 'designPreset' && BRAND.DESIGN_PRESETS[value]) return BRAND.DESIGN_PRESETS[value].label;
    return String(value);
  }

  let pendingDesignPresetId = null;

  function openDesignPresetModal(presetId) {
    if (!BRAND || !currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 디자인 프리셋을 적용할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const summary = designPresetChangeSummary(presetId);
    if (!summary) return;
    pendingDesignPresetId = presetId;
    const modal = document.getElementById('builderDesignPresetModal');
    if (!modal) return;
    document.getElementById('builderDesignPresetTitle').textContent = `「${summary.preset.label}」 적용`;
    document.getElementById('builderDesignPresetDesc').textContent = summary.preset.description;
    const list = document.getElementById('builderDesignPresetChanges');
    list.innerHTML = summary.changes.length
      ? summary.changes.map((c) => `<li><b>${escapeHtml(c.label)}</b>: ${escapeHtml(c.before)} → ${escapeHtml(c.after)}</li>`).join('')
      : '<li>바뀌는 항목이 없습니다. 이미 같은 디자인이 적용되어 있습니다.</li>';
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
  }

  function closeDesignPresetModal() {
    const modal = document.getElementById('builderDesignPresetModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    pendingDesignPresetId = null;
  }

  async function applyDesignPreset() {
    const presetId = pendingDesignPresetId;
    if (!presetId || !BRAND || !currentPreview) return;
    const summary = designPresetChangeSummary(presetId);
    if (!summary) return;
    brandUndoSnapshot = currentBrandDraft();
    const previousVisibility = normalizeSectionVisibility(currentPreview.sectionVisibility);
    designPresetUndoVisibility = Object.assign({}, previousVisibility);
    replaceBrandDraft(summary.result.draft);
    if (summary.result.sectionChanges.length) {
      const visibility = normalizeSectionVisibility(Object.assign({}, previousVisibility));
      summary.result.sectionChanges.forEach((change) => { visibility[change.sectionId] = change.visible; });
      await saveSectionVisibilityForPreset(visibility, previousVisibility);
    }
    closeDesignPresetModal();
    renderPublicPreview(currentPreview);
    persistTextChangesAfterApply({});
    const pane = document.querySelector('[data-builder-panel-pane="brand"]');
    if (pane) syncBrandPane(pane);
    toast(`「${summary.preset.label}」 프리셋을 적용했습니다. 상단 발행 버튼으로 저장하세요.`, 'fa-palette');
  }

  /** 토글과 같은 두 갈래(발행 전 pending / 발행 후 PUT)로 섹션 표시 상태를 저장한다. */
  async function saveSectionVisibilityForPreset(visibility, previousVisibility) {
    currentPreview.sectionVisibility = visibility;
    applySectionVisibilityState(visibility);
    if (!currentPreview.publicPathUrl) {
      persistPendingSectionVisibility(visibility);
      return;
    }
    try {
      const res = await fetch('/api/tutor/academy/page-settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(pageSettingsWriteBody(currentPreview.pageSettings, { sectionVisibility: visibility }))
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      currentPreview.sectionVisibility = normalizeSectionVisibility(
        (academy.pageSettings && academy.pageSettings.sectionVisibility) || visibility
      );
      applySectionVisibilityState(currentPreview.sectionVisibility);
    } catch (_) {
      currentPreview.sectionVisibility = previousVisibility;
      designPresetUndoVisibility = null;
      applySectionVisibilityState(previousVisibility);
      toast('섹션 표시 설정 저장에 실패했습니다.', 'fa-triangle-exclamation');
    }
  }

  function wireDesignPresetModal() {
    const modal = document.getElementById('builderDesignPresetModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-design-preset-close]').forEach((button) => {
      button.addEventListener('click', closeDesignPresetModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeDesignPresetModal();
    });
    const apply = document.getElementById('builderDesignPresetApplyBtn');
    if (apply) apply.addEventListener('click', applyDesignPreset);
  }

  // ── 상담 신청 CTA 편집 (P0-3) ─────────────────────────────────────────
  // sec-cta 섹션의 버튼(data-cta-edit)을 눌러 전화번호·카카오톡 URL을 입력한다.
  function wireCtaEditing() {
    const pageContent = document.getElementById('pageContent');
    if (!pageContent || pageContent.dataset.ctaWired === 'true') return;
    pageContent.dataset.ctaWired = 'true';
    pageContent.addEventListener('click', (event) => {
      const target = event.target && event.target.closest
        ? event.target.closest('[data-cta-edit]')
        : null;
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      openCtaModal();
    });
    wireCtaModal();
  }

  function wireCtaModal() {
    const modal = document.getElementById('builderCtaModal');
    if (!modal || modal.dataset.wired === 'true') return;
    modal.dataset.wired = 'true';
    modal.querySelectorAll('[data-cta-close]').forEach((button) => {
      button.addEventListener('click', closeCtaModal);
    });
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeCtaModal();
    });
    const apply = document.getElementById('builderCtaApplyBtn');
    if (apply) apply.addEventListener('click', applyCtaFromModal);
  }

  function openCtaModal() {
    if (!currentPreview) {
      toast('랜딩에서 학원 정보를 입력한 뒤 CTA를 편집할 수 있습니다.', 'fa-circle-info');
      return;
    }
    const modal = document.getElementById('builderCtaModal');
    const phoneInput = document.getElementById('builderCtaPhoneInput');
    const kakaoInput = document.getElementById('builderCtaKakaoInput');
    if (!modal || !phoneInput || !kakaoInput) return;
    const draft = (currentPreview.pageSettings && currentPreview.pageSettings.builderDraft) || {};
    phoneInput.value = draft.ctaPhone || '';
    kakaoInput.value = draft.ctaKakaoUrl || '';
    const businessPhone = currentPreview.pageSettings && currentPreview.pageSettings.businessInfo
      && currentPreview.pageSettings.businessInfo.phone;
    phoneInput.placeholder = businessPhone
      ? `비우면 사업자 정보의 전화번호(${businessPhone}) 사용`
      : '비우면 사업자 정보의 전화번호 사용';
    clearCtaError();
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(() => phoneInput.focus(), 30);
  }

  function closeCtaModal() {
    const modal = document.getElementById('builderCtaModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    clearCtaError();
  }

  function setCtaError(message) {
    const error = document.getElementById('builderCtaError');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }

  function clearCtaError() {
    const error = document.getElementById('builderCtaError');
    if (!error) return;
    error.textContent = '';
    error.hidden = true;
  }

  async function applyCtaFromModal() {
    const phoneInput = document.getElementById('builderCtaPhoneInput');
    const kakaoInput = document.getElementById('builderCtaKakaoInput');
    const apply = document.getElementById('builderCtaApplyBtn');
    if (!phoneInput || !kakaoInput || !currentPreview) return;
    const phone = phoneInput.value.trim();
    const kakaoUrl = kakaoInput.value.trim();
    if (kakaoUrl && !/^https?:\/\//i.test(kakaoUrl)) {
      setCtaError('카카오톡 URL은 http:// 또는 https://로 시작해야 합니다.');
      return;
    }
    if (phone && !/[0-9]/.test(phone.replace(/[^0-9]/g, ''))) {
      setCtaError('전화번호에 숫자가 포함되어야 합니다.');
      return;
    }
    const patch = { ctaPhone: phone, ctaKakaoUrl: kakaoUrl };
    const previousDraft = Object.assign({},
      currentPreview.pageSettings && currentPreview.pageSettings.builderDraft);
    const merged = Object.assign({}, previousDraft, patch);
    const pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { builderDraft: merged });
    currentPreview.pageSettings = pageSettings;

    if (!currentPreview.publicPathUrl) {
      persistPendingTextChanges(currentPreview);
      renderPublicPreview(currentPreview, 'sec-cta');
      closeCtaModal();
      toast('발행 전 미리보기에 CTA 연결 정보를 반영했습니다.', 'fa-phone-volume');
      return;
    }

    setSaving(apply, true);
    try {
      const res = await fetch('/api/tutor/academy/page-settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(pageSettingsWriteBody(currentPreview.pageSettings, { builderDraft: merged }))
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      syncPreviewFromAcademy(academy);
      applyAcademyToLivePreview(currentPreview, 'sec-cta');
      closeCtaModal();
      toast('CTA 연결 정보를 저장했습니다.', 'fa-phone-volume');
    } catch (_) {
      currentPreview.pageSettings = Object.assign({}, currentPreview.pageSettings || {}, { builderDraft: previousDraft });
      setCtaError('CTA 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSaving(apply, false);
    }
  }

  // ── 발행 전 검증 (P0-4) ───────────────────────────────────────────────
  // 학원명·전화번호는 필수. CTA 섹션이 켜져 있으면 전화번호와 카카오톡 URL이 둘 다 있어야 한다.
  function validateBeforePublish(publishRequest) {
    const errors = [];
    const request = publishRequest || {};
    const pageSettings = request.pageSettings || {};
    const name = String(request.name || '').trim();
    if (!name) errors.push('학원명을 입력해 주세요.');
    const businessInfo = pageSettings.businessInfo || {};
    const draft = pageSettings.builderDraft || {};
    const phone = String(draft.ctaPhone || businessInfo.phone || '').trim();
    const phoneDigits = phone.replace(/[^0-9]/g, '');
    // 마스킹 dummy(000-0000-0000 등 0만 있는 값)는 전화번호가 아니다
    if (!phoneDigits || /^0+$/.test(phoneDigits)) {
      errors.push('전화번호를 입력해 주세요. (사업자 정보 또는 CTA 설정에서 입력)');
    }
    const visibility = normalizeSectionVisibility(pageSettings.sectionVisibility);
    const ctaOn = visibility['sec-cta'] === true;
    if (ctaOn) {
      if (!phoneDigits) errors.push('CTA 섹션이 켜져 있습니다. 전화번호를 입력해 주세요.');
      const kakaoUrl = String(draft.ctaKakaoUrl || '').trim();
      if (!kakaoUrl) errors.push('CTA 섹션이 켜져 있습니다. 카카오톡 채널 URL을 입력해 주세요.');
      else if (!/^https?:\/\//i.test(kakaoUrl)) errors.push('카카오톡 채널 URL은 http:// 또는 https://로 시작해야 합니다.');
    }
    return { ok: errors.length === 0, errors };
  }

  window.F2RAcademyBuilderTestHooks = window.F2RAcademySectionOrderTestHooks = Object.freeze({
    normalizeSectionVisibility,
    normalizeSectionOrder,
    orderedSectionIds,
    visibleSectionIds(academy) {
      return orderedSectionIds(academy).filter((id) => academySectionVisible(academy, id));
    },
    layoutSectionIds,
    academyUpdateRequest,
    pageSettingsWriteBody,
    publishRequestFromPending,
    displayUrlForPreview,
    nextSectionOrder,
    textHighlightSelectors,
    featuredLessonIds: SECTIONS.featuredLessonIds,
    selectedFeaturedLessons: SECTIONS.selectedFeaturedLessons,
    // P0: 발행 전 검증 + 디자인 프리셋 변경 요약(단위 테스트용)
    validateBeforePublish,
    designPresetChangeSummary,
    humanizeDesignValue
  });

  // 렌더 결과 단언용 훅. academy_public.js의 동명 훅과 짝을 이루며, 두 렌더러가 같은 view에서
  // 같은 골격을 내는지 비교하는 데 쓴다. 빌더 렌더 함수는 전역 currentPreview/builderVideos를
  // 읽으므로 테스트가 그 둘을 주입할 수 있어야 한다.
  window.F2RAcademyRenderTestHooks = Object.freeze({
    renderGnb,
    renderHero,
    renderFeatured,
    renderAiProcess,
    renderIntro,
    renderPrincipal,
    renderPhilosophy,
    renderVideos,
    renderMiniLectures,
    renderTeachers,
    renderLessons,
    renderExternalServices,
    renderCta,
    renderFooter,
    escapeHtml,
    escapeAttr,
    setPreview(preview) { currentPreview = preview || null; },
    setVideos(list) { builderVideos = Array.isArray(list) ? list : []; },
    // 원장 소개/수업 영상 목록 분리 검증용 — 순수 필터라 DOM 없이도 단언할 수 있다.
    videosForPurpose,
    // 저장 전 업로드 슬롯. 캔버스가 로컬 objectURL로 미리보기를 그리는지 검증하는 데 쓴다.
    setPendingHeroMedia(map) { pendingHeroMedia = map || {}; },
    // 캔버스 재렌더가 편집 상태를 끊는지 검증하기 위한 접근자.
    renderPublicPreview,
    setActiveTextEditSection,
    getActiveTextEditSection() { return activeTextEditSection; },
    // Phase 4 AI Suggestion 워크플로 검증용. 절대 원칙: openAiSuggestionModal(생성)만으로는
    // currentPreview가 바뀌지 않는다 — applyAiSuggestionToBuilder를 호출해야만 바뀐다.
    // task/target은 이제 트리거 버튼의 data 속성에서 직접 오므로 openAiSuggestionModal이
    // 두 값을 인자로 받는다(더 이상 AI_ASSIST_SECTIONS 같은 간접 조회 테이블이 없다).
    openAiSuggestionModal,
    applyAiSuggestionToBuilder,
    getAiSuggestionState: () => aiSuggestionState,
    // Phase 4.2: 생성 전 "요구사항" 입력 + [제안 생성]/[다시 생성] 분리 검증용.
    requestAiSuggestion,
    loadCompletionCheck,
    renderCompletionCheck,
    // 실제 클릭 위임 배선. "AI로 다듬기" 버튼이 실제 렌더 마크업에서 클릭으로 도달 가능한지
    // (내부 함수를 직접 호출하는 우회가 아니라) 검증하는 회귀 테스트 전용 훅.
    wireAiAssistTriggers,
    wireAiSuggestionEditing,
    // Phase 5A: AI Academy Builder(전체 진단 + proposal). 절대 원칙 — requestAcademyProposal은
    // 읽기 전용이고, applyComposeProposalById / applyAllComposeProposals 를 호출해야만
    // currentPreview가 바뀐다. Publish는 어떤 경로로도 자동 실행되지 않는다.
    wireAcademyComposeModal,
    openAcademyComposeModal,
    requestAcademyProposal,
    renderAcademyProposal,
    applyComposeProposalById,
    applyAllComposeProposals,
    revertComposeChanges,
    getAcademyComposeState: () => academyComposeState,
    wireTextEditing,
    wirePreviewSectionSelection,
    enterSectionTextEditMode,
    // Phase 5B Direct Text Editing UX — 클릭 위치에 caret을 놓고 전체 선택은 하지 않는다.
    caretRangeFromPoint,
    placeCaretAtPoint,
    focusEditableTarget,
    // Phase 5B AI Guide. 절대 원칙 — requestAiGuideRecommendations는 읽기 전용이고,
    // applyAiGuideCandidate를 호출해야만 currentPreview가 바뀐다. Publish는 자동 실행되지 않는다.
    wireAiGuide,
    renderAiGuide,
    setAiGuideOpen,
    setAiGuideEnabled,
    setAiGuidePanelOpen,
    moveAiGuideStep,
    skipAiGuideStep,
    goAiGuideStep,
    requestAiGuideRecommendations,
    applyAiGuideCandidate,
    restartAiGuide,
    // §2 추천안 캐러셀 — 한 번에 1개만 노출. 여러 추천안을 세로로 쌓아 말풍선이 뷰포트 밖으로 밀리는 것을 막는다.
    stepAiGuideCandidate,
    getAiGuideCandidateIndex: () => aiGuideCandidateIndex,
    getAiGuideSession: () => aiGuideSession,
    getAiGuidePending: () => aiGuidePending,
    getAiGuideUiState: () => ({ enabled: aiGuideEnabled, panelOpen: aiGuidePanelOpen }),
    previewBrandProposal,
    applyBrandProposal,
    undoBrandDesign,
    wireBrandDesign,
    // Phase 5B.1b — Bildi Scroll Story waypoint positioning. Bildi 위치는 스크롤 progress에 따라
    // 실제로 이동해야 한다(고정 chatbot 금지). 아래 훅으로 좌표 이동을 직접 검증한다.
    getBildi: () => bildi,
    getBildiBubble: () => bildiBubble,
    bildiSafeArea,
    updateBildiFlight,
    refreshBildiFlight,
    orderedBildiSectionKeys,
    bildiWaypoint,
    getLastBildiFlightPos: () => lastBildiFlightPos
  });

  function readJson(key) {
    try { return JSON.parse(sessionStorage.getItem(key) || 'null'); } catch (_) { return null; }
  }
  function clearPending() {
    try { sessionStorage.removeItem(PENDING_KEY); } catch (_) {}
    try { sessionStorage.removeItem(PENDING_PREVIEW_KEY); } catch (_) {}
    try { sessionStorage.removeItem(PENDING_LOGO_KEY); } catch (_) {}
    pendingLogoFile = null;
    Object.keys(pendingHeroMedia).forEach(dropPendingHeroMedia);
  }
  function setText(selector, text) {
    const el = document.querySelector(selector);
    if (el && text) el.textContent = text;
  }
  function toast(msg, icon) {
    if (typeof window.showToast === 'function') window.showToast(msg, icon);
    else alert(msg);
  }

  window.scrollToId = function (id) {
    const root = document.getElementById('pageContent') || document;
    const el = root.querySelector ? root.querySelector('#' + cssEscape(id)) : null;
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };


  function normalizedYoutubeUrl(url) {
    const videoId = youtubeVideoId(url);
    return videoId ? 'https://www.youtube.com/watch?v=' + videoId : null;
  }

  function youtubeEmbedUrl(url) {
    const videoId = youtubeVideoId(url);
    return videoId ? 'https://www.youtube.com/embed/' + videoId : null;
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

  function validYoutubeId(value) {
    return /^[A-Za-z0-9_-]{11}$/.test(String(value || '')) ? value : null;
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }
})();
