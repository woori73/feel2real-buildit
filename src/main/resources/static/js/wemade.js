'use strict';
/* 위메이드(112040) 개인주주 분석 — 바닐라 JS (외부 프레임워크 없음)
 * XSS 방지: 서버에서 받은 모든 데이터는 esc()로 이스케이프한 뒤 DOM에 삽입한다.
 * URL은 http/https 스킴만 허용한다. */
(function () {
  'use strict';

  /* ================= 상수 ================= */
  var API = {
    overview: '/api/wemade/overview',
    prices: function (days) { return '/api/wemade/prices?days=' + encodeURIComponent(days); },
    flows: '/api/wemade/flows?days=60',
    events: '/api/wemade/events',
    factChecks: '/api/wemade/fact-checks',
    tracking: '/api/wemade/game-tracking',
    community: '/api/wemade/community'
  };

  /* 기간 선택: 1개월/3개월/6개월/1년/전체 (전체는 days=3650으로 전체 조회) */
  var PERIODS = [
    { label: '1개월', days: 30 },
    { label: '3개월', days: 90 },
    { label: '6개월', days: 180 },
    { label: '1년', days: 365 },
    { label: '전체', days: 3650 }
  ];
  var DEFAULT_PERIOD = 180;

  var FLOW_TABS = [
    { key: 'd1', label: '오늘' },
    { key: 'd5', label: '5일' },
    { key: 'd20', label: '20일' },
    { key: 'd60', label: '60일' }
  ];

  var INVESTORS = [
    { key: 'foreign', label: '외국인' },
    { key: 'institution', label: '기관' },
    { key: 'retail', label: '개인' }
  ];

  var TRACK_TABS = [
    { key: 'official', label: '공식확인' },
    { key: 'press', label: '언론' },
    { key: 'community', label: '커뮤니티 주장' }
  ];

  var GRADES = {
    A: '공식 1차 자료',
    B: '복수 신뢰자료',
    C: '언론/업계자료',
    D: '계산·커뮤니티 주장',
    U: '미확인'
  };

  var NO_DATA = '데이터 없음';

  /* ================= 유틸 ================= */
  function el(id) { return document.getElementById(id); }

  function esc(v) {
    return String(v === null || v === undefined ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function safeUrl(u) {
    if (typeof u !== 'string') return null;
    var t = u.trim();
    return /^https?:\/\//i.test(t) ? t : null;
  }

  function num(v) {
    if (v === null || v === undefined || v === '') return null;
    var n = Number(v);
    return isFinite(n) ? n : null;
  }

  function fmtInt(v) {
    var n = num(v);
    return n === null ? null : Math.round(n).toLocaleString('ko-KR');
  }

  function fmtSigned(v) {
    var n = num(v);
    if (n === null) return null;
    var s = n > 0 ? '+' : (n < 0 ? '-' : '');
    return s + Math.abs(Math.round(n)).toLocaleString('ko-KR');
  }

  function fmtPct(v, digits) {
    var n = num(v);
    if (n === null) return null;
    var d = (digits === undefined) ? 2 : digits;
    var s = n > 0 ? '+' : '';
    return s + n.toFixed(d) + '%';
  }

  function shortDate(iso) {
    if (typeof iso !== 'string') return '';
    var m = iso.match(/(\d{4})-(\d{2})-(\d{2})/);
    return m ? m[2] + '/' + m[3] : iso;
  }

  function signClass(v) {
    var n = num(v);
    if (n === null) return '';
    return n > 0 ? 'up' : (n < 0 ? 'down' : 'flat');
  }

  function stateHtml(msg) {
    return '<div class="state">' + esc(msg) + '</div>';
  }

  function gradeBadge(g) {
    var key = String(g || 'U').toUpperCase();
    if (!GRADES[key]) key = 'U';
    return '<span class="grade grade-' + esc(key) + '">' + esc(key) + ' · ' + esc(GRADES[key]) + '</span>';
  }

  function extLink(url, label) {
    var u = safeUrl(url);
    if (!u) return '';
    return ' <a href="' + esc(u) + '" target="_blank" rel="noopener noreferrer">' + esc(label || '원문 보기') + '</a>';
  }

  function getJSON(url) {
    return fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status + ' ' + url);
        return res.json();
      });
  }

  /* ================= 2. 10초 요약 ================= */
  function renderSummaryPrice(o) {
    var box = el('sum-price');
    if (!o || !o.hasData || num(o.close) === null) {
      box.innerHTML = '<div class="sum-label">현재가 / 등락률</div>' + stateHtml(NO_DATA);
      return;
    }
    var base = (o.changeRate !== null && o.changeRate !== undefined) ? o.changeRate : o.change;
    var cls = signClass(base);
    var chg = fmtSigned(o.change);
    var rate = fmtPct(o.changeRate);
    var chgText = (chg !== null && rate !== null)
      ? chg + '원 (' + rate + ')'
      : (rate !== null ? rate : (chg !== null ? chg + '원' : '—'));
    box.innerHTML =
      '<div class="sum-label">현재가 / 등락률' +
      (o.tradeDate ? ' <span class="sum-date">' + esc(o.tradeDate) + ' 기준</span>' : '') +
      '</div>' +
      '<div class="price-row"><span class="price">' + esc(fmtInt(o.close)) + '원</span>' +
      '<span class="chg-pill ' + cls + '">' + esc(chgText) + '</span></div>';
  }

  function renderSummaryFlow(summary) {
    var box = el('sum-flow');
    var d1 = summary ? summary.d1 : null;
    if (!d1) {
      box.innerHTML = '<div class="sum-label">오늘 수급</div>' + stateHtml(NO_DATA);
      return;
    }
    var rows = INVESTORS.map(function (inv) {
      var v = num(d1[inv.key]);
      var txt = v === null ? NO_DATA : fmtSigned(v) + ' 백만원';
      return '<div class="flow-row"><span>' + esc(inv.label) + '</span>' +
        '<span class="' + signClass(v) + '">' + esc(txt) + '</span></div>';
    }).join('');
    box.innerHTML = '<div class="sum-label">오늘 수급</div>' + rows;
  }

  /* 공시 기반: 1주당 68,910원(DART rcpNo 20260630900888)에 경영권을 넘김.
   * 현재가 대비 몇 배인지 첫 화면 핵심 카드에 근거를 갖춰 표시한다. */
  function renderSummaryDeal(o) {
    var box = el('sum-deal');
    if (!box) return;
    var close = o && o.hasData ? num(o.close) : null;
    if (close === null || close <= 0) {
      box.innerHTML = '<div class="sum-label">경영권 매각가 vs 현재가</div>' + stateHtml(NO_DATA);
      return;
    }
    var multiple = 68910 / close;
    var mText = '약 ' + multiple.toFixed(1) + '배';
    box.innerHTML =
      '<div class="sum-label">경영권 매각가 vs 현재가</div>' +
      '<div class=\"sum-value\">' + esc(mText) + '</div>' +
      '<div class=\"sum-sub\">공시 1주당 68,910원 · 현재가 ' + esc(fmtInt(close)) + '원<br>' +
      'DART rcpNo 20260630900888</div>';
  }

  function loadOverview() {
    getJSON(API.overview).then(function (o) {
      renderSummaryPrice(o);
      renderSummaryDeal(o);
      var hi = o ? fmtInt(o.high52w) : null;
      var lo = o ? fmtInt(o.low52w) : null;
      el('fiftytwo').textContent = (hi && lo) ? ('52주 최고 ' + hi + '원 · 최저 ' + lo + '원') : '';
    }).catch(function () {
      renderSummaryPrice(null);
      renderSummaryDeal(null);
    });
  }

  /* ================= 3. 주가차트 ================= */
  var priceChart = null;
  var rsiChart = null;

  function destroyCharts() {
    if (priceChart) { priceChart.destroy(); priceChart = null; }
    if (rsiChart) { rsiChart.destroy(); rsiChart = null; }
  }

  function renderPeriodTabs() {
    var wrap = el('period-tabs');
    wrap.innerHTML = PERIODS.map(function (p) {
      var active = p.days === DEFAULT_PERIOD ? 'true' : 'false';
      return '<button type="button" class="tab" role="tab" data-days="' + p.days +
        '" aria-selected="' + active + '">' + esc(p.label) + '</button>';
    }).join('');
  }

  function onPeriodClick(e) {
    var btn = e.target.closest('button.tab');
    if (!btn) return;
    var days = parseInt(btn.getAttribute('data-days'), 10);
    if (!isFinite(days)) return;
    var wrap = el('period-tabs');
    var tabs = wrap.querySelectorAll('.tab');
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].setAttribute('aria-selected', tabs[i] === btn ? 'true' : 'false');
    }
    loadPrices(days);
  }

  function setChartState(msg) {
    /* msg가 null이면 차트 표시, 문자열이면 상태 메시지만 표시 */
    var area = el('chart-area');
    var state = el('chart-state');
    if (msg === null) {
      area.style.display = '';
      state.innerHTML = '';
    } else {
      destroyCharts();
      area.style.display = 'none';
      state.innerHTML = stateHtml(msg);
    }
  }

  function loadPrices(days) {
    if (typeof Chart === 'undefined') {
      setChartState('차트 라이브러리를 불러오지 못했습니다.');
      return;
    }
    el('chart-state').innerHTML = stateHtml('불러오는 중…');
    getJSON(API.prices(days)).then(function (rows) {
      if (!Array.isArray(rows) || rows.length === 0) {
        setChartState(NO_DATA);
        return;
      }
      setChartState(null);
      renderCharts(rows);
    }).catch(function () {
      setChartState('주가 데이터를 불러오지 못했습니다.');
    });
  }

  function renderCharts(rows) {
    destroyCharts();
    var labels = rows.map(function (r) { return shortDate(r.date); });
    function series(key) {
      return rows.map(function (r) {
        var v = num(r[key]);
        return v;
      });
    }

    priceChart = new Chart(el('priceChart'), {
      type: 'line',
      data: {
        labels: labels,
        datasets: [
          { label: '종가', data: series('close'), borderColor: '#191f28', backgroundColor: 'rgba(25,31,40,0.06)', fill: true, borderWidth: 2, pointRadius: 0, tension: 0.1 },
          { label: 'MA5', data: series('ma5'), borderColor: '#e8890c', borderWidth: 1, pointRadius: 0, tension: 0.1 },
          { label: 'MA20', data: series('ma20'), borderColor: '#1f6feb', borderWidth: 1, pointRadius: 0, tension: 0.1 },
          { label: 'MA60', data: series('ma60'), borderColor: '#8250df', borderWidth: 1, pointRadius: 0, tension: 0.1 },
          { label: 'MA120', data: series('ma120'), borderColor: '#1a7f37', borderWidth: 1, pointRadius: 0, tension: 0.1 },
          { label: '거래량', data: series('volume'), type: 'bar', yAxisID: 'y1', backgroundColor: 'rgba(31,111,235,0.22)', borderWidth: 0 }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { position: 'top', labels: { boxWidth: 10, boxHeight: 10, font: { size: 10 }, padding: 10 } },
          tooltip: {
            callbacks: {
              label: function (ctx) {
                var v = ctx.parsed.y;
                if (v === null || v === undefined) return ' ' + ctx.dataset.label + ': -';
                if (ctx.dataset.yAxisID === 'y1') return ' ' + ctx.dataset.label + ': ' + fmtInt(v);
                return ' ' + ctx.dataset.label + ': ' + fmtInt(v) + '원';
              }
            }
          }
        },
        scales: {
          x: {
            ticks: { maxTicksLimit: 6, font: { size: 10 } },
            grid: { display: false }
          },
          y: {
            position: 'left',
            ticks: {
              font: { size: 10 },
              callback: function (v) {
                return v >= 10000 ? (v / 10000).toFixed(1).replace(/\.0$/, '') + '만' : v;
              }
            },
            grid: { color: '#eef1f4' }
          },
          y1: { position: 'right', display: false, grid: { display: false } }
        }
      }
    });

    var rsi = series('rsi14');
    var hasRsi = rsi.some(function (v) { return v !== null; });
    var rsiBox = el('rsiChart').closest('.chart-box');
    var rsiSub = rsiBox ? rsiBox.previousElementSibling : null;
    if (!hasRsi) {
      if (rsiBox) rsiBox.style.display = 'none';
      if (rsiSub && rsiSub.classList.contains('chart-sub')) rsiSub.style.display = 'none';
      return;
    }
    if (rsiBox) rsiBox.style.display = '';
    if (rsiSub && rsiSub.classList.contains('chart-sub')) rsiSub.style.display = '';

    function constLine(v) {
      return rows.map(function () { return v; });
    }
    rsiChart = new Chart(el('rsiChart'), {
      type: 'line',
      data: {
        labels: labels,
        datasets: [
          { label: 'RSI14', data: rsi, borderColor: '#8250df', borderWidth: 1.5, pointRadius: 0, tension: 0.1 },
          { label: '70', data: constLine(70), borderColor: '#d92d20', borderDash: [4, 4], borderWidth: 1, pointRadius: 0 },
          { label: '30', data: constLine(30), borderColor: '#2470e0', borderDash: [4, 4], borderWidth: 1, pointRadius: 0 }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: function (ctx) {
                var v = ctx.parsed.y;
                return ' ' + ctx.dataset.label + ': ' + (v === null || v === undefined ? '-' : Number(v).toFixed(1));
              }
            }
          }
        },
        scales: {
          x: { ticks: { maxTicksLimit: 6, font: { size: 10 } }, grid: { display: false } },
          y: { min: 0, max: 100, ticks: { font: { size: 10 } }, grid: { color: '#eef1f4' } }
        }
      }
    });
  }

  /* ================= 4. 수급 ================= */
  var flowSummary = null;

  function renderFlowTabs() {
    var wrap = el('flow-tabs');
    wrap.innerHTML = FLOW_TABS.map(function (t, i) {
      return '<button type="button" class="tab" role="tab" data-key="' + esc(t.key) +
        '" aria-selected="' + (i === 0 ? 'true' : 'false') + '">' + esc(t.label) + '</button>';
    }).join('');
  }

  function onFlowTabClick(e) {
    var btn = e.target.closest('button.tab');
    if (!btn) return;
    var key = btn.getAttribute('data-key');
    var wrap = el('flow-tabs');
    var tabs = wrap.querySelectorAll('.tab');
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].setAttribute('aria-selected', tabs[i] === btn ? 'true' : 'false');
    }
    renderFlowBody(key);
  }

  function renderFlowBody(key) {
    var body = el('flow-body');
    var s = flowSummary ? flowSummary[key] : null;
    if (!s) {
      body.innerHTML = stateHtml(NO_DATA);
      return;
    }
    var rows = INVESTORS.map(function (inv) {
      var v = num(s[inv.key]);
      var txt = v === null ? NO_DATA : fmtSigned(v) + ' 백만원';
      return '<tr><th scope="row">' + esc(inv.label) + '</th>' +
        '<td class="' + signClass(v) + '">' + esc(txt) + '</td></tr>';
    }).join('');
    body.innerHTML = '<table class="flow-table"><tbody>' + rows + '</tbody></table>' +
      '<p class="legend-note">누적 순매수 · 단위 백만원</p>';
  }

  function loadFlows() {
    getJSON(API.flows).then(function (data) {
      flowSummary = (data && data.hasData && data.summary) ? data.summary : null;
      renderSummaryFlow(flowSummary);
      renderFlowTabs();
      renderFlowBody('d1');
    }).catch(function () {
      flowSummary = null;
      renderSummaryFlow(null);
      renderFlowTabs();
      el('flow-body').innerHTML = stateHtml('수급 데이터를 불러오지 못했습니다.');
    });
  }

  /* ================= 5. 이벤트 타임라인 ================= */
  function sortedEvents(list) {
    return (Array.isArray(list) ? list.slice() : []).sort(function (a, b) {
      return String(b.eventDate || '').localeCompare(String(a.eventDate || ''));
    });
  }

  function pctCell(v) {
    var t = fmtPct(v);
    if (t === null) return '<span class="na">—</span>';
    return '<span class="' + signClass(v) + '">' + esc(t) + '</span>';
  }

  function renderSummaryEvent(list) {
    var box = el('sum-event');
    var items = sortedEvents(list);
    if (items.length === 0) {
      box.innerHTML = '<div class="sum-label">오늘 중요정보</div>' + stateHtml(NO_DATA);
      return;
    }
    var ev = items[0];
    box.innerHTML = '<div class="sum-label">오늘 중요정보</div>' +
      '<div class="sum-event-title">' + esc(ev.title || '제목 없음') + '</div>' +
      (ev.eventDate ? '<div class="sum-sub">' + esc(ev.eventDate) + '</div>' : '');
  }

  function renderEvents(list) {
    var wrap = el('event-list');
    var items = sortedEvents(list);
    if (items.length === 0) {
      wrap.innerHTML = stateHtml('등록된 이벤트가 없습니다.');
      return;
    }
    wrap.innerHTML = items.map(function (ev, i) {
      var price = num(ev.priceAtEvent);
      var pairs = [
        ['1거래일', ev.change1d],
        ['3거래일', ev.change3d],
        ['5거래일', ev.change5d],
        ['20거래일', ev.change20d]
      ];
      var chg = pairs.map(function (pair) {
        return '<div class="chg-cell"><div class="d">' + esc(pair[0]) + '</div>' +
          '<div class="v">' + pctCell(pair[1]) + '</div></div>';
      }).join('');
      var srcName = ev.sourceName || ev.source || '';
      return '<article class="ev-item">' +
        '<button type="button" class="ev-head" aria-expanded="false">' +
          '<span class="ev-date">' + esc(ev.eventDate || '') + '</span>' +
          '<span class="ev-main">' +
            (ev.category ? '<span class="ev-cat">' + esc(ev.category) + '</span>' : '') +
            '<span class="ev-title">' + esc(ev.title || '제목 없음') + '</span>' +
          '</span>' +
          '<span class="chev" aria-hidden="true">▾</span>' +
        '</button>' +
        '<div class="ev-body" hidden>' +
          '<div class="kv"><span class="k">사건 요약</span><span>' + esc(ev.summary || NO_DATA) + '</span></div>' +
          '<div class="kv"><span class="k">원자료</span><span>' +
            esc(srcName || NO_DATA) + ' ' + gradeBadge(ev.factGrade) + extLink(ev.sourceUrl) +
          '</span></div>' +
          '<div class="kv"><span class="k">당시 주가</span><span>' +
            (price === null
              ? esc(NO_DATA)
              : esc(fmtInt(price)) + '원' + (ev.priceDate ? ' (' + esc(ev.priceDate) + ' 기준)' : '')) +
          '</span></div>' +
          '<div class="kv"><span class="k">이후 변화율</span></div>' +
          '<div class="chg-grid">' + chg + '</div>' +
        '</div>' +
      '</article>';
    }).join('');
  }

  function onEventListClick(e) {
    var btn = e.target.closest('.ev-head');
    if (!btn || btn.tagName === 'A') return;
    if (e.target.closest('a')) return;
    var item = btn.closest('.ev-item');
    var body = item.querySelector('.ev-body');
    var open = item.classList.toggle('open');
    btn.setAttribute('aria-expanded', open ? 'true' : 'false');
    if (open) body.removeAttribute('hidden');
    else body.setAttribute('hidden', '');
  }

  function loadEvents() {
    getJSON(API.events).then(function (list) {
      renderEvents(list);
      renderSummaryEvent(list);
    }).catch(function () {
      el('event-list').innerHTML = stateHtml('이벤트를 불러오지 못했습니다.');
      el('sum-event').innerHTML = '<div class="sum-label">오늘 중요정보</div>' + stateHtml(NO_DATA);
    });
  }

  /* ================= 6. 중국/게임 Tracker ================= */
  var trackData = null;

  function renderTrackTabs() {
    var wrap = el('track-tabs');
    wrap.innerHTML = TRACK_TABS.map(function (t, i) {
      return '<button type="button" class="tab" role="tab" data-key="' + esc(t.key) +
        '" aria-selected="' + (i === 0 ? 'true' : 'false') + '">' + esc(t.label) + '</button>';
    }).join('');
  }

  function onTrackTabClick(e) {
    var btn = e.target.closest('button.tab');
    if (!btn) return;
    var key = btn.getAttribute('data-key');
    var wrap = el('track-tabs');
    var tabs = wrap.querySelectorAll('.tab');
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].setAttribute('aria-selected', tabs[i] === btn ? 'true' : 'false');
    }
    renderTrackBody(key);
  }

  /* "연말·연초 출시" 류의 일정 언급은 확정 일정이 아니므로 딱지 표시 */
  function scheduleBadge(text) {
    var t = String(text || '');
    if (/(연말|연초)/.test(t) && /(출시|론칭)/.test(t)) {
      return '<span class="badge-schedule">확정 일정 아님</span>';
    }
    return '';
  }

  function renderTrackBody(key) {
    var body = el('track-body');
    var items = trackData ? trackData[key] : null;
    if (!Array.isArray(items) || items.length === 0) {
      body.innerHTML = stateHtml(NO_DATA);
      return;
    }
    var cards = items.map(function (it) {
      var text = (it.item || '') + ' ' + (it.detail || '');
      return '<div class="track-card">' +
        '<div class="track-meta"><span>' + esc(it.trackedAt || '') + '</span>' +
        (it.gameName ? '<span aria-hidden="true">·</span><span>' + esc(it.gameName) + '</span>' : '') + '</div>' +
        '<div class="track-item">' + esc(it.item || '') + scheduleBadge(text) + '</div>' +
        (it.detail ? '<div class="track-detail">' + esc(it.detail) + '</div>' : '') +
        '<div class="track-src">출처: ' + esc(it.sourceName || it.sourceType || '') + '</div>' +
      '</div>';
    }).join('');
    var note = key === 'community'
      ? '<p class="legend-note">커뮤니티 주장은 사실이 아니며 Fact Check 큐로만 활용됩니다.</p>'
      : '';
    body.innerHTML = cards + note;
  }

  function loadTracking() {
    getJSON(API.tracking).then(function (data) {
      trackData = data || {};
      renderTrackTabs();
      renderTrackBody('official');
    }).catch(function () {
      renderTrackTabs();
      el('track-body').innerHTML = stateHtml('트래커 데이터를 불러오지 못했습니다.');
    });
  }

  /* ================= 7. Fact Check ================= */
  /* 2026-10-05 정정 반영: 68,910원은 DART rcpNo 20260630900888 「1주당 가액(원)」에
   * 기재된 공시값(A등급)이다. "파생 계산값" 판정은 오류로 철회됨.
   * 공시 근거(rcpNo)는 API의 sourceUrl/basis에 포함되어 카드에 함께 표시된다. */

  function isUnconfirmedSensitive(fc) {
    var claimText = (fc.claimKey || '') + ' ' + (fc.claim || '');
    var topic = /(중국|판호).*(승인|발급)|거래.*종결|인수.*종결|종결.*완료/.test(claimText);
    var t = claimText + ' ' + (fc.verdict || '') + ' ' + (fc.basis || '');
    var unconfirmed = String(fc.grade || '').toUpperCase() === 'U' || /미확인/.test(t);
    return topic && unconfirmed;
  }

  function renderFactChecks(list) {
    var wrap = el('fact-list');
    if (!Array.isArray(list) || list.length === 0) {
      wrap.innerHTML = stateHtml(NO_DATA);
      return;
    }
    wrap.innerHTML = list.map(function (fc) {
      var notices = '';
      if (isUnconfirmedSensitive(fc)) {
        notices += '<div class="fact-notice warn"><strong class="unconfirmed">미확인</strong>' +
          '<span> — 해당 내용은 아직 확인되지 않았습니다.</span></div>';
      }
      var srcName = fc.sourceName || '';
      return '<div class="fact-card">' +
        gradeBadge(fc.grade) +
        '<div class="fact-claim">' + esc(fc.claim || '') + '</div>' +
        (fc.verdict ? '<div class="fact-verdict">' + esc(fc.verdict) + '</div>' : '') +
        (fc.basis ? '<div class="fact-basis">' + esc(fc.basis) + '</div>' : '') +
        ((srcName || fc.sourceUrl)
          ? '<div class="fact-src">출처: ' + esc(srcName) + extLink(fc.sourceUrl) + '</div>'
          : '') +
        notices +
      '</div>';
    }).join('');
  }

  function loadFactChecks() {
    getJSON(API.factChecks).then(function (list) {
      renderFactChecks(list);
    }).catch(function () {
      el('fact-list').innerHTML = stateHtml('Fact Check 데이터를 불러오지 못했습니다.');
    });
  }

  /* ================= 8. 주주 정보(커뮤니티) ================= */
  function loadCommunity() {
    getJSON(API.community).then(function (list) {
      var body = el('community-body');
      if (!Array.isArray(list) || list.length === 0) {
        body.innerHTML = '<div class="state">수집된 커뮤니티 글이 없습니다. ' +
          '커뮤니티 글은 사실이 아니며 Fact Check 큐로만 활용됩니다.</div>';
        return;
      }
      var cards = list.map(function (p) {
        return '<div class="track-card">' +
          '<div class="track-meta"><span>' + esc(p.createdAt || p.trackedAt || '') + '</span>' +
          (p.sourceName ? '<span aria-hidden="true">·</span><span>' + esc(p.sourceName) + '</span>' : '') + '</div>' +
          '<div class="track-item">' + esc(p.title || '') + '</div>' +
          (p.content || p.summary ? '<div class="track-detail">' + esc(p.content || p.summary) + '</div>' : '') +
        '</div>';
      }).join('');
      body.innerHTML = '<p class="legend-note">커뮤니티 글은 사실이 아니며 Fact Check 큐로만 활용됩니다.</p>' + cards;
    }).catch(function () {
      el('community-body').innerHTML = stateHtml('커뮤니티 데이터를 불러오지 못했습니다.');
    });
  }

  /* ================= 초기화 ================= */
  function init() {
    renderPeriodTabs();
    el('period-tabs').addEventListener('click', onPeriodClick);
    el('flow-tabs').addEventListener('click', onFlowTabClick);
    el('track-tabs').addEventListener('click', onTrackTabClick);
    el('event-list').addEventListener('click', onEventListClick);

    loadOverview();
    loadFlows();
    loadPrices(DEFAULT_PERIOD);
    loadEvents();
    loadTracking();
    loadFactChecks();
    loadCommunity();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
