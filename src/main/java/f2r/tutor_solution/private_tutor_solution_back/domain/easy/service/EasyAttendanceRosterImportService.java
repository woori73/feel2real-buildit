package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudentRosterController.RosterLesson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학생명단(시간표) 진행표 → F2R 출결부 가져오기. 진행표에 이름이 있는 날 = 등원(CHECK_IN), 그날 수업 수는 메모로 남긴다.
 *
 * <p>순서: 미리보기(이름별 수업 수·후보 학생) → 관리자가 이름마다 기존 학생 연결/새로 등록/건너뛰기를 고름 → 적용. 미리보기 이후
 * 명단이 바뀌면(해시 불일치) 거절한다. 같은 학생·날짜는 source_key로 한 번만 들어간다. 실명은 서버 안에서만 쓰고 로그·AI로 보내지 않는다.
 */
@Service
public class EasyAttendanceRosterImportService {

  /** 진행표 읽기: (관리자, 연도) → 수업 기록. 기본은 학생명단 자료실 컨트롤러. */
  private final BiFunction<AuthenticatedPrincipal, Integer, List<RosterLesson>> roster;
  private final EasyStudentRepository studentRepository;
  private final EasyAttendanceEventRepository eventRepository;
  private final EasyClassRepository classRepository;
  private final EasyClassMemberRepository memberRepository;
  private final EasyBusinessRepository businessRepository;
  private final EasyAttendanceService attendanceService;
  /** 오늘(한국 날짜). 오늘보다 뒤인 진행표 날짜는 아직 하지 않은 수업이라 넣지 않는다. */
  private final java.util.function.Supplier<LocalDate> today;

  @org.springframework.beans.factory.annotation.Autowired
  public EasyAttendanceRosterImportService(
      ObjectProvider<f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudentRosterController> rosterController,
      EasyStudentRepository studentRepository, EasyAttendanceEventRepository eventRepository,
      EasyClassRepository classRepository, EasyClassMemberRepository memberRepository,
      EasyBusinessRepository businessRepository, EasyAttendanceService attendanceService) {
    this((p, year) -> {
      var controller = rosterController.getIfAvailable();
      if (controller == null) throw new IllegalStateException("학생명단 자료실을 사용할 수 없습니다.");
      try {
        return controller.lessonsOf(p, year);
      } catch (IOException e) {
        throw new IllegalStateException(year + "년 학생명단 파일을 읽지 못했습니다.", e);
      }
    }, studentRepository, eventRepository, classRepository, memberRepository, businessRepository, attendanceService,
        () -> LocalDate.now(EasyAttendanceService.KST));
  }

  EasyAttendanceRosterImportService(BiFunction<AuthenticatedPrincipal, Integer, List<RosterLesson>> roster,
      EasyStudentRepository studentRepository, EasyAttendanceEventRepository eventRepository,
      EasyClassRepository classRepository, EasyClassMemberRepository memberRepository,
      EasyBusinessRepository businessRepository, EasyAttendanceService attendanceService,
      java.util.function.Supplier<LocalDate> today) {
    this.roster = roster;
    this.today = today;
    this.studentRepository = studentRepository;
    this.eventRepository = eventRepository;
    this.classRepository = classRepository;
    this.memberRepository = memberRepository;
    this.businessRepository = businessRepository;
    this.attendanceService = attendanceService;
  }

  public record Candidate(Long studentId, String name, String school, Integer grade, String status) {}

  public record NameRow(String name, int lessonDays, int sessions, LocalDate firstDate,
      LocalDate lastDate, List<Candidate> candidates, String suggestedAction, Long suggestedStudentId) {}

  public record Mapping(String name, String action, Long studentId) {}

  public record ApplyRequest(Long businessId, Integer year, String previewHash, Long classId,
      List<Mapping> mappings) {}

