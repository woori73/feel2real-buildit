/*
 * 학생 풀이 첫 화면(/learn): 과목·시험 탭, 내신 탭의 학년·학교 거르기, 「풀기」 → 기존 풀이 화면.
 * 풀이·채점은 기존 /api/testprep 을 그대로 쓴다. 이 파일은 응시를 만들고 풀이 화면 주소로 보내기만 한다.
 */
(function (root) {
  'use strict';
  const LH = {};
  LH.TABS = ['naesin', 'csat', 'ibt', 'sat', 'toeic', 'korean', 'math', 'social', 'science'];

  /** 주소의 tab 값. 모르는 값이면 첫 탭. */
  LH.safeTab = tab => (LH.TABS.includes(tab) ? tab : LH.TABS[0]);

  /** 로그인 뒤 돌아올 주소. 이 화면 안쪽만 쓴다. */
  LH.returnUrl = tab => '/learn?tab=' + LH.safeTab(tab);

  /** 풀이 화면 주소. NAESIN = 디지털 모의고사, SAT = 전용 시험 화면, 그 밖(iBT·TOEIC·어휘) = 공통 시험 화면. */
  LH.runUrl = (run, attemptId) => {
    const id = encodeURIComponent(attemptId);
    if (run === 'NAESIN') return '/naesintest/run?attempt=' + id;
    if (run === 'SAT') return '/testprep/sat/run?attempt=' + id;
    return '/testprep/run?attempt=' + id;
  };

  /** 응시 시작 요청. parts(쉼표로 이은 파트)·tag(유형)는 TOEIC 파트·유형만 골라 풀 때만 싣는다. */
  LH.startBody = d => {
    const body = { examCode: d.exam };
    const parts = String(d.parts || '').split(',').map(s => s.trim()).filter(Boolean);
    if (parts.length) body.taskTypes = parts;
    if (d.tag) body.focusTag = d.tag;
    return body;
  };

  /** 내신 묶음이 고른 학년·학교에 맞는지. 학교·학년이 없는 묶음(재원생 앱)은 「전체」일 때만 보인다. */
  LH.matches = (sec, grade, school) => {
    if (grade && sec.grade !== grade) return false;
    if (school && sec.school !== school) return false;
    return true;
  };

  /** 일반 회원이 풀 수 없는 내신 회차인지(1회 = 코드 끝 -R01만 열려 있다). */
  LH.lockedForMember = (run, examCode) => run === 'NAESIN' && !/-R01$/.test(String(examCode || ''));

  /** 세트 제목 끝 괄호의 우리말 이름. */
  LH.setName = title => { const m = /\(([^()]+)\)\s*$/.exec(String(title || '')); return m ? m[1] : String(title || ''); };

  /** 내 오답 가운데 TOEIC Part 5·6만 세트별로 모은다 → [{examCode, name, ids}]. */
  LH.toeicWrongSets = items => {
    const by = new Map();
    (items || []).forEach(w => {
      if (w.kind !== 'TOEIC' || !/^TOEIC_P[56]$/.test(w.taskType || '')) return;
      if (!by.has(w.examCode)) by.set(w.examCode, { examCode: w.examCode, name: LH.setName(w.examTitle), ids: [] });
      by.get(w.examCode).ids.push(w.questionId);
    });
    return Array.from(by.values());
  };

  LH.esc = v => String(v == null ? '' : v).replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));

  function boot(doc) {
    const main = doc.getElementById('lhMain');
    if (!main) return;
    let tab = LH.safeTab(main.dataset.active);
    let me; // undefined = 아직 모름, null = 비회원
    let wrongLoaded = false;

    async function api(url, opts) {
      const res = await fetch(url, Object.assign({ credentials: 'same-origin', headers: { 'Content-Type': 'application/json' } }, opts || {}));
      if (!res.ok) {
        let msg = res.statusText;
        try { msg = (await res.json()).message || msg; } catch (e) { /* 본문 없음 */ }
        const err = new Error(msg); err.status = res.status; throw err;
      }
      const text = await res.text();
      return text ? JSON.parse(text) : null;
    }

    async function loadMe() {
      if (me !== undefined) return me;
      try { me = await api('/api/auth/me'); } catch (e) { me = null; }
      return me;
    }

    function toast(text) {
      const n = doc.createElement('div');
      n.className = 'lh-toast'; n.setAttribute('role', 'status'); n.textContent = text;
      doc.body.appendChild(n); setTimeout(() => n.remove(), 4500);
    }

    function openLogin() {
      if (doc.getElementById('lhLogin')) return;
      const wrap = doc.createElement('div');
      wrap.id = 'lhLogin'; wrap.className = 'lh-modal';
      wrap.innerHTML = '<div class="lh-modal-card" role="dialog" aria-modal="true" aria-labelledby="lhLoginTitle">'
        + '<h2 id="lhLoginTitle">로그인이 필요합니다</h2><p>문제를 풀고 채점 결과를 저장하려면 로그인해 주세요. 로그인하면 이 화면으로 돌아옵니다.</p>'
        + '<div class="lh-actions"><button type="button" class="lh-btn" data-close>닫기</button>'
        + '<a class="lh-btn primary" data-naver href="/oauth2/authorization/naver">네이버로 로그인</a></div></div>';
      doc.body.appendChild(wrap);
      wrap.querySelector('[data-naver]').addEventListener('click', () => {
        const secure = location.protocol === 'https:' ? '; Secure' : '';
        doc.cookie = 'oauth_return_url=' + encodeURIComponent(LH.returnUrl(tab)) + '; Path=/; Max-Age=300; SameSite=Lax' + secure;
      });
      wrap.querySelector('[data-close]').addEventListener('click', () => wrap.remove());
    }

    async function start(btn, body) {
      if (!(await loadMe())) { openLogin(); return; }
      btn.disabled = true;
      try {
        if (btn.dataset.resume) { location.href = LH.runUrl(btn.dataset.run, btn.dataset.resume); return; }
        const a = await api('/api/testprep/attempts', { method: 'POST', body: JSON.stringify(body || LH.startBody(btn.dataset)) });
        location.href = LH.runUrl(btn.dataset.run || 'TP', a.attemptId);
      } catch (e) {
        btn.disabled = false;
        if (e.status === 401) { me = null; openLogin(); return; }
        toast(e.status === 403 ? '1회는 로그인한 회원 누구나, 2회부터는 압구정에듀홀릭 재원생만 풀 수 있습니다.' : '시작하지 못했습니다: ' + e.message);
      }
    }

    function showTab(next, push) {
      tab = LH.safeTab(next);
      doc.querySelectorAll('[data-tab]').forEach(a => a.setAttribute('aria-selected', String(a.dataset.tab === tab)));
      doc.querySelectorAll('[data-panel]').forEach(p => { p.hidden = p.dataset.panel !== tab; });
      if (push && root.history && root.history.replaceState) root.history.replaceState(null, '', LH.returnUrl(tab));
      if (tab === 'toeic') loadToeicWrong();
    }

    function applyFilter(state) {
      const panel = doc.querySelector('[data-panel="naesin"]');
      if (!panel) return;
      let shown = 0;
      panel.querySelectorAll('.lh-sec').forEach(sec => {
        const ok = LH.matches({ grade: sec.dataset.grade || '', school: sec.dataset.school || '' }, state.grade, state.school);
        sec.hidden = !ok;
        if (ok) shown++;
      });
      const empty = panel.querySelector('[data-filter-empty]');
      if (empty) empty.hidden = shown > 0;
    }

    // 회원이면: 이름 표시, 내신 「이어 풀기」, 일반 회원에게 잠긴 회차 표시
    async function decorate() {
      const who = doc.getElementById('lhWho');
      if (!(await loadMe())) {
        who.innerHTML = '<button type="button" class="lh-btn">로그인</button>';
        who.querySelector('button').addEventListener('click', openLogin);
        return;
      }
      who.textContent = (me.name || me.nickname || me.loginId || '회원') + ' 님';
      try {
        const hist = await api('/api/testprep/attempts');
        const resume = {};
        hist.forEach(h => { if (h.kind === 'NAESIN' && h.status === 'IN_PROGRESS' && !resume[h.examCode]) resume[h.examCode] = h.attemptId; });
        doc.querySelectorAll('button[data-run="NAESIN"]').forEach(b => {
          if (resume[b.dataset.exam]) { b.dataset.resume = resume[b.dataset.exam]; b.textContent = '이어 풀기'; }
        });
      } catch (e) { /* 기록을 못 불러와도 목록은 쓸 수 있다 */ }
      try {
        const access = await api('/api/naesintest/access');
        if (!access.full) {
          doc.querySelectorAll('button[data-run="NAESIN"]').forEach(b => {
            if (!LH.lockedForMember('NAESIN', b.dataset.exam)) return;
            b.disabled = true; b.classList.remove('primary'); b.classList.add('is-locked');
            b.textContent = '재원생 전용'; b.title = access.message || '';
          });
        }
      } catch (e) { /* 범위를 못 불러오면 서버가 시작할 때 막는다 */ }
      if (tab === 'toeic') loadToeicWrong();
    }

    // TOEIC 탭: 내가 틀린 Part 5·6 문항을 세트별로 다시 풀기(기존 오답 다시 풀기 그대로)
    async function loadToeicWrong() {
      const box = doc.getElementById('lhToeicWrong');
      if (!box || wrongLoaded || !me) return;
      wrongLoaded = true;
      try {
        const sets = LH.toeicWrongSets((await api('/api/testprep/learning')).wrongItems);
        if (!sets.length) return;
        box.innerHTML = '<strong>내가 틀린 Part 5·6 문항</strong><div class="lh-actions">'
          + sets.map((s, i) => '<button type="button" class="lh-btn" data-wrong="' + i + '">' + LH.esc(s.name) + ' · 오답 ' + s.ids.length + '문항 다시 풀기</button>').join('')
          + '</div>';
        box.querySelectorAll('[data-wrong]').forEach(b => b.addEventListener('click', () => {
          const s = sets[Number(b.dataset.wrong)];
          start(b, { examCode: s.examCode, questionIds: s.ids });
        }));
        box.hidden = false;
      } catch (e) { wrongLoaded = false; }
    }

    doc.querySelectorAll('[data-tab]').forEach(a => a.addEventListener('click', ev => { ev.preventDefault(); showTab(a.dataset.tab, true); }));
    const state = { grade: '', school: '' };
    ['grade', 'school'].forEach(key => doc.querySelectorAll('button[data-' + key + ']').forEach(b => b.addEventListener('click', () => {
      state[key] = b.dataset[key];
      doc.querySelectorAll('button[data-' + key + ']').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
      applyFilter(state);
    })));
    doc.querySelectorAll('button[data-exam]').forEach(b => b.addEventListener('click', () => start(b)));
    showTab(tab, false);
    decorate();
  }

  root.LearnHub = LH;
  if (typeof module !== 'undefined' && module.exports) module.exports = LH;
  if (typeof document !== 'undefined' && document.addEventListener) document.addEventListener('DOMContentLoaded', () => boot(document));
})(typeof window !== 'undefined' ? window : globalThis);
