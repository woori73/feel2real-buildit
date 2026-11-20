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

test('메뉴: 원장 휴대폰 앱 첫 화면(/easy/app)에 렌즈 찾기 타일이 있다', () => {
  // given
  const fs = require('node:fs');
  const path = require('node:path');
  const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/easy/app.html'), 'utf8');
  // when
  const tile = html.match(/<a class="tile" href="\/easy\/exam-security\/lens">[\s\S]*?<\/a>/);
  // then
  assert.ok(tile, '렌즈 찾기 타일이 없다');
  assert.match(tile[0], /렌즈 찾기/);
});

/** 가짜 카메라 스트림. torch가 true면 플래시를 조절할 수 있다. */
function fakeStream(deviceId, torch) {
  const track = {
    stopped: false,
    getCapabilities: () => (torch ? { torch: true } : {}),
    getSettings: () => ({ deviceId }),
    stop() { this.stopped = true; },
  };
  return { track, getVideoTracks: () => [track], getTracks: () => [track] };
}

/** 가짜 navigator.mediaDevices. cameras: [{deviceId, label, torch}] , 기본(facingMode) 카메라는 첫 번째. */
function fakeMedia(cameras) {
  const opened = [];
  return {
    opened,
    async getUserMedia(c) {
      const id = c.video.deviceId ? c.video.deviceId.exact : cameras[0].deviceId;
      const cam = cameras.find((x) => x.deviceId === id);
      const s = fakeStream(cam.deviceId, cam.torch);
      opened.push(s);
      return s;
    },
    async enumerateDevices() {
      return cameras.map((x) => ({ kind: 'videoinput', deviceId: x.deviceId, label: x.label }));
    },
  };
}

test('orderCameras: 지금 카메라는 빼고 이름에 뒷면(back·후면)이 있는 카메라를 앞에 둔다', () => {
  // given
  const devices = [
    { kind: 'videoinput', deviceId: 'a', label: 'camera2 0, facing back' },
    { kind: 'videoinput', deviceId: 'b', label: 'camera2 1, facing front' },
    { kind: 'audioinput', deviceId: 'm', label: 'mic' },
    { kind: 'videoinput', deviceId: 'c', label: '후면 카메라 2' },
  ];
  // when
  const order = L.orderCameras(devices, 'a').map((d) => d.deviceId);
  // then
  assert.deepEqual(order, ['c', 'b']);
});

test('openTorchCamera: 기본 뒷면 카메라에 플래시가 없으면(초광각 등) 플래시가 있는 다른 뒷면 카메라를 찾아 연다', async () => {
  // given
  const media = fakeMedia([
    { deviceId: 'wide', label: 'camera2 2, facing back', torch: false },
    { deviceId: 'front', label: 'camera2 1, facing front', torch: false },
    { deviceId: 'main', label: 'camera2 0, facing back', torch: true },
  ]);
  // when
  const r = await L.openTorchCamera(media, { wait: async () => {} });
  // then
  assert.equal(r.torch, true);
  assert.equal(r.stream.track.getSettings().deviceId, 'main');
  assert.equal(media.opened[0].track.stopped, true, '처음 연 카메라는 닫는다');
});

test('openTorchCamera: 기본 카메라가 플래시를 지원하면 다른 카메라를 열지 않는다', async () => {
  // given
  const media = fakeMedia([{ deviceId: 'main', label: 'back', torch: true }, { deviceId: 'other', label: 'back 2', torch: true }]);
  // when
  const r = await L.openTorchCamera(media, { wait: async () => {} });
  // then
  assert.equal(r.torch, true);
  assert.equal(media.opened.length, 1);
});

test('openTorchCamera: 어느 카메라도 플래시를 지원하지 않으면 미리보기용 뒷면 카메라와 torch=false를 돌려준다', async () => {
  // given
  const media = fakeMedia([{ deviceId: 'main', label: 'back', torch: false }, { deviceId: 'front', label: 'front', torch: false }]);
  // when
  const r = await L.openTorchCamera(media, { wait: async () => {} });
  // then
  assert.equal(r.torch, false);
  assert.equal(r.stream.track.stopped, false);
  assert.equal(r.stream.track.getSettings().deviceId, 'main');
});

