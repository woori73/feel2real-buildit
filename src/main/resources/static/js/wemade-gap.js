/* 위메이드 가격괴리 분석(gap) + 누가 고점에서 팔았나(who-sold) fragment용 JS.
 * 읽기 전용 fetch. 단일 목표주가를 계산·표시하지 않는다.
 * 수급 데이터는 미확보이므로 PENDING 상태로만 표시한다. */
(function () {
  'use strict';

  var API = '/api/wemade/gap';

  var HYPOTHESES = {
    1: { title: '① 미르(传奇) IP 중국 전략가치 가설', verdict: '부분 지지 — IP 시나리오 2,500~7,500억원은 괴리 약 1.81조원 중 일부만 설명' },
    2: { title: '② 경영권 프리미엄 가설', verdict: '규모상 이례적 — 340%대 프리미엄은 Shanda Games 상장폐지(46.5~53.8%) 대비 과도. 단독 설명 불가' },
    3: { title: '③ 거래종결 리스크 할인 가설', verdict: '지지 요소 — 중국 승인 불확실성이 킹넷 공시에 명시(A등급). 정량은 불가' },
    4: { title: '④ 오버행·수급 압박 가설', verdict: '가격 FACT 확정 · 주체 PENDING — 급등 후 되돌림·lower highs·대량 보합은 확인, 매도 주체 식별 불가' },
    5: { title: '⑤ 실적·재무 펀더멘털 할인 가설', verdict: '미확인 — 별도 재무제표 분석 필요' },
    6: { title: '⑥ 인수 이해관계자-수급 연관 가설', verdict: '미확인 — 직접 증거 없음. "중국 대주주가 주가를 누른다"는 결론을 세우지 않음', unverified: true }
  };

  function getJSON(url) {
    return fetch(url, { credentials: 'same-origin' }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function fmtEok(v) {
    if (v === null || v === undefined) return '미산정';
    return (Math.round(v / 1e8 * 10) / 10).toLocaleString('ko-KR') + '억원';
  }
  function fmtWon(v) {
    if (v === null || v === undefined) return '—';
    return Number(v).toLocaleString('ko-KR') + '원';
  }
  function fmtVol(v) {
    if (v === null || v === undefined) return '—';
    if (v >= 10000) return (Math.round(v / 10000 * 10) / 10).toLocaleString('ko-KR') + '만주';
    return Number(v).toLocaleString('ko-KR') + '주';
  }
  function fmtPct(v) {
    if (v === null || v === undefined) return '—';
    return (v > 0 ? '+' : '') + v.toFixed(2) + '%';
  }
  function gradeBadge(g) {
    var label = { A: 'A·공시', B: 'B·복수신뢰', C: 'C·언론/증권사', D: 'D·계산/커뮤니티', U: 'U·미확인' }[g] || g;
    var cls = g === 'A' ? 'grade-a' : 'muted';
    return '<span class="' + cls + '">' + esc(label) + '</span>';
  }
  function retCls(v) {
    if (v === null || v === undefined) return 'muted';
    return v < 0 ? 'ws-neg' : (v > 0 ? 'ws-pos' : 'muted');
  }

  /* ---------- gap fragment ---------- */
  function renderGapHeadline(h) {
    if (!h) return;
    var set = function (id, txt) { var el = document.getElementById(id); if (el) el.textContent = txt; };
    set('gap-mktcap', fmtEok(h.marketCapKrw));
    set('gap-close', fmtWon(h.closePriceKrw));
    set('gap-close-date', h.tradeDate || '');
    set('gap-deal', fmtEok(h.dealAmountKrw));
    set('gap-implied', fmtEok(h.implied100PctKrw));
    set('gap-implied-note', h.implied100PctNote || '');
    set('gap-discount', h.discountPct != null ? h.discountPct.toFixed(2) + '%' : '—');
    set('gap-pershare', fmtWon(h.perShareGapKrw));
    set('gap-multiple', h.controlPremiumMultiple != null ? h.controlPremiumMultiple.toFixed(2) + '배' : '—');
  }

  function renderHypotheses(factors) {
    var box = document.getElementById('gap-hypotheses');
    if (!box) return;
    if (!factors || !factors.length) { box.innerHTML = '<p class="muted">요인 데이터가 없습니다.</p>'; return; }
    var byHyp = {};
    factors.forEach(function (f) {
      if (f.hypothesisNo === 0) return; // 기준 수치는 헤드라인에서 표시
      (byHyp[f.hypothesisNo] = byHyp[f.hypothesisNo] || []).push(f);
    });
    var html = '';
    Object.keys(HYPOTHESES).map(Number).sort().forEach(function (no) {
      var hp = HYPOTHESES[no];
      var fs = byHyp[no] || [];
      html += '<details class="gap-hyp"' + (no <= 2 ? ' open' : '') + '><summary>' + esc(hp.title) +
        '<span class="tag' + (hp.unverified ? ' unverified' : '') + '">' + esc(hp.verdict.split(' — ')[0]) + '</span></summary><div class="body">';
      html += '<p class="muted" style="font-size:12px;">판정: ' + esc(hp.verdict) + '</p>';
      fs.forEach(function (f) {
        var amt = (f.amountLowKrw != null || f.amountHighKrw != null)
          ? fmtEok(f.amountLowKrw) + (f.amountHighKrw !== f.amountLowKrw ? ' ~ ' + fmtEok(f.amountHighKrw) : '')
          : '미산정';
        html += '<div class="gap-factor"><span class="fk">' + esc(f.nameKo) + '</span> ' + gradeBadge(f.grade) +
          '<span class="muted" style="font-size:12px;"> · ' + esc(amt) + '</span>' +
          '<span class="gap-basis">' + esc(f.basis || '') +
          (f.sourceName ? ' <span class="muted">출처: ' + esc(f.sourceName) + (f.asOfDate ? ' (' + esc(f.asOfDate) + ')' : '') + '</span>' : '') +
          '</span></div>';
      });
      html += '</div></details>';
    });
    box.innerHTML = html;
  }

  /* ---------- who-sold fragment ---------- */
  function renderChart(series, events) {
    var canvas = document.getElementById('ws-chart');
    if (!canvas || !series || !series.length) return;
    var dpr = window.devicePixelRatio || 1;
    var W = canvas.clientWidth || 600, H = 300;
    canvas.width = W * dpr; canvas.height = H * dpr;
    var ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);
    var padL = 52, padR = 8, padT = 12, padB = 44;
    var closes = series.map(function (p) { return p.close; }).filter(function (v) { return v != null; });
    var vols = series.map(function (p) { return p.volume || 0; });
    var minP = Math.min.apply(null, closes), maxP = Math.max.apply(null, closes);
    var maxV = Math.max.apply(null, vols);
    var span = (maxP - minP) || 1;
    minP -= span * 0.08; maxP += span * 0.08;
    var n = series.length;
    var x = function (i) { return padL + (W - padL - padR) * i / Math.max(1, n - 1); };
    var y = function (p) { return padT + (H - padT - padB) * (1 - (p - minP) / (maxP - minP)); };
    var yv = function (v) { return H - padB - (H - padT - padB) * 0.25 * (v / (maxV || 1)); };

    ctx.clearRect(0, 0, W, H);
    // 거래량 막대
    ctx.fillStyle = 'rgba(120,140,170,0.35)';
    var bw = Math.max(1, (W - padL - padR) / n * 0.6);
    series.forEach(function (p, i) {
      var h = (H - padB) - yv(p.volume || 0);
      ctx.fillRect(x(i) - bw / 2, yv(p.volume || 0), bw, h);
    });
    // 종가 선
    ctx.strokeStyle = '#1f4e79'; ctx.lineWidth = 1.6; ctx.beginPath();
    series.forEach(function (p, i) {
      if (p.close == null) return;
      if (i === 0) ctx.moveTo(x(i), y(p.close)); else ctx.lineTo(x(i), y(p.close));
    });
    ctx.stroke();
    // 68,910원 기준선
    var dealY = y(68910);
    if (dealY > padT && dealY < H - padB) {
      ctx.strokeStyle = '#b00'; ctx.setLineDash([5, 4]); ctx.beginPath();
      ctx.moveTo(padL, dealY); ctx.lineTo(W - padR, dealY); ctx.stroke(); ctx.setLineDash([]);
      ctx.fillStyle = '#b00'; ctx.font = '11px sans-serif';
      ctx.fillText('SPA 68,910원', padL + 4, dealY - 4);
    }
    // 이벤트 마커
    var evByDate = {};
    (events || []).forEach(function (e) { evByDate[e.eventDate] = e; });
    ctx.font = '10px sans-serif';
    series.forEach(function (p, i) {
      var e = evByDate[p.date];
      if (!e || p.close == null) return;
      var color = e.eventType === 'SURGE' ? '#0a6e0a' : (e.eventType === 'CRASH' ? '#b00' : '#b8860b');
      ctx.fillStyle = color;
      ctx.beginPath(); ctx.arc(x(i), y(p.close), 4, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#333';
      var label = e.eventDate.slice(5).replace('-', '/');
      ctx.fillText(label, Math.min(x(i) - 12, W - 44), H - padB + 14);
    });
    // y축 눈금
    ctx.fillStyle = '#666';
    [minP + span * 0.08, (minP + maxP) / 2, maxP - span * 0.08].forEach(function (v) {
      ctx.fillText(Math.round(v).toLocaleString('ko-KR'), 4, y(v) + 3);
    });
    // x축 날짜 (월 경계)
    var lastMonth = '';
    ctx.fillStyle = '#666';
    series.forEach(function (p, i) {
      var mo = p.date.slice(0, 7);
      if (mo !== lastMonth) { lastMonth = mo; ctx.fillText(p.date.slice(5).replace('-', '/'), x(i) - 10, H - padB + 28); }
    });
  }

  function renderEvents(events) {
    var box = document.getElementById('ws-events');
    if (!box) return;
    if (!events || !events.length) { box.innerHTML = '<span class="muted">이벤트가 없습니다.</span>'; return; }
    var html = '<ul style="padding-left:18px;margin:6px 0;">';
    events.forEach(function (e) {
      var tail = e.closePrice != null
        ? ' — ' + fmtWon(e.closePrice) + ' (' + fmtPct(e.priceChangePct) + ', ' + fmtVol(e.volume) + ')'
        : ' (비거래일)';
      html += '<li><strong>' + esc(e.eventDate) + '</strong> ' + esc(e.title) + gradeBadge(e.grade) +
        '<br/><span class="muted">' + esc(e.summary || '') + tail + '</span></li>';
    });
    box.innerHTML = html + '</ul>';
  }

  function renderSurgeTable(surges) {
    var tbody = document.querySelector('#ws-surge-tbl tbody');
    if (!tbody) return;
    if (!surges || !surges.length) { tbody.innerHTML = '<tr><td colspan="8" class="muted">급등일 없음</td></tr>'; return; }
    var html = '';
    surges.forEach(function (s) {
      var post = s.postReturns || {};
      var cell = function (k) {
        var p = post[k];
        if (!p) return '<td class="muted">—</td>';
        return '<td class="' + retCls(p.returnPct) + '">' + fmtPct(p.returnPct) + '</td>';
      };
      html += '<tr><td>' + esc(s.date) + '</td>' +
        '<td class="ws-pos">' + fmtPct(s.changePct) + '</td>' +
        '<td>' + fmtWon(s.close) + '</td>' +
        '<td>' + fmtVol(s.volume) + '</td>' +
        '<td>' + (s.break20dHigh ? '<strong>돌파</strong>' : '<span class="muted">—</span>') + '</td>' +
        cell('1d') + cell('3d') + cell('5d') + '</tr>';
    });
    tbody.innerHTML = html;
  }

  function renderVolume(vs) {
    var box = document.getElementById('ws-volume');
    if (!box) return;
    if (!vs || !vs.spikes) { box.innerHTML = '<p class="muted">데이터 없음</p>'; return; }
    var html = '<table class="ws-tbl"><thead><tr><th>날짜</th><th>거래량</th><th>등락률</th><th>종가</th><th>고가</th></tr></thead><tbody>';
    vs.spikes.forEach(function (s) {
      html += '<tr><td>' + esc(s.date) + '</td><td>' + fmtVol(s.volume) + '</td>' +
        '<td class="' + retCls(s.changePct) + '">' + fmtPct(s.changePct) + '</td>' +
        '<td>' + fmtWon(s.close) + '</td><td>' + fmtWon(s.high) + '</td></tr>';
    });
    box.innerHTML = html + '</tbody></table><p class="muted ws-note">' + esc(vs.thresholdNote || '') + '</p>';
  }

  function renderVerdict(v, supply) {
    var box = document.getElementById('ws-verdict');
    if (!box) return;
    var sec = function (title, items, badge) {
      var h = '<li><strong>' + esc(title) + '</strong> ' + badge + '<ul style="margin-top:4px;">';
      (items || []).forEach(function (t) { h += '<li>' + esc(t) + '</li>'; });
      return h + '</ul></li>';
    };
    box.innerHTML =
      sec('① FACT — 가격 고점·반전', v.fact, '<span class="grade-a">A·실측</span>') +
      sec('② ANALYSIS — 수급 동행', v.analysis, '<span class="muted">PENDING</span>') +
      sec('③ HYPOTHESIS — 신규 대주주 연관', v.hypothesis, '<span class="muted">미확인</span>');
    var note = document.getElementById('ws-supply-note');
    if (note && supply) {
      note.textContent = '상태: ' + supply.status + ' — ' + (supply.reason || '');
    }
  }

  function boot() {
    var gapRoot = document.getElementById('gap-section');
    var wsRoot = document.getElementById('who-sold-section');
    if (!gapRoot && !wsRoot) return;

    getJSON(API + '/comparison').then(function (data) {
      if (gapRoot) renderGapHeadline(data.headline);
      if (wsRoot) {
        renderChart(data.priceSeries, null);
        renderSurgeTable((data.pricePattern || {}).surgeDays);
        renderVolume((data.pricePattern || {}).volumeSpikes);
        renderVerdict(data.verdict, data.supplyData);
        // 타임라인을 가져와 차트에 이벤트 마커 표시
        getJSON(API + '/timeline').then(function (events) {
          renderChart(data.priceSeries, events);
          renderEvents(events);
        }).catch(function () { /* 타임라인 실패는 무시 */ });
      }
    }).catch(function () {
      if (gapRoot) {
        var b = document.getElementById('gap-hypotheses');
        if (b) b.innerHTML = '<p class="muted">데이터를 불러오지 못했습니다.</p>';
      }
    });

    if (gapRoot) {
      getJSON(API + '/factors').then(renderHypotheses).catch(function () {
        var b = document.getElementById('gap-hypotheses');
        if (b) b.innerHTML = '<p class="muted">데이터를 불러오지 못했습니다.</p>';
      });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
