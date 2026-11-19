package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.LessonForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.WrongItem;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * AI 코디네이터 — 학생부 말 명령. "11월 20일 김아무개 수업 범위·숙제" → 수업 기록, 채점된 답지 사진을 Claude가 읽은 결과
 * (맞은 수·틀린 문항·피드백) → 채점 기록. 기존 {@link EasyLessonRecordService}만 부른다. 되돌리기 = 방금 만든 기록 삭제.
 * 답지 사진은 MCP로 받지 않고, 실행 뒤 주는 링크(학생부 화면)에서 사용자가 올린다.
 */
@Component
public class CoordRecordActions implements CoordActions {

  public static final String LESSON_RECORD = "LESSON_RECORD";
  public static final String GRADING_RECORD = "GRADING_RECORD";
  static final Set<String> KINDS = Set.of(LESSON_RECORD, GRADING_RECORD);
  static final Map<String, String> CHECK_LABEL = Map.of("DONE", "다 해 옴", "PARTIAL", "일부 해 옴", "NOT_DONE", "안 해 옴");

  private final EasyLessonRecordService records;
  private final EasyStudentRepository students;
  private final Clock clock;

  @Autowired
  public CoordRecordActions(EasyLessonRecordService records, EasyStudentRepository students) {
    this(records, students, Clock.system(KstTime.ZONE));
  }

  CoordRecordActions(EasyLessonRecordService records, EasyStudentRepository students, Clock clock) {
    this.records = records;
    this.students = students;
    this.clock = clock;
  }

  @Override
  public Set<String> areas() {
    return Set.of("RECORD");
  }

  @Override
  public Set<String> kinds() {
    return KINDS;
  }

  /** 학생부 조회(AI용): 이름은 가리고 학교는 뺀다. 사진은 개수만. */
  public Map<String, Object> book(Long bid, Long studentId) {
    Map<String, Object> b = new LinkedHashMap<>(records.studentBook(bid, studentId));
    b.put("displayName", CoordinatorService.maskName((String) b.remove("name")));
    b.remove("school");
    b.put("note", "이름은 가려서 보여 줍니다. 답지 사진은 학생부 화면에서만 볼 수 있습니다.");
    return b;
  }

  /* ---------------- 초안 ---------------- */

  @Override
  public Draft prepare(String area, Long bid, String action, JsonNode a) {
    String act = action == null ? "" : action.strip().toUpperCase();
    EasyStudent s = requireStudent(a);
    String masked = CoordinatorService.maskName(s.getName());
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("businessId", bid);
    plan.put("studentId", s.getStudentId());
    if ("LESSON".equals(act)) {
      String day = day(a, "lessonDate");
      plan.put("classId", has(a, "classId") ? a.get("classId").asLong() : null);
      plan.put("lessonDate", day);
      plan.put("lessonRange", text(a, "lessonRange"));
      String check = has(a, "homeworkCheck") ? text(a, "homeworkCheck").toUpperCase() : null;
      if (check != null && !CHECK_LABEL.containsKey(check)) {
        throw new IllegalArgumentException("homeworkCheck는 DONE(다 해 옴)·PARTIAL(일부)·NOT_DONE(안 해 옴) 중 하나입니다.");
      }
      plan.put("homeworkCheck", check);
      plan.put("homeworkDoneNote", text(a, "homeworkDoneNote"));
      plan.put("nextHomework", text(a, "nextHomework"));
      plan.put("teacherNote", text(a, "teacherNote"));
      List<String> parts = new ArrayList<>();
      if (plan.get("lessonRange") != null) parts.add("수업 범위: " + plan.get("lessonRange"));
      if (check != null) parts.add("숙제: " + CHECK_LABEL.get(check));
      if (plan.get("homeworkDoneNote") != null) parts.add("해 온 것: " + plan.get("homeworkDoneNote"));
      if (plan.get("nextHomework") != null) parts.add("낸 숙제: " + plan.get("nextHomework"));
      if (plan.get("teacherNote") != null) parts.add("메모: " + plan.get("teacherNote"));
      if (parts.isEmpty()) throw new IllegalArgumentException("수업 범위·숙제 확인·해 온 숙제·낸 숙제·메모 중 하나는 주세요.");
      String tail = " · " + day + " 수업 기록 · " + String.join(" / ", parts);
      return new Draft(LESSON_RECORD, plan, s.getName() + tail, masked + tail);
    }
    if ("GRADING".equals(act)) {
      String day = day(a, "gradedOn");
      Integer total = has(a, "totalCount") ? a.get("totalCount").asInt() : null;
      Integer correct = has(a, "correctCount") ? a.get("correctCount").asInt() : null;
      List<Map<String, Object>> wrong = new ArrayList<>();
      if (a.has("wrongItems") && a.get("wrongItems").isArray()) {
        for (JsonNode w : a.get("wrongItems")) {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("no", text(w, "no"));
          m.put("topic", text(w, "topic"));
          m.put("reason", text(w, "reason"));
          wrong.add(m);
        }
      }
      if (total != null && correct == null) correct = Math.max(0, total - wrong.size());
      plan.put("lessonRecordId", has(a, "lessonRecordId") ? a.get("lessonRecordId").asLong() : null);
      plan.put("gradedOn", day);
      plan.put("subjectArea", text(a, "subjectArea"));
      plan.put("material", text(a, "material"));
      plan.put("totalCount", total);
      plan.put("correctCount", correct);
      plan.put("wrongItems", wrong);
      plan.put("feedback", text(a, "feedback"));
      String score = total == null ? "" : " · " + correct + "/" + total;
      String nums = wrong.isEmpty() ? "" : " · 틀린 문항 " + String.join(",", wrong.stream()
          .map(w -> w.get("no") == null ? "?" : String.valueOf(w.get("no"))).toList());
      String area2 = plan.get("subjectArea") == null ? "" : " · " + plan.get("subjectArea");
      String tail = " · " + day + " 채점 기록" + area2 + score + nums
          + (plan.get("feedback") == null ? "" : " · 피드백 있음");
      return new Draft(GRADING_RECORD, plan, s.getName() + tail, masked + tail);
    }
    throw new IllegalArgumentException("action은 LESSON(수업 기록)·GRADING(채점 기록) 중 하나입니다.");
  }

