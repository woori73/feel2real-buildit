(function () {
  const FUTURE_SECTION_IDS = new Set([
    'sec-reviews',
    'sec-curriculum',
    'sec-effect',
    'sec-market',
    'sec-payment'
  ]);
  // Education Storefront 신규 섹션 — 명시적 true일 때만 렌더한다(기본 OFF, 기존 사이트 불변).
  const OPT_IN_SECTION_IDS = new Set([
    'sec-featured',
    'sec-digitaltwin',
    'sec-minilectures',
    'sec-cta'
  ]);
  if (!window.F2RAcademyBusinessInfo) {
    throw new Error('F2RAcademyBusinessInfo must be loaded before academy_public.js');
  }
  if (!window.F2RAcademySections) {
    throw new Error('F2RAcademySections must be loaded before academy_public.js');
  }
  const BUSINESS_INFO = window.F2RAcademyBusinessInfo;
  // 섹션 마크업 정본. 공개/빌더가 같은 모듈을 쓰고 화면 차이는 ctx가 흡수한다.
  const SECTIONS = window.F2RAcademySections;
  const BRAND = typeof window !== 'undefined' ? window.F2RAcademyBrand : null;
  // deps는 지연 평가한다 — slug는 이 시점에 아직 계산 전이고, 쇼케이스 모듈은 정본 모듈과
  // 다른 컨텍스트에 있을 수 있다.
  // 작업본 미리보기의 사진 캐시 무효화용. 렌더(load/postMessage)마다 새로 매긴다.
  let mediaVersion = String(Date.now());
  const PUBLIC_CTX = SECTIONS.createPublicContext({
    workingCopyMedia: () => WORKING_COPY_PREVIEW,
    mediaVersion: () => mediaVersion,
    slug: () => slug,
    showcase: () => window.F2RLessonProductShowcase,
    videos: () => academyVideos
  });
  const sectionView = (academy) => SECTIONS.toSectionView(academy);
  const root = document.getElementById('publicView');
  const errorMsg = document.getElementById('errorMsg');
  // 서브도메인 root 렌더는 data-slug로 academy를 주입받는다(URL path가 '/'라 pathname 파싱 불가).
  // 없으면(경로형 /academies/{slug}) 기존처럼 pathname에서 slug를 뽑는다.
  let lastSectionOrder = [];
  // sec-videos 렌더 소스. AcademyResponse에 싣지 않고 별도 endpoint로 받는다 —
  // 목록 API(listPublic)까지 영상이 딸려가 N+1이 나는 것을 피하기 위해서다.
  let academyVideos = [];
  // 상담 신청 모달에 띄울 학원 연락 수단. render()마다 businessInfo에서 다시 뽑는다.
  let consultChannels = [];
  // AI Native pipeline canvas(660×460 좌표계)를 stage 폭에 맞추는 resize 리스너를 한 번만 건다.
  let aiNativeFitBound = false;
  // 편집기의 P0 불러오기 전용: 게시 상태와 관계없이 P0 원본을 렌더한다.
  const FORCE_P0_RENDER = new URLSearchParams(location.search).get('ve-source') === 'p0';
  // 작업본 비공개 미리보기(/tutor/academy/preview): 작업본 API에서 읽고, 방문 통계를 기록하지 않는다.
  const WORKING_COPY_PREVIEW = !!(root && root.dataset.preview === 'working-copy');
  const slug = (root && root.dataset.slug)
    ? root.dataset.slug
    : decodeURIComponent(location.pathname.split('/').filter(Boolean).pop() || '');
  window.addEventListener('message', (event) => {
    if (event.origin !== location.origin) return;
    const data = event.data || {};
    if (data.type !== 'F2R_BUILDER_APPLY_ACADEMY' || !data.academy) return;
    mediaVersion = String(Date.now());
    render(data.academy, true);
    scrollToManagedSection(data.scrollToSection);
  });

  if (!window.F2R_ACADEMY_SECTION_ORDER_TEST) {
    load();
  }

  async function load() {
    mediaVersion = String(Date.now());
    try {
      const res = await fetch(WORKING_COPY_PREVIEW
        ? ((root && root.dataset.previewApi) || '/api/tutor/academy/working-copy/preview')
        : `/api/academies/${encodeURIComponent(slug)}`);
      if (!res.ok) throw new Error('공개 Academy를 찾을 수 없습니다.');
      const academy = await res.json();
      academyVideos = await loadVideos();
      window.F2RAcademyPublicVideos = academyVideos;
      // 시각 편집기 custom 모드: published HTML을 직접 렌더한다.
      // 실패하거나 게시본이 없으면 기존 P0 렌더로 폴백한다.
      // ?ve-source=p0 는 편집기의 "기존 홈페이지 불러오기"가 P0 원본을 읽기 위한 강제 P0 렌더다.
      if (!FORCE_P0_RENDER && !WORKING_COPY_PREVIEW && renderModeOf(academy) === 'custom') {
        const rendered = await renderCustomHomepage(academy);
        if (rendered) return;
      }
      render(academy, await isOwnAcademy(academy.slug));
    } catch (err) {
      errorMsg.textContent = err.message || 'Academy 조회에 실패했습니다.';
    }
  }

  /**
   * BuildIt 시각 편집기 custom 렌더 모드.
   *
   * <p>pageSettings.builderDraft["homepage.renderMode"]가 "custom"일 때 호출된다.
   * GET /api/academies/{slug}/homepage에서 게시된 HTML/CSS를 받아
   * 섹션 영역(Shadow DOM)에 삽입한다. GNB와 푸터는 P0 것을 유지하고, tenant 스크립트
   * (캠페인 팝업·CTA 등)는 script 태그 로더이므로 그대로 동작한다.
   *
   * <p>게시본이 없거나 조회 실패 시 false를 반환하여 P0 렌더로 폴백한다.
   */
  function renderModeOf(academy) {
    const draft = academy && academy.pageSettings && academy.pageSettings.builderDraft;
    const mode = draft && draft['homepage.renderMode'];
    return mode === 'custom' ? 'custom' : 'template';
  }

  async function renderCustomHomepage(academy) {
    try {
      const RENDER = window.F2RVisualEditorRender;
      if (!RENDER) return false;
      // slug 엔드포인트: handle이 없는 기존 학원도 동일하게 조회된다.
      const key = academy.slug || slug;
      const res = await fetch(
        `/api/academies/${encodeURIComponent(key)}/homepage`);
      if (!res.ok) return false;
      const data = await res.json();
      if (!data.html) return false;

      if (BRAND) BRAND.apply(root, academy);

      const ownAcademy = await isOwnAcademy(academy.slug);
      const topBtn = ownAcademy
        ? '<a class="cv-btn-owner" href="/tutor/academy">학원 설정으로</a>'
        : '<button class="cv-btn-primary" onclick="requestConsult()">상담 신청</button>';
      consultChannels = BUSINESS_INFO.contactChannels(
        academy.pageSettings && academy.pageSettings.businessInfo);

      // GNB와 푸터는 P0 것을 유지하고, 섹션 영역만 편집기 HTML로 교체한다.
      // 편집기 HTML/CSS는 Shadow DOM에 격리해 그린다: P0 사이트 CSS(제목·문단 여백 0 등)가
      // 편집 결과를 덮어쓰지 않고, 편집기 CSS(* / body 규칙)가 P0 GNB·푸터로 새지도 않는다.
      root.innerHTML =
        renderGnb(academy, topBtn)
        + '<div id="ve-custom-body"></div>'
        + renderFooter(academy);
      await RENDER.mount(root.querySelector('#ve-custom-body'), data.html, data.css);
      void loadVisitStatistics(academy, ownAcademy);
      return true;
    } catch (_) {
      return false;
    }
  }

  /** 영상 조회 실패가 페이지 전체를 막지 않도록 빈 목록으로 떨어뜨린다(준비중 placeholder로 렌더된다). */
  async function loadVideos() {
    try {
      const res = await fetch(`/api/academies/${encodeURIComponent(slug)}/videos`);
      if (!res.ok) return [];
      const videos = await res.json();
      return Array.isArray(videos) ? videos : [];
    } catch (_) {
      return [];
    }
  }

  async function isOwnAcademy(publicSlug) {
    const user = currentUser();
    if (!user || user.role !== 'TUTOR') return false;
    try {
      const res = await fetchAuth('/api/tutor/academy/me');
      if (!res.ok) return false;
      const mine = await res.json();
      return mine.slug === publicSlug;
    } catch (_) {
      return false;
    }
  }

  function render(academy, ownAcademy) {
    if (BRAND) BRAND.apply(root, academy);
    const primarySubject = (academy.subjects && academy.subjects[0])
      ? (academy.subjects[0].nameKo || academy.subjects[0].nameEn) : '';
    const regionText = (academy.regions && academy.regions[0]) ? academy.regions[0].displayName : '';
    // 소유자=관리 허브로, 방문자=상담 신청. 우상단 버튼 1개 스왑(AC-8/AC-9).
    const topBtn = ownAcademy
      ? '<a class="cv-btn-owner" href="/tutor/academy">학원 설정으로</a>'
      : '<button class="cv-btn-primary" onclick="requestConsult()">상담 신청</button>';

    consultChannels = BUSINESS_INFO.contactChannels(
      academy.pageSettings && academy.pageSettings.businessInfo);
    lastSectionOrder = orderedSectionIds(academy);
    root.innerHTML =
      renderGnb(academy, topBtn)
      + renderOrderedSections(academy, primarySubject, regionText)
      + renderFooter(academy);
    SECTIONS.applyImageMediaAspect(root);
    wireReveal();
    wireSceneFlow();
    wireCinematic();
    wireContentCollections();
    wireAiNative();
    wireFeaturedCoverflow();
    wireHeroTabletCycle();
    wirePhilosophyStep();
    void loadVisitStatistics(academy, ownAcademy);
  }

  /**
   * AI Native Panorama(sec-digitaltwin) — GSAP+ScrollTrigger 는 academy_cinematic.js(F2RCinematic)
   * 한 곳에만 있다. 여기서는 호출만 한다. 모듈 미로드 / reduced-motion / 모바일 → no-op 이고
   * 섹션은 CSS 로 정적 판독 가능(폴백은 wireAiNative IO).
   */
  function wireCinematic() {
    const CINEMATIC = window.F2RCinematic;
    if (!CINEMATIC || !root) return;
    CINEMATIC.wire(root);
  }

  /**
   * Vertical Scene Flow — 세로 스크롤에 따라 주요 섹션이 enter → active → exit(이전 섹션 흐려짐).
   * academy_scene.js(F2RSceneFlow)가 IntersectionObserver 로 data-scene 을 주고 academy_scene.css
   * 가 opacity/translateY 로 표현한다. 모듈 미로드·reduced-motion·IO 미지원 → 즉시 active(정적).
   * CSR 재렌더마다 호출 — F2RSceneFlow.wire 가 이전 IO 를 끊는다.
   * (AI Native 애니메이션과 별개: sec-digitaltwin 이 active 면 opacity 1 로 선명. sec-featured
   *  가로 학교 카드 스와이프는 세로 rect 를 안 바꿔 영향 없음.)
   */
  const SCENE_FLOW_IDS = [
    'sec-featured', 'sec-digitaltwin', 'sec-videos',
    'sec-minilectures', 'sec-lessons', 'sec-intro', 'sec-social'
  ];
  function wireSceneFlow() {
    const SF = window.F2RSceneFlow;
    if (!SF || typeof SF.wire !== 'function' || !root) return;
    const sections = SCENE_FLOW_IDS
      .map((id) => root.querySelector('#' + id))
      .filter(Boolean);
    SF.wire(sections);
  }

  /**
   * 스크롤 모션 — 단 하나의 primitive(academy_reveal.js). translateY + opacity → 최종 상태,
   * 1회성, 형제 stagger, reveal 후 unobserve, reduced-motion → 즉시 최종. 지나간 섹션은
   * 건드리지 않는다(흐리게/축소 없음). CSR 재렌더마다 호출, 이미 배선한 그룹은 건너뛴다.
   */
  function wireReveal() {
    const REVEAL = window.F2RReveal;
    if (!REVEAL || !root) return;
    REVEAL.wire(root);
  }

  /**
   * Horizontal Content Collection(sec-lessons / sec-videos 가로 레이아웃)의 배선.
   * academy_collection.js primitive를 쓴다 — 화살표 스크롤 + 비디오 카드 인라인 재생 + 진행 카운터만.
   * 자동 이동 없음(Shopify 레퍼런스 확정). 데스크톱은 CSS grid, 모바일은 네이티브 swipe/scroll-snap.
   * 모듈이 없으면(로드 실패) 아무것도 하지 않고 콘텐츠는 정적 shelf로 완전히 보인다.
   */
  function wireContentCollections() {
    const COLLECTION = window.F2RAcademyCollection;
    if (!COLLECTION || !root) return;
    COLLECTION.wire(root);
  }

  /**
   * AI Native pipeline canvas 핏 + 스크롤 트리거 애니메이션 (승인된 Dynamic Process)
   * - Canvas scale fit (기존)
   * - 단계별 스태거 진입 (IntersectionObserver)
   * - 연결선 애니메이션 (데스크톱 수평 / 모바일 수직)
   * - Digital Twin 뉴럴 브레인 펄스 (GSAP 연동 또는 CSS 폴백)
   * - FINAL 완료 이펙트 (스프링 애니메이션)
   * - prefers-reduced-motion 대응
   */
  function wireAiNative() {
    if (!root) return;
    const section = root.querySelector('#sec-digitaltwin');
    if (!section) return;

    // Canvas fit (기존)
    const stage = section.querySelector('[data-ai-stage]');
    const canvas = stage && stage.querySelector('[data-ai-canvas]');
    if (stage && canvas) {
      const fit = () => {
        const w = stage.clientWidth || 0;
        if (!w) return;
        const mobile = (window.innerWidth || document.documentElement.clientWidth || 0) < 900;
        const base = mobile ? 330 : 660;
        const boxH = mobile ? 560 : 460;
        const s = w / base;
        canvas.style.transform = 'scale(' + s + ')';
        stage.style.height = (boxH * s) + 'px';
      };
      fit();
      if (!aiNativeFitBound) {
        aiNativeFitBound = true;
        window.addEventListener('resize', fit);
      }
    }

    // 스크롤 트리거 단계 애니메이션
    if (section.dataset.aiNativeWired === 'true') return;
    section.dataset.aiNativeWired = 'true';

    const steps = Array.prototype.slice.call(section.querySelectorAll('.ai-native-step'));
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const isMobile = window.innerWidth < 900;

    if (reduceMotion) {
      steps.forEach((s) => s.classList.add('is-visible', 'is-active'));
      const finalStep = section.querySelector('[data-ai-step="4"]');
      if (finalStep) finalStep.classList.add('is-final-active');
      return;
    }

    // IntersectionObserver로 단계별 스태거 진입
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const step = entry.target;
          const index = parseInt(step.getAttribute('data-ai-step-index') || step.getAttribute('data-ai-step'), 10);
          const delay = index * 150;
          setTimeout(() => {
            step.classList.add('is-visible');
            step.classList.add('is-active');
            // FINAL 단계 완료 이펙트
            if (index === 4) {
              setTimeout(() => step.classList.add('is-final-active'), 400);
            }
          }, delay);
          observer.unobserve(step);
        }
      });
    }, { threshold: 0.3, rootMargin: '-50px' });

    steps.forEach((step) => observer.observe(step));

    // 모바일: 수직 연결선 애니메이션
    if (isMobile) {
      const connectionObserver = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const step = entry.target;
            const index = parseInt(step.getAttribute('data-ai-step-index') || step.getAttribute('data-ai-step'), 10);
            if (index > 0) {
              const prevStep = section.querySelector('[data-ai-step-index="' + (index - 1) + '"]');
              if (prevStep) prevStep.classList.add('is-active');
            }
            connectionObserver.unobserve(step);
          }
        });
      }, { threshold: 0.5 });
      steps.forEach((step) => connectionObserver.observe(step));
    }
  }

  /**
   * 「2학기 영어 내신」 섹션(sec-featured) — 커버플로우 캐러셀 (승인된 UI, 21st.dev @ruixen.ui/coverflow-carousel 스타일)
   * 중앙 카드 확대(1.15x), 좌우 카드 축소(0.85x) + 3D Y축 회전 + 투명도 그라데이션
   * 데스크톱: 마우스 드래그/휠 + 화살표 + 인디케이터
   * 모바일: 터치 스와이프 + 인디케이터
   * 각 카드 = 그 학교의 실제 /api/analyzes 출제자 Digital Twin
   * 배선: #sec-featured 안에서만.
   */
  function wireFeaturedCoverflow() {
    if (!root) return;
    const section = root.querySelector('#sec-featured');
    if (!section || section.dataset.coverflowWired === 'true') return;
    section.dataset.coverflowWired = 'true';

    const track = section.querySelector('[data-coverflow-track]');
    const viewport = section.querySelector('[data-coverflow-viewport]');
    const cards = Array.prototype.slice.call(section.querySelectorAll('[data-coverflow-card]'));
    const indicators = Array.prototype.slice.call(section.querySelectorAll('[data-coverflow-indicator]'));
    const prevBtn = section.querySelector('[data-coverflow-prev]');
    const nextBtn = section.querySelector('[data-coverflow-next]');

    if (!track || cards.length === 0) return;

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let current = 0;
    let animating = false;
    let dragStartX = null;
    let dragStartPos = 0;
    let isDragging = false;

    // 카드 너비 계산 (카드 + gap)
    function getCardWidth() {
      const card = cards[0];
      const style = window.getComputedStyle(card);
      const cardWidth = card.offsetWidth;
      const gap = parseFloat(window.getComputedStyle(track).gap) || 24;
      return cardWidth + gap;
    }

    // 트랙 위치 업데이트 (중앙 카드를 뷰포트 중앙에 위치)
    function updateTrackPosition() {
      const cardWidth = getCardWidth();
      const viewportWidth = viewport.clientWidth;
      const offset = (viewportWidth - cards[0].offsetWidth) / 2;
      const translateX = -current * getCardWidth() + offset;
      track.style.transform = 'translateX(' + translateX + 'px)';
    }

    // 카드 위치 클래스 업데이트
    function updateCardPositions() {
      cards.forEach((card, i) => {
        const diff = i - current;
        card.classList.remove('is-center', 'is-side', 'is-side-left', 'is-side-right', 'is-far', 'is-far-left', 'is-far-right');
        if (diff === 0) {
          card.classList.add('is-center');
        } else if (Math.abs(diff) === 1) {
          card.classList.add('is-side');
          card.classList.add(diff < 0 ? 'is-side-left' : 'is-side-right');
        } else {
          card.classList.add('is-far');
          card.classList.add(diff < 0 ? 'is-far-left' : 'is-far-right');
        }
      });
    }

    // 인디케이터 업데이트
    function updateIndicators() {
      indicators.forEach((ind, i) => {
        const isOn = i === current;
        ind.classList.toggle('is-on', isOn);
        ind.setAttribute('aria-selected', isOn);
      });
    }

    // 버튼 상태 업데이트
    function updateButtons() {
      if (prevBtn) prevBtn.disabled = current === 0;
      if (nextBtn) nextBtn.disabled = current === cards.length - 1;
    }

    // 카드 데이터 로드 (학교별 출제자 Digital Twin)
    function loadCardData(card, index) {
      const school = card.getAttribute('data-school');
      const body = card.querySelector('[data-coverflow-card-body]');
      const more = card.querySelector('[data-coverflow-card-more]');
      if (!school || !body) return;

      fetch('/api/analyzes?schoolName=' + encodeURIComponent(school))
        .then((r) => (r.ok ? r.json() : []))
        .then((raw) => (Array.isArray(raw) ? raw : []).filter((a) => {
          const s = a.subject || {};
          return s.nameEn === 'english' || s.nameKo === '영어' || (!s.nameEn && !s.nameKo);
        }))
        .catch(() => [])
        .then((list) => {
          if (!list.length) {
            body.innerHTML = '<p class="coverflow-card-loading">' + escapeHtml(school)
              + ' 출제 분석을 준비 중입니다.</p>';
            return;
          }
          const rows = list.slice(0, 4).map((a) => {
            const label = escapeHtml(a.grade) + '학년 · ' + escapeHtml(a.semester) + ' · ' + escapeHtml(a.examType);
            const href = a.detailUrl ? escapeAttr(a.detailUrl) : '';
            return '<li class="coverflow-card-row">'
              + (href
                ? '<a class="coverflow-card-rowlink" href="' + href + '" target="_blank" rel="noopener">'
                  + label + '<span class="coverflow-card-rowgo" aria-hidden="true">›</span></a>'
                : '<span class="coverflow-card-rowlink is-plain">' + label + '</span>')
              + '</li>';
          }).join('');
          body.innerHTML =
            '<ul class="coverflow-card-rows">' + rows + '</ul>';
          if (more && list[0] && list[0].detailUrl) more.setAttribute('href', list[0].detailUrl);
        });
    }

    // 모든 카드 데이터 로드
    cards.forEach((card, i) => loadCardData(card, i));

    function goTo(index, direction) {
      if (animating || index < 0 || index >= cards.length || index === current) return;
      animating = true;
      current = index;
      updateCardPositions();
      updateTrackPosition();
      updateIndicators();
      updateButtons();
      setTimeout(() => { animating = false; }, reduceMotion ? 0 : 500);
    }

    function goPrev() { goTo(current - 1, 'prev'); }
    function goNext() { goTo(current + 1, 'next'); }

    // 화살표 버튼
    if (prevBtn) prevBtn.addEventListener('click', goPrev);
    if (nextBtn) nextBtn.addEventListener('click', goNext);

    // 인디케이터 클릭
    indicators.forEach((ind, i) => {
      ind.addEventListener('click', () => goTo(i));
      ind.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowRight' && i < cards.length - 1) { e.preventDefault(); goTo(i + 1); indicators[i + 1].focus(); }
        else if (e.key === 'ArrowLeft' && i > 0) { e.preventDefault(); goTo(i - 1); indicators[i - 1].focus(); }
      });
    });

    // 터치/마우스 드래그
    function onDragStart(clientX) {
      dragStartX = clientX;
      dragStartPos = current;
      isDragging = true;
      track.style.cursor = 'grabbing';
    }

    function onDragMove(clientX) {
      if (!isDragging || dragStartX === null) return;
      const dx = dragStartX - clientX;
      const threshold = 60;
      if (dx > threshold && current < cards.length - 1) {
        goNext();
        dragStartX = clientX;
      } else if (dx < -threshold && current > 0) {
        goPrev();
        dragStartX = clientX;
      }
    }

    function onDragEnd() {
      if (isDragging) {
        isDragging = false;
        dragStartX = null;
        track.style.cursor = 'grab';
      }
    }

    // 터치 이벤트
    track.addEventListener('touchstart', (e) => onDragStart(e.changedTouches[0].clientX), { passive: true });
    track.addEventListener('touchmove', (e) => onDragMove(e.changedTouches[0].clientX), { passive: true });
    track.addEventListener('touchend', onDragEnd, { passive: true });

    // 마우스 이벤트 (데스크톱)
    track.addEventListener('mousedown', (e) => {
      if (e.button !== 0) return;
      onDragStart(e.clientX);
      e.preventDefault();
    });
    window.addEventListener('mousemove', (e) => onDragMove(e.clientX), { passive: true });
    window.addEventListener('mouseup', onDragEnd, { passive: true });

    // 휠 스크롤 (데스크톱)
    viewport.addEventListener('wheel', (e) => {
      if (animating) return;
      if (e.deltaY > 0) goNext();
      else if (e.deltaY < 0) goPrev();
      e.preventDefault();
    }, { passive: false });

    // 리사이즈 시 트랙 위치 재계산
    window.addEventListener('resize', () => {
      if (!animating) updateTrackPosition();
    });

    // 초기화
    updateCardPositions();
    updateTrackPosition();
    updateIndicators();
    updateButtons();
  }


  /**
   * Hero 섹션 우측 태블릿 프레임 — 원장 사진 ↔ 영상 순환 재생
   * 사진 2초 → 영상 자동재생(음소거) → 영상 종료 후 사진 2~3초 → 반복
   * 전환은 fade, 영상 프레임 내 🔇/🔊 버튼으로 음소거 토글, 사용자 선택 상태 반복 시 유지
   */
  function wireHeroTabletCycle() {
    if (!root) return;
    const tablet = root.querySelector('.tablet');
    if (!tablet) return;
    if (tablet.dataset.heroCycleWired === 'true') return;
    tablet.dataset.heroCycleWired = 'true';

    const mediaStack = tablet.querySelector('.tablet-media-stack');
    const photo = tablet.querySelector('.tablet-photo');
    const video = tablet.querySelector('.tablet-video');
    const soundToggle = tablet.querySelector('.tablet-sound-toggle');
    const soundHint = tablet.querySelector('.tablet-sound-hint');

    if (!mediaStack || !video) return;

    // 사용자 음소거 선택 저장 (sessionStorage로 세션 내 유지)
    const STORAGE_KEY = 'eduholic-hero-sound-' + (root.dataset.slug || 'default');
    let userWantsSound = false;
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      if (stored !== null) userWantsSound = stored === 'true';
    } catch (_) {}

    // 초기 상태: 사진 표시, 영상 숨김
    let currentPhase = 'photo'; // 'photo' | 'video' | 'photo-after-video'
    let cycleTimer = null;
    let videoEnded = false;
    let hintHidden = false;

    function showPhoto() {
      photo.style.opacity = '1';
      photo.style.zIndex = '2';
      video.style.opacity = '0';
      video.style.zIndex = '1';
      currentPhase = 'photo';
    }

    function showVideo() {
      photo.style.opacity = '0';
      photo.style.zIndex = '1';
      video.style.opacity = '1';
      video.style.zIndex = '2';
      currentPhase = 'video';
      video.currentTime = 0;
      video.muted = !userWantsSound;
      const playPromise = video.play();
      if (playPromise !== undefined) {
        playPromise.catch(() => {
          // 자동재생 차단 시 음소거로 재시도
          video.muted = true;
          video.play().catch(() => {});
        });
      }
      // 사운드 힌트 잠시 표시
      if (!hintHidden && soundHint && !userWantsSound) {
        soundHint.style.opacity = '1';
        soundHint.style.transform = 'translateY(0)';
        setTimeout(() => {
          soundHint.style.opacity = '0';
          soundHint.style.transform = 'translateY(8px)';
        }, 3000);
      }
    }

    function onVideoEnded() {
      videoEnded = true;
      showPhoto();
      currentPhase = 'photo-after-video';
      // 영상 종료 후 사진 2~3초 표시
      cycleTimer = setTimeout(startCycle, 2500);
    }

    function startCycle() {
      if (currentPhase === 'photo-after-video') {
        showVideo();
      } else {
        // 최초 진입: 사진 2초
        showPhoto();
        cycleTimer = setTimeout(() => {
          showVideo();
        }, 2000);
      }
    }

    // 영상 종료 이벤트
    video.addEventListener('ended', onVideoEnded);

    // 사운드 토글 버튼
    if (soundToggle) {
      soundToggle.addEventListener('click', (e) => {
        e.stopPropagation();
        userWantsSound = !userWantsSound;
        video.muted = !userWantsSound;
        soundToggle.textContent = userWantsSound ? '🔊' : '🔇';
        soundToggle.setAttribute('aria-pressed', userWantsSound ? 'true' : 'false');
        soundToggle.setAttribute('title', userWantsSound ? '소리 끄기' : '소리 켜기');
        try {
          sessionStorage.setItem(STORAGE_KEY, String(userWantsSound));
        } catch (_) {}
        // 힌트 영구 숨김
        hintHidden = true;
        if (soundHint) {
          soundHint.style.opacity = '0';
          soundHint.style.transform = 'translateY(8px)';
        }
      });
    }

    // 초기 스타일 설정 (CSS transition을 위해)
    if (photo) {
      photo.style.transition = 'opacity 0.4s ease';
      photo.style.position = 'absolute';
      photo.style.inset = '0';
      photo.style.width = '100%';
      photo.style.height = '100%';
      photo.style.objectFit = 'cover';
    }
    video.style.transition = 'opacity 0.4s ease';
    video.style.position = 'absolute';
    video.style.inset = '0';
    video.style.width = '100%';
    video.style.height = '100%';
    video.style.objectFit = 'cover';
    video.style.zIndex = '1';
    video.style.opacity = '0';

    mediaStack.style.position = 'relative';
    mediaStack.style.width = '100%';
    mediaStack.style.height = '100%';

    // 사운드 힌트 초기 스타일
    if (soundHint) {
      soundHint.style.position = 'absolute';
      soundHint.style.bottom = '44px';
      soundHint.style.left = '50%';
      soundHint.style.transform = 'translateX(-50%) translateY(8px)';
      soundHint.style.background = 'rgba(10,10,20,.85)';
      soundHint.style.color = '#fff';
      soundHint.style.padding = '6px 12px';
      soundHint.style.borderRadius = '999px';
      soundHint.style.fontSize = '11px';
      soundHint.style.fontWeight = '700';
      soundHint.style.whiteSpace = 'nowrap';
      soundHint.style.opacity = '0';
      soundHint.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
      soundHint.style.zIndex = '3';
      soundHint.style.pointerEvents = 'none';
    }

    // 사운드 토글 버튼 초기 스타일
    if (soundToggle) {
      soundToggle.style.position = 'absolute';
      soundToggle.style.bottom = '12px';
      soundToggle.style.right = '12px';
      soundToggle.style.zIndex = '4';
      soundToggle.style.width = '36px';
      soundToggle.style.height = '36px';
      soundToggle.style.borderRadius = '50%';
      soundToggle.style.background = 'rgba(10,10,20,.7)';
      soundToggle.style.border = 'none';
      soundToggle.style.color = '#fff';
      soundToggle.style.fontSize = '16px';
      soundToggle.style.display = 'flex';
      soundToggle.style.alignItems = 'center';
      soundToggle.style.justifyContent = 'center';
      soundToggle.style.cursor = 'pointer';
      soundToggle.style.boxShadow = '0 2px 8px rgba(0,0,0,.3)';
      soundToggle.style.transition = 'background 0.2s ease, transform 0.15s ease';
    }

    // 페이지 언로드/언마운트 시 정리
    const cleanup = () => {
      if (cycleTimer) clearTimeout(cycleTimer);
      video.removeEventListener('ended', onVideoEnded);
    };
    window.addEventListener('pagehide', cleanup, { once: true });
    // MutationObserver로 태블릿이 DOM에서 제거될 때도 정리
    if (window.MutationObserver) {
      const mo = new MutationObserver(() => {
        if (!document.body.contains(tablet)) {
          cleanup();
          mo.disconnect();
        }
      });
      mo.observe(document.body, { childList: true, subtree: true });
    }

    // 사이클 시작
    startCycle();
  }


  /**
   * 학습 철학 Step Progression (승인된 UI) — 단일 카드 + 진행바 + 인디케이터 + prev/next + 스와이프
   * - 4개 원칙을 stepper 형태로 순환
   * - 진행바 애니메이션 (scaleX 0→1)
   * - 텍스트 slide/fade 전환 (방향 인식)
   * - 번호 인디케이터 pulse (선택 시)
   * - prev/next 버튼 + 키보드 접근성
   * - 터치 스와이프 지원 (모바일)
   * - prefers-reduced-motion 대응 (즉시 전환)
   */
  function wirePhilosophyStep() {
    if (!root) return;
    const section = root.querySelector('#sec-philosophy');
    if (!section || section.dataset.philosophyWired === 'true') return;

    const card = section.querySelector('[data-philosophy-step-card]');
    if (!card) return;

    const stepsDataEl = section.querySelector('[data-philosophy-steps]');
    const steps = stepsDataEl ? JSON.parse(stepsDataEl.textContent) : [];
    if (!Array.isArray(steps) || !steps.length) return;

    const content = card.querySelector('[data-philosophy-step-content]');
    const labelEl = card.querySelector('[data-step-label]');
    const counterEl = card.querySelector('[data-step-counter]');
    const textEl = card.querySelector('[data-step-text]');
    const bars = Array.prototype.slice.call(card.querySelectorAll('[data-step-bar]'));
    const indicators = Array.prototype.slice.call(card.querySelectorAll('[data-step-indicator]'));
    const prevBtn = card.querySelector('[data-step-prev]');
    const nextBtn = card.querySelector('[data-step-next]');

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let current = 0;
    let animating = false;
    let touchStartX = null;

    function updateUI(index, direction) {
      current = index;
      card.dataset.activeStep = String(current + 1);

      // 진행바
      bars.forEach((bar, i) => {
        bar.classList.toggle('is-active', i <= current);
      });

      // 인디케이터
      indicators.forEach((ind, i) => {
        const isOn = i === current;
        ind.classList.toggle('is-on', isOn);
        ind.setAttribute('aria-selected', isOn);
        if (isOn && !reduceMotion) {
          ind.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.15)' }, { transform: 'scale(1)' }], {
            duration: 300, easing: 'cubic-bezier(0.34, 1.56, 0.64, 1)'
          });
        }
      });

      // 버튼 상태
      if (prevBtn) prevBtn.disabled = current === 0;
      if (nextBtn) nextBtn.disabled = current === steps.length - 1;

      // 라벨/카운터
      if (labelEl) labelEl.textContent = '원칙 ' + String(current + 1).padStart(2, '0');
      if (counterEl) counterEl.textContent = (current + 1) + ' / ' + steps.length;

      // 텍스트 전환
      if (textEl) {
        textEl.textContent = steps[current];
        if (reduceMotion || !direction) return;
        const easing = 'cubic-bezier(0.16, 1, 0.3, 1)';
        content.animate([
          { opacity: 0, transform: 'translateX(' + (direction === 'prev' ? 30 : -30) + 'px)' },
          { opacity: 1, transform: 'translateX(0)' }
        ], { duration: 350, easing });
        textEl.animate([
          { opacity: 0, transform: 'translateY(8px)' },
          { opacity: 1, transform: 'translateY(0)' }
        ], { duration: 350, delay: 100, easing, fill: 'backwards' });
      }
    }

    function goTo(index) {
      if (animating || index < 0 || index >= steps.length || index === current) return;
      const direction = index > current ? 'next' : 'prev';
      animating = true;
      updateUI(index, direction);
      setTimeout(() => { animating = false; }, 400);
    }

    function goPrev() { goTo(current - 1); }
    function goNext() { goTo(current + 1); }

    // 인디케이터 클릭
    indicators.forEach((ind, i) => {
      ind.addEventListener('click', () => goTo(i));
    });

    // prev/next 버튼
    if (prevBtn) prevBtn.addEventListener('click', goPrev);
    if (nextBtn) nextBtn.addEventListener('click', goNext);

    // 키보드 (포커스된 인디케이터에서 좌우 화살표)
    indicators.forEach((ind, i) => {
      ind.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowRight' && i < steps.length - 1) { e.preventDefault(); goTo(i + 1); indicators[i + 1].focus(); }
        else if (e.key === 'ArrowLeft' && i > 0) { e.preventDefault(); goTo(i - 1); indicators[i - 1].focus(); }
      });
    });

    // 터치 스와이프
    card.addEventListener('touchstart', (e) => { touchStartX = e.changedTouches[0].clientX; }, { passive: true });
    card.addEventListener('touchend', (e) => {
      if (touchStartX === null) return;
      const dx = e.changedTouches[0].clientX - touchStartX;
      touchStartX = null;
      if (dx < -40) goNext();
      else if (dx > 40) goPrev();
    }, { passive: true });

    // 초기화
    updateUI(0);
    section.dataset.philosophyWired = 'true';
  }


  function renderVisitStatistics() {
    return `<span id="visitStatistics" class="counter" aria-label="홈페이지 방문 통계" aria-live="polite"><span class="d"></span>오늘 <span id="visitToday">-</span> · 누적 <span id="visitTotal">-</span></span>`;
  }

  async function loadVisitStatistics(academy, ownAcademy) {
    const current = currentUser();
    const shouldRecord = !WORKING_COPY_PREVIEW && !ownAcademy && (!current || current.role !== 'ADMIN');
    try {
      const res = await fetch(`/api/academies/${encodeURIComponent(academy.slug)}/visits`, {
        method: shouldRecord ? 'POST' : 'GET',
        credentials: 'same-origin'
      });
      if (!res.ok) throw new Error('방문 통계 조회 실패');
      const statistics = await res.json();
      document.getElementById('visitToday').textContent = formatVisitCount(statistics.today);
      document.getElementById('visitTotal').textContent = formatVisitCount(statistics.total);
    } catch (_) {
      const widget = document.getElementById('visitStatistics');
      if (widget) widget.hidden = true;
    }
  }

  function formatVisitCount(value) {
    return Number(value || 0).toLocaleString('ko-KR');
  }

  function visitChartSvg(days) {
    if (!days.length) return '<span>집계된 방문이 없습니다.</span>';
    const width = 220;
    const height = 120;
    const left = 14;
    const right = width - 14;
    const top = 20;
    const bottom = 86;
    const max = Math.max(1, ...days.map(day => Number(day.count || 0)));
    const step = days.length > 1 ? (right - left) / (days.length - 1) : 0;
    const points = days.map((day, index) => ({
      x: left + step * index,
      y: bottom - (Number(day.count || 0) / max) * (bottom - top),
      count: Number(day.count || 0),
      label: String(day.date || '').slice(5).replace('-', '.')
    }));
    const polyline = points.map(point => `${point.x},${point.y}`).join(' ');
    const markers = points.map(point => `
      <g>
        <circle cx="${point.x}" cy="${point.y}" r="4"></circle>
        <text class="visit-chart-count" x="${point.x}" y="${Math.max(14, point.y - 10)}">${point.count}</text>
        <text class="visit-chart-date" x="${point.x}" y="112">${point.label}</text>
      </g>`).join('');
    return `<svg viewBox="0 0 ${width} ${height}" role="img" aria-label="최근 7일 방문 횟수 선형 그래프">
      <line class="visit-chart-axis" x1="${left}" y1="${bottom}" x2="${right}" y2="${bottom}"></line>
      <polyline points="${polyline}"></polyline>
      ${markers}
    </svg>`;
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
    if (id === 'sec-teachers') return renderTeachers(academy);
    if (id === 'sec-philosophy') return renderPhilosophy(academy);
    if (id === 'sec-videos') return renderVideos(academy);
    if (id === 'sec-minilectures') return renderMiniLectures(academy);
    if (id === 'sec-digitaltwin') return renderAiProcess(academy);
    if (id === 'sec-lessons') return renderLessons(academy);
    if (id === 'sec-cta') return SECTIONS.renderCta(sectionView(academy), PUBLIC_CTX);
    if (id === 'sec-social') return renderExternalServices(academy);
    return '';
  }

  function isSectionVisible(academy, id) {
    if (FUTURE_SECTION_IDS.has(id)) return false;
    const visibility = academy.pageSettings && academy.pageSettings.sectionVisibility;
    if (OPT_IN_SECTION_IDS.has(id)) return !!(visibility && visibility[id] === true);
    return !(visibility && visibility[id] === false);
  }

  /**
   * 수업 영상 섹션(유튜브 재생목록형): 왼쪽 메인 플레이어 + 오른쪽 썸네일 리스트.
   * 섹션 골격은 다른 섹션과 같은 section-header/title 구조를 따른다. 배지·안내문은 빌더 입력
   * 안내 성격이라 공개 페이지에서 뺐다.
   */
  function renderVideos(academy) {
    return SECTIONS.renderVideos(sectionView(academy), PUBLIC_CTX);
  }

  function renderMiniLectures(academy) {
    return SECTIONS.renderMiniLectures(sectionView(academy), PUBLIC_CTX);
  }

  function renderTeachers(academy) {
    return SECTIONS.renderTeachers(sectionView(academy));
  }






  function toggleLessonSchedule(button) {
    const wrap = button.closest('.lesson-schedule-wrap');
    const nextChecked = button.getAttribute('aria-checked') !== 'true';
    button.setAttribute('aria-checked', String(nextChecked));
    button.classList.toggle('on', nextChecked);
    if (wrap) wrap.classList.toggle('is-schedule-hidden', !nextChecked);
  }

  // 재생목록 클릭 → 메인 플레이어 src 교체. root는 재렌더되지만 위임이라 매번 다시 붙일 필요가 없다.
  if (root) {
    root.addEventListener('click', (event) => {
      const toggle = event.target.closest ? event.target.closest('[data-schedule-toggle]') : null;
      if (toggle && root.contains(toggle)) {
        event.preventDefault();
        toggleLessonSchedule(toggle);
        return;
      }
      const item = event.target.closest ? event.target.closest('[data-video-select]') : null;
      if (!item || !root.contains(item)) return;
      const player = document.getElementById('academyVideoPlayer');
      if (!player) return;
      // 첫 영상은 서버 렌더 HTML이 이미 <video src>로 로드해 둔 상태다. 여기서 같은 URL을
      // .src에 다시 대입하면(문자열이 같아도) 브라우저가 media element load algorithm을
      // 재실행해 진행 중이던 최초 로드를 스스로 중단시킨다(play()가 AbortError로 조용히
      // 실패) — "새로고침 후 첫 영상 첫 클릭만 재생 안 됨" 버그의 원인. 실제로 바뀔 때만
      // 대입하고, 재생 시도는(이미 로드된 영상이라도) 매번 한다.
      const nextSrc = item.dataset.videoSelect;
      if (player.getAttribute('src') !== nextSrc) {
        player.src = nextSrc;
      }
      player.play().catch(() => { /* 자동재생 차단은 무시하고 사용자가 재생 버튼을 누르게 둔다 */ });
      root.querySelectorAll('[data-video-select]').forEach((el) => el.classList.remove('active'));
      item.classList.add('active');
    });
  }

  // 학원별 사업자 footer. legacy Academy는 학원명과 마스킹된 더미 정보를 기본값으로 사용한다.
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
    return `<footer class="cv-footer academy-business-footer">
        <div class="foot-top">
          <span class="eh-logo-component footer-logo">${renderLogoSymbol(academy)}</span>
          <div class="foot-meta">
            ${renderVisitStatistics()}
            <span class="madeby">Made with <span class="bi"><b>Build</b><i>It</i></span></span>
          </div>
        </div>
        <p class="f-addr">${footerContactLine(businessInfo)}</p>
        <div class="academy-business-footer__rows">${BUSINESS_INFO.renderRows(businessInfo, escapeHtml)}</div>
        <p class="f-copy">© ${year} ${escapeHtml(academy.name)}. Powered by F2R.</p>
      </footer>`;
  }

  function renderGnb(academy, topBtn) {
    // 상단 간판은 히어로 제목과 별개로 둔다. 정식 학원명이 길면 GNB에서만 짧게 쓰고 싶다는
    // 요구가 있어, 비어 있을 때만 academy.name으로 떨어지게 했다.
    const headerTitle = draftText(academy, 'headerTitle', academy.name);
    return `<nav class="cv-gnb">
        <a class="eh-logo-component" href="/academies/${encodeURIComponent(academy.slug)}">
          ${renderLogoSymbol(academy)}
          <span class="eh-logo-text-area">
            <b class="eh-logo-title">${escapeHtml(headerTitle)}</b>
          </span>
        </a>
        <div class="cv-nav">
          ${topBtn}
        </div>
      </nav>`;
  }


  function renderLogoSymbol(academy) {
    if (academy.logoUrl) {
      return `<span class="eh-logo-symbol"><img src="${escapeHtml(academy.logoUrl)}" alt="${escapeHtml(academy.name)} 로고"></span>`;
    }
    return `<span class="eh-logo-symbol"><span class="eh-logo-symbol-inner"><span class="eh-book"><span class="eh-letters">${escapeHtml(initials(academy.name))}</span></span></span></span>`;
  }


  function renderHero(academy) {
    return SECTIONS.renderHero(sectionView(academy), PUBLIC_CTX);
  }

  // 패널은 수업 탭과 같은 짧은 이름을 쓴다. tabShortName이 비면 정식 수업명으로 떨어진다.

  // 대표 수업 패널. 상세는 sec-lessons가 담당하고 여기서는 네비게이션만 한다(클릭 → 스크롤).

  /** 채워진 히어로 슬롯인지. 비어 있으면 시안 placeholder로 렌더한다. */
  /** 공개 페이지는 발행된 학원만 통과하는 공개 경로로 바이트를 받는다. */




  // 학원 소개 좌측 컬럼은 nav·hero가 이미 보여준 로고/학원명을 반복하지 않고, 과목과 학원 특징을
  // 보여준다. 과목은 Academy 데이터(subjects), 특징은 builderDraft.introFeatures(개행 결합 문자열)다.
  function renderIntro(academy) {
    return SECTIONS.renderIntro(sectionView(academy), PUBLIC_CTX);
  }


  // 원장 소개 — 시안(sec-principal) 이식. 좌 카드(이름/직함/소개/약력/인용) + 우 원장 소개 영상.
  // 영상은 학원 영상 보관함의 principalVideoId 또는 principalYoutubeUrl 중 하나를 쓴다.
  // 공개 페이지는 편집 UI 없이 표시만 한다.
  function renderPrincipal(academy) {
    return SECTIONS.renderPrincipal(sectionView(academy), PUBLIC_CTX);
  }

  function renderPhilosophy(academy) {
    return SECTIONS.renderPhilosophy(sectionView(academy), PUBLIC_CTX);
  }

  // Education Storefront 신규 섹션. 마크업 정본은 academy_sections.js에 있고 여기서는 공개 ctx만 얹는다.
  // sectionView.sectionVisible이 opt-in(명시적 true) 규칙을 적용하므로 기존 학원에는 렌더되지 않는다.
  function renderFeatured(academy) {
    return SECTIONS.renderFeatured(sectionView(academy), PUBLIC_CTX);
  }

  function renderAiProcess(academy) {
    return SECTIONS.renderAiProcess(sectionView(academy), PUBLIC_CTX);
  }


  function findUploadedVideo(videoId) {
    const id = String(videoId || '').trim();
    if (!/^[1-9]\d*$/.test(id)) return null;
    return academyVideos.find((video) => String(video.videoId) === id) || {
      videoId: Number(id),
      videoUrl: `/api/academies/${encodeURIComponent(slug)}/videos/${encodeURIComponent(id)}/stream`,
      thumbnailUrl: null
    };
  }

  // 전부 속성값이라 escapeAttr을 쓴다(백틱까지 차단). 빌더 판과 동일하게 맞췄다.
  function uploadedVideoPlayer(video, className, title) {
    const poster = video.thumbnailUrl ? ` poster="${escapeAttr(video.thumbnailUrl)}"` : '';
    return `<video class="${escapeAttr(className)}" controls playsinline preload="metadata"${poster} src="${escapeAttr(video.videoUrl || '')}" aria-label="${escapeAttr(title)}"></video>`;
  }

  function draftText(academy, key, fallback) {
    const draft = academy && academy.pageSettings && academy.pageSettings.builderDraft;
    const value = draft && draft[key];
    return value == null || value === '' ? fallback : value;
  }

  function renderLessons(academy) {
    return SECTIONS.renderLessons(sectionView(academy), PUBLIC_CTX);
  }

  // 외부 서비스 연동(네이버 블로그·네이버 지도) — 마크업 정본은 academy_sections.js에 있다.
  // 여기서는 공개 페이지용 ctx를 얹어 호출만 한다.
  function renderExternalServices(academy) {
    return SECTIONS.renderExternalServices(sectionView(academy), PUBLIC_CTX);
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

  // 학원명 앞 2글자 → 로고 심볼용 이니셜.
  function initials(name) {
    return String(name || '').replace(/\s+/g, '').slice(0, 2).toUpperCase();
  }

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
  const ORDERED_SECTIONS = new Set(DEFAULT_SECTION_ORDER);

  function scrollToManagedSection(id) {
    if (!id) return;
    requestAnimationFrame(() => {
      const target = document.getElementById(id) || nearestRenderedSection(id);
      if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }

  function nearestRenderedSection(id) {
    const order = currentRenderedOrder();
    const index = order.indexOf(id);
    if (index < 0) return null;
    for (let i = index + 1; i < order.length; i += 1) {
      const next = document.getElementById(order[i]);
      if (next) return next;
    }
    for (let i = index - 1; i >= 0; i -= 1) {
      const prev = document.getElementById(order[i]);
      if (prev) return prev;
    }
    return null;
  }

  function currentRenderedOrder() {
    return lastSectionOrder.length ? lastSectionOrder : DEFAULT_SECTION_ORDER.slice();
  }

  function orderedSectionIds(academy) {
    const raw = academy && academy.pageSettings && academy.pageSettings.sectionOrder;
    if (!Array.isArray(raw)) return DEFAULT_SECTION_ORDER.slice();
    const seen = new Set();
    const filtered = raw.filter((id) => ORDERED_SECTIONS.has(id) && !seen.has(id) && seen.add(id));
    if (filtered[0] !== 'sec-hero') return DEFAULT_SECTION_ORDER.slice();
    // 빠진 관리 섹션은 canonical 위치(그 다음 순위 섹션 앞)에 삽입한다.
    // 서버 AcademyService.normalizeSectionOrderForWrite와 같은 규칙이다.
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

  window.scrollToId = function (id) {
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: 'smooth' });
  };





  // 상담 신청 — 학원이 footer 사업자 정보에 입력한 전화/이메일로 바로 연결한다.
  // 상담 접수 폼(백엔드 저장)은 여전히 미구현이라, 연락처가 하나도 없으면 기존 데모 안내를 유지한다.
  window.requestConsult = function () {
    if (!consultChannels.length) {
      alert('상담 신청 기능은 준비 중입니다. (데모)');
      return;
    }
    const options = document.getElementById('consultOptions');
    const modal = document.getElementById('consultModal');
    if (!options || !modal) return;
    options.innerHTML = consultChannels.map(consultOption).join('');
    modal.style.display = 'flex';
  };

  function consultOption(channel) {
    return `<a class="consult-contact-option" href="${escapeHtml(channel.href)}">
        <span class="consult-contact-icon">${escapeHtml(channel.icon)}</span>
        <span class="consult-contact-text">
          <span class="consult-contact-label">${escapeHtml(channel.label)}</span>
          <span class="consult-contact-value">${escapeHtml(channel.value)}</span>
        </span>
      </a>`;
  }

  // 배경 클릭으로도 닫는다(currentTarget===target일 때만 = 카드 내부 클릭은 무시).
  window.closeConsultModal = function (event) {
    if (event && event.currentTarget !== event.target) return;
    const modal = document.getElementById('consultModal');
    if (modal) modal.style.display = 'none';
  };

  let _pendingLessonId = null;

  window.F2RAcademySectionOrderTestHooks = Object.freeze({
    orderedSectionIds,
    visibleSectionIds(academy) {
      return orderedSectionIds(academy).filter((id) => isSectionVisible(academy, id));
    }
  });

  // 렌더 결과 단언용 훅. 테스트가 섹션 HTML을 소스 텍스트 정규식이 아니라 실제 렌더 결과로
  // 검사하게 해준다 — 렌더러를 공용 모듈로 추출해도 단언이 그대로 유지되도록 하기 위한 것이다.
  // setVideos는 모듈 지역 academyVideos를 주입하는 유일한 경로다(테스트에서 fetch를 태우지 않는다).
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
    renderCta(academy) { return SECTIONS.renderCta(sectionView(academy), PUBLIC_CTX); },
    renderFooter,
    // 정적 캡처/스냅샷용: 전체 페이지 HTML 문자열을 반환한다(DOM 주입 없이).
    renderPage(academy) {
      const primarySubject = (academy.subjects && academy.subjects[0])
        ? (academy.subjects[0].nameKo || academy.subjects[0].nameEn) : '';
      const regionText = (academy.regions && academy.regions[0]) ? academy.regions[0].displayName : '';
      const topBtn = '<button class="cv-btn-primary">상담 신청</button>';
      return renderGnb(academy, topBtn)
        + renderOrderedSections(academy, primarySubject, regionText)
        + renderFooter(academy);
    },
    escapeHtml,
    escapeAttr,
    setVideos(list) { academyVideos = Array.isArray(list) ? list : []; }
  });

  window.applyPublicLesson = async function (lessonId) {
    const user = currentUser();
    if (!user || user.role !== 'STUDENT') {
      alert('학생 로그인 후 신청할 수 있습니다.');
      return;
    }
    _pendingLessonId = lessonId;
    await openApplyModal();
  };

  async function openApplyModal() {
    const select = document.getElementById('conversationSelect');
    select.innerHTML = '<option value="">새 대화로 시작</option>';
    try {
      const res = await fetchAuth('/api/conversations');
      if (res.ok) {
        const conversations = await res.json();
        conversations.forEach((conv) => {
          const opt = document.createElement('option');
          opt.value = conv.conversationId;
          opt.textContent = conv.title || '(제목 없음)';
          select.appendChild(opt);
        });
      }
    } catch (_) {}
    const modal = document.getElementById('applyModal');
    modal.style.display = 'flex';
  }

  window.closeApplyModal = function () {
    document.getElementById('applyModal').style.display = 'none';
    _pendingLessonId = null;
  };

  window.confirmApply = async function () {
    if (!_pendingLessonId) return;
    const conversationIdRaw = document.getElementById('conversationSelect').value;
    const conversationId = conversationIdRaw ? Number(conversationIdRaw) : null;
    const confirmBtn = document.getElementById('applyConfirmBtn');
    confirmBtn.disabled = true;
    confirmBtn.textContent = '신청 중...';
    try {
      const res = await fetchAuth('/api/lesson-applications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lessonId: _pendingLessonId, conversationId })
      });
      if (!res.ok) throw new Error(await res.text());
      document.getElementById('applyModal').style.display = 'none';
      const body = await res.json();
      if (body.conversationId) {
        location.href = '/conversation/dashboard?id=' + body.conversationId;
      } else {
        await load();
      }
    } catch (e) {
      alert('신청 실패: 정원이 마감되었거나 이미 신청한 수업입니다.');
    } finally {
      confirmBtn.disabled = false;
      confirmBtn.textContent = '신청하기';
      _pendingLessonId = null;
    }
  };

  // 정원 미정(null)은 무제한 접수로 본다. 편집기가 정원을 받지 않아 신규 수업은 대부분 null이다.
  function isFull(lesson) {
    if (lesson.maxCapacity == null) return false;
    return (lesson.currentCount || 0) >= lesson.maxCapacity;
  }

  // 정원·대상·유형은 비어 있을 수 있다. 값이 있는 항목만 모아 한 줄로 만들고, 전부 없으면 줄을 생략한다.
  function lessonMeta(lesson) {
    const parts = [];
    if (lesson.maxCapacity != null) {
      parts.push(`정원 ${lesson.currentCount || 0}/${lesson.maxCapacity}명`);
    }
    if (lesson.targetGroup) parts.push(label(lesson.targetGroup));
    if (lesson.lessonType) parts.push(label(lesson.lessonType));
    return parts.length ? `<p>${parts.join(' · ')}</p>` : '';
  }

  function currentUser() {
    try { return JSON.parse(sessionStorage.getItem('currentUser') || 'null'); } catch (_) { return null; }
  }

  function label(value) {
    return ({ SUBSCRIPTION: '구독형', SHORT_TERM: '단기형', ELEMENTARY: '초등학생', MIDDLE: '중학생', HIGH: '고등학생', UNIVERSITY: '대학생', ADULT: '성인' }[value]) || value;
  }

  // 정본 — academy_builder.js와 동일 구현. `String(value || '')`을 쓰면 숫자 0과 false가
  // 빈 문자열로 지워진다(정원 0명, 카운터 0 등이 화면에서 사라진다). null/undefined만 걸러낸다.
  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  // 속성값 전용 — 템플릿 리터럴 백틱까지 막는다.
  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }
})();
