package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller.NaesinTestPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller.NaesinTestPageController.School;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 제작 공장 — 화면에서 바로 탑재(대표 결정 2026-11-22: 탑재는 개발 PR 방식과 화면에서 바로 올리기 둘 다).
 *
 * <p>검수 3곳 통과 + 원장 게시 승인을 거친 내신(영어) 5지선다 글 문항을 「게시용 파일」(JSON) 그대로 받아
 * {@code tp_exams}(kind=NAESIN, status=PUBLISHED)·{@code tp_questions}에 넣는다 — 기존 마이그레이션(V1121)이 넣던 것과 같은 행이라
 * 학생 풀이 페이지(/naesintest/school/{slug})에 바로 보인다. 그림 문항({@code [[img:…]]})은 그림 파일 배포가 필요해 받지 않는다(PR 방식).
 * 내리기는 지우지 않고 status=ARCHIVED(학생 응시 기록 보존).
 */
@Service
public class FactoryExamUploader {

  static final int MAX_QUESTIONS = 100;
  /** 기존 내신 회차와 같은 값(화면의 풀이 시간은 문항 수로 따로 계산한다). */
  static final int DEFAULT_TIME_LIMIT_SEC = 7200;
  private static final String LETTERS = "ABCDE";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final JdbcTemplate jdbc;

  public FactoryExamUploader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  record Question(String taskType, String prompt, String body, String choicesJson, String answerKey, int points, String explanation) {}

  record Pack(String examCode, School school, String title, String subtitle, String description, int timeLimitSec,
      List<Question> questions) {
    int totalPoints() {
      return questions.stream().mapToInt(Question::points).sum();
    }

    String url() {
      return "/naesintest/school/" + school.slug();
    }
  }

  /** 게시용 파일을 넣고 학생 페이지 주소를 돌려준다. 같은 시험 코드가 이미 있으면 거절(덮어쓰지 않는다). */
  @Transactional
  public Pack upload(JsonNode file, String loginId) {
    Pack pack = parse(file);
    Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM tp_exams WHERE exam_code = ?", Integer.class, pack.examCode());
    if (exists != null && exists > 0) {
      throw new IllegalStateException("시험 코드 " + pack.examCode() + "는 이미 올라가 있습니다. 회차 번호를 바꿔 주세요(덮어쓰지 않습니다).");
    }
    jdbc.update("INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by)"
        + " VALUES (?, 'NAESIN', ?, ?, ?, NULL, ?, 'PUBLISHED', ?)",
        pack.examCode(), pack.title(), pack.subtitle(), pack.description(), pack.timeLimitSec(), loginId);
    Long examId = jdbc.queryForObject("SELECT exam_id FROM tp_exams WHERE exam_code = ?", Long.class, pack.examCode());
    int no = 1;
    for (Question q : pack.questions()) {
      jdbc.update("INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key,"
          + " auto_score, explanation, order_no, points, created_by) VALUES (?, 'EN', ?, ?, ?, 'NONE', ?, ?, 1, ?, ?, ?, ?)",
          examId, q.taskType(), q.prompt(), q.body(), q.choicesJson(), q.answerKey(), q.explanation(), no++, q.points(), loginId);
    }
    return pack;
  }

  /** 화면에서 올린 회차를 학생 페이지에서 내린다(행은 남김). */
  @Transactional
  public void archive(String examCode, String loginId) {
    jdbc.update("UPDATE tp_exams SET status = 'ARCHIVED', updated_by = ? WHERE exam_code = ? AND kind = 'NAESIN'", loginId, examCode);
  }

