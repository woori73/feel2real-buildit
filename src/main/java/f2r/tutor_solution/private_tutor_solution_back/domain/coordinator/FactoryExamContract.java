package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * 제작 공장 → 학생 풀이 페이지 게시 계약(디지털 모의고사 형태). 학생 페이지 쪽 계약({@code docs/learn-hub/README.md} 4장)과 같은 값만 받는다.
 *
 * <ul>
 *   <li>모두 {@code tp_exams.kind = NAESIN}, 과목은 시험 코드 머리로 가른다: 내신(영어) {@code TP-NAESIN-<학교 코드>-…},
 *       국어 {@code TP-NAESIN-KOR-}, 수학 {@code -MATH-}, 사회 {@code -SOC-}, 과학 {@code -SCI-}.</li>
 *   <li>문항은 {@code NAESIN_MC5}(하나 고르기)·{@code NAESIN_MC5_MULTI}(모두 고르기), 선택지 5개, 정답 A~E, 배점, 해설 첫 줄 「정답 …」.</li>
 *   <li>그림은 {@code [[img:/naesin/…​.webp]]} 표기로, 이미 사이트에 있는 그림만 쓴다(새 그림 올리기는 이 경로에 없다).</li>
 * </ul>
 * 수능·iBT·SAT·TOEIC은 세트 형식이 달라 이 경로로 올리지 않는다(세트 마이그레이션).
 */
final class FactoryExamContract {

  private FactoryExamContract() {}

  /** 구분 → [시험 코드 머리, 문항 section 값, 학생 페이지 주소]. */
  static final Map<String, String[]> TRACKS = tracks();
  static final int MAX_QUESTIONS = 60;
  static final int MIN_SECONDS = 60;
  static final int MAX_SECONDS = 3 * 60 * 60;
  private static final Pattern CODE = Pattern.compile("^TP-NAESIN-[A-Z0-9]+(?:-[A-Z0-9]+)*-R\\d{2}$");
  private static final Pattern IMG = Pattern.compile("\\[\\[img:([^\\]]*)\\]\\]");
  private static final Pattern IMG_PATH = Pattern.compile("^/naesin/[a-z0-9\\-]+(?:/[a-z0-9\\-]+)*/[a-z0-9\\-]+\\.webp$");
  private static final Pattern SINGLE = Pattern.compile("^[A-E]$");
  private static final Pattern MULTI = Pattern.compile("^[A-E](?:,[A-E]){1,4}$");
  private static final List<String> SUBJECT_HEADS = List.of("TP-NAESIN-KOR-", "TP-NAESIN-MATH-", "TP-NAESIN-SOC-", "TP-NAESIN-SCI-");

  private static Map<String, String[]> tracks() {
    Map<String, String[]> m = new LinkedHashMap<>();
    m.put("NAESIN", new String[] {"TP-NAESIN-", "EN", "/naesintest"});
    m.put("KOREAN", new String[] {"TP-NAESIN-KOR-", "KO", "/learn?tab=korean"});
    m.put("MATH", new String[] {"TP-NAESIN-MATH-", "MATH", "/learn?tab=math"});
    m.put("SOCIAL", new String[] {"TP-NAESIN-SOC-", "SOC", "/learn?tab=social"});
    m.put("SCIENCE", new String[] {"TP-NAESIN-SCI-", "SCI", "/learn?tab=science"});
    return m;
  }

  record Question(int no, String section, String taskType, String prompt, String bodyText, List<String> choices,
      String answerKey, int points, String explanation) {}

  record Exam(String examCode, String title, String subtitle, int timeLimitSec, List<Question> questions) {
    int maxScore() {
      return questions.stream().mapToInt(Question::points).sum();
    }
  }

  static boolean supports(String track) {
    return TRACKS.containsKey(track);
  }

  static String studentUrl(String track) {
    return TRACKS.get(track)[2];
  }

