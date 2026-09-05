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
    'sec-minilectures'
  ]);
  const OPT_IN_SECTIONS = new Set(OPT_IN_SECTION_IDS);
  const MANAGED_SECTION_IDS = [
    'sec-featured',
    'sec-intro',
    'sec-principal',
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
    wirePrincipalVideoEditing(preview);
    wirePrincipalCareerEditing(preview);
    wireIntroFeatureEditing(preview);
    wirePublicHandleEditing(preview);
    wireSocialUrlEditing(preview);
    wireConsultContactEditing();
    wireBusinessInfoEditing();
    wireLogoEditing(preview);
    wireBasicDataEditing(preview);
    wireVideoManagement();
    wireLessonScheduleToggle();
    wirePreviewSectionSelection();
    wireEditableGuide();
    wireHeaderSectionApply();
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
    if (highlightField || activeTextField) highlightTextTargets(highlightField || activeTextField);
    if (scrollToSection) scrollPreviewToSection(scrollToSection);
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
    if (id === 'sec-minilectures') return renderMiniLectures(academy);
    if (id === 'sec-digitaltwin') return renderAiProcess(academy);
    if (id === 'sec-lessons') return renderLessons(academy);
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
    const body = logoUrl
      ? `<img src="${escapeHtml(logoUrl)}" alt="${escapeHtml(academy.name)} 로고">`
      : `<span class="eh-logo-symbol-inner"><span class="eh-book"><span class="eh-letters">${escapeHtml(initials(academy.name))}</span></span></span>`;
    return `<span class="eh-logo-symbol" data-builder-logo="${context}" tabindex="0" title="클릭하여 로고 이미지 수정">${body}<span class="builder-logo-edit-chip"><i class="fa-solid fa-image"></i> 로고</span></span>`;
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
      event.preventDefault();
      event.stopPropagation();
      if (!enterSectionTextEditMode(section)) return;
      focusEditableTarget(target);
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
      pageContent.addEventListener('click', (event) => {
        const target = event.target && event.target.closest ? event.target.closest('[data-builder-logo]') : null;
        if (!target) return;
        event.preventDefault();
        event.stopPropagation();
        openLogoModal();
      });
      pageContent.addEventListener('keydown', (event) => {
        const target = event.target && event.target.closest ? event.target.closest('[data-builder-logo]') : null;
        if (!target || (event.key !== 'Enter' && event.key !== ' ')) return;
        event.preventDefault();
        openLogoModal();
      });
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
    appendAiAssistControl(controls, section);
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

  // ── AI Suggestion (Phase 4) ──────────────────────────────────────────────
  // 절대 원칙: 여기서 만드는 건 "제안"뿐이다. 생성만으로는 currentPreview.pageSettings도,
  // Academy DB도 절대 바뀌지 않는다 — 사용자가 이 모달의 "적용"을 눌러야만 기존 인라인 텍스트
  // 편집과 완전히 같은 경로(pageSettings.builderDraft 갱신 → populatePreview →
  // persistTextChangesAfterApply, 즉 발행 전이면 미리보기 반영, 발행 후면 "발행" 버튼으로
  // 저장 안내)로 반영된다. AI 전용 저장 경로를 새로 만들지 않는다.
  const AI_ASSIST_SECTIONS = {
    'sec-principal': { task: 'PRINCIPAL_INTRO', defaultTarget: 'principalDesc' },
    'sec-philosophy': {
      task: 'PHILOSOPHY_ITEM',
      defaultTarget: 'philosophyItem1',
      targets: ['philosophyItem1', 'philosophyItem2', 'philosophyItem3', 'philosophyItem4']
    }
  };
  let aiSuggestionState = null;

  function appendAiAssistControl(controls, section) {
    const sectionId = canonicalSectionId(section.dataset.builderSectionKey);
    const cfg = AI_ASSIST_SECTIONS[sectionId];
    if (!cfg || !currentPreview) return;
    controls.appendChild(
      sectionControlButton('fa-wand-magic-sparkles', 'AI로 다듬기', '', () => openAiSuggestionModal(cfg)));
  }

  /** 철학 항목처럼 target이 여러 개인 Task는 지금 커서가 있는 항목을 우선한다. */
  function activeAiTarget(cfg) {
    if (!cfg.targets) return cfg.defaultTarget;
    const active = activeTextField && activeTextField.indexOf('draft:') === 0
      ? activeTextField.slice('draft:'.length)
      : null;
    return active && cfg.targets.indexOf(active) !== -1 ? active : cfg.defaultTarget;
  }

  function openAiSuggestionModal(cfg) {
    const modal = document.getElementById('builderAiSuggestionModal');
    if (!modal || !currentPreview) return;
    const target = activeAiTarget(cfg);
    const currentValue = draftText(currentPreview, target, '');
    aiSuggestionState = { task: cfg.task, target, currentValue, suggestedValue: null };
    setAiSuggestionCurrentText(currentValue);
    setAiSuggestionResultText('');
    clearAiSuggestionError();
    setAiSuggestionApplyEnabled(false);
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    requestAiSuggestion();
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

  function setAiSuggestionLoading(loading) {
    const loadingEl = document.getElementById('builderAiSuggestionLoading');
    const regenBtn = document.getElementById('builderAiSuggestionRegenerateBtn');
    if (loadingEl) loadingEl.hidden = !loading;
    if (regenBtn) regenBtn.disabled = loading;
    setAiSuggestionApplyEnabled(false);
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

  /** "다시 생성"도 이 함수를 그대로 다시 호출한다 — 새 suggestion으로 교체될 뿐 어떤 데이터도 저장하지 않는다. */
  async function requestAiSuggestion() {
    if (!aiSuggestionState) return;
    const requested = aiSuggestionState;
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
          currentValue: requested.currentValue
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
    if (regenerate) regenerate.addEventListener('click', requestAiSuggestion);
    const apply = document.getElementById('builderAiSuggestionApplyBtn');
    if (apply) apply.addEventListener('click', applyAiSuggestionToBuilder);
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

  function focusEditableTarget(target) {
    if (!target) return;
    target.classList.remove('builder-text-invalid');
    target.setAttribute('contenteditable', 'true');
    target.focus();
    selectEditableText(target);
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
    const withMissing = filtered.concat(DEFAULT_SECTION_ORDER.filter((id) => !seen.has(id)));
    if (withMissing[0] !== 'sec-hero') {
      return DEFAULT_SECTION_ORDER.slice();
    }
    return withMissing;
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
    selectedFeaturedLessons: SECTIONS.selectedFeaturedLessons
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
    renderLessons,
    renderExternalServices,
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
    openAiSuggestionModal,
    applyAiSuggestionToBuilder,
    getAiSuggestionState: () => aiSuggestionState,
    aiAssistSectionsForTest: AI_ASSIST_SECTIONS,
    loadCompletionCheck,
    renderCompletionCheck
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
