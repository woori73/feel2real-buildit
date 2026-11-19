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
  /**
   * 채점 순서(2026-11-19 사용자 지시). 정답 근거를 이 순서로 찾는다: ① 정답지 사진 → ② 내신 문제면 홈페이지 「디지털 내신문제」 정답·해설
   * (f2r_naesin_answer_key)과 내신 Wiki(get_final_context의 generated_tests·final_deliverables·weekly_exam_finals) →
   * ③ 내신이 아닌 문제는 사용자에게 정답지 사진을 함께 달라고 요청 → ④ 사용자가 문제지만으로 하라고 하면 직접 풀어 채점하고 불확실한 문항은
   * '확인 필요', 피드백에 "정답지 없이 직접 풀어 채점"을 적는다.
   */
  static final String GRADING_GUIDE = "이미지 순서는 images 목록과 같습니다. 정답 근거는 이 순서로 찾으세요."
      + " ① 정답지 사진이 있으면 그것과 학생 답안을 대조합니다."
      + " ② 정답지가 없고 내신 문제(학교 시험 대비)면 f2r_naesin_answer_key(홈페이지 디지털 내신문제의 정답·해설)와 BuildIt Wiki"
      + " get_final_context의 generated_tests·final_deliverables·weekly_exam_finals에서 같은 회차·같은 문항을 찾아 정답과 해설을 대조합니다"
      + "(문항 번호와 문제가 같은지 먼저 확인)."
      + " ③ 내신이 아닌 문제(교재·모의고사 등)는 채점 전에 사용자에게 정답지 사진을 문제지와 함께 올려 달라고 요청합니다."
      + " ④ 사용자가 정답지 없이 문제지만으로 하라고 하면 직접 풀어 채점하되, 확실하지 않은 문항은 '확인 필요'로 표시하고"
      + " feedback에 '정답지 없이 Claude가 직접 풀어 채점함'을 적습니다."
      + " 읽을 수 없는 답은 추측하지 마세요. 틀린 문항을 바로 알려 준 뒤 f2r_grading_save로 저장하고, f2r_weakness_save로 약점 정리를 갱신하세요.";

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

  /** 학생부 화면 주소(상대). 원장이 "그동안 기록 보여줘"라고 하면 AI가 이 링크를 준다. */
  static String bookPath(Long bid, Long studentId) {
    return "/easy/records?businessId=" + bid + "&studentId=" + studentId;
  }

  /** 학생부 조회(AI용): 이름은 가리고 학교는 뺀다. 사진은 개수만. bookPath = 학생부 화면. */
  public Map<String, Object> book(Long bid, Long studentId) {
    Map<String, Object> b = new LinkedHashMap<>(records.studentBook(bid, studentId));
    b.put("displayName", CoordinatorService.maskName((String) b.remove("name")));
    b.remove("school");
    b.put("bookPath", bookPath(bid, studentId));
    b.put("note", "이름은 가려서 보여 줍니다. 사진은 학생부 화면에서 봅니다. 원장에게 bookUrl을 주어 화면으로 보게 하세요.");
    return b;
  }

  /** 채점 전(PENDING) 목록(AI용, 가린 이름). studentId를 주면 그 학생 것만. */
  public Map<String, Object> pending(Long bid, Long studentId) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (var g : records.pending(bid)) {
      if (studentId != null && !studentId.equals(g.studentId())) continue;
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("gradingId", g.gradingId());
      m.put("studentId", g.studentId());
      m.put("displayName", students.findById(g.studentId()).map(s -> CoordinatorService.maskName(s.getName())).orElse(""));
      m.put("gradedOn", g.gradedOn());
      m.put("subjectArea", g.subjectArea());
      m.put("material", g.material());
      Map<String, Long> kinds = new LinkedHashMap<>();
      g.photos().forEach(ph -> kinds.merge(ph.kindLabel(), 1L, Long::sum));
      m.put("photos", kinds);
      rows.add(m);
    }
    return Map.of("pending", rows, "note", "f2r_grading_photos(gradingId)로 문제지·정답지·학생 답안 사진을 받아 채점하세요.");
  }

  /** 채점 사진 묶음(AI용): 사진마다 종류 라벨. */
  public record PhotoBundle(Map<String, Object> meta, List<String> labels, List<byte[]> images) {}

  public PhotoBundle photos(Long bid, Long gradingId) throws java.io.IOException {
    var g = records.grading(bid, gradingId);
    var list = records.photosForAi(bid, gradingId);
    if (list.isEmpty()) throw new IllegalArgumentException("올린 사진이 없습니다. 학생부 화면에서 문제지·정답지·학생 답안을 먼저 올려 주세요.");
    List<String> labels = new ArrayList<>();
    List<byte[]> images = new ArrayList<>();
    for (var ph : list) {
      labels.add(EasyLessonRecordService.PHOTO_KIND_LABEL.getOrDefault(ph.kind(), ph.kind()) + " #" + ph.photoId());
      images.add(ph.bytes());
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("gradingId", gradingId);
    meta.put("studentId", g.studentId());
    meta.put("gradedOn", g.gradedOn());
    meta.put("subjectArea", g.subjectArea());
    meta.put("material", g.material());
    meta.put("status", g.status());
    meta.put("images", labels);
    meta.put("instruction", GRADING_GUIDE);
    return new PhotoBundle(meta, labels, images);
  }

  /** Claude 채점 결과 저장(바로 저장 — 사용자 지시 "틀린 부분 바로 알려주고 학생기록에 남김"). */
  public Map<String, Object> saveResult(Long bid, Long gradingId, JsonNode a) {
    List<WrongItem> wrong = new ArrayList<>();
    a.path("wrongItems").forEach(w -> wrong.add(new WrongItem(text(w, "no"), text(w, "topic"), text(w, "reason"))));
    var v = records.saveResult(bid, gradingId, new EasyLessonRecordService.ResultForm(
        has(a, "totalCount") ? a.get("totalCount").asInt() : null, has(a, "correctCount") ? a.get("correctCount").asInt() : null,
        wrong, text(a, "feedback"), text(a, "subjectArea")));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("saved", true);
    out.put("gradingId", v.gradingId());
    out.put("status", v.status());
    out.put("score", v.totalCount() == null ? null : v.correctCount() + "/" + v.totalCount());
    out.put("wrongCount", v.wrongItems().size());
    out.put("bookPath", bookPath(bid, v.studentId()) + "&grading=" + v.gradingId());
    out.put("message", "학생부에 저장했습니다. 학생부 화면에서 고치거나 지울 수 있습니다.");
    return out;
  }

  /** 약점 정리 저장(바로 저장, 쌓임). */
  public Map<String, Object> saveWeakness(Long bid, JsonNode a) {
    EasyStudent s = requireStudent(a);
    List<EasyLessonRecordService.WeaknessTopic> topics = new ArrayList<>();
    a.path("topics").forEach(t -> topics.add(new EasyLessonRecordService.WeaknessTopic(text(t, "topic"), text(t, "level"),
        text(t, "evidence"))));
    var v = records.saveWeakness(bid, s.getStudentId(), text(a, "summary"), topics, "MCP");
    return Map.of("saved", true, "noteId", v.noteId(), "topicCount", v.topics().size(),
        "bookPath", bookPath(bid, s.getStudentId()), "message", "약점 정리를 학생부에 저장했습니다.");
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
    String book = bookPath(bid, sid);
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
          p.hasNonNull("correctCount") ? p.get("correctCount").asInt() : null, wrong, str(p, "feedback"), "GRADED"), "MCP");
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
