const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { loadRenderHooks } = require('./helpers/academy_render_hooks');

// 버그: 대표 원장 소개 영상을 올리면 그 영상이 대표 수업(미니 강의)/수업상품 영상 선택
// 목록에도 나타났다. academy_videos는 학원당 하나의 공용 풀이라 용도가 저장되지 않았기
// 때문이다. Phase 1/2 고침: 업로드 시 purpose(DIRECTOR|LESSON)를 함께 저장하고, 각 선택
// 목록은 자신의 용도 + 용도 미상(레거시, purpose 없음) 영상만 보여준다(하위 호환).
//
// Phase 3: 그 "용도 미상은 양쪽에 노출" 하위 호환 규칙 자체가 production 분리 실패의
// 원인이었다 — purpose 컬럼 도입 이전 레거시 원장 영상이 미지정 상태로 남아 계속 노출됐다.
// 이제 미지정은 "관리자가 아직 분류하지 않은 미완료 상태"로 취급해 어느 picker/공개 화면
// 에도 자동 노출하지 않는다. 영상 관리 화면에서 명시적으로 DIRECTOR 또는 LESSON으로
// 분류해야만 나타난다(재분류 API: PATCH /api/tutor/academy/videos/{id}/purpose).

const BUILDER_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js'), 'utf8');
const LESSON_EDITOR_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/lesson_product_editor.js'), 'utf8');
const VIDEOS_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/tutor_academy_videos.js'), 'utf8');
const VIDEOS_HTML = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/tutor/tutor_academy_videos.html'), 'utf8');

const VIDEOS = [
  { videoId: 1, originalFilename: '원장님 인터뷰.mp4', purpose: 'DIRECTOR' },
  { videoId: 2, originalFilename: '문법 강의.mp4', purpose: 'LESSON' },
  { videoId: 3, originalFilename: '옛날 업로드.mp4', purpose: null }
];

test('academy_builder.js: videosForPurpose는 DIRECTOR로 명시 분류된 영상만 반환한다(미지정 제외)', () => {
  const hooks = loadRenderHooks('builder');
  hooks.setVideos(VIDEOS);
  const directorList = hooks.videosForPurpose('DIRECTOR').map((v) => v.videoId);
  assert.deepEqual(directorList, [1], '원장 목록에 LESSON(2)이나 미지정(3)이 섞이면 안 된다');
});

test('academy_builder.js: videosForPurpose는 LESSON으로 명시 분류된 영상만 반환한다(미지정 제외)', () => {
  const hooks = loadRenderHooks('builder');
  hooks.setVideos(VIDEOS);
  const lessonList = hooks.videosForPurpose('LESSON').map((v) => v.videoId);
  assert.deepEqual(lessonList, [2], '수업 목록에 DIRECTOR(1)나 미지정(3)이 섞이면 안 된다');
});

test('academy_builder.js: 원장/히어로 영상 목록(renderHomeVideoList)은 DIRECTOR 필터를 거친다', () => {
  assert.match(BUILDER_JS, /const videos = videosForPurpose\('DIRECTOR'\);/);
});

test('academy_builder.js: 미니 강의(대표 수업) 영상 목록은 LESSON 필터를 거친다', () => {
  assert.match(BUILDER_JS, /videosForPurpose\('LESSON'\)\.filter\(\(video\) => video && video\.videoId != null\)/);
});

test('academy_builder.js: videosForPurpose는 taxonomy 단일 소스(academy_sections.js)의 predicate를 그대로 쓴다', () => {
  assert.match(BUILDER_JS,
    /const predicate = purpose === 'DIRECTOR' \? SECTIONS\.isDirectorVideo : SECTIONS\.isLessonVideo;/);
});

