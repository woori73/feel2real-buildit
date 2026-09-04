const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { loadSectionsModule } = require('./helpers/browser_script_harness');

// 대표 수업 이미지 crop 회귀 방지.
//   버그: .f2r-program-card-media img가 object-fit:cover + 고정 16:10 aspect-ratio라서
//   세로/정사각형 대표 이미지가 잘려 전체 내용을 확인할 수 없었다.
//   고침: IMAGE 미디어는 data-media-fit="image"로 표시하고, applyImageMediaAspect()가
//   렌더 후 <img>의 실제 naturalWidth/Height를 측정해 --f2r-media-aspect를 채운 뒤
//   object-fit:contain으로 전체를 보여준다(영상 stage와 같은 클램프 로직 재사용).
//   VIDEO 썸네일은 기존 cover 동작을 그대로 유지해야 한다(회귀 금지).

const SECTIONS = loadSectionsModule();
const COLLECTION_CSS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_collection.css'), 'utf8');

test('공개 컨텍스트: IMAGE 대표 미디어는 data-media-fit="image"로 표시된다', () => {
  const ctx = SECTIONS.createPublicContext({ slug: () => 'test-academy' });
  const html = ctx.programCardMedia({ lessonId: 5, mediaType: 'IMAGE' });
  assert.match(html, /class="f2r-program-card-media" data-media-fit="image"/);
  assert.match(html, /\/api\/academies\/test-academy\/lessons\/5\/hero-image/);
});

test('빌더 컨텍스트: IMAGE 대표 미디어도 동일하게 data-media-fit="image"로 표시된다', () => {
  const ctx = SECTIONS.createBuilderContext({});
  const html = ctx.programCardMedia({ lessonId: 7, mediaType: 'IMAGE' });
  assert.match(html, /class="f2r-program-card-media" data-media-fit="image"/);
});

test('VIDEO 대표 미디어는 data-media-fit 마커가 없다(기존 cover 동작 유지)', () => {
  const ctx = SECTIONS.createBuilderContext({ videos: [{ videoId: 9, thumbnailUrl: '/t9' }] });
  const html = ctx.programCardMedia({ mediaType: 'VIDEO', mediaVideoId: 9 });
  assert.doesNotMatch(html, /data-media-fit="image"/);
});

test('applyImageMediaAspect: 이미지가 이미 로드돼 있으면 즉시 측정해 --f2r-media-aspect를 채운다', () => {
  const img = { complete: true, naturalWidth: 1600, naturalHeight: 900, addEventListener() { throw new Error('로드 완료 상태에서는 리스너를 달면 안 된다'); } };
  const styleVars = {};
  const box = {
    style: { setProperty(key, value) { styleVars[key] = value; } },
    setAttribute(key, value) { this[key] = value; },
    querySelector: () => img
  };
  const root = { querySelectorAll: () => [box] };
  SECTIONS.applyImageMediaAspect(root);
  assert.match(styleVars['--f2r-media-aspect'], /^\d+ \/ 1000$/);
  assert.equal(box['data-media-fit-done'], '1');
});

test('applyImageMediaAspect: 아직 로드 전이면 load 시점까지 기다렸다가 채운다', () => {
  let loadHandler = null;
  const img = {
    complete: false,
    naturalWidth: 0,
    naturalHeight: 0,
    addEventListener(type, cb) { if (type === 'load') loadHandler = cb; }
  };
  const styleVars = {};
  const box = {
    style: { setProperty(key, value) { styleVars[key] = value; } },
    setAttribute(key, value) { this[key] = value; },
    querySelector: () => img
  };
  SECTIONS.applyImageMediaAspect({ querySelectorAll: () => [box] });
  assert.equal(styleVars['--f2r-media-aspect'], undefined, '로드 전에는 아직 채우면 안 된다');
  assert.ok(typeof loadHandler === 'function', 'load 리스너가 등록돼야 한다');
  img.naturalWidth = 1080;
  img.naturalHeight = 1920;
  loadHandler();
  assert.match(styleVars['--f2r-media-aspect'], /^\d+ \/ 1000$/);
});

test('CSS: data-media-fit="image" 컨테이너의 img만 contain, 그 외(VIDEO 썸네일 등)는 여전히 cover', () => {
  assert.match(COLLECTION_CSS, /\.f2r-program-card-media img\s*\{[^}]*object-fit:\s*cover;?[^}]*\}/);
  assert.match(COLLECTION_CSS, /\.f2r-program-card-media\[data-media-fit="image"\] img\s*\{[^}]*object-fit:\s*contain;?[^}]*\}/);
});
