const path = require('node:path');
const { loadBrowserScript } = require('./browser_script_harness');

const PUBLIC_JS = path.resolve(__dirname, '../../../main/resources/static/js/academy_public.js');
const BUILDER_JS = path.resolve(__dirname, '../../../main/resources/static/js/academy_builder.js');

/**
 * 섹션 렌더 결과 단언용 헬퍼.
 *
 * 이전에는 테스트가 academy_public.js / academy_builder.js의 **소스 텍스트**를 정규식으로
 * 검사했다. 그 방식은 두 가지 문제가 있었다.
 *   1) 함수명·변수명·if문 형태 같은 구현 세부를 계약으로 고정한다.
 *   2) `source.slice(indexOf(A), indexOf(B))` 패턴은 A나 B가 사라지면 -1이 되어
 *      빈 문자열을 검사하며 **조용히 통과한다**(vacuous green).
 * 렌더 결과를 직접 보면 두 문제가 모두 사라진다.
 *
 * ⚠ 하네스는 jsdom이 아니라 vm 샌드박스이고 document는 스텁이다.
 *    따라서 단언은 **innerHTML 문자열 매칭**까지만 가능하고 DOM 질의는 쓸 수 없다.
 */
function loadRenderHooks(kind, options = {}) {
  const file = kind === 'builder' ? BUILDER_JS : PUBLIC_JS;
  const { window } = loadBrowserScript(file, {
    ...options,
    // public은 이 플래그가 없으면 즉시 fetch로 Academy를 불러오려 한다.
    window: { F2R_ACADEMY_SECTION_ORDER_TEST: true, ...(options.window || {}) }
  });
  const hooks = window.F2RAcademyRenderTestHooks;
  if (!hooks) throw new Error(`${kind}: F2RAcademyRenderTestHooks가 노출되지 않았다`);
  return hooks;
}

/** public/builder 두 렌더러를 같은 데이터로 돌려 비교할 때 쓴다. */
function bothRenderers(options = {}) {
  return [
    ['public', loadRenderHooks('public', options)],
    ['builder', loadRenderHooks('builder', options)]
  ];
}

/**
 * 렌더 호출 전 준비. 빌더 렌더 함수는 전역 currentPreview/builderVideos를 읽으므로
 * 테스트가 그 둘을 주입해야 public과 같은 조건이 된다.
 */
function primeRenderer(hooks, academy, videos = []) {
  if (hooks.setPreview) hooks.setPreview(academy);
  if (hooks.setVideos) hooks.setVideos(videos);
  return hooks;
}

/** 문자열 안에서 마커들이 주어진 순서대로 등장하는지. 하나라도 없으면 실패시킨다. */
function markerOrder(html, markers) {
  return markers.map((marker) => {
    const index = html.indexOf(marker);
    if (index < 0) throw new Error(`마커를 찾을 수 없다: ${marker}`);
    return index;
  });
}

function isAscending(values) {
  return values.every((value, index) => index === 0 || values[index - 1] < value);
}

/** 최소 필드만 채운 Academy. 테스트가 필요한 것만 덮어쓴다. */
function academyFixture(overrides = {}) {
  const { pageSettings, ...rest } = overrides;
  return {
    name: '테스트학원',
    slug: 'test-academy',
    intro: '학원 소개 문구',
    logoUrl: null,
    subjects: [{ nameKo: '영어' }],
    regions: [{ displayName: '강남구' }],
    targetGroups: ['MIDDLE'],
    lessons: [],
    heroMediaSlots: [],
    ...rest,
    pageSettings: {
      sectionVisibility: {},
      builderDraft: {},
      ...(pageSettings || {})
    }
  };
}

module.exports = {
  loadRenderHooks,
  bothRenderers,
  primeRenderer,
  markerOrder,
  isAscending,
  academyFixture,
  PUBLIC_JS,
  BUILDER_JS
};
