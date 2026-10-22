/* Feel2Real 홈 3D UI — 순수 계산 모듈(DOM·WebGL 호출 없음). 브라우저에서는 window.F3DCore, Node 테스트에서는 module.exports. */
(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.F3DCore = api;
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  function clamp(v, a, b) { return Math.min(b, Math.max(a, v)); }
  function lerp(a, b, t) { return a + (b - a) * t; }

  /** 포인터 위치 → 카드 기울기(도)와 반사광 위치(%). 카드 밖 좌표는 가장자리로 고정한다. */
  function tiltFromPointer(rect, x, y, maxDeg) {
    var w = rect.width || 1;
    var h = rect.height || 1;
    var nx = clamp(((x - rect.left) / w) * 2 - 1, -1, 1);
    var ny = clamp(((y - rect.top) / h) * 2 - 1, -1, 1);
    return {
      rx: -ny * maxDeg,
      ry: nx * maxDeg,
      gx: (nx + 1) * 50,
      gy: (ny + 1) * 50
    };
  }

  /** 3D 렌더 방식 결정. 움직임 감소·저사양·데이터 절약·WebGL 미지원이면 정적 대체 UI만 쓴다. */
  function decideRenderMode(env) {
    if (!env || env.reduce || env.lowEnd || env.saveData || !env.webgl) return 'static';
    return 'webgl';
  }

  /** 프레임 간격(ms). 터치 기기는 30fps, 데스크톱은 약 60fps. */
  function frameInterval(coarse) { return coarse ? 33 : 16; }

  /** 캔버스 해상도 배율 상한. GPU 부하를 줄이기 위해 모바일은 1.25배까지만 쓴다. */
  function dprFor(coarse, devicePixelRatio) {
    return Math.min(devicePixelRatio || 1, coarse ? 1.25 : 1.75);
  }

  /** 요소가 화면을 지나가는 진행도 0~1 (아래에서 등장 0 → 위로 사라짐 1). */
  function scrollProgress(rectTop, rectHeight, viewH) {
    var span = viewH + rectHeight;
    if (span <= 0) return 0;
    return clamp((viewH - rectTop) / span, 0, 1);
  }

  function hash3(x, y, z) {
    var s = Math.sin(x * 127.1 + y * 311.7 + z * 74.7) * 43758.5453;
    return s - Math.floor(s);
  }

  /**
   * 면 단위로 평평하게 음영을 주는 결정형 보석(이십면체 세분화).
   * 같은 위치의 정점은 같은 반지름 변형을 받아 면이 벌어지지 않는다. 반환: {positions, normals, count}
   */
  function crystal(subdiv, jitter) {
    var t = (1 + Math.sqrt(5)) / 2;
    var base = [[-1, t, 0], [1, t, 0], [-1, -t, 0], [1, -t, 0], [0, -1, t], [0, 1, t], [0, -1, -t], [0, 1, -t],
      [t, 0, -1], [t, 0, 1], [-t, 0, -1], [-t, 0, 1]];
    var faces = [[0, 11, 5], [0, 5, 1], [0, 1, 7], [0, 7, 10], [0, 10, 11], [1, 5, 9], [5, 11, 4], [11, 10, 2], [10, 7, 6],
      [7, 1, 8], [3, 9, 4], [3, 4, 2], [3, 2, 6], [3, 6, 8], [3, 8, 9], [4, 9, 5], [2, 4, 11], [6, 2, 10], [8, 6, 7], [9, 8, 1]];
    function norm(v) {
      var l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
      return [v[0] / l, v[1] / l, v[2] / l];
    }
    function mid(a, b) { return norm([(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2]); }
    var tris = faces.map(function (f) { return [norm(base[f[0]]), norm(base[f[1]]), norm(base[f[2]])]; });
    for (var s = 0; s < (subdiv || 0); s++) {
      var next = [];
      tris.forEach(function (tr) {
        var ab = mid(tr[0], tr[1]);
        var bc = mid(tr[1], tr[2]);
        var ca = mid(tr[2], tr[0]);
        next.push([tr[0], ab, ca], [tr[1], bc, ab], [tr[2], ca, bc], [ab, bc, ca]);
      });
      tris = next;
    }
    var amount = jitter || 0;
    var positions = [];
    var normals = [];
    tris.forEach(function (tr) {
      var pts = tr.map(function (p) {
        var k = 1 + (hash3(p[0], p[1], p[2]) - 0.5) * 2 * amount;
        return [p[0] * k, p[1] * k, p[2] * k];
      });
      var u = [pts[1][0] - pts[0][0], pts[1][1] - pts[0][1], pts[1][2] - pts[0][2]];
      var v = [pts[2][0] - pts[0][0], pts[2][1] - pts[0][1], pts[2][2] - pts[0][2]];
      var n = norm([u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]);
      var c = [(pts[0][0] + pts[1][0] + pts[2][0]) / 3, (pts[0][1] + pts[1][1] + pts[2][1]) / 3, (pts[0][2] + pts[1][2] + pts[2][2]) / 3];
      if (n[0] * c[0] + n[1] * c[1] + n[2] * c[2] < 0) { n = [-n[0], -n[1], -n[2]]; pts = [pts[0], pts[2], pts[1]]; }
      pts.forEach(function (p) { positions.push(p[0], p[1], p[2]); normals.push(n[0], n[1], n[2]); });
    });
    return { positions: new Float32Array(positions), normals: new Float32Array(normals), count: positions.length / 3 };
  }

  /** XY 평면 원의 점 배열(닫힌 선). 궤도 링에 쓴다. */
  function ringPoints(segments, radius) {
    var out = new Float32Array((segments + 1) * 3);
    for (var i = 0; i <= segments; i++) {
      var a = (i / segments) * Math.PI * 2;
      out[i * 3] = Math.cos(a) * radius;
      out[i * 3 + 1] = Math.sin(a) * radius;
      out[i * 3 + 2] = 0;
    }
    return out;
  }

  /** 열 우선(column-major) 4x4 행렬 유틸. */
  function identity() { return [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; }

  function multiply(a, b) {
    var o = new Array(16);
    for (var c = 0; c < 4; c++) {
      for (var r = 0; r < 4; r++) {
        o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
      }
    }
    return o;
  }

  function rotationXYZ(ax, ay, az) {
    var cx = Math.cos(ax), sx = Math.sin(ax), cy = Math.cos(ay), sy = Math.sin(ay), cz = Math.cos(az), sz = Math.sin(az);
    var rx = [1, 0, 0, 0, 0, cx, sx, 0, 0, -sx, cx, 0, 0, 0, 0, 1];
    var ry = [cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1];
    var rz = [cz, sz, 0, 0, -sz, cz, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    return multiply(rz, multiply(ry, rx));
  }

  function perspective(fovy, aspect, near, far) {
    var f = 1 / Math.tan(fovy / 2);
    var nf = 1 / (near - far);
    return [f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) * nf, -1, 0, 0, 2 * far * near * nf, 0];
  }

  function translation(x, y, z) { return [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1]; }

  return {
    clamp: clamp, lerp: lerp, tiltFromPointer: tiltFromPointer, decideRenderMode: decideRenderMode,
    frameInterval: frameInterval, dprFor: dprFor, scrollProgress: scrollProgress, crystal: crystal,
    ringPoints: ringPoints, identity: identity, multiply: multiply, rotationXYZ: rotationXYZ,
    perspective: perspective, translation: translation
  };
});
