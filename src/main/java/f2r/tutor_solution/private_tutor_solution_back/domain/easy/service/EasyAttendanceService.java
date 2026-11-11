package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F2R 출결부(반별 출결부) — 반·반 배정·출결 기록·정정·월별 현황. 학생은 장부 학생 원장(easy_students)을 그대로 쓴다.
 *
 * <p>삭제 정책: 출결은 지우지 않고 정정(새 행 + 사유, 이전 행 superseded) / 반은 종료 / 반 배정은 해제일을 남긴다.
 * 권한(ADMIN·사업체)은 컨트롤러에서 먼저 확인한다.
 */
@Service
@Transactional
public class EasyAttendanceService {

  static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Set<String> WEEKDAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

  private final EasyClassRepository classRepository;
  private final EasyClassMemberRepository memberRepository;
  private final EasyAttendanceEventRepository eventRepository;
  private final EasyStudentRepository studentRepository;
  private final EasyBusinessRepository businessRepository;
  private final Clock clock;

  @Autowired
  public EasyAttendanceService(EasyClassRepository classRepository,
      EasyClassMemberRepository memberRepository, EasyAttendanceEventRepository eventRepository,
      EasyStudentRepository studentRepository, EasyBusinessRepository businessRepository) {
    this(classRepository, memberRepository, eventRepository, studentRepository, businessRepository,
        Clock.system(KST));
  }

  EasyAttendanceService(EasyClassRepository classRepository,
      EasyClassMemberRepository memberRepository, EasyAttendanceEventRepository eventRepository,
      EasyStudentRepository studentRepository, EasyBusinessRepository businessRepository, Clock clock) {
    this.classRepository = classRepository;
    this.memberRepository = memberRepository;
    this.eventRepository = eventRepository;
    this.studentRepository = studentRepository;
    this.businessRepository = businessRepository;
    this.clock = clock;
  }

  // ------------------------------------------------------------------ 요청·응답

  public record ClassForm(String name, String teacherName, String description, List<String> weekdays,
      String startTime, String endTime, String room, Integer capacity) {}

  public record ClassView(Long classId, String name, String teacherName, String description,
      List<String> weekdays, String startTime, String endTime, String room, Integer capacity,
      String status, int memberCount) {}

  public record MemberView(Long studentId, String name, String school, Integer grade,
      String studentStatus, LocalDate assignedOn) {}

  public record EventView(Long eventId, String eventType, String label, LocalDateTime occurredAt,
      String note, String source, Long correctsEventId, String correctionReason) {}

  public record SheetRow(Long studentId, String name, String school, Integer grade,
      List<EventView> events) {}

  public record RecordForm(Long classId, Long studentId, String eventType, String occurredAt,
      String note) {}

  public record CorrectForm(String eventType, String reason, String note) {}

  // ------------------------------------------------------------------ 반

  @Transactional(readOnly = true)
  public List<ClassView> listClasses(Long businessId, boolean includeEnded) {
    requireBusiness(businessId);
    return classRepository.findAllByBusiness_BusinessIdOrderByStatusAscNameAsc(businessId).stream()
        .filter(c -> includeEnded || c.isActive())
        .map(c -> view(c, memberRepository.findAllByEasyClass_ClassIdAndReleasedOnIsNull(c.getClassId()).size()))
        .toList();
  }

  public ClassView createClass(Long businessId, ClassForm form) {
    EasyBusiness business = requireBusiness(businessId);
    Normalized n = normalize(form);
    EasyClass saved = classRepository.save(EasyClass.builder().business(business).name(n.name)
        .teacherName(n.teacher).description(n.description).weekdays(n.weekdays)
        .startTime(n.start).endTime(n.end).room(n.room).capacity(n.capacity).build());
    return view(saved, 0);
  }

  public ClassView updateClass(Long businessId, Long classId, ClassForm form) {
    EasyClass c = requireActiveClass(businessId, classId);
    Normalized n = normalize(form);
    int members = memberRepository.findAllByEasyClass_ClassIdAndReleasedOnIsNull(classId).size();
    if (n.capacity != null && n.capacity < members) {
      throw new IllegalArgumentException("정원(" + n.capacity + "명)이 지금 배정된 학생 수(" + members + "명)보다 적습니다.");
    }
    c.update(n.name, n.teacher, n.description, n.weekdays, n.start, n.end, n.room, n.capacity);
    return view(c, members);
  }

