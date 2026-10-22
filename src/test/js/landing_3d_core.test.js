const test = require('node:test');
const assert = require('node:assert');
const core = require('../../main/resources/static/js/landing_3d_core.js');

const rect = { left: 100, top: 200, width: 400, height: 200 };

test('tiltFromPointer: 카드 중앙이면 기울기 0, 반사광은 50%', () => {
  // given / when
  const t = core.tiltFromPointer(rect, 300, 300, 10);
  // then
  assert.strictEqual(t.rx, -0);
  assert.strictEqual(t.ry, 0);
  assert.strictEqual(t.gx, 50);
  assert.strictEqual(t.gy, 50);
});

test('tiltFromPointer: 오른쪽 아래로 갈수록 ry는 +, rx는 − 이고 최대 각도를 넘지 않는다', () => {
  // given / when
  const edge = core.tiltFromPointer(rect, 500, 400, 10);
  const outside = core.tiltFromPointer(rect, 9999, 9999, 10);
  // then
  assert.strictEqual(edge.ry, 10);
  assert.strictEqual(edge.rx, -10);
  assert.strictEqual(outside.ry, 10);
  assert.strictEqual(outside.gx, 100);
});

test('tiltFromPointer: 크기가 0인 요소도 NaN 없이 계산한다', () => {
  // given / when
  const t = core.tiltFromPointer({ left: 0, top: 0, width: 0, height: 0 }, 5, 5, 8);
  // then
  assert.ok(Number.isFinite(t.rx) && Number.isFinite(t.ry));
});

test('decideRenderMode: 움직임 감소·저사양·데이터 절약·WebGL 미지원이면 static, 아니면 webgl', () => {
  // given
  const ok = { reduce: false, lowEnd: false, saveData: false, webgl: true };
  // when / then
  assert.strictEqual(core.decideRenderMode(ok), 'webgl');
  assert.strictEqual(core.decideRenderMode({ ...ok, reduce: true }), 'static');
  assert.strictEqual(core.decideRenderMode({ ...ok, lowEnd: true }), 'static');
  assert.strictEqual(core.decideRenderMode({ ...ok, saveData: true }), 'static');
  assert.strictEqual(core.decideRenderMode({ ...ok, webgl: false }), 'static');
  assert.strictEqual(core.decideRenderMode(null), 'static');
});

test('frameInterval·dprFor: 터치 기기는 30fps·배율 1.25 상한, 데스크톱은 60fps·1.75 상한', () => {
  // given / when / then
  assert.strictEqual(core.frameInterval(true), 33);
  assert.strictEqual(core.frameInterval(false), 16);
  assert.strictEqual(core.dprFor(true, 3), 1.25);
  assert.strictEqual(core.dprFor(false, 3), 1.75);
  assert.strictEqual(core.dprFor(false, 1), 1);
  assert.strictEqual(core.dprFor(false, undefined), 1);
});

test('scrollProgress: 화면 아래에서 등장하면 0, 위로 완전히 지나가면 1', () => {
  // given / when / then
  assert.strictEqual(core.scrollProgress(800, 400, 800), 0);
  assert.strictEqual(core.scrollProgress(-400, 400, 800), 1);
  assert.ok(Math.abs(core.scrollProgress(200, 400, 800) - 0.5) < 1e-9);
  assert.strictEqual(core.scrollProgress(0, 0, 0), 0);
});

test('crystal: 세분화 단계에 맞는 삼각형 수, 정규 벡터, 바깥쪽을 향한 면', () => {
  // given / when
  const g0 = core.crystal(0, 0);
  const g1 = core.crystal(1, 0.05);
  // then: 이십면체 20면, 1단계 세분화 80면 (면당 정점 3개)
  assert.strictEqual(g0.count, 20 * 3);
  assert.strictEqual(g1.count, 80 * 3);
  assert.strictEqual(g1.positions.length, g1.count * 3);
  assert.strictEqual(g1.normals.length, g1.count * 3);
  for (let i = 0; i < g1.count; i++) {
    const nx = g1.normals[i * 3], ny = g1.normals[i * 3 + 1], nz = g1.normals[i * 3 + 2];
    assert.ok(Math.abs(Math.hypot(nx, ny, nz) - 1) < 1e-5, '법선 정규화');
    const px = g1.positions[i * 3], py = g1.positions[i * 3 + 1], pz = g1.positions[i * 3 + 2];
    assert.ok(nx * px + ny * py + nz * pz > 0, '법선은 바깥쪽');
  }
});

test('crystal: 같은 입력이면 항상 같은 결과(결정형)이고 jitter가 0이면 모든 정점이 단위 구 위에 있다', () => {
  // given / when
  const a = core.crystal(1, 0.08);
  const b = core.crystal(1, 0.08);
  const flat = core.crystal(1, 0);
  // then
  assert.deepStrictEqual(Array.from(a.positions), Array.from(b.positions));
  for (let i = 0; i < flat.count; i++) {
    const r = Math.hypot(flat.positions[i * 3], flat.positions[i * 3 + 1], flat.positions[i * 3 + 2]);
    assert.ok(Math.abs(r - 1) < 1e-5);
  }
});

test('ringPoints: 닫힌 원이고 모든 점이 반지름 위에 있다', () => {
  // given / when
  const r = core.ringPoints(64, 2);
  // then
  assert.strictEqual(r.length, 65 * 3);
  assert.ok(Math.abs(r[0] - r[64 * 3]) < 1e-5 && Math.abs(r[1] - r[64 * 3 + 1]) < 1e-5);
  for (let i = 0; i <= 64; i++) assert.ok(Math.abs(Math.hypot(r[i * 3], r[i * 3 + 1]) - 2) < 1e-5);
});

test('행렬: 단위 행렬과 곱해도 변하지 않고, 회전 행렬은 길이를 보존한다', () => {
  // given
  const rot = core.rotationXYZ(0.4, 1.1, -0.7);
  // when
  const same = core.multiply(core.identity(), rot);
  const v = [1, 2, 3];
  const out = [0, 1, 2].map((r) => rot[r] * v[0] + rot[4 + r] * v[1] + rot[8 + r] * v[2]);
  // then
  rot.forEach((x, i) => assert.ok(Math.abs(x - same[i]) < 1e-12));
  assert.ok(Math.abs(Math.hypot(...out) - Math.hypot(...v)) < 1e-9);
});

test('perspective·translation: 시야각 90도에서 가까운 점의 w는 거리와 같다', () => {
  // given
  const p = core.perspective(Math.PI / 2, 1, 0.1, 100);
  const t = core.translation(0, 0, -5);
  // when
  const m = core.multiply(p, t);
  const w = m[3] * 0 + m[7] * 0 + m[11] * 0 + m[15];
  // then
  assert.ok(Math.abs(p[0] - 1) < 1e-9 && Math.abs(p[5] - 1) < 1e-9);
  assert.ok(Math.abs(w - 5) < 1e-9);
});