  /**
   * 게시용 파일 검증. 틀린 곳을 모두 모아 한 번에 알려 준다.
   *
   * <pre>
   * { "examCode": "TP-NAESIN-SGM2-2602-R12", "title": "신구중 2학년 2학기 기말 12회 · Lesson 7·8 종합 예상문제 2회",
   *   "subtitle": "Lesson 7·8 전 범위 · 40문항 · 20분 · 100점", "description": "…",
   *   "questions": [ { "prompt": "발문", "body": "지문(없으면 생략)", "choices": ["…", "…", "…", "…", "…"],
   *                    "answer": "A" (모두 고르기는 "B,D"), "points": 2, "explanation": "해설" } ] }
   * </pre>
   */
  static Pack parse(JsonNode f) {
    if (f == null || !f.isObject()) throw new IllegalArgumentException("게시용 파일(JSON)이 비어 있습니다.");
    List<String> errors = new ArrayList<>();
    String code = text(f, "examCode").toUpperCase();
    School school = NaesinTestPageController.SCHOOLS.stream().filter(s -> code.startsWith(s.codePrefix())).findFirst().orElse(null);
    if (!code.matches("TP-NAESIN-[A-Z0-9]{2,8}-\\d{4}-R\\d{2}")) {
      errors.add("examCode는 TP-NAESIN-SGM2-2602-R12 같은 형식입니다(학교-학년 · 연도 뒤 두 자리+학기+시험 · 회차).");
    } else if (school == null) {
      errors.add("examCode의 학교(" + code + ")가 학생 풀이 페이지에 없습니다. 올릴 수 있는 앞부분: "
          + String.join(", ", NaesinTestPageController.SCHOOLS.stream().map(s -> s.codePrefix() + "(" + s.title() + ")").toList()));
    }
    String title = text(f, "title");
    if (title.isEmpty() || title.length() > 150) errors.add("title(시험 이름, 150자까지)이 필요합니다.");
    String subtitle = text(f, "subtitle");
    if (subtitle.length() > 255) errors.add("subtitle은 255자까지입니다.");
    int limit = f.hasNonNull("timeLimitSec") ? f.get("timeLimitSec").asInt() : DEFAULT_TIME_LIMIT_SEC;
    if (limit < 60 || limit > 14400) errors.add("timeLimitSec은 60~14400초입니다.");
    JsonNode qs = f.path("questions");
    List<Question> questions = new ArrayList<>();
    if (!qs.isArray() || qs.isEmpty() || qs.size() > MAX_QUESTIONS) {
      errors.add("questions는 1~" + MAX_QUESTIONS + "문항이어야 합니다.");
    } else {
      int no = 1;
      for (JsonNode q : qs) {
        Question parsed = question(q, no++, errors);
        if (parsed != null) questions.add(parsed);
      }
    }
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException("게시용 파일을 올릴 수 없습니다 — " + String.join(" / ", errors.subList(0, Math.min(errors.size(), 12)))
          + (errors.size() > 12 ? " … 외 " + (errors.size() - 12) + "건" : ""));
    }
    return new Pack(code, school, title, subtitle.isEmpty() ? null : subtitle,
        text(f, "description").isEmpty() ? null : text(f, "description"), limit, questions);
  }

  private static Question question(JsonNode q, int no, List<String> errors) {
    int before = errors.size();
    String prompt = text(q, "prompt");
    String body = text(q, "body");
    String explanation = text(q, "explanation");
    if (prompt.isEmpty()) errors.add(no + "번: prompt(발문)가 없습니다.");
    if ((prompt + body).contains("[[img:")) errors.add(no + "번: 그림 문항은 화면에서 올릴 수 없습니다(그림 파일 배포가 필요해 PR 방식으로).");
    JsonNode choices = q.path("choices");
    List<String> list = new ArrayList<>();
    if (choices.isArray()) for (JsonNode c : choices) list.add(c.asText("").strip());
    if (list.size() != 5 || list.stream().anyMatch(String::isEmpty)) errors.add(no + "번: choices는 빈칸 없는 선택지 5개입니다.");
    List<String> answers = new ArrayList<>();
    for (String a : text(q, "answer").toUpperCase().split(",")) {
      String v = a.strip();
      if (v.length() != 1 || !LETTERS.contains(v) || answers.contains(v)) {
        errors.add(no + "번: answer는 A~E 한 글자(모두 고르기는 \"B,D\")입니다.");
        break;
      }
      answers.add(v);
    }
    int points = q.path("points").asInt(0);
    if (points < 1 || points > 20) errors.add(no + "번: points(배점)는 1~20입니다.");
    if (explanation.isEmpty()) errors.add(no + "번: explanation(해설)이 없습니다.");
    if (errors.size() > before) return null;
    answers.sort(null);
    return new Question(answers.size() > 1 ? "NAESIN_MC5_MULTI" : "NAESIN_MC5", prompt, body.isEmpty() ? null : body,
        JSON.writeValueAsString(list), String.join(",", answers), points, explanation);
  }

  private static String text(JsonNode a, String f) {
    return a != null && a.hasNonNull(f) ? a.get(f).asText().strip() : "";
  }
}
