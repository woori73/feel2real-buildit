/* 2026 2학기 중간고사 공개 LIVE.
 * GET .../wiki-sources 의 public exposed source 만 read-only visual 로 사용한다.
 * API/preview 보안 경계는 서버가 소유하며 이 스크립트는 previewUrl 외 파일 정보를 사용하지 않는다.
 * 첫 화면 = Shopify Atelier hero(featured 자료 1건). 그 아래 = Shopify Motion
 * "evolving collections" — 나머지 최근 자료가 비대칭 collage / 모바일 rail 로 이어진다.
 */
(function () {
  'use strict';

  var root = document.getElementById('mtRoot');
  if (!root) return;

  var HANDLE = root.getAttribute('data-handle') || 'eduholic';
  var SOURCES_API = '/api/academies/' + encodeURIComponent(HANDLE)
    + '/midterm-campaign/wiki-sources';
  var HOME_URL = 'https://eduholic.feel2real.co.kr/';
  var SEASON = '2026 2학기 중간고사';
  var TITLE = '중간고사 준비 중';
  var NOTE = '학교 프린트 내용을 반영했습니다.';
  var RECENT_TITLE = '최근 준비 자료';
  var RECENT_MAX = 5;                 // Motion collage 는 최대 5장까지 배치한다.
  var ROLES = ['a', 'b', 'c', 'd'];   // crop presentation role (DB taxonomy 아님).

  var reduceMotion = window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  fetch(SOURCES_API, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  })
    .then(function (response) {
      if (response.status === 404) return [];
      if (!response.ok) throw new Error('wiki sources load failed: ' + response.status);
      return response.json();
    })
    .then(function (payload) {
      var sources = Array.isArray(payload) ? payload : [];
      var hero = latestSource(sources);
      render(hero);
      renderRecent(recentSources(sources, hero));
    })
    .catch(function () {
      render(null);
    });

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function timestamp(source) {
    return Date.parse(source && source.createdAt ? source.createdAt : '') || 0;
  }

  function latestSource(sources) {
    return sources
      .filter(function (source) {
        return source && typeof source.previewUrl === 'string' && source.previewUrl;
      })
      .slice()
      .sort(function (a, b) { return timestamp(b) - timestamp(a); })[0] || null;
  }

  /** hero 로 쓰는 sourceId 는 제외한다. 순서는 서버가 반환한 canonical order 를 존중한다. */
  function recentSources(sources, hero) {
    var heroId = hero && hero.sourceId;
    return sources.filter(function (source) {
      return source
        && typeof source.previewUrl === 'string' && source.previewUrl
        && source.sourceId && source.sourceId !== heroId;
    }).slice(0, RECENT_MAX);
  }

  function gradeLabel(grade) {
    var value = String(grade == null ? '' : grade).trim();
    if (!value) return '';
    return /학년$/.test(value) ? value : value + '학년';
  }

  function updateDate(value) {
    if (!value) return '';
    var date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return (date.getMonth() + 1) + '.' + date.getDate() + ' 업데이트';
  }

  function metadata(source) {
    var schoolGrade = [String(source.school || '').trim(), gradeLabel(source.grade)]
      .filter(Boolean)
      .join(' ');
    var date = updateDate(source.createdAt);
    return [schoolGrade, date].filter(Boolean).join(' · ');
  }

  function renderTopbar() {
    var topbar = el('header', 'mt-topbar');
    var wordmark = el('a', 'mt-wordmark', '압구정에듀홀릭');
    wordmark.href = HOME_URL;
    wordmark.rel = 'noopener';
    topbar.appendChild(wordmark);
    topbar.appendChild(el('span', 'mt-season', SEASON));
    return topbar;
  }

  function render(source) {
    root.innerHTML = '';
    root.appendChild(renderTopbar());

    var main = el('main', 'mt-main');
    if (source) main.classList.add('mt-main--feature');

    var intro = el('div', 'mt-intro');
    if (source) {
      var meta = metadata(source);
      if (meta) intro.appendChild(el('p', 'midterm-feature__meta', meta));
    }
    intro.appendChild(el('h1', 'mt-title', source ? TITLE : SEASON));
    if (source) intro.appendChild(el('p', 'mt-note', NOTE));
    main.appendChild(intro);

    if (source) main.appendChild(renderFeature(source));

    root.appendChild(main);
  }

  function renderFeature(source) {
    var section = el('section', 'midterm-feature');
    section.setAttribute('aria-label', '실제 중간고사 준비 자료');

    // 지식상품 패키지: page bg → product surface → clipped media window → 실제 Wiki HTML.
    var surface = el('div', 'midterm-feature__surface');
    var visual = el('div', 'midterm-feature__visual');
    visual.appendChild(safeFrame(source.previewUrl, 'midterm-feature__document', '중간고사 준비 자료'));
    surface.appendChild(visual);
    section.appendChild(surface);

    wireDocumentMotion(section);
    return section;
  }

  /** Motion "evolving collections": featured 를 뺀 최근 자료를 비대칭 collage 로 잇는다.
   *  0건이면 이 섹션 자체를 렌더하지 않는다("준비 중" 더미 카드 없음). */
  function renderRecent(sources) {
    if (!sources.length) return;

    var section = el('section', 'midterm-recent');
    section.classList.add('midterm-recent--n' + Math.min(sources.length, RECENT_MAX));
    section.setAttribute('aria-label', '최근 중간고사 준비 자료');

    var head = el('header', 'midterm-recent__head');
    head.appendChild(el('h2', 'midterm-recent__title', RECENT_TITLE));
    section.appendChild(head);

    var collection = el('div', 'midterm-recent__collection');
    sources.forEach(function (source, index) {
      collection.appendChild(renderRecentItem(source, index));
    });
    section.appendChild(collection);

    root.appendChild(section);
    revealOnView(section);
  }

  function renderRecentItem(source, index) {
    var item = el('article', 'midterm-recent__item');
    item.classList.add('is-role-' + ROLES[index % ROLES.length]);

    // hero 와 동일한 패키지 grammar: surface → clipped media window → 실제 Wiki HTML.
    var surface = el('div', 'midterm-recent__surface');
    var media = el('div', 'midterm-recent__media');
    media.appendChild(safeFrame(source.previewUrl, 'midterm-recent__doc', '중간고사 준비 자료'));
    surface.appendChild(media);
    item.appendChild(surface);

    var meta = metadata(source);
    if (meta) item.appendChild(el('p', 'midterm-recent__meta', meta));

    return item;
  }

  /** preview 보안 계약: server-controlled previewUrl 만, sandbox / no interaction / no scroll. */
  function safeFrame(previewUrl, className, title) {
    var frame = document.createElement('iframe');
    frame.className = className;
    frame.src = previewUrl;
    frame.title = title;
    frame.setAttribute('sandbox', '');
    frame.setAttribute('tabindex', '-1');
    frame.setAttribute('aria-hidden', 'true');
    frame.setAttribute('scrolling', 'no');
    return frame;
  }

  function wireDocumentMotion(section) {
    if (reduceMotion) return;

    function start() {
      if (!section.classList.contains('is-in-view')) {
        section.classList.add('is-in-view');
      }
    }

    if (!('IntersectionObserver' in window)) {
      start();
      return;
    }

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          start();
          observer.disconnect();
        }
      });
    }, { threshold: 0.28 });
    observer.observe(section);
  }

  /** 그룹이 뷰포트에 처음 닿으면 한 번만 is-in-view (하위 item 은 CSS stagger).
   *  reduced-motion / IO 미지원이면 즉시 최종 상태. */
  function revealOnView(node) {
    if (reduceMotion || !('IntersectionObserver' in window)) {
      node.classList.add('is-in-view');
      return;
    }
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          node.classList.add('is-in-view');
          observer.disconnect();
        }
      });
    }, { threshold: 0.05 });
    observer.observe(node);
  }
})();
