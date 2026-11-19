/**
 * 학생부(/easy/records) 표시 변환(순수 함수). 브라우저는 window.RecordsView, 테스트(Node)는 module.exports.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RecordsView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  const CHECK = { DONE: '숙제 다 해 옴', PARTIAL: '숙제 일부 해 옴', NOT_DONE: '숙제 안 해 옴' };

  /** ?businessId=1&studentId=2&grading=3 → 문자열 값들(숫자가 아니면 버림). */
  function deepLink(search) {
    const q = new URLSearchParams(search || '');
    const num = k => (/^\d{1,18}$/.test(q.get(k) || '') ? q.get(k) : '');
    return { businessId: num('businessId'), studentId: num('studentId'), grading: num('grading') };
  }

  /** "2026-11-20" → "11월 20일". */
  function day(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso || ''));
    return m ? Number(m[2]) + '월 ' + Number(m[3]) + '일' : String(iso || '');
  }

  /** 수업 기록 한 줄들(비어 있는 칸은 뺀다). */
  function lessonLines(l) {
    const out = [];
    if (l.className) out.push('반: ' + l.className);
    if (l.lessonRange) out.push('수업 범위: ' + l.lessonRange);
    if (l.homeworkCheck) out.push(CHECK[l.homeworkCheck] || l.homeworkCheck);
    if (l.homeworkDoneNote) out.push('해 온 것: ' + l.homeworkDoneNote);
    if (l.nextHomework) out.push('낸 숙제: ' + l.nextHomework);
    if (l.teacherNote) out.push('메모: ' + l.teacherNote);
    return out;
  }

  /** 채점 제목: "11월 20일 · 문법 · 18/20". */
  function gradingHeadline(g) {
    const parts = [day(g.gradedOn)];
    if (g.subjectArea) parts.push(g.subjectArea);
    if (g.material) parts.push(g.material);
    if (g.totalCount != null) parts.push((g.correctCount == null ? '?' : g.correctCount) + '/' + g.totalCount);
    return parts.join(' · ');
  }

  /** 틀린 문항 한 줄: "3번 · 관계대명사 — what/that 혼동". */
  function wrongLine(w) {
    const head = (w.no ? w.no + '번' : '번호 없음') + (w.topic ? ' · ' + w.topic : '');
    return w.reason ? head + ' — ' + w.reason : head;
  }

  /** 오답 기록 요약: "관계대명사 3회 · 시제 1회". */
  function topicsText(topics) {
    if (!Array.isArray(topics) || !topics.length) return '아직 오답 기록이 없습니다.';
    return topics.map(t => t.topic + ' ' + t.count + '회').join(' · ');
  }

  /** "3, 관계대명사, what/that 혼동" 줄들 → [{no, topic, reason}] (빈 줄 무시). */
  function parseWrongText(text) {
    return String(text || '').split('\n').map(s => s.trim()).filter(Boolean).map(line => {
      const [no, topic, ...rest] = line.split(',').map(s => s.trim());
      return { no: no || '', topic: topic || '', reason: rest.join(', ') };
    });
  }

  const LEVEL = { HIGH: '많이 약함', MEDIUM: '보통', LOW: '조금' };
  function levelLabel(level) { return LEVEL[level] || String(level || ''); }

  /** 채점 상태 안내. 채점 전이면 Claude에게 할 말을 알려 준다. */
  function statusText(g) {
    if (g.status === 'PENDING') {
      return g.photos.length ? '채점 전 — 휴대폰 Claude에게 「이 학생 채점해줘」라고 말하세요.' : '채점 전 — 문제지·정답지·학생 답안 사진을 올려 주세요.';
    }
    return '';
  }

  /** 약점 항목 한 줄: "관계대명사 (많이 약함) — 11/20 3·7번". */
  function weaknessLine(t) {
    const head = t.topic + (t.level ? ' (' + levelLabel(t.level) + ')' : '');
    return t.evidence ? head + ' — ' + t.evidence : head;
  }

  return { deepLink, day, lessonLines, gradingHeadline, wrongLine, topicsText, parseWrongText, CHECK, levelLabel,
    statusText, weaknessLine };
});
