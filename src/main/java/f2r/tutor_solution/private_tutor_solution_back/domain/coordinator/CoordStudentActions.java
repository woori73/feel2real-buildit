package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.ClassView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.WeekdayForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyStudentService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * AI 코디네이터 — 학생·반 관리 작업. 초안(prepare) → 사용자 확인 후 실행(execute) → 10분 안 되돌리기(undo).
 *
 * <p>기존 {@link EasyStudentService}·{@link EasyAttendanceService} 기능만 부른다(새 저장 경로 없음). 학생·학부모 연락처는
 * 다루지 않는다 — 수정할 때도 기존 값을 그대로 둔다. 되돌리기는 실행 뒤 상태가 그대로일 때만 한다(그사이 바뀌었으면 거절).
 */
@Component
public class CoordStudentActions implements CoordActions {

  public static final String STUDENT_CREATE = "STUDENT_CREATE";
  public static final String STUDENT_UPDATE = "STUDENT_UPDATE";
  public static final String CLASS_CREATE = "CLASS_CREATE";
  public static final String CLASS_ASSIGN = "CLASS_ASSIGN";
  public static final String CLASS_MOVE = "CLASS_MOVE";
  public static final String CLASS_RELEASE = "CLASS_RELEASE";
  public static final String WEEKDAYS = "WEEKDAYS";
  static final Set<String> KINDS = Set.of(STUDENT_CREATE, STUDENT_UPDATE, CLASS_CREATE, CLASS_ASSIGN, CLASS_MOVE,
      CLASS_RELEASE, WEEKDAYS);
  static final Set<String> STATUSES = Set.of("ENROLLED", "WITHDRAWN", "GRADUATED");
  static final Map<String, String> STATUS_LABEL = Map.of("ENROLLED", "재원", "WITHDRAWN", "퇴원", "GRADUATED", "졸업");
  static final List<String> WEEK = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
  static final Map<String, String> WEEK_LABEL = Map.of("MON", "월", "TUE", "화", "WED", "수", "THU", "목", "FRI", "금",
      "SAT", "토", "SUN", "일");

  private final EasyStudentRepository students;
  private final EasyStudentService studentService;
  private final EasyAttendanceService attendance;

  public CoordStudentActions(EasyStudentRepository students, EasyStudentService studentService,
      EasyAttendanceService attendance) {
    this.students = students;
    this.studentService = studentService;
    this.attendance = attendance;
  }

  @Override
  public Set<String> areas() {
    return Set.of("STUDENT", "CLASS");
  }

  @Override
  public Set<String> kinds() {
    return KINDS;
  }

  @Override
  public Draft prepare(String area, Long businessId, String action, JsonNode args) {
    return "STUDENT".equals(area) ? prepareStudent(action, args) : prepareClass(businessId, action, args);
  }

  /* ---------------- 초안 ---------------- */

