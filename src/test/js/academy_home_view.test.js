const assert = require('node:assert/strict');
const test = require('node:test');
const v = require('../../main/resources/static/js/academy_home_view.js');

// 학원 홈(/dashboard) 표시 규칙. 예시 값은 테스트용 가상 데이터다.
const CLASSES = [
  { id: 3, name: '중2 B반', weekdays: ['WED'], startTime: '19:00', endTime: '21:00', studentCount: 4, active: true },
  { id: 1, name: '중1 A반', weekdays: ['MON', 'WED'], startTime: '16:00:00', endTime: '18:00:00', description: '문법 3단원', studentCount: 5, active: true },
  { id: 2, name: '고1 C반', weekdays: ['FRI'], startTime: '18:00', endTime: '20:00', active: true },
  { id: 4, name: '시간 미정반', weekdays: [], startTime: null, active: true },
  { id: 5, name: '종료반', weekdays: ['WED'], startTime: '10:00', endTime: '11:00', active: false }
];

test('given 반 목록 when 수요일 수업을 고르면 then 운영 중·시간 등록된 반만 시작 시각순으로 나온다', () => {
  // given / when
  const list = v.lessonsOn(CLASSES, 'WED');
  // then
  assert.deepEqual(list.map((l) => l.name), ['중1 A반', '중2 B반']);
  assert.equal(list[0].start, '16:00');
  assert.equal(list[0].end, '18:00');
  assert.equal(v.unscheduledCount(CLASSES), 1);
});

test('given 수업 중인 시각 when 배너를 고르면 then 진행 중 수업을 보여준다', () => {
  // given
  const clock = { date: '2026-11-11', day: 'WED', minute: 17 * 60 };
  // when
  const h = v.headline(CLASSES, clock);
  // then
  assert.equal(h.kind, 'live');
  assert.equal(h.lesson.classId, 1);
  assert.deepEqual(h.today.map((l) => l.state), ['live', 'upcoming']);
});

test('given 첫 수업 전·수업 사이 when 배너를 고르면 then 다음 예정 수업을 보여준다', () => {
  assert.equal(v.headline(CLASSES, { date: '2026-11-11', day: 'WED', minute: 9 * 60 }).lesson.classId, 1);
  const between = v.headline(CLASSES, { date: '2026-11-11', day: 'WED', minute: 18 * 60 + 30 });
  assert.equal(between.kind, 'next');
  assert.equal(between.lesson.classId, 3);
});

test('given 오늘 수업이 모두 끝남 when 배너를 고르면 then 종료 상태와 다음 등록 일정을 알려준다', () => {
  // given / when
  const h = v.headline(CLASSES, { date: '2026-11-11', day: 'WED', minute: 22 * 60 });
  // then
  assert.equal(h.kind, 'finished');
  assert.equal(h.lesson.classId, 2);
  assert.equal(h.date, '2026-11-13');
});

test('given 오늘 수업이 없고 등록 일정도 없음 when 배너를 고르면 then none + 수업 없음', () => {
  const h = v.headline([], { date: '2026-11-11', day: 'WED', minute: 600 });
  assert.equal(h.kind, 'none');
  assert.equal(h.lesson, null);
});

test('given 시작한 수업의 출결부와 답변 대기 상담 when 학생 챙기기를 만들면 then 미기록 먼저·소수만 노출한다', () => {
  // given
  const today = v.headline(CLASSES, { date: '2026-11-11', day: 'WED', minute: 19 * 60 + 10 }).today;
  const rosters = {
    1: { entries: [{ studentId: 1, status: 'NONE' }, { studentId: 2, status: 'PRESENT' }, { studentId: 3, status: 'NONE' }] },
    3: { entries: [{ studentId: 9, status: 'LATE' }] }
  };
  const consults = [
    { id: 8, status: 'OPEN', studentName: '가상학생B', title: '보강 문의', createdAt: '2026-11-10T09:00:00' },
    { id: 7, status: 'OPEN', studentName: '가상학생A', title: '교재 문의', createdAt: '2026-11-09T09:00:00' },
    { id: 6, status: 'ANSWERED', title: '답변 완료' },
    { id: 5, status: 'OPEN', type: 'NEWS', title: '아이소식은 OPEN에 머문다', createdAt: '2026-11-01T09:00:00' }
  ];
  // when
  const care = v.careItems(today, rosters, consults, '2026-11-11', 2);
  // then
  assert.equal(care.items[0].title, '중1 A반 출결 미기록 2명');
  assert.equal(care.items[0].href, '/academycraft/screens/ATT-03-attendance-roster.html?classId=1&date=2026-11-11');
  assert.equal(care.items[1].href, '/academycraft/screens/CNS-05-consultation-detail.html?id=7');
  assert.equal(care.hidden, 1);
});