  /** 반 종료: 지우지 않는다. 배정은 모두 해제(CLASS_ENDED)하고 출결 기록은 그대로 남긴다. */
  public ClassView endClass(Long businessId, Long classId) {
    EasyClass c = requireActiveClass(businessId, classId);
    LocalDate today = LocalDate.now(clock);
    for (EasyClassMember m : memberRepository.findAllByEasyClass_ClassIdAndReleasedOnIsNull(classId)) {
      m.release(today, "CLASS_ENDED");
    }
    c.end(LocalDateTime.now(clock));
    return view(c, 0);
  }

  // ------------------------------------------------------------------ 반 배정

  @Transactional(readOnly = true)
  public List<MemberView> members(Long businessId, Long classId) {
    requireClass(businessId, classId);
    return memberRepository.findAllByEasyClass_ClassIdAndReleasedOnIsNull(classId).stream()
        .map(m -> memberView(m.getStudent(), m.getAssignedOn()))
        .sorted(java.util.Comparator.comparing(MemberView::name))
        .toList();
  }

  public MemberView assign(Long businessId, Long classId, Long studentId) {
    return assignOn(businessId, classId, studentId, LocalDate.now(clock));
  }

  /** 배정일을 지정한다(진행표 가져오기는 첫 수업일로 배정). 정원을 넘기면 거절한다. */
  public MemberView assignOn(Long businessId, Long classId, Long studentId, LocalDate assignedOn) {
    EasyClass c = requireActiveClass(businessId, classId);
    EasyStudent s = requireStudent(studentId);
    if (s.getStatus() == EasyStudentStatus.WITHDRAWN) {
      throw new IllegalArgumentException("퇴원(비활성) 학생은 반에 배정할 수 없습니다.");
    }
    List<EasyClassMember> active = memberRepository.findAllByEasyClass_ClassIdAndReleasedOnIsNull(classId);
    if (active.stream().anyMatch(m -> m.getStudent().getStudentId().equals(studentId))) {
      throw new IllegalArgumentException(s.getName() + " 학생은 이미 이 반에 있습니다.");
    }
    if (c.getCapacity() != null && active.size() >= c.getCapacity()) {
      throw new IllegalArgumentException("정원(" + c.getCapacity() + "명)이 찼습니다.");
    }
    memberRepository.save(new EasyClassMember(c, s, assignedOn));
    return memberView(s, assignedOn);
  }

  /** 반에서 빼기: 학생 등록과 출결 기록은 남는다. */
  public void release(Long businessId, Long classId, Long studentId) {
    requireClass(businessId, classId);
    activeMember(classId, studentId).release(LocalDate.now(clock), "REMOVED");
  }

  /** 다른 반으로 이동: 대상 반 정원을 먼저 확인하고, 지금 반은 MOVED로 해제한다. */
  public MemberView move(Long businessId, Long classId, Long studentId, Long targetClassId) {
    if (Objects.equals(classId, targetClassId)) {
      throw new IllegalArgumentException("같은 반으로는 옮길 수 없습니다.");
    }
    requireClass(businessId, classId);
    EasyClassMember current = activeMember(classId, studentId);
    MemberView moved = assign(businessId, targetClassId, studentId);
    current.release(LocalDate.now(clock), "MOVED");
    return moved;
  }

  // ------------------------------------------------------------------ 출결부

  /** 반 + 날짜 출결부: 그날 배정돼 있던 학생과 그날의 현재 기록(정정된 행 제외). */
  @Transactional(readOnly = true)
  public List<SheetRow> sheet(Long businessId, Long classId, LocalDate day) {
    requireClass(businessId, classId);
    List<EasyClassMember> members = memberRepository.findAllByEasyClass_ClassIdOrderByMemberIdAsc(classId).stream()
        .filter(m -> m.activeOn(day)).toList();
    Map<Long, List<EasyAttendanceEvent>> byStudent = currentEvents(classId,
        members.stream().map(m -> m.getStudent().getStudentId()).toList(), day, day);
    List<SheetRow> rows = new ArrayList<>();
    Set<Long> seen = new java.util.HashSet<>();
    for (EasyClassMember m : members) {
      EasyStudent s = m.getStudent();
      if (!seen.add(s.getStudentId())) continue;
      rows.add(new SheetRow(s.getStudentId(), s.getName(), s.getSchool(), gradeOf(s),
          byStudent.getOrDefault(s.getStudentId(), List.of()).stream().map(EasyAttendanceService::eventView).toList()));
    }
    rows.sort(java.util.Comparator.comparing(SheetRow::name));
    return rows;
  }

