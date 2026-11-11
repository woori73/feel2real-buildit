/**
 * 통합 대시보드(/admin/hub) — 일은 AI에게 부탁하고, 사람은 이번 달 숫자만 한눈에 본다.
 *
 * - 「AI에게 부탁하기」: 적은 글을 /dashboard?ask= 로 넘긴다. AI 업무 비서는 입력칸만 채우고 자동으로 보내지 않는다.
 * - 「한눈에 보기」: 기존 위젯 API(`/api/admin/easy/dashboard/widgets`)를 읽기만 한다(수납·지출·미수금·원생 수 + 미수 학생·최근 거래 5건).
 * - 표시 문장 변환은 hub_view.js(HubView)의 순수 함수가 맡는다.
 */
(function () {
  'use strict';

  var WIDGET_API = '/api/admin/easy/dashboard/widgets';
  var BIZ_API = '/api/admin/easy/businesses';
  var BIZ_KEY = 'hub.bizId';
  var V = window.HubView;

  var state = { bizId: null, loading: false };

  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function fmtTime(iso) {
    if (!iso) return '';
    try { return new Date(iso).toLocaleString('ko-KR', { hour12: false }); } catch (e) { return ''; }
  }
  function bizIdOf(b) { return String(b.businessId != null ? b.businessId : b.id); }

  async function init() {
    var user = await ensureCurrentUser();
    if (!user || user.role !== 'ADMIN') { location.href = '/admin/login'; return; }
    bindAsk();
    $('refreshBtn').addEventListener('click', function () { if (!state.loading) loadSummary(); });
    await loadBusinesses();
    await loadSummary();
  }

  /* ---------- ① AI에게 부탁하기 ---------- */
  function bindAsk() {
    var chips = $('askChips');
    V.ASK_EXAMPLES.forEach(function (text) {
      var b = el('button', 'hub-chip', text);
      b.type = 'button';
      b.addEventListener('click', function () {
        $('askInput').value = text;
        $('askInput').focus();
      });
      chips.appendChild(b);
    });
    $('askForm').addEventListener('submit', function (e) {
      e.preventDefault();
      location.href = V.askUrl($('askInput').value);
    });
  }

  /* ---------- 사업체 ---------- */
  async function loadBusinesses() {
    var sel = $('bizSelect');
    var list = [];
    try {
      var res = await fetchAuth(BIZ_API);
      if (res.ok) list = await res.json();
    } catch (e) { list = []; }
    if (!Array.isArray(list)) list = [];
    sel.innerHTML = '';
    if (list.length === 0) {
      sel.appendChild(el('option', null, '사업체 없음'));
      sel.disabled = true;
      return;
    }
    list.forEach(function (b) {
      var opt = el('option', null, b.name || b.businessName || ('사업체 ' + bizIdOf(b)));
      opt.value = bizIdOf(b);
      sel.appendChild(opt);
    });
    var saved = null;
    try { saved = localStorage.getItem(BIZ_KEY); } catch (e) {}
    var valid = list.some(function (b) { return bizIdOf(b) === String(saved); });
    state.bizId = valid ? String(saved) : bizIdOf(list[0]);
    sel.value = state.bizId;
    sel.addEventListener('change', function () {
      state.bizId = sel.value;
      try { localStorage.setItem(BIZ_KEY, state.bizId); } catch (e) {}
      loadSummary();
    });
  }

  /* ---------- ② 한눈에 보기 (읽기 전용) ---------- */
  function message(cls, text) {
    $('numRow').replaceChildren(el('div', cls, text));
    $('listRow').replaceChildren();
  }

  async function loadSummary() {
    if (state.loading) return;
    if (!state.bizId) { message('hub-empty', '사업체가 없어 숫자를 보여 드릴 수 없습니다. 장부에서 사업체를 먼저 만들어 주세요.'); return; }
    state.loading = true;
    $('refreshBtn').disabled = true;
    message('hub-empty', '불러오는 중…');
    try {
      var res = await fetchAuth(WIDGET_API + '?businessId=' + encodeURIComponent(state.bizId));
      if (!res.ok) throw new Error('HTTP ' + res.status);
      var data = await res.json();
      render(data.widgets || []);
      $('dataAsof').textContent = data.generatedAt ? '기준 ' + fmtTime(data.generatedAt) : '';
    } catch (e) {
      message('hub-error', '숫자를 불러오지 못했습니다. 새로고침을 눌러 주세요.');
      $('dataAsof').textContent = '';
    } finally {
      state.loading = false;
      $('refreshBtn').disabled = false;
    }
  }

  function render(widgets) {
    var sum = V.summaryCards(widgets);
    var nums = $('numRow');
    nums.replaceChildren();
    if (sum.error) nums.appendChild(el('div', 'hub-error', sum.error));
    else if (sum.cards.length === 0) nums.appendChild(el('div', 'hub-empty', '이번 달 장부 기록이 아직 없습니다.'));
    sum.cards.forEach(function (c) {
      var a = el('a', 'hub-num' + (c.label === '미수금' ? ' warn' : ''));
      a.href = c.href;
      a.appendChild(el('div', 'l', c.label));
      a.appendChild(el('div', 'v', c.value));
      a.appendChild(el('div', 'h', c.trend ? c.help + ' · ' + c.trend : c.help));
      nums.appendChild(a);
    });

    var lists = $('listRow');
    lists.replaceChildren();
    ['easy-overdue-students', 'easy-recent-transactions'].forEach(function (id) {
      var p = V.listPreview(widgets, id, 5);
      if (!p) return;
      var box = el('div', 'hub-list-box');
      var head = el('div', 'head');
      head.appendChild(el('b', null, p.title + (p.total ? ' (' + p.total + ')' : '')));
      var more = el('a', null, '자세히 ›');
      more.href = p.href;
      head.appendChild(more);
      box.appendChild(head);
      if (p.error) box.appendChild(el('div', 'hub-error', p.error));
      else if (p.items.length === 0) box.appendChild(el('div', 'hub-empty', p.emptyText));
      else {
        var ul = el('ul');
        p.items.forEach(function (i) {
          var li = el('li', null, i.title);
          if (i.sub) li.appendChild(el('div', 's', i.sub));
          ul.appendChild(li);
        });
        box.appendChild(ul);
      }
      lists.appendChild(box);
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
