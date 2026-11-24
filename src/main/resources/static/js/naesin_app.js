/*
 * 재원생 앱(/naesintest/app) — 접속 코드 로그인 → 원장이 배정한 시험 → 내 응시 기록.
 * - 이름·연락처는 받지 않는다. 코드는 원장이 학생부에서 발급한다(QR은 /naesintest/app#code=XXXX 형식).
 * - #code= 뒤의 코드는 서버로 가지 않는 주소 조각(hash)이라 서버 기록에 남지 않고, 읽은 뒤 주소창에서 지운다.
 * - 시험·해설은 기존 화면을 앱 모드로 쓴다: 내신은 /naesintest/app/run·review,
 *   TOEIC·iBT·어휘는 /naesintest/app/tp-run, SAT은 /naesintest/app/sat-run, 둘의 해설은 /naesintest/app/tp-review(2026-11-24).
 */
(function () {
  'use strict';
  const NA = {};
  const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

  /** '#code=ab12-cd34' → 'AB12CD34'. 형식이 틀리면 null. */
  NA.codeFromHash = function (hash) {
    const m = /(?:^#|&)code=([^&]+)/.exec(String(hash || ''));
    return m ? NA.normalize(decodeURIComponent(m[1])) : null;
  };

  /** 대문자·하이픈/공백 제거, 8글자이고 쓰는 글자만 있으면 그 값, 아니면 null(서버 StudentAppCodeService.normalize와 같은 규칙). */
  NA.normalize = function (raw) {
    const v = String(raw || '').toUpperCase().replace(/[\s-]/g, '');
    if (v.length !== 8) return null;
    for (const ch of v) if (ALPHABET.indexOf(ch) < 0) return null;
    return v;
  };

  NA.duration = function (sec) {
    sec = Math.max(0, Math.floor(Number(sec) || 0));
    return Math.floor(sec / 60) + '분 ' + (sec % 60) + '초';
  };

  NA.when = function (iso) {
    if (!iso) return '';
    const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso);
    return m ? Number(m[2]) + '월 ' + Number(m[3]) + '일 ' + m[4] + ':' + m[5] : iso;
  };

  NA.esc = v => String(v == null ? '' : v).replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));

  // 시험 종류 → 화면 이름·응시 화면·해설 화면(서버 StudentExamAdminService.KIND_LABELS와 같은 이름).
  const KIND_LABELS = { TOEIC: 'TOEIC', PRACTICE: 'iBT', VOCAB: '어휘', SAT: 'SAT' };
  NA.kindLabel = kind => KIND_LABELS[kind] || '';
  NA.runUrl = function (kind, attemptId) {
    const base = !kind || kind === 'NAESIN' ? '/naesintest/app/run' : (kind === 'SAT' ? '/naesintest/app/sat-run' : '/naesintest/app/tp-run');
    return base + '?attempt=' + encodeURIComponent(attemptId);
  };
  NA.reviewUrl = function (kind, attemptId) {
    return (!kind || kind === 'NAESIN' ? '/naesintest/app/review' : '/naesintest/app/tp-review') + '?attempt=' + encodeURIComponent(attemptId);
  };

  NA.score = (s, max) => (s == null ? '' : s + (max == null ? '' : ' / ' + max) + '점');

  NA.examItem = function (e) {
    const meta = [e.questionCount + '문항'];
    if (NA.kindLabel(e.kind)) meta.unshift(NA.kindLabel(e.kind));
    if (e.attempts) meta.push(e.attempts + '번 응시');
    if (e.lastScore != null) meta.push('최근 ' + NA.score(e.lastScore, e.lastMax));
    const label = e.inProgressAttemptId ? '이어서 풀기' : (e.attempts ? '다시 풀기' : '시험 보기');
    return '<li><b>' + NA.esc(e.title) + '</b><div class="na-row"><span class="na-meta">' + NA.esc(meta.join(' · ')) + '</span>'
      + '<button type="button" class="na-btn" data-start="' + NA.esc(e.examCode) + '" data-kind="' + NA.esc(e.kind || '') + '">' + label + '</button></div></li>';
  };

  NA.resultItem = function (r) {
    return '<li><b>' + NA.esc(r.examTitle) + '</b><div class="na-row"><span class="na-score">' + NA.esc(NA.score(r.score, r.maxScore)) + '</span>'
      + '<span class="na-meta">' + r.attemptNo + '번째 응시 · ' + NA.esc(NA.when(r.submittedAt)) + ' · ' + r.correctCount + '/' + r.totalCount + ' 정답 · ' + NA.duration(r.durationSec) + '</span></div>'
      + '<a class="na-btn is-line" href="' + NA.esc(NA.reviewUrl(r.kind, r.attemptId)) + '">틀린 문제 해설</a></li>';
  };

  async function call(url, opts) {
    const res = await fetch(url, Object.assign({ credentials: 'same-origin', headers: { 'Content-Type': 'application/json' } }, opts || {}));
    let body = null;
    try { body = res.status === 204 ? null : await res.json(); } catch (e) { /* 본문 없음 */ }
    if (!res.ok) { const err = new Error((body && body.message) || '잠시 뒤에 다시 시도해 주세요.'); err.status = res.status; throw err; }
    return body;
  }

  function $(id) { return document.getElementById(id); }
  function status(t) { $('naStatus').textContent = t || ''; }

  async function load() {
    try {
      const me = await call('/api/naesintest/app/me');
      $('naLogin').hidden = true; $('naHome').hidden = false;
      $('naName').textContent = me.studentName || '';
      $('naExams').innerHTML = me.exams.length ? me.exams.map(NA.examItem).join('')
        : '<li class="na-empty">아직 배정된 시험이 없습니다. 원장님께 말씀해 주세요.</li>';
      $('naResults').innerHTML = me.results.length ? me.results.map(NA.resultItem).join('')
        : '<li class="na-empty">아직 응시 기록이 없습니다.</li>';
      $('naExams').querySelectorAll('[data-start]').forEach(b => b.addEventListener('click', () => start(b)));
    } catch (e) {
      $('naHome').hidden = true; $('naLogin').hidden = false;
      if (e.status && e.status !== 401 && e.status !== 403) status(e.message);
    }
  }

  async function login(raw) {
    const code = NA.normalize(raw);
    if (!code) { status('코드는 8글자입니다(예: AB12-CD34).'); return; }
    status('');
    try { await call('/api/naesintest/app/login', { method: 'POST', body: JSON.stringify({ code }) }); await load(); }
    catch (e) { status(e.message); }
  }

  async function start(btn) {
    btn.disabled = true;
    try {
      const a = await call('/api/naesintest/app/attempts', { method: 'POST', body: JSON.stringify({ examCode: btn.dataset.start }) });
      location.href = NA.runUrl(a.kind || btn.dataset.kind, a.attemptId);
    } catch (e) { btn.disabled = false; status(e.message); }
  }

  function wireInstall() {
    const standalone = window.matchMedia && window.matchMedia('(display-mode: standalone)').matches;
    if (standalone || navigator.standalone) { $('naInstall').hidden = true; return; }
    let deferred = null;
    window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); deferred = e; $('naInstallBtn').hidden = false; });
    $('naInstallBtn').addEventListener('click', async () => {
      if (!deferred) return;
      deferred.prompt(); await deferred.userChoice.catch(() => null); deferred = null; $('naInstallBtn').hidden = true;
    });
  }

  async function boot() {
    wireInstall();
    $('naLoginForm').addEventListener('submit', e => { e.preventDefault(); login($('naCode').value); });
    $('naLogout').addEventListener('click', async () => {
      try { await call('/api/naesintest/app/logout', { method: 'POST' }); } catch (e) { /* 그래도 로그인 칸을 보인다 */ }
      $('naHome').hidden = true; $('naLogin').hidden = false; $('naCode').value = '';
    });
    const fromQr = NA.codeFromHash(location.hash);
    if (location.hash) history.replaceState(null, '', location.pathname + location.search); // 코드를 주소창에 남기지 않는다
    if (fromQr) await login(fromQr); else await load();
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = NA;
  if (typeof document !== 'undefined' && document.addEventListener) document.addEventListener('DOMContentLoaded', boot);
})();