  public Draft prepareStudent(String action, JsonNode a) {
    String act = upper(action);
    Map<String, Object> plan = new LinkedHashMap<>();
    if ("CREATE".equals(act)) {
      String name = requireText(a, "name", "학생 이름");
      plan.put("name", name);
      plan.put("school", text(a, "school"));
      plan.put("grade", grade(a));
      plan.put("enrolledOn", date(a, "enrolledOn"));
      plan.put("memo", clip(text(a, "memo"), 500));
      boolean dup = students.findAllByOrderByNameAsc().stream()
          .anyMatch(s -> name.equals(s.getName()) && s.getStatus().name().equals("ENROLLED"));
      String tail = describe(plan.get("school"), plan.get("grade")) + (dup ? " (같은 이름의 재원생이 이미 있음)" : "");
      return new Draft(STUDENT_CREATE, plan, "학생 등록 · " + name + tail,
          "학생 등록 · " + CoordinatorService.maskName(name) + tail);
    }
    if ("UPDATE".equals(act)) {
      EasyStudent s = requireStudent(a);
      plan.put("studentId", s.getStudentId());
      List<String> changes = new ArrayList<>();
      List<String> aiChanges = new ArrayList<>();
      if (has(a, "name")) {
        String n = requireText(a, "name", "학생 이름");
        plan.put("name", n);
        changes.add("이름 → " + n);
        aiChanges.add("이름 → " + CoordinatorService.maskName(n));
      }
      if (has(a, "school")) { plan.put("school", text(a, "school")); changes.add("학교 → " + text(a, "school")); aiChanges.add("학교 변경"); }
      if (has(a, "grade")) { plan.put("grade", grade(a)); changes.add("학년 → " + grade(a)); aiChanges.add("학년 → " + grade(a)); }
      if (has(a, "status")) {
        String st = upper(text(a, "status"));
        if (!STATUSES.contains(st)) throw new IllegalArgumentException("재원 상태는 ENROLLED(재원)·WITHDRAWN(퇴원)·GRADUATED(졸업) 중 하나입니다.");
        plan.put("status", st);
        changes.add("상태 → " + STATUS_LABEL.get(st));
        aiChanges.add("상태 → " + STATUS_LABEL.get(st));
      }
      if (has(a, "memo")) { plan.put("memo", clip(text(a, "memo"), 500)); changes.add("메모 변경"); aiChanges.add("메모 변경"); }
      if (changes.isEmpty()) throw new IllegalArgumentException("바꿀 내용(name·school·grade·status·memo)을 하나 이상 주세요.");
      return new Draft(STUDENT_UPDATE, plan, "학생 정보 수정 · " + s.getName() + " · " + String.join(", ", changes),
          "학생 정보 수정 · " + CoordinatorService.maskName(s.getName()) + " · " + String.join(", ", aiChanges));
    }
    throw new IllegalArgumentException("action은 CREATE(등록)·UPDATE(수정·퇴원) 중 하나입니다.");
  }

  public Draft prepareClass(Long bid, String action, JsonNode a) {
    String act = upper(action);
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("businessId", bid);
    switch (act) {
      case "CREATE" -> {
        String name = requireText(a, "name", "반 이름");
        plan.put("name", name);
        plan.put("teacherName", text(a, "teacherName"));
        plan.put("weekdays", weekdays(a));
        plan.put("startTime", text(a, "startTime"));
        plan.put("endTime", text(a, "endTime"));
        plan.put("room", text(a, "room"));
        plan.put("capacity", has(a, "capacity") ? a.get("capacity").asInt() : null);
        @SuppressWarnings("unchecked") String days = dayText((List<String>) plan.get("weekdays"));
        String t = "반 만들기 · " + name + (days.isEmpty() ? "" : " · " + days);
        return new Draft(CLASS_CREATE, plan, t, t);
      }
      case "ASSIGN", "RELEASE" -> {
        EasyStudent s = requireStudent(a);
        ClassView c = requireClass(bid, a, "classId");
        plan.put("studentId", s.getStudentId());
        plan.put("classId", c.classId());
        String verb = "ASSIGN".equals(act) ? " 반 배정" : " 반에서 빼기";
        return new Draft("ASSIGN".equals(act) ? CLASS_ASSIGN : CLASS_RELEASE, plan,
            s.getName() + " → " + c.name() + verb, CoordinatorService.maskName(s.getName()) + " → " + c.name() + verb);
      }
      case "MOVE" -> {
        EasyStudent s = requireStudent(a);
        ClassView from = requireClass(bid, a, "classId");
        ClassView to = requireClass(bid, a, "targetClassId");
        plan.put("studentId", s.getStudentId());
        plan.put("classId", from.classId());
        plan.put("targetClassId", to.classId());
        String tail = " · " + from.name() + " → " + to.name() + " 반 이동";
        return new Draft(CLASS_MOVE, plan, s.getName() + tail, CoordinatorService.maskName(s.getName()) + tail);
      }
      case "WEEKDAYS" -> {
        EasyStudent s = requireStudent(a);
        List<String> days = weekdays(a);
        if (days == null) throw new IllegalArgumentException("weekdays(MON~SUN 목록)가 필요합니다. 요일 미정은 빈 목록입니다.");
        plan.put("studentId", s.getStudentId());
        plan.put("weekdays", days);
        String tail = " · 수업 요일 → " + (days.isEmpty() ? "미정" : dayText(days));
        return new Draft(WEEKDAYS, plan, s.getName() + tail, CoordinatorService.maskName(s.getName()) + tail);
      }
      default -> throw new IllegalArgumentException("action은 CREATE·ASSIGN·MOVE·RELEASE·WEEKDAYS 중 하나입니다.");
    }
  }

