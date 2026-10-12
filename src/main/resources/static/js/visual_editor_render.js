/*
 * BuildIt 시각 편집기 공유 렌더러.
 *
 * 편집 캔버스 · 미리보기 · 공개 페이지가 같은 방식으로 HTML/CSS를 그리도록 한 곳에 둔다.
 *  - mount(host, html, css): Shadow DOM에 격리 렌더 (공개 페이지)
 *  - previewDoc(html, css, baseCss): sandbox iframe srcdoc 문서 (미리보기)
 *  - scopeCss(css): GrapesJS CSS의 body/html 선택자를 .ve-root로 바꿔 Shadow DOM에서도 같은 효과
 *
 * 왜 Shadow DOM인가: 공개 페이지는 P0 사이트 CSS가 큰 특이도로 제목·문단 여백을 0으로
 * 만들고(#publicView :is(h1,...,p):not(...)), 편집기 CSS를 문서 전역 <style>로 넣으면
 * 반대로 P0 GNB/푸터가 영향을 받는다. 격리하면 양방향 충돌이 모두 사라진다.
 */
(function (global) {
  'use strict';

  const BASE_CSS_URL = '/css/visual_editor_base.css';
  const BASE_CSS_VERSION = '20261012-1';
  const PRETENDARD_URL =
    'https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css';

  // Shadow 루트 안에서 호스트 쪽 상속(글꼴·색·정렬 등)을 끊고 블록으로 만든다.
  const SHADOW_PREFACE = '.ve-root{all:initial;display:block;}';

  let baseCssPromise = null;

  function loadBaseCss() {
    if (!baseCssPromise) {
      baseCssPromise = fetch(BASE_CSS_URL + '?v=' + BASE_CSS_VERSION)
        .then((res) => (res.ok ? res.text() : ''))
        .catch(() => '');
    }
    return baseCssPromise;
  }

  /**
   * GrapesJS CSS의 최상위 body/html 선택자를 .ve-root로 바꾼다.
   * (Shadow DOM 안에는 body/html이 없어 `body{...}` 규칙이 아무 데도 적용되지 않기 때문이다.)
   * 속성 값·클래스명 안의 "body" 글자는 건드리지 않는다.
   */
  function scopeCss(css) {
    return String(css || '').replace(/(^|[,{}\s])(?:html|body)(?=\s*[,{.:#\[>+~])/g, '$1.ve-root');
  }

  /**
   * host(요소)에 html/css를 Shadow DOM으로 격리해 그린다.
   * @returns {Promise<ShadowRoot>}
   */
  async function mount(host, html, css) {
    if (!host || typeof host.attachShadow !== 'function') {
      throw new Error('Shadow DOM을 지원하지 않는 환경입니다.');
    }
    const base = await loadBaseCss();
    const shadow = host.shadowRoot || host.attachShadow({ mode: 'open' });
    shadow.innerHTML =
      '<style>' + SHADOW_PREFACE + '\n' + base + '\n' + scopeCss(css) + '</style>'
      + '<div class="ve-root">' + String(html || '') + '</div>';
    return shadow;
  }

  /**
   * 미리보기 iframe(srcdoc)용 문서를 만든다. 기본 CSS를 인라인으로 넣어 캔버스·공개와 같은 기본값을 쓴다.
   * <body class="ve-root">로 .ve-root 규칙과 body 규칙이 모두 적용된다.
   */
  function previewDoc(html, css, baseCss) {
    return '<!DOCTYPE html><html lang="ko"><head><meta charset="UTF-8">'
      + '<meta name="viewport" content="width=device-width, initial-scale=1.0">'
      + '<link rel="stylesheet" href="' + PRETENDARD_URL + '">'
      + '<style>' + String(baseCss || '') + '\n' + String(css || '') + '</style></head>'
      + '<body class="ve-root">' + String(html || '') + '</body></html>';
  }

  global.F2RVisualEditorRender = {
    BASE_CSS_URL: BASE_CSS_URL,
    BASE_CSS_VERSION: BASE_CSS_VERSION,
    PRETENDARD_URL: PRETENDARD_URL,
    loadBaseCss: loadBaseCss,
    scopeCss: scopeCss,
    mount: mount,
    previewDoc: previewDoc
  };
})(window);
