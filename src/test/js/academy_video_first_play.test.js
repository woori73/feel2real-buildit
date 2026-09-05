const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const fs = require('node:fs');
const { loadSectionsModule } = require('./helpers/browser_script_harness');

const businessInfo = require('../../main/resources/static/js/academy_business_info.js');

const PUBLIC_JS = path.resolve(__dirname, '../../main/resources/static/js/academy_public.js');

/**
 * 오래된 production 버그: 새로고침 직후 재생목록의 "첫 번째" 영상을 클릭하면 재생되지
 * 않거나 반응하지 않고, 다른 영상을 먼저 재생한 뒤 다시 첫 영상을 선택하면 그제야
 * 재생됐다.
 *
 * 원인은 CSS가 아니라 클릭 배선이었다: 서버 렌더 HTML이 이미 `<video src="첫 영상 URL">`
 * 로 첫 영상을 로드해 둔 상태인데, 첫 재생목록 항목 클릭 핸들러가 무조건
 * `player.src = item.dataset.videoSelect`를 실행했다. 첫 항목의 URL은 지금 로드된 값과
 * "똑같은" 문자열이라도, `.src`를 다시 대입하면 브라우저는 media element load algorithm을
 * 재실행해 진행 중이던 최초 로드를 스스로 중단시킨다(play()가 조용히 실패). 실제로 다른
 * 영상을 클릭하면 src가 진짜로 바뀌므로 정상적으로 로드→재생된다 — 그래서 "다른 영상은
 * 되는데 첫 영상만 첫 클릭에 안 된다"는 정확히 이 버그의 증상과 일치한다.
 *
 * 이 테스트는 academy_public.js의 실제 클릭 위임 핸들러를 실행해 검증한다(재구현 아님).
 */
function runPublicPage(academy, videos) {
  const elements = new Map();
  const element = (id) => {
    if (!elements.has(id)) {
      elements.set(id, {
        id,
        dataset: {},
        style: {},
        innerHTML: '',
        textContent: '',
        classList: { add() {}, remove() {} },
        addEventListener() {},
        contains() { return true; },
        querySelectorAll() { return []; }
      });
    }
    return elements.get(id);
  };

  const root = element('publicView');
  let rootClickHandler = null;
  root.addEventListener = (type, cb) => {
    if (type === 'click') rootClickHandler = cb;
  };

  const player = {
    id: 'academyVideoPlayer',
    dataset: {},
    _src: '',
    srcSetCount: 0,
    playCallCount: 0,
    classList: { add() {}, remove() {} },
    getAttribute(name) { return name === 'src' ? this._src : null; },
    play() {
      this.playCallCount += 1;
      return Promise.resolve();
    }
  };
  Object.defineProperty(player, 'src', {
    get() { return this._src; },
    set(value) {
      this.srcSetCount += 1;
      this._src = value;
    }
  });

  const document = {
    getElementById(id) {
      if (id === 'academyVideoPlayer') return player;
      return ['publicView', 'errorMsg'].includes(id) ? element(id) : null;
    },
    addEventListener() {},
    querySelector() { return null; },
    querySelectorAll() { return []; }
  };
  element('publicView').dataset.slug = academy.slug;

  const context = vm.createContext({
    console,
    document,
    async fetch(url) {
      if (url === `/api/academies/${academy.slug}`) return { ok: true, json: async () => academy };
      if (url === `/api/academies/${academy.slug}/videos`) return { ok: true, json: async () => videos };
      return { ok: false, json: async () => [] };
    },
    async fetchAuth() { return { ok: false }; },
    location: { href: '', origin: 'http://localhost', pathname: `/academies/${academy.slug}` },
    sessionStorage: { getItem() { return null; } },
    setTimeout,
    window: {
      F2RAcademyBusinessInfo: businessInfo,
      F2RLessonProductShowcase: { render() { return ''; } },
      F2RAcademySections: loadSectionsModule(),
      addEventListener() {}
    }
  });
  vm.runInContext(fs.readFileSync(PUBLIC_JS, 'utf8'), context, { filename: PUBLIC_JS });

  return {
    player,
    click(item) {
      if (!rootClickHandler) throw new Error('재생목록 클릭 핸들러가 아직 등록되지 않았다');
      rootClickHandler({
        preventDefault() {},
        stopPropagation() {},
        // 실제 DOM의 closest()처럼 셀렉터를 따진다 — data-schedule-toggle 분기가 이 클릭을
        // 가로채지 않는지도 함께 검증한다.
        target: { closest: (selector) => (selector === '[data-video-select]' ? item : null) }
      });
    }
  };
}

