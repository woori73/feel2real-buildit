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
  assert.deepEqual(set.options, { maxOffMean: 150 }, '최종 기준: 기본 밝기값 + 꺼도 밝은 반사면 제외');
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

test('lensDensity: 밝기 차이가 크고 반복해서 나오고 작은 점일수록 D가 높다', () => {
  // given — 같은 화면(640×360)에서 세 후보
  const area = 640 * 360;
  const lens = { diff: 120, hits: 3, area: 4 };      // 작고 또렷하게 3번 모두 나온 점
  const flicker = { diff: 120, hits: 2, area: 4 };   // 3번 중 2번만 나온 점
  const shiny = { diff: 120, hits: 3, area: 300 };   // 넓게 번쩍이는 면(반짝이 장식)
  // when
  const dLens = L.lensDensity(lens, 3, area);
  const dFlicker = L.lensDensity(flicker, 3, area);
  const dShiny = L.lensDensity(shiny, 3, area);
  // then
  assert.ok(dLens > dFlicker, '반복해서 나올수록 높다(dΨ/dt)');
  assert.ok(dLens > dShiny, '작은 점일수록 높다(V가 작음)');
});

test('allocateFine: D가 높은 후보부터 정밀 확인 예산만큼만 고르고 나머지는 따로 둔다', () => {
  // given
  const area = 640 * 360;
  const cands = [
    { id: 'a', diff: 40, hits: 2, area: 50 },
    { id: 'b', diff: 150, hits: 3, area: 3 },
    { id: 'c', diff: 90, hits: 3, area: 6 },
  ];
  // when
  const r = L.allocateFine(cands, 3, area, 2);
  // then
  assert.deepEqual(r.picked.map((c) => c.id), ['b', 'c']);
  assert.deepEqual(r.rest.map((c) => c.id), ['a']);
  assert.ok(r.picked[0].d >= r.picked[1].d);
});

test('roiRect: 1단계 후보 자리를 원본 해상도 좌표로 옮기고 화면 밖으로 나가지 않게 자른다', () => {
  // given — 1단계 640×360, 원본 1920×1080(배율 3), 반폭 96
  // when
  const mid = L.roiRect({ cx: 320, cy: 180 }, 640, 360, 1920, 1080, 96);
  const corner = L.roiRect({ cx: 2, cy: 2 }, 640, 360, 1920, 1080, 96);
  // then
  assert.deepEqual(mid, { x: 960 - 96, y: 540 - 96, w: 192, h: 192, s: 3 });
  assert.equal(corner.x, 0);
  assert.equal(corner.y, 0);
  assert.equal(corner.w, 192);
});

test('fineOptions: 잘라 본 칸이 작아도 반사면 크기 상한은 1단계와 같은 실제 크기로 맞춘다', () => {
  // given — 1단계 화면 넓이 230,400, 상한 0.2% = 460.8픽셀, 배율 3 → 원본 4,147픽셀
  const roi = { x: 0, y: 0, w: 192, h: 192, s: 3 };
  // when
  const o = L.fineOptions({ onMin: 170, diffMin: 40 }, roi, 640 * 360);
  // then
  assert.equal(o.onMin, 170, '2단계 기준값은 그대로 둔다');
  assert.ok(Math.abs(o.maxAreaRatio * 192 * 192 - 640 * 360 * 0.002 * 9) < 1e-6);
});

test('rangeSettings: 1단계(전체)는 놓치지 않게 2단계(정밀)보다 기준을 낮춘다', () => {
  // given
  // when
  const near = L.rangeSettings('near', {});
  const far = L.rangeSettings('far', {});
  // then
  for (const set of [near, far]) {
    const fine = Object.assign({}, L.DEFAULTS, set.options);
    assert.ok(set.coarse.onMin < fine.onMin);
    assert.ok(set.coarse.diffMin < fine.diffMin);
  }
});

test('verdict: 정밀 확인에서 다시 안 보인 약한 후보는 판정에 넣지 않고 알려만 준다', () => {
  // given
  // when
  const none = L.verdict([], 2);
  const review = L.verdict([{}], 1);
  // then
  assert.equal(none.level, 'NONE');
  assert.match(none.detail, /약한 후보 2곳/);
  assert.equal(review.level, 'REVIEW');
  assert.match(review.detail, /약한 후보 1곳/);
});

/** w×h 프레임을 k배로 줄인다(칸 평균) — 휴대폰 원본을 1단계 분석 폭으로 줄이는 것과 같다. */
function downscale(f, k) {
  const w = f.width / k, h = f.height / k, o = frame(w, h, 0);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let s = 0;
    for (let j = 0; j < k; j++) for (let i = 0; i < k; i++) s += f.data[((y * k + j) * f.width + x * k + i) * 4];
    const p = (y * w + x) * 4; o.data[p] = o.data[p + 1] = o.data[p + 2] = s / (k * k);
  }
  return o;
}

/** 원본에서 사각형 r만 잘라 온다(2단계 grabRoi와 같다). */
function cropFrame(f, r) {
  const o = frame(r.w, r.h, 0);
  for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) {
    const a = ((r.y + y) * f.width + r.x + x) * 4, b = (y * r.w + x) * 4;
    o.data[b] = f.data[a]; o.data[b + 1] = f.data[a + 1]; o.data[b + 2] = f.data[a + 2];
  }
  return o;
}

