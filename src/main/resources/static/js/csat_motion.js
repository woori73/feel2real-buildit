/* 2027 수능 영어 모션 해설 공용 도구: 자료 읽기 · 문제 종이 만들기 · 정답 카드 만들기.
   자료: /img/csat/2027-en/data.json (scripts/csat/build_csat_2027_english.py가 필기본 PDF에서 만든다). */
(function () {
  'use strict';
  var BASE = '/img/csat/2027-en/';
  var CIRC = ['', '①', '②', '③', '④', '⑤'];
  var cache = null;

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function load() {
    if (!cache) {
      cache = fetch(BASE + 'data.json', { credentials: 'same-origin' }).then(function (r) {
        if (!r.ok) throw new Error('자료를 불러오지 못했습니다.');
        return r.json();
      }).then(function (d) {
        d.byKey = {};
        d.questions.forEach(function (q) { d.byKey[q.key] = q; });
        return d;
      });
    }
    return cache;
  }

  /** 그림 한 장(형광 자리 포함). 형광은 is-evidence가 붙으면 순서대로 그어진다. */
  function figure(item, counter) {
    var f = el('figure', 'cm-fig');
    f.style.maxWidth = Math.round(item.widthPt / 337 * 100) + '%';
    var img = el('img');
    img.src = BASE + item.src;
    img.alt = '';
    img.width = item.w;
    img.height = item.h;
    img.decoding = 'async';
    f.appendChild(img);
    (item.highlights || []).forEach(function (h) {
      var m = el('span', 'cm-hl');
      m.style.left = h.x + '%';
      m.style.top = h.y + '%';
      m.style.width = h.w + '%';
      m.style.height = h.h + '%';
      m.style.setProperty('--i', counter.n++);
      f.appendChild(m);
    });
    return f;
  }

  /** 문제 종이: 왼쪽 칸(발문·지문·선택지 그림)을 위에서 아래로. shared는 공통 지문 문항. */
  function paper(q, data, opts) {
    opts = opts || {};
    var p = el('div', 'cm-paper');
    var pan = el('div', 'cm-pan');
    var counter = { n: 0 };
    if (q.sharedFrom && data.byKey[q.sharedFrom] && !opts.noShared) {
      pan.appendChild(el('p', 'cm-label', '공통 지문 · ' + Number(q.sharedFrom) + '번'));
      data.byKey[q.sharedFrom].left.forEach(function (it) {
        if (it.kind === 'img') pan.appendChild(figure(it, counter));
      });
    }
    var items = opts.only ? opts.only : q.left;
    items.forEach(function (it) {
      if (it.kind === 'img') pan.appendChild(figure(it, counter));
      else if (it.kind === 'label') pan.appendChild(el('p', 'cm-label', it.text));
      else if (!/공통 지문은/.test(it.text)) pan.appendChild(el('p', 'cm-note', it.text));
    });
    p.appendChild(pan);
    p.appendChild(el('span', 'cm-scan'));
    p.highlightCount = counter.n;
    return p;
  }

  function answerText(q) {
    var keys = Object.keys(q.answers);
    if (keys.length === 1) return { big: CIRC[q.answers[keys[0]]], small: '정답' };
    return { big: keys.map(function (k) { return CIRC[q.answers[k]]; }).join(' '), small: keys.map(function (k) { return k + '번'; }).join(' · ') };
  }

  /** 3D로 뒤집히는 정답 카드. */
  function flip(q) {
    var a = answerText(q);
    var wrap = el('div', 'cm-flip');
    var inner = el('div', 'cm-flip-in');
    inner.appendChild(el('div', 'cm-flip-face cm-flip-front', '?'));
    var back = el('div', 'cm-flip-face cm-flip-back');
    var s = el('span');
    s.appendChild(el('b', null, a.big));
    s.appendChild(document.createTextNode(a.small));
    back.appendChild(s);
    inner.appendChild(back);
    wrap.appendChild(inner);
    var colors = ['#ffd84d', '#2b7de0', '#1f6f78', '#ff8a65', '#9ad0ff'];
    for (var i = 0; i < 12; i++) {
      var b = el('span', 'cm-burst');
      var ang = i / 12 * Math.PI * 2;
      b.style.setProperty('--dx', Math.round(Math.cos(ang) * 70) + 'px');
      b.style.setProperty('--dy', Math.round(Math.sin(ang) * 50) + 'px');
      b.style.background = colors[i % colors.length];
      wrap.appendChild(b);
    }
    return wrap;
  }

  function section(q, title) {
    return (q.right || []).filter(function (s) { return s.title === title; })[0];
  }

  function reduced() {
    return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  window.CsatMotion = { load: load, paper: paper, flip: flip, section: section, el: el, reduced: reduced, CIRC: CIRC };
})();
