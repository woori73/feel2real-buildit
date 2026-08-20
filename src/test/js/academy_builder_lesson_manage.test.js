const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { loadRenderHooks, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

const builderJs = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js'),
  'utf8'
);

test('given 발행된 Academy when Builder 수업 관리를 렌더링 then 수업 편집기로 이동하는 링크를 제공한다', () => {
  // given — 발행된 학원은 publicPathUrl을 갖는다
  const hooks = loadRenderHooks('builder');
  const academy = academyFixture({ publicPathUrl: '/academies/test-academy' });

  // when
  const html = primeRenderer(hooks, academy).renderLessons(academy);

  // then
  assert.match(html, /<a class="cv-btn-primary builder-lesson-manage" href="\/tutor\/academy\/lessons\/editor"/);
  assert.doesNotMatch(html, /data-lesson-publish-required/, '발행된 학원인데 이동 차단 버튼이 렌더됨');
});

test('given 발행 전 Academy when Builder 수업 관리를 렌더링 then 링크 대신 차단 버튼을 렌더한다', () => {
  // given — publicPathUrl이 없으면 아직 발행 전이다
  const hooks = loadRenderHooks('builder');
  const academy = academyFixture();

  // when
  const html = primeRenderer(hooks, academy).renderLessons(academy);

  // then
  assert.match(html, /<button type="button" class="cv-btn-primary builder-lesson-manage" data-lesson-publish-required/);
  assert.doesNotMatch(html, /href="\/tutor\/academy\/lessons\/editor"/, '발행 전인데 편집기 링크가 열려 있음');
});

test('given 공개 페이지 when 수업 목록을 렌더 then 빌더 전용 관리 버튼이 새지 않는다', () => {
  // given
  const hooks = loadRenderHooks('public');
  const academy = academyFixture({ publicPathUrl: '/academies/test-academy' });

  // when
  const html = primeRenderer(hooks, academy).renderLessons(academy);

  // then
  assert.doesNotMatch(html, /builder-lesson-manage|data-lesson-publish-required/);
});

test('given 발행 전 Academy when Builder 수업 관리를 클릭 then 이동을 막고 발행 안내를 보여준다', () => {
  // given / when / then
  assert.match(builderJs, /data-lesson-publish-required/);
  assert.match(
    builderJs,
    /closest\('\[data-video-publish-required\], \[data-lesson-publish-required\]'\)/
  );
  assert.match(
    builderJs,
    /'학원 홈페이지를 먼저 발행한 후 수업을 등록할 수 있습니다\.'/
  );
  assert.match(builderJs, /lessonManagement\s*\?/);
});
