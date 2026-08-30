const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * 섹션 등장 애니메이션(reveal)의 안전장치.
 *
 * 이 페이지는 CSR이라 fetch 후에야 섹션이 생기고, 섹션 토글·순서변경으로 재렌더도 된다.
 * 그래서 로드 시점이 아니라 render()마다 applyReveal을 호출한다.
 *
 * 가장 위험한 실패는 CDN 장애다. `[data-fade]{opacity:0}`을 무조건 걸어두면 GSAP이
 * 안 실려온 순간 학원 페이지 전체가 빈 화면이 된다. 그래서 초기 상태는 JS가 GSAP 존재를
 * 확인하고 html.js-motion을 붙였을 때만 걸리게 해뒀다.
 */

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');

const publicCss = read('../../main/resources/static/css/academy_public.css');
const publicJs = read('../../main/resources/static/js/academy_public.js');
const publicTemplate = read('../../main/resources/templates/public/academy_public.html');

test('given 렌더된 섹션 when 마크업을 확인 then 공개·빌더 모두 data-fade를 갖는다', () => {
  for (const [name, hooks] of bothRenderers()) {
    // given
    const academy = academyFixture();
    primeRenderer(hooks, academy);

    // when / then — 정본 모듈이 내므로 두 화면이 같은 앵커를 갖는다
    for (const [label, html] of [
      ['hero', hooks.renderHero(academy)],
      ['intro', hooks.renderIntro(academy)],
      ['principal', hooks.renderPrincipal(academy)],
      ['videos', hooks.renderVideos(academy)],
      ['lessons', hooks.renderLessons(academy)],
      ['social', hooks.renderExternalServices(academy)]
    ]) {
      assert.match(html, /data-fade/, `${name}/${label}: reveal 앵커가 없다`);
    }
  }
});

