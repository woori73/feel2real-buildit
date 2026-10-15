const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { loadRenderHooks, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * normalizePreview는 화이트리스트로 preview 객체를 새로 만든다.
 *
 * 그래서 렌더러가 읽는 필드를 여기 추가하지 않으면 렌더 직전에 값이 조용히 사라진다.
 * 예외도 경고도 없이 `undefined`가 되기 때문에, 서버 응답과 DB가 멀쩡한데
 * 캔버스만 빈 화면으로 보이는 형태로 나타난다(heroMediaSlots가 실제로 그랬다).
 * 이 테스트가 그 누락을 잡는다.
 */

const builderJs = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js'),
  'utf8'
);

/** normalizePreview가 돌려주는 객체 리터럴의 최상위 키 목록. */
function normalizePreviewKeys(source) {
  const start = source.indexOf('function normalizePreview(');
  assert.notEqual(start, -1, 'normalizePreview 선언을 찾지 못했다');
  const open = source.indexOf('return {', start);
  assert.notEqual(open, -1, 'normalizePreview의 반환 객체를 찾지 못했다');

  let depth = 0;
  let end = -1;
  for (let i = source.indexOf('{', open); i < source.length; i += 1) {
    if (source[i] === '{') depth += 1;
    if (source[i] === '}') {
      depth -= 1;
      if (depth === 0) { end = i; break; }
    }
  }
  assert.notEqual(end, -1, 'normalizePreview의 반환 객체가 닫히지 않았다');

  const body = source.slice(open, end);
  // 최상위(들여쓰기 6칸) 키만 센다 — 중첩 객체의 키는 제외한다.
  return new Set([...body.matchAll(/^ {6}([a-zA-Z][a-zA-Z0-9_]*):/gm)].map((m) => m[1]));
}

/** 빌더 소스에서 `academy.<필드>` 로 읽는 이름들(메서드 호출은 제외). */
function fieldsReadFromAcademy(source) {
  return new Set(
    [...source.matchAll(/\bacademy\.([a-zA-Z][a-zA-Z0-9_]*)\s*(\()?/g)]
      .filter((m) => !m[2])
      .map((m) => m[1])
  );
}

test('given 빌더 렌더러가 읽는 academy 필드 when normalizePreview와 비교 then 화이트리스트에 모두 있다', () => {
  // given
  const whitelist = normalizePreviewKeys(builderJs);
  const read = fieldsReadFromAcademy(builderJs);

  // when
  const dropped = [...read].filter((field) => !whitelist.has(field)).sort();

  // then — 여기 걸리면 렌더러는 그 필드를 읽는데 normalizePreview가 지우고 있다는 뜻이다
  assert.deepEqual(
    dropped,
    [],
    `normalizePreview 화이트리스트에 추가해야 하는 필드: ${dropped.join(', ')}`
  );
});

test('given 업로드된 히어로 이미지 슬롯 when 캔버스를 렌더 then placeholder 대신 실제 이미지가 나온다', () => {
  // given
  const whitelist = normalizePreviewKeys(builderJs);

  // when / then
  // 회귀 방지: 이 키가 빠지면 hasHeroMedia()가 항상 false가 되어
  // 원장 대표사진이 업로드해도 영영 시안 placeholder로만 보인다.
  assert.ok(
    whitelist.has('heroMediaSlots'),
    'normalizePreview가 heroMediaSlots를 보존해야 한다'
  );
  // 공개 렌더러와 같은 게이트를 쓰는지 렌더 결과로 확인한다.
  const academy = academyFixture({ heroMediaSlots: ['principal'] });
  for (const kind of ['public', 'builder']) {
    const html = primeRenderer(loadRenderHooks(kind), academy).renderHero(academy);
    assert.match(html, /<img src="[^"]*principal[^"]*"/, `${kind}: 업로드한 원장사진이 placeholder로만 보인다`);
    assert.doesNotMatch(html, /tablet-empty/, `${kind}: 업로드했는데 placeholder가 남았다`);
  }
  // 슬롯이 비어 있으면 양쪽 모두 placeholder로 떨어진다(게이트가 실제로 동작하는지).
  const empty = academyFixture({ heroMediaSlots: [] });
  for (const kind of ['public', 'builder']) {
    const html = primeRenderer(loadRenderHooks(kind), empty).renderHero(empty);
    assert.match(html, /tablet-empty/, `${kind}: 슬롯이 비었는데 placeholder가 없다`);
    assert.doesNotMatch(html, /<img src="[^"]*principal/, `${kind}: 슬롯이 비었는데 이미지를 그린다`);
  }
});
