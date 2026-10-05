/* 위메이드 가치 재평가(SOTP) fragment용 JS. 읽기 전용 fetch + 시나리오 탭.
 * 단일 목표주가를 계산·표시하지 않는다. */
(function () {
  'use strict';
  var root = document.getElementById('valuation-section');
  if (!root) return;

  var API = '/api/wemade/valuation';
  var currentScenario = 'BASE';
  var chart = null;

  function fmtKrw(v) {
    if (v === null || v === undefined) return '미산정';
    if (v >= 1e8) {
      var eok = v / 1e8;
      return (Math.round(eok * 10) / 10).toLocaleString('ko-KR') + '억원';
    }
    return Number(v).toLocaleString('ko-KR') + '원';
  }
  function fmtWon(v) {
    if (v === null || v === undefined) return '—';
    return Number(v).toLocaleString('ko-KR') + '원';
  }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function gradeBadge(g) {
    var label = { A: 'A·공시', B: 'B·복수신뢰', C: 'C·언론/증권사', D: 'D·계산/커뮤니티', U: 'U·미확인' }[g] || g;
    var cls = g === 'A' ? 'grade-a' : 'muted';
    return '<span class="' + cls + '">' + esc(label) + '</span>';
  }
  function getJSON(url) {
    return fetch(url, { credentials: 'same-origin' }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }

  function renderSotp(data) {
    var mc = data.marketCap || {};
    document.getElementById('val-close').textContent = fmtWon(mc.closePriceKrw);
    document.getElementById('val-close-date').textContent = mc.tradeDate ? '(' + mc.tradeDate + ' 기준)' : '';
    document.getElementById('val-mktcap-v').textContent = fmtKrw(mc.marketCapKrw);
    var deal = data.dealImplied || {};
    document.getElementById('val-deal-implied').textContent = fmtKrw(deal.impliedMarketCapKrw);

    var maxPart = (data.components || []).find(function (c) { return c.componentKey === 'stake_wemademax'; });
    document.getElementById('val-max-v').textContent = fmtKrw(maxPart && maxPart.valueKrw);

    document.getElementById('val-total').textContent = fmtKrw(data.totalKrw);
    document.getElementById('val-total-scenario').textContent = (data.scenarioNameKo || '') + ' 시나리오 부분합';
    document.getElementById('val-pershare').textContent = fmtWon(data.perShareKrw);

    var assumeBox = document.getElementById('val-assumptions');
    var assume = {};
    try { assume = JSON.parse(data.assumptionsJson || '{}'); } catch (e) { assume = {}; }
    var html = '<strong>[' + esc(data.scenarioNameKo || '') + ' 시나리오 가정]</strong>\n' + esc(data.description || '') + '\n';
    Object.keys(assume).forEach(function (k) {
      var v = assume[k];
      html += '· ' + esc(k) + ': ' + esc(Array.isArray(v) ? v.join(' / ') : v) + '\n';
    });
    assumeBox.innerHTML = html;

    var notesBox = document.getElementById('val-notes');
    notesBox.textContent = (data.notes || []).join(' ');
  }

  function renderChart() {
    // 3개 시나리오 + 시가총액 + 거래시사 가치 비교 (억원 단위)
    Promise.all([
      getJSON(API + '/sotp?scenario=CONSERVATIVE'),
      getJSON(API + '/sotp?scenario=BASE'),
      getJSON(API + '/sotp?scenario=OPTIMISTIC')
    ]).then(function (rs) {
      var toEok = function (v) { return v == null ? null : Math.round(v / 1e8); };
      var mc = rs[0].marketCap || {};
      var deal = rs[0].dealImplied || {};
      var labels = ['보수적', '기준', '낙관적', '현재 시가총액', '거래 시사(참고)'];
      var values = [toEok(rs[0].totalKrw), toEok(rs[1].totalKrw), toEok(rs[2].totalKrw),
                    toEok(mc.marketCapKrw), toEok(deal.impliedMarketCapKrw)];
      var canvas = document.getElementById('val-chart');
      if (!canvas || typeof Chart === 'undefined') return;
      if (chart) chart.destroy();
      chart = new Chart(canvas, {
        type: 'bar',
        data: {
          labels: labels,
          datasets: [{
            label: '억원',
            data: values,
            backgroundColor: ['#9db8d2', '#4a7ebb', '#1f4e79', '#c9c9c9', '#e0a030']
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: { y: { beginAtZero: true, title: { display: true, text: '억원' } } }
        }
      });
    }).catch(function () { /* 차트 실패는 무시 */ });
  }

  function loadScenario(key) {
    currentScenario = key;
    root.querySelectorAll('.val-tab').forEach(function (b) {
      var on = b.getAttribute('data-scenario') === key;
      b.classList.toggle('active', on);
      if (on) b.setAttribute('aria-selected', 'true'); else b.removeAttribute('aria-selected');
    });
    getJSON(API + '/sotp?scenario=' + encodeURIComponent(key))
      .then(renderSotp)
      .catch(function () {
        document.getElementById('val-assumptions').textContent = '데이터를 불러오지 못했습니다.';
      });
  }

  root.querySelectorAll('.val-tab').forEach(function (b) {
    b.addEventListener('click', function () { loadScenario(b.getAttribute('data-scenario')); });
  });

  loadScenario(currentScenario);
  renderChart();
})();
