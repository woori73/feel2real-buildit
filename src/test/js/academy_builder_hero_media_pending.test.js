const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { loadRenderHooks, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');
const builderJs = read('../../main/resources/static/js/academy_builder.js');

function bodyOf(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, `${startMarker} 를 찾지 못함`);
  const end = source.indexOf(endMarker, start);
  assert.notEqual(end, -1, `${endMarker} 를 찾지 못함`);
  return source.slice(start, end);
}

test('given 발행 전 상태 when 히어로 이미지를 고르면 then 서버로 바로 올리지 않고 보류한다', () => {
  // given: 발행 전에는 Academy row가 없어 업로드 API가 404를 낸다
  const body = bodyOf(builderJs, 'async function uploadHeroMedia(', 'async function removeHeroMedia(');

  // when / then: publicPathUrl이 없으면 stash 후 조기 반환한다
  assert.match(body, /!currentPreview\.publicPathUrl/,
    '발행 전 분기가 없어 404 경로로 그대로 들어감');
  assert.match(body, /stashHeroMedia\(slot, file\)/, '보류 저장을 하지 않음');
  assert.match(body, /발행 시 함께 저장됩니다/, '로고와 같은 안내 문구가 없음');

  const guardAt = body.indexOf('!currentPreview.publicPathUrl');
  const fetchAt = body.indexOf('fetch(');
  assert.ok(guardAt < fetchAt, '발행 전 분기가 fetch 뒤에 있어 404가 먼저 난다');
});

test('given 발행 성공 when 보류한 이미지가 있으면 then 리다이렉트 전에 업로드한다', () => {
  // given: Academy row는 발행 응답이 성공한 뒤에야 생긴다.
  // `const academy = await res.json()`은 파일 곳곳에 있으므로 발행 성공 지점을 고유 문구로 잡는다.
  const flushAt = builderJs.indexOf('await flushPendingHeroMedia()');
  assert.notEqual(flushAt, -1, '보류분을 발행 후 올리지 않음');

  // when / then: 같은 성공 블록 안에서 flush → clearPending → 리다이렉트 순서여야 한다
  const clearAt = builderJs.indexOf('clearPending();', flushAt);
  const publishToastAt = builderJs.indexOf('학원 페이지가 발행되었습니다', flushAt);
  const redirectAt = builderJs.indexOf('location.href = target', flushAt);

  assert.ok(clearAt !== -1 && flushAt < clearAt,
    'clearPending()이 먼저 돌면 보류분이 지워진 뒤 업로드하게 된다');
  assert.ok(redirectAt !== -1 && flushAt < redirectAt,
    '리다이렉트 뒤에 업로드하면 요청이 중단된다');
  assert.ok(publishToastAt !== -1 && publishToastAt - flushAt < 400,
    'flush가 발행 성공 블록 안에 있지 않다');
});

test('given 보류 중인 슬롯 when 캔버스를 렌더 then 로컬 objectURL을 쓴다', () => {
  // given — 발행 전에는 /api/tutor/academy/hero-media/{slot} 이 404다.
  //         저장 전 업로드는 로컬 objectURL로 미리 보여줘야 한다.
  const hooks = loadRenderHooks('builder');
  const academy = academyFixture({ heroMediaSlots: [] });
  primeRenderer(hooks, academy);
  hooks.setPendingHeroMedia({ principal: { objectUrl: 'blob:local-principal' } });

  // when
  const html = hooks.renderHero(academy);

  // then — 보류분이 채워진 슬롯으로 보여야 placeholder가 사라진다
  assert.match(html, /src="blob:local-principal"/, '로컬 미리보기 URL을 쓰지 않음');
  assert.doesNotMatch(html, /photo-empty/, '보류분이 있는데 placeholder가 남았다');
});

test('given 보류분이 없는 슬롯 when 캔버스를 렌더 then 소유자 전용 경로에 캐시버스팅을 붙인다', () => {
  // given — 저장된 슬롯은 서버에서 읽는다. 발행 전 학원도 봐야 하므로 공개 경로를 쓸 수 없다.
  const hooks = loadRenderHooks('builder');
  const academy = academyFixture({ heroMediaSlots: ['principal'] });
  primeRenderer(hooks, academy);
  hooks.setPendingHeroMedia({});

  // when
  const html = hooks.renderHero(academy);

  // then
  assert.match(html, /src="\/api\/tutor\/academy\/hero-media\/principal\?v=\d+"/);
});

test('given 보류 중인 슬롯 when 삭제하면 then 서버를 호출하지 않는다', () => {
  // given: 서버에 없는 이미지를 DELETE하면 404가 난다
  const body = bodyOf(builderJs, 'async function removeHeroMedia(', 'function heroMediaForm(');

  // when / then
  const pendingAt = body.indexOf('pendingHeroMedia[slot]');
  const fetchAt = body.indexOf('fetch(');
  assert.ok(pendingAt !== -1 && pendingAt < fetchAt, '보류분 분기가 없거나 fetch 뒤에 있음');
  assert.match(body, /dropPendingHeroMedia\(slot\)/, '보류분을 정리하지 않음');
});

test('given 본문 없는 실패 응답 when 토스트를 띄우면 then 빈 메시지 대신 기본 문구를 쓴다', () => {
  // given: 404 등은 body가 비어 있어 new Error('') 가 되고 err.message가 빈 문자열이 된다
  const body = bodyOf(builderJs, 'function heroMediaErrorMessage(', 'function stashHeroMedia(');

  // when / then
  assert.match(body, /message \|\| fallback/, '빈 메시지가 그대로 토스트에 노출된다');
  assert.match(body, /trim\(\)/, '공백만 있는 응답을 걸러내지 않는다');
});

test('given 보류 이미지 when 저장 방식을 확인 then sessionStorage를 쓰지 않는다', () => {
  // given: 움짤은 十MB 단위라 base64로 sessionStorage에 넣으면 용량 한도를 넘긴다
  const body = bodyOf(builderJs, 'function stashHeroMedia(', '/**\n   * 발행 직후');

  // when / then
  assert.doesNotMatch(body, /sessionStorage|writeJson/, 'sessionStorage에 담으면 큰 움짤에서 터진다');
  assert.match(body, /URL\.createObjectURL\(file\)/, 'objectURL로 미리보기를 만들지 않음');
});

test('given 보류분 정리 when objectURL을 해제 then revokeObjectURL을 호출한다', () => {
  // given: objectURL을 놓아주지 않으면 큰 파일이 메모리에 남는다
  const body = bodyOf(builderJs, 'function dropPendingHeroMedia(', '/**\n   * 발행 직후');

  // when / then
  assert.match(body, /URL\.revokeObjectURL\(pending\.objectUrl\)/, 'objectURL 해제를 하지 않음');
  assert.match(body, /delete pendingHeroMedia\[slot\]/, '보류 항목을 지우지 않음');
});
