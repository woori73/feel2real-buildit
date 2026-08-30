/**
 * Storefront Vertical Story Engine (V2-4) — GSAP ScrollTrigger 위의 얇은 래퍼.
 *
 * VERTICAL SCROLL = STORY TRANSITION.  scrub 로 애니메이션 진행도를 스크롤 위치에 묶으므로
 * 위로 스크롤하면 그 과정이 그대로 역전된다(bidirectional). scroll hijack 없음 — pin 은
 * spacer 로 문서를 늘릴 뿐이고 사용자의 wheel/trackpad/touch/keyboard 입력을 가로채지 않는다.
 *
 *   F2RStorefrontStory.stage(section, { scenes, sceneSelector, id, distance, minWidth })
 *     section 을 뷰포트에 pin 하고 scenes 개 장면으로 나눠 scrub 한다. 장면이 바뀌면
 *       section[data-story-scene] = "0".."scenes-1"
 *       각 sceneSelector 요소 → data-scene-state = past | active | upcoming
 *     pin 이 실제로 걸리면 section[data-story-staged] = "true".
 *
 *   F2RStorefrontStory.exit(section, { id })
 *     pin 없이, section 이 뷰포트를 빠져나가는 동안 CSS 변수 --story-exit(0→1) 를 준다
 *     (현재 장면이 "퇴장"하는 연출 — 다음 장면이 active stage 를 차지하게).
 *
 * 안전 / 폴백
 *   - GSAP / ScrollTrigger 미로드 → 전부 no-op. 콘텐츠는 정적(모든 장면 요소 active, --story-exit 0).
 *   - prefers-reduced-motion → pin/scrub 안 함, 정적 폴백과 동일.
 *   - minWidth(기본 761) 미만 뷰포트(모바일) → pin 안 함, 정적 폴백. (모바일 pin 은 주소창 리사이즈
 *     이슈가 있고, 세로 스토리는 네이티브 스크롤이 더 안정적이다.)
 *   - html.js-story 는 실제로 무언가 pin/scrub 됐을 때만 붙는다 → CSS 초기 상태 게이트.
 *   - CSR 재렌더 대비: 같은 id 의 ScrollTrigger 를 kill 후 재생성한다.
 */