test('lesson_product_editor.js: 수업상품 미디어 목록은 LESSON으로 명시 분류된 영상만 쓴다(미지정 제외)', () => {
  assert.match(LESSON_EDITOR_JS, /SECTIONS\.isLessonVideo/);
  assert.doesNotMatch(LESSON_EDITOR_JS, /!video\.purpose \|\| video\.purpose === 'LESSON'/,
    '미지정을 계속 허용하는 옛 하위호환 필터가 남아 있으면 안 된다');
});

test('lesson_product_editor.js: 영상 업로드 시 purpose=LESSON을 함께 보낸다', () => {
  const uploadBlock = LESSON_EDITOR_JS.slice(
    LESSON_EDITOR_JS.indexOf('async function uploadMedia'),
    LESSON_EDITOR_JS.indexOf('function setMediaStatus'));
  assert.match(uploadBlock, /body\.append\('purpose', 'LESSON'\)/);
});

test('tutor_academy_videos.js: 업로드 시 선택한 용도를 FormData에 담아 보낸다(필수, 기본값 없음)', () => {
  assert.match(VIDEOS_JS, /body\.append\('purpose', purpose\)/);
  assert.match(VIDEOS_JS, /function selectedPurpose\(\)\s*\{\s*\n\s*const checked = document\.querySelector\('input\[name="videoPurpose"\]:checked'\);\s*\n\s*return checked \? checked\.value : null;/);
  // 회귀 방지 원본 계약: 여전히 같은 엔드포인트로 단일 same-origin 업로드다.
  assert.match(VIDEOS_JS, /body\.append\('video', file\)/);
  // 값이 없으면 업로드를 진행하지 않는다(필수화)
  assert.match(VIDEOS_JS, /if \(!purpose\) \{/);
});

test('tutor_academy_videos.js: PATCH .../purpose로 기존 영상의 용도를 재지정한다', () => {
  assert.match(VIDEOS_JS, /PATCH/);
  assert.match(VIDEOS_JS, /\/api\/tutor\/academy\/videos\/\$\{videoId\}\/purpose/);
});

test('tutor_academy_videos.js: 화면을 미지정/원장/수업 세 영역으로 완전히 분리해 렌더한다', () => {
  assert.match(VIDEOS_JS, /SECTIONS\.isUnclassifiedVideo/);
  assert.match(VIDEOS_JS, /SECTIONS\.isDirectorVideo/);
  assert.match(VIDEOS_JS, /SECTIONS\.isLessonVideo/);
  // 예전의 "한 목록 + 필터 드롭다운" 방식이 되살아나지 않았는지 확인한다.
  assert.doesNotMatch(VIDEOS_JS, /purposeFilter/, '단일 목록 + 필터 드롭다운 방식으로 되돌아가면 안 된다');
});

test('tutor_academy_videos.html: 업로드 폼에 용도 선택 라디오가 있고 기본 선택값이 없다(필수 클릭 유도)', () => {
  assert.match(VIDEOS_HTML, /name="videoPurpose" value="LESSON"/);
  assert.match(VIDEOS_HTML, /name="videoPurpose" value="DIRECTOR"/);
  assert.doesNotMatch(VIDEOS_HTML, /name="videoPurpose"[^>]*checked/, '기본 선택값이 있으면 필수 선택을 우회할 수 있다');
  // 기존 검증 문구(120초/200MiB 안내)는 그대로 남아 있어야 한다.
  assert.match(VIDEOS_HTML, /120초 이하 · 200MiB 이하/);
});

test('tutor_academy_videos.html: academy_sections.js(taxonomy 단일 소스)를 로드한다', () => {
  assert.match(VIDEOS_HTML, /academy_sections\.js/);
  const sectionsIndex = VIDEOS_HTML.indexOf('academy_sections.js');
  const pageJsIndex = VIDEOS_HTML.indexOf('tutor_academy_videos.js');
  assert.ok(sectionsIndex >= 0 && sectionsIndex < pageJsIndex,
    'academy_sections.js는 tutor_academy_videos.js보다 먼저 로드되어야 한다');
});
