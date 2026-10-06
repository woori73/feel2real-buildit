/* 위메이드 개인주주 분석 — 모바일 투자정보 5영역 구조 (2026-10-06 재구성) */
(function () {
  'use strict';

  var VIEWER = window.WEMADE_VIEWER || 'GUEST';
  var MEMBER = VIEWER === 'MEMBER';

  var API = {
    today: '/api/wemade/today',
    deal: '/api/wemade/deal',
    prices: '/api/wemade/prices',
    minutePrices: '/api/wemade/prices/minute',
    compare: '/api/wemade/compare',
    flows: '/api/wemade/flows',
    members: '/api/wemade/anomaly/members',
    events: '/api/wemade/events',
    factChecks: '/api/wemade/fact-checks',
    gameTracking: '/api/wemade/game-tracking',
    community: '/api/wemade/community',
    disclosures: '/api/wemade/disclosures',
    chinaDisc: '/api/wemade/china/disclosures',
    chinaNews: '/api/wemade/china/news'
  };
  var NO_DATA = '데이터 없음';

  function el(id) { return document.getElementById(id); }
  function esc(v) {
    return String(v === null || v === undefined ? '' : v)
      .replace(/[&<>"']/g, function (c) {
        return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
      });
  }
  function num(v) { var n = Number(v); return isFinite(n) ? n : null; }
  function fmtInt(v) { var n = num(v); return n === null ? null : Math.round(n).toLocaleString('ko-KR'); }
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
  function extLink(url, label) {
    if (!url || !/^https?:\/\//i.test(url)) return '';
    return ' <a href="' + esc(url) + '" target="_blank" rel="noopener noreferrer">' + esc(label || '원문 ↗') + '</a>';
  }
  function gradeBadge(g) {
    // 내부 분류(A/B/C/D/U) 코드는 노출하지 않고 쉬운 한국어만 표시한다.
    var map = { A: '공식 1차 자료', B: '복수 신뢰자료', C: '언론/업계자료', D: '계산·커뮤니티 주장', U: '미확인' };
    var t = map[String(g || 'U').toUpperCase()] || map.U;
    var cls = { A: 'grade-a', B: 'grade-b', C: 'grade-c', D: 'grade-d', U: 'grade-u' }[String(g || 'U').toUpperCase()] || 'grade-u';
    return '<span class="' + cls + '">' + esc(t) + '</span>';
  }

  /* ============ 로그아웃 ============ */
  function wireLogout() {
    var btn = el('logout-btn');
    if (!btn) return;
    btn.addEventListener('click', function () {
      fetch('/api/auth/logout', { method: 'POST' }).then(function () { location.href = '/wemade'; })
        .catch(function () { location.href = '/wemade'; });
    });
  }

  /* ============ 1. 오늘의 위메이드 ============ */
  function renderToday(t) {
    if (!t || num(t.price) === null) {
      el('today-lines').innerHTML = '<li>' + stateHtml(NO_DATA) + '</li>';
      return;
    }
    el('today-price').textContent = fmtInt(t.price) + '원';
    var chg = el('today-chg');
    chg.innerHTML = '<span class="' + signClass(t.changeRate) + '">' +
      esc(fmtSigned(t.change) + '원 (' + fmtPct(t.changeRate) + ')') + '</span>';
    el('today-vol').textContent = (fmtInt(t.volume) || '—') + '주';
    var disc = el('today-disc');
    disc.innerHTML = '<span class="down">' + esc(num(t.discountPct).toFixed(1) + '% 할인') + '</span>';
    var dday = el('today-dday');
    dday.textContent = t.dday > 0 ? 'D-' + t.dday : (t.dday === 0 ? 'D-Day' : 'D+' + Math.abs(t.dday));
    var badge = el('today-badge');
    if (badge) badge.innerHTML = t.realtime
      ? '<span class="badge-live">실시간</span>'
      : '<span class="badge-delayed">지연 시세</span>';
    el('today-lines').innerHTML = (t.lines || []).map(function (ln) {
      return '<li>' + esc(ln) + '</li>';
    }).join('');
    el('today-asof').textContent = (t.tradeDate ? t.tradeDate + ' 기준' : '') +
      ' · 인수가 68,910원 (DART rcpNo 20260630900888)';
  }

  /* ============ 2. 주가 차트 (1일/1주/1월/3개월) ============ */
  var priceChart = null, rsiChart = null;
  var PERIODS = [
    { label: '1일', k: 'day' },
    { label: '1주', k: 'week', days: 7 },
    { label: '1개월', k: 'month', days: 30 },
    { label: '3개월', k: 'q', days: 90 }
  ];
  function renderPeriodTabs() {
    el('period-tabs').innerHTML = PERIODS.map(function (p, i) {
      return '<button type="button" class="tab" role="tab" data-k="' + p.k + '" aria-selected="' + (i === 1) + '">' + esc(p.label) + '</button>';
    }).join('');
  }
  function destroyCharts() {
    if (priceChart) { priceChart.destroy(); priceChart = null; }
    if (rsiChart) { rsiChart.destroy(); rsiChart = null; }
  }
  function baseOpts(extra) {
    var o = {
      responsive: true, maintainAspectRatio: false, animation: false,
      interaction: { mode: 'index', intersect: false },
      plugins: { legend: { labels: { boxWidth: 12, font: { size: 10 } } } },
      scales: {
        x: { ticks: { maxTicksLimit: 6, font: { size: 10 } }, grid: { display: false } },
        y: { ticks: { font: { size: 10 } } }
      }
    };
    if (extra) Object.assign(o, extra);
    return o;
  }
  function loadMinuteChart() {
    el('chart-state').innerHTML = '';
    el('chart-note').textContent = '';
    getJSON(API.minutePrices + '?symbol=112040').then(function (rows) {
      destroyCharts();
      if (!rows || !rows.length) {
        el('chart-state').innerHTML = stateHtml('당일 분봉 데이터를 불러오지 못했습니다.');
        return;
      }
      var labels = rows.map(function (r) { return String(r.time || '').replace(/(\d{2})(\d{2})(\d{2})/, '$1:$2'); });
      el('chart-src').textContent = 'KIS 분봉 · ' + rows.length + '봉';
      priceChart = new Chart(el('priceChart').getContext('2d'), {
        type: 'bar',
        data: {
          labels: labels,
          datasets: [
            { type: 'line', label: '체결가', data: rows.map(function (r) { return r.price; }), borderColor: '#14213d', borderWidth: 1.5, pointRadius: 0, tension: 0.2, yAxisID: 'y' },
            { label: '거래량', data: rows.map(function (r) { return r.volume; }), backgroundColor: 'rgba(47,111,237,0.35)', yAxisID: 'y1' }
          ]
        },
        options: baseOpts({ scales: {
          x: { ticks: { maxTicksLimit: 8, font: { size: 10 } }, grid: { display: false } },
          y: { ticks: { font: { size: 10 } } },
          y1: { position: 'right', ticks: { font: { size: 9 } }, grid: { display: false } }
        }})
      });
      el('rsiChart').closest('.chart-box').style.display = 'none';
      el('chart-note').textContent = '당일 분봉 기준. 장 시간 외에는 데이터가 없을 수 있습니다.';
    }).catch(function () {
      destroyCharts();
      el('chart-state').innerHTML = stateHtml('분봉 데이터를 불러오지 못했습니다.');
    });
  }
  function loadDailyChart(days) {
    el('chart-state').innerHTML = '';
    el('chart-note').textContent = '';
    el('rsiChart').closest('.chart-box').style.display = '';
    getJSON(API.prices + '?symbol=112040&days=' + days).then(function (rows) {
      destroyCharts();
      if (!rows || !rows.length) {
        el('chart-state').innerHTML = stateHtml(NO_DATA);
        return;
      }
      var labels = rows.map(function (r) { return r.date; });
      el('chart-src').textContent = '일봉 · ' + rows.length + '거래일';
      priceChart = new Chart(el('priceChart').getContext('2d'), {
        type: 'bar',
        data: {
          labels: labels,
          datasets: [
            { type: 'line', label: '종가', data: rows.map(function (r) { return r.close; }), borderColor: '#14213d', borderWidth: 2, pointRadius: 0, tension: 0.2, yAxisID: 'y' },
            { type: 'line', label: 'MA5', data: rows.map(function (r) { return r.ma5; }), borderColor: '#e5484d', borderWidth: 1.2, pointRadius: 0, tension: 0.2, spanGaps: true, yAxisID: 'y' },
            { type: 'line', label: 'MA20', data: rows.map(function (r) { return r.ma20; }), borderColor: '#2f6fed', borderWidth: 1.2, pointRadius: 0, tension: 0.2, spanGaps: true, yAxisID: 'y' },
            { label: '거래량', data: rows.map(function (r) { return r.volume; }), backgroundColor: 'rgba(47,111,237,0.25)', yAxisID: 'y1' }
          ]
        },
        options: baseOpts({ scales: {
          x: { ticks: { maxTicksLimit: 6, font: { size: 10 } }, grid: { display: false } },
          y: { ticks: { font: { size: 10 } } },
          y1: { position: 'right', ticks: { font: { size: 9 } }, grid: { display: false } }
        }})
      });
      rsiChart = new Chart(el('rsiChart').getContext('2d'), {
        type: 'line',
        data: { labels: labels, datasets: [{ label: 'RSI14', data: rows.map(function (r) { return r.rsi14; }), borderColor: '#b7791f', borderWidth: 1.5, pointRadius: 0, tension: 0.2, spanGaps: true }] },
        options: { responsive: true, maintainAspectRatio: false, animation: false, plugins: { legend: { display: false } }, scales: { x: { display: false }, y: { min: 0, max: 100, ticks: { font: { size: 9 } } } } }
      });
    }).catch(function () {
      destroyCharts();
      el('chart-state').innerHTML = stateHtml('차트 데이터를 불러오지 못했습니다.');
    });
  }
  function onPeriod(k) {
    var p = PERIODS.filter(function (x) { return x.k === k; })[0];
    if (!p) return;
    if (p.k === 'day') loadMinuteChart();
    else loadDailyChart(p.days);
  }

  /* ============ 2. 투자자 수급 ============ */
  var INVESTORS = [
    { key: 'foreign', label: '외국인' },
    { key: 'institution', label: '기관' },
    { key: 'retail', label: '개인' }
  ];
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
    var max = 1;
    INVESTORS.forEach(function (inv) { var v = num(d[inv.key]); if (v !== null) max = Math.max(max, Math.abs(v)); });
    box.innerHTML = INVESTORS.map(function (inv) {
      var v = num(d[inv.key]);
      if (v === null) return '<div class="flow-row"><span>' + esc(inv.label) + '</span><span></span><span class="amt muted">' + NO_DATA + '</span></div>';
      var w = Math.max(4, Math.abs(v) / max * 100);
      var fill = v >= 0
        ? '<span class="bar-fill pos" style="left:50%;width:' + (w / 2).toFixed(1) + '%"></span>'
        : '<span class="bar-fill neg" style="right:50%;width:' + (w / 2).toFixed(1) + '%"></span>';
      return '<div class="flow-row"><span>' + esc(inv.label) + '</span>' +
        '<span class="bar-track">' + fill + '</span>' +
        '<span class="amt ' + signClass(v) + '">' + esc(fmtSigned(v)) + '</span></div>';
    }).join('');
  }

  /* ============ 2. 창구 (회원사 순매수/순매도 상위) ============ */
  function renderBrokers(list) {
    var box = el('broker-lists');
    if (!list || !list.length) {
      box.innerHTML = stateHtml('창구 집계 데이터를 불러오지 못했습니다.');
      return;
    }
    // 최신 측정 시각의 스냅샷만 사용
    var latest = list.reduce(function (a, b) { return (a.measuredAt > b.measuredAt ? a : b); });
    var snap = list.filter(function (s) { return s.measuredAt === latest.measuredAt && s.brokerName; });
    el('broker-asof').textContent = String(latest.measuredAt || '').replace('T', ' ').slice(0, 16) + ' 기준';
    var byBroker = {};
    snap.forEach(function (s) { byBroker[s.brokerName] = s; });
    var arr = Object.keys(byBroker).map(function (k) { return byBroker[k]; });
    var buys = arr.filter(function (s) { return num(s.netVolume) > 0; })
      .sort(function (a, b) { return b.netVolume - a.netVolume; }).slice(0, 5);
    var sells = arr.filter(function (s) { return num(s.netVolume) < 0; })
      .sort(function (a, b) { return a.netVolume - b.netVolume; }).slice(0, 5);
    function rows(rs, cls) {
      if (!rs.length) return '<div class="muted small">해당 없음</div>';
      return rs.map(function (s) {
        return '<div class="kv"><span class="k">' + esc(s.brokerName) + '</span>' +
          '<span class="v ' + cls + '">' + esc((num(s.netVolume) > 0 ? '+' : '') + fmtInt(s.netVolume)) + '주</span></div>';
      }).join('');
    }
    box.innerHTML =
      '<div class="broker-cols">' +
      '<div><h4 class="sub-title up">순매수 상위</h4>' + rows(buys, 'up') + '</div>' +
      '<div><h4 class="sub-title down">순매도 상위</h4>' + rows(sells, 'down') + '</div>' +
      '</div>';
  }

  /* ============ 2. 3종목 비교 ============ */
  var compareChart = null;
  function renderCompare(data) {
    var box = el('compareChart');
    if (typeof Chart === 'undefined') return;
    if (compareChart) { compareChart.destroy(); compareChart = null; }
    var series = (data && data.series) || [];
    var colors = { '112040': '#e5484d', '101730': '#2f6fed', '002517': '#7c5cff' };
    var labelSet = {};
    series.forEach(function (s) { (s.points || []).forEach(function (p) { labelSet[p.date] = 1; }); });
    var labels = Object.keys(labelSet).sort();
    var actives = series.filter(function (s) { return s.hasData; });
    var datasets = actives.map(function (s) {
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
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { maxTicksLimit: 6, font: { size: 10 } }, grid: { display: false } },
          y: { ticks: { font: { size: 10 } }, title: { display: true, text: '기준일=100', font: { size: 10 } } }
        }
      }
    });
    el('compare-legend').innerHTML = datasets.map(function (ds, i) {
      var key = actives[i].symbol;
      return '<span><span class="sw" style="background:' + (colors[key] || '#888') + '"></span>' + esc(ds.label) + '</span>';
    }).join('') + '<div class="muted small" style="width:100%;margin-top:4px">이벤트: 2026-06-29 중국 판호 · 2026-06-30 SPA 체결 · 2026-09-12 킹넷 공시</div>';
  }

  /* ============ 3. 68,910원 인수 ============ */
  var todayData = null;
  function renderDeal(d) {
    if (!d) return;
    function showMultiple(t) {
      if (t && num(t.price) !== null && num(d.perShare) !== null) {
        var m = num(d.perShare) / num(t.price);
        el('deal-multiple').innerHTML = '<span class="big">약 ' + m.toFixed(1) + '배</span>' +
          '<span class="cap">공시 1주당 68,910원에 경영권 매각</span>';
      }
    }
    if (todayData) showMultiple(todayData);
    else getJSON(API.today + '?symbol=112040').then(showMultiple).catch(function () { /* 배율 생략 */ });
    var tl = el('deal-timeline');
    tl.innerHTML = (d.timeline || []).map(function (s) {
      return '<li class="' + esc(s.status) + '"><span class="dot"></span><div><strong>' +
        esc(s.title) + '</strong><span>' + esc(s.sub) + '</span></div></li>';
    }).join('');
  }

  /* ============ ④ 앞으로의 핵심 일정 ============ */
  function renderUpcoming(deal, catalystEvents, ncwOfficial) {
    var box = el('upcoming-list');
    var cards = [];
    // 인수 잔금/종결
    if (deal) {
      cards.push({
        date: deal.closeDate + ' 예정',
        title: '인수 잔금 8,280억원 · 거래 종결',
        status: deal.dday > 0 ? 'D-' + deal.dday : (deal.dday === 0 ? 'D-Day' : '일정 경과'),
        why: '잔금 지급과 함께 최대주주가 NeoPulse로 변경됩니다. 중국 관련 승인·신고 등 선행조건 충족 여부가 관건입니다.'
      });
    }
    // 촉매 이벤트 (인수 잔금·NCW는 전용 카드가 있으므로 제외)
    (catalystEvents || []).forEach(function (e) {
      if (e.catalystKey === 'DEAL_CLOSE' || e.catalystKey === 'NCW_LAUNCH') return;
      cards.push({
        date: e.dday || e.targetLabel || '일정 미정',
        title: e.nameKo || '',
        status: e.status === 'UNCONFIRMED' ? '미확정' : '진행 중',
        why: e.summary || ''
      });
    });
    // Night Crows W 출시 일정
    cards.push({
      date: '일정 미정',
      title: 'Night Crows W 출시',
      status: '추적 중',
      why: '중국 판호(夜鸦, 2026-06-29)는 발급 완료. 출시일은 확정 아님 — 연말·연초 출시는 커뮤니티·언론 언급 수준.'
    });
    if (!cards.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    box.innerHTML = cards.map(function (c) {
      return '<div class="event-card">' +
        '<div class="tl-date">' + esc(c.date) + ' · <strong>' + esc(c.status) + '</strong></div>' +
        '<div class="tl-title">' + esc(c.title) + '</div>' +
        (c.why ? '<div class="event-why">' + esc(c.why) + '</div>' : '') +
        '</div>';
    }).join('');
  }

  /* ============ ⑤ 최신 핵심 공시 · 뉴스 ============ */
  function renderHeadlines(dartList, chinaLatest) {
    var box = el('headline-list');
    var html = '';
    (dartList || []).slice(0, 2).forEach(function (d) {
      html += '<div class="doc-item"><div class="tl-date">' + esc(d.disclosedAt || '') + ' · DART</div>' +
        '<div class="track-detail"><strong>' + esc(d.title || '') + '</strong></div>' +
        (d.sourceUrl ? '<div class="muted small"><a href="' + esc(d.sourceUrl) + '" target="_blank" rel="noopener noreferrer">원문 ↗</a></div>' : '') + '</div>';
    });
    var dc = chinaLatest && chinaLatest.disclosure, nw = chinaLatest && chinaLatest.news;
    if (dc) {
      html += '<div class="doc-item"><div class="tl-date">' + esc(dc.announcedAt || '') + ' · 중국 공식 공시 ' + gradeBadge(dc.grade) + '</div>' +
        '<div class="track-detail"><strong>' + esc(dc.titleKr || dc.titleCn || '') + '</strong></div>' +
        (dc.summaryKr ? '<div class="why">' + esc(dc.summaryKr) + '</div>' : '') +
        (dc.hasDetail ? '<div class="meta"><a class="detail-link" href="/wemade/china/disclosures/' + dc.id + '">한국어로 자세히 보기 →</a></div>' : '') + '</div>';
    }
    if (nw) {
      html += '<div class="doc-item"><div class="tl-date">' + esc(nw.publishedAt || '') + ' · 중국 뉴스 ' + gradeBadge(nw.grade) + '</div>' +
        '<div class="track-detail"><strong>' + esc(nw.titleKr || nw.titleCn || '') + '</strong></div>' +
        (nw.summaryKr ? '<div class="why">' + esc(nw.summaryKr) + '</div>' : '') +
        (nw.hasDetail ? '<div class="meta"><a class="detail-link" href="/wemade/china/news/' + nw.id + '">한국어로 자세히 보기 →</a></div>' : '') + '</div>';
    }
    box.innerHTML = html || stateHtml(NO_DATA);
  }

  /* ============ ④ 사건 타임라인 카드 (자료실: 과거 주요 사건) ============ */
  function renderEventCards(list) {
    var box = el('event-cards');
    if (!list || !list.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    box.innerHTML = list.map(function (ev) {
      return '<div class="event-card">' +
        '<div class="tl-head"><span class="tl-date">' + esc(ev.eventDate || '') + '</span>' +
        '<span class="cat">' + esc(ev.category || '') + '</span>' + gradeBadge(ev.factGrade) + '</div>' +
        '<div class="tl-title">무슨 일인가: ' + esc(ev.title || '') + '</div>' +
        (ev.summary ? '<div class="event-why"><strong>주주에게 왜 중요한가:</strong> ' + esc(ev.summary) + '</div>' : '') +
        '<div class="event-src">근거자료: ' + esc(ev.sourceName || ev.source || '') + extLink(ev.sourceUrl) + '</div>' +
        '</div>';
    }).join('');
  }

  /* ============ 4. 트래커 ============ */
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

  /* ============ 4. Fact Check ============ */
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

  /* ============ 5. 자료실 ============ */
  function renderDart(list, expanded) {
    var box = el('dart-list');
    if (!list || !list.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    var show = expanded ? list : list.slice(0, 3);
    box.innerHTML = show.map(function (d) {
      return '<div class="doc-item"><div class="tl-date">' + esc(d.disclosedAt || '') + '</div>' +
        '<div class="track-detail">' + esc(d.title || '') + '</div>' +
        '<div class="muted small">' + (d.rcpNo ? 'DART rcpNo ' + esc(d.rcpNo) + ' · ' : '') +
        (d.sourceUrl ? '<a href="' + esc(d.sourceUrl) + '" target="_blank" rel="noopener noreferrer">원문 ↗</a>' : '') + '</div></div>';
    }).join('');
    var btn = el('dart-more');
    if (list.length > 3) {
      btn.hidden = false;
      btn.textContent = expanded ? '접기' : '전체 보기 (' + list.length + '건)';
    } else btn.hidden = true;
  }
  function renderChinaDisc(list, expanded) {
    var box = el('china-disc');
    if (!list || !list.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    var show = expanded ? list : list.slice(0, 3);
    box.innerHTML = show.map(function (d) {
      return '<div class="china-item">' + gradeBadge(d.grade) +
        '<div class="cn">' + esc(d.titleCn || '') + '</div>' +
        (d.titleKr ? '<div class="kr">' + esc(d.titleKr) + '</div>' : '') +
        '<div class="meta">' + esc(d.secName || '') + '(' + esc(d.secCode || '') + ') · ' + esc(d.announcedAt || '') + '</div>' +
        (d.summaryKr ? '<div class="why"><strong>핵심:</strong> ' + esc(d.summaryKr) + '</div>' : '') +
        (d.hasDetail ? '<div class="meta"><a class="detail-link" href="/wemade/china/disclosures/' + d.id + '">한국어로 자세히 보기 →</a></div>' : '') +
        (d.sourceUrl ? '<div class="meta"><a href="' + esc(d.sourceUrl) + '" target="_blank" rel="noopener noreferrer">원문 ↗</a></div>' : '') +
        '</div>';
    }).join('');
  }
  function renderChinaNews(list, expanded) {
    var box = el('china-news');
    if (!list || !list.length) { box.innerHTML = stateHtml(NO_DATA); return; }
    var show = expanded ? list : list.slice(0, 3);
    box.innerHTML = show.map(function (n) {
      return '<div class="china-item">' + gradeBadge(n.grade) +
        '<div class="cn">' + esc(n.titleCn || '') + '</div>' +
        (n.titleKr ? '<div class="kr">' + esc(n.titleKr) + '</div>' : '') +
        '<div class="meta">' + esc(n.sourceName || '') + ' · ' + esc(n.publishedAt || '') +
        (n.matchesDisclosure === true ? ' · 공시와 일치' : (n.matchesDisclosure === false ? ' · <strong>공시와 차이 있음(공시 우선)</strong>' : '')) + '</div>' +
        (n.summaryKr ? '<div class="why">' + esc(n.summaryKr) + '</div>' : '') +
        (n.hasDetail ? '<div class="meta"><a class="detail-link" href="/wemade/china/news/' + n.id + '">한국어로 자세히 보기 →</a></div>' : '') +
        extLink(n.sourceUrl) + '</div>';
    }).join('');
  }
  function renderCommunity(list, expanded) {
    var box = el('community-list');
    if (!list || !list.length) {
      box.innerHTML = stateHtml('수집된 커뮤니티 글이 없습니다.');
      el('community-more').hidden = true;
      return;
    }
    var show = expanded ? list : list.slice(0, 3);
    box.innerHTML = show.map(function (p) {
      return '<div class="track-item"><div class="tl-date">' + esc(p.postedAt || '') +
        (p.holderVerified ? ' · 보유인증' : '') + '</div>' +
        '<div class="track-detail">' + esc(p.title || p.excerpt || '') + '</div></div>';
    }).join('');
    var btn = el('community-more');
    if (list.length > 3) {
      btn.hidden = false;
      btn.textContent = expanded ? '접기' : '전체 보기 (' + list.length + '건)';
    } else btn.hidden = true;
  }

  /* ============ 탭 공용 ============ */
  function onTabClick(containerId, attr, cb) {
    var c = el(containerId);
    if (!c) return;
    c.addEventListener('click', function (e) {
      var btn = e.target.closest('button.tab');
      if (!btn) return;
      var tabs = c.querySelectorAll('button.tab');
      for (var i = 0; i < tabs.length; i++) tabs[i].setAttribute('aria-selected', tabs[i] === btn ? 'true' : 'false');
      cb(btn.getAttribute(attr));
    });
  }

  /* ============ 초기화 ============ */
  document.addEventListener('DOMContentLoaded', function () {
    wireLogout();

    // 1. 오늘의 위메이드 (티저, 비회원 공개)
    getJSON(API.today + '?symbol=112040')
      .then(function (t) { todayData = t; renderToday(t); })
      .catch(function () { renderToday(null); });

    if (!MEMBER) return;

    // 2. 주가 차트
    renderPeriodTabs();
    onTabClick('period-tabs', 'data-k', onPeriod);
    if (typeof Chart === 'undefined') {
      el('chart-state').innerHTML = stateHtml('차트 라이브러리를 불러오지 못했습니다.');
    } else {
      onPeriod('week');
    }

    // 2. 투자자 수급
    renderFlowTabs();
    getJSON(API.flows + '?days=60').then(function (data) {
      var has = data && data.hasData;
      flowSummary = has && data.summary ? data.summary : null;
      renderFlowDetail('d1');
      if (has && data.rows && data.rows.length) {
        el('flow-asof').textContent = data.rows[data.rows.length - 1].date + ' 기준';
      }
    }).catch(function () { renderFlowDetail('d1'); });
    onTabClick('flow-tabs', 'data-k', renderFlowDetail);

    // 2. 창구
    getJSON(API.members + '?symbol=112040&days=1').then(renderBrokers)
      .catch(function () { renderBrokers(null); });

    // 2. 3종목 비교
    getJSON(API.compare + '?base=2026-06-30&days=90').then(renderCompare)
      .catch(function () { el('compare-legend').innerHTML = stateHtml('비교 데이터를 불러오지 못했습니다.'); });

    // 3. 인수 + ④ 앞으로의 핵심 일정 (deal 데이터 공유)
    Promise.all([
      getJSON(API.deal).catch(function () { return null; }),
      getJSON('/api/wemade/catalyst/events').catch(function () { return []; }),
      getJSON(API.gameTracking).catch(function () { return null; })
    ]).then(function (rs) {
      renderDeal(rs[0]);
      renderUpcoming(rs[0], rs[1], rs[2] && rs[2].official);
      trackData = rs[2]; renderTrack('official');
    }).catch(function () {
      renderDeal(null);
      el('upcoming-list').innerHTML = stateHtml('일정을 불러오지 못했습니다.');
    });
    renderTrackTabs();
    onTabClick('track-tabs', 'data-k', renderTrack);

    // ⑤ 최신 핵심 공시 · 뉴스
    Promise.all([
      getJSON(API.disclosures).catch(function () { return []; }),
      getJSON('/api/wemade/china/latest').catch(function () { return null; })
    ]).then(function (rs) { renderHeadlines(rs[0], rs[1]); })
      .catch(function () { el('headline-list').innerHTML = stateHtml('공시·뉴스를 불러오지 못했습니다.'); });

    // ⑥ 자료실: 과거 주요 사건 + Fact Check
    getJSON(API.events).then(renderEventCards)
      .catch(function () { el('event-cards').innerHTML = stateHtml('이벤트를 불러오지 못했습니다.'); });
    getJSON(API.factChecks).then(renderFactChecks)
      .catch(function () { el('fact-list').innerHTML = stateHtml('Fact Check 데이터를 불러오지 못했습니다.'); });

    // ⑥ 자료실: DART / 중국 / 커뮤니티 (전체)
    var dartData = null, dartExp = false;
    getJSON(API.disclosures).then(function (list) {
      dartData = list || [];
      renderDart(dartData, false);
    }).catch(function () { renderDart(null, false); });
    el('dart-more').addEventListener('click', function () {
      dartExp = !dartExp;
      renderDart(dartData, dartExp);
    });

    var chinaD = null, chinaN = null, chinaExp = false;
    function renderChinaAll() {
      renderChinaDisc(chinaD, chinaExp);
      renderChinaNews(chinaN, chinaExp);
      var btn = el('china-more');
      var total = (chinaD || []).length + (chinaN || []).length;
      if (total > 6) {
        btn.hidden = false;
        btn.textContent = chinaExp ? '접기' : '전체 보기 (' + total + '건)';
      } else btn.hidden = true;
    }
    getJSON(API.chinaDisc).then(function (d) { chinaD = d || []; renderChinaAll(); })
      .catch(function () { renderChinaDisc(null, false); });
    getJSON(API.chinaNews).then(function (n) { chinaN = n || []; renderChinaAll(); })
      .catch(function () { renderChinaNews(null, false); });
    el('china-more').addEventListener('click', function () {
      chinaExp = !chinaExp;
      renderChinaAll();
    });

    var commData = null, commExp = false;
    getJSON(API.community).then(function (list) {
      commData = list || [];
      renderCommunity(commData, false);
    }).catch(function () { renderCommunity(null, false); });
    el('community-more').addEventListener('click', function () {
      commExp = !commExp;
      renderCommunity(commData, commExp);
    });
  });
})();
