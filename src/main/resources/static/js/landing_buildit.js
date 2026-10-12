/**
 * Build It 랜딩 스크롤 연출 + 시작하기 게이트.
 *
 * 연출은 GSAP ScrollTrigger 기반이며, GSAP 로드에 실패해도 페이지가 빈 화면이 되지 않도록
 * 정적 폴백(reveal 해제 / Wiki 화면 전부 노출)을 먼저 준비한다.
 */
(function () {
  const BUILDER_URL = '/builder';
  const REGISTER_URL = '/register?returnUrl=' + encodeURIComponent(BUILDER_URL);

  // --- 시작하기: 기존 랜딩 발행 게이트와 동일한 분기 ---
  // TUTOR 로그인 → /builder, 그 외(비로그인/학생) → 강사 가입 게이트.
  async function currentUser() {
    try {
      const cached = JSON.parse(sessionStorage.getItem('currentUser') || 'null');
      if (cached && cached.role) return cached;
    } catch (_) { /* 캐시가 깨졌으면 서버에 다시 묻는다 */ }
    try {
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (!res.ok) return null;
      const user = await res.json();
      try { sessionStorage.setItem('currentUser', JSON.stringify(user)); } catch (_) { /* 저장 실패는 무시 */ }
      return user;
    } catch (_) {
      return null;
    }
  }

  async function onStartClick(event) {
    event.preventDefault();
    const user = await currentUser();
    location.href = user && user.role === 'TUTOR' ? BUILDER_URL : REGISTER_URL;
  }

  document.querySelectorAll('[data-buildit-start]').forEach((el) => {
    el.addEventListener('click', onStartClick);
  });

  // --- Hero 소개영상: 첫 화면에서는 썸네일만, 클릭할 때 YouTube iframe을 생성한다. ---
  document.querySelectorAll('[data-youtube-id]').forEach((card) => {
    const trigger = card.querySelector('.hero-video-trigger');
    if (!trigger) return;
    trigger.addEventListener('click', () => {
      const videoId = card.dataset.youtubeId;
      if (!/^[\w-]{11}$/.test(videoId || '')) return;
      const iframe = document.createElement('iframe');
      iframe.src = 'https://www.youtube-nocookie.com/embed/' + videoId
        + '?autoplay=1&rel=0&modestbranding=1';
      iframe.title = 'BuildIt 소개영상';
      iframe.allow = 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share';
      iframe.allowFullscreen = true;
      iframe.referrerPolicy = 'strict-origin-when-cross-origin';
      card.replaceChildren(iframe);
    }, { once: true });
  });

  // --- 보호 화면에서 튕겨 온 경우: 로그인 안내 ---
  // SecurityConfig가 /problem-generator/** 익명 HTML 요청을 "/?login=required"로 302한다.
  // 안내 전에 마커를 지우므로 새로고침·뒤로가기에서 다시 뜨지 않는다.
  // ⚠ openLoginModal은 defer로 로드되는 account_menu.js가 등록한다. 이 파일은 body 끝에서
  //   즉시 실행되어 defer 스크립트보다 먼저 돌기 때문에 DOMContentLoaded까지 기다려야 한다.
  function promptLoginAfterRedirect() {
    const params = new URLSearchParams(location.search);
    if (params.get('login') !== 'required') return;

    const rawReturnUrl = params.get('returnUrl');
    const returnUrl = rawReturnUrl && typeof window.safeInternalReturnUrl === 'function'
      ? window.safeInternalReturnUrl(rawReturnUrl)
      : null;
    const isWikiReturn = returnUrl && returnUrl.startsWith('/wiki/');
    // CHOONGGI Creator SSR(/choonggi/admin/**) uses the same post-login returnUrl mechanism as
    // Wiki -- SecurityConfig redirects anonymous /choonggi/admin/** here instead of a raw 401.
    const isChoonggiReturn = returnUrl && returnUrl.startsWith('/choonggi/admin');
    if (isWikiReturn) {
      try { sessionStorage.setItem('wikiPostLoginReturnUrl', returnUrl); } catch (_) {}
      document.querySelectorAll('[data-auth-signup]').forEach((link) => {
        link.href = '/register?returnUrl=' + encodeURIComponent(returnUrl);
      });
    } else if (isChoonggiReturn) {
      try { sessionStorage.setItem('postLoginReturnUrl', returnUrl); } catch (_) {}
    } else if (returnUrl) {
      // 2026-10-12: 일반 페이지의 검증된 returnUrl도 보존 (Wiki·Choonggi뿐 아니라 전체)
      try { sessionStorage.setItem('postLoginReturnUrl', returnUrl); } catch (_) {}
    }

    params.delete('login');
    params.delete('returnUrl');
    const query = params.toString();
    history.replaceState({}, '', location.pathname + (query ? '?' + query : '') + location.hash);
    alert(isChoonggiReturn
      ? '로그인이 필요합니다. 로그인 후 계속 이용할 수 있습니다.'
      : '회원 전용 자료입니다. 로그인 또는 무료 회원가입 후 모든 Wiki 자료를 이용할 수 있습니다.');
    if (typeof window.openLoginModal === 'function') window.openLoginModal();
  }

  window.addEventListener('f2r:login', () => {
    let returnUrl = null;
    try {
      returnUrl = sessionStorage.getItem('wikiPostLoginReturnUrl') || sessionStorage.getItem('postLoginReturnUrl');
      sessionStorage.removeItem('wikiPostLoginReturnUrl');
      sessionStorage.removeItem('postLoginReturnUrl');
    } catch (_) {}
    if (returnUrl && typeof window.safeInternalReturnUrl === 'function') {
      location.href = window.safeInternalReturnUrl(returnUrl);
    }
  });

  document.addEventListener('DOMContentLoaded', promptLoginAfterRedirect);

  // --- Wiki 3단계 화면 ---
  const screens = Array.prototype.slice.call(document.querySelectorAll('.scr'));
  const steps = Array.prototype.slice.call(document.querySelectorAll('.flow-h-step'));

  function play(video) {
    if (!video) return;
    const played = video.play();
    if (played && typeof played.catch === 'function') played.catch(() => { /* 자동재생 차단은 무시 */ });
  }

  // 보이지 않는 화면의 영상까지 계속 디코딩되지 않도록 활성 단계만 재생한다.
  function playOnly(index) {
    screens.forEach((screen, i) => {
      const video = screen.querySelector('video');
      if (!video) return;
      if (i === index) play(video); else video.pause();
    });
  }

  // 모바일에서는 세 화면이 동시에 보이므로 전부 재생한다.
  function playAll() {
    screens.forEach((screen) => play(screen.querySelector('video')));
  }

  let schoolRevealed = false;
  function revealNewSchool(animate) {
    if (schoolRevealed) return;
    schoolRevealed = true;
    const el = document.querySelector('.mws-new');
    if (!el) return;
    if (animate && window.gsap) {
      gsap.fromTo(el, { opacity: 0, scale: .85 },
        { opacity: 1, scale: 1, duration: .5, delay: .3, ease: 'back.out(2)' });
      return;
    }
    el.style.opacity = '1';
    el.style.transform = 'none';
  }

  // GSAP이 없을 때의 정적 폴백: 모든 연출 요소를 최종 상태로 고정한다.
  function staticFallback() {
    document.querySelectorAll('.reveal').forEach((el) => {
      el.style.opacity = '1';
      el.style.transform = 'none';
    });
    screens.forEach((screen) => {
      screen.style.opacity = '1';
      screen.style.transform = 'none';
      screen.style.position = 'relative';
    });
    steps.forEach((step) => step.classList.add('passed'));
    playAll();
    revealNewSchool(false);
  }

  if (!window.gsap || !window.ScrollTrigger) {
    staticFallback();
    return;
  }

  gsap.registerPlugin(ScrollTrigger);

  const nav = document.getElementById('biNav');
  if (nav) {
    ScrollTrigger.create({
      start: 'top -10',
      end: 99999,
      toggleClass: { targets: nav, className: 'scrolled' }
    });
  }

  gsap.utils.toArray('[data-fade]').forEach((el) => {
    gsap.to(el, {
      opacity: 1, y: 0, duration: .45, ease: 'power3.out',
      scrollTrigger: { trigger: el, start: 'top 94%', toggleActions: 'play none none reverse' }
    });
  });

  gsap.to('.hero-lines', {
    yPercent: 18, ease: 'none',
    scrollTrigger: { trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true }
  });
  gsap.to('.hero-inner', {
    yPercent: -12, opacity: 0, ease: 'none',
    scrollTrigger: { trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true }
  });


  // --- Wiki: pin + 3단계 플로우 + 화면 슬라이드 전환 ---
  if (screens.length) {
    gsap.set(screens[0], { opacity: 1, y: 0 });
    playOnly(0);

    ScrollTrigger.matchMedia({
      '(min-width: 861px)': function () {
        let lastStage = 0;
        ScrollTrigger.create({
          trigger: '.wiki', start: 'top top', end: '+=90%',
          scrub: .15, pin: '.wiki-pin',
          onUpdate: (self) => {
            const stepIndex = Math.min(screens.length - 1, Math.floor(self.progress * screens.length));

            steps.forEach((s) => {
              const si = Number(s.dataset.step);
              s.classList.toggle('current', si === stepIndex);
              s.classList.toggle('passed', si < stepIndex);
            });

            if (stepIndex !== lastStage) {
              gsap.to(screens[lastStage], { opacity: 0, y: -18, duration: .15, ease: 'power2.in' });
              gsap.fromTo(screens[stepIndex], { opacity: 0, y: 18 },
                { opacity: 1, y: 0, duration: .2, ease: 'power2.out' });
              lastStage = stepIndex;
              playOnly(stepIndex);
              if (stepIndex === screens.length - 1) revealNewSchool(true);
            }
          }
        });
      },
      '(max-width: 860px)': function () {
        gsap.set(screens, { opacity: 1, y: 0, position: 'relative' });
        playAll();
        steps.forEach((s) => s.classList.add('passed'));
        revealNewSchool(true);
      }
    });
  }
})();