test('given GSAP 미로드 when 페이지를 보면 then 섹션이 숨겨지지 않는다', () => {
  // given / when / then — 초기 opacity:0은 html.js-motion 아래에만 걸린다.
  // 이 스코프가 사라지면 CDN 장애 시 페이지가 통째로 빈 화면이 된다.
  assert.match(publicCss, /html\.js-motion \[data-fade\]\{opacity:0/,
    'reveal 초기 상태가 js-motion 스코프 밖에 있다 — CDN 장애 시 빈 화면');
  assert.doesNotMatch(publicCss, /^\[data-fade\]\{opacity:0/m,
    '무조건 걸리는 [data-fade]{opacity:0}이 있다');

  // then — JS도 GSAP이 없으면 조기 반환하고 js-motion을 붙이지 않는다
  assert.match(publicJs, /if \(!window\.gsap \|\| !window\.ScrollTrigger\) return;/);
  const revealBody = publicJs.slice(publicJs.indexOf('function applyReveal()'));
  const guardAt = revealBody.indexOf('!window.gsap');
  const classAt = revealBody.indexOf("classList.add('js-motion')");
  assert.ok(guardAt >= 0 && classAt > guardAt, 'js-motion을 GSAP 확인보다 먼저 붙인다');
});

test('given 모션 선호를 끈 사용자 when 페이지를 보면 then 섹션이 즉시 보인다', () => {
  // given / when / then
  assert.match(publicCss, /@media \(prefers-reduced-motion:reduce\)\{\s*html\.js-motion \[data-fade\]\{opacity:1/);
});

test('given 재렌더 when reveal을 다시 적용 then 이미 처리한 요소는 건너뛴다', () => {
  // given / when / then — 섹션 토글·순서변경마다 render()가 다시 돌아 중복 트리거가 쌓인다
  assert.match(publicJs, /el\.dataset\.fadeWired === 'true'/);
  assert.match(publicJs, /el\.dataset\.fadeWired = 'true'/);
  assert.match(publicJs, /dataset\.motionWired !== 'true'/);
  // render() 안에서 GSAP reveal + Storefront 모션이 순서대로 호출되어야 CSR 렌더 뒤에 잡힌다
  assert.match(publicJs, /applyReveal\(\);\s*\n\s*wireStorefrontMotion\(\);\s*\n\s*void loadVisitStatistics/);
});

test('given Storefront 모션 when 책임 분리를 확인 then Featured/AI Native는 GSAP data-fade가 아니라 primitive를 쓴다', () => {
  const sectionsJs = read('../../main/resources/static/js/academy_sections.js');
  const featured = sectionsJs.slice(sectionsJs.indexOf('function renderFeatured'), sectionsJs.indexOf('function renderAiProcess'));
  const aiProcess = sectionsJs.slice(sectionsJs.indexOf('function renderAiProcess'), sectionsJs.indexOf('function noteParts'));

  // GSAP applyReveal 대상(data-fade)이 아니어야 한다 — 두 시스템이 같은 요소에 겹치지 않는다
  assert.doesNotMatch(featured, /data-fade/, 'featured 섹션에 data-fade 잔존');
  assert.doesNotMatch(aiProcess, /data-fade/, 'ai-process 섹션에 data-fade 잔존');
  // 대신 primitive 앵커
  assert.match(featured, /featured-title f2r-reveal/);
  assert.match(featured, /featured-cta f2r-reveal/);
  assert.match(aiProcess, /ai-process-title f2r-reveal/);

  // primitive 모듈이 렌더러보다 먼저 로드된다
  assert.ok(
    publicTemplate.indexOf('/js/academy_storefront_motion.js') < publicTemplate.indexOf('/js/academy_public.js'),
    'storefront_motion이 렌더러보다 늦게 로드된다');

  // CDN/모듈 장애 안전: 초기 opacity:0은 js-storefront-motion 스코프 아래에만 걸린다
  assert.match(publicCss, /html\.js-storefront-motion \.f2r-reveal:not\(\.is-in\)\{opacity:0/);
  assert.match(publicCss, /html\.js-storefront-motion \.ai-process-step\{opacity:0/);
  assert.doesNotMatch(publicCss, /^\.f2r-reveal(:not\(\.is-in\))?\{opacity:0/m);
  assert.match(publicJs, /if \(!MOTION \|\| !root\) return;/);

  // 커넥터 활성 → 다음 카드 활성: is-linked 가 커넥터(::before)를, is-active 가 카드를 켠다
  assert.match(publicCss, /\.ai-process-step\.is-linked::before/);
  assert.match(publicCss, /\.ai-process-step\.is-linked::before[\s\S]{0,320}\{color:var\(--accent\)/);
  assert.match(publicCss, /html\.js-storefront-motion \.ai-process-step\.is-active\{[^}]*border-color/);
  // note 는 전 단계 완료(is-complete) 후 등장
  assert.match(publicCss, /\.ai-process-steps\.is-complete ~ \.ai-process-note\{opacity:1/);

  // reduced-motion: 즉시 최종 상태 + 커넥터 최종 상태
  assert.match(publicCss, /@media \(prefers-reduced-motion:reduce\)\{[\s\S]*?html\.js-storefront-motion \.f2r-reveal[\s\S]*?opacity:1!important/);
});

test('given 공개 템플릿 when 스크립트 순서를 확인 then GSAP이 렌더러보다 먼저 로드된다', () => {
  // given / when / then — 랜딩과 같은 3.12.5를 쓴다
  assert.ok(
    publicTemplate.indexOf('gsap.min.js') < publicTemplate.indexOf('/js/academy_public.js'),
    'GSAP이 렌더러보다 늦게 로드된다');
  assert.ok(
    publicTemplate.indexOf('gsap.min.js') < publicTemplate.indexOf('ScrollTrigger.min.js'),
    'ScrollTrigger가 gsap보다 먼저 로드된다');
});

test('given 공개 페이지 경로 when 아이콘을 확인 then Font Awesome 의존이 없다', () => {
  // 시안은 외부 아이콘 없이 이모지/CSS로만 그린다. 링크만 지우고 fa- 클래스를 남기면
  // 발행 페이지에 깨진 아이콘 자리가 그대로 남는다 — 실제로 lesson_product_showcase.js의
  // fa-hand-pointer가 sec-lessons 안으로 렌더되고 있었다.
  const showcaseJs = read('../../main/resources/static/js/lesson_product_showcase.js');
  const sectionsJs = read('../../main/resources/static/js/academy_sections.js');

  for (const [name, source] of [
    ['template', publicTemplate], ['public.js', publicJs],
    ['sections.js', sectionsJs], ['showcase.js', showcaseJs], ['public.css', publicCss]
  ]) {
    assert.doesNotMatch(source, /fa-solid|fa-regular|font-awesome/, `${name}: Font Awesome 잔재`);
  }

  // 체크 아이콘은 CSS가 스타일링하므로 앵커 클래스는 남아 있어야 한다.
  assert.match(sectionsJs, /class="li-check"/);
  assert.match(publicCss, /\.principal-career li \.li-check\{/);
  assert.doesNotMatch(publicCss, /\.intro-feature-list li \.li-check/);
});
