/* 위메이드 개인주주 분석 — glanceable fintech UI */
(function () {
  'use strict';

  var API = {
    symbols: '/api/wemade/symbols',
    overview: '/api/wemade/overview',
    prices: '/api/wemade/prices',
    compare: '/api/wemade/compare',
    flows: '/api/wemade/flows',
    events: '/api/wemade/events',
    factChecks: '/api/wemade/fact-checks',
    gameTracking: '/api/wemade/game-tracking',
    community: '/api/wemade/community',
    chinaDisc: '/api/wemade/china/disclosures',
    chinaNews: '/api/wemade/china/news',
    chinaLatest: '/api/wemade/china/latest'
  };
  var NO_DATA = '데이터 없음';
  var DEAL_CLOSE = new Date(2026, 9, 30); // 10/30 잔금 예정
  var PER_SHARE = 68910; // DART rcpNo 20260630900888 공시값 (A등급)

  function el(id) { return document.getElementById(id); }
  function esc(v) {
    return String(v === null || v === undefined ? '' : v)
      .replace(/[&<>"']/g, function (c) {
        return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
      });
  }
  function num(v) { var n = Number(v); return isFinite(n) ? n : null; }
  function fmtInt(v) { var n = num(v); return n === null ? null : Math.round(n).toLocaleString('ko-KR'); }
  function fmtPrice(v, currency) {
    var n = num(v);
    if (n === null) return null;
    if (currency === 'CNY') return n.toFixed(2);
    return Math.round(n).toLocaleString('ko-KR');
  }
  function fmtSigned(v) { var n = num(v); if (n === null) return null; return (n > 0 ? '+' : '') + Math.round(n).toLocaleString('ko-KR'); }
  function fmtPct(v) { var n = num(v); if (n === null) return null; return (n > 0 ? '+' : '') + n.toFixed(2) + '%'; }
  function signClass(v) { var n = num(v); if (n === null || n === 0) return ''; return n > 0 ? 'up' : 'down'; }
  function stateHtml(msg) { return '<div class="state">' + esc(msg) + '</div>'; }
  function getJSON(url) {
    return fetch(url, { headers: { 'Accept': 'application/json' } }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }
  function extLink(url) {
    if (!url || !/^https?:\/\//i.test(url)) return '';
    return ' <a href="' + esc(url) + '" target="_blank" rel="noopener noreferrer">원문 ↗</a>';
  }
  function gradeBadge(g) {
    var map = { A: ['grade-a', 'A·공식 1차 자료'], B: ['grade-b', 'B·복수 신뢰자료'], C: ['grade-c', 'C·언론/업계자료'], D: ['grade-d', 'D·계산·커뮤니티 주장'], U: ['grade-u', 'U·미확인'] };
    var m = map[String(g || 'U').toUpperCase()] || map.U;
    return '<span class="' + m[0] + '">' + esc(m[1]) + '</span>';
  }

  /* ============ ① 히어로 ============ */
  function renderHero(o, spark) {
    var priceBox = el('hero-price'), chgBox = el('hero-chg');
    if (!o || !o.hasData || num(o.close) === null) {
      priceBox.innerHTML = stateHtml(NO_DATA);
      el('hero-date').textContent = '';
      return;
    }
    priceBox.textContent = fmtInt(o.close) + '원';
    var cls = signClass(o.changeRate !== undefined ? o.changeRate : o.change);
    chgBox.innerHTML = '<span class="chg-pill ' + cls + '">' +
      esc(fmtSigned(o.change) + '원 (' + fmtPct(o.changeRate) + ')') + '</span>';
    el('hero-date').textContent = (o.tradeDate || '') + ' 장마감 기준 · KOSDAQ';
    // 52주 레인지 바
    var hi = num(o.high52w), lo = num(o.low52w), c = num(o.close);
    if (hi !== null && lo !== null && hi > lo) {
      var pos = Math.min(100, Math.max(0, (c - lo) / (hi - lo) * 100));
      el('range52').innerHTML = '<div>52주 최고 ' + esc(fmtInt(hi)) + ' · 최저 ' + esc(fmtInt(lo)) + '</div>' +
        '<div class="range-bar"><span class="pos" style="left:' + pos.toFixed(1) + '%"></span></div>';
    }
    // 스파크라인
    if (spark && spark.length > 1 && typeof Chart !== 'undefined') {
      var ctx = el('spark').getContext('2d');
      new Chart(ctx, {
        type: 'line',
        data: { labels: spark.map(function (p) { return p.date; }), datasets: [{ data: spark.map(function (p) { return p.close; }), borderColor: '#ffd166', borderWidth: 2, pointRadius: 0, tension: 0.3 }] },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { enabled: false } }, scales: { x: { display: false }, y: { display: false } }, animation: false }
      });
    }
    // 경영권 매각 배수
    var m = PER_SHARE / c;
    el('deal-multiple').innerHTML =
      '<span class="big">약 ' + m.toFixed(1) + '배</span>' +
      '<span class="cap">공시 1주당 68,910원에 경영권 매각<br>DART rcpNo 20260630900888</span>';
  }

  /* ============ ② D-Day ============ */
  function renderDday() {
    var now = new Date(); now.setHours(0, 0, 0, 0);
    var diff = Math.round((DEAL_CLOSE - now) / 86400000);
    el('dday').textContent = diff > 0 ? 'D-' + diff : (diff === 0 ? 'D-Day' : 'D+' + Math.abs(diff));
  }

  /* ============ ③ 중국 카드 ============ */
  function renderChinaHero(latest) {
    var box = el('china-hero');
    var d = latest && latest.disclosure, n = latest && latest.news;
    if (!d && !n) { box.innerHTML = stateHtml(NO_DATA); return; }
    var html = '';
    if (d) {
      html += '<div class="china-item">' + gradeBadge(d.grade) +
        '<div class="kr">' + esc(d.titleKr || d.titleCn) + '</div>' +
        '<div class="meta">' + esc(d.secName || '') + ' · ' + esc(d.announcedAt || '') + ' · CNINFO 공식공시</div>' +
        (d.summaryKr ? '<div class="why">' + esc(d.summaryKr) + '</div>' : '') + '</div>';
    }
    if (n) {
      html += '<div class="china-item">' + gradeBadge(n.grade) +
        '<div class="kr">' + esc(n.titleKr || n.titleCn) + '</div>' +
        '<div class="meta">' + esc(n.sourceName || '') + ' · ' + esc(n.publishedAt || '') + '</div></div>';
    }
    box.innerHTML = html;
  }

  /* ============ ④ 수급 바 ============ */
  var INVESTORS = [
    { key: 'foreign', label: '외국인' },
    { key: 'institution', label: '기관' },
    { key: 'retail', label: '개인' }
  ];
  function renderFlowBars(summary) {
    var box = el('flow-bars');
    var d1 = summary && summary.d1;
    if (!d1) { box.innerHTML = stateHtml(NO_DATA); return; }
    var max = 1;
    INVESTORS.forEach(function (inv) { var v = num(d1[inv.key]); if (v !== null) max = Math.max(max, Math.abs(v)); });
    box.innerHTML = INVESTORS.map(function (inv) {
      var v = num(d1[inv.key]);
      if (v === null) return '<div class="flow-row"><span>' + esc(inv.label) + '</span><span></span><span class="amt muted">' + NO_DATA + '</span></div>';
      var w = Math.max(4, Math.abs(v) / max * 100);
      var fill = v >= 0
        ? '<span class="bar-fill pos" style="left:50%;width:' + (w / 2).toFixed(1) + '%"></span>'
        : '<span class="bar-fill neg" style="right:50%;width:' + (w / 2).toFixed(1) + '%"></span>';
      return '<div class="flow-row"><span>' + esc(inv.label) + '</span>' +
        '<span class="bar-track">' + fill + '</span>' +
        '<span class="amt ' + signClass(v) + '">' + esc(fmtSigned(v)) + '</span></div>';
    }).join('') + '<div class="muted small" style="margin-top:6px">단위: 백만원 · +순매수 / −순매도</div>';
  }

  /* ============ ⑤ 오늘 중요정보 ============ */
  function renderTodayIntel(events) {
    var box = el('today-intel');
    if (!events || !events.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    var ev = events[0];
    box.innerHTML = '<div class="tl-date">' + esc(ev.eventDate || '') + '</div>' +
      '<div class="tl-title">' + esc(ev.title || '') + '</div>' +
      '<div style="margin-top:6px">' + gradeBadge(ev.factGrade) + '</div>' +
      (ev.summary ? '<div class="muted small" style="margin-top:6px">' + esc(ev.summary) + '</div>' : '');
  }

  /* ============ 3종목 비교 ============ */
  var compareChart = null;
  var EVENT_LINES = [
    { date: '2026-06-29', label: '중국 판호' },
    { date: '2026-06-30', label: 'SPA 체결' },
    { date: '2026-09-12', label: '킹넷 공시' }
  ];
  function renderCompare(data) {
    var box = el('compareChart');
    if (typeof Chart === 'undefined') { return; }
    if (compareChart) { compareChart.destroy(); compareChart = null; }
    var series = (data && data.series) || [];
    var colors = { '112040': '#e5484d', '101730': '#2f6fed', '002517': '#7c5cff' };
    // 모든 날짜 라벨 합치기
    var labelSet = {};
    series.forEach(function (s) { (s.points || []).forEach(function (p) { labelSet[p.date] = 1; }); });
    var labels = Object.keys(labelSet).sort();
    var datasets = series.filter(function (s) { return s.hasData; }).map(function (s) {
      var byDate = {};
      (s.points || []).forEach(function (p) { byDate[p.date] = p.value; });
      return {
        label: s.name + ' (' + s.currency + ')',
        data: labels.map(function (d) { return byDate[d] === undefined ? null : byDate[d]; }),
        borderColor: colors[s.symbol] || '#888',
        borderWidth: 2, pointRadius: 0, tension: 0.2, spanGaps: true
      };
    });
    compareChart = new Chart(box.getContext('2d'), {
      type: 'line',
      data: { labels: labels, datasets: datasets },
      options: {
        responsive: true, maintainAspectRatio: false, animation: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: function (c) { return ' ' + c.dataset.label + ': ' + c.parsed.y; } } },
          annotation: undefined
        },
        scales: {
          x: { ticks: { maxTicksLimit: 6, font: { size: 10 } }, grid: { display: false } },
          y: { ticks: { font: { size: 10 } }, title: { display: true, text: '기준일=100', font: { size: 10 } } }
        }
      }
    });
    // 이벤트 수직선 마커 (범례 아래 텍스트로)
    var marks = EVENT_LINES.map(function (e) { return e.date + ' ' + e.label; }).join(' · ');
    el('compare-legend').innerHTML = datasets.map(function (ds, i) {
      var key = series.filter(function (s) { return s.hasData; })[i].symbol;
      return '<span><span class="sw" style="background:' + (colors[key] || '#888') + '"></span>' + esc(ds.label) + '</span>';
    }).join('') + '<div class="muted small" style="width:100%;margin-top:4px">이벤트: ' + esc(marks) + '</div>';
  }

  function renderStockCards(overviews) {
    var box = el('stock-cards');
    box.innerHTML = overviews.map(function (o) {
      if (!o || !o.hasData) {
        return '<div class="stock-card"><div class="n">' + esc(o ? o.name : '') + '</div><div class="r muted">' + NO_DATA + '</div></div>';
      }
      var cls = signClass(o.changeRate);
      return '<div class="stock-card"><div class="n">' + esc(o.name) + '</div>' +
        '<div class="p">' + esc(fmtPrice(o.close, o.currency)) + '</div>' +
        '<div class="r ' + cls + '">' + esc(fmtPct(o.changeRate)) + '</div>' +
        '<div class="c">' + esc(o.currency) + ' · ' + esc(o.tradeDate || '') + '</div></div>';
    }).join('');
  }

  /* ============ 주가차트 ============ */
  var priceChart = null, rsiChart = null;
  var PERIODS = [
    { label: '1개월', days: 30 }, { label: '3개월', days: 90 }, { label: '6개월', days: 180 },
    { label: '1년', days: 365 }, { label: '전체', days: 1500 }
  ];
  function renderPeriodTabs() {
    el('period-tabs').innerHTML = PERIODS.map(function (p, i) {
      return '<button type="button" class="tab" role="tab" data-days="' + p.days + '" aria-selected="' + (i === 2) + '">' + esc(p.label) + '</button>';
    }).join('');
  }
  function loadPrices(days) {
    if (typeof Chart === 'undefined') { el('chart-state').innerHTML = stateHtml('차트 라이브러리를 불러오지 못했습니다.'); return; }
    el('chart-state').innerHTML = '';
    getJSON(API.prices + '?symbol=112040&days=' + days).then(function (rows) {
      if (priceChart) { priceChart.destroy(); priceChart = null; }
      if (rsiChart) { rsiChart.destroy(); rsiChart = null; }
      var labels = rows.map(function (r) { return r.date; });
      function ds(key, color, width) {
        return { label: key, data: rows.map(function (r) { return r[key]; }), borderColor: color, borderWidth: width || 1.5, pointRadius: 0, tension: 0.2, spanGaps: true };
      }
      priceChart = new Chart(el('priceChart').getContext('2d'), {
        type: 'line',
        data: { labels: labels, datasets: [
          { label: '종가', data: rows.map(function (r) { return r.close; }), borderColor: '#14213d', borderWidth: 2, pointRadius: 0, tension: 0.2 },
          ds('ma5', '#e5484d'), ds('ma20', '#2f6fed'), ds('ma60', '#7c5cff'), ds('ma120', '#1f9d55')
        ]},
        options: { responsive: true, maintainAspectRatio: false, animation: false, interaction: { mode: 'index', intersect: false }, plugins: { legend: { labels: { boxWidth: 12, font: { size: 10 } } } }, scales: { x: { ticks: { maxTicksLimit: 6, font: { size: 10 } }, grid: { display: false } }, y: { ticks: { font: { size: 10 } } } } }
      });
      rsiChart = new Chart(el('rsiChart').getContext('2d'), {
        type: 'line',
        data: { labels: labels, datasets: [{ label: 'RSI14', data: rows.map(function (r) { return r.rsi14; }), borderColor: '#b7791f', borderWidth: 1.5, pointRadius: 0, tension: 0.2, spanGaps: true }] },
        options: { responsive: true, maintainAspectRatio: false, animation: false, plugins: { legend: { display: false } }, scales: { x: { display: false }, y: { min: 0, max: 100, ticks: { font: { size: 9 } } } } }
      });
    }).catch(function () { el('chart-state').innerHTML = stateHtml('차트 데이터를 불러오지 못했습니다.'); });
  }

  /* ============ 수급 상세 ============ */
  var FLOW_TABS = [{ label: '오늘', k: 'd1' }, { label: '5일', k: 'd5' }, { label: '20일', k: 'd20' }, { label: '60일', k: 'd60' }];
  var flowSummary = null;
  function renderFlowTabs() {
    el('flow-tabs').innerHTML = FLOW_TABS.map(function (t, i) {
      return '<button type="button" class="tab" role="tab" data-k="' + t.k + '" aria-selected="' + (i === 0) + '">' + esc(t.label) + '</button>';
    }).join('');
  }
  function renderFlowDetail(k) {
    var box = el('flow-detail');
    var d = flowSummary ? flowSummary[k] : null;
    if (!d) { box.innerHTML = stateHtml(NO_DATA); return; }
    box.innerHTML = INVESTORS.map(function (inv) {
      var v = num(d[inv.key]);
      var txt = v === null ? NO_DATA : fmtSigned(v) + ' 백만원';
      return '<div class="kv"><span class="k">' + esc(inv.label) + '</span><span class="v ' + signClass(v) + '">' + esc(txt) + '</span></div>';
    }).join('');
  }

  /* ============ 이벤트 타임라인 ============ */
  function pct(v) { var n = num(v); return n === null ? '—' : fmtPct(n); }
  function renderEvents(list) {
    var box = el('event-timeline');
    if (!list || !list.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    box.innerHTML = list.map(function (ev, i) {
      var chg = [ev.change1d, ev.change3d, ev.change5d, ev.change20d];
      var labels = ['1거래일', '3거래일', '5거래일', '20거래일'];
      var rows = labels.map(function (lb, j) {
        return '<div class="kv"><span class="k">' + lb + ' 후</span><span class="v ' + signClass(chg[j]) + '">' + esc(pct(chg[j])) + '</span></div>';
      }).join('');
      return '<div class="tl-item" data-i="' + i + '">' +
        '<div class="tl-head"><span class="tl-date">' + esc(ev.eventDate || '') + '</span>' +
        '<span class="cat">' + esc(ev.category || '') + '</span>' + gradeBadge(ev.factGrade) + '</div>' +
        '<div class="tl-title">' + esc(ev.title || '') + '</div>' +
        '<div class="tl-detail">' +
          (ev.summary ? '<div class="muted" style="margin-bottom:8px">' + esc(ev.summary) + '</div>' : '') +
          '<div class="kv"><span class="k">원자료</span><span class="v">' + esc(ev.sourceName || ev.source || '') + extLink(ev.sourceUrl) + '</span></div>' +
          '<div class="kv"><span class="k">당시 주가</span><span class="v">' + esc(ev.priceAtEvent !== null && ev.priceAtEvent !== undefined ? fmtInt(ev.priceAtEvent) + '원 (' + esc(ev.priceDate || '') + ')' : '—') + '</span></div>' +
          rows +
        '</div></div>';
    }).join('');
    box.querySelectorAll('.tl-item').forEach(function (item) {
      item.addEventListener('click', function () { item.classList.toggle('open'); });
    });
  }

  /* ============ 트래커 ============ */
  var TRACK_TABS = [{ k: 'official', label: '공식확인' }, { k: 'press', label: '언론' }, { k: 'community', label: '커뮤니티 주장' }];
  var trackData = null;
  function renderTrackTabs() {
    el('track-tabs').innerHTML = TRACK_TABS.map(function (t, i) {
      return '<button type="button" class="tab" role="tab" data-k="' + t.k + '" aria-selected="' + (i === 0) + '">' + esc(t.label) + '</button>';
    }).join('');
  }
  function renderTrack(k) {
    var box = el('track-body');
    var list = trackData ? trackData[k] : null;
    if (!list || !list.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    if (k === 'community') box.innerHTML = '<div class="muted small" style="margin-bottom:8px">커뮤니티 주장은 사실이 아니며 Fact Check 큐로만 활용됩니다.</div>' + box.innerHTML;
    box.innerHTML = (k === 'community' ? '<div class="muted small" style="margin-bottom:8px">커뮤니티 주장은 사실이 아니며 Fact Check 큐로만 활용됩니다.</div>' : '') +
      list.map(function (p) {
        var detail = p.detail || '';
        var launchBadge = /연말|연초/.test(detail) && /출시|론칭/.test(detail)
          ? '<div class="launch-note"><strong>확정 일정 아님</strong></div>' : '';
        return '<div class="track-item"><div class="tl-date">' + esc(p.trackedAt || '') + ' · ' + esc(p.gameName || '') + ' · ' + esc(p.item || '') + '</div>' +
          '<div class="track-detail">' + esc(detail) + '</div>' + launchBadge +
          (p.sourceName ? '<div class="muted small">출처: ' + esc(p.sourceName) + extLink(p.sourceUrl) + '</div>' : '') + '</div>';
      }).join('');
  }

  /* ============ Fact Check ============ */
  function isUnconfirmedSensitive(fc) {
    var claimText = (fc.claimKey || '') + ' ' + (fc.claim || '');
    var topic = /(중국|판호).*(승인|발급)|거래.*종결|인수.*종결|종결.*완료/.test(claimText);
    var t = claimText + ' ' + (fc.verdict || '') + ' ' + (fc.basis || '');
    var unconfirmed = String(fc.grade || '').toUpperCase() === 'U' || /미확인/.test(t);
    return topic && unconfirmed;
  }
  function renderFactChecks(list) {
    var wrap = el('fact-list');
    if (!Array.isArray(list) || list.length === 0) { wrap.innerHTML = stateHtml(NO_DATA); return; }
    wrap.innerHTML = list.map(function (fc) {
      var notices = '';
      if (isUnconfirmedSensitive(fc)) {
        notices += '<div class="fact-notice"><strong class="unconfirmed">미확인</strong><span> — 해당 내용은 아직 확인되지 않았습니다.</span></div>';
      }
      var srcName = fc.sourceName || '';
      return '<div class="fact-card">' + gradeBadge(fc.grade) +
        '<div class="fact-claim">' + esc(fc.claim || '') + '</div>' +
        (fc.verdict ? '<div class="fact-verdict">' + esc(fc.verdict) + '</div>' : '') +
        (fc.basis ? '<div class="fact-basis">' + esc(fc.basis) + '</div>' : '') +
        ((srcName || fc.sourceUrl) ? '<div class="fact-src">출처: ' + esc(srcName) + extLink(fc.sourceUrl) + '</div>' : '') +
        notices + '</div>';
    }).join('');
  }

  /* ============ 중국 상세 ============ */
  function renderChinaDisc(list) {
    var box = el('china-disc');
    if (!list || !list.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    box.innerHTML = list.map(function (d) {
      return '<div class="china-item">' + gradeBadge(d.grade) +
        '<div class="cn">' + esc(d.titleCn || '') + '</div>' +
        (d.titleKr ? '<div class="kr">' + esc(d.titleKr) + '</div>' : '') +
        '<div class="meta">' + esc(d.secName || '') + '(' + esc(d.secCode || '') + ') · ' + esc(d.announcedAt || '') + '</div>' +
        (d.summaryKr ? '<div class="why"><strong>핵심:</strong> ' + esc(d.summaryKr) + '</div>' : '') +
        (d.whyMatters ? '<div class="why"><strong>왜 중요한가:</strong> ' + esc(d.whyMatters) + '</div>' : '') +
        (d.sourceUrl ? '<div class="meta">원문: <a href="' + esc(d.sourceUrl) + '" target="_blank" rel="noopener noreferrer">CNINFO ↗</a></div>' : '') +
        '</div>';
    }).join('');
  }
  function renderChinaNews(list) {
    var box = el('china-news');
    if (!list || !list.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    box.innerHTML = list.map(function (n) {
      return '<div class="china-item">' + gradeBadge(n.grade) +
        '<div class="cn">' + esc(n.titleCn || '') + '</div>' +
        (n.titleKr ? '<div class="kr">' + esc(n.titleKr) + '</div>' : '') +
        '<div class="meta">' + esc(n.sourceName || '') + ' · ' + esc(n.publishedAt || '') +
        (n.matchesDisclosure === true ? ' · 공시와 일치' : (n.matchesDisclosure === false ? ' · <strong>공시와 차이 있음(공시 우선)</strong>' : '')) + '</div>' +
        (n.summaryKr ? '<div class="why">' + esc(n.summaryKr) + '</div>' : '') +
        (n.whyMatters ? '<div class="why"><strong>왜 중요한가:</strong> ' + esc(n.whyMatters) + '</div>' : '') +
        extLink(n.sourceUrl) + '</div>';
    }).join('');
  }

  /* ============ 커뮤니티 ============ */
  function renderCommunity(list) {
    var box = el('community-list');
    if (!list || !list.length) {
      box.innerHTML = stateHtml('수집된 커뮤니티 글이 없습니다. 커뮤니티 글은 사실이 아니며 Fact Check 큐로만 활용됩니다.');
      return;
    }
    box.innerHTML = list.map(function (p) {
      return '<div class="track-item"><div class="tl-date">' + esc(p.postedAt || '') +
        (p.holderVerified ? ' · 보유인증' : '') + '</div>' +
        '<div class="track-detail">' + esc(p.title || p.excerpt || '') + '</div></div>';
    }).join('');
  }

  /* ============ 초기화 ============ */
  function onTabClick(containerId, attr, cb) {
    el(containerId).addEventListener('click', function (e) {
      var btn = e.target.closest('button.tab');
      if (!btn) return;
      var tabs = el(containerId).querySelectorAll('button.tab');
      for (var i = 0; i < tabs.length; i++) tabs[i].setAttribute('aria-selected', tabs[i] === btn ? 'true' : 'false');
      cb(btn.getAttribute(attr));
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    renderDday();

    // ① 히어로 + 스파크라인
    getJSON(API.overview + '?symbol=112040').then(function (o) {
      getJSON(API.prices + '?symbol=112040&days=30').then(function (rows) {
        renderHero(o, rows.slice(-30));
      }).catch(function () { renderHero(o, null); });
    }).catch(function () { renderHero(null, null); });

    // ③ 중국 카드
    getJSON(API.chinaLatest).then(renderChinaHero).catch(function () { renderChinaHero(null); });

    // ④ 수급 + 상세
    getJSON(API.flows + '?days=60').then(function (data) {
      var has = data && data.hasData;
      flowSummary = has && data.summary ? data.summary : null;
      renderFlowBars(flowSummary);
      renderFlowDetail('d1');
      if (has && data.rows && data.rows.length) {
        var last = data.rows[data.rows.length - 1];
        el('flow-asof').textContent = last.date + ' 기준';
      }
    }).catch(function () { renderFlowBars(null); renderFlowDetail('d1'); });
    renderFlowTabs();
    onTabClick('flow-tabs', 'data-k', renderFlowDetail);

    // ⑤ 오늘 중요정보 + 이벤트 타임라인
    getJSON(API.events).then(function (evs) {
      renderTodayIntel(evs);
      renderEvents(evs);
    }).catch(function () { renderTodayIntel(null); el('event-timeline').innerHTML = stateHtml('이벤트를 불러오지 못했습니다.'); });

    // 3종목 비교 + 종목 카드
    getJSON(API.compare + '?base=2026-06-30&days=120').then(renderCompare)
      .catch(function () { el('compare-legend').innerHTML = stateHtml('비교 데이터를 불러오지 못했습니다.'); });
    Promise.all([
      getJSON(API.overview + '?symbol=112040').catch(function () { return null; }),
      getJSON(API.overview + '?symbol=101730').catch(function () { return null; }),
      getJSON(API.overview + '?symbol=002517').catch(function () { return null; })
    ]).then(renderStockCards);

    // 주가차트
    renderPeriodTabs();
    onTabClick('period-tabs', 'data-days', function (d) { loadPrices(parseInt(d, 10) || 180); });
    loadPrices(180);

    // 트래커
    renderTrackTabs();
    getJSON(API.gameTracking).then(function (data) {
      trackData = data; renderTrack('official');
    }).catch(function () { el('track-body').innerHTML = stateHtml('트래커 데이터를 불러오지 못했습니다.'); });
    onTabClick('track-tabs', 'data-k', renderTrack);

    // Fact Check
    getJSON(API.factChecks).then(renderFactChecks)
      .catch(function () { el('fact-list').innerHTML = stateHtml('Fact Check 데이터를 불러오지 못했습니다.'); });

    // 중국 상세
    getJSON(API.chinaDisc).then(renderChinaDisc).catch(function () { renderChinaDisc(null); });
    getJSON(API.chinaNews).then(renderChinaNews).catch(function () { renderChinaNews(null); });

    // 커뮤니티
    getJSON(API.community).then(renderCommunity).catch(function () { renderCommunity(null); });
  });
})();
