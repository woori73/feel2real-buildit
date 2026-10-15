package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 갈무리 DISPLAY 변환 — DB의 RAG용 structured 원본(## 분류/RESULT_CONFIRMED/UNKNOWN 등)을
 * 그대로 출력하지 않고 사람이 읽는 형식으로 재조립한다. 원본 행은 수정·삭제하지 않으며
 * 변환은 API 응답 생성 시점에만 일어난다(MACHINE DATA ≠ HUMAN DISPLAY, 2026-09-30 지시).
 *
 * <p>대상 형식은 {@code NewstartCuratedReviewIngestService.buildCaseContent}가 생성한
 * {@code ## 분류 / ## 결과/상태 / ## 상황 / ## 원문 본문} 직렬화다. 다른 형식(이미 사람이 읽는
 * 텍스트)은 통과시킨다. UNKNOWN을 나열하지 않고, 원문이 지지하는 사실만 재배치한다(창작 금지).
 */
final class NewStartGalmuriDisplay {

  private static final String CLASSIFY_HEADER = "## 분류";
  private static final String RESULT_HEADER = "## 결과/상태";
  private static final String SITUATION_HEADER = "## 상황";
  private static final String BODY_HEADER = "## 원문 본문";

  private static final String RESULT_CONFIRMED = "RESULT_CONFIRMED";
  private static final String OPERATOR_PREDICTION = "OPERATOR_PREDICTION";

  private NewStartGalmuriDisplay() {}

  static String humanizeContent(String content) {
    if (content == null || !content.contains(CLASSIFY_HEADER) || !content.contains(BODY_HEADER)) {
      return content;
    }
    Map<String, String> classified = splitSections(content);
    String classification = firstToken(classified.get("분류"));
    List<Pair> results = pairs(classified.get(RESULT_HEADER.substring(3)));
    List<Pair> situation = pairs(classified.get(SITUATION_HEADER.substring(3)));
    String body = classified.getOrDefault("원문 본문", "").strip();

    StringBuilder out = new StringBuilder();
    List<String> situationLines = humanPairs(situation);
    if (!situationLines.isEmpty()) {
      out.append("[상황]\n");
      situationLines.forEach(l -> out.append("- ").append(l).append('\n'));
      out.append('\n');
    }

    List<String> confirmedLines = new ArrayList<>();
    List<String> predictedLines = new ArrayList<>();
    for (Pair p : results) {
      String value = humanValue(p.value);
      if (value == null) continue;
      if (p.key.contains("예측")) {
        predictedLines.add(p.key + ": " + value);
      } else {
        confirmedLines.add(humanKey(p.key) + ": " + value);
      }
    }
    out.append("[결과]\n");
    if (!confirmedLines.isEmpty()) {
      confirmedLines.forEach(l -> out.append("- ").append(l).append('\n'));
    } else {
      out.append("- 아직 최종 결과는 확인되지 않았습니다.\n");
    }
    if (!predictedLines.isEmpty()) {
      out.append("\n[운영자 예측 — 확정 결과 아님]\n");
      predictedLines.forEach(l -> out.append("- ").append(l).append('\n'));
    }
    out.append('\n');

    if (!body.isEmpty()) {
      out.append("[게시 원문]\n").append(body).append("\n\n");
    }

    List<String> cautions = new ArrayList<>();
    if (OPERATOR_PREDICTION.equals(classification) || !predictedLines.isEmpty()) {
      cautions.add("운영자의 예상·예측 내용은 공식적으로 확인된 규칙이 아니며, 확정 사실처럼 안내하지 않습니다.");
    }
    if (!RESULT_CONFIRMED.equals(classification) || confirmedLines.isEmpty()) {
      cautions.add("이 사례는 최종 결과 확인 전 단계의 진술을 포함합니다. 실제 담보 설정·재산 변동은 심사에 영향을 줄 수 있으므로 담당 기관 확인이 필요합니다.");
    } else {
      cautions.add("확정 결과는 이 사례 기준입니다. 개별 심사는 개인별 사정으로 달라질 수 있어 담당 기관 확인이 필요합니다.");
    }
    out.append("[주의해서 볼 점]\n");
    cautions.forEach(c -> out.append("- ").append(c).append('\n'));
    // 출처·게시 시점·원출처 링크는 화면의 메타 행(출처 기관/기준일/원출처)이 이미 표시한다.
    return out.toString().strip();
  }

  /** 내부 enum·머신 태그는 사용자 화면에 그대로 올리지 않는다(사람용 badge로만 변환). */
  static List<String> humanizeTags(List<String> tags) {
    List<String> human = new ArrayList<>();
    for (String tag : tags) {
      String t = tag.strip();
      String mapped = switch (t) {
        case RESULT_CONFIRMED -> "결과 확인";
        case OPERATOR_PREDICTION -> "운영자 예측";
        case "RESULT_PARTIAL" -> "결과 일부 확인";
        case "OPERATIONAL_CHANGE_SUSPECTED" -> "운영 변화 의심";
        case "OFFICIAL_CHANGE_UNCONFIRMED" -> "공식 확인 필요";
        case "CONFLICT_REVIEW" -> "운영자 검토";
        case "CASE" -> "실제 사례";
        case "CAFE_ARTICLE" -> "카페 사례";
        case "CAFE_COMMENT" -> "카페 댓글";
        case "CHAT_DISCOVERY" -> "회원 대화";
        case "REQUEST_DISCOVERY" -> "질문요청";
        default -> null;
      };
      if (mapped != null) {
        if (!human.contains(mapped)) human.add(mapped);
        continue;
      }
      if (t.contains(":") || t.contains("UNKNOWN") || t.equals("REVIEW") || t.equals("EBOOK")
          || t.matches("[A-Z_]+")) {
        continue; // board:/section:/chapter:/file:/rag_importance: 등 머신 토큰·단순 enum은 미노출
      }
      if (!human.contains(t)) human.add(t);
    }
    return human;
  }

  /** "## 헤더" 기준 섹션 분리(헤더 이름 → 본문). 첫 섹션("## 분류")은 키 없이 헤더값만 추출용. */
  private static Map<String, String> splitSections(String content) {
    Map<String, String> sections = new LinkedHashMap<>();
    String current = null;
    StringBuilder buf = new StringBuilder();
    for (String line : content.split("\n", -1)) {
      if (line.startsWith("## ")) {
        if (current != null) sections.put(current, buf.toString());
        current = line.substring(3).trim();
        buf = new StringBuilder();
      } else {
        buf.append(line).append('\n');
      }
    }
    if (current != null) sections.put(current, buf.toString());
    return sections;
  }

  private static String firstToken(String section) {
    if (section == null) return "";
    for (String line : section.split("\n")) {
      String l = line.strip();
      if (l.startsWith("-")) {
        String token = l.substring(1).strip();
        int space = token.indexOf(' ');
        return space > 0 ? token.substring(0, space) : token;
      }
    }
    return "";
  }

  private record Pair(String key, String value) {}

  private static List<Pair> pairs(String section) {
    List<Pair> pairs = new ArrayList<>();
    if (section == null) return pairs;
    for (String line : section.split("\n")) {
      String l = line.strip();
      if (!l.startsWith("-")) continue;
      int colon = l.indexOf(": ");
      if (colon < 0) continue;
      pairs.add(new Pair(l.substring(1, colon).strip(), l.substring(colon + 2).strip()));
    }
    return pairs;
  }

  private static List<String> humanPairs(List<Pair> pairs) {
    List<String> lines = new ArrayList<>();
    for (Pair p : pairs) {
      String value = humanValue(p.value);
      if (value == null) continue;
      lines.add(humanKey(p.key) + ": " + value);
    }
    return lines;
  }

  /** UNKNOWN 나열 금지: 없으면 라인 삭제, 혼재면 "미확인"으로만 표시. */
  private static String humanValue(String value) {
    if (value == null) return null;
    String v = value.strip();
    if (v.isEmpty() || v.equals("UNKNOWN")) return null;
    if (v.contains(" / ")) {
      List<String> parts = new ArrayList<>();
      for (String part : v.split(" / ")) {
        parts.add(part.strip().equals("UNKNOWN") || part.isBlank() ? "미확인" : part.strip());
      }
      String joined = String.join(" / ", parts);
      return parts.stream().allMatch(p -> p.equals("미확인")) ? null : joined;
    }
    return v;
  }

  private static String humanKey(String key) {
    return key.replace("(author followup)", "").strip();
  }
}
