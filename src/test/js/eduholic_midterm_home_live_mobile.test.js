const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const js = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/eduholic_midterm_home_live.js'), 'utf8');

test('school LIVE card scenes go 시험범위 Wiki → 최근 시험 출제 경향 → 실제 수업 영상', () => {
  assert.ok(js.indexOf("tag: '시험범위'") < js.indexOf("tag: '출제 경향'"), '시험범위 → 출제 경향');
  assert.ok(js.indexOf("tag: '출제 경향'") < js.indexOf("tag: '수업 영상'"), '출제 경향 → 수업 영상');
});

test('each scene is built only when distinct data exists (§2)', () => {
  // 시험범위 = 캠페인 showcase 추출 내용, 출제 경향 = /api/analyzes 카드 본문. 서로 다른 소스.
  assert.ok(js.includes('/midterm-campaign/showcase'), 'showcase 조회');
  assert.ok(js.includes('function pickSyllabus('), 'pickSyllabus');
  assert.ok(js.includes("indexOf('final-') === 0"), 'final- 완성본이면 시험범위 scene 생략(중복 금지)');
  assert.ok(js.includes('.featured-schoolcard-row'), '출제 경향은 카드 본문 재사용');
  // 카드마다 fetch 아님 — 캠페인 스코프 2개(showcase, live-videos)만.
  assert.equal((js.match(/fetch\(/g) || []).length, 2);
  assert.ok(!js.includes('/api/analyzes'), '카드마다 analyze 재조회 금지(academy_public.js 담당)');
});

test('mobile video policy is lazy muted inline; embeds and other cards pause', () => {
  ["preload = 'none'", 'defaultMuted = true', 'playsInline = true', 'pauseOtherVideos(video)',
   'prepareVisibleVideo(false)', 'IntersectionObserver', 'data-src',
   'mt-hl-embed', "setAttribute('src', 'about:blank')"].forEach(function (token) {
    assert.ok(js.includes(token), token);
  });
});

test('EXTERNAL video embeds inline when possible (YouTube / 네이버TV), else link card', () => {
  assert.ok(js.includes('function videoScene('));
  assert.ok(js.includes("provider === 'YOUTUBE'"));
  assert.ok(js.includes("provider === 'NAVER'"));
  assert.ok(js.includes('youtube-nocookie.com/embed/'));
});

test('clip-viewer.naver.com/oembed (네이버 쇼츠 공식 iframe src) → 카드 안 embed scene, 링크카드 아님', () => {
  // videoScene 의 NAVER 분기는 host 를 가리지 않고 provider+embedUrl 로만 판단한다.
  // validator 가 clip-viewer 를 canonical embedUrl 로 통과시키면 그대로 인라인 embed scene.
  const naverBranch = js.slice(
    js.indexOf("if (link.provider === 'NAVER' && link.embedUrl)"),
    js.indexOf("href: link.externalUrl || link.embedUrl"));
  assert.ok(naverBranch.includes('embed:'), 'NAVER + embedUrl 이면 embed scene 을 만든다');
  assert.ok(!naverBranch.includes('tv.naver.com'), 'embed 판정에 host 화이트리스트가 없다(validator 가 판정)');

  // 실제 렌더링 검증: clip-viewer embedUrl 을 가진 NAVER link → data-kind="embed" scene.
  const vm = require('node:vm');
  const { MiniElement } = require('./helpers/mini_dom');
  const CLIP = 'https://clip-viewer.naver.com/oembed/?seedMediaId='
    + '7EC5EB4A7C09B448379F094D0787416655BF&mediaType=SHORT_FORM&serviceType=NTV';
  const sandbox = {
    console,
    window: { location: { hostname: 'x', pathname: '/' }, matchMedia: () => ({ matches: false }) },
    document: {
      readyState: 'loading', head: new MiniElement('head'),
      createElement: (t) => new MiniElement(t),
      getElementById: () => null, querySelectorAll: () => [], addEventListener: () => {}
    }
  };
  sandbox.window.document = sandbox.document;
  vm.createContext(sandbox);
  // videoScene 만 꺼내 쓰기 위해 IIFE 안의 함수를 노출한다.
  const src = js.replace('})();', '  sandbox.__videoScene = videoScene;\n})();')
    .replace("if (window.location.hostname !== EDUHOLIC_HOST) return;", '')
    .replace("if (window.location.pathname === LANDING_PATH) return;", '');
  vm.runInContext('var sandbox = this;\n' + src, sandbox, { filename: 'home_live.js' });
  const scene = sandbox.__videoScene('신구중', { provider: 'NAVER', embedUrl: CLIP,
    externalUrl: 'https://naver.me/F4WnDovq', title: '신구중 실제 수업' });
  assert.ok(scene.embed && scene.embed.indexOf('https://clip-viewer.naver.com/oembed/') === 0,
    'clip-viewer → embed scene (링크카드/ href 아님)');
  assert.ok(!scene.href, 'href 링크카드로 폴백하지 않는다');
});
