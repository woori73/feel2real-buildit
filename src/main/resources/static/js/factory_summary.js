/* 제작 공장 요약 카드(업무 대시보드·통합 대시보드 공용). [data-factory-summary] 안의 [data-fs] 칸에 지금 숫자를 채운다.
   숫자는 /api/admin/coord/factory(원장 ADMIN 전용)에서 읽는다. 원장이 아니거나 못 읽으면 숫자 대신 안내 글만 보인다. */
(function (root) {
  'use strict';
  const FS = {};
  const WORKING = ['ANALYSIS', 'SCOPE', 'DRAFT', 'REVIEW', 'REVISE'];

  /** 현황판 응답 → { working: 제작·검수 중, review: 검수 단계(④⑤), approve: 게시 승인 대기, published: 게시됨 } */
  FS.summarize = board => {
    const jobs = (board && board.jobs) || [];
    const count = f => jobs.filter(f).length;
    return {
      working: count(j => WORKING.includes(j.stage) || j.state === 'OWNER_CONFIRM'),
      review: count(j => j.stage === 'REVIEW' || j.stage === 'REVISE'),
      approve: count(j => j.canApprove === true),
      published: count(j => j.state === 'PUBLISHED')
    };
  };

  FS.render = (box, sum) => {
    Object.keys(sum).forEach(k => { const el = box.querySelector('[data-fs="' + k + '"]'); if (el) el.textContent = String(sum[k]); });
    box.querySelectorAll('[data-fs-nums]').forEach(el => { el.hidden = false; });
    const alert = box.querySelector('[data-fs-alert]');
    if (alert) alert.hidden = sum.approve === 0;
  };

  FS.load = async () => {
    const boxes = document.querySelectorAll('[data-factory-summary]');
    if (!boxes.length || !root.fetchAuth) return;
    try {
      const res = await root.fetchAuth('/api/admin/coord/factory', { redirectOnAuthError: false });
      if (!res.ok) throw new Error(String(res.status));
      const sum = FS.summarize(await res.json());
      boxes.forEach(b => FS.render(b, sum));
    } catch (e) {
      boxes.forEach(b => b.querySelectorAll('[data-fs-fallback]').forEach(el => { el.hidden = false; }));
    }
  };

  root.FactorySummary = FS;
  if (typeof module !== 'undefined' && module.exports) module.exports = FS;
  else if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', FS.load); else FS.load();
  }
})(typeof window !== 'undefined' ? window : globalThis);
