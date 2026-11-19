/**
 * AI 코디네이터 승인 화면(/admin/coord/approvals) 표시 변환(순수 함수). 브라우저는 window.CoordView, 테스트(Node)는 module.exports.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.CoordView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  const EVENT_LABEL = { CHECK_IN: '등원', CHECK_OUT: '하원', ABSENT: '결석', LATE: '지각', EARLY_LEAVE: '조퇴' };
  const STATUS_LABEL = {
    review_required: '승인 대기', approved: '처리 중', completed: '완료', rejected: '거절함',
    expired: '시간 초과', failed: '실패', cancelled: '취소함'
  };

  function eventLabel(type) { return EVENT_LABEL[type] || String(type || ''); }
  function statusLabel(status) { return STATUS_LABEL[status] || String(status || ''); }

  /** "2026-11-19T16:05" → "11월 19일 16:05". 형식이 다르면 그대로. */
  function when(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(String(iso || ''));
    if (!m) return String(iso || '');
    return Number(m[2]) + '월 ' + Number(m[3]) + '일 ' + m[4] + ':' + m[5];
  }

  /** 승인 화면 한 줄 요약: "김민수 학생 · 등원 · 11월 19일 16:05". */
  function headline(job) {
    const s = (job && job.summary) || {};
    return (s.student || '') + ' 학생 · ' + eventLabel(s.eventType) + ' · ' + when(s.occurredAt);
  }

  /** 학교·학년·반 한 줄. 동명이인 확인용. */
  function detailLine(job) {
    const s = (job && job.summary) || {};
    const parts = [];
    if (s.school) parts.push(s.school);
    if (s.grade != null) parts.push(s.grade + '학년');
    if (Array.isArray(s.classes) && s.classes.length) parts.push(s.classes.join(', '));
    return parts.join(' · ');
  }

  function canDecide(job) { return !!job && job.status === 'review_required'; }

  /** 결과 문장(완료·실패·거절·시간 초과). */
  function resultText(job) {
    if (!job) return '';
    if (job.status === 'completed' && job.result) return job.result.label + ' 기록 완료 (' + when(job.result.at) + ')';
    if (job.status === 'failed') return '기록하지 못했습니다: ' + (job.error || '알 수 없는 오류');
    if (job.status === 'rejected') return '거절했습니다. 기록하지 않았습니다.';
    if (job.status === 'cancelled') return '기록했다가 대화창에서 취소했습니다.';
    if (job.status === 'expired') return '승인 시간(30분)이 지나 기록하지 않았습니다. AI에게 다시 요청해 주세요.';
    return '';
  }

  return { eventLabel, statusLabel, when, headline, detailLine, canDecide, resultText };
});