  /* ---------------- 실행 ---------------- */

  /** 실행하고 되돌리기에 필요한 값(이전 상태)을 돌려준다. 결과에는 실명을 넣지 않는다. */
  @Override
  public Map<String, Object> execute(String kind, JsonNode plan) {
    Map<String, Object> r = new LinkedHashMap<>();
    switch (kind) {
      case STUDENT_CREATE -> {
        var created = studentService.create(new EasyStudentRequest(plan.get("name").asText(), str(plan, "school"),
            plan.hasNonNull("grade") ? (byte) plan.get("grade").asInt() : null, null, null, "ENROLLED",
            plan.hasNonNull("enrolledOn") ? LocalDate.parse(plan.get("enrolledOn").asText()) : null, str(plan, "memo")));
        r.put("studentId", created.studentId());
        r.put("label", "학생 등록");
      }
      case STUDENT_UPDATE -> {
        EasyStudent s = students.findById(plan.get("studentId").asLong())
            .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        r.put("before", snapshot(s));
        studentService.update(s.getStudentId(), new EasyStudentRequest(
            plan.has("name") ? plan.get("name").asText() : s.getName(),
            plan.has("school") ? str(plan, "school") : s.getSchool(),
            plan.has("grade") ? (plan.hasNonNull("grade") ? (byte) plan.get("grade").asInt() : null) : s.getGrade(),
            s.getPhone(), s.getParentPhone(),
            plan.has("status") ? plan.get("status").asText() : s.getStatus().name(),
            s.getEnrolledOn(),
            plan.has("memo") ? str(plan, "memo") : s.getMemo()));
        r.put("after", snapshot(students.findById(s.getStudentId()).orElseThrow()));
        r.put("label", "학생 정보 수정");
      }
      case CLASS_CREATE -> {
        var c = attendance.createClass(plan.get("businessId").asLong(), classForm(plan));
        r.put("classId", c.classId());
        r.put("label", "반 만들기");
      }
      case CLASS_ASSIGN -> {
        attendance.assign(bid(plan), plan.get("classId").asLong(), plan.get("studentId").asLong());
        r.put("label", "반 배정");
      }
      case CLASS_RELEASE -> {
        attendance.release(bid(plan), plan.get("classId").asLong(), plan.get("studentId").asLong());
        r.put("label", "반에서 빼기");
      }
      case CLASS_MOVE -> {
        attendance.move(bid(plan), plan.get("classId").asLong(), plan.get("studentId").asLong(),
            plan.get("targetClassId").asLong());
        r.put("label", "반 이동");
      }
      case WEEKDAYS -> {
        Long sid = plan.get("studentId").asLong();
        var before = attendance.studentWeekdays(bid(plan), sid);
        r.put("before", before.weekdays());
        var after = attendance.updateStudentWeekdays(bid(plan), sid, new WeekdayForm(list(plan.get("weekdays")), before.revision()));
        r.put("after", after.weekdays());
        r.put("label", "수업 요일 변경");
      }
      default -> throw new IllegalArgumentException("알 수 없는 작업입니다: " + kind);
    }
    return r;
  }

  /* ---------------- 되돌리기 ---------------- */

