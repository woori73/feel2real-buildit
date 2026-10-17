/**
 * FEEL2REAL Hub 통합 대시보드 — 위젯 렌더러 (Phase 1-B).
 *
 * Phase 1-A의 위젯 스키마 v1을 사용해 EASY 위젯을 표시한다.
 * 위젯별 로딩/오류/빈 데이터 상태를 구분해 표시한다.
 */
(function () {
  'use strict';

  const WIDGET_API = '/api/admin/easy/dashboard/widgets';
  let currentBusinessId = null;
  let businesses = [];

  function fmtNum(n) {
    return Number(n || 0).toLocaleString('ko-KR');
  }

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  async function init() {
    // 페이지 진입 가드: ADMIN이 아니면 로그인 페이지로 (dashboard.html과 동일 패턴)
    const currentUser = await ensureCurrentUser();
    if (!currentUser || currentUser.role !== 'ADMIN') {
      location.href = '/admin/login';
      return;
    }
    bindTabs();
    await loadBusinesses();
    await loadWidgets();
  }

  function bindTabs() {
    document.getElementById('serviceTabs').addEventListener('click', (e) => {
      const btn = e.target.closest('.hub-service-tab');
      if (!btn) return;
      document.querySelectorAll('.hub-service-tab').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      filterWidgets(btn.dataset.service);
    });
  }

  function filterWidgets(service) {
    document.querySelectorAll('.hub-widget').forEach(w => {
      w.style.display = (service === 'all' || w.dataset.service === service) ? '' : 'none';
    });
  }

  async function loadBusinesses() {
    try {
      const res = await fetchAuth('/api/admin/easy/businesses');
      if (!res.ok) return;
      businesses = await res.json();
      if (businesses.length > 0) {
        currentBusinessId = businesses[0].businessId || businesses[0].id;
      }
    } catch (e) {
      console.warn('사업체 목록 로드 실패:', e);
    }
  }

  async function loadWidgets() {
    const container = document.getElementById('widgetContainer');
    container.innerHTML = '';
    container.appendChild(el('div', 'hub-loading', '위젯을 불러오는 중...'));

    if (!currentBusinessId) {
      container.innerHTML = '';
      container.appendChild(el('div', 'hub-empty', '조회할 사업체가 없습니다.'));
      return;
    }

    try {
      const url = WIDGET_API + '?businessId=' + encodeURIComponent(currentBusinessId);
      const res = await fetchAuth(url);
      if (!res.ok) throw new Error('위젯 API 오류: ' + res.status);
      const data = await res.json();

      container.innerHTML = '';
      if (data.generatedAt) {
        document.getElementById('dataAsof').textContent =
          '데이터 기준: ' + new Date(data.generatedAt).toLocaleString('ko-KR');
      }

      const widgets = data.widgets || [];
      if (widgets.length === 0) {
        container.appendChild(el('div', 'hub-empty', '표시할 위젯이 없습니다.'));
        return;
      }

      widgets.forEach(w => container.appendChild(renderWidget(w)));
    } catch (e) {
      console.error('위젯 로드 실패:', e);
      container.innerHTML = '';
      const errDiv = el('div', 'hub-error', '위젯을 불러오지 못했습니다.');
      const retryBtn = el('button', null, '다시 시도');
      retryBtn.addEventListener('click', loadWidgets);
      errDiv.appendChild(retryBtn);
      container.appendChild(errDiv);
    }
  }

  function renderWidget(w) {
    const div = el('div', 'hub-widget');
    div.dataset.service = 'easy';
    div.dataset.widgetId = w.id;

    const h3 = el('h3', null, w.title);
    if (w.link) {
      const a = el('a', null, '자세히 →');
      a.href = w.link;
      h3.appendChild(a);
    }
    div.appendChild(h3);

    // 위젯별 오류 격리: error 필드가 있으면 에러 표시
    if (w.error) {
      const errDiv = el('div', 'hub-error', w.error);
      const retryBtn = el('button', null, '다시 시도');
      retryBtn.addEventListener('click', loadWidgets);
      errDiv.appendChild(retryBtn);
      div.appendChild(errDiv);
      return div;
    }

    switch (w.type) {
      case 'stat-cards':
        div.appendChild(renderStatCards(w.data));
        break;
      case 'list':
        div.appendChild(renderList(w.data));
        break;
      case 'chart':
        div.appendChild(renderChartPlaceholder(w.data));
        break;
      case 'calendar':
        div.appendChild(renderCalendarPlaceholder(w.data));
        break;
      default:
        div.appendChild(el('div', 'hub-empty', '지원하지 않는 위젯 타입: ' + w.type));
    }
    return div;
  }

  function renderStatCards(data) {
    const grid = el('div', 'hub-stat-cards');
    const cards = (data && data.cards) || [];
    if (cards.length === 0) {
      return el('div', 'hub-empty', '데이터가 없습니다.');
    }
    cards.forEach(c => {
      const card = el('div', 'hub-stat-card');
      card.appendChild(el('div', 'v', fmtNum(c.value) + (c.unit || '')));
      card.appendChild(el('div', 'l', c.label));
      if (c.trend) {
        const t = el('div', 't ' + (c.trendDirection || ''), c.trend);
        card.appendChild(t);
      }
      grid.appendChild(card);
    });
    return grid;
  }

  function renderList(data) {
    const items = (data && data.items) || [];
    if (items.length === 0) {
      return el('div', 'hub-empty', (data && data.emptyText) || '데이터가 없습니다.');
    }
    const ul = el('ul', 'hub-list');
    items.forEach(item => {
      const li = el('li');
      li.appendChild(el('div', 't', item.title));
      if (item.subtitle) li.appendChild(el('div', 's', item.subtitle));
      if (item.meta) li.appendChild(el('div', 'm', item.meta));
      ul.appendChild(li);
    });
    return ul;
  }

  function renderChartPlaceholder(data) {
    // Phase 1-B: 차트 라이브러리 없이 텍스트 요약으로 표시 (2차에서 차트 추가)
    return el('div', 'hub-empty', '차트 위젯 (2차에서 지원 예정)');
  }

  function renderCalendarPlaceholder(data) {
    return el('div', 'hub-empty', '달력 위젯 (2차에서 지원 예정)');
  }

  // DOM 로드 후 초기화
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
