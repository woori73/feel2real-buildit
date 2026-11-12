/**
 * F2R 출결부 화면 표시 도우미(순수 함수). 브라우저는 window.EasyAttendanceView, 테스트(Node)는 module.exports.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.EasyAttendanceView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  const TYPES = [
    ['CHECK_IN', '등원', '등'],
    ['LATE', '지각', '지'],
    ['ABSENT', '결석', '결'],
    ['EARLY_LEAVE', '조퇴', '조'],
    ['CHECK_OUT', '하원', '하']
  ];
  const WEEKDAYS = [['MON', '월'], ['TUE', '화'], ['WED', '수'], ['THU', '목'], ['FRI', '금'], ['SAT', '토'], ['SUN', '일']];

  function label(type) { const t = TYPES.find(x => x[0] === type); return t ? t[1] : type; }
  function mark(type) { const t = TYPES.find(x => x[0] === type); return t ? t[2] : '?'; }

  /** 반 한 줄 설명: "월·수 15:00~16:30 · 201호 · 정원 8명". */
  function classLine(c) {
    const days = (c.weekdays || []).map(d => (WEEKDAYS.find(w => w[0] === d) || [d, d])[1]).join('·');
    const time = c.startTime ? c.startTime.slice(0, 5) + (c.endTime ? '~' + c.endTime.slice(0, 5) : '') : '';
    return [days + (time ? ' ' + time : ''), c.room, c.capacity ? '정원 ' + c.capacity + '명' : '', c.teacherName ? '담당 ' + c.teacherName : '']
      .map(s => (s || '').trim()).filter(Boolean).join(' · ');
  }

  /** 시각 표시. 진행표에서 시각 없이 들어온 기록은 "시각 미상". */
  function timeText(ev) {
    if (ev.source === 'ROSTER_IMPORT' && /시각 미상/.test(ev.note || '')) return '시각 미상';
    const m = String(ev.occurredAt || '').match(/T(\d{2}:\d{2})/);
    return m ? m[1] : '';
  }

  /** 월별 표: 학생 × 날짜 칸 표시 글자와 합계. */
  function monthGrid(data) {
    const days = Array.from({ length: data.daysInMonth || 0 }, (_, i) => i + 1);
    const rows = (data.students || []).map(s => ({
      studentId: s.studentId,
      name: s.name,
      cells: days.map(d => ((s.days || {})[d] || (s.days || {})[String(d)] || []).map(mark).join('')),
      counts: TYPES.map(t => (s.counts || {})[t[0]] || 0)
    }));
    return { days, rows, countHeaders: TYPES.map(t => t[1]) };
  }

  /** 진행표 미리보기의 기본 선택: 후보 1명 = 그 학생 연결, 없음 = 새로 등록, 여러 명 = 직접 고르기. */
  function defaultMappings(names) {
    return (names || []).map(n => ({
      name: n.name,
      action: n.suggestedAction === 'MATCH' ? 'MATCH' : n.suggestedAction === 'CREATE' ? 'CREATE' : '',
      studentId: n.suggestedStudentId || null
    }));
  }

  /** 적용 전 확인: 고르지 않은 이름, 연결인데 학생이 없는 줄. */
  function mappingProblems(mappings) {
    const unchosen = mappings.filter(m => !m.action).map(m => m.name);
    const noStudent = mappings.filter(m => m.action === 'MATCH' && !m.studentId).map(m => m.name);
    return { unchosen, noStudent, ok: !unchosen.length && !noStudent.length };
  }

  /** 적용 요약 문장. */
  function applySummary(r) {
    return '새 학생 ' + r.createdStudents + '명 · 기존 학생 연결 ' + r.matchedStudents + '명 · 건너뜀 ' + r.skippedNames
      + '명 · 등원 기록 ' + r.eventsCreated + '건 저장 · 이미 있던 기록 ' + r.eventsAlreadyThere + '건';
  }

  /**
   * 주소의 바로가기: 사업체·반·날짜와 화면 탭. 허용되지 않은 탭은 출결부로 연다.
   */
  function deepLink(search) {
    let q;
    try { q = new URLSearchParams(String(search || '')); } catch (e) { q = new URLSearchParams(); }
    const id = v => (/^[1-9][0-9]{0,18}$/.test(v || '') ? v : null);
    const d = q.get('date');
    const tab = ['sheet', 'month', 'classes', 'import'].includes(q.get('tab')) ? q.get('tab') : 'sheet';
    return { businessId: id(q.get('businessId')), classId: id(q.get('classId')), date: /^\d{4}-\d{2}-\d{2}$/.test(d || '') ? d : null, tab };
  }

  return { deepLink, TYPES, WEEKDAYS, label, mark, classLine, timeText, monthGrid, defaultMappings, mappingProblems, applySummary };
});
