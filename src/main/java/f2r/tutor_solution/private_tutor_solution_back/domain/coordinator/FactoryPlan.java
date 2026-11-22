package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * 제작 공장(대표 지시 2026-11-22) — 제작 요청에 붙는 「공장」 정보. 새 테이블 없이 {@code coord_jobs.plan_json}의 {@code factory}
 * 안에 담는다: 과목·시험 구분(9종), 시험 범위(여러 쪽수 구간), 기출·시험범위 자료 위치, 문항 표시(3종).
 */
final class FactoryPlan {

  private FactoryPlan() {}

  /** 과목·시험 구분 9종(화면에 보이는 순서). */
  static final Map<String, String> TRACK_LABEL = ordered("NAESIN", "내신(영어)", "CSAT", "수능", "IBT", "iBT", "SAT", "SAT",
      "TOEIC", "TOEIC", "KOREAN", "국어", "MATH", "수학", "SOCIAL", "사회", "SCIENCE", "과학");
  /** 학교 시험 구분 — 학교·학년·연도가 필요하다. 나머지(수능·공인시험)는 학교 없이 맡길 수 있다. */
  static final Set<String> SCHOOL_TRACKS = Set.of("NAESIN", "KOREAN", "MATH", "SOCIAL", "SCIENCE");
  /** 구분을 적지 않은 예전 작업은 내신(영어)으로 본다(대기열이 처음부터 내신 영어용이었다). */
  static final String DEFAULT_TRACK = "NAESIN";
  /** 문항 표시 3종. 모두 F2R이 새로 쓴 문항이고, 무엇을 근거로 썼는지만 다르다. */
  static final Map<String, String> ITEM_LABEL = ordered("PAST_ELEMENT", "기출 평가 요소 기반", "SCOPE_LINKED", "시험 범위 연계",
      "CONCEPT", "개념 연습");
  /** 화면 안내와 제작 지시서에 똑같이 넣는 원칙. */
  static final String ORIGINALITY = "기출·시험범위 자료에서는 평가 요소·사고 과정·오개념만 읽는다. 지문·보기·선지·해설은 새로 쓰고,"
      + " 다른 회사의 지문·문항·그림은 싣지 않는다.";
  static final int MAX_RANGES = 30;
  static final int MAX_PAGE = 2000;
  private static final Pattern RANGE = Pattern.compile("^(\\d{1,4})(?:\\s*[~\\-–]\\s*(\\d{1,4}))?\\s*(?:쪽|p|P)?$");

  /** 요청에 track이 있으면 공장 정보를 검증해 돌려준다. 없으면 null(예전 방식 요청). */
  static Map<String, Object> section(JsonNode a) {
    String track = text(a, "track").toUpperCase();
    if (track.isEmpty()) return null;
    if (!TRACK_LABEL.containsKey(track)) {
      throw new IllegalArgumentException("track은 " + String.join("·", TRACK_LABEL.keySet()) + " 중 하나입니다.");
    }
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("track", track);
    List<Map<String, Integer>> ranges = ranges(a == null ? null : a.get("ranges"));
    if (!ranges.isEmpty()) f.put("ranges", ranges);
    f.put("pastExamRef", clip(text(a, "pastExamRef"), 500));
    f.put("scopeRef", clip(text(a, "scopeRef"), 500));
    List<String> labels = new ArrayList<>();
    JsonNode raw = a.get("itemLabels");
    if (raw != null && raw.isArray()) {
      for (JsonNode n : raw) {
        String v = n.asText("").strip().toUpperCase();
        if (!ITEM_LABEL.containsKey(v)) throw new IllegalArgumentException("itemLabels는 PAST_ELEMENT·SCOPE_LINKED·CONCEPT 중에서 고릅니다.");
        if (!labels.contains(v)) labels.add(v);
      }
    }
    if (!labels.isEmpty()) f.put("itemLabels", labels);
    return f;
  }