  /** 출결 기록. 반을 고르면 그날 그 반에 배정된 학생만 기록할 수 있다. 시각을 비우면 지금(한국 시각). */
  public EventView record(Long businessId, RecordForm form) {
    EasyBusiness business = requireBusiness(businessId);
    EasyAttendanceEvent.Type type = type(form.eventType());
    EasyStudent student = requireStudent(form.studentId());
    LocalDateTime at = form.occurredAt() == null || form.occurredAt().isBlank()
        ? LocalDateTime.now(clock).withSecond(0).withNano(0) : parseDateTime(form.occurredAt());
    EasyClass c = null;
    if (form.classId() != null) {
      c = requireClass(businessId, form.classId());
      LocalDate day = at.toLocalDate();
      boolean member = memberRepository.findAllByEasyClass_ClassIdOrderByMemberIdAsc(c.getClassId()).stream()
          .anyMatch(m -> m.getStudent().getStudentId().equals(student.getStudentId()) && m.activeOn(day));
      if (!member) {
        throw new IllegalArgumentException(student.getName() + " 학생은 " + day + "에 이 반에 배정돼 있지 않습니다.");
      }
    }
    EasyAttendanceEvent saved = eventRepository.save(EasyAttendanceEvent.builder().business(business)
        .easyClass(c).student(student).occurredAt(at).eventType(type).note(clip(form.note(), 200))
        .source(EasyAttendanceEvent.Source.MANUAL).build());
    return eventView(saved);
  }

  /** 정정: 사유가 없으면 거절. 이전 기록은 지우지 않고 superseded로 남기고, 같은 학생·반·시각으로 새 행을 만든다. */
  public EventView correct(Long businessId, Long eventId, CorrectForm form) {
    String reason = form == null || form.reason() == null ? "" : form.reason().trim();
    if (reason.isEmpty()) {
      throw new IllegalArgumentException("정정 사유를 적어 주세요.");
    }
    if (reason.length() > 200) {
      throw new IllegalArgumentException("정정 사유는 200자 이내로 적어 주세요.");
    }
    EasyAttendanceEvent prior = eventRepository.findById(eventId)
        .orElseThrow(() -> new IllegalArgumentException("출결 기록을 찾을 수 없습니다."));
    if (!prior.getBusiness().getBusinessId().equals(businessId)) {
      throw new IllegalArgumentException("다른 사업체의 출결 기록입니다.");
    }
    if (prior.isSuperseded()) {
      throw new IllegalStateException("이미 정정된 기록입니다. 화면을 새로 고친 뒤 최신 기록을 정정하세요.");
    }
    EasyAttendanceEvent.Type type = type(form.eventType());
    prior.supersede();
    EasyAttendanceEvent saved = eventRepository.save(EasyAttendanceEvent.builder()
        .business(prior.getBusiness()).easyClass(prior.getEasyClass()).student(prior.getStudent())
        .occurredAt(prior.getOccurredAt()).eventType(type)
        .note(form.note() == null ? prior.getNote() : clip(form.note(), 200))
        .source(EasyAttendanceEvent.Source.MANUAL).correctsEventId(prior.getEventId())
        .correctionReason(reason).build());
    return eventView(saved);
  }

  /** 학생 한 명의 모든 출결(정정 이력 포함, 최신순). */
  @Transactional(readOnly = true)
  public List<EventView> studentHistory(Long businessId, Long studentId) {
    requireBusiness(businessId);
    requireStudent(studentId);
    return eventRepository.findAllByStudent_StudentIdOrderByOccurredAtDesc(studentId).stream()
        .filter(e -> e.getBusiness().getBusinessId().equals(businessId))
        .map(EasyAttendanceService::eventView).toList();
  }

