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
    'sec-digitaltwin'
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
  // deps는 지연 평가한다 — slug는 이 시점에 아직 계산 전이고, 쇼케이스 모듈은 정본 모듈과
  // 다른 컨텍스트에 있을 수 있다.
  const PUBLIC_CTX = SECTIONS.createPublicContext({
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
  const slug = (root && root.dataset.slug)
    ? root.dataset.slug
    : decodeURIComponent(location.pathname.split('/').filter(Boolean).pop() || '');
  window.addEventListener('message', (event) => {
    if (event.origin !== location.origin) return;
    const data = event.data || {};
    if (data.type !== 'F2R_BUILDER_APPLY_ACADEMY' || !data.academy) return;
    render(data.academy, true);
    scrollToManagedSection(data.scrollToSection);
  });

  if (!window.F2R_ACADEMY_SECTION_ORDER_TEST) {
    load();
  }

  async function load() {
    try {
      const res = await fetch(`/api/academies/${encodeURIComponent(slug)}`);
      if (!res.ok) throw new Error('공개 Academy를 찾을 수 없습니다.');
      const academy = await res.json();
      academyVideos = await loadVideos();
      render(academy, await isOwnAcademy(academy.slug));
    } catch (err) {
      errorMsg.textContent = err.message || 'Academy 조회에 실패했습니다.';
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
    applyReveal();
    void loadVisitStatistics(academy, ownAcademy);
  }

  /**
   * 섹션 등장 애니메이션. 랜딩(landing_buildit.js:94-121)과 같은 GSAP + ScrollTrigger 방식이다.
   *
   * 이 페이지는 CSR이라 fetch 후에야 섹션이 생기고, 섹션 토글·순서변경으로 재렌더도 된다.
   * 그래서 로드 시점이 아니라 render()마다 호출하고, 이미 처리한 요소는 건너뛴다.
   *
   * GSAP이 없으면(CDN 장애·차단) 아무것도 하지 않는다. html.js-motion을 붙이지 않으므로
   * CSS의 opacity:0도 걸리지 않아 페이지가 그냥 정적으로 보인다 — 빈 화면이 되지 않는다.
   */
  function applyReveal() {
    if (!window.gsap || !window.ScrollTrigger) return;
    document.documentElement.classList.add('js-motion');
    gsap.registerPlugin(ScrollTrigger);
    gsap.utils.toArray('[data-fade]').forEach((el) => {
      if (el.dataset.fadeWired === 'true') return;
      el.dataset.fadeWired = 'true';
      gsap.to(el, {
        opacity: 1, y: 0, duration: .45, ease: 'power3.out',
        scrollTrigger: { trigger: el, start: 'top 94%', toggleActions: 'play none none reverse' }
      });
    });
  }

  function renderVisitStatistics() {
    return `<span id="visitStatistics" class="counter" aria-label="홈페이지 방문 통계" aria-live="polite"><span class="d"></span>오늘 <span id="visitToday">-</span> · 누적 <span id="visitTotal">-</span></span>`;
  }

  async function loadVisitStatistics(academy, ownAcademy) {
    const current = currentUser();
    const shouldRecord = !ownAcademy && (!current || current.role !== 'ADMIN');
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
    if (id === 'sec-philosophy') return renderPhilosophy(academy);
    if (id === 'sec-videos') return renderVideos(academy);
    if (id === 'sec-digitaltwin') return renderAiProcess(academy);
    if (id === 'sec-lessons') return renderLessons(academy);
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
      player.src = item.dataset.videoSelect;
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
    'sec-philosophy',
    'sec-videos',
    'sec-reviews',
    'sec-lessons',
    'sec-curriculum',
    'sec-effect',
    'sec-digitaltwin',
    'sec-market',
    'sec-payment',
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
    const withMissing = filtered.concat(DEFAULT_SECTION_ORDER.filter((id) => !seen.has(id)));
    return withMissing[0] === 'sec-hero' ? withMissing : DEFAULT_SECTION_ORDER.slice();
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
    renderLessons,
    renderExternalServices,
    renderFooter,
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