  /**
   * 시험 범위 쪽수 구간. 「12~45」·「72-83쪽」·「92」 같은 글 여러 줄(배열 또는 줄바꿈·쉼표로 나눈 한 덩어리)이나 {from,to} 객체를 받는다.
   * 시작 쪽 순서로 돌려주고, 거꾸로 된 구간·겹치는 구간은 거절한다 — 시작·끝 쪽만 적으면 빠진 쪽에서 문제가 나오기 때문에 구간을 따로 적게 한다.
   */
  static List<Map<String, Integer>> ranges(JsonNode raw) {
    List<int[]> out = new ArrayList<>();
    if (raw == null || raw.isNull()) return List.of();
    if (raw.isArray()) {
      for (JsonNode n : raw) addRange(out, n);
    } else {
      for (String line : raw.asText("").split("[\\n,]")) addLine(out, line);
    }
    if (out.size() > MAX_RANGES) throw new IllegalArgumentException("시험 범위 구간은 " + MAX_RANGES + "개까지입니다.");
    out.sort(Comparator.comparingInt(r -> r[0]));
    for (int i = 1; i < out.size(); i++) {
      if (out.get(i)[0] <= out.get(i - 1)[1]) {
        throw new IllegalArgumentException("시험 범위 구간이 겹칩니다: " + one(out.get(i - 1)) + " / " + one(out.get(i)));
      }
    }
    List<Map<String, Integer>> list = new ArrayList<>();
    for (int[] r : out) {
      Map<String, Integer> m = new LinkedHashMap<>();
      m.put("from", r[0]);
      m.put("to", r[1]);
      list.add(m);
    }
    return list;
  }

  private static void addRange(List<int[]> out, JsonNode n) {
    if (n.isObject()) add(out, n.path("from").asInt(0), n.path("to").asInt(n.path("from").asInt(0)));
    else addLine(out, n.asText(""));
  }

  private static void addLine(List<int[]> out, String line) {
    String v = line == null ? "" : line.strip();
    if (v.isEmpty()) return;
    Matcher m = RANGE.matcher(v);
    if (!m.matches()) throw new IllegalArgumentException("시험 범위는 「12~45」처럼 쪽수로 적습니다: " + clip(v, 30));
    int from = Integer.parseInt(m.group(1));
    add(out, from, m.group(2) == null ? from : Integer.parseInt(m.group(2)));
  }

  private static void add(List<int[]> out, int from, int to) {
    if (from < 1 || to > MAX_PAGE || from > to) {
      throw new IllegalArgumentException("시험 범위 쪽수는 1~" + MAX_PAGE + "이고 시작이 끝보다 클 수 없습니다: " + from + "~" + to);
    }
    out.add(new int[] {from, to});
  }

  /** 「12~45쪽, 72~83쪽」. 구간이 없으면 빈 글. */
  static String rangeLabel(JsonNode ranges) {
    List<String> parts = new ArrayList<>();
    if (ranges != null && ranges.isArray()) {
      for (JsonNode r : ranges) parts.add(one(new int[] {r.path("from").asInt(), r.path("to").asInt()}));
    }
    return String.join(", ", parts);
  }

  static String track(JsonNode plan) {
    return plan.path("factory").path("track").asText(DEFAULT_TRACK);
  }

  static boolean isSchool(JsonNode plan) {
    return SCHOOL_TRACKS.contains(track(plan));
  }

  static List<String> itemLabels(JsonNode plan) {
    List<String> out = new ArrayList<>();
    for (JsonNode n : plan.path("factory").path("itemLabels")) out.add(ITEM_LABEL.getOrDefault(n.asText(), n.asText()));
    return out;
  }

  private static String one(int[] r) {
    return (r[0] == r[1] ? String.valueOf(r[0]) : r[0] + "~" + r[1]) + "쪽";
  }

  private static Map<String, String> ordered(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
    return java.util.Collections.unmodifiableMap(m);
  }

  private static String text(JsonNode a, String f) {
    return a != null && a.hasNonNull(f) ? a.get(f).asText().strip() : "";
  }

  private static String clip(String s, int max) {
    return s.length() > max ? s.substring(0, max) : s;
  }
}
