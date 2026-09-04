const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.resolve(
  __dirname, '../../main/resources/static/js/lesson_product_editor.js'), 'utf8');

// 버그: 기존 수업의 대표 이미지를 새로 올리면, 편집기 미리보기(가로형 Program Card와
// 세로형 courseMediaArea 둘 다)가 lessonId 기반 /hero-image 엔드포인트로 이미지를 그렸다.
// 그 엔드포인트는 서버에 저장된 mediaImageKey만 돌려주는데, 새 이미지는 [수업상품 저장]을
// 눌러 컬렉션 PUT이 성공하기 전까지 DB에 반영되지 않는다 — 그래서 업로드 직후에는 항상
// 예전 이미지(또는 캐시된 응답)가 그대로 남아, 새 이미지가 반영되지 않거나 카드 이미지가
// 바뀌지 않는 것처럼 보였다. 고침: imageKey -> blob URL 로컬 미리보기를 만들어 저장 전에도
// 우선 그것을 보여주고, 저장 후 새로고침되면(맵이 비므로) 서버 URL로 자연히 넘어간다.

test('heroImageSrc는 로컬 blob 미리보기를 lessonId 기반 서버 URL보다 우선한다', () => {
  assert.match(source, /function heroImageSrc\(imageKey, lessonId\) \{/);
  const body = source.slice(source.indexOf('function heroImageSrc'));
  const localLookupIndex = body.indexOf('localImagePreviews.get(imageKey)');
  const serverUrlIndex = body.indexOf('/hero-image?v=');
  assert.ok(localLookupIndex > -1, 'localImagePreviews 조회가 있어야 한다');
  assert.ok(serverUrlIndex > -1, 'lessonId 기반 폴백 URL이 있어야 한다');
  assert.ok(localLookupIndex < serverUrlIndex, '로컬 미리보기 조회가 서버 URL 조립보다 먼저 와야 한다');
});

test('업로드 성공 시 파일을 즉시 로컬 미리보기로 등록한 뒤 draft에 반영한다', () => {
  const uploadBlock = source.slice(
    source.indexOf('async function uploadMedia'),
    source.indexOf('function setMediaStatus'));
  assert.match(uploadBlock, /registerLocalImagePreview\(payload\.imageKey, file\);\s*\n\s*setMedia\(payload\.imageKey, null\);/,
    'registerLocalImagePreview가 setMedia보다 먼저 호출돼야 즉시 렌더에 blob이 반영된다');
});

test('가로형 Program Card 미리보기는 lessonId만으로 직접 <img src>를 조립하지 않고 heroImageSrc를 거친다', () => {
  const ctxBlock = source.slice(
    source.indexOf('const EDITOR_CARD_CTX'),
    source.indexOf('function renderProgramCardPreview'));
  assert.match(ctxBlock, /heroImageSrc\(lesson\.mediaImageKey, lesson\.lessonId\)/);
  // 회귀 방지: 예전 버그였던, imageKey를 무시하고 lessonId만으로 만드는 하드코딩된 src가 없어야 한다.
  assert.doesNotMatch(ctxBlock,
    /<img src="\/api\/tutor\/academy\/lessons\/\$\{encodeURIComponent\(lesson\.lessonId\)\}\/hero-image" alt="" loading="lazy">/);
});

test('renderProgramCardPreview는 lesson 객체에 mediaImageKey를 함께 넘긴다', () => {
  const fnBlock = source.slice(
    source.indexOf('function renderProgramCardPreview'),
    source.indexOf('function renderProgramCardOt'));
  assert.match(fnBlock, /mediaImageKey: value\.mediaImageKey/);
});

test('세로형 courseMediaArea 미리보기도 같은 heroImageSrc 헬퍼를 쓴다(가로형과 동일 규칙)', () => {
  const fnBlock = source.slice(
    source.indexOf('function renderMediaArea'),
    source.indexOf('async function openMediaPicker'));
  assert.match(fnBlock, /heroImageSrc\(imageKey, active\?\.lessonId/);
});

test('이미지를 교체하면 이전 로컬 미리보기 blob을 해제해 메모리를 남기지 않는다', () => {
  const fnBlock = source.slice(
    source.indexOf('function setMedia(imageKey, videoId)'),
    source.indexOf('function registerLocalImagePreview'));
  assert.match(fnBlock, /revokeLocalImagePreview\(previousImageKey\)/);
});
