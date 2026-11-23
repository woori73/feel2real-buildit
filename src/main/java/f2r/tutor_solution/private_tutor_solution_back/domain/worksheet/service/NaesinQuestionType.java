package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 내신 예상문제 문항의 유형·난이도를 문항에 이미 적혀 있는 값에서 읽는다.
 * 문항 표(tp_questions)에는 유형·난이도 칸이 없어서, 해설 첫 줄의 발문과 배점을 쓴다.
 */
public final class NaesinQuestionType {

  public static final String WRITING = "서술형·영작";
  public static final String GRAMMAR = "어법";
  public static final String VOCABULARY = "어휘";
  public static final String DIALOGUE = "대화";
  public static final String READING = "독해";
  public static final String OTHER = "기타";

  /** 해설 첫 줄 '정답 ② · 발문 (3점)'에서 발문만 꺼낸다. */
  private static final Pattern HEAD =
      Pattern.compile("^정답[^·\\n]*·\\s*([^\\n]+?)\\s*(?:\\(\\d+점\\))?\\s*$", Pattern.MULTILINE);

  private NaesinQuestionType() {}

  /** 해설에서 발문을 꺼낸다. 형식이 다르면 빈 값. */
  public static String stem(String explanation) {
    if (explanation == null) return "";
    Matcher m = HEAD.matcher(explanation);
    return m.find() ? m.group(1).strip() : "";
  }

  /** 발문에 든 낱말로 영어 문항 유형을 나눈다. 앞에 있는 규칙이 먼저다. */
  public static String classify(String stem) {
    if (stem == null || stem.isBlank()) return OTHER;
    if (has(stem, "서술", "영작", "쓰시오", "완성하시오", "배열하")) return WRITING;
    if (has(stem, "어법", "문법", "올바른 문장", "어색한 문장", "쓰임", "전환", "변화형", "비교급", "관계대명사", "전치사",
        "지각동사", "사역동사")) return GRAMMAR;
    if (has(stem, "단어", "어휘", "영영", "뜻", "의미", "발음", "속담", "표현")) return VOCABULARY;
    if (has(stem, "대화", "응답", "대답")) return DIALOGUE;
    if (has(stem, "윗글", "글의", "글을", "내용", "주제", "제목", "요지", "빈칸", "순서", "일치", "답할 수 없는", "이유",
        "추론", "유추", "지칭", "연결어", "문장 넣기", "요약", "흐름", "Which is true")) return READING;
    return OTHER;
  }

  /** 배점으로 난이도를 나눈다: 2점 이하 하, 3점 중, 4점 이상 상. 배점이 없으면 null(구분 없음). */
  public static String level(Integer points) {
    if (points == null) return null;
    if (points <= 2) return "하";
    return points == 3 ? "중" : "상";
  }

  private static boolean has(String text, String... words) {
    for (String w : words) if (text.contains(w)) return true;
    return false;
  }
}
