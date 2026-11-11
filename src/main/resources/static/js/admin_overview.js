/**
 * /dashboard 관리자 첫 화면(압구정에듀홀릭 ADMIN 전용) — 왼쪽: 이번 달 재무 + 재원 학생·수업 요일, 오른쪽: AI와 의논.
 * - 읽기 전용. 재무는 /api/dashboard/widgets, 학생은 /api/admin/easy/attendance/student-overview.
 * - 오른쪽 AI 대화는 기존 AI 대화(uaCommand 등)를 그대로 쓴다. 예시는 입력칸만 채우고 자동 전송하지 않는다.
 * - ADMIN이 아니면 이 화면을 숨기고 기존 화면을 그대로 둔다(일반 화면은 바꾸지 않음).
 */
(function () {
  'use strict';
  var V = window.AdminOverviewView;
  var state = { biz: null, rows: [], day: '', loading: false };

  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function viewBtn(view) { return document.querySelector('.ops-views [data-view-btn="' + view + '"]'); }

  async function init() {
    if (!$('adminOverview') || !V) return;
    var user = null;
    try { user = await ensureCurrentUser(); } catch (e) { user = null; }
    if (!user || user.role !== 'ADMIN') {
      if (document.body.dataset.view === 'admin' && viewBtn('all')) viewBtn('all').click();
      return;
    }
    document.querySelectorAll('[data-admin-only]').forEach(function (n) { n.hidden = false; });
    document.body.classList.add('is-admin');
    // 관리자는 이 화면이 첫 화면. 주소에 #ai/#all을 붙여 들어오면 그 화면을 존중한다.
    if (!/^#(ai|all)$/.test(location.hash) && viewBtn('admin')) viewBtn('admin').click();
    bind();
    waitForBusiness();
  }

  function bind() {
    $('admSearch').addEventListener('input', renderStudents);
    $('admDays').addEventListener('click', function (e) {
      var b = e.target.closest('[data-day]');
      if (!b) return;
      state.day = state.day === b.dataset.day ? '' : b.dataset.day;
      renderDays();
      renderStudents();
    });
    $('admRefresh').addEventListener('click', function () { if (state.biz) load(state.biz); });
    var chips = $('admAsk');
    V.DISCUSS_EXAMPLES.forEach(function (text) {
      var b = el('button', 'adm-chip', text);
      b.type = 'button';
      b.addEventListener('click', function () {
        var input = $('uaCommand');
        if (!input) return;
        input.value = text;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.focus();
      });
      chips.appendChild(b);
    });
    $('admCopyPrompt').addEventListener('click', function () {
      var text = $('admPrompt').textContent;
      var done = function () { $('admCopyPrompt').textContent = '복사했습니다'; setTimeout(function () { $('admCopyPrompt').textContent = '질문 복사'; }, 1600); };
      if (navigator.clipboard) navigator.clipboard.writeText(text).then(done, function () {});
    });
    var sel = $('bizSelect');
    if (sel) sel.addEventListener('change', function () { if (sel.value) load(sel.value); });
  }

  /** 사업체 선택칸은 다른 스크립트가 채운다. 값이 생기면 한 번 불러온다(최대 10초). */
  function waitForBusiness() {
    var tries = 0;
    var timer = setInterval(function () {
      var sel = $('bizSelect');
      tries++;
      if (sel && sel.value) { clearInterval(timer); load(sel.value); }
      else if (tries > 33) { clearInterval(timer); $('admFinance').replaceChildren(el('p', 'adm-empty', '사업체가 없어 현황을 보여 드릴 수 없습니다.')); }
    }, 300);
  }

  async function load(biz) {
    if (state.loading) return;
    state.biz = biz;
    state.loading = true;
    $('admRefresh').disabled = true;
    $('admFinance').replaceChildren(el('p', 'adm-empty', '불러오는 중…'));
    $('admStudentBody').replaceChildren();
    $('admStudentMsg').textContent = '불러오는 중…';
    var sel = $('bizSelect');
    var name = sel && sel.selectedOptions[0] ? sel.selectedOptions[0].textContent : '';
    $('admBizName').textContent = name;
    $('admPrompt').textContent = V.claudePrompt(name);
    try {
      var results = await Promise.all([
        fetchAuth('/api/dashboard/widgets?businessId=' + encodeURIComponent(biz), { cache: 'no-store' })
          .then(function (r) { return r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status)); })
          .catch(function () { return null; }),
        fetchAuth('/api/admin/easy/attendance/student-overview?businessId=' + encodeURIComponent(biz), { cache: 'no-store' })
          .then(function (r) { return r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status)); })
          .catch(function () { return null; })
      ]);
      renderFinance(results[0]);
      state.rows = Array.isArray(results[1]) ? results[1] : [];
      $('admStudentMsg').textContent = results[1] == null ? '학생 목록을 불러오지 못했습니다. 새로고침을 눌러 주세요.' : '';
      renderDays();
      renderStudents();
    } finally {
      state.loading = false;
      $('admRefresh').disabled = false;
    }
  }

  function renderFinance(data) {
    var box = $('admFinance');
    box.replaceChildren();
    if (!data) { box.appendChild(el('p', 'adm-error', '재무 숫자를 불러오지 못했습니다. 새로고침을 눌러 주세요.')); return; }
    var fin = V.financeCards(data.widgets);
    if (fin.error) { box.appendChild(el('p', 'adm-error', fin.error)); return; }
    if (!fin.cards.length) { box.appendChild(el('p', 'adm-empty', '이번 달 장부 기록이 아직 없습니다.')); return; }
    fin.cards.forEach(function (c) {
      var a = el('a', 'adm-num' + (c.label === '미수금' ? ' warn' : ''));
      a.href = '/easy';
      a.appendChild(el('span', 'l', c.label));
      a.appendChild(el('b', 'v', c.value));
      a.appendChild(el('span', 'h', c.help));
      box.appendChild(a);
    });
  }

  function renderDays() {
    var box = $('admDays');
    box.replaceChildren();
    V.weekdayCounts(state.rows).forEach(function (d) {
      var b = el('button', 'adm-day' + (state.day === d.day ? ' on' : ''));
      b.type = 'button';
      b.dataset.day = d.day;
      b.setAttribute('aria-pressed', state.day === d.day ? 'true' : 'false');
      b.appendChild(el('span', 'd', d.label));
      b.appendChild(el('b', 'c', d.count + '명'));
      box.appendChild(b);
    });
  }

  function renderStudents() {
    var rows = V.filterRows(state.rows, $('admSearch').value, state.day);
    $('admStudentCount').textContent = state.rows.length ? rows.length + ' / ' + state.rows.length + '명' : '';
    var body = $('admStudentBody');
    body.replaceChildren();
    if (!state.rows.length) {
      if (!$('admStudentMsg').textContent) $('admStudentMsg').textContent = '재원 학생이 없습니다. 출결부에서 학생명단을 넣을 수 있습니다.';
      return;
    }
    if (!rows.length) { $('admStudentMsg').textContent = '조건에 맞는 학생이 없습니다.'; return; }
    $('admStudentMsg').textContent = '';
    rows.forEach(function (r) {
      var tr = document.createElement('tr');
      var name = el('td', 'n');
      name.appendChild(el('b', null, r.name));
      var school = V.schoolText(r.school, r.grade);
      if (school !== '-') name.appendChild(el('small', null, school));
      var days = el('td', 'w');
      days.appendChild(el('b', null, V.weekdayText(r.weekdays)));
      days.appendChild(el('small', null, V.sourceNote(r.weekdaySource)));
      tr.append(name, days,
        el('td', 'c', (r.classes || []).join(', ') || '-'),
        el('td', 'm', String(r.monthCheckIns || 0) + '회'),
        el('td', 'd', V.dayText(r.lastCheckIn)));
      tr.children[0].dataset.h = '학생';
      tr.children[1].dataset.h = '수업 요일';
      tr.children[2].dataset.h = '반';
      tr.children[3].dataset.h = '이번 달 등원';
      tr.children[4].dataset.h = '마지막 등원';
      body.appendChild(tr);
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
