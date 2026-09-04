const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { loadRenderHooks } = require('./helpers/academy_render_hooks');

// 버그: 대표 원장 소개 영상을 올리면 그 영상이 대표 수업(미니 강의)/수업상품 영상 선택
// 목록에도 나타났다. academy_videos는 학원당 하나의 공용 풀이라 용도가 저장되지 않았기
// 때문이다. 고침: 업로드 시 purpose(DIRECTOR|LESSON)를 함께 저장하고, 각 선택 목록은
// 자신의 용도 + 용도 미상(레거시, purpose 없음) 영상만 보여준다 — 레거시 데이터를
// 추측해 일괄 마이그레이션하지 않는다(하위 호환).

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

test('academy_builder.js: videosForPurpose는 DIRECTOR 영상 + 용도 미상(레거시)만 반환한다', () => {
  const hooks = loadRenderHooks('builder');
  hooks.setVideos(VIDEOS);
  const directorList = hooks.videosForPurpose('DIRECTOR').map((v) => v.videoId);
  assert.deepEqual(directorList, [1, 3], '원장 목록에 LESSON 영상(2)이 섞이면 안 된다');
});

test('academy_builder.js: videosForPurpose는 LESSON 영상 + 용도 미상(레거시)만 반환한다', () => {
  const hooks = loadRenderHooks('builder');
  hooks.setVideos(VIDEOS);
  const lessonList = hooks.videosForPurpose('LESSON').map((v) => v.videoId);
  assert.deepEqual(lessonList, [2, 3], '수업 목록에 DIRECTOR 영상(1)이 섞이면 안 된다');
});

test('academy_builder.js: 원장/히어로 영상 목록(renderHomeVideoList)은 DIRECTOR 필터를 거친다', () => {
  assert.match(BUILDER_JS, /const videos = videosForPurpose\('DIRECTOR'\);/);
});

test('academy_builder.js: 미니 강의(대표 수업) 영상 목록은 LESSON 필터를 거친다', () => {
  assert.match(BUILDER_JS, /videosForPurpose\('LESSON'\)\.filter\(\(video\) => video && video\.videoId != null\)/);
});

test('lesson_product_editor.js: 수업상품 미디어 목록은 DIRECTOR 영상을 제외한다', () => {
  assert.match(LESSON_EDITOR_JS,
    /myVideos = videos\.filter\(\(video\) => !video\.purpose \|\| video\.purpose === 'LESSON'\);/);
});

test('lesson_product_editor.js: 영상 업로드 시 purpose=LESSON을 함께 보낸다', () => {
  const uploadBlock = LESSON_EDITOR_JS.slice(
    LESSON_EDITOR_JS.indexOf('async function uploadMedia'),
    LESSON_EDITOR_JS.indexOf('function setMediaStatus'));
  assert.match(uploadBlock, /body\.append\('purpose', 'LESSON'\)/);
});

test('tutor_academy_videos.js: 업로드 시 선택한 용도를 FormData에 담아 보낸다', () => {
  assert.match(VIDEOS_JS, /body\.append\('purpose', selectedPurpose\(\)\)/);
  assert.match(VIDEOS_JS, /function selectedPurpose\(\)/);
  // 회귀 방지 원본 계약: 여전히 같은 엔드포인트로 단일 same-origin 업로드다.
  assert.match(VIDEOS_JS, /body\.append\('video', file\)/);
});

test('tutor_academy_videos.html: 업로드 폼에 용도 선택(원장 소개/수업 영상) 라디오가 있다', () => {
  assert.match(VIDEOS_HTML, /name="videoPurpose" value="LESSON"/);
  assert.match(VIDEOS_HTML, /name="videoPurpose" value="DIRECTOR"/);
  // 기존 검증 문구(120초/200MiB 안내)는 그대로 남아 있어야 한다.
  assert.match(VIDEOS_HTML, /120초 이하 · 200MiB 이하/);
});
