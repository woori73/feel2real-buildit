/* Feel2Real 홈 3D UI.
 * - 라이브러리 없음(CSS 3D + 직접 작성한 작은 WebGL). 계산은 landing_3d_core.js(F3DCore)가 맡는다.
 * - 레이어: ① 스크롤 등장 ② 카드 기울기·반사광 ③ 히어로 시차 ④ WebGL 보석(보일 때만, 지원·성능 조건 충족 시).
 * - 어느 단계가 실패해도 기존 화면(정적 대체 UI)은 그대로 쓸 수 있다. 서버·API 호출 없음.
 */
(function () {
  'use strict';
  var C = window.F3DCore;
  var doc = document;
  var root = doc.documentElement;
  if (!C) return;

  var mq = function (q) { return window.matchMedia ? window.matchMedia(q) : { matches: false, addEventListener: function () {} }; };
  var reduceMq = mq('(prefers-reduced-motion: reduce)');
  var coarse = mq('(pointer: coarse)').matches;
  var conn = navigator.connection || {};
  var saveData = !!conn.saveData || /(^|-)(2g|3g)$/.test(conn.effectiveType || '');
  var lowEnd = (navigator.deviceMemory && navigator.deviceMemory <= 2) ||
    (navigator.hardwareConcurrency && navigator.hardwareConcurrency <= 2);

  root.classList.add('f3d-js');
  if (reduceMq.matches) root.classList.add('f3d-reduce');

  /* ① 스크롤 등장 — JS가 없거나 움직임 감소면 처음부터 보인다 */
  function initReveal() {
    var items = doc.querySelectorAll('[data-f3d-reveal]');
    if (!items.length) return;
    if (reduceMq.matches || !('IntersectionObserver' in window)) {
      items.forEach(function (el) { el.classList.add('is-in'); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (!e.isIntersecting) return;
        e.target.classList.add('is-in');
        io.unobserve(e.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });
    items.forEach(function (el, i) {
      el.style.setProperty('--f3d-delay', (i % 4) * 70 + 'ms');
      io.observe(el);
    });
  }

  /* ② 카드 기울기·반사광 — 마우스/펜 전용(터치는 누름 효과만 CSS로) */
  function initTilt() {
    if (reduceMq.matches || coarse) return;
    doc.querySelectorAll('[data-f3d-tilt]').forEach(function (el) {
      var max = parseFloat(el.getAttribute('data-f3d-tilt')) || 7;
      var raf = 0;
      var last = null;
      function apply() {
        raf = 0;
        if (!last) return;
        var t = C.tiltFromPointer(el.getBoundingClientRect(), last.x, last.y, max);
        el.style.setProperty('--f3d-rx', t.rx.toFixed(2) + 'deg');
        el.style.setProperty('--f3d-ry', t.ry.toFixed(2) + 'deg');
        el.style.setProperty('--f3d-gx', t.gx.toFixed(1) + '%');
        el.style.setProperty('--f3d-gy', t.gy.toFixed(1) + '%');
      }
      el.addEventListener('pointermove', function (e) {
        if (e.pointerType === 'touch') return;
        last = { x: e.clientX, y: e.clientY };
        el.classList.add('is-tilting');
        if (!raf) raf = requestAnimationFrame(apply);
      });
      el.addEventListener('pointerleave', function () {
        last = null;
        el.classList.remove('is-tilting');
        el.style.setProperty('--f3d-rx', '0deg');
        el.style.setProperty('--f3d-ry', '0deg');
      });
    });
  }

  /* ③ 히어로 시차 — 보이는 동안만 스크롤 값을 CSS 변수로 전달 */
  function initParallax() {
    var stage = doc.querySelector('[data-f3d-stage]');
    if (!stage || reduceMq.matches) return;
    var visible = true;
    var ticking = false;
    function update() {
      ticking = false;
      var r = stage.getBoundingClientRect();
      var p = C.scrollProgress(r.top, r.height, window.innerHeight);
      stage.style.setProperty('--f3d-scroll', p.toFixed(3));
    }
    function onScroll() {
      if (!visible || ticking) return;
      ticking = true;
      requestAnimationFrame(update);
    }
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(function (es) { visible = es[0].isIntersecting; if (visible) onScroll(); }).observe(stage);
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    update();
  }

  /* ④ WebGL 보석 — 직접 작성한 셰이더. 보이는 탭·화면 안에서만 그린다 */
  var VERT = [
    'attribute vec3 aPos; attribute vec3 aNorm;',
    'uniform mat4 uMvp; uniform mat4 uRot;',
    'varying vec3 vN; varying vec3 vP;',
    'void main(){ vN = mat3(uRot[0].xyz, uRot[1].xyz, uRot[2].xyz) * aNorm; vP = (uRot * vec4(aPos,1.0)).xyz; gl_Position = uMvp * vec4(aPos,1.0); }'
  ].join('\n');

  var FRAG = [
    'precision mediump float;',
    'varying vec3 vN; varying vec3 vP;',
    'uniform vec3 uCam; uniform float uTime; uniform float uBack;',
    'vec3 env(vec3 d){',
    '  d = normalize(d);',
    '  float y = d.y*0.5+0.5;',
    '  vec3 c = mix(vec3(0.02,0.04,0.08), vec3(0.42,0.60,0.84), pow(y,1.6));',
    '  c += vec3(0.95,0.97,1.0) * smoothstep(0.80,0.97, dot(d, normalize(vec3(-0.5,0.8,0.6))));',
    '  c += vec3(0.88,0.74,0.52) * smoothstep(0.86,0.99, dot(d, normalize(vec3(0.75,-0.25,0.65))));',
    '  c += vec3(0.38,0.66,0.88) * smoothstep(0.90,0.99, dot(d, normalize(vec3(0.1,-0.9,0.3))))*0.8;',
    '  return c; }',
    'void main(){',
    '  vec3 N = normalize(vN); if (uBack > 0.5) N = -N;',
    '  vec3 V = normalize(uCam - vP);',
    '  float nv = max(dot(N,V),0.0);',
    '  float fres = pow(1.0 - nv, 3.0);',
    '  vec3 R = reflect(-V,N);',
    '  vec3 refr = vec3(env(refract(-V,N,0.76)).r, env(refract(-V,N,0.78)).g, env(refract(-V,N,0.80)).b);',
    '  vec3 col = mix(refr*0.85, env(R), clamp(fres*1.25+0.12,0.0,1.0));',
    '  col += pow(max(dot(R, normalize(vec3(-0.5,0.8,0.6))),0.0), 70.0) * 1.1;',
    '  col += vec3(0.50,0.64,0.84) * max(dot(N, normalize(vec3(-0.4,0.7,0.6))),0.0) * 0.42 * (uBack > 0.5 ? 0.0 : 1.0);',
    '  col += vec3(0.16,0.40,0.72) * fres * 0.55;',
    '  col += 0.05 * vec3(sin(nv*9.0+uTime), sin(nv*9.0+2.1+uTime), sin(nv*9.0+4.2+uTime));',
    '  float a = uBack > 0.5 ? 0.45 : 0.90;',
    '  gl_FragColor = vec4(col*a, a); }'
  ].join('\n');

  var LINE_VERT = [
    'attribute vec3 aPos; uniform mat4 uMvp;',
    'void main(){ gl_Position = uMvp * vec4(aPos,1.0); }'
  ].join('\n');
  var LINE_FRAG = [
    'precision mediump float; uniform vec4 uColor;',
    'void main(){ gl_FragColor = vec4(uColor.rgb*uColor.a, uColor.a); }'
  ].join('\n');

  var DOT_VERT = [
    'attribute vec3 aPos; attribute float aSeed;',
    'uniform mat4 uMvp; uniform float uTime; uniform float uSize;',
    'varying float vA;',
    'void main(){',
    '  vec3 p = aPos + vec3(sin(uTime*0.4+aSeed*6.2831), cos(uTime*0.33+aSeed*12.566), sin(uTime*0.27+aSeed*9.42))*0.08;',
    '  gl_Position = uMvp * vec4(p,1.0);',
    '  gl_PointSize = uSize * (0.6 + 0.8*fract(aSeed*7.0)) / gl_Position.w;',
    '  vA = 0.35 + 0.65*fract(aSeed*13.0); }'
  ].join('\n');
  var DOT_FRAG = [
    'precision mediump float; varying float vA;',
    'void main(){ float d = length(gl_PointCoord-0.5); float a = smoothstep(0.5,0.0,d)*vA;',
    '  gl_FragColor = vec4(vec3(0.72,0.82,0.95)*a, a); }'
  ].join('\n');

  function compile(gl, type, src) {
    var s = gl.createShader(type);
    gl.shaderSource(s, src);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s) || 'shader');
    return s;
  }
  function program(gl, v, f) {
    var p = gl.createProgram();
    gl.attachShader(p, compile(gl, gl.VERTEX_SHADER, v));
    gl.attachShader(p, compile(gl, gl.FRAGMENT_SHADER, f));
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p) || 'link');
    return p;
  }
  function buffer(gl, data) {
    var b = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, b);
    gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
    return b;
  }

  function createScene(canvas, gl) {
    var gem = C.crystal(1, 0.07);
    var pGem = program(gl, VERT, FRAG);
    var pLine = program(gl, LINE_VERT, LINE_FRAG);
    var pDot = program(gl, DOT_VERT, DOT_FRAG);
    var bPos = buffer(gl, gem.positions);
    var bNorm = buffer(gl, gem.normals);
    var rings = [C.ringPoints(160, 1.5), C.ringPoints(160, 1.78)].map(function (r) { return buffer(gl, r); });
    var dotCount = coarse ? 36 : 72;
    var dots = new Float32Array(dotCount * 4);
    for (var i = 0; i < dotCount; i++) {
      var u = Math.random() * 2 - 1;
      var th = Math.random() * Math.PI * 2;
      var rr = 1.7 + Math.random() * 1.6;
      var s = Math.sqrt(1 - u * u);
      dots[i * 4] = rr * s * Math.cos(th);
      dots[i * 4 + 1] = rr * u * 0.8;
      dots[i * 4 + 2] = rr * s * Math.sin(th);
      dots[i * 4 + 3] = Math.random();
    }
    var bDots = buffer(gl, dots);
    var L = {
      gem: { pos: gl.getAttribLocation(pGem, 'aPos'), norm: gl.getAttribLocation(pGem, 'aNorm'),
        mvp: gl.getUniformLocation(pGem, 'uMvp'), rot: gl.getUniformLocation(pGem, 'uRot'), cam: gl.getUniformLocation(pGem, 'uCam'),
        time: gl.getUniformLocation(pGem, 'uTime'), back: gl.getUniformLocation(pGem, 'uBack') },
      line: { pos: gl.getAttribLocation(pLine, 'aPos'), mvp: gl.getUniformLocation(pLine, 'uMvp'), color: gl.getUniformLocation(pLine, 'uColor') },
      dot: { pos: gl.getAttribLocation(pDot, 'aPos'), seed: gl.getAttribLocation(pDot, 'aSeed'), mvp: gl.getUniformLocation(pDot, 'uMvp'),
        time: gl.getUniformLocation(pDot, 'uTime'), size: gl.getUniformLocation(pDot, 'uSize') }
    };
    var camZ = 4.7;
    var view = C.translation(0, 0, -camZ); /* 넓은 화면에서는 보석을 오른쪽 위로 비켜 배너와 겹치지 않게 한다 */

    function draw(t, rx, ry, w, h, dpr) {
      gl.viewport(0, 0, w, h);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      var aspect = w / h;
      var proj = C.perspective(0.72, aspect, 0.1, 50);
      var vp = C.multiply(proj, C.multiply(C.translation(aspect > 1.3 ? 0.3 : 0.05, 0.18, 0), view));
      var rot = C.rotationXYZ(rx, ry, 0.12);

      /* 보석: 뒷면(굴절) → 앞면(반사) 순서 */
      gl.useProgram(pGem);
      gl.bindBuffer(gl.ARRAY_BUFFER, bPos);
      gl.enableVertexAttribArray(L.gem.pos);
      gl.vertexAttribPointer(L.gem.pos, 3, gl.FLOAT, false, 0, 0);
      gl.bindBuffer(gl.ARRAY_BUFFER, bNorm);
      gl.enableVertexAttribArray(L.gem.norm);
      gl.vertexAttribPointer(L.gem.norm, 3, gl.FLOAT, false, 0, 0);
      gl.uniformMatrix4fv(L.gem.mvp, false, new Float32Array(C.multiply(vp, rot)));
      gl.uniformMatrix4fv(L.gem.rot, false, new Float32Array(rot));
      gl.uniform3f(L.gem.cam, 0, 0, camZ);
      gl.uniform1f(L.gem.time, t);
      gl.enable(gl.CULL_FACE);
      gl.cullFace(gl.FRONT);
      gl.uniform1f(L.gem.back, 1);
      gl.drawArrays(gl.TRIANGLES, 0, gem.count);
      gl.cullFace(gl.BACK);
      gl.uniform1f(L.gem.back, 0);
      gl.drawArrays(gl.TRIANGLES, 0, gem.count);
      gl.disable(gl.CULL_FACE);

      /* 궤도 링 */
      gl.useProgram(pLine);
      gl.blendFunc(gl.ONE, gl.ONE);
      gl.enableVertexAttribArray(L.line.pos);
      rings.forEach(function (rb, i) {
        gl.bindBuffer(gl.ARRAY_BUFFER, rb);
        gl.vertexAttribPointer(L.line.pos, 3, gl.FLOAT, false, 0, 0);
        var m = C.multiply(vp, C.rotationXYZ(1.1 + i * 0.5 + rx * 0.3, t * (0.12 + i * 0.05), i * 0.9));
        gl.uniformMatrix4fv(L.line.mvp, false, new Float32Array(m));
        gl.uniform4f(L.line.color, 0.55, 0.72, 0.92, i ? 0.32 : 0.5);
        gl.drawArrays(gl.LINE_STRIP, 0, 161);
      });

      /* 떠다니는 입자 */
      gl.useProgram(pDot);
      gl.bindBuffer(gl.ARRAY_BUFFER, bDots);
      gl.enableVertexAttribArray(L.dot.pos);
      gl.vertexAttribPointer(L.dot.pos, 3, gl.FLOAT, false, 16, 0);
      gl.enableVertexAttribArray(L.dot.seed);
      gl.vertexAttribPointer(L.dot.seed, 1, gl.FLOAT, false, 16, 12);
      gl.uniformMatrix4fv(L.dot.mvp, false, new Float32Array(C.multiply(vp, C.rotationXYZ(rx * 0.4, t * 0.05, 0))));
      gl.uniform1f(L.dot.time, t);
      gl.uniform1f(L.dot.size, 26 * dpr);
      gl.drawArrays(gl.POINTS, 0, dotCount);
    }
    return { draw: draw };
  }

  function initCore() {
    var box = doc.getElementById('f3dCore');
    if (!box) return;
    var canvas = box.querySelector('canvas');
    if (!canvas) return;

    function webglAvailable() {
      try {
        var t = doc.createElement('canvas');
        return !!(window.WebGLRenderingContext && (t.getContext('webgl') || t.getContext('experimental-webgl')));
      } catch (e) { return false; }
    }
    var mode = C.decideRenderMode({ reduce: reduceMq.matches, lowEnd: lowEnd, saveData: saveData, webgl: webglAvailable() });
    box.setAttribute('data-f3d-mode', mode);
    if (mode !== 'webgl') return; /* 정적 대체 UI(SVG)가 그대로 보인다 */

    var gl = null;
    var scene = null;
    var running = false;
    var inView = false;
    var raf = 0;
    var lastFrame = 0;
    var t0 = performance.now();
    var tx = 0, ty = 0, cx = 0, cy = 0, scrollRot = 0;
    var size = { w: 0, h: 0, dpr: 1 };
    var interval = C.frameInterval(coarse);

    function resize() {
      var r = canvas.getBoundingClientRect();
      var dpr = C.dprFor(coarse, window.devicePixelRatio);
      var w = Math.max(2, Math.round(r.width * dpr));
      var h = Math.max(2, Math.round(r.height * dpr));
      if (w !== size.w || h !== size.h) { canvas.width = w; canvas.height = h; }
      size = { w: w, h: h, dpr: dpr };
    }

    function start() {
      if (!gl) {
        try {
          gl = canvas.getContext('webgl', { alpha: true, antialias: true, premultipliedAlpha: true, powerPreference: 'low-power' });
          if (!gl) throw new Error('no webgl');
          scene = createScene(canvas, gl);
        } catch (e) {
          box.setAttribute('data-f3d-mode', 'static');
          gl = null;
          return;
        }
        canvas.addEventListener('webglcontextlost', function (ev) {
          ev.preventDefault();
          stop();
          box.removeAttribute('data-f3d-ready');
          box.setAttribute('data-f3d-mode', 'static');
        });
        canvas.addEventListener('webglcontextrestored', function () {
          gl = null;
          scene = null;
          box.setAttribute('data-f3d-mode', 'webgl');
          if (inView) start();
        });
      }
      if (running) return;
      running = true;
      resize();
      raf = requestAnimationFrame(loop);
    }
    function stop() { running = false; if (raf) cancelAnimationFrame(raf); raf = 0; }

    function loop(now) {
      if (!running) return;
      raf = requestAnimationFrame(loop);
      if (now - lastFrame < interval) return;
      lastFrame = now;
      var t = (now - t0) / 1000;
      cx = C.lerp(cx, tx, 0.06);
      cy = C.lerp(cy, ty, 0.06);
      scene.draw(t, 0.35 + cy * 0.5 + scrollRot * 0.4, t * 0.28 + cx * 0.9 + scrollRot, size.w, size.h, size.dpr);
      if (!box.hasAttribute('data-f3d-ready')) box.setAttribute('data-f3d-ready', '');
    }

    /* 포인터·터치 — 화면 중심 기준 -1~1 */
    function aim(x, y) {
      tx = C.clamp((x / window.innerWidth) * 2 - 1, -1, 1);
      ty = C.clamp((y / window.innerHeight) * 2 - 1, -1, 1);
    }
    window.addEventListener('pointermove', function (e) { aim(e.clientX, e.clientY); }, { passive: true });
    window.addEventListener('scroll', function () { scrollRot = window.scrollY / 900; }, { passive: true });
    if ('ResizeObserver' in window) new ResizeObserver(resize).observe(canvas); else window.addEventListener('resize', resize);

    /* 지연 로딩·화면 밖 중지: 보일 때만 컨텍스트를 만들고 그린다 */
    /* 첫 컨텍스트 생성·셰이더 컴파일은 첫 화면 렌더(LCP)와 겹치지 않도록 브라우저 유휴 시간으로 미룬다 */
    var booted = false;
    function startSoon() {
      if (booted) { start(); return; }
      booted = true;
      var go = function () { if (inView && !doc.hidden) start(); };
      var schedule = function () {
        if ('requestIdleCallback' in window) requestIdleCallback(go, { timeout: coarse ? 3000 : 1500 }); else setTimeout(go, 600);
      };
      /* 터치 기기는 페이지 로딩이 끝난 뒤에 시작해 첫 방문의 CPU 경쟁을 피한다 */
      if (coarse && doc.readyState !== 'complete') window.addEventListener('load', schedule, { once: true }); else schedule();
    }
    function sync() { if (inView && !doc.hidden) startSoon(); else stop(); }
    doc.addEventListener('visibilitychange', sync);
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(function (es) { inView = es[0].isIntersecting; sync(); }, { rootMargin: '80px' }).observe(box);
    } else { inView = true; sync(); }
  }

  function boot() {
    try { initReveal(); } catch (e) { /* 연출 실패가 기능을 막지 않는다 */ }
    try { initTilt(); } catch (e) { /* noop */ }
    try { initParallax(); } catch (e) { /* noop */ }
    try { initCore(); } catch (e) { /* noop */ }
  }
  if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', boot); else boot();
})();