  /** 관리자 대시보드용 학생 한 줄: 반·수업 요일(최근 4주 등원, 없으면 반 요일)·이번 달 등원·마지막 등원. */
  public record StudentOverviewRow(Long studentId, String name, String school, Integer grade,
      List<String> classes, List<String> weekdays, String weekdaySource, int monthCheckIns,
      LocalDate lastCheckIn) {}

  /** 최근 등원 요일을 볼 기간(오늘 포함 28일 = 4주). */
  static final int RECENT_DAYS = 28;
  private static final List<String> WEEK_ORDER = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

  /**
   * 재원(ENROLLED) 학생 전체의 반·수업 요일·등원 요약(읽기 전용). 수업 요일은 최근 4주 실제 등원 요일을 먼저 쓰고,
   * 등원 기록이 없으면 배정된 반의 요일을 쓴다. 정정된(superseded) 기록과 다른 사업체 기록은 세지 않는다.
   */
  @Transactional(readOnly = true)
  public List<StudentOverviewRow> studentOverview(Long businessId) {
    requireBusiness(businessId);
    LocalDate today = LocalDate.now(clock);
    LocalDate recentFrom = today.minusDays(RECENT_DAYS - 1L);
    LocalDate monthFrom = today.withDayOfMonth(1);
    LocalDate from = recentFrom.isBefore(monthFrom) ? recentFrom : monthFrom;
    List<EasyStudent> students = studentRepository.findAllByOrderByNameAsc().stream()
        .filter(st -> st.getStatus() == EasyStudentStatus.ENROLLED).toList();
    if (students.isEmpty()) return List.of();

    Map<Long, List<String>> classNames = new LinkedHashMap<>();
    Map<Long, Set<String>> classDays = new LinkedHashMap<>();
    for (EasyClass c : classRepository.findAllByBusiness_BusinessIdOrderByStatusAscNameAsc(businessId)) {
      if (!c.isActive()) continue;
      for (EasyClassMember m : memberRepository.findAllByEasyClass_ClassIdAndReleasedOnIsNull(c.getClassId())) {
        Long id = m.getStudent().getStudentId();
        classNames.computeIfAbsent(id, k -> new ArrayList<>()).add(c.getName());
        if (c.getWeekdays() != null && !c.getWeekdays().isBlank()) {
          classDays.computeIfAbsent(id, k -> new java.util.HashSet<>()).addAll(List.of(c.getWeekdays().split(",")));
        }
      }
    }

    Map<Long, List<EasyAttendanceEvent>> checkIns = eventRepository
        .findAllByStudent_StudentIdInAndOccurredOnBetweenOrderByOccurredAtAsc(
            students.stream().map(EasyStudent::getStudentId).toList(), from, today).stream()
        .filter(e -> !e.isSuperseded())
        .filter(e -> e.getBusiness().getBusinessId().equals(businessId))
        .filter(e -> e.getEventType() == EasyAttendanceEvent.Type.CHECK_IN)
        .collect(Collectors.groupingBy(e -> e.getStudent().getStudentId()));

    List<StudentOverviewRow> rows = new ArrayList<>();
    for (EasyStudent st : students) {
      List<EasyAttendanceEvent> mine = checkIns.getOrDefault(st.getStudentId(), List.of());
      Set<String> recent = mine.stream().filter(e -> !e.getOccurredOn().isBefore(recentFrom))
          .map(e -> e.getOccurredOn().getDayOfWeek().name().substring(0, 3))
          .collect(Collectors.toSet());
      String source = null;
      Set<String> days = recent;
      if (!recent.isEmpty()) {
        source = "ATTENDANCE";
      } else if (classDays.containsKey(st.getStudentId())) {
        days = classDays.get(st.getStudentId());
        source = "CLASS";
      }
      final Set<String> chosen = days;
      List<String> ordered = WEEK_ORDER.stream().filter(chosen::contains).toList();
      int month = (int) mine.stream().filter(e -> !e.getOccurredOn().isBefore(monthFrom)).count();
      LocalDate last = mine.stream().map(EasyAttendanceEvent::getOccurredOn).max(LocalDate::compareTo).orElse(null);
      rows.add(new StudentOverviewRow(st.getStudentId(), st.getName(), st.getSchool(),
          st.getGrade() == null ? null : st.getGrade().intValue(),
          classNames.getOrDefault(st.getStudentId(), List.of()), ordered, source, month, last));
    }
    return rows;
  }

