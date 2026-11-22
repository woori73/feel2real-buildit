package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TOEIC 문항을 파트·유형으로 묶는 규칙. 새 열 없이 이미 있는 값만 읽는다.
 * <ul>
 *   <li>파트: {@code tp_questions.task_type} = TOEIC_P1 ~ TOEIC_P7</li>
 *   <li>유형: 해설({@code explanation}) 맨 앞의 꼬리표. 예) {@code [문법-품사(부사 자리)] 정답 (A)…} → "문법-품사",
 *       {@code [Part6-문장삽입] …} → "문장삽입". 괄호 안 세부 설명과 "PartN-" 머리는 뗀다.</li>
 * </ul>
 * 학생 풀이 화면(/learn)의 TOEIC 탭과 부분 응시 시작({@code TestPrepService.start})이 같은 규칙을 쓴다.
 */
public final class ToeicFocus {

  /** 파트 한 개. taskType = tp_questions.task_type, area = 듣기/읽기. */
  public record Part(String taskType, int number, String name, String area) {
    public String label() { return "Part " + number; }
  }

  public static final List<Part> PARTS = List.of(
      new Part("TOEIC_P1", 1, "사진 묘사", "듣기"),
      new Part("TOEIC_P2", 2, "질의응답", "듣기"),
      new Part("TOEIC_P3", 3, "짧은 대화", "듣기"),
      new Part("TOEIC_P4", 4, "설명문", "듣기"),
      new Part("TOEIC_P5", 5, "단문 빈칸 (어휘·어법)", "읽기"),
      new Part("TOEIC_P6", 6, "장문 빈칸 (어휘·어법)", "읽기"),
      new Part("TOEIC_P7", 7, "독해", "읽기"));

  /** 실제 TOEIC L&R 시험 시간을 문항 수로 나눈 값: Reading 75분 ÷ 100문항 = 45초, Listening 45분 ÷ 100문항 = 27초. */
  public static final int READING_SEC_PER_QUESTION = 75 * 60 / 100;
  public static final int LISTENING_SEC_PER_QUESTION = 45 * 60 / 100;

  /** Part 7 끝부분 = 이중·삼중 지문 묶음. 실제 시험 번호 176~200번(order_no). */
  public static final int PART7_LAST_FROM = 176;
  public static final int PART7_LAST_TO = 200;

  /** 실제 문항 번호가 구간 안인지. 구간의 한쪽이 비어 있으면 그쪽은 막지 않는다. */
  public static boolean inRange(Integer orderNo, Integer fromNo, Integer toNo) {
    if (fromNo == null && toNo == null) return true;
    if (orderNo == null) return false;
    return (fromNo == null || orderNo >= fromNo) && (toNo == null || orderNo <= toNo);
  }

  private static final Pattern TAG = Pattern.compile("^\\s*\\[([^\\]]+)\\]");

  /** 해설 맨 앞 꼬리표에서 유형 이름을 얻는다. 꼬리표가 없으면 null. */
  public static String label(String explanation) {
    if (explanation == null) return null;
    Matcher m = TAG.matcher(explanation);
    if (!m.find()) return null;
    String tag = m.group(1).replaceFirst("\\(.*$", "").replaceFirst("^Part\\d-", "").trim();
    return tag.isEmpty() ? null : tag;
  }

  /** 유형이 어법인지 어휘인지. "어휘-동사" → 어휘, 그 밖(문법-품사·접속사·전치사·문장삽입…)은 어법·문맥. */
  public static String area(String label) {
    return label != null && label.startsWith("어휘") ? "어휘" : "어법";
  }

  /** 화면에 보일 유형 이름. "문법-품사" → "품사", "어휘-동사" → "동사 어휘", "어휘" → "문맥 어휘". */
  public static String display(String label) {
    if (label == null) return "";
    if (label.equals("어휘")) return "문맥 어휘";
    if (label.equals("문법")) return "문맥 어법";
    if (label.startsWith("어휘-")) return label.substring(3) + " 어휘";
    if (label.startsWith("문법-")) return label.substring(3);
    return label;
  }

  private ToeicFocus() {}
}