test('다이내믹 덴서티 2단계: 멀리 있어 1단계에서 흐려진 작은 렌즈를 놓치지 않고 원본 해상도로 확인한다', () => {
  // given — 원본 1920×1080에 2×2픽셀 렌즈 반사(멀리 있는 렌즈). 1단계는 3배 줄인 640×360
  const W = 1920, H = 1080;
  const off = frame(W, H, 60);
  const on = paint(frame(W, H, 70), 1000, 500, 2, 255);
  const set = L.rangeSettings('near', {});
  const onC = downscale(on, 3), offC = downscale(off, 3);
  // when
  const singlePass = L.findCandidates(onC, offC, set.options);          // 예전 방식: 한 번에 최종 기준
  const coarse = L.findCandidates(onC, offC, set.coarse);               // 1단계: 낮춘 기준
  const alloc = L.allocateFine(L.consistentCandidates([coarse, coarse, coarse], set.radius, 2), 3, 640 * 360);
  const r = L.roiRect(alloc.picked[0], 640, 360, W, H, 96);
  const fine = L.findCandidates(cropFrame(on, r), cropFrame(off, r), L.fineOptions(set.options, r, 640 * 360)); // 2단계: 최종 기준
  // then
  assert.equal(singlePass.length, 0, '줄인 화면에서 최종 기준만 쓰면 흐려진 렌즈를 놓친다');
  assert.equal(alloc.picked.length, 1);
  assert.equal(fine.length, 1, '원본 해상도에서는 최종 기준으로도 렌즈가 보인다');
  assert.ok(Math.abs((r.x + fine[0].cx) - 1000.5) < 1 && Math.abs((r.y + fine[0].cy) - 500.5) < 1, '렌즈 자리를 원본 좌표로 정확히 되돌린다');
});


test('findCandidates: 플래시를 껐을 때 밝기(offMean)를 재고, 최종 기준에서 꺼도 밝은 점(반사면)은 뺀다', () => {
  // given — 왼쪽: 꺼지면 어두운 렌즈 점, 오른쪽: 꺼도 밝은 플라스틱 반짝임
  const off = paint(frame(320, 160, 20), 200, 60, 3, 180);
  const on = paint(paint(frame(320, 160, 20), 60, 60, 3, 250), 200, 60, 3, 250);
  // when
  const all = L.findCandidates(on, off, { diffMin: 40 });
  const strict = L.findCandidates(on, off, { diffMin: 40, maxOffMean: 150 });
  // then
  assert.equal(all.length, 2);
  const lens = all.find((c) => c.cx < 160);
  assert.ok(lens.offMean < 30);
  assert.equal(strict.length, 1, '꺼도 밝은 점은 최종 기준에서 빠진다');
  assert.ok(strict[0].cx < 160);
});

test('lensDensity: 현장 시험 재현 — 꺼지면 어두운 휴대폰 렌즈가 케이블·철망 반짝임보다 순위가 높다', () => {
  // given — 2026-11-21 원장님 시험: 빨간 원 3곳이 케이블·철망 필통·펜, 실제 렌즈는 노란 점선
  const area = 640 * 360;
  const lens = { id: 'phone-lens', diff: 90, offMean: 30, area: 6, aspect: 1.2, fill: 0.8, hits: 3, members: 1 };
  const cable = { id: 'cable', diff: 120, offMean: 170, area: 8, aspect: 2.6, fill: 0.5, hits: 3, members: 1 };
  const mesh = { id: 'mesh-pen', diff: 130, offMean: 110, area: 5, aspect: 1.3, fill: 0.7, hits: 3, members: 4 };
  // when
  const r = L.allocateFine([cable, mesh, lens], 3, area, 1);
  // then
  assert.equal(r.picked[0].id, 'phone-lens');
  assert.ok(L.lensDensity(lens, 3, area) > 2 * L.lensDensity(cable, 3, area));
});

test('markEyePairs: 가로로 나란한 비슷한 두 점은 눈·안경 짝, 가까이 붙은 휴대폰 렌즈 두 개는 아니다', () => {
  // given — 화면 폭 640: 눈 두 개(60px 떨어짐), 휴대폰 렌즈 두 개(8px 떨어짐)
  const cands = [
    { id: 'eyeL', cx: 300, cy: 120, diff: 80 }, { id: 'eyeR', cx: 360, cy: 124, diff: 95 },
    { id: 'camA', cx: 100, cy: 300, diff: 90 }, { id: 'camB', cx: 108, cy: 301, diff: 85 },
  ];
  // when
  const r = L.markEyePairs(cands, 640);
  // then
  assert.deepEqual(r.eyes.map((c) => c.id).sort(), ['eyeL', 'eyeR']);
  assert.deepEqual(r.others.map((c) => c.id).sort(), ['camA', 'camB']);
});

test('rankAll·nearestRanked: 빨간 원과 노란 점선을 D 순으로 세우고, 누른 자리의 순위를 찾는다', () => {
  // given
  const confirmed = [{ cx: 50, cy: 50, d: 30 }];
  const weak = [{ cx: 200, cy: 100, d: 90 }, { cx: 400, cy: 300, d: 10 }];
  // when
  const ranked = L.rankAll(confirmed, weak);
  const hit = L.nearestRanked(ranked, 205, 96, 20);
  const miss = L.nearestRanked(ranked, 600, 20, 20);
  // then
  assert.deepEqual(ranked.map((c) => [c.rank, c.level]), [[1, 'WEAK'], [2, 'REVIEW'], [3, 'WEAK']]);
  assert.equal(hit.rank, 1);
  assert.equal(miss, null, '반경 밖이면 후보에 없던 자리');
});

test('화면: 시험 기록(실제 렌즈 순위) 버튼과 목록이 있고, 기록은 저장하지 않는다고 안내한다', () => {
  // given
  const fs = require('node:fs');
  const path = require('node:path');
  const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/easy/exam_security_lens.html'), 'utf8');
  // when & then
  assert.match(html, /id="lensMark"/);
  assert.match(html, /id="lensCopy"/);
  assert.match(html, /id="lensLog"/);
  assert.match(html, /새로고침하면 사라집니다/);
});
