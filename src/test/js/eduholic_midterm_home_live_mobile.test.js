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
