/* 제작 공장(/dashboard/factory) 공통 도우미. 화면이 쓰고 node 테스트에서도 그대로 부른다. 여기서 만드는 HTML에는 서버 글을 넣지 않는다(숫자와 고정 이름만). */
(function (root) {
  'use strict';
  const PF = {};

  PF.STAGES = ['ANALYSIS', 'SCOPE', 'DRAFT', 'REVIEW', 'REVISE', 'PUBLISH'];
  /* 진행 띠의 좁은 칸에 들어가는 짧은 이름(정식 이름은 서버가 준다). */
  PF.STAGE_LABEL = { ANALYSIS: '기출', SCOPE: '범위', DRAFT: '제작', REVIEW: '검수', REVISE: '수정', PUBLISH: '게시' };

  /**
   * 시험 범위 글(한 줄에 구간 하나, 쉼표도 됨)을 구간 목록으로. 「12~45」「72-83쪽」「92」를 받는다.
   * 돌려주는 값: { lines: ['12~45', …](서버에 보낼 글), error: 첫 잘못된 줄 안내(없으면 '') }. 서버가 같은 규칙으로 다시 확인한다.
   */
  PF.parseRanges = text => {
    const lines = [], seen = [];
    for (const raw of String(text || '').split(/[\n,]/)) {
      const v = raw.trim();
      if (!v) continue;
      const m = /^(\d{1,4})(?:\s*[~\-–]\s*(\d{1,4}))?\s*(?:쪽|p|P)?$/.exec(v);
      if (!m) return { lines: [], error: '「' + v + '」 — 「12~45」처럼 쪽수로 적어 주세요.' };
      const from = Number(m[1]), to = m[2] == null ? from : Number(m[2]);
      if (from < 1 || to > 2000 || from > to) return { lines: [], error: '「' + v + '」 — 시작 쪽이 끝 쪽보다 클 수 없습니다(1~2000쪽).' };
      if (seen.some(r => from <= r[1] && to >= r[0])) return { lines: [], error: '「' + v + '」 — 다른 구간과 겹칩니다.' };
      seen.push([from, to]);
      lines.push(from === to ? String(from) : from + '~' + to);
    }
    return { lines, error: '' };
  };

  /** 구간 목록의 쪽수 합계(안내 문장용). */
  PF.pageCount = lines => (lines || []).reduce((n, l) => { const p = String(l).split('~').map(Number); return n + (p.length > 1 ? p[1] - p[0] + 1 : 1); }, 0);

  /** 목록 거르기: 단계·구분·검색어. 취소·실패(stage 없음)는 showClosed일 때만. */
  PF.filter = (jobs, f) => (jobs || []).filter(j => {
    if (!j.stage && !(f && f.showClosed)) return false;
    if (f && f.stage && j.stage !== f.stage) return false;
    if (f && f.track && j.track !== f.track) return false;
    const word = f && f.word ? f.word.trim().toLowerCase() : '';
    return !word || [j.title, j.exam, j.trackLabel, j.ranges, j.makerSession].join(' ').toLowerCase().includes(word);
  });

  /** 여섯 칸 진행 띠: 지난 단계 done, 지금 단계 now. 공정 밖(취소·실패)이면 모두 꺼짐. */
  PF.track = j => '<ol class="pf-track" aria-label="공정 ' + (j.stageNo || 0) + '/6">' + PF.STAGES.map((s, i) =>
    '<li class="' + (!j.stageNo ? '' : i + 1 < j.stageNo ? 'done' : i + 1 === j.stageNo ? 'now' : '') + '"><i>' + (i + 1) + '</i><span>' + PF.STAGE_LABEL[s] + '</span></li>').join('') + '</ol>';

  /** 검수 n/3 표시. 검수 없는 종류면 '-' */
  PF.reviewBadge = j => !j.reviewed ? '-' : '<span class="pf-review' + (j.reviewsPassed >= j.reviewsRequired ? ' ok' : '') + '">'
    + j.reviewsPassed + '/' + j.reviewsRequired + '</span>';

  /** 요청 화면 값 → 서버에 보낼 내용. 비운 칸은 보내지 않는다. */
  PF.payload = (v, requestId) => {
    const out = { requestId, track: v.track, type: v.type, preferredWorker: v.worker, ranges: PF.parseRanges(v.ranges).lines, itemLabels: v.labels || [] };
    [['school', v.school], ['grade', v.grade], ['semester', v.semester], ['exam', v.exam], ['pastExamRef', v.past], ['scopeRef', v.scope], ['instructions', v.note]]
      .forEach(([k, val]) => { if (val && String(val).trim()) out[k] = String(val).trim(); });
    if (v.year) out.year = Number(v.year);
    if (v.count) out.count = Number(v.count);
    return out;
  };

  /** 같은 요청을 두 번 눌러도 한 건만 생기게 하는 요청 번호(8~64자 영문·숫자·-). */
  PF.requestId = (now, random) => 'web-' + (now || Date.now()).toString(36) + '-' + Math.floor((random || Math.random)() * 1e9).toString(36);

  root.PF = PF;
  if (typeof module !== 'undefined' && module.exports) module.exports = PF;
})(typeof window !== 'undefined' ? window : globalThis);
