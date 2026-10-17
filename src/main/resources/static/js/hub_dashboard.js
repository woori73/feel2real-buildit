/**
 * FEEL2REAL Hub 통합 운영 대시보드 — 위젯 렌더러 (P0 개선).
 *
 * 위젯 스키마 v1 (`/api/admin/easy/dashboard/widgets`)을 사용한다.
 * - 핵심 지표(KPI)를 상단 히어로 영역에 배치
 * - 사업체 선택 드롭다운 (선택 유지)
 * - 로딩(스켈레톤) / 빈 데이터 / 오류 / 마지막 갱신 시각을 명확히 구분
 * - 차트·달력 타입은 기본 화면에서 제외 (미구현)
 * - 기존 EASY API·캐시 로직 재사용 (JS에서 캐시 건드리지 않음)
 */
(function () {
  'use strict';

  var WIDGET_API = '/api/admin/easy/dashboard/widgets';
  var BIZ_API = '/api/admin/easy/businesses';
  var BIZ_KEY = 'hub.bizId';

  var state = {
    businesses: [],
    bizId: null,
    widgets: [],
    generatedAt: null,
    loading: false
  };

  /* ---------- 유틸 ---------- */
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function fmtNum(n) {
    return Number(n || 0).toLocaleString('ko-KR');
  }
  function fmtTime(iso) {
    if (!iso) return '';
    try { return new Date(iso).toLocaleString('ko-KR', { hour12: false }); }
    catch (e) { return ''; }
  }
  function esc(s) { return String(s == null ? '' : s); }

  /* ---------- 진입 ---------- */
  async function init() {
    var user = await ensureCurrentUser();
    if (!user || user.role !== 'ADMIN') { location.href = '/admin/login'; return; }
    bindTabs();
    bindRefresh();
    await loadBusinesses();
    buildShortcuts();
    await loadWidgets();
  }

  function bindTabs() {
    document.getElementById('serviceTabs').addEventListener('click', function (e) {
      var btn = e.target.closest('.hub-tab');
      if (!btn) return;
      document.querySelectorAll('.hub-tab').forEach(function (b) { b.classList.remove('active'); });
      btn.classList.add('active');
      applyTab(btn.dataset.service);
    });
  }

  function applyTab(service) {
    var showAll = service === 'all';
    // 섹션 타이틀 표시 제어
    document.querySelectorAll('.hub-section-title').forEach(function (t) {
      var s = t.dataset.service;
      t.style.display = (showAll || s === service) ? '' : 'none';
    });
    // 홍보 카드 표시 제어 (data-service="all"이므로 전체/이지재무 탭에서 표시)
    document.querySelectorAll('.hub-promo').forEach(function (p) {
      var s = p.dataset.service || 'all';
      p.style.display = (showAll || s === service || s === 'all') ? '' : 'none';
    });
    // KPI / 위젯 / 바로가기 표시 제어
    [['kpiRow', 'easy'], ['widgetContainer', 'easy'], ['shortcutRow', 'links']].forEach(function (pair) {
      var node = document.getElementById(pair[0]);
      var s = pair[1];
      node.style.display = (showAll || s === service) ? '' : 'none';
    });
  }

  function bindRefresh() {
    document.getElementById('refreshBtn').addEventListener('click', function () {
      if (!state.loading) loadWidgets();
    });
  }

  /* ---------- P0-2: 사업체 선택 ---------- */
  async function loadBusinesses() {
    var sel = document.getElementById('bizSelect');
    try {
      var res = await fetchAuth(BIZ_API);
      if (!res.ok) throw new Error('HTTP ' + res.status);
      var list = await res.json();
      state.businesses = Array.isArray(list) ? list : [];
    } catch (e) {
      console.warn('사업체 목록 로드 실패:', e);
      state.businesses = [];
    }
    sel.innerHTML = '';
    if (state.businesses.length === 0) {
      sel.appendChild(el('option', null, '조회할 사업체가 없습니다.'));
      sel.disabled = true;
      return;
    }
    state.businesses.forEach(function (b) {
      var id = b.businessId != null ? b.businessId : b.id;
      var name = b.name || b.businessName || ('사업체 ' + id);
      var opt = el('option', null, name);
      opt.value = id;
      sel.appendChild(opt);
    });
    // 이전 선택 복원, 없으면 첫 번째
    var saved = null;
    try { saved = localStorage.getItem(BIZ_KEY); } catch (e) {}
    var valid = state.businesses.some(function (b) {
      var id = b.businessId != null ? b.businessId : b.id;
      return String(id) === String(saved);
    });
    state.bizId = valid ? saved : String(state.businesses[0].businessId != null ? state.businesses[0].businessId : state.businesses[0].id);
    sel.value = state.bizId;
    sel.addEventListener('change', function () {
      state.bizId = sel.value;
      try { localStorage.setItem(BIZ_KEY, state.bizId); } catch (e) {}
      loadWidgets();
    });
  }

  /* ---------- P0-3: 상태 구분 (스켈레톤 / 빈 / 오류 / 갱신시각) ---------- */
  function showSkeleton() {
    var c = document.getElementById('widgetContainer');
    c.innerHTML = '';
    var sk = el('div', 'hub-skeleton');
    for (var i = 0; i < 3; i++) {
      var card = el('div', 'hub-skel');
      ['w60', 'w80', 'w40'].forEach(function (w) {
        var bar = el('div', 'bar ' + w);
        card.appendChild(bar);
      });
      sk.appendChild(card);
    }
    c.appendChild(sk);
    // KPI도 스켈레톤
    var kpi = document.getElementById('kpiRow');
    kpi.innerHTML = '';
    for (var j = 0; j < 4; j++) {
      var k = el('div', 'hub-kpi');
      k.appendChild(el('div', 'k-label', '불러오는 중'));
      k.appendChild(el('div', 'k-value', '···'));
      kpi.appendChild(k);
    }
  }

  function showState(kind, msg, hint, retry) {
    var c = document.getElementById('widgetContainer');
    c.innerHTML = '';
    var box = el('div', 'hub-state' + (kind === 'error' ? ' error' : ''));
    var icon = kind === 'error' ? '⚠️' : (kind === 'empty' ? '📭' : 'ℹ️');
    box.appendChild(el('span', 'icon', icon));
    box.appendChild(el('div', 'msg', msg));
    if (hint) box.appendChild(el('div', 'hint', hint));
    if (retry) {
      var btn = el('button', 'hub-retry', '다시 시도');
      btn.addEventListener('click', loadWidgets);
      box.appendChild(btn);
    }
    c.appendChild(box);
    document.getElementById('kpiRow').innerHTML = '';
    document.getElementById('widgetCount').textContent = '';
  }

  async function loadWidgets() {
    if (state.loading) return;
    if (!state.bizId) {
      showState('empty', '조회할 사업체가 없습니다.', '사업체를 먼저 등록해 주세요.', false);
      return;
    }
    state.loading = true;
    document.getElementById('refreshBtn').disabled = true;
    showSkeleton();
    try {
      var url = WIDGET_API + '?businessId=' + encodeURIComponent(state.bizId);
      var res = await fetchAuth(url);
      if (!res.ok) throw new Error('위젯 API 오류: HTTP ' + res.status);
      var data = await res.json();
      state.widgets = data.widgets || [];
      state.generatedAt = data.generatedAt || null;
      renderAll();
    } catch (e) {
      console.error('위젯 로드 실패:', e);
      showState('error', '위젯을 불러오지 못했습니다.', esc(e.message || ''), true);
    } finally {
      state.loading = false;
      document.getElementById('refreshBtn').disabled = false;
    }
  }

  /* ---------- 렌더 ---------- */
  function renderAll() {
    renderKpis();
    renderWidgets();
    var asof = document.getElementById('dataAsof');
    asof.textContent = state.generatedAt
      ? '데이터 기준: ' + fmtTime(state.generatedAt) + ' · 5분 캐시'
      : '';
  }

  // P0-1: 핵심 지표를 상단 히어로에 배치 (finance-summary 위젯 재사용)
  function renderKpis() {
    var kpi = document.getElementById('kpiRow');
    kpi.innerHTML = '';
    var fin = null;
    for (var i = 0; i < state.widgets.length; i++) {
      if (state.widgets[i].id === 'easy-finance-summary') { fin = state.widgets[i]; break; }
    }
    var cards = (fin && fin.data && fin.data.cards) || [];
    if (fin && fin.error) {
      kpi.appendChild(el('div', 'hub-kpi', '지표를 불러오지 못했습니다.'));
      return;
    }
    if (cards.length === 0) {
      var empty = el('div', 'hub-kpi');
      empty.appendChild(el('div', 'k-label', '이번달 지표'));
      empty.appendChild(el('div', 'k-value', '데이터 없음'));
      kpi.appendChild(empty);
      return;
    }
    var clsMap = { '수납': 'kpi-income', '지출': 'kpi-expense', '미수금': 'kpi-receivable', '원생 수': 'kpi-students' };
    cards.forEach(function (c) {
      var card = el('div', 'hub-kpi ' + (clsMap[c.label] || ''));
      card.appendChild(el('div', 'k-label', c.label));
      var v = el('div', 'k-value');
      v.appendChild(document.createTextNode(fmtNum(c.value)));
      var unit = el('span', 'unit', c.unit || '');
      v.appendChild(unit);
      card.appendChild(v);
      if (c.trend) {
        var t = el('div', 'k-sub ' + (c.trendDirection || ''), c.trend);
        card.appendChild(t);
      } else if (state.generatedAt) {
        card.appendChild(el('div', 'k-sub', '기준 ' + fmtTime(state.generatedAt)));
      }
      kpi.appendChild(card);
    });
  }

  function renderWidgets() {
    var c = document.getElementById('widgetContainer');
    c.innerHTML = '';
    // P0-4: finance-summary는 KPI로 이미 표시 → 상세 위젯에서 제외
    // chart / calendar 타입은 미구현이므로 기본 화면에서 제외
    var rest = state.widgets.filter(function (w) {
      return w.id !== 'easy-finance-summary' && (w.type === 'stat-cards' || w.type === 'list');
    });
    document.getElementById('widgetCount').textContent = rest.length + '개';
    if (rest.length === 0) {
      var box = el('div', 'hub-state');
      box.appendChild(el('span', 'icon', '📭'));
      box.appendChild(el('div', 'msg', '표시할 상세 위젯이 없습니다.'));
      c.appendChild(box);
      return;
    }
    rest.forEach(function (w) { c.appendChild(renderWidget(w)); });
  }

  function renderWidget(w) {
    var div = el('div', 'hub-widget');
    div.dataset.service = 'easy';
    div.dataset.widgetId = w.id;

    var head = el('div', 'hub-widget-head');
    head.appendChild(el('h3', null, w.title));
    if (w.link) {
      var a = el('a', null, '자세히 →');
      a.href = w.link;
      head.appendChild(a);
    }
    div.appendChild(head);

    if (w.error) {
      var eb = el('div', 'hub-state error');
      eb.appendChild(el('span', 'icon', '⚠️'));
      eb.appendChild(el('div', 'msg', w.error));
      var rb = el('button', 'hub-retry', '다시 시도');
      rb.addEventListener('click', loadWidgets);
      eb.appendChild(rb);
      div.appendChild(eb);
      return div;
    }

    if (w.type === 'list') div.appendChild(renderList(w.data));
    else if (w.type === 'stat-cards') div.appendChild(renderStatCards(w.data));
    else div.appendChild(el('div', 'hub-state', '지원하지 않는 위젯 타입입니다.'));

    if (w.dataAsOf) {
      div.appendChild(el('div', 'hub-widget-asof', '갱신: ' + fmtTime(w.dataAsOf)));
    }
    return div;
  }

  function renderStatCards(data) {
    var cards = (data && data.cards) || [];
    if (cards.length === 0) {
      var b = el('div', 'hub-state');
      b.appendChild(el('div', 'msg', '데이터가 없습니다.'));
      return b;
    }
    var wrap = el('div');
    var ul = el('ul', 'hub-list');
    cards.forEach(function (cc) {
      var li = el('li');
      var left = el('div');
      left.appendChild(el('div', 't', cc.label));
      li.appendChild(left);
      var amt = el('div', 'amt', fmtNum(cc.value) + (cc.unit || ''));
      li.appendChild(amt);
      ul.appendChild(li);
    });
    wrap.appendChild(ul);
    return wrap;
  }

  function renderList(data) {
    var items = (data && data.items) || [];
    if (items.length === 0) {
      var b = el('div', 'hub-state');
      b.appendChild(el('span', 'icon', '📭'));
      b.appendChild(el('div', 'msg', (data && data.emptyText) || '데이터가 없습니다.'));
      return b;
    }
    var ul = el('ul', 'hub-list');
    items.forEach(function (item) {
      var li = el('li');
      var left = el('div');
      left.appendChild(el('div', 't', item.title));
      if (item.subtitle) left.appendChild(el('div', 's', item.subtitle));
      if (item.meta) left.appendChild(el('div', 'm', item.meta));
      li.appendChild(left);
      ul.appendChild(li);
    });
    return ul;
  }

  /* ---------- P1: 구현된 서비스 바로가기 (링크만, 가짜 통계 없음) ---------- */
  function buildShortcuts() {
    var row = document.getElementById('shortcutRow');
    row.innerHTML = '';
    // 실제 존재하는 관리자 페이지로만 연결 (템플릿 실측 기준)
    var links = [
      { icon: '💰', name: 'EASY 장부', desc: '수납·지출·미수금 관리', href: '/easy' },
      { icon: '📒', name: '원장 화면', desc: '장부 작업 공간', href: '/ledger' },
      { icon: '🏠', name: '홈페이지 빌더', desc: '학원 홈페이지 관리', href: '/builder' },
      { icon: '📝', name: '내신위키', desc: '문제 생성기', href: '/problem-generator' },
      { icon: '📊', name: 'TESTPREP', desc: '시험 관리', href: '/testprep' },
      { icon: '🎨', name: 'DesignCraft', desc: '출판 편집', href: '/designcraft' },
      { icon: '📈', name: '운영 대시보드', desc: '기존 EASY 대시보드', href: '/admin/dashboard' },
      { icon: '💬', name: 'NewStart 상담', desc: '1:1 상담 관리', href: '/admin/newstart_consultations' }
    ];
    links.forEach(function (l) {
      var a = el('a', 'hub-shortcut');
      a.href = l.href;
      var ic = el('div', 'ic', l.icon);
      var tx = el('div', 'tx');
      tx.appendChild(el('div', 'n', l.name));
      tx.appendChild(el('div', 'd', l.desc));
      a.appendChild(ic);
      a.appendChild(tx);
      row.appendChild(a);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
