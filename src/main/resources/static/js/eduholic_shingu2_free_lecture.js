/* 압구정에듀홀릭 홈페이지 — 「2학기 영어내신」(#sec-featured)에 신구중2 직보 문제해설
 * 무료 공개 강의를 추가한다. 로그인·LMS·workspace 없이 이 화면에서 바로 재생한다.
 *
 * - hostname gate(eduholic.feel2real.co.kr)로 다른 Academy 공개 페이지에는 노출되지 않는다.
 * - 공용 섹션 마크업(academy_sections.js)·커버플로우 카드·상담 신청 CTA는 건드리지 않는다.
 *   `.featured-cta` 앞에 독립 패널을 삽입하고, 이미 있으면 되풀이하지 않는다.
 * - 영상은 정적 자산 `/video/eduholic/*.mp4`(principal-intro.mp4와 같은 서빙 방식).
 *   MEDIA_SRC 한 상수만 바꾸면 저장 위치를 옮길 수 있다.
 * - autoplay 금지: 「무료로 바로 보기」를 눌렀을 때만 player가 나타나고 재생된다.
 */
(function () {
  'use strict';

  var EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  var PANEL_CLASS = 'mt-free-lecture';
  var OPEN_CLASS = 'is-open';
  var MEDIA_SRC = '/video/eduholic/shingu2-jikbo-explainer.mp4';
  // 이 영상이 속한 학교. 섹션의 실제 학교 카드([data-school])에 신구중이 있을 때만 노출한다 —
  // 카드 목록은 builderDraft(DB) 값이므로 코드가 학교를 만들지 않는다.
  var SCHOOL = '신구중';
  var LABEL = {
    title: '신구중2 직보 문제해설',
    desc: '내일 시험 전 마지막으로 확인하는 영어 문제풀이 · 핵심 해설',
    cta: '무료로 바로 보기',
    badge: '무료 · 로그인 없음',
    length: '약 5분 35초'
  };

  if (window.location.hostname !== EDUHOLIC_HOST) return;

  function normalizedSchool(value) {
    return String(value || '').replace(/중학교/g, '중').replace(/\s+/g, '').toLowerCase();
  }

  function hasSchoolCard(inner) {
    var cards = inner.querySelectorAll('[data-school]');
    for (var i = 0; i < cards.length; i++) {
      if (normalizedSchool(cards[i].getAttribute('data-school')) === normalizedSchool(SCHOOL)) return true;
    }
    return false;
  }

  function apply() {
    var inner = document.querySelector('#sec-featured .featured-inner');
    if (!inner) return;
    if (inner.querySelector(':scope > .' + PANEL_CLASS)) return;
    if (!hasSchoolCard(inner)) return; // 신구중 카드가 없는 화면에서는 아무것도 만들지 않는다

    var panel = document.createElement('article');
    panel.className = PANEL_CLASS;
    panel.setAttribute('data-free-lecture', '');

    var head = document.createElement('div');
    head.className = PANEL_CLASS + '__head';
    var badge = document.createElement('span');
    badge.className = PANEL_CLASS + '__badge';
    badge.textContent = LABEL.badge;
    var school = document.createElement('b');
    school.className = PANEL_CLASS + '__school';
    school.textContent = SCHOOL;
    var title = document.createElement('h3');
    title.className = PANEL_CLASS + '__title';
    title.textContent = LABEL.title;
    head.appendChild(school);
    head.appendChild(title);
    head.appendChild(badge);
    panel.appendChild(head);

    var desc = document.createElement('p');
    desc.className = PANEL_CLASS + '__desc';
    desc.textContent = LABEL.desc;
    panel.appendChild(desc);

    var bar = document.createElement('div');
    bar.className = PANEL_CLASS + '__bar';
    var cta = document.createElement('button');
    cta.type = 'button';
    cta.className = PANEL_CLASS + '__cta';
    cta.textContent = LABEL.cta;
    var length = document.createElement('span');
    length.className = PANEL_CLASS + '__length';
    length.textContent = LABEL.length;
    bar.appendChild(cta);
    bar.appendChild(length);
    panel.appendChild(bar);

    var box = document.createElement('div');
    box.className = PANEL_CLASS + '__player';
    panel.appendChild(box);

    cta.addEventListener('click', function () {
      if (box.querySelector('video')) {
        panel.classList.add(OPEN_CLASS);
        return;
      }
      var video = document.createElement('video');
      video.className = PANEL_CLASS + '__video';
      video.controls = true;
      video.playsInline = true;
      video.setAttribute('preload', 'metadata');
      video.setAttribute('controlsList', 'nodownload');
      video.setAttribute('aria-label', LABEL.title);
      var source = document.createElement('source');
      source.src = MEDIA_SRC;
      source.type = 'video/mp4';
      video.appendChild(source);
      video.addEventListener('play', function () { pauseOtherVideos(video); });
      box.appendChild(video);
      panel.classList.add(OPEN_CLASS);
      video.play().catch(function () { /* 컨트롤로 직접 재생 */ });
    });

    var ctaRow = inner.querySelector(':scope > .featured-cta');
    if (ctaRow) inner.insertBefore(panel, ctaRow);
    else inner.appendChild(panel);
  }

  function pauseOtherVideos(active) {
    Array.prototype.slice.call(document.querySelectorAll('#sec-featured video')).forEach(function (video) {
      if (video !== active && !video.paused) { try { video.pause(); } catch (e) {} }
    });
  }

  function injectStyle() {
    if (document.getElementById('eduholicShingu2FreeLectureStyle')) return;
    var style = document.createElement('style');
    style.id = 'eduholicShingu2FreeLectureStyle';
    style.textContent = [
      '#sec-featured .' + PANEL_CLASS + '{',
      '  box-sizing:border-box;width:min(100%,720px);margin:40px auto 0;padding:22px 20px;',
      '  text-align:left;border:1px solid rgba(96,165,250,.34);border-radius:18px;',
      '  background:rgba(255,255,255,.04);color:#fff;',
      '}',
      '#sec-featured .' + PANEL_CLASS + '__head{display:flex;flex-wrap:wrap;align-items:center;gap:8px 10px;}',
      '#sec-featured .' + PANEL_CLASS + '__badge{font-size:11px;font-weight:800;letter-spacing:.02em;padding:4px 10px;border-radius:999px;background:rgba(96,165,250,.16);color:#93c5fd;}',
      '#sec-featured .' + PANEL_CLASS + '__school{font-size:13px;font-weight:800;color:#93c5fd;}',
      '#sec-featured .' + PANEL_CLASS + '__title{margin:0;flex:1 1 100%;font-size:clamp(19px,3.4vw,24px);font-weight:900;letter-spacing:-.02em;line-height:1.3;color:#fff;word-break:keep-all;}',
      '#sec-featured .' + PANEL_CLASS + '__desc{margin:10px 0 0;font-size:14px;line-height:1.65;color:rgba(255,255,255,.74);word-break:keep-all;}',
      '#sec-featured .' + PANEL_CLASS + '__bar{display:flex;flex-wrap:wrap;align-items:center;gap:12px;margin-top:16px;}',
      '#sec-featured .' + PANEL_CLASS + '__cta{',
      '  appearance:none;cursor:pointer;padding:11px 20px;border-radius:999px;border:1px solid #60a5fa;',
      '  background:#1d4ed8;color:#fff;font-size:14px;font-weight:800;line-height:1;',
      '  transition:background .15s ease, box-shadow .15s ease;',
      '}',
      '#sec-featured .' + PANEL_CLASS + '__cta:hover{background:#1e40af;box-shadow:0 8px 22px rgba(29,78,216,.36);}',
      '#sec-featured .' + PANEL_CLASS + '__cta:focus-visible{outline:3px solid #93c5fd;outline-offset:3px;}',
      '#sec-featured .' + PANEL_CLASS + '__length{font-size:12.5px;font-weight:700;color:rgba(255,255,255,.6);}',
      '#sec-featured .' + PANEL_CLASS + '__player{display:none;margin-top:18px;}',
      '#sec-featured .' + PANEL_CLASS + '.is-open .' + PANEL_CLASS + '__player{display:block;}',
      '#sec-featured .' + PANEL_CLASS + '.is-open .' + PANEL_CLASS + '__cta{display:none;}',
      '#sec-featured .' + PANEL_CLASS + '__video{display:block;width:100%;aspect-ratio:16/9;background:#090d18;border-radius:14px;}',
      '@media (max-width:600px){#sec-featured .' + PANEL_CLASS + '{margin-top:28px;padding:18px 15px;border-radius:15px;}}',
      '@media (prefers-reduced-motion:reduce){#sec-featured .' + PANEL_CLASS + '__cta{transition:none;}}'
    ].join('');
    document.head.appendChild(style);
  }

  function start() {
    injectStyle();
    apply();
    var pv = document.getElementById('publicView');
    if (pv && window.MutationObserver) {
      // 공개 섹션은 CSR 렌더 + 빌더 재적용 message 로 다시 그려질 수 있어 관찰이 필요하다.
      new MutationObserver(apply).observe(pv, { childList: true, subtree: true });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