  /** 미리보기: 저장하지 않는다. */
  @Transactional(readOnly = true)
  public Map<String, Object> preview(AuthenticatedPrincipal p, Long businessId, int year) {
    requireBusiness(businessId);
    List<RosterLesson> all = roster.apply(p, year);
    List<RosterLesson> lessons = past(all);
    Map<String, List<EasyStudent>> byName = studentsByName();
    List<NameRow> rows = new ArrayList<>();
    lessons.stream().collect(Collectors.groupingBy(RosterLesson::name, LinkedHashMap::new, Collectors.toList()))
        .forEach((name, list) -> {
          List<Candidate> candidates = byName.getOrDefault(name, List.of()).stream()
              .map(s -> new Candidate(s.getStudentId(), s.getName(), s.getSchool(),
                  s.getGrade() == null ? null : s.getGrade().intValue(), s.getStatus().name()))
              .toList();
          String action = candidates.isEmpty() ? "CREATE" : candidates.size() == 1 ? "MATCH" : "CHOOSE";
          rows.add(new NameRow(name, list.size(), list.stream().mapToInt(RosterLesson::sessions).sum(),
              list.get(0).date(), list.get(list.size() - 1).date(), candidates, action,
              candidates.size() == 1 ? candidates.get(0).studentId() : null));
        });
    rows.sort(java.util.Comparator.comparing(NameRow::lessonDays).reversed().thenComparing(NameRow::name));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("year", year);
    out.put("previewHash", hash(lessons));
    out.put("lessonDays", lessons.size());
    out.put("futureLessonDays", all.size() - lessons.size());
    out.put("names", rows);
    out.put("rule", "진행표에 이름이 있는 날 = 등원(CHECK_IN). 그날 수업 수는 메모로 남깁니다. 같은 학생·날짜는 한 번만 들어갑니다."
        + " 오늘 이후 날짜(예정 수업)는 넣지 않습니다.");
    return out;
  }

  /** 적용: 관리자가 고른 연결대로 학생을 등록·연결하고 등원 기록을 만든다. */
  @Transactional
  public Map<String, Object> apply(AuthenticatedPrincipal p, ApplyRequest request) {
    EasyBusiness business = requireBusiness(request.businessId());
    if (request.year() == null) throw new IllegalArgumentException("연도를 보내 주세요.");
    int year = request.year();
    List<RosterLesson> lessons = past(roster.apply(p, year));
    if (request.previewHash() == null || !request.previewHash().equals(hash(lessons))) {
      throw new IllegalStateException("미리보기 뒤에 학생명단이 바뀌었습니다. 미리보기를 다시 해 주세요.");
    }
    EasyClass easyClass = null;
    if (request.classId() != null) {
      easyClass = classRepository.findById(request.classId())
          .filter(c -> c.getBusiness().getBusinessId().equals(business.getBusinessId()))
          .orElseThrow(() -> new IllegalArgumentException("반을 찾을 수 없습니다."));
      if (!easyClass.isActive()) throw new IllegalStateException("종료된 반에는 넣을 수 없습니다.");
    }
    Map<String, Mapping> mappings = new HashMap<>();
    for (Mapping m : request.mappings() == null ? List.<Mapping>of() : request.mappings()) {
      mappings.put(m.name(), m);
    }
    Map<String, List<RosterLesson>> byName = lessons.stream()
        .collect(Collectors.groupingBy(RosterLesson::name, LinkedHashMap::new, Collectors.toList()));
    List<String> missing = byName.keySet().stream().filter(n -> !mappings.containsKey(n)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("모든 이름의 처리 방법(연결·새로 등록·건너뛰기)을 골라 주세요. 남은 이름 " + missing.size() + "개");
    }

    int created = 0;
    int matched = 0;
    int skippedNames = 0;
    Map<String, EasyStudent> resolved = new LinkedHashMap<>();
    for (var entry : byName.entrySet()) {
      Mapping m = mappings.get(entry.getKey());
      String action = m.action() == null ? "" : m.action().trim().toUpperCase();
      switch (action) {
        case "SKIP" -> skippedNames++;
        case "MATCH" -> {
          resolved.put(entry.getKey(), attendanceService.requireStudent(m.studentId()));
          matched++;
        }
        case "CREATE" -> {
          // 다시 적용해도 같은 학생을 또 만들지 않도록, 이 연도 진행표로 이미 등록한 같은 이름 학생이 있으면 그 학생을 쓴다.
          String memo = year + " 진행표에서 등록";
          var already = studentRepository.findAllByOrderByNameAsc().stream()
              .filter(s -> s.getName().trim().equals(entry.getKey()) && memo.equals(s.getMemo())).findFirst();
          if (already.isPresent()) {
            resolved.put(entry.getKey(), already.get());
            matched++;
          } else {
            resolved.put(entry.getKey(), studentRepository.save(EasyStudent.builder().name(entry.getKey())
                .grade((byte) 0).status(EasyStudentStatus.ENROLLED).enrolledOn(entry.getValue().get(0).date())
                .memo(memo).build()));
            created++;
          }
        }
        default -> throw new IllegalArgumentException("처리 방법은 MATCH·CREATE·SKIP 중 하나입니다.");
      }
    }

    // 반을 골랐으면 아직 배정되지 않은 학생을 첫 수업일 기준으로 배정한다(정원 초과면 거절).
    if (easyClass != null) {
      Set<Long> active = memberRepository.findAllByEasyClass_ClassIdAndReleasedOnIsNull(easyClass.getClassId())
          .stream().map(EasyClassMember::getStudent).map(EasyStudent::getStudentId).collect(Collectors.toSet());
      for (var entry : resolved.entrySet()) {
        if (!active.contains(entry.getValue().getStudentId())) {
          attendanceService.assignOn(business.getBusinessId(), easyClass.getClassId(),
              entry.getValue().getStudentId(), byName.get(entry.getKey()).get(0).date());
          active.add(entry.getValue().getStudentId());
        }
      }
    }

    LocalTime at = easyClass != null && easyClass.getStartTime() != null ? easyClass.getStartTime() : LocalTime.MIDNIGHT;
    Map<String, RosterLesson> byKey = new LinkedHashMap<>();
    Map<String, EasyStudent> studentByKey = new HashMap<>();
    for (var entry : resolved.entrySet()) {
      for (RosterLesson lesson : byName.get(entry.getKey())) {
        String key = sourceKey(business.getBusinessId(), year, entry.getValue().getStudentId(), lesson.date());
        byKey.putIfAbsent(key, lesson);
        studentByKey.putIfAbsent(key, entry.getValue());
      }
    }
    Set<String> existing = byKey.isEmpty() ? Set.of() : eventRepository.findAllBySourceKeyIn(byKey.keySet()).stream()
        .map(EasyAttendanceEvent::getSourceKey).collect(Collectors.toSet());
    List<EasyAttendanceEvent> toSave = new ArrayList<>();
    for (var entry : byKey.entrySet()) {
      if (existing.contains(entry.getKey())) continue;
      RosterLesson lesson = entry.getValue();
      toSave.add(EasyAttendanceEvent.builder().business(business).easyClass(easyClass)
          .student(studentByKey.get(entry.getKey())).occurredAt(lesson.date().atTime(at))
          .eventType(EasyAttendanceEvent.Type.CHECK_IN)
          .note("진행표 기록 · 수업 " + lesson.sessions() + "건" + (easyClass == null || easyClass.getStartTime() == null ? " · 시각 미상" : ""))
          .source(EasyAttendanceEvent.Source.ROSTER_IMPORT).sourceKey(entry.getKey()).build());
    }
    eventRepository.saveAll(toSave);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("year", year);
    out.put("createdStudents", created);
    out.put("matchedStudents", matched);
    out.put("skippedNames", skippedNames);
    out.put("eventsCreated", toSave.size());
    out.put("eventsAlreadyThere", existing.size());
    return out;
  }