  /**
   * 묶음을 계약에 맞춰 읽는다. 틀린 곳이 있으면 문항 번호를 붙여 모두 모아 한 번에 알려 준다.
   * imageExists: 그림 주소가 사이트에 실제로 있는지(테스트에서는 바꿔 끼운다).
   */
  static Exam parse(String track, JsonNode a, Predicate<String> imageExists) {
    if (!supports(track)) {
      throw new IllegalArgumentException("디지털 모의고사로 게시할 수 있는 구분은 내신(영어)·국어·수학·사회·과학입니다."
          + " 수능·iBT·SAT·TOEIC은 세트 마이그레이션으로 올립니다.");
    }
    List<String> errors = new ArrayList<>();
    String[] t = TRACKS.get(track);
    String code = text(a, "examCode");
    if (!CODE.matcher(code).matches() || code.length() > 40) {
      errors.add("examCode는 " + t[0] + "로 시작하고 -R과 두 자리 회차로 끝나는 40자 이내 대문자 코드입니다(예: " + t[0]
          + ("NAESIN".equals(track) ? "APM2-2602-R01" : "APM2-2602-R01") + ").");
    } else if (!code.startsWith(t[0])) {
      errors.add("examCode는 " + t[0] + "로 시작해야 이 구분의 탭에 나옵니다.");
    } else if ("NAESIN".equals(track) && SUBJECT_HEADS.stream().anyMatch(code::startsWith)) {
      errors.add("내신(영어) 시험 코드에 다른 과목 머리(KOR·MATH·SOC·SCI)를 쓸 수 없습니다.");
    }
    String title = text(a, "title");
    if (title.isEmpty() || title.length() > 150) errors.add("title(목록에 보일 제목)은 1~150자입니다.");
    String subtitle = text(a, "subtitle");
    if (subtitle.length() > 255) errors.add("subtitle(시험 범위 쪽수 구간·문항 수·만점)은 255자까지입니다.");
    int seconds = a != null && a.hasNonNull("timeLimitSec") ? a.get("timeLimitSec").asInt(0) : 0;
    if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) errors.add("timeLimitSec는 60~10800초입니다.");
    JsonNode raw = a == null ? null : a.get("questions");
    List<Question> questions = new ArrayList<>();
    if (raw == null || !raw.isArray() || raw.isEmpty() || raw.size() > MAX_QUESTIONS) {
      errors.add("questions는 1~" + MAX_QUESTIONS + "문항입니다.");
    } else {
      int expected = 1;
      for (JsonNode q : raw) {
        questions.add(question(q, expected, t[1], imageExists, errors));
        expected++;
      }
    }
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException("시험 묶음이 게시 계약과 다릅니다:\n- " + String.join("\n- ",
          errors.subList(0, Math.min(errors.size(), 30))) + (errors.size() > 30 ? "\n- …외 " + (errors.size() - 30) + "건" : ""));
    }
    return new Exam(code, title, subtitle.isEmpty() ? null : subtitle, seconds, List.copyOf(questions));
  }

  private static Question question(JsonNode q, int no, String section, Predicate<String> imageExists, List<String> errors) {
    String at = no + "번: ";
    int given = q.hasNonNull("no") ? q.get("no").asInt(0) : no;
    if (given != no) errors.add(at + "no는 1부터 차례대로입니다(" + given + ").");
    String type = text(q, "taskType");
    if (type.isEmpty()) type = "NAESIN_MC5";
    if (!"NAESIN_MC5".equals(type) && !"NAESIN_MC5_MULTI".equals(type)) errors.add(at + "taskType은 NAESIN_MC5·NAESIN_MC5_MULTI입니다.");
    String prompt = text(q, "prompt");
    if (prompt.isEmpty()) errors.add(at + "prompt(발문)가 비었습니다.");
    String body = text(q, "bodyText");
    images(prompt, at, imageExists, errors);
    images(body, at, imageExists, errors);
    List<String> choices = new ArrayList<>();
    JsonNode c = q.get("choices");
    if (c == null || !c.isArray() || c.size() != 5) {
      errors.add(at + "choices는 5개입니다.");
    } else {
      for (JsonNode n : c) {
        String v = n.asText("").strip();
        if (v.isEmpty()) errors.add(at + "빈 선택지가 있습니다.");
        choices.add(v);
      }
    }
    String key = text(q, "answerKey").toUpperCase().replace(" ", "");
    boolean multi = "NAESIN_MC5_MULTI".equals(type);
    if (!(multi ? MULTI.matcher(key).matches() || SINGLE.matcher(key).matches() : SINGLE.matcher(key).matches())) {
      errors.add(at + (multi ? "answerKey는 A~E를 쉼표로(예: B,D)" : "answerKey는 A~E 중 하나") + "입니다.");
    }
    int points = q.hasNonNull("points") ? q.get("points").asInt(0) : 0;
    if (points < 1 || points > 20) errors.add(at + "points(배점)는 1~20입니다.");
    String explanation = text(q, "explanation");
    if (!explanation.startsWith("정답")) errors.add(at + "explanation 첫 줄은 「정답 ③ · 유형 (배점)」 형식입니다.");
    return new Question(no, section, type, prompt, body.isEmpty() ? null : body, List.copyOf(choices), key, points, explanation);
  }

  private static void images(String text, String at, Predicate<String> imageExists, List<String> errors) {
    Matcher m = IMG.matcher(text == null ? "" : text);
    while (m.find()) {
      String path = m.group(1).strip();
      if (!IMG_PATH.matcher(path).matches()) errors.add(at + "그림은 [[img:/naesin/…​.webp]] 형식만 됩니다(" + path + ").");
      else if (!imageExists.test(path)) errors.add(at + "사이트에 없는 그림입니다(" + path + "). 그림은 먼저 저장소에 올려야 합니다.");
    }
  }

  private static String text(JsonNode a, String f) {
    return a != null && a.hasNonNull(f) ? a.get(f).asText().strip() : "";
  }
}
