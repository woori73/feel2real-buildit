/*
 * 내신 실전 연습 목록(/naesintest) — 회차별 최근 점수 표시, 시작/이어 풀기, 로그인 안내.
 */
(function () {
  'use strict';

  async function start(examCode, resumeId) {
    if (!NT.user) { NT.openLogin('/naesintest'); return; }
    if (resumeId) { location.href = '/naesintest/run?attempt=' + resumeId; return; }
    try {
      const a = await NT.api('/api/testprep/attempts', { method: 'POST', body: JSON.stringify({ examCode }) });
      location.href = '/naesintest/run?attempt=' + a.attemptId;
    } catch (e) {
      if (NT.user) alertBox(e.status === 403 ? '1회는 누구나, 2회부터는 압구정에듀홀릭 재원생만 풀 수 있습니다.' : '시험을 시작하지 못했습니다: ' + e.message);
    }
  }

  function alertBox(text) {
    const n = document.createElement('div');
    n.className = 'nt-toast'; n.setAttribute('role', 'status'); n.textContent = text;
    document.body.appendChild(n); setTimeout(() => n.remove(), 4000);
  }

  async function boot() {
    await NT.loadMe();
    const who = document.getElementById('nt-who');
    if (NT.user) who.textContent = (NT.user.name || NT.user.nickname || '회원') + ' 님';
    else {
      who.innerHTML = '<button type="button" class="nt-btn nt-btn-line">로그인</button>';
      who.querySelector('button').addEventListener('click', () => NT.openLogin('/naesintest'));
    }

    const resume = {};
    if (NT.user) {
      try {
        const hist = await NT.api('/api/testprep/attempts');
        const latest = {};
        hist.filter(h => h.kind === 'NAESIN').forEach(h => {
          if (h.status === 'IN_PROGRESS' && !resume[h.examCode]) resume[h.examCode] = h.attemptId;
          if (h.status === 'SUBMITTED' && !latest[h.examCode]) latest[h.examCode] = h;
        });
        document.querySelectorAll('tr[data-exam]').forEach(tr => {
          const h = latest[tr.dataset.exam];
          const cell = tr.querySelector('[data-last]');
          if (h && h.totalScaled != null) {
            cell.classList.remove('is-empty');
            cell.innerHTML = '<a href="/naesintest/review?attempt=' + h.attemptId + '" title="채점 결과 보기">'
              + h.totalScaled + '<small> / ' + (h.totalMax == null ? '-' : h.totalMax) + '</small></a>';
            const meta = tr.querySelector('.nt-meta-m');
            if (meta) meta.textContent += ' · 최근 ' + h.totalScaled + '점';
          }
          if (resume[tr.dataset.exam]) tr.querySelector('[data-start]').textContent = '이어 풀기';
        });
      } catch (e) { /* 기록을 못 불러와도 목록은 쓸 수 있다 */ }
      // 일반 회원은 1회만: 2회부터 시작 단추를 '재원생 전용'으로 바꾼다(서버도 403으로 막는다).
      try {
        const access = await NT.api('/api/naesintest/access');
        if (!access.full) {
          document.querySelectorAll('tr[data-exam]').forEach(tr => {
            if (/-R01$/.test(tr.dataset.exam)) return;
            const b = tr.querySelector('[data-start]');
            if (b) b.outerHTML = '<span class="nt-locked" title="' + NT.esc(access.message) + '">재원생 전용</span>';
          });
        }
      } catch (e) { /* 범위를 못 불러오면 서버가 시작할 때 막는다 */ }
    }

    document.querySelectorAll('[data-start]').forEach(b => b.addEventListener('click', () => {
      b.disabled = true;
      start(b.dataset.start, resume[b.dataset.start]).finally(() => { b.disabled = false; });
    }));

    const params = new URLSearchParams(location.search);
    if (params.get('login') === 'required' && !NT.user) NT.openLogin(NT.safeReturn(params.get('returnUrl')) || '/naesintest');
  }

  document.addEventListener('DOMContentLoaded', boot);
})();
