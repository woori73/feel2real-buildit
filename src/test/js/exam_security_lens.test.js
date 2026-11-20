const { test } = require('node:test');
const assert = require('node:assert/strict');
const L = require('../../main/resources/static/js/exam_security_lens.js');

/** 회색 바탕(밝기 v) w×h RGBA 프레임을 만든다. */
function frame(w, h, v) {
  const data = new Uint8ClampedArray(w * h * 4);
  for (let i = 0; i < w * h; i++) { data[i * 4] = v; data[i * 4 + 1] = v; data[i * 4 + 2] = v; data[i * 4 + 3] = 255; }
  return { width: w, height: h, data };
}

/** (x,y)부터 size×size 칸을 밝기 v로 칠한다. */
function paint(f, x, y, size, v) {
  for (let yy = y; yy < y + size; yy++) {
    for (let xx = x; xx < x + size; xx++) {
      const i = (yy * f.width + xx) * 4;
      f.data[i] = v; f.data[i + 1] = v; f.data[i + 2] = v;
    }
  }
  return f;
}

test('luma: 흰색 255, 검정 0', () => {
  // given & when & then
  assert.equal(Math.round(L.luma(255, 255, 255)), 255);
  assert.equal(L.luma(0, 0, 0), 0);
});

test('findCandidates: 플래시를 켰을 때만 빛나는 작은 점은 렌즈 후보로 찾는다', () => {
  // given: 100×100 어두운 장면, 플래시를 켜면 (40,30)에 3×3 밝은 점이 생긴다
  const off = frame(100, 100, 40);
  const on = paint(frame(100, 100, 50), 40, 30, 3, 250);
  // when
  const c = L.findCandidates(on, off);
  // then
  assert.equal(c.length, 1);
  assert.equal(c[0].x, 40);
  assert.equal(c[0].y, 30);
  assert.equal(c[0].area, 9);
});

test('findCandidates: 켜고 끈 차이 없이 늘 밝은 전등은 후보가 아니다', () => {
  // given: 두 프레임 모두 같은 자리가 밝다
  const off = paint(frame(100, 100, 40), 10, 10, 3, 250);
  const on = paint(frame(100, 100, 50), 10, 10, 3, 250);
  // when & then
  assert.deepEqual(L.findCandidates(on, off), []);
});

test('findCandidates: 넓게 번쩍이는 면(안경알·흰 벽)은 렌즈가 아니라 반사면으로 보고 뺀다', () => {
  // given: 플래시에 40×40 넓은 면이 밝아진다(화면의 16%)
  const off = frame(100, 100, 40);
  const on = paint(frame(100, 100, 50), 20, 20, 40, 250);
  // when & then
  assert.deepEqual(L.findCandidates(on, off), []);
});

test('findCandidates: 가늘고 긴 반사선(형광등 줄)은 뺀다', () => {
  // given: 1×12 선
  const off = frame(200, 200, 40);
  const on = frame(200, 200, 50);
  for (let y = 50; y < 62; y++) paint(on, 70, y, 1, 250);
  // when & then
  assert.deepEqual(L.findCandidates(on, off), []);
});

test('findCandidates: 크기가 다른 두 프레임은 오류로 알린다', () => {
  // given & when & then
  assert.throws(() => L.findCandidates(frame(10, 10, 0), frame(20, 10, 0)), /크기/);
});

test('consistentCandidates: 여러 번 깜빡여 같은 자리에서 2번 이상 나온 점만 남긴다', () => {
  // given: 1회차 (10,10)·(80,80), 2회차 (11,10), 3회차 (50,50)
  const rounds = [
    [{ cx: 10, cy: 10, score: 5 }, { cx: 80, cy: 80, score: 9 }],
    [{ cx: 11, cy: 10, score: 7 }],
    [{ cx: 50, cy: 50, score: 3 }],
  ];
  // when
  const kept = L.consistentCandidates(rounds, 6, 2);
  // then: (10,10) 근처만 2번 나왔다
  assert.equal(kept.length, 1);
  assert.equal(kept[0].hits, 2);
  assert.ok(Math.abs(kept[0].cx - 10.5) < 1e-9);
});

test('verdict: 후보가 없으면 "의심 없음", 있으면 "확인 필요"만 알리고 부정행위라고 판정하지 않는다', () => {
  // given & when
  const none = L.verdict([]);
  const review = L.verdict([{ cx: 1, cy: 1 }, { cx: 2, cy: 2 }]);
  // then
  assert.equal(none.level, 'NONE');
  assert.equal(none.title, '의심 없음');
  assert.equal(review.level, 'REVIEW');
  assert.equal(review.title, '확인 필요');
  assert.match(review.detail, /2곳/);
  assert.match(review.detail, /판정이 아닙니다/);
  assert.doesNotMatch(review.title + review.detail, /적발|부정행위입니다|100%/);
});

test('개인정보: 시연 스크립트는 서버 전송·저장 API를 쓰지 않는다(사진·영상 저장 안 함)', () => {
  // given
  const fs = require('node:fs');
  const path = require('node:path');
  const src = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/exam_security_lens.js'), 'utf8');
  // when & then
  for (const banned of ['fetch(', 'XMLHttpRequest', 'sendBeacon', 'localStorage', 'sessionStorage', 'indexedDB', 'toDataURL', 'toBlob', 'MediaRecorder']) {
    assert.ok(!src.includes(banned), banned + ' 를 쓰면 안 된다');
  }
});

test('화면: 시연 화면은 판정하지 않는다는 안내와 저장하지 않는다는 안내를 보여 준다', () => {
  // given
  const fs = require('node:fs');
  const path = require('node:path');
  const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/easy/exam_security_lens.html'), 'utf8');
  // when & then
  assert.match(html, /부정행위를 판정하지 않습니다/);
  assert.match(html, /저장하거나 서버로 보내지 않습니다/);
  assert.match(html, /<script src="\/js\/exam_security_lens\.js"><\/script>/);
  assert.doesNotMatch(html, /<form|type="file"|100% 적발|100%적발|완벽/);
});