  /** 반 + 연월 월별 현황: 학생별 날짜별 현재 출결과 구분별 횟수. */
  @Transactional(readOnly = true)
  public Map<String, Object> monthly(Long businessId, Long classId, String month) {
    EasyClass c = requireClass(businessId, classId);
    YearMonth ym = parseMonth(month);
    LocalDate from = ym.atDay(1);
    LocalDate to = ym.atEndOfMonth();
    List<EasyClassMember> members = memberRepository.findAllByEasyClass_ClassIdOrderByMemberIdAsc(classId).stream()
        .filter(m -> !m.getAssignedOn().isAfter(to) && (m.getReleasedOn() == null || m.getReleasedOn().isAfter(from)))
        .toList();
    Map<Long, EasyStudent> students = new LinkedHashMap<>();
    members.forEach(m -> students.putIfAbsent(m.getStudent().getStudentId(), m.getStudent()));
    Map<Long, List<EasyAttendanceEvent>> events = currentEvents(classId, new ArrayList<>(students.keySet()), from, to);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (EasyStudent s : students.values()) {
      Map<Integer, List<String>> days = new TreeMap<>();
      Map<EasyAttendanceEvent.Type, Integer> counts = new EnumMap<>(EasyAttendanceEvent.Type.class);
      for (EasyAttendanceEvent e : events.getOrDefault(s.getStudentId(), List.of())) {
        days.computeIfAbsent(e.getOccurredOn().getDayOfMonth(), d -> new ArrayList<>()).add(e.getEventType().name());
        counts.merge(e.getEventType(), 1, Integer::sum);
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("studentId", s.getStudentId());
      row.put("name", s.getName());
      row.put("days", days);
      row.put("counts", counts.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
      rows.add(row);
    }
    rows.sort(java.util.Comparator.comparing(r -> String.valueOf(r.get("name"))));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("classId", c.getClassId());
    out.put("className", c.getName());
    out.put("month", ym.toString());
    out.put("daysInMonth", ym.lengthOfMonth());
    out.put("students", rows);
    return out;
  }

  // ------------------------------------------------------------------ 내부

  /** 학생별 기간 내 현재 기록(정정된 행 제외). 반이 정해진 기록은 그 반 것만, 반 없는 기록(진행표 등)은 함께 보여 준다. */
  private Map<Long, List<EasyAttendanceEvent>> currentEvents(Long classId, List<Long> studentIds,
      LocalDate from, LocalDate to) {
    if (studentIds.isEmpty()) return Map.of();
    return eventRepository.findAllByStudent_StudentIdInAndOccurredOnBetweenOrderByOccurredAtAsc(studentIds, from, to)
        .stream()
        .filter(e -> !e.isSuperseded())
        .filter(e -> e.getEasyClass() == null || e.getEasyClass().getClassId().equals(classId))
        .collect(Collectors.groupingBy(e -> e.getStudent().getStudentId(), LinkedHashMap::new, Collectors.toList()));
  }

  private record Normalized(String name, String teacher, String description, String weekdays,
      LocalTime start, LocalTime end, String room, Integer capacity) {}

  static Normalized normalize(ClassForm form) {
    if (form == null) throw new IllegalArgumentException("반 정보를 보내 주세요.");
    String name = form.name() == null ? "" : form.name().trim();
    if (name.isEmpty() || name.length() > 60) {
      throw new IllegalArgumentException("반 이름을 60자 이내로 적어 주세요.");
    }
    List<String> days = form.weekdays() == null ? List.of() : form.weekdays().stream()
        .map(d -> d == null ? "" : d.trim().toUpperCase()).filter(d -> !d.isEmpty()).distinct().toList();
    for (String d : days) {
      if (!WEEKDAYS.contains(d)) throw new IllegalArgumentException("요일은 MON~SUN으로 적어 주세요: " + d);
    }
    LocalTime start = time(form.startTime(), "시작 시각");
    LocalTime end = time(form.endTime(), "끝나는 시각");
    if (start != null && end != null && !end.isAfter(start)) {
      throw new IllegalArgumentException("끝나는 시각이 시작 시각보다 늦어야 합니다.");
    }
    if (form.capacity() != null && (form.capacity() < 1 || form.capacity() > 500)) {
      throw new IllegalArgumentException("정원은 1~500명으로 적어 주세요.");
    }
    return new Normalized(name, clip(form.teacherName(), 30), clip(form.description(), 300),
        days.isEmpty() ? null : String.join(",", days), start, end, clip(form.room(), 30), form.capacity());
  }

  static EasyAttendanceEvent.Type type(String value) {
    try {
      return EasyAttendanceEvent.Type.valueOf(value == null ? "" : value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("출결 구분은 CHECK_IN·CHECK_OUT·ABSENT·LATE·EARLY_LEAVE 중 하나입니다.");
    }
  }

  private static LocalTime time(String value, String label) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalTime.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(label + "은 15:30 형식으로 적어 주세요.");
    }
  }

  private static LocalDateTime parseDateTime(String value) {
    try {
      return LocalDateTime.parse(value.trim()).withSecond(0).withNano(0);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("시각은 2026-11-10T15:30 형식으로 적어 주세요.");
    }
  }

  static YearMonth parseMonth(String value) {
    try {
      return YearMonth.parse(value == null ? "" : value.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("연월은 2026-10 형식으로 적어 주세요.");
    }
  }

  private EasyBusiness requireBusiness(Long businessId) {
    if (businessId == null) throw new IllegalArgumentException("사업체를 선택해 주세요.");
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다."));
    if (!business.isActive()) throw new IllegalArgumentException("비활성 사업체입니다.");
    return business;
  }

  private EasyClass requireClass(Long businessId, Long classId) {
    requireBusiness(businessId);
    EasyClass c = classRepository.findById(classId == null ? -1L : classId)
        .orElseThrow(() -> new IllegalArgumentException("반을 찾을 수 없습니다."));
    if (!c.getBusiness().getBusinessId().equals(businessId)) {
      throw new IllegalArgumentException("다른 사업체의 반입니다.");
    }
    return c;
  }

  private EasyClass requireActiveClass(Long businessId, Long classId) {
    EasyClass c = requireClass(businessId, classId);
    if (!c.isActive()) throw new IllegalStateException("종료된 반입니다.");
    return c;
  }

  EasyStudent requireStudent(Long studentId) {
    return studentRepository.findById(studentId == null ? -1L : studentId)
        .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
  }

  private EasyClassMember activeMember(Long classId, Long studentId) {
    return memberRepository.findAllByEasyClass_ClassIdAndReleasedOnIsNull(classId).stream()
        .filter(m -> m.getStudent().getStudentId().equals(studentId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("이 반에 배정된 학생이 아닙니다."));
  }

  private static ClassView view(EasyClass c, int memberCount) {
    return new ClassView(c.getClassId(), c.getName(), c.getTeacherName(), c.getDescription(),
        c.getWeekdays() == null ? List.of() : List.of(c.getWeekdays().split(",")),
        c.getStartTime() == null ? null : c.getStartTime().toString(),
        c.getEndTime() == null ? null : c.getEndTime().toString(), c.getRoom(), c.getCapacity(),
        c.getStatus().name(), memberCount);
  }

  private static MemberView memberView(EasyStudent s, LocalDate assignedOn) {
    return new MemberView(s.getStudentId(), s.getName(), s.getSchool(), gradeOf(s),
        s.getStatus().name(), assignedOn);
  }

  static EventView eventView(EasyAttendanceEvent e) {
    return new EventView(e.getEventId(), e.getEventType().name(), e.getEventType().label(),
        e.getOccurredAt(), e.getNote(), e.getSource().name(), e.getCorrectsEventId(),
        e.getCorrectionReason());
  }

  private static Integer gradeOf(EasyStudent s) {
    return s.getGrade() == null ? null : s.getGrade().intValue();
  }

  static String clip(String value, int max) {
    if (value == null) return null;
    String t = value.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }
}