  @Override
  public void undo(String kind, JsonNode plan, JsonNode result) {
    switch (kind) {
      case STUDENT_CREATE -> {
        EasyStudent s = students.findById(result.get("studentId").asLong())
            .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        studentService.update(s.getStudentId(), new EasyStudentRequest(s.getName(), s.getSchool(), s.getGrade(),
            s.getPhone(), s.getParentPhone(), "WITHDRAWN", s.getEnrolledOn(),
            ((s.getMemo() == null || s.getMemo().isBlank()) ? "" : s.getMemo() + " ") + "(AI 등록 취소)"));
      }
      case STUDENT_UPDATE -> {
        EasyStudent s = students.findById(plan.get("studentId").asLong())
            .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        if (!snapshot(s).equals(toMap(result.get("after")))) {
          throw new IllegalStateException("그사이 학생 정보가 다시 바뀌어 되돌릴 수 없습니다. 학생 원장에서 직접 고쳐 주세요.");
        }
        JsonNode b = result.get("before");
        studentService.update(s.getStudentId(), new EasyStudentRequest(b.get("name").asText(), str(b, "school"),
            b.hasNonNull("grade") ? (byte) b.get("grade").asInt() : null, s.getPhone(), s.getParentPhone(),
            b.get("status").asText(), s.getEnrolledOn(), str(b, "memo")));
      }
      case CLASS_CREATE -> {
        long classId = result.get("classId").asLong();
        if (!attendance.members(bid(plan), classId).isEmpty()) {
          throw new IllegalStateException("그사이 학생이 배정되어 반을 되돌릴 수 없습니다. 출결부에서 반을 종료해 주세요.");
        }
        attendance.endClass(bid(plan), classId);
      }
      case CLASS_ASSIGN -> attendance.release(bid(plan), plan.get("classId").asLong(), plan.get("studentId").asLong());
      case CLASS_RELEASE -> attendance.assign(bid(plan), plan.get("classId").asLong(), plan.get("studentId").asLong());
      case CLASS_MOVE -> attendance.move(bid(plan), plan.get("targetClassId").asLong(), plan.get("studentId").asLong(),
          plan.get("classId").asLong());
      case WEEKDAYS -> {
        Long sid = plan.get("studentId").asLong();
        var now = attendance.studentWeekdays(bid(plan), sid);
        if (!Objects.equals(now.weekdays(), list(result.get("after")))) {
          throw new IllegalStateException("그사이 수업 요일이 다시 바뀌어 되돌릴 수 없습니다.");
        }
        attendance.updateStudentWeekdays(bid(plan), sid, new WeekdayForm(list(result.get("before")), now.revision()));
      }
      default -> throw new IllegalArgumentException("알 수 없는 작업입니다: " + kind);
    }
  }

  /* ---------------- 조회(가린 이름) ---------------- */

  /** 반 목록. classId를 주면 그 반 학생(가린 이름)도 준다. */
  public Map<String, Object> classes(Long bid, Long classId, boolean includeEnded) {
    List<Map<String, Object>> list = new ArrayList<>();
    for (ClassView c : attendance.listClasses(bid, includeEnded)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("classId", c.classId());
      m.put("name", c.name());
      m.put("teacherName", c.teacherName());
      m.put("weekdays", c.weekdays());
      m.put("time", c.startTime() == null ? null : c.startTime() + "~" + (c.endTime() == null ? "" : c.endTime()));
      m.put("capacity", c.capacity());
      m.put("memberCount", c.memberCount());
      m.put("status", c.status());
      list.add(m);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("businessId", bid);
    out.put("classes", list);
    if (classId != null) {
      out.put("members", attendance.members(bid, classId).stream().map(mv -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", mv.studentId());
        m.put("displayName", CoordinatorService.maskName(mv.name()));
        m.put("grade", mv.grade());
        m.put("assignedOn", mv.assignedOn() == null ? null : mv.assignedOn().toString());
        return m;
      }).toList());
    }
    return out;
  }

  /* ---------------- 내부 ---------------- */

  static Map<String, Object> snapshot(EasyStudent s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", s.getName());
    m.put("school", s.getSchool());
    m.put("grade", s.getGrade() == null ? null : s.getGrade().intValue());
    m.put("status", s.getStatus().name());
    m.put("memo", s.getMemo());
    return m;
  }

