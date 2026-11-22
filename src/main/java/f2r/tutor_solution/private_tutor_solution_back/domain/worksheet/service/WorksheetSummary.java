package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank.Question;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 학습지 목록의 타입·학년·요약 칸에 쓸 글을 고른 문항에서 만든다. */
public final class WorksheetSummary {

  private static final Pattern MIDDLE_GRADE = Pattern.compile("중\\s*(\\d)학년");

  private WorksheetSummary() {}

  /** 타입: 내신 문항만이면 '내신', 어법 문항만이면 '어법', 섞였으면 '혼합'. */
  public static String kind(List<Question> questions) {
    boolean naesin = questions.stream().anyMatch(q -> WorksheetQuestionBank.NAESIN.equals(q.source()));
    boolean grammar = questions.stream().anyMatch(q -> WorksheetQuestionBank.GRAMMAR.equals(q.source()));
    if (naesin && grammar) return "혼합";
    return naesin ? "내신" : grammar ? "어법" : "";
  }

  /** 학년: 내신 문항은 묶음 이름의 '중 N학년'에서, 어법 문항은 예비고1. 여럿이면 가운뎃점으로 잇는다. */
  public static String grade(List<Question> questions) {
    Set<String> grades = new LinkedHashSet<>();
    for (Question q : questions) {
      if (WorksheetQuestionBank.GRAMMAR.equals(q.source())) {
        grades.add("예비고1");
        continue;
      }
      Matcher m = MIDDLE_GRADE.matcher(q.setTitle() == null ? "" : q.setTitle());
      if (m.find()) grades.add("중" + m.group(1));
    }
    return clip(String.join("·", grades), 40);
  }

  /** 요약: 난이도별 수 | 유형별 수(많은 순 3개) | 첫 범위 외 N. 예: '하 2·중 3·상 5 | 어법 6, 독해 4 | 압구정중 2학년 … 1회 외 1' */
  public static String scope(List<Question> questions) {
    if (questions.isEmpty()) return "";
    Map<String, Integer> levels = new LinkedHashMap<>();
    for (String level : List.of("하", "중", "상")) levels.put(level, 0);
    Map<String, Integer> types = new LinkedHashMap<>();
    Set<String> sets = new LinkedHashSet<>();
    for (Question q : questions) {
      if (q.level() != null) levels.merge(q.level(), 1, Integer::sum);
      types.merge(q.type(), 1, Integer::sum);
      sets.add(q.setTitle());
    }
    StringBuilder sb = new StringBuilder();
    String levelText = levels.entrySet().stream().filter(e -> e.getValue() > 0)
        .map(e -> e.getKey() + " " + e.getValue()).reduce((a, b) -> a + "·" + b).orElse("");
    if (!levelText.isEmpty()) sb.append(levelText).append(" | ");
    sb.append(types.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(3)
        .map(e -> e.getKey() + " " + e.getValue()).reduce((a, b) -> a + ", " + b).orElse(""));
    String first = sets.iterator().next();
    sb.append(" | ").append(first).append(sets.size() > 1 ? " 외 " + (sets.size() - 1) : "");
    return clip(sb.toString(), 255);
  }

  static String clip(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max - 1) + "…";
  }
}
