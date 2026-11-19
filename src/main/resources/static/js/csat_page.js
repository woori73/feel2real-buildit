/* /csat/2027-english 전체 화면: 45문항을 한 문항씩 문제 → 근거 → 정답 → 해설 단계로 넘겨 본다.
   ←/→ 문항 이동, Space·Enter 다음 단계. 주소 뒤 #q33 으로 바로 열 수 있다. */
(function () {
  'use strict';
  var M = window.CsatMotion;
  var app = document.getElementById('csatApp');
  if (!app || !M) return;
  var el = M.el;
  var map = document.getElementById('csatMap');
  var view = document.getElementById('csatView');
  var btnStep = document.getElementById('csatStep');
  var btnPrev = document.getElementById('csatPrev');
  var btnNext = document.getElementById('csatNext');
  var auto = document.getElementById('csatAuto');
  var STEP_LABEL = ['근거 보기', '정답 보기', '해설 보기', '다음 문항'];
  var data, order = [], pos = 0, step = 0, autoTimer = null, seen = {};
  var refs = {};

  function keyOfNo(n) {
    for (var i = 0; i < data.questions.length; i++) if (data.questions[i].nos.indexOf(n) >= 0) return i;
    return 0;
  }

  function buildMap() {
    for (var n = 1; n <= 45; n++) {
      var b = el('button', n <= 17 ? 'is-listen' : '', String(n));
      b.type = 'button';
      b.dataset.no = n;
      b.setAttribute('aria-label', n + '번');
      b.addEventListener('click', function () { open(keyOfNo(Number(this.dataset.no)), 0); });
      map.appendChild(b);
    }
  }

  function syncMap() {
    var q = data.questions[pos];
    Array.prototype.forEach.call(map.children, function (b) {
      var n = Number(b.dataset.no);
      b.classList.toggle('is-cur', q.nos.indexOf(n) >= 0);
      b.classList.toggle('is-seen', !!seen[n]);
    });
  }

  function card(title, cls) {
    var c = el('section', 'cm-card cm-reveal' + (cls ? ' ' + cls : ''));
    c.appendChild(el('h3', null, title));
    return c;
  }

  function render(q) {
    view.replaceChildren();
    var head = el('div', 'cm-qhead');
    head.appendChild(el('h2', null, (q.nos.length > 1 ? q.nos.join('~') : q.nos[0]) + '번 · ' + q.type));
    head.appendChild(el('span', 'cm-tag', q.section));
    var st = el('ol', 'cm-steps');
    ['문제', '근거', '정답', '해설'].forEach(function (t) { st.appendChild(el('li', null, t)); });
    head.appendChild(st);
    view.appendChild(head);

    var stage = el('div', 'cm-stage');
    var p = M.paper(q, data);
    p.classList.add('is-enter');
    stage.appendChild(p);
    view.appendChild(stage);

    var side = el('div', 'cm-side');
    var topic = M.section(q, '주제') || M.section(q, '공통 주제');
    var topicCard = null;
    if (topic && topic.body[0]) {
      topicCard = card(topic.title === '공통 주제' ? '공통 주제' : '주제 · 근거가 말하는 것', 'cm-topic');
      topicCard.appendChild(el('p', null, topic.body[0]));
      side.appendChild(topicCard);
    }
    var ansCard = card('정답');
    var row = el('div', 'cm-answer-row');
    var fl = M.flip(q);
    row.appendChild(fl);
    var keys = Object.keys(q.answers);
    row.appendChild(el('p', null, keys.map(function (k) { return k + '번 정답 ' + M.CIRC[q.answers[k]]; }).join(' · ')
      + ' (' + (data.answerBasis || '가답안 기준') + ')'));
    ansCard.appendChild(row);
    var groups = (q.right || []).filter(function (s) { return s.choices && s.choices.length; });
    var choiceEls = [];
    groups.forEach(function (g) {
      if (groups.length > 1) ansCard.appendChild(el('p', 'cm-group-title', g.title));
      var ul = el('ul', 'cm-choices');
      g.choices.forEach(function (c, i) {
        var li = el('li', 'cm-choice cm-reveal ' + (c.correct ? 'is-ok' : 'is-no'));
        li.style.setProperty('--d', i + 2);
        li.appendChild(el('b', null, M.CIRC[c.no] + ' ' + (c.correct ? '정답' : '오답')));
        li.appendChild(el('span', null, c.text));
        ul.appendChild(li);
        choiceEls.push(li);
      });
      ansCard.appendChild(ul);
    });
    side.appendChild(ansCard);

    var explainEls = [];
    var d = 0;
    (q.right || []).forEach(function (s) {
      if (s.choices.length || /주제$/.test(s.title) || s.title === '핵심 단어' || !s.body.length) return;
      var c = card(s.title);
      c.style.setProperty('--d', d++);
      s.body.forEach(function (b) { c.appendChild(el('p', null, b)); });
      side.appendChild(c);
      explainEls.push(c);
    });
    var words = M.section(q, '핵심 단어');
    if (words && words.body.length) {
      var wc = card('핵심 단어 · 눌러서 뜻 보기');
      wc.style.setProperty('--d', d++);
      var grid = el('div', 'cm-words');
      words.body.forEach(function (w) {
        var m = w.match(/^([A-Za-z][A-Za-z'’ \-~.]*?)\s+(.+)$/);
        var b = el('button', 'cm-word');
        b.type = 'button';
        var s = el('span');
        s.appendChild(el('em', null, m ? m[1] : w));
        s.appendChild(el('strong', null, m ? m[2] : ''));
        b.appendChild(s);
        b.addEventListener('click', function () { b.classList.toggle('is-flip'); });
        grid.appendChild(b);
      });
      wc.appendChild(grid);
      side.appendChild(wc);
      explainEls.push(wc);
    }
    view.appendChild(side);

    var sentWrap = null;
    var sents = q.sentences && q.sentences.length ? q.sentences
      : (q.sharedFrom && data.byKey[q.sharedFrom] ? data.byKey[q.sharedFrom].sentences : []);
    if (sents && sents.length) {
      sentWrap = el('section', 'cm-sent cm-card cm-reveal');
      sentWrap.style.gridColumn = '1 / -1';
      sentWrap.appendChild(el('h3', null, '문장별 해석과 어휘'));
      var list = el('div', 'cm-sent-list');
      sents.forEach(function (s, i) {
        var it = el('div', 'cm-sent-item cm-reveal');
        it.style.setProperty('--d', i);
        it.appendChild(el('div', 'en', (i + 1) + '. ' + s.en));
        it.appendChild(el('div', 'ko', s.ko));
        var vo = el('div', 'vo');
        s.vocab.forEach(function (v) { vo.appendChild(el('span', null, v)); });
        it.appendChild(vo);
        list.appendChild(it);
      });
      sentWrap.appendChild(list);
      view.appendChild(sentWrap);
    }
    refs = { stage: stage, paper: p, steps: st, topic: topicCard, ans: ansCard, choices: choiceEls, explain: explainEls, sent: sentWrap };
  }

  function apply() {
    var r = refs;
    Array.prototype.forEach.call(r.steps.children, function (li, i) {
      li.classList.toggle('is-on', i === step);
      li.classList.toggle('is-done', i < step);
    });
    r.stage.classList.toggle('is-evidence', step >= 1);
    // 아직 안 나온 카드는 자리를 차지하지 않게 숨긴다(작은 화면에서 빈칸 방지)
    if (r.topic) r.topic.hidden = step < 1;
    r.ans.hidden = step < 2;
    r.explain.forEach(function (c) { c.hidden = step < 3; });
    if (r.sent) r.sent.hidden = step < 3;
    if (r.topic) r.topic.classList.toggle('is-in', step >= 1);
    r.ans.classList.toggle('is-in', step >= 2);
    r.ans.classList.toggle('is-answer', step >= 2);
    r.choices.forEach(function (c) { c.classList.toggle('is-in', step >= 2); });
    r.explain.forEach(function (c) { c.classList.toggle('is-in', step >= 3); });
    if (r.sent) {
      r.sent.classList.toggle('is-in', step >= 3);
      Array.prototype.forEach.call(r.sent.querySelectorAll('.cm-sent-item'), function (c) { c.classList.toggle('is-in', step >= 3); });
    }
    if (step >= 2) data.questions[pos].nos.forEach(function (n) { seen[n] = true; });
    btnStep.textContent = STEP_LABEL[step];
    syncMap();
    if (window.innerWidth < 900) {
      var target = step === 1 ? r.paper.querySelector('.cm-hl') : step === 2 ? r.ans : step === 3 ? r.explain[0] : null;
      if (target) target.scrollIntoView({ behavior: M.reduced() ? 'auto' : 'smooth', block: step === 1 ? 'center' : 'start' });
    }
  }

  function open(i, s) {
    pos = (i + data.questions.length) % data.questions.length;
    step = s || 0;
    render(data.questions[pos]);
    apply();
    var key = 'q' + data.questions[pos].nos[0];
    if (location.hash !== '#' + key) history.replaceState(null, '', '#' + key);
    scheduleAuto();
  }

  function nextStep() {
    if (step < 3) { step++; apply(); scheduleAuto(); }
    else open(pos + 1, 0);
  }

  function scheduleAuto() {
    clearTimeout(autoTimer);
    if (!auto.checked) return;
    var wait = [2800, 2200 + (refs.paper.highlightCount || 0) * 420, 3800, 7000][step];
    autoTimer = setTimeout(nextStep, wait);
  }

  btnStep.addEventListener('click', nextStep);
  btnPrev.addEventListener('click', function () { open(pos - 1, 0); });
  btnNext.addEventListener('click', function () { open(pos + 1, 0); });
  auto.addEventListener('change', scheduleAuto);
  document.addEventListener('keydown', function (e) {
    if (e.target && /INPUT|TEXTAREA|SELECT/.test(e.target.tagName)) return;
    if (e.key === 'ArrowRight') { e.preventDefault(); open(pos + 1, 0); }
    else if (e.key === 'ArrowLeft') { e.preventDefault(); open(pos - 1, 0); }
    else if ((e.key === ' ' || e.key === 'Enter') && e.target === document.body) { e.preventDefault(); nextStep(); }
  });

  function buildQuick() {
    var g = document.getElementById('csatQuick');
    if (!g) return;
    data.quick.forEach(function (x) {
      var b = el('button', null, x.no + '번');
      b.type = 'button';
      var s = el('span', null, M.CIRC[x.answer]);
      b.appendChild(s);
      b.setAttribute('aria-label', x.no + '번 정답 보기');
      b.addEventListener('click', function () { b.classList.toggle('is-open'); });
      g.appendChild(b);
    });
    var all = document.getElementById('csatQuickAll');
    if (all) all.addEventListener('click', function () {
      Array.prototype.forEach.call(g.children, function (b) { b.classList.add('is-open'); });
    });
  }

  // 로그인 뒤 돌아왔으면(?download=1) PDF를 한 번 내려받는다
  if (/[?&]download=1/.test(location.search)) {
    history.replaceState(null, '', location.pathname + location.hash);
    location.href = '/csat/2027-english/pdf';
  }

  M.load().then(function (d) {
    data = d;
    buildMap();
    buildQuick();
    var m = location.hash.match(/^#q(\d{1,2})$/);
    open(m ? keyOfNo(Number(m[1])) : 0, 0);
  }).catch(function () {
    view.replaceChildren(el('p', 'cm-loading', '해설 자료를 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.'));
  });
})();