test('mergeNearby: 렌즈 하나의 반사가 가까운 점 여러 개로 쪼개져도 한 곳으로 센다', () => {
  // given — 같은 렌즈 주변 점 3개(서로 10픽셀 안)와 멀리 떨어진 점 1개
  const cands = [
    { cx: 100, cy: 50, w: 2, h: 2, score: 5, hits: 2 },
    { cx: 108, cy: 54, w: 2, h: 2, score: 9, hits: 3 },
    { cx: 103, cy: 60, w: 2, h: 2, score: 4, hits: 2 },
    { cx: 250, cy: 120, w: 2, h: 2, score: 7, hits: 2 },
  ];
  // when
  const merged = L.mergeNearby(cands, 16);
  // then
  assert.equal(merged.length, 2);
  assert.equal(merged[0].cx, 108, '가장 많이 잡힌 점을 대표로 남긴다');
  assert.equal(merged[0].members, 3);
  assert.equal(merged[1].cx, 250);
});

test('mergeNearby: 반경보다 멀리 떨어진 렌즈 두 개는 따로 센다', () => {
  // given
  const cands = [{ cx: 10, cy: 10, w: 1, h: 1, score: 1, hits: 2 }, { cx: 40, cy: 10, w: 1, h: 1, score: 1, hits: 2 }];
  // when
  const merged = L.mergeNearby(cands, 16);
  // then
  assert.equal(merged.length, 2);
});

test('fitRect: 세로로 긴 프레임을 가로로 넓은 칸에 넣으면 좌우에 여백을 두고 잘리지 않게 줄인다', () => {
  // given — 180×320 세로 프레임, 640×360 화면 칸
  // when
  const f = L.fitRect(180, 320, 640, 360);
  // then
  assert.equal(f.scale, 360 / 320);
  assert.equal(f.oy, 0);
  assert.equal(f.ox, (640 - 180 * (360 / 320)) / 2);
  // 프레임 오른쪽 아래 끝이 화면 칸 안에 들어온다
  assert.ok(f.ox + 180 * f.scale <= 640);
  assert.ok(f.oy + 320 * f.scale <= 360);
});

test('화면: 영상과 원 그림은 잘리지 않게(contain) 같은 방식으로 맞춘다', () => {
  // given
  const fs = require('node:fs');
  const path = require('node:path');
  const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/easy/exam_security_lens.html'), 'utf8');
  // when & then
  assert.match(html, /\.stage video, \.stage canvas \{[^}]*object-fit: contain/);
  assert.doesNotMatch(html, /object-fit: cover/);
});

test('rangeSettings: 가까이(입구·순회)는 확대하지 않고 기본 기준값을 쓴다', () => {
  // given
  const caps = { zoom: { min: 1, max: 10 } };
  // when
  const set = L.rangeSettings('near', caps);
  // then
  assert.equal(set.zoom, 1);
  assert.deepEqual(set.options, {});
  assert.equal(set.radius, 20, '640폭 분석이라 320폭 기준 반경 10의 2배');
  assert.equal(set.mergeRadius, 32);
});

test('rangeSettings: 멀리(교실 앞)는 카메라가 되는 만큼 3배까지 확대하고 기준값을 낮춘다', () => {
  // given
  // when
  const big = L.rangeSettings('far', { zoom: { min: 1, max: 10 } });
  const small = L.rangeSettings('far', { zoom: { min: 1, max: 2 } });
  const none = L.rangeSettings('far', {});
  // then
  assert.equal(big.zoom, 3);
  assert.equal(small.zoom, 2, '최대 확대가 3배보다 작으면 최대까지만');
  assert.equal(none.zoom, null, '확대를 지원하지 않으면 확대하지 않는다');
  assert.ok(big.options.diffMin < L.DEFAULTS.diffMin);
  assert.ok(big.options.onMin < L.DEFAULTS.onMin);
});

test('화면: 검사 거리(가까이 1~2m / 멀리 3~5m)를 고를 수 있다', () => {
  // given
  const fs = require('node:fs');
  const path = require('node:path');
  const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/easy/exam_security_lens.html'), 'utf8');
  // when & then
  assert.match(html, /name="lensRange" value="near" checked/);
  assert.match(html, /name="lensRange" value="far"/);
});