(function (global) {
  'use strict';

  var doc = global.document;
  if (!doc) return;

  function GSAP() { return global.gsap; }
  function ST() { return global.ScrollTrigger; }

  function reduced() {
    return typeof global.matchMedia === 'function'
      && global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function viewportWidth() {
    return global.innerWidth
      || (doc.documentElement && doc.documentElement.clientWidth)
      || 0;
  }

  var registered = false;
  function usable(minWidth) {
    if (!GSAP() || !ST() || !doc.documentElement) return false;
    if (reduced()) return false;
    if (viewportWidth() < (minWidth || 761)) return false;
    if (!registered) { GSAP().registerPlugin(ST()); registered = true; }
    return true;
  }

  function markStory() {
    if (doc.documentElement && doc.documentElement.classList) {
      doc.documentElement.classList.add('js-story');
    }
  }

  function killById(id) {
    if (ST() && ST().getById) {
      var t = ST().getById(id);
      if (t) t.kill(true);
    }
  }

  /**
   * 장면 상태를 "요소 자신의 attribute" 로 준다. 조상 attribute(section[data-story-scene]) + CSS
   * transition 조합은 일부 Chromium 에서 transition 이 첫 값에 붙어버리는 버그가 있어, 상태를
   * 각 대상에 직접 붙인다(V2-4 .ai-process-step 이 이미 쓰는 방식과 동일).
   *   sceneSelector 요소(단계 목록): i 번째가 scene i 에서 active — DOM 순서 = 장면 순서.
   *   layerSelector 요소(stage 오버레이): data-scene(1-index)로 자기 장면 번호를 갖는다.
   */
  function paintScenes(section, els, layers, idx) {
    section.setAttribute('data-story-scene', String(idx));
    for (var i = 0; i < els.length; i += 1) {
      els[i].setAttribute('data-scene-state', i < idx ? 'past' : (i === idx ? 'active' : 'upcoming'));
    }
    for (var j = 0; j < layers.length; j += 1) {
      var n = parseInt(layers[j].getAttribute('data-scene'), 10) - 1;
      if (idx > n) layers[j].setAttribute('data-scene-state', 'past');
      else if (idx === n) layers[j].setAttribute('data-scene-state', 'active');
      else layers[j].removeAttribute('data-scene-state');
    }
  }

  function staticStage(section, els, layers, scenes) {
    section.setAttribute('data-story-scene', String(scenes - 1));
    for (var i = 0; i < els.length; i += 1) els[i].setAttribute('data-scene-state', 'active');
    for (var j = 0; j < layers.length; j += 1) layers[j].removeAttribute('data-scene-state');
  }

  function stage(section, opts) {
    var o = opts || {};
    if (!section) return;
    var scenes = Math.max(1, o.scenes || 1);
    var id = o.id || ('story-' + (section.id || 'stage'));
    var els = o.sceneSelector ? section.querySelectorAll(o.sceneSelector) : [];
    var layers = o.layerSelector ? section.querySelectorAll(o.layerSelector) : [];

    if (!usable(o.minWidth)) {
      killById(id);
      section.removeAttribute('data-story-staged');
      staticStage(section, els, layers, scenes);
      return;
    }

    killById(id);

    // data-story-staged 를 먼저 세팅 → CSS(min-height:100vh 등)가 적용된 뒤 ScrollTrigger 가
    // 실제 높이로 pin 을 계산하게 한다. (뒤에 세팅하면 GSAP 이 옛 높이로 pin 해 어긋난다.)
    section.setAttribute('data-story-staged', 'true');
    paintScenes(section, els, layers, 0);

    // 안전 가드: staged 상태의 섹션이 뷰포트보다 훨씬 크면(콘텐츠가 안 들어가면) pin 하지 않는다 —
    // pin 된 요소가 뷰포트를 넘으면 아래쪽이 잘려 보인다. 정적 폴백으로 되돌린다.
    var vh = global.innerHeight || (doc.documentElement && doc.documentElement.clientHeight) || 0;
    var h = section.getBoundingClientRect ? section.getBoundingClientRect().height : 0;
    if (vh && h > vh * 1.3) {
      section.removeAttribute('data-story-staged');
      staticStage(section, els, layers, scenes);
      return;
    }

    markStory();
    var distance = o.distance || (scenes * 0.85);   // 장면당 ~0.85 viewport
    var lastIdx = -1;
    try {
      ST().create({
        id: id,
        trigger: section,
        start: 'top top',
        end: '+=' + Math.round(distance * 100) + '%',
        pin: true,
        pinSpacing: true,
        anticipatePin: 1,
        invalidateOnRefresh: true,
        scrub: o.scrub == null ? 0.4 : o.scrub,
        onUpdate: function (self) {
          var idx = Math.min(scenes - 1, Math.max(0, Math.round(self.progress * (scenes - 1))));
          if (idx !== lastIdx) { lastIdx = idx; paintScenes(section, els, layers, idx); }
        }
      });
    } catch (err) {
      // ScrollTrigger 생성 실패 → 정적 폴백. 콘텐츠는 온전히 보인다.
      killById(id);
      section.removeAttribute('data-story-staged');
      staticStage(section, els, layers, scenes);
    }
  }

  function exit(section, opts) {
    var o = opts || {};
    if (!section) return;
    var id = o.id || ('story-' + (section.id || 'section') + '-exit');

    if (!usable(o.minWidth)) {
      killById(id);
      section.style.setProperty('--story-exit', '0');
      return;
    }

    markStory();
    killById(id);
    ST().create({
      id: id,
      trigger: section,
      start: 'top top',
      end: 'bottom top',
      scrub: o.scrub == null ? 0.3 : o.scrub,
      invalidateOnRefresh: true,
      onUpdate: function (self) {
        section.style.setProperty('--story-exit', self.progress.toFixed(3));
      }
    });
    section.style.setProperty('--story-exit', '0');
  }

  function refresh() {
    if (ST() && ST().refresh) ST().refresh();
  }

  global.F2RStorefrontStory = Object.freeze({
    stage: stage,
    exit: exit,
    refresh: refresh
  });
})(typeof window !== 'undefined' ? window : globalThis);
