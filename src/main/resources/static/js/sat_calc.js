/*
 * Digital SAT 연습용 계산기 — 공학용 + 그래프. eval/Function을 쓰지 않고 직접 만든 파서로 식을 계산한다.
 * 지원: + − × ÷ ^ ( ), 암묵적 곱(2x, 3(…), (…)(…)), sin cos tan asin acos atan sqrt abs ln log exp, π(pi) e, x, ans
 * 설계: docs/design/testprep-digital-sat.md §7 계산기
 */
(function (root) {
  'use strict';

  const FUNCS = {
    sin: (v, d) => Math.sin(d ? v * Math.PI / 180 : v),
    cos: (v, d) => Math.cos(d ? v * Math.PI / 180 : v),
    tan: (v, d) => Math.tan(d ? v * Math.PI / 180 : v),
    asin: (v, d) => d ? Math.asin(v) * 180 / Math.PI : Math.asin(v),
    acos: (v, d) => d ? Math.acos(v) * 180 / Math.PI : Math.acos(v),
    atan: (v, d) => d ? Math.atan(v) * 180 / Math.PI : Math.atan(v),
    sqrt: v => Math.sqrt(v),
    abs: v => Math.abs(v),
    ln: v => Math.log(v),
    log: v => Math.log10(v),
    exp: v => Math.exp(v)
  };
  // 긴 이름부터 맞춰 본다(asin이 sin보다 먼저).
  const NAMES = ['asin', 'acos', 'atan', 'sqrt', 'sin', 'cos', 'tan', 'abs', 'exp', 'log', 'ln', 'ans', 'pi', 'x', 'e'];

  function tokenize(src) {
    const s = String(src).replace(/−/g, '-').replace(/×|·/g, '*').replace(/÷/g, '/').replace(/π/g, 'pi').replace(/√/g, 'sqrt');
    const tokens = [];
    let i = 0;
    while (i < s.length) {
      const c = s[i];
      if (/\s/.test(c)) { i++; continue; }
      if (/[0-9.]/.test(c)) {
        const m = /^(\d+\.?\d*|\.\d+)(e[+-]?\d+)?/i.exec(s.slice(i));
        if (!m) throw new Error('Invalid number');
        // "2e"처럼 지수 표기가 아닌 e는 상수로 남긴다.
        tokens.push({ t: 'num', v: parseFloat(m[0]) });
        i += m[0].length;
        continue;
      }
      if ('+-*/^(),'.includes(c)) { tokens.push({ t: 'op', v: c }); i++; continue; }
      if (/[a-z]/i.test(c)) {
        const rest = s.slice(i).toLowerCase();
        const name = NAMES.find(n => rest.startsWith(n));
        if (!name) throw new Error('Unknown symbol: ' + c);
        tokens.push({ t: 'id', v: name });
        i += name.length;
        continue;
      }
      throw new Error('Unexpected character: ' + c);
    }
    return tokens;
  }

  // 재귀 하강 파서 → 클로저 트리. node(env) = 수
  function parse(src) {
    const tk = tokenize(src);
    let p = 0;
    const peek = () => tk[p];
    const isOp = (v) => peek() && peek().t === 'op' && peek().v === v;
    const startsFactor = () => {
      const t = peek();
      return t && (t.t === 'num' || t.t === 'id' || (t.t === 'op' && t.v === '('));
    };

    function expr() {
      let left = term();
      while (isOp('+') || isOp('-')) {
        const op = tk[p++].v;
        const a = left, b = term();
        left = op === '+' ? env => a(env) + b(env) : env => a(env) - b(env);
      }
      return left;
    }
    function term() {
      let left = unary();
      for (;;) {
        if (isOp('*') || isOp('/')) {
          const op = tk[p++].v;
          const a = left, b = unary();
          left = op === '*' ? env => a(env) * b(env) : env => a(env) / b(env);
        } else if (startsFactor()) { // 암묵적 곱
          const a = left, b = power();
          left = env => a(env) * b(env);
        } else break;
      }
      return left;
    }
    function unary() {
      if (isOp('-')) { p++; const a = unary(); return env => -a(env); }
      if (isOp('+')) { p++; return unary(); }
      return power();
    }
    function power() {
      const base = primary();
      if (isOp('^')) {
        p++;
        const ex = unary(); // 오른쪽 결합: 2^3^2 = 2^(3^2)
        return env => Math.pow(base(env), ex(env));
      }
      return base;
    }
    function primary() {
      const t = tk[p];
      if (!t) throw new Error('Incomplete expression');
      if (t.t === 'num') { p++; const v = t.v; return () => v; }
      if (t.t === 'op' && t.v === '(') {
        p++;
        const inner = expr();
        if (!isOp(')')) throw new Error('Missing )');
        p++;
        return inner;
      }
      if (t.t === 'id') {
        p++;
        if (t.v === 'pi') return () => Math.PI;
        if (t.v === 'e') return () => Math.E;
        if (t.v === 'x') return env => (env.x === undefined ? NaN : env.x);
        if (t.v === 'ans') return env => (env.ans === undefined ? 0 : env.ans);
        const fn = FUNCS[t.v];
        let arg;
        if (isOp('(')) {
          p++;
          arg = expr();
          if (!isOp(')')) throw new Error('Missing )');
          p++;
        } else {
          arg = power(); // sin x, sqrt 2
        }
        return env => fn(arg(env), !!env.deg);
      }
      throw new Error('Unexpected ' + t.v);
    }

    if (!tk.length) throw new Error('Empty expression');
    const tree = expr();
    if (p < tk.length) throw new Error('Unexpected ' + tk[p].v);
    return tree;
  }

  function compile(src) {
    const body = String(src).replace(/^\s*y\s*=/i, '');
    const tree = parse(body);
    return env => tree(env || {});
  }

  function evaluate(src, env) {
    return compile(src)(env || {});
  }

  function format(v) {
    if (typeof v !== 'number' || Number.isNaN(v)) return 'Error';
    if (!Number.isFinite(v)) return v > 0 ? '∞' : '−∞';
    if (Math.abs(v) < 1e-12) return '0';
    const s = Math.abs(v) >= 1e10 || Math.abs(v) < 1e-6 ? v.toExponential(8) : String(parseFloat(v.toPrecision(12)));
    return s.replace(/(\.\d*?)0+(e|$)/, '$1$2').replace(/\.(e|$)/, '$1').replace(/^-/, '−');
  }

  // ===== 화면 =====
  const COLORS = ['#c0392b', '#2563eb', '#15803d', '#7c3aed'];

  function mount(el) {
    el.innerHTML = ''
      + '<div class="calc-tabs" role="tablist">'
      + '<button type="button" class="calc-tab active" data-tab="sci">Scientific</button>'
      + '<button type="button" class="calc-tab" data-tab="graph">Graphing</button>'
      + '<button type="button" class="calc-deg" data-deg title="Angle mode">RAD</button></div>'
      + '<div class="calc-pane" data-pane="sci">'
      + '<div class="calc-history" data-history></div>'
      + '<input class="calc-input" data-expr type="text" spellcheck="false" autocomplete="off" aria-label="Expression" placeholder="e.g. 3(2+5)^2 or sqrt(2)">'
      + '<div class="calc-result" data-result aria-live="polite">&nbsp;</div>'
      + '<div class="calc-keys">' + [
        ['7', '8', '9', '÷', '(', ')'],
        ['4', '5', '6', '×', '^', '√('],
        ['1', '2', '3', '−', 'π', 'e'],
        ['0', '.', 'ans', '+', 'sin(', 'cos('],
        ['tan(', 'ln(', 'log(', 'abs(', '⌫', 'C'],
      ].map(r => r.map(k => '<button type="button" data-key="' + k + '"' + (k === '⌫' || k === 'C' ? ' class="fn"' : '') + '>' + (k.length > 1 ? k.replace('(', '') : k) + '</button>').join('')).join('')
      + '<button type="button" class="eq" data-key="=">=</button></div></div>'
      + '<div class="calc-pane" data-pane="graph" hidden>'
      + '<div class="graph-exprs">' + COLORS.map((c, i) => '<label class="graph-row"><span class="dot" style="background:' + c + '"></span>'
        + '<span>y =</span><input type="text" data-g="' + i + '" spellcheck="false" autocomplete="off" placeholder="' + (i === 0 ? '2x − 3' : '') + '"></label>').join('') + '</div>'
      + '<div class="graph-tools"><button type="button" data-zoom="in" aria-label="Zoom in">＋</button><button type="button" data-zoom="out" aria-label="Zoom out">−</button><button type="button" data-zoom="reset">Reset</button><span class="graph-err" data-gerr></span></div>'
      + '<canvas class="graph-canvas" data-canvas width="600" height="420"></canvas></div>';

    const state = { deg: false, ans: 0, view: { cx: 0, cy: 0, scale: 10 } };
    const input = el.querySelector('[data-expr]');
    const result = el.querySelector('[data-result]');
    const history = el.querySelector('[data-history]');

    el.querySelectorAll('.calc-tab').forEach(b => b.addEventListener('click', () => {
      el.querySelectorAll('.calc-tab').forEach(x => x.classList.toggle('active', x === b));
      el.querySelectorAll('.calc-pane').forEach(pn => { pn.hidden = pn.dataset.pane !== b.dataset.tab; });
      if (b.dataset.tab === 'graph') draw();
    }));
    el.querySelector('[data-deg]').addEventListener('click', e => {
      state.deg = !state.deg;
      e.currentTarget.textContent = state.deg ? 'DEG' : 'RAD';
      preview(); draw();
    });

    function preview() {
      const src = input.value.trim();
      if (!src) { result.innerHTML = '&nbsp;'; return; }
      try { result.textContent = '= ' + format(evaluate(src, { deg: state.deg, ans: state.ans })); result.classList.remove('err'); }
      catch (err) { result.textContent = ' '; }
    }
    function commit() {
      const src = input.value.trim();
      if (!src) return;
      try {
        const v = evaluate(src, { deg: state.deg, ans: state.ans });
        state.ans = v;
        const row = document.createElement('div');
        row.className = 'calc-hrow';
        row.innerHTML = '<span></span><b></b>';
        row.firstChild.textContent = src;
        row.lastChild.textContent = format(v);
        row.addEventListener('click', () => { input.value = src; preview(); input.focus(); });
        history.appendChild(row);
        history.scrollTop = history.scrollHeight;
        input.value = '';
        result.textContent = '= ' + format(v);
      } catch (err) {
        result.textContent = err.message;
        result.classList.add('err');
      }
    }
    input.addEventListener('input', preview);
    input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); commit(); } e.stopPropagation(); });
    el.querySelectorAll('[data-key]').forEach(b => b.addEventListener('click', () => {
      const k = b.dataset.key;
      if (k === '=') commit();
      else if (k === 'C') { input.value = ''; preview(); }
      else if (k === '⌫') { input.value = input.value.slice(0, -1); preview(); }
      else {
        const pos = input.selectionStart == null ? input.value.length : input.selectionStart;
        input.value = input.value.slice(0, pos) + k + input.value.slice(input.selectionEnd == null ? pos : input.selectionEnd);
        input.selectionStart = input.selectionEnd = pos + k.length;
        preview();
      }
      input.focus();
    }));

    // ----- 그래프 -----
    const canvas = el.querySelector('[data-canvas]');
    const gerr = el.querySelector('[data-gerr]');
    const gInputs = [...el.querySelectorAll('[data-g]')];
    gInputs.forEach(inp => {
      inp.addEventListener('input', draw);
      inp.addEventListener('keydown', e => e.stopPropagation());
    });
    el.querySelectorAll('[data-zoom]').forEach(b => b.addEventListener('click', () => {
      const z = b.dataset.zoom;
      if (z === 'in') state.view.scale /= 1.5;
      else if (z === 'out') state.view.scale *= 1.5;
      else state.view = { cx: 0, cy: 0, scale: 10 };
      draw();
    }));
    let drag = null;
    canvas.addEventListener('pointerdown', e => { drag = { x: e.clientX, y: e.clientY, cx: state.view.cx, cy: state.view.cy }; canvas.setPointerCapture(e.pointerId); });
    canvas.addEventListener('pointermove', e => {
      if (!drag) return;
      const r = canvas.getBoundingClientRect();
      const unit = (2 * state.view.scale) / r.width;
      state.view.cx = drag.cx - (e.clientX - drag.x) * unit;
      state.view.cy = drag.cy + (e.clientY - drag.y) * unit;
      draw();
    });
    canvas.addEventListener('pointerup', () => { drag = null; });
    canvas.addEventListener('wheel', e => { e.preventDefault(); state.view.scale *= e.deltaY > 0 ? 1.15 : 1 / 1.15; draw(); }, { passive: false });

    function niceStep(range) {
      const raw = range / 10;
      const p = Math.pow(10, Math.floor(Math.log10(raw)));
      const m = raw / p;
      return (m < 1.5 ? 1 : m < 3.5 ? 2 : m < 7.5 ? 5 : 10) * p;
    }

    function draw() {
      if (canvas.closest('[hidden]')) return;
      const w = canvas.clientWidth || 600;
      const h = Math.round(w * 0.7);
      const dpr = root.devicePixelRatio || 1;
      canvas.width = w * dpr; canvas.height = h * dpr;
      canvas.style.height = h + 'px';
      const ctx = canvas.getContext('2d');
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const v = state.view;
      const xMin = v.cx - v.scale, xMax = v.cx + v.scale;
      const yHalf = v.scale * h / w;
      const yMin = v.cy - yHalf, yMax = v.cy + yHalf;
      const X = x => (x - xMin) / (xMax - xMin) * w;
      const Y = y => h - (y - yMin) / (yMax - yMin) * h;
      ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, w, h);
      const step = niceStep(xMax - xMin);
      ctx.lineWidth = 1; ctx.strokeStyle = '#e5e9ef'; ctx.fillStyle = '#6b7785'; ctx.font = '11px sans-serif';
      for (let gx = Math.ceil(xMin / step) * step; gx <= xMax; gx += step) {
        ctx.beginPath(); ctx.moveTo(X(gx), 0); ctx.lineTo(X(gx), h); ctx.stroke();
        if (Math.abs(gx) > step / 2) ctx.fillText(format(gx), X(gx) + 2, Math.min(h - 4, Math.max(12, Y(0) + 12)));
      }
      for (let gy = Math.ceil(yMin / step) * step; gy <= yMax; gy += step) {
        ctx.beginPath(); ctx.moveTo(0, Y(gy)); ctx.lineTo(w, Y(gy)); ctx.stroke();
        if (Math.abs(gy) > step / 2) ctx.fillText(format(gy), Math.min(w - 30, Math.max(2, X(0) + 4)), Y(gy) - 2);
      }
      ctx.strokeStyle = '#334155'; ctx.lineWidth = 1.4;
      ctx.beginPath(); ctx.moveTo(0, Y(0)); ctx.lineTo(w, Y(0)); ctx.moveTo(X(0), 0); ctx.lineTo(X(0), h); ctx.stroke();
      const errors = [];
      gInputs.forEach((inp, i) => {
        const src = inp.value.trim();
        if (!src) return;
        let f;
        try { f = compile(src); } catch (err) { errors.push('y' + (i + 1) + ': ' + err.message); return; }
        ctx.strokeStyle = COLORS[i]; ctx.lineWidth = 2.2; ctx.beginPath();
        let pen = false, prevY = null;
        for (let px = 0; px <= w; px += 1) {
          const x = xMin + (px / w) * (xMax - xMin);
          const y = f({ x, deg: state.deg, ans: state.ans });
          if (!Number.isFinite(y) || (prevY !== null && Math.abs(y - prevY) > (yMax - yMin) * 2)) { pen = false; prevY = Number.isFinite(y) ? y : null; continue; }
          const py = Y(y);
          if (pen) ctx.lineTo(px, py); else { ctx.moveTo(px, py); pen = true; }
          prevY = y;
        }
        ctx.stroke();
      });
      gerr.textContent = errors.join(' · ');
    }

    return { focus: () => input.focus(), redraw: draw };
  }

  root.SATCalc = { compile, evaluate, format, tokenize, mount };
})(typeof window !== 'undefined' ? window : globalThis);
