/* 새 응시 기록(/dashboard/exam-results): 재원생 앱 응시 기록 — 원장·지정한 강사만. 점검 표시만 하고 지우지 않는다. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  let onlyNew = true;
  const ER = {};
  ER.esc = v => String(v == null ? '' : v).replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));
  ER.when = iso => { const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso || ''); return m ? Number(m[2]) + '월 ' + Number(m[3]) + '일 ' + m[4] + ':' + m[5] : ''; };
  ER.wrong = items => (items || []).map(w => w.no + '번' + (w.topic ? '(' + w.topic + ')' : '')).join(', ');
  ER.item = r => '<li><div class="er-head"><b>' + ER.esc(r.studentName) + ' · ' + ER.esc(r.examTitle) + ' · ' + r.attemptNo + '번째 응시</b>'
    + '<span class="er-score">' + (r.score == null ? r.correctCount + '/' + r.totalCount : r.score + '/' + r.maxScore + '점') + '</span></div>'
    + '<span class="muted">' + ER.when(r.submittedAt) + ' · 정답 ' + r.correctCount + '/' + r.totalCount + ' · 걸린 시간 ' + Math.floor(r.durationSec / 60) + '분 ' + (r.durationSec % 60) + '초'
    + (r.checkedAt ? ' · ' + ER.when(r.checkedAt) + ' 점검' : '') + '</span>'
    + (r.wrongItems && r.wrongItems.length ? '<span class="er-wrong">틀린 문항: ' + ER.esc(ER.wrong(r.wrongItems)) + '</span>' : '<span class="er-wrong">모두 맞혔습니다.</span>')
    + (r.checkedAt ? '' : '<div><button type="button" data-check="' + r.resultId + '">점검함</button></div>') + '</li>';

  async function json(url, opts) {
    const res = await fetchAuth(url, opts);
    const text = await res.text();
    if (!res.ok) throw new Error(text || '불러오지 못했습니다.');
    return text ? JSON.parse(text) : null;
  }
  function msg(t) { $('msg').textContent = t || ''; $('msg').style.display = t ? 'block' : 'none'; }

  async function load() {
    msg('');
    try {
      const d = await json('/api/exam-results?businessId=' + encodeURIComponent($('business').value) + '&onlyNew=' + onlyNew);
      $('newCount').textContent = d.newCount ? '(' + d.newCount + ')' : '';
      $('list').innerHTML = d.results.map(ER.item).join('') || '<li class="muted">' + (onlyNew ? '새 응시 기록이 없습니다.' : '응시 기록이 없습니다.') + '</li>';
      $('list').querySelectorAll('[data-check]').forEach(b => b.addEventListener('click', async () => {
        b.disabled = true;
        try { await json('/api/exam-results/' + b.dataset.check + '/check?businessId=' + encodeURIComponent($('business').value), { method: 'POST' }); load(); }
        catch (e) { b.disabled = false; msg(e.message); }
      }));
    } catch (e) { $('list').innerHTML = ''; msg(e.message); }
  }

  async function boot() {
    const list = await json('/api/exam-results/businesses');
    if (!list.length) { msg('원장님이 지정한 사람만 응시 기록을 볼 수 있습니다.'); return; }
    list.forEach(b => $('business').add(new Option(b.name, String(b.businessId))));
    $('business').addEventListener('change', load);
    $('tabNew').addEventListener('click', () => { onlyNew = true; $('tabNew').setAttribute('aria-pressed', 'true'); $('tabAll').setAttribute('aria-pressed', 'false'); load(); });
    $('tabAll').addEventListener('click', () => { onlyNew = false; $('tabAll').setAttribute('aria-pressed', 'true'); $('tabNew').setAttribute('aria-pressed', 'false'); load(); });
    load();
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = ER;
  if (typeof document !== 'undefined' && document.addEventListener) document.addEventListener('DOMContentLoaded', () => boot().catch(e => msg(e.message)));
})();
