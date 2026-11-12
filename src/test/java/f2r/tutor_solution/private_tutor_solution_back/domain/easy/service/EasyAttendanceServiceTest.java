package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAttendanceEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClassMember;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAttendanceEventRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassMemberRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentWeekdayScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.ClassForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.CorrectForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.RecordForm;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** F2R 출결부 서비스 — 메모리 저장소 하네스. 모든 이름은 가짜다. */
class EasyAttendanceServiceTest {

  static final Long BIZ = 1L;
  static final Long OTHER_BIZ = 2L;
  /** 2026-11-10 06:00 UTC = 15:00 KST */
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), EasyAttendanceService.KST);

  final Map<Long, EasyBusiness> businesses = new LinkedHashMap<>();
  final Map<Long, EasyStudent> students = new LinkedHashMap<>();
  final Map<Long, EasyClass> classes = new LinkedHashMap<>();
  final List<EasyClassMember> members = new ArrayList<>();
  final List<EasyAttendanceEvent> events = new ArrayList<>();
  final AtomicLong ids = new AtomicLong(100);

  EasyClassRepository classRepository = lenient(EasyClassRepository.class);
  EasyClassMemberRepository memberRepository = lenient(EasyClassMemberRepository.class);
  EasyAttendanceEventRepository eventRepository = lenient(EasyAttendanceEventRepository.class);
  EasyStudentRepository studentRepository = lenient(EasyStudentRepository.class);
  EasyBusinessRepository businessRepository = lenient(EasyBusinessRepository.class);
  EasyStudentWeekdayScheduleRepository scheduleRepository = lenient(EasyStudentWeekdayScheduleRepository.class);
  EasyAttendanceService service;

  static <T> T lenient(Class<T> type) {
    return mock(type, withSettings().strictness(Strictness.LENIENT));
  }

  @BeforeEach
  void setUp() {
    business(BIZ);
    business(OTHER_BIZ);
    when(businessRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(businesses.get(i.getArgument(0))));
    when(studentRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(students.get(i.getArgument(0))));
    when(studentRepository.findAllByOrderByNameAsc()).thenAnswer(i -> new ArrayList<>(students.values()));
    when(studentRepository.save(any(EasyStudent.class))).thenAnswer(i -> {
      EasyStudent s = i.getArgument(0);
      if (s.getStudentId() == null) ReflectionTestUtils.setField(s, "studentId", ids.incrementAndGet());
      students.put(s.getStudentId(), s);
      return s;
    });
    when(classRepository.save(any(EasyClass.class))).thenAnswer(i -> {
      EasyClass c = i.getArgument(0);
      if (c.getClassId() == null) ReflectionTestUtils.setField(c, "classId", ids.incrementAndGet());
      classes.put(c.getClassId(), c);
      return c;
    });
    when(classRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(classes.get(i.getArgument(0))));
    when(classRepository.findAllByBusiness_BusinessIdOrderByStatusAscNameAsc(any())).thenAnswer(i ->
        classes.values().stream().filter(c -> c.getBusiness().getBusinessId().equals(i.getArgument(0))).toList());
    when(memberRepository.save(any(EasyClassMember.class))).thenAnswer(i -> {
      EasyClassMember m = i.getArgument(0);
      ReflectionTestUtils.setField(m, "memberId", ids.incrementAndGet());
      members.add(m);
      return m;
    });
    when(memberRepository.findAllByEasyClass_ClassIdOrderByMemberIdAsc(any())).thenAnswer(i ->
        members.stream().filter(m -> m.getEasyClass().getClassId().equals(i.getArgument(0))).toList());
    when(memberRepository.findAllByEasyClass_ClassIdAndReleasedOnIsNull(any())).thenAnswer(i ->
        members.stream().filter(m -> m.getEasyClass().getClassId().equals(i.getArgument(0)) && m.isActive()).toList());
    when(eventRepository.save(any(EasyAttendanceEvent.class))).thenAnswer(i -> {
      EasyAttendanceEvent e = i.getArgument(0);
      ReflectionTestUtils.setField(e, "eventId", ids.incrementAndGet());
      events.add(e);
      return e;
    });
    when(eventRepository.saveAll(any())).thenAnswer(i -> {
      List<EasyAttendanceEvent> list = new ArrayList<>();
      for (Object o : (Iterable<?>) i.getArgument(0)) list.add(eventRepository.save((EasyAttendanceEvent) o));
      return list;
    });
    when(eventRepository.findById(any())).thenAnswer(i ->
        events.stream().filter(e -> e.getEventId().equals(i.getArgument(0))).findFirst());
    when(eventRepository.findAllByStudent_StudentIdInAndOccurredOnBetweenOrderByOccurredAtAsc(any(), any(), any()))
        .thenAnswer(i -> {
          Collection<?> sids = i.getArgument(0);
          LocalDate from = i.getArgument(1);
          LocalDate to = i.getArgument(2);
          return events.stream().filter(e -> sids.contains(e.getStudent().getStudentId())
              && !e.getOccurredOn().isBefore(from) && !e.getOccurredOn().isAfter(to)).toList();
        });
    when(eventRepository.findAllBySourceKeyIn(any())).thenAnswer(i -> {
      Collection<?> keys = i.getArgument(0);
      return events.stream().filter(e -> e.getSourceKey() != null && keys.contains(e.getSourceKey())).toList();
    });
    when(eventRepository.findAllByStudent_StudentIdOrderByOccurredAtDesc(any())).thenAnswer(i ->
        events.stream().filter(e -> e.getStudent().getStudentId().equals(i.getArgument(0))).toList());
    service = new EasyAttendanceService(classRepository, memberRepository, eventRepository,
        studentRepository, businessRepository, scheduleRepository, CLOCK);
  }

  void business(Long id) {
    EasyBusiness b = new EasyBusiness("B" + id, "사업체" + id, true, id.intValue());
    ReflectionTestUtils.setField(b, "businessId", id);
    businesses.put(id, b);
  }

  EasyStudent student(String name) {
    return student(name, EasyStudentStatus.ENROLLED);
  }

  EasyStudent student(String name, EasyStudentStatus status) {
    return studentRepository.save(EasyStudent.builder().name(name).grade((byte) 0).status(status).build());
  }

  Long newClass(String name, Integer capacity) {
    return service.createClass(BIZ, new ClassForm(name, "가나", null, List.of("MON", "WED"), "15:00", "16:30",
        "201호", capacity)).classId();
  }

  // ------------------------------------------------------------ 반

  @Test
  @DisplayName("given 이름 없음·잘못된 요일·끝이 시작보다 이름·정원 0 when 반 만들기 then 거절한다")
  void classValidation() {
    // when / then
    assertThatThrownBy(() -> service.createClass(BIZ, new ClassForm(" ", null, null, null, null, null, null, null)))
        .hasMessageContaining("반 이름");
    assertThatThrownBy(() -> service.createClass(BIZ, new ClassForm("A", null, null, List.of("XYZ"), null, null, null, null)))
        .hasMessageContaining("요일");
    assertThatThrownBy(() -> service.createClass(BIZ, new ClassForm("A", null, null, null, "16:00", "15:00", null, null)))
        .hasMessageContaining("늦어야");
    assertThatThrownBy(() -> service.createClass(BIZ, new ClassForm("A", null, null, null, null, null, null, 0)))
        .hasMessageContaining("정원");
  }

  @Test
  @DisplayName("given 반 when 만들고 목록 then 요일·시각·학생 수가 보이고 다른 사업체 목록에는 없다")
  void createAndList() {
    // given
    Long classId = newClass("고1 수학", 8);
    service.assign(BIZ, classId, student("가나").getStudentId());

    // when
    var list = service.listClasses(BIZ, false);

    // then
    assertThat(list).singleElement().satisfies(c -> {
      assertThat(c.weekdays()).containsExactly("MON", "WED");
      assertThat(c.startTime()).isEqualTo("15:00");
      assertThat(c.memberCount()).isEqualTo(1);
    });
    assertThat(service.listClasses(OTHER_BIZ, true)).isEmpty();
  }

  @Test
  @DisplayName("given 정원 1명 반 when 두 번째 학생 배정 then 거절, 같은 학생 재배정·퇴원 학생도 거절")
  void assignRules() {
    // given
    Long classId = newClass("소수정예", 1);
    EasyStudent first = student("가나");
    service.assign(BIZ, classId, first.getStudentId());

    // when / then
    assertThatThrownBy(() -> service.assign(BIZ, classId, student("다라").getStudentId())).hasMessageContaining("정원");
    assertThatThrownBy(() -> service.assign(BIZ, classId, first.getStudentId())).hasMessageContaining("이미");
    Long other = newClass("다른 반", null);
    assertThatThrownBy(() -> service.assign(BIZ, other, student("마바", EasyStudentStatus.WITHDRAWN).getStudentId()))
        .hasMessageContaining("퇴원");
  }

  @Test
  @DisplayName("given 학생 2명 반 when 반 종료 then 배정은 모두 해제되고 종료된 반에는 배정할 수 없다")
  void endClassReleasesMembers() {
    // given
    Long classId = newClass("종료할 반", null);
    service.assign(BIZ, classId, student("가나").getStudentId());
    service.assign(BIZ, classId, student("다라").getStudentId());

    // when
    var ended = service.endClass(BIZ, classId);

    // then
    assertThat(ended.status()).isEqualTo("ENDED");
    assertThat(members).allSatisfy(m -> assertThat(m.getReleaseReason()).isEqualTo("CLASS_ENDED"));
    assertThatThrownBy(() -> service.assign(BIZ, classId, student("사아").getStudentId())).hasMessageContaining("종료");
    assertThat(service.listClasses(BIZ, false)).isEmpty();
    assertThat(service.listClasses(BIZ, true)).hasSize(1);
  }

  @Test
  @DisplayName("given 학생 when 다른 반으로 이동 then 지금 반은 MOVED로 해제되고 대상 반에 배정된다")
  void moveStudent() {
    // given
    Long from = newClass("A반", null);
    Long to = newClass("B반", null);
    Long sid = student("가나").getStudentId();
    service.assign(BIZ, from, sid);

    // when
    service.move(BIZ, from, sid, to);

    // then
    assertThat(service.members(BIZ, from)).isEmpty();
    assertThat(service.members(BIZ, to)).extracting(m -> m.studentId()).containsExactly(sid);
    assertThat(members.get(0).getReleaseReason()).isEqualTo("MOVED");
  }

  // ------------------------------------------------------------ 출결

  @Test
  @DisplayName("given 반 학생 when 시각 없이 등원 기록 then 지금(한국 시각 15:00)으로 저장되고 출결부에 보인다")
  void recordAndSheet() {
    // given
    Long classId = newClass("A반", null);
    Long sid = student("가나").getStudentId();
    service.assign(BIZ, classId, sid);

    // when
    var saved = service.record(BIZ, new RecordForm(classId, sid, "CHECK_IN", null, "정시"));

    // then
    assertThat(saved.occurredAt()).hasToString("2026-11-10T15:00");
    assertThat(saved.label()).isEqualTo("등원");
    var sheet = service.sheet(BIZ, classId, LocalDate.of(2026, 11, 10));
    assertThat(sheet).singleElement().satisfies(r -> assertThat(r.events()).extracting(e -> e.eventType()).containsExactly("CHECK_IN"));
  }

  @Test
  @DisplayName("given 반에 없는 학생·잘못된 구분 when 기록 then 거절한다")
  void recordRejectsNonMemberAndBadType() {
    // given
    Long classId = newClass("A반", null);
    Long outsider = student("가나").getStudentId();

    // when / then
    assertThatThrownBy(() -> service.record(BIZ, new RecordForm(classId, outsider, "CHECK_IN", null, null)))
        .hasMessageContaining("배정돼 있지 않습니다");
    service.assign(BIZ, classId, outsider);
    assertThatThrownBy(() -> service.record(BIZ, new RecordForm(classId, outsider, "PRESENT", null, null)))
        .hasMessageContaining("출결 구분");
  }

  @Test
  @DisplayName("given 등원 기록 when 사유 없이 정정 then 거절, 사유를 적으면 새 행이 생기고 이전 행은 지우지 않고 정정됨으로 남는다")
  void correctKeepsHistory() {
    // given
    Long classId = newClass("A반", null);
    Long sid = student("가나").getStudentId();
    service.assign(BIZ, classId, sid);
    var first = service.record(BIZ, new RecordForm(classId, sid, "CHECK_IN", "2026-11-10T15:00", null));

    // when / then
    assertThatThrownBy(() -> service.correct(BIZ, first.eventId(), new CorrectForm("LATE", " ", null)))
        .hasMessageContaining("사유");
    var fixed = service.correct(BIZ, first.eventId(), new CorrectForm("LATE", "10분 늦게 옴", null));
    assertThat(fixed.correctsEventId()).isEqualTo(first.eventId());
    assertThat(fixed.correctionReason()).isEqualTo("10분 늦게 옴");
    assertThat(events).hasSize(2);
    assertThat(events.get(0).isSuperseded()).isTrue();
    assertThat(service.sheet(BIZ, classId, LocalDate.of(2026, 11, 10)).get(0).events())
        .extracting(e -> e.eventType()).containsExactly("LATE");
    assertThatThrownBy(() -> service.correct(BIZ, first.eventId(), new CorrectForm("ABSENT", "다시", null)))
        .hasMessageContaining("이미 정정");
    assertThatThrownBy(() -> service.correct(OTHER_BIZ, fixed.eventId(), new CorrectForm("ABSENT", "다른 곳", null)))
        .hasMessageContaining("다른 사업체");
    assertThat(service.studentHistory(BIZ, sid)).hasSize(2);
  }

  @Test
  @DisplayName("given 반 없는 진행표 기록과 다른 반 기록 when 출결부 then 반 없는 기록은 보이고 다른 반 기록은 숨긴다")
  void sheetIncludesClasslessButNotOtherClass() {
    // given
    Long a = newClass("A반", null);
    Long b = newClass("B반", null);
    EasyStudent s = student("가나");
    service.assign(BIZ, a, s.getStudentId());
    service.assign(BIZ, b, s.getStudentId());
    service.record(BIZ, new RecordForm(b, s.getStudentId(), "ABSENT", "2026-11-10T10:00", null));
    eventRepository.save(EasyAttendanceEvent.builder().business(businesses.get(BIZ)).student(s)
        .occurredAt(LocalDate.of(2026, 11, 10).atStartOfDay()).eventType(EasyAttendanceEvent.Type.CHECK_IN)
        .source(EasyAttendanceEvent.Source.ROSTER_IMPORT).sourceKey("k").build());

    // when
    var sheetA = service.sheet(BIZ, a, LocalDate.of(2026, 11, 10));

    // then
    assertThat(sheetA.get(0).events()).extracting(e -> e.source()).containsExactly("ROSTER_IMPORT");
  }

  @Test
  @DisplayName("given 한 달 기록 when 월별 현황 then 날짜별 구분과 구분별 횟수를 준다")
  void monthly() {
    // given
    Long classId = newClass("A반", null);
    Long sid = student("가나").getStudentId();
    members.add(new EasyClassMember(classes.get(classId), students.get(sid), LocalDate.of(2026, 10, 1)));
    service.record(BIZ, new RecordForm(classId, sid, "CHECK_IN", "2026-10-05T15:00", null));
    service.record(BIZ, new RecordForm(classId, sid, "LATE", "2026-10-07T15:10", null));
    service.record(BIZ, new RecordForm(classId, sid, "CHECK_IN", "2026-11-02T15:00", null));

    // when
    Map<String, Object> out = service.monthly(BIZ, classId, "2026-10");

    // then
    assertThat(out.get("daysInMonth")).isEqualTo(31);
    @SuppressWarnings("unchecked")
    var row = ((List<Map<String, Object>>) out.get("students")).get(0);
    assertThat(row.get("days")).isEqualTo(new java.util.TreeMap<>(Map.of(5, List.of("CHECK_IN"), 7, List.of("LATE"))));
    assertThat(row.get("counts")).isEqualTo(Map.of("CHECK_IN", 1, "LATE", 1));
    assertThatThrownBy(() -> service.monthly(BIZ, classId, "2026/10")).hasMessageContaining("연월");
  }

  @Test
  @DisplayName("given 다른 사업체의 반 when 출결부 조회 then 거절한다")
  void otherBusinessClassRejected() {
    // given
    Long classId = newClass("A반", null);

    // when / then
    assertThatThrownBy(() -> service.sheet(OTHER_BIZ, classId, LocalDate.of(2026, 11, 10)))
        .hasMessageContaining("다른 사업체");
  }

  // ------------------------------------------------------------ 관리자 대시보드 학생 요약

  @Test
  @DisplayName("given 최근 4주 등원·정정·반 요일·퇴원 학생 when 학생 요약 then 실제 등원 요일 우선, 없으면 반 요일, 정정·퇴원은 빼고 이번 달 횟수를 센다")
  void studentOverview() {
    // given — 오늘 2026-11-10(화), 최근 4주 = 10-14 ~ 11-10
    Long classId = newClass("A반", null); // 월·수
    EasyStudent ganada = student("가나");
    EasyStudent dara = student("다라");
    EasyStudent saa = student("사아");
    student("마바", EasyStudentStatus.WITHDRAWN);
    service.assign(BIZ, classId, ganada.getStudentId());
    service.assign(BIZ, classId, dara.getStudentId());
    members.forEach(m -> ReflectionTestUtils.setField(m, "assignedOn", LocalDate.of(2026, 10, 1)));
    service.record(BIZ, new RecordForm(classId, ganada.getStudentId(), "CHECK_IN", "2026-10-09T15:00", null)); // 목, 4주 밖
    service.record(BIZ, new RecordForm(classId, ganada.getStudentId(), "CHECK_IN", "2026-11-02T15:00", null)); // 월
    var wed = service.record(BIZ, new RecordForm(classId, ganada.getStudentId(), "CHECK_IN", "2026-11-04T15:00", null)); // 수
    service.record(BIZ, new RecordForm(classId, ganada.getStudentId(), "CHECK_IN", "2026-11-06T15:00", null)); // 금
    service.correct(BIZ, wed.eventId(), new CorrectForm("ABSENT", "실제로는 결석", null));

    // when
    var rows = service.studentOverview(BIZ);

    // then
    assertThat(rows).extracting(r -> r.name()).containsExactly("가나", "다라", "사아");
    var g = rows.get(0);
    assertThat(g.weekdays()).containsExactly("MON", "FRI");
    assertThat(g.weekdaySource()).isEqualTo("ATTENDANCE");
    assertThat(g.monthCheckIns()).isEqualTo(2);
    assertThat(g.lastCheckIn()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(g.classes()).containsExactly("A반");
    var d = rows.get(1);
    assertThat(d.weekdays()).containsExactly("MON", "WED");
    assertThat(d.weekdaySource()).isEqualTo("CLASS");
    assertThat(d.monthCheckIns()).isZero();
    assertThat(d.lastCheckIn()).isNull();
    var none = rows.get(2);
    assertThat(none.weekdays()).isEmpty();
    assertThat(none.weekdaySource()).isNull();
    assertThat(none.classes()).isEmpty();
  }

  @Test
  @DisplayName("given 없는 사업체 when 학생 요약 then 거절한다")
  void studentOverviewNeedsBusiness() {
    // when / then
    assertThatThrownBy(() -> service.studentOverview(99L)).hasMessageContaining("사업체");
  }
}