const academy = () => ({
  slug: 'sample-academy',
  name: '샘플 학원',
  intro: '소개',
  subjects: [],
  regions: [],
  lessons: [],
  pageSettings: { sectionVisibility: {}, builderDraft: {} }
});

const videoList = () => [
  { videoId: 1, videoUrl: '/api/academies/sample-academy/videos/1/stream', thumbnailUrl: null, durationSec: 60 },
  { videoId: 2, videoUrl: '/api/academies/sample-academy/videos/2/stream', thumbnailUrl: null, durationSec: 30 }
];

test('given 새로고침 직후 첫 영상이 이미 <video src>로 로드된 상태 when 재생목록 1번 항목을 클릭 then src를 다시 대입하지 않고 재생만 시도한다', async () => {
  // given — 서버 렌더 HTML이 이미 첫 영상 URL을 player.src로 세팅해 둔 상태를 흉내낸다.
  const videos = videoList();
  const page = runPublicPage(academy(), videos);
  await new Promise((resolve) => setTimeout(resolve, 0));
  page.player.src = videos[0].videoUrl;
  page.player.srcSetCount = 0; // 위 대입은 "초기 렌더"로 취급하고 카운트를 리셋한다

  // when
  const firstItem = { dataset: { videoSelect: videos[0].videoUrl }, classList: { add() {}, remove() {} } };
  page.click(firstItem);

  // then — 동일 URL 재대입은 media element load algorithm을 재실행시켜 진행 중이던
  // 첫 로드를 스스로 중단시킨다. 값이 안 바뀌었으면 절대 재대입하면 안 된다.
  assert.equal(page.player.srcSetCount, 0, '이미 로드된 첫 영상과 동일한 src를 다시 대입하면 첫 재생이 끊긴다');
  // then — 재생 시도 자체는 첫 클릭에서도 반드시 일어나야 한다.
  assert.equal(page.player.playCallCount, 1, '첫 클릭에서도 play()를 호출해야 한다');
});

test('given 다른 영상으로 전환 when 2번 항목을 클릭 then src를 실제로 갈아끼우고 재생한다', async () => {
  // given
  const videos = videoList();
  const page = runPublicPage(academy(), videos);
  await new Promise((resolve) => setTimeout(resolve, 0));
  page.player.src = videos[0].videoUrl;
  page.player.srcSetCount = 0;

  // when
  const secondItem = { dataset: { videoSelect: videos[1].videoUrl }, classList: { add() {}, remove() {} } };
  page.click(secondItem);

  // then — 실제로 다른 URL이면 정상적으로 src를 대입해야 한다(회귀 방지: 위 수정이
  // 모든 src 대입을 막아버리는 방향으로 잘못 구현되지 않았는지 확인).
  assert.equal(page.player.srcSetCount, 1, '다른 영상은 실제로 src를 갈아끼워야 한다');
  assert.equal(page.player._src, videos[1].videoUrl);
  assert.equal(page.player.playCallCount, 1);
});

test('given 이미 선택된(active) 영상을 다시 클릭 when 재생 중이 아니어도 then 항상 재생을 시도한다', async () => {
  // given — 2번 영상으로 이미 전환된 상태에서 같은 2번을 재선택하는 경우
  const videos = videoList();
  const page = runPublicPage(academy(), videos);
  await new Promise((resolve) => setTimeout(resolve, 0));
  page.player.src = videos[1].videoUrl;
  page.player.srcSetCount = 0;

  // when
  const secondItem = { dataset: { videoSelect: videos[1].videoUrl }, classList: { add() {}, remove() {} } };
  page.click(secondItem);

  // then — 동일 영상 재선택은 재로드가 아니라 이어재생/재생 시도만 해야 한다.
  assert.equal(page.player.srcSetCount, 0);
  assert.equal(page.player.playCallCount, 1);
});
