/*
 * 재원생 앱 관리(/easy/student-app, 원장 전용): 접속 코드 발급·재발급·폐기, 시험 배정, 응시 기록을 볼 사람 지정.
 * 코드 원문과 QR은 발급 응답에서 한 번만 받는다(서버에는 해시만 저장).
 */
(function () {
  'use strict';
  const BASE = '/api/admin/easy/student-app';
  const $ = id => document.getElementById(id);
  let current = null;

  const SA = {};
  SA.esc = v => String(v == null ? '' : v).replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));
  SA.when = iso => { const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso || ''); return m ? m[1] + '.' + m[2] + '.' + m[3] + ' ' + m[4] + ':' + m[5] : ''; };
  SA.codeLabel = c => c && c.active ? '사용 중(…' + c.tail + ')' + (c.lastUsedAt ? ' · 최근 ' + SA.when(c.lastUsedAt) : ' · 아직 안 씀') : '없음';
  SA.score = r => r.score == null ? r.correctCount + '/' + r.totalCount : r.score + '/' + r.maxScore + '점';
  SA.duration = sec => Math.floor((sec || 0) / 60) + '분 ' + ((sec || 0) % 60) + '초';

  const bid = () => $('business').value;
  async function api(path, opts) {
    const sep = path.indexOf('?') >= 0 ? '&' : '?';
    const res = await fetchAuth(BASE + path + sep + 'businessId=' + encodeURIComponent(bid()),
      Object.assign({ headers: { 'Content-Type': 'application/json' } }, opts || {}));
    const text = await res.text();
    if (!res.ok) throw new Error(text || '요청을 처리하지 못했습니다.');
    return text ? JSON.parse(text) : null;
  }
  function msg(t) { const m = $('msg'); m.textContent = t || ''; m.className = 'msg err'; m.style.display = t ? 'block' : 'none'; }

  async function loadStudents() {
    const rows = await api('/students');
    $('students').innerHTML = rows.map(r => '<tr><td>' + SA.esc(r.name) + '</td><td>' + SA.esc((r.school || '') + (r.grade ? ' ' + r.grade + '학년' : '')) + '</td>'
      + '<td><span class="tag' + (r.code.active ? ' on' : '') + '">' + SA.esc(SA.codeLabel(r.code)) + '</span></td><td>' + r.assignedCount + '개</td>'
      + '<td><button type="button" data-open="' + r.studentId + '">관리</button></td></tr>').join('')
      || '<tr><td colspan="5" class="muted">재원 중인 학생이 없습니다.</td></tr>';
    $('students').querySelectorAll('[data-open]').forEach(b => b.addEventListener('click', () => open(Number(b.dataset.open))));
  }

  function renderPanel(p) {
    current = p.studentId;
    $('panel').hidden = false;
    $('panelTitle').textContent = p.name + ' 학생';
    $('codeState').textContent = SA.codeLabel(p.code);
    $('issue').textContent = p.code.active ? '코드 재발급' : '코드 발급';
    $('revoke').disabled = !p.code.active;
    $('assigned').innerHTML = p.assignments.map(a => '<li><span>' + SA.esc(a.title) + ' <span class="muted">' + SA.when(a.assignedAt) + ' 배정</span></span>'
      + '<button type="button" data-remove="' + SA.esc(a.examCode) + '">빼기</button></li>').join('') || '<li class="muted">배정한 시험이 없습니다.</li>';
    $('assigned').querySelectorAll('[data-remove]').forEach(b => b.addEventListener('click', async () => {
      try { renderPanel(await api('/students/' + current + '/assignments?examCode=' + encodeURIComponent(b.dataset.remove), { method: 'DELETE' })); loadStudents(); } catch (e) { msg(e.message); }
    }));
    $('results').innerHTML = p.results.map(r => '<li><span>' + SA.esc(r.examTitle) + ' · ' + r.attemptNo + '번째 · <b>' + SA.esc(SA.score(r)) + '</b>'
      + ' <span class="muted">' + SA.when(r.submittedAt) + ' · ' + SA.duration(r.durationSec) + (r.checkedAt ? ' · 점검함' : ' · 새 기록') + '</span></span></li>').join('')
      || '<li class="muted">아직 응시 기록이 없습니다.</li>';
  }

  async function open(studentId) {
    $('issued').hidden = true;
    try { renderPanel(await api('/students/' + studentId)); } catch (e) { msg(e.message); }
  }

  async function issue() {
    if (!current) return;
    const again = $('issue').textContent.indexOf('재발급') >= 0;
    if (again && !confirm('재발급하면 지금 코드는 바로 못 씁니다. 재발급할까요?')) return;
    try {
      const r = await api('/students/' + current + '/code', { method: 'POST' });
      $('qr').innerHTML = r.qrSvg; // 서버(zxing)가 만든 SVG
      $('codeText').textContent = r.code;
      $('codeUrl').textContent = r.appUrl;
      $('issued').hidden = false;
      await open(current); $('issued').hidden = false; loadStudents();
    } catch (e) { msg(e.message); }
  }

  async function revoke() {
    if (!current || !confirm('코드를 폐기하면 학생 앱이 바로 로그아웃됩니다. 응시 기록은 남습니다. 폐기할까요?')) return;
    try { await api('/students/' + current + '/code', { method: 'DELETE' }); $('issued').hidden = true; open(current); loadStudents(); } catch (e) { msg(e.message); }
  }

  async function loadExams() {
    const list = await fetchAuth(BASE + '/exams').then(r => r.json());
    $('exam').innerHTML = list.map(e => '<option value="' + SA.esc(e.examCode) + '">' + SA.esc(e.title) + ' (' + e.questionCount + '문항)</option>').join('');
  }

  async function loadViewers() {
    const list = await api('/viewers');
    renderViewers(list);
  }
  function renderViewers(list) {
    $('viewers').innerHTML = list.map(v => '<li><span>' + SA.esc(v.name || v.loginId) + ' <span class="muted">' + SA.esc(v.loginId) + ' · ' + SA.esc(v.role) + '</span></span>'
      + '<button type="button" data-viewer="' + v.userId + '">해제</button></li>').join('') || '<li class="muted">지정한 사람이 없습니다(원장만 봅니다).</li>';
    $('viewers').querySelectorAll('[data-viewer]').forEach(b => b.addEventListener('click', async () => {
      try { renderViewers(await api('/viewers/' + b.dataset.viewer, { method: 'DELETE' })); } catch (e) { msg(e.message); }
    }));
  }

  async function boot() {
    const businesses = await Easy.loadBusinesses();
    businesses.forEach(b => $('business').add(new Option(b.name, String(b.businessId))));
    $('business').addEventListener('change', () => { $('panel').hidden = true; loadStudents(); loadViewers(); });
    $('issue').addEventListener('click', issue);
    $('revoke').addEventListener('click', revoke);
    $('assign').addEventListener('click', async () => {
      if (!current || !$('exam').value) return;
      try { renderPanel(await api('/students/' + current + '/assignments', { method: 'POST', body: JSON.stringify({ examCode: $('exam').value }) })); loadStudents(); } catch (e) { msg(e.message); }
    });
    $('addViewer').addEventListener('click', async () => {
      try { renderViewers(await api('/viewers', { method: 'POST', body: JSON.stringify({ loginId: $('viewerId').value }) })); $('viewerId').value = ''; } catch (e) { msg(e.message); }
    });
    if (!businesses.length) return;
    await Promise.all([loadStudents(), loadExams(), loadViewers()]);
    const want = new URLSearchParams(location.search).get('studentId');
    if (want) open(Number(want));
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = SA;
  if (typeof document !== 'undefined' && document.addEventListener) document.addEventListener('DOMContentLoaded', () => boot().catch(e => msg(e.message)));
})();