test('given 예정 수업·조회 실패 출결부 when 학생 챙기기를 만들면 then 추측해서 넣지 않는다', () => {
  const today = [{ classId: 1, name: 'X', state: 'upcoming', start: '20:00' }, { classId: 2, name: 'Y', state: 'live', start: '18:00' }];
  const care = v.careItems(today, { 1: { entries: [{ status: 'NONE' }] }, 2: { error: true } }, [], '2026-11-11');
  assert.deepEqual(care.items, []);
});

test('given 서버 상태 when 재원생 수를 표시하면 then 정상 응답 정수만 숫자로, 나머지는 상태로 구분한다', () => {
  assert.equal(v.studentCountText('ok', { studentCount: 0 }), '재원생 0명');
  assert.equal(v.studentCountText('ok', { studentCount: 1234 }), '재원생 1,234명');
  assert.equal(v.studentCountText('ok', { studentCount: '2' }), '재원생 조회 실패');
  assert.equal(v.studentCountText('ok', null), '재원생 조회 실패');
  assert.equal(v.studentCountText('forbidden'), '재원생 조회 권한 없음');
  assert.equal(v.studentCountText('unlinked'), '학원 연결 필요');
  assert.equal(v.studentCountText('error'), '재원생 조회 실패');
  assert.equal(v.studentCountText('loading'), '재원생 확인 중');
});

test('given HTTP 코드 when 상태로 바꾸면 then 401·403·기타 실패를 구분한다', () => {
  assert.deepEqual([200, 401, 403, 404, 500].map(v.statusFromHttp), ['ok', 'login', 'forbidden', 'error', 'error']);
});

test('given 학원이사 노트 when 중점 업무를 고르면 then 보관되지 않은 최근 중요 과제 한 건만', () => {
  // given
  const entries = [
    { title: '중요 · 옛 과제', direction: 'WORKING', created_at: '2026-11-01T00:00:00' },
    { title: '중요: 새 과제', direction: 'WORKING', dialogue: '이전 일정', content: '본문', file_id: 'f-1', filename: '계약서.pdf', created_at: '2026-11-10T00:00:00' },
    { title: '중요 보관 과제', direction: 'ARCHIVED', created_at: '2026-11-11T00:00:00' },
    { title: '일반 메모', direction: 'WORKING', created_at: '2026-11-11T01:00:00' }
  ];
  // when
  const task = v.focusTask(entries);
  // then
  assert.equal(task.title, '새 과제');
  assert.equal(task.category, '이전 일정');
  assert.equal(task.file.href, '/api/studio/files/f-1/content?projectId=academy-relocation');
  assert.equal(v.focusTask([{ title: '일반 메모' }]), null);
});

test('given 날짜·시각 when 한국 시간으로 바꾸면 then 날짜·요일·분이 맞다', () => {
  // given: 2026-11-11 15:30 UTC = 2026-11-12 00:30 KST(목)
  const c = v.seoulClock(new Date('2026-11-11T15:30:00Z'));
  // then
  assert.deepEqual(c, { date: '2026-11-12', day: 'THU', minute: 30 });
  assert.equal(v.dateLabel('2026-11-11'), '11월 11일 (수)');
  assert.equal(v.addDays('2026-12-31', 1), '2027-01-01');
  assert.equal(v.minutes('25:00'), null);
});

test('given 필터 when 학생 명단 주소를 만들면 then 반·학생 조건을 쿼리로 넘긴다', () => {
  assert.equal(v.studentsUrl(), '/academycraft/screens/MBR-01-student-list.html');
  assert.equal(v.studentsUrl({ classId: 3 }), '/academycraft/screens/MBR-01-student-list.html?classId=3');
});