  private static Map<String, Object> toMap(JsonNode n) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", str(n, "name"));
    m.put("school", str(n, "school"));
    m.put("grade", n.hasNonNull("grade") ? n.get("grade").asInt() : null);
    m.put("status", str(n, "status"));
    m.put("memo", str(n, "memo"));
    return m;
  }

  private EasyStudent requireStudent(JsonNode a) {
    if (!has(a, "studentId")) throw new IllegalArgumentException("studentId가 필요합니다. 먼저 f2r_student_search로 찾으세요.");
    return students.findById(a.get("studentId").asLong()).orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
  }

  private ClassView requireClass(Long bid, JsonNode a, String field) {
    if (!has(a, field)) throw new IllegalArgumentException(field + "가 필요합니다. 먼저 f2r_class_list로 반을 찾으세요.");
    long id = a.get(field).asLong();
    return attendance.listClasses(bid, false).stream().filter(c -> c.classId() == id).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("운영 중인 반을 찾을 수 없습니다: " + id));
  }

  private static EasyAttendanceService.ClassForm classForm(JsonNode p) {
    return new EasyAttendanceService.ClassForm(p.get("name").asText(), str(p, "teacherName"), null,
        list(p.get("weekdays")), str(p, "startTime"), str(p, "endTime"), str(p, "room"),
        p.hasNonNull("capacity") ? p.get("capacity").asInt() : null);
  }

  private static Long bid(JsonNode plan) {
    return plan.get("businessId").asLong();
  }

  static List<String> weekdays(JsonNode a) {
    if (a == null || !a.has("weekdays") || a.get("weekdays").isNull()) return null;
    List<String> out = new ArrayList<>();
    for (JsonNode d : a.get("weekdays")) {
      String v = upper(d.asText());
      if (!WEEK.contains(v)) throw new IllegalArgumentException("요일은 MON·TUE·WED·THU·FRI·SAT·SUN으로 주세요: " + d.asText());
      if (!out.contains(v)) out.add(v);
    }
    out.sort(java.util.Comparator.comparingInt(WEEK::indexOf));
    return out;
  }

  static String dayText(List<String> days) {
    return days == null ? "" : String.join("", days.stream().map(WEEK_LABEL::get).toList());
  }

  private static List<String> list(JsonNode n) {
    if (n == null || n.isNull()) return null;
    List<String> out = new ArrayList<>();
    n.forEach(x -> out.add(x.asText()));
    return out;
  }

  private static String describe(Object school, Object grade) {
    String s = school == null ? "" : " · " + school;
    return s + (grade == null ? "" : " · " + grade + "학년");
  }

  private static Integer grade(JsonNode a) {
    if (!has(a, "grade")) return null;
    int g = a.get("grade").asInt();
    if (g < 0 || g > 12) throw new IllegalArgumentException("학년은 0~12 사이 숫자입니다.");
    return g;
  }

  private static String date(JsonNode a, String f) {
    return has(a, f) ? LocalDate.parse(a.get(f).asText().strip()).toString() : null;
  }

  private static boolean has(JsonNode a, String f) {
    return a != null && a.hasNonNull(f) && !(a.get(f).isTextual() && a.get(f).asText().isBlank());
  }

  private static String text(JsonNode a, String f) {
    return has(a, f) ? a.get(f).asText().strip() : null;
  }

  private static String str(JsonNode a, String f) {
    return a != null && a.hasNonNull(f) ? a.get(f).asText() : null;
  }

  private static String requireText(JsonNode a, String f, String label) {
    String v = text(a, f);
    if (v == null) throw new IllegalArgumentException(label + "을(를) 주세요.");
    return clip(v, 100);
  }

  private static String upper(String s) {
    return s == null ? "" : s.strip().toUpperCase();
  }

  private static String clip(String s, int max) {
    return s == null ? null : s.length() > max ? s.substring(0, max) : s;
  }
}
