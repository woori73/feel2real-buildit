/* 수업 준비 › 학습지 공통 도우미. 화면(목록·만들기·보기)이 함께 쓰고, node 테스트에서도 그대로 부른다. */
(function (root) {
  'use strict';
  const WS = {};

  WS.STATUS = { DRAFT: '작성 중', ACTIVE: '사용 중', HIDDEN: '숨김' };
  WS.ASSIGN = { NOT_STARTED: '시작 전', IN_PROGRESS: '진행 중', DONE: '완료' };
  WS.SOURCE = { NAESIN: '내신 예상문제', GRAMMAR: '예비고1 어법' };

  WS.esc = v => String(v == null ? '' : v).replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));

  /** 2026-11-22T10:05:00 → 11월 22일 */
  WS.day = iso => { const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || ''); return m ? Number(m[2]) + '월 ' + Number(m[3]) + '일' : ''; };

  /** 후보 중 아직 고르지 않은 문항. */
  WS.remaining = (candidates, pickedRefs) => {
    const used = new Set(pickedRefs || []);
    return (candidates || []).filter(q => !used.has(q.ref));
  };

  /**
   * 남은 후보에서 count개를 고른다. 무작위로 뽑되 결과는 원래 순서(회차·번호 순)로 돌려준다.
   * random은 0 이상 1 미만 수를 주는 함수(테스트에서 바꿔 끼운다).
   */
  WS.pick = (candidates, pickedRefs, count, random) => {
    const left = WS.remaining(candidates, pickedRefs);
    const want = Math.max(0, Math.min(Number(count) || 0, left.length));
    const order = left.map((q, i) => i);
    const rnd = random || Math.random;
    for (let i = order.length - 1; i > 0; i--) {
      const j = Math.floor(rnd() * (i + 1));
      const t = order[i]; order[i] = order[j]; order[j] = t;
    }
    return order.slice(0, want).sort((a, b) => a - b).map(i => left[i]);
  };

  /** 목록의 index번째를 delta(-1 위로, +1 아래로)만큼 옮긴 새 목록. 끝에서는 그대로. */
  WS.move = (list, index, delta) => {
    const to = index + delta;
    if (index < 0 || index >= list.length || to < 0 || to >= list.length) return list.slice();
    const next = list.slice();
    const t = next[index]; next[index] = next[to]; next[to] = t;
    return next;
  };

  /** 바꾸기: 같은 묶음·같은 유형 후보 중 아직 쓰지 않은 첫 문항. 없으면 null. */
  WS.replacement = (current, candidates, pickedRefs) => {
    const used = new Set(pickedRefs || []);
    return (candidates || []).find(q => !used.has(q.ref) && q.setKey === current.setKey && q.type === current.type) || null;
  };

  /** 문항 글을 화면용 HTML로. [[img:/naesin/...]]만 그림으로 바꾸고 나머지는 글자 그대로 보여 준다. */
  WS.rich = text => WS.esc(text || '').replace(/\[\[img:(\/naesin\/[A-Za-z0-9_\-\/.]+)\]\]/g,
    (m, src) => '<img class="ws-q-img" loading="lazy" alt="문항 그림" src="' + src + '">').replace(/\n/g, '<br>');

  /** 학습지 문항 한 개(보기·인쇄·편집 미리보기 공용). no는 학습지 안 번호. */
  WS.questionHtml = (q, no) => {
    const circ = ['①', '②', '③', '④', '⑤'];
    const plain = (q.choices || []).every((c, i) => c === circ[i]);
    const choices = plain ? '' : '<ol class="ws-q-choices">' + (q.choices || []).map((c, i) => '<li><span>' + (circ[i] || '') + '</span> ' + WS.esc(c) + '</li>').join('') + '</ol>';
    const sameBody = q.body && q.body === q.stem;
    return '<article class="ws-q"><div class="ws-q-no">' + no + '</div><div class="ws-q-main">'
      + (q.body && !sameBody && q.source === 'NAESIN' ? '<div class="ws-q-body">' + WS.rich(q.body) + '</div>' : '')
      + '<div class="ws-q-stem">' + WS.rich(q.stem) + '</div>'
      + (q.body && !sameBody && q.source !== 'NAESIN' ? '<div class="ws-q-body">' + WS.rich(q.body) + '</div>' : '')
      + choices + '</div></article>';
  };

  /** 목록 한 줄에 쓰는 배정 요약. */
  WS.assignSummary = a => !a || !(a.notStarted + a.inProgress + a.done) ? '배정 없음'
    : '배정 ' + (a.notStarted + a.inProgress + a.done) + '명 · 시작 전 ' + a.notStarted + ' · 진행 중 ' + a.inProgress + ' · 완료 ' + a.done;

  /** 서버 호출 공통: 실패하면 서버가 준 문장을 그대로 오류로 던진다. */
  WS.json = async (url, opts) => {
    const res = await root.fetchAuth(url, opts);
    const text = await res.text();
    if (!res.ok) { const e = new Error(text || '처리하지 못했습니다.'); e.status = res.status; throw e; }
    return text ? JSON.parse(text) : null;
  };
  WS.body = (method, data) => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
  WS.param = name => new URLSearchParams(root.location ? root.location.search : '').get(name);

  root.WS = WS;
  if (typeof module !== 'undefined' && module.exports) module.exports = WS;
})(typeof window !== 'undefined' ? window : globalThis);
