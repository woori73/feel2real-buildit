/**
 * 학원 홈(/dashboard) 표시 규칙 — 순수 함수만 둔다. 서버 값은 바꾸지 않고 화면에 쓸 순서·문장·주소만 만든다.
 * 브라우저에서는 window.AcademyHomeView, 테스트(Node)에서는 module.exports로 쓴다.
 *
 * 데이터 원천(모두 AcademyCraft, BuildIt 게이트웨이 경유):
 * - 재원생 수: GET /academycraft/api/rea/{academyId}/dashboard/stats 의 studentCount
 *   (= active 학생 수, 학생 목록 기본 조회와 같은 조건)
 * - 오늘 수업: GET .../classes 의 weekdays·startTime·endTime (운영 중인 반만 내려온다)
 * - 출결 미기록: GET .../attendance/roster?classId=&date= 의 entries[].status === 'NONE'
 * - 후속 상담: GET .../consultations?type=INQUIRY&status=OPEN (학부모 문의 중 학원 답변 대기).
 *   아이소식(NEWS)은 구조상 OPEN에 머물러 제외한다. 상담에 '연락 예정일' 필드는 아직 없다.
 *
 * ADMIN(압구정에듀홀릭 관리자)은 학생 기준을 F2R 장부로 쓴다(사용자 결정 2026-11-11): useF2r(businessId) 후에는
 * 재원생·반·출결을 F2R 장부 학생 원장·F2R 출결부(/api/admin/easy/attendance/**)에서 읽고 링크도 F2R 화면으로 간다.
 * 상담은 F2R에 없으므로 AcademyCraft 그대로다.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AcademyHomeView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  const DAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
  const DAY_LABEL = { MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일' };
  const SCREENS = '/academycraft/screens/';
  /** F2R 모드: { businessId } 또는 null(AcademyCraft). */
  let f2r = null;

  function useF2r(businessId) { f2r = businessId == null ? null : { businessId: String(businessId) }; }
  function isF2r() { return f2r != null; }

  /** F2R 출결부 반 → 홈 수업 계산에 쓰는 반 모양(AcademyCraft 반 목록과 같은 필드 이름). 종료된 반은 뺀다. */
  function f2rClasses(list) {
    return (list || []).filter(c => c && c.status === 'ACTIVE').map(c => ({
      id: c.classId, name: c.name || '', weekdays: Array.isArray(c.weekdays) ? c.weekdays : [],
      startTime: c.startTime || null, endTime: c.endTime || null, room: c.room || '',
      teacherName: c.teacherName || '', description: c.description || '',
      studentCount: Number.isFinite(Number(c.memberCount)) ? Number(c.memberCount) : null, active: true
    }));
  }

  /** F2R 출결부 그날 명단 → { entries:[{studentId,status}] }. 그날 기록이 하나도 없으면 'NONE'(미기록). */
  function f2rRoster(rows) {
    if (!Array.isArray(rows)) return { error: true };
    return { entries: rows.map(r => ({ studentId: r.studentId, status: Array.isArray(r.events) && r.events.length ? 'RECORDED' : 'NONE' })) };
  }

  /** F2R 학생 요약(재원생) → 재원생 수 응답 모양 { studentCount }. */
  function f2rStats(rows) {
    return Array.isArray(rows) ? { studentCount: rows.length } : null;
  }

  /** 최근 4주 등원 요일(없으면 반 요일)에 오늘 요일이 들어 있는 재원생 수. */
  function todayStudentCount(rows, day) {
    return (rows || []).filter(r => r && Array.isArray(r.weekdays) && r.weekdays.includes(day)).length;
  }

  /** 'HH:mm' 또는 'HH:mm:ss' → 자정 이후 분. 형식이 아니면 null. */
  function minutes(hhmm) {
    const m = /^(\d{1,2}):(\d{2})/.exec(String(hhmm == null ? '' : hhmm));
    if (!m) return null;
    const h = Number(m[1]), mm = Number(m[2]);
    return h < 24 && mm < 60 ? h * 60 + mm : null;
  }

  function hhmm(value) {
    const m = /^(\d{1,2}):(\d{2})/.exec(String(value == null ? '' : value));
    return m ? m[1].padStart(2, '0') + ':' + m[2] : '';
  }

  /** 한국 시간 기준 오늘 날짜(YYYY-MM-DD)·요일 코드·현재 분. now는 Date. */
  function seoulClock(now) {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', hourCycle: 'h23', weekday: 'short'
    }).formatToParts(now);
    const get = type => (parts.find(p => p.type === type) || {}).value;
    const day = { Sun: 'SUN', Mon: 'MON', Tue: 'TUE', Wed: 'WED', Thu: 'THU', Fri: 'FRI', Sat: 'SAT' }[get('weekday')];
    return {
      date: get('year') + '-' + get('month') + '-' + get('day'),
      day,
      minute: Number(get('hour')) * 60 + Number(get('minute'))
    };
  }

  /** 'YYYY-MM-DD' → '11월 11일 (수)' */
  function dateLabel(isoDate) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(isoDate || ''));
    if (!m) return '';
    const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])));
    return Number(m[2]) + '월 ' + Number(m[3]) + '일 (' + DAY_LABEL[DAYS[d.getUTCDay()]] + ')';
  }

  function addDays(isoDate, n) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
    const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]) + n));
    return d.toISOString().slice(0, 10);
  }

  /** 운영 중이고 요일·시작 시각이 등록된 반만, 해당 요일 수업으로 시작 시각순 정렬. */
  function lessonsOn(classes, day) {
    return (classes || [])
      .filter(c => c && c.active !== false && Array.isArray(c.weekdays) && c.weekdays.includes(day) && minutes(c.startTime) != null)
      .map(c => ({
        classId: c.id,
        name: c.name || '',
        start: hhmm(c.startTime),
        end: hhmm(c.endTime),
        startMin: minutes(c.startTime),
        endMin: minutes(c.endTime),
        room: c.room || '',
        teacherName: c.teacherName || '',
        description: c.description || '',
        studentCount: Number.isFinite(Number(c.studentCount)) ? Number(c.studentCount) : null
      }))
      .sort((a, b) => a.startMin - b.startMin || String(a.name).localeCompare(String(b.name), 'ko'));
  }

  /** 반별 시간 미등록 수(오늘 목록에 못 넣는 반)를 따로 알린다. */
  function unscheduledCount(classes) {
    return (classes || []).filter(c => c && c.active !== false && (!Array.isArray(c.weekdays) || !c.weekdays.length || minutes(c.startTime) == null)).length;
  }

  /** 수업 상태: 종료(끝 시각 지남) / 진행 중 / 예정. 끝 시각이 없으면 시작 후 진행 중으로 본다. */
  function lessonState(lesson, nowMin) {
    if (nowMin < lesson.startMin) return 'upcoming';
    if (lesson.endMin != null && lesson.endMin > lesson.startMin && nowMin >= lesson.endMin) return 'done';
    if (lesson.endMin == null && nowMin >= 24 * 60) return 'done';
    return 'live';
  }

  /**
   * 배너에 띄울 수업: 진행 중 → 다음 예정 순. 오늘이 모두 끝났거나 없으면 다음 등록 일정을 찾는다(최대 7일).
   * 반환: { kind: 'live'|'next'|'finished'|'none', lesson, date }
   */
  function headline(classes, clock) {
    const today = lessonsOn(classes, clock.day).map(l => ({ ...l, state: lessonState(l, clock.minute) }));
    const live = today.find(l => l.state === 'live');
    if (live) return { kind: 'live', lesson: live, date: clock.date, today };
    const next = today.find(l => l.state === 'upcoming');
    if (next) return { kind: 'next', lesson: next, date: clock.date, today };
    const dayIndex = DAYS.indexOf(clock.day);
    for (let i = 1; i <= 7; i++) {
      const day = DAYS[(dayIndex + i) % 7];
      const first = lessonsOn(classes, day)[0];
      if (first) return { kind: today.length ? 'finished' : 'none', lesson: { ...first, state: 'upcoming' }, date: addDays(clock.date, i), today };
    }
    return { kind: today.length ? 'finished' : 'none', lesson: null, date: null, today };
  }

  function rosterUrl(classId, date) {
    if (f2r) {
      const q = new URLSearchParams({ businessId: f2r.businessId });
      if (classId != null) q.set('classId', classId);
      if (date) q.set('date', date);
      return '/easy/attendance?' + q.toString();
    }
    return SCREENS + 'ATT-03-attendance-roster.html?classId=' + encodeURIComponent(classId) + '&date=' + encodeURIComponent(date);
  }

  function studentsUrl(filter) {
    if (f2r) return '/easy/students';
    const q = new URLSearchParams();
    if (filter && filter.classId != null) q.set('classId', filter.classId);
    if (filter && filter.studentId != null) q.set('studentId', filter.studentId);
    const s = q.toString();
    return SCREENS + 'MBR-01-student-list.html' + (s ? '?' + s : '');
  }

  /** 반/수업은 출결부의 기본 탭 대신 실제 반 관리 탭으로 바로 연다. */
  function classesUrl() {
    return f2r ? rosterUrl(null, null) + '&tab=classes' : SCREENS + 'CLS-01-class-list.html';
  }

  /** 기존 장부 위젯 중 실제 금액·목록만 복구한다. 재원생 수는 학생 원장 기준을 유지한다. */
  function financeView(widgets) {
    const list = Array.isArray(widgets) ? widgets : [];
    const finance = list.find(w => w && w.id === 'easy-finance-summary');
    const source = finance && !finance.error && finance.data && Array.isArray(finance.data.cards) ? finance.data.cards : [];
    const cards = ['수납', '지출', '미수금'].map(label => {
      const card = source.find(c => c && c.label === label);
      return { label, value: card && typeof card.value === 'number' && Number.isFinite(card.value)
        ? card.value.toLocaleString('ko-KR') + '원' : '조회 불가' };
    });
    const lists = ['easy-overdue-students', 'easy-recent-transactions'].map(id => {
      const widget = list.find(w => w && w.id === id);
      const data = widget && !widget.error && widget.data;
      return {
        title: id === 'easy-overdue-students' ? '미수 원생' : '최근 거래',
        error: !data || !Array.isArray(data.items),
        items: data && Array.isArray(data.items) ? data.items.slice(0, 5).map(i => ({
          title: String(i.title || ''), sub: [i.subtitle, i.meta].filter(Boolean).join(' · ')
        })) : [],
        empty: id === 'easy-overdue-students' ? '미수 원생이 없습니다.' : '최근 거래가 없습니다.'
      };
    });
    return { cards, lists };
  }

  /**
   * 학생 챙기기 목록. 이미 시작한 오늘 수업의 출결 미기록을 먼저(이른 수업부터), 그다음 답변 대기 상담(오래된 것부터).
   * rosters: { [classId]: { entries:[{studentId,status}] } | { error:true } }
   */
  function careItems(today, rosters, consultations, date, limit) {
    const items = [];
    for (const l of today || []) {
      if (l.state === 'upcoming') continue;
      const r = rosters && rosters[l.classId];
      if (!r || r.error || !Array.isArray(r.entries)) continue;
      const missing = r.entries.filter(e => e && e.status === 'NONE').length;
      if (missing > 0) items.push({
        kind: 'attendance', title: l.name + ' 출결 미기록 ' + missing + '명',
        sub: l.start + (l.end ? '~' + l.end : '') + ' 수업', href: rosterUrl(l.classId, date), order: l.startMin
      });
    }
    const open = (consultations || [])
      .filter(c => c && c.status === 'OPEN' && c.type !== 'NEWS')
      .sort((a, b) => String(a.lastMessageAt || a.createdAt || '').localeCompare(String(b.lastMessageAt || b.createdAt || '')));
    for (const c of open) items.push({
      kind: 'consult', title: (c.studentName ? c.studentName + ' · ' : '') + (c.title || '상담'),
      sub: '답변 대기 · ' + String(c.lastMessageAt || c.createdAt || '').replace('T', ' ').slice(0, 16),
      href: SCREENS + 'CNS-05-consultation-detail.html?id=' + encodeURIComponent(c.id)
    });
    const max = limit || 5;
    return { items: items.slice(0, max), hidden: Math.max(0, items.length - max) };
  }

  /**
   * 재원생 수 표시. 서버가 정상 응답한 정수일 때만 숫자를 쓰고, 나머지는 상태 문장으로 구분한다.
   * status: 'ok' | 'forbidden' | 'unlinked' | 'error' | 'loading'
   */
  function studentCountText(status, stats) {
    if (status === 'loading') return '재원생 확인 중';
    if (status === 'forbidden') return '재원생 조회 권한 없음';
    if (status === 'unlinked') return '학원 연결 필요';
    if (status !== 'ok' || !stats || !Number.isSafeInteger(stats.studentCount) || stats.studentCount < 0) return '재원생 조회 실패';
    return '재원생 ' + stats.studentCount.toLocaleString('ko-KR') + '명';
  }

  /** 게이트웨이 응답 코드 → 상태. 401은 로그인, 403은 권한, 그 외 실패는 조회 실패. */
  function statusFromHttp(code) {
    if (code >= 200 && code < 300) return 'ok';
    if (code === 401) return 'login';
    if (code === 403) return 'forbidden';
    return 'error';
  }

  /**
   * 이번 주 중점 업무: 학원이사 업무 노트(/api/admin/academy-move/entries)에서 원장이 '중요'로 시작하는 제목으로
   * 등록한 우선 과제 중 보관(direction === 'ARCHIVED')되지 않은 가장 최근 한 건. 학원이사 화면의 우선 과제 표시와 같은 규칙.
   * 없으면 null — 다른 자료를 우선 과제처럼 꾸미지 않는다.
   */
  function focusTask(entries) {
    const active = (entries || []).filter(e => e && e.direction !== 'ARCHIVED' && /^중요/.test(String(e.title || '').trim()));
    active.sort((a, b) => String(b.created_at || '').localeCompare(String(a.created_at || '')));
    const top = active[0];
    if (!top) return null;
    return {
      title: String(top.title).trim().replace(/^중요\s*[·:]?\s*/, '') || String(top.title).trim(),
      category: top.dialogue || '',
      createdAt: top.created_at || '',
      body: top.content || '',
      file: top.file_id ? { name: top.filename || '첨부 원본', href: '/api/studio/files/' + encodeURIComponent(top.file_id) + '/content?projectId=academy-relocation' } : null
    };
  }

  return {
    DAY_LABEL, minutes, hhmm, seoulClock, dateLabel, addDays, lessonsOn, unscheduledCount, lessonState, headline,
    rosterUrl, studentsUrl, classesUrl, financeView, careItems, studentCountText, statusFromHttp, focusTask,
    useF2r, isF2r, f2rClasses, f2rRoster, f2rStats, todayStudentCount
  };
});