  /** 오늘까지의 수업만(미래 날짜 = 예정 수업은 출결이 아니다). */
  private List<RosterLesson> past(List<RosterLesson> lessons) {
    LocalDate limit = today.get();
    return lessons.stream().filter(l -> !l.date().isAfter(limit)).toList();
  }

  static String sourceKey(Long businessId, int year, Long studentId, LocalDate date) {
    return "ROSTER:" + businessId + ":" + year + ":" + studentId + ":" + date;
  }

  /** 명단 내용 해시(미리보기 이후 바뀌었는지 확인). */
  static String hash(List<RosterLesson> lessons) {
    StringBuilder sb = new StringBuilder();
    for (RosterLesson l : lessons) sb.append(l.date()).append('|').append(l.name()).append('|').append(l.sessions()).append('\n');
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(sb.toString().getBytes(StandardCharsets.UTF_8))).substring(0, 32);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, List<EasyStudent>> studentsByName() {
    return studentRepository.findAllByOrderByNameAsc().stream()
        .collect(Collectors.groupingBy(s -> s.getName().trim()));
  }

  private EasyBusiness requireBusiness(Long businessId) {
    if (businessId == null) throw new IllegalArgumentException("사업체를 선택해 주세요.");
    return businessRepository.findById(businessId)
        .filter(EasyBusiness::isActive)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다."));
  }
}