  /* ---------------- 실행·되돌리기 ---------------- */

  @Override
  public Map<String, Object> execute(String kind, JsonNode p) {
    Long bid = p.get("businessId").asLong();
    Long sid = p.get("studentId").asLong();
    Map<String, Object> r = new LinkedHashMap<>();
    String book = "/easy/records?businessId=" + bid + "&studentId=" + sid;
    if (LESSON_RECORD.equals(kind)) {
      var v = records.createLesson(bid, sid, new LessonForm(p.hasNonNull("classId") ? p.get("classId").asLong() : null,
          str(p, "lessonDate"), str(p, "lessonRange"), str(p, "homeworkCheck"), str(p, "homeworkDoneNote"),
          str(p, "nextHomework"), str(p, "teacherNote")), "MCP");
      r.put("lessonRecordId", v.lessonRecordId());
      r.put("label", "수업 기록");
      r.put("linkPath", book);
      return r;
    }
    if (GRADING_RECORD.equals(kind)) {
      List<WrongItem> wrong = new ArrayList<>();
      p.path("wrongItems").forEach(w -> wrong.add(new WrongItem(str(w, "no"), str(w, "topic"), str(w, "reason"))));
      var v = records.createGrading(bid, sid, new GradingForm(
          p.hasNonNull("lessonRecordId") ? p.get("lessonRecordId").asLong() : null, str(p, "gradedOn"),
          str(p, "subjectArea"), str(p, "material"), p.hasNonNull("totalCount") ? p.get("totalCount").asInt() : null,
          p.hasNonNull("correctCount") ? p.get("correctCount").asInt() : null, wrong, str(p, "feedback")), "MCP");
      r.put("gradingId", v.gradingId());
      r.put("label", "채점 기록");
      r.put("linkPath", book + "&grading=" + v.gradingId());
      return r;
    }
    throw new IllegalArgumentException("알 수 없는 작업입니다: " + kind);
  }

  @Override
  public void undo(String kind, JsonNode plan, JsonNode result) {
    Long bid = plan.get("businessId").asLong();
    if (LESSON_RECORD.equals(kind)) {
      records.deleteLesson(bid, result.get("lessonRecordId").asLong());
      return;
    }
    if (GRADING_RECORD.equals(kind)) {
      records.deleteGrading(bid, result.get("gradingId").asLong());
      return;
    }
    throw new IllegalArgumentException("알 수 없는 작업입니다: " + kind);
  }

  /* ---------------- 내부 ---------------- */

  private EasyStudent requireStudent(JsonNode a) {
    if (!has(a, "studentId")) throw new IllegalArgumentException("studentId가 필요합니다. 먼저 f2r_student_search로 찾으세요.");
    return students.findById(a.get("studentId").asLong()).orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
  }

  private String day(JsonNode a, String f) {
    if (!has(a, f)) return LocalDate.now(clock).toString();
    try {
      return LocalDate.parse(a.get(f).asText().strip()).toString();
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException(f + "는 YYYY-MM-DD 형식입니다.");
    }
  }

  private static boolean has(JsonNode a, String f) {
    return a != null && a.hasNonNull(f) && !(a.get(f).isTextual() && a.get(f).asText().isBlank());
  }

  private static String text(JsonNode a, String f) {
    return has(a, f) ? a.get(f).asText().strip() : null;
  }

  private static String str(JsonNode a, String f) {
    return a.hasNonNull(f) ? a.get(f).asText() : null;
  }
}
