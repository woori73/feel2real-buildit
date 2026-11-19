/* 홈 오른쪽 카드: 2027 수능 영어 해설 모션(문제 → 노란 근거 → 정답·해설)을 독해 문항 순서로 자동 재생한다.
   화면에 보일 때만 자료를 읽고 재생한다. 멈춤 버튼으로 세울 수 있다. */
(function () {
  'use strict';
  var root = document.getElementById('csatHome');
  if (!root || !window.CsatMotion) return;
  var M = window.CsatMotion;
  var PLAY = ['33', '20', '23', '31', '29', '38', '40', '22', '18', '34', '32', '24', '21', '19', '30', '39'];
  var stage = root.querySelector('[data-cm-stage]');
  var qLabel = root.querySelector('[data-cm-q]');
  var steps = Array.prototype.slice.call(root.querySelectorAll('.cm-steps li'));
  var btnPause = root.querySelector('[data-cm-pause]');
  var btnPrev = root.querySelector('[data-cm-prev]');
  var btnNext = root.querySelector('[data-cm-next]');
  var data = null;
  var idx = 0;
  var timers = [];
  var paused = M.reduced();
  var started = false;

  function clear() { timers.forEach(clearTimeout); timers = []; }
  function later(fn, ms) { timers.push(setTimeout(fn, ms)); }

  function mark(step, dur) {
    steps.forEach(function (li, i) {
      li.classList.toggle('is-on', i === step);
      li.classList.toggle('is-done', i < step);
      if (i === step) li.style.setProperty('--cm-step', (dur / 1000) + 's');
    });
  }

  function bestImages(q) {
    var imgs = q.left.filter(function (i) { return i.kind === 'img'; });
    var best = imgs.reduce(function (a, b) { return (b.highlights.length > (a ? a.highlights.length : -1)) ? b : a; }, null);
    var out = [];
    if (imgs[0] && imgs[0] !== best && imgs[0].h < 120) out.push(imgs[0]);
    if (best) out.push(best);
    return out;
  }

  function show(i) {
    clear();
    idx = (i + PLAY.length) % PLAY.length;
    var q = data.byKey[PLAY[idx]];
    if (!q) return;
    var hx = M.section(q, '해설');
    qLabel.textContent = Number(q.nos[0]) + '번 · ' + q.type;
    var box = M.el('div', 'cm-stage');
    var p = M.paper(q, data, { only: bestImages(q), noShared: true });
    box.appendChild(p);
    var ans = M.el('div', 'cm-home-ans');
    ans.appendChild(M.flip(q));
    ans.appendChild(M.el('p', null, hx && hx.body[0] ? hx.body[0] : ''));
    box.appendChild(ans);
    stage.replaceChildren(box);
    if (paused && M.reduced()) {
      box.classList.add('is-evidence', 'is-answer');
      mark(2, 0);
      return;
    }
    p.classList.add('is-enter');
    var n = p.highlightCount;
    var tQ = 2600, tE = 900 + n * 420 + 1400, tA = 5200;
    mark(0, tQ);
    later(function () {
      // 첫 근거가 보이도록 종이를 끌어올린다(높은 지문)
      var pan = p.querySelector('.cm-pan');
      var first = p.querySelector('.cm-hl');
      if (pan && first) {
        var limit = p.clientHeight;
        var top = first.getBoundingClientRect().top - pan.getBoundingClientRect().top;
        var shift = Math.max(0, Math.min(top - limit * 0.35, pan.scrollHeight - limit));
        pan.style.transform = 'translateY(' + (-shift) + 'px)';
      }
      box.classList.add('is-evidence');
      mark(1, tE);
    }, tQ);
    later(function () { box.classList.add('is-answer'); mark(2, tA); }, tQ + tE);
    if (!paused) later(function () { show(idx + 1); }, tQ + tE + tA);
  }

  function setPaused(v) {
    paused = v;
    btnPause.textContent = v ? '▶' : 'Ⅱ';
    btnPause.setAttribute('aria-label', v ? '재생' : '멈춤');
    btnPause.setAttribute('aria-pressed', String(v));
    if (!v) show(idx);
    else clear();
  }

  function start() {
    if (started) return;
    started = true;
    M.load().then(function (d) {
      data = d;
      setPaused(paused);
      if (paused) show(idx);
    }).catch(function () {
      stage.replaceChildren(M.el('div', 'cm-loading', '해설을 불러오지 못했습니다. 아래 「45문항 모션 해설」에서 보세요.'));
    });
  }

  btnPause.addEventListener('click', function () { setPaused(!paused); });
  btnPrev.addEventListener('click', function () { if (data) show(idx - 1); });
  btnNext.addEventListener('click', function () { if (data) show(idx + 1); });
  document.addEventListener('visibilitychange', function () { if (document.hidden && data) clear(); else if (data && !paused) show(idx); });

  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) { if (e.isIntersecting) { start(); io.disconnect(); } });
    }, { rootMargin: '200px' });
    io.observe(root);
  } else {
    start();
  }
})();
