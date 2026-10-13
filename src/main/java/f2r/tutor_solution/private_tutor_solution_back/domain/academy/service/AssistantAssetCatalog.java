package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Op;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BuildIt2 디자인 자산을 대화 에이전트가 고를 수 있게 한 정적 카탈로그(DB·외부 호출 없음).
 *
 * <p>자산마다 "S1 연산 목록(ops)"으로 풀리는 값을 갖는다 — 에이전트는 코드를 새로 만들지 않고 검증된 값 묶음을 고른다.
 * ACTIVE만 적용·추천되고, 연결하지 못한 자산은 NOT_CONNECTED로 이유와 함께 남겨 둔다.
 * 원본은 {@code design-assets/catalog.json}. 순수 로직이라 컨테이너 없이 테스트한다.
 */
final class AssistantAssetCatalog {

  static final String RESOURCE = "design-assets/catalog.json";
  private static final int KEYWORD_SCORE = 3;
  private static final int MOOD_SCORE = 2;
  private static final int TYPE_SCORE = 1;

  private AssistantAssetCatalog() {}

  record Asset(
      String id, String kind, String status, String label, String summary,
      List<String> keywords, List<String> moodWords, List<String> academyTypes,
      List<Op> ops, String note) {
    boolean active() {
      return "ACTIVE".equals(status);
    }

    boolean recipe() {
      return "recipe".equals(kind);
    }

    /** 레시피가 고르는 디자인 프리셋(첫 setPreset). 없으면 null. */
    String preset() {
      return ops.stream().filter(o -> "setPreset".equals(o.op())).map(Op::value).findFirst().orElse(null);
    }
  }

  record Hit(Asset asset, int score) {}

  private static final Map<String, Asset> ASSETS = load();

  private static Map<String, Asset> load() {
    try (InputStream in = AssistantAssetCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
      if (in == null) throw new IllegalStateException("디자인 자산 카탈로그가 없습니다: " + RESOURCE);
      return parse(new ObjectMapper().readTree(in));
    } catch (IOException e) {
      throw new IllegalStateException("디자인 자산 카탈로그를 읽을 수 없습니다.", e);
    }
  }

  static Map<String, Asset> parse(JsonNode root) {
    Map<String, Asset> map = new LinkedHashMap<>();
    for (JsonNode n : root.path("assets")) {
      List<Op> ops = new ArrayList<>();
      for (JsonNode o : n.path("ops")) {
        ops.add(new Op(text(o, "op"), text(o, "key"), text(o, "value"), text(o, "id"), text(o, "before"), text(o, "after")));
      }
      Asset a = new Asset(
          text(n, "id"), text(n, "kind"), text(n, "status"), text(n, "label"), text(n, "summary"),
          strings(n.path("keywords")), strings(n.path("moodWords")), strings(n.path("academyTypes")),
          List.copyOf(ops), text(n, "note"));
      if (map.put(a.id(), a) != null) throw new IllegalStateException("중복된 디자인 자산 id: " + a.id());
    }
    return map;
  }

  static Asset find(String id) {
    return id == null ? null : ASSETS.get(id.trim());
  }

  static List<Asset> all() {
    return List.copyOf(ASSETS.values());
  }

  static List<Asset> active() {
    return ASSETS.values().stream().filter(Asset::active).toList();
  }

  /** id → 사람이 읽는 이름(변경 요약용). 모르는 id는 그대로. */
  static String labelOf(String id) {
    Asset a = find(id);
    return a == null ? id : a.label();
  }

  /**
   * 요청 문장과 맞는 ACTIVE 자산을 점수순으로. 강한 키워드 3점, 분위기 단어 2점, 학원 유형 1점.
   * 점수 0은 뺀다. 동점은 {@code tieSeed}(보통 학원 이름) 해시로 정해, 같은 입력은 항상 같은 결과이고
   * 학원마다 다르게 갈린다.
   */
  static List<Hit> search(String text, String tieSeed, String kind) {
    String t = normalize(text);
    if (t.isEmpty()) return List.of();
    int seed = tieSeed == null ? 0 : tieSeed.hashCode();
    List<Hit> hits = new ArrayList<>();
    for (Asset a : ASSETS.values()) {
      if (!a.active() || (kind != null && !kind.equals(a.kind()))) continue;
      int score = count(t, a.keywords()) * KEYWORD_SCORE + count(t, a.moodWords()) * MOOD_SCORE
          + count(t, a.academyTypes()) * TYPE_SCORE;
      if (score > 0) hits.add(new Hit(a, score));
    }
    hits.sort(Comparator.comparingInt(Hit::score).reversed()
        .thenComparingInt(h -> Math.floorMod(h.asset().id().hashCode() ^ seed, 9973)));
    return hits;
  }

  /** 요청에 강한 키워드가 직접 들어 있는 자산(규칙 해석기용). 없으면 null. */
  static Asset byKeyword(String text) {
    String t = normalize(text);
    Asset best = null;
    int bestCount = 0;
    for (Asset a : ASSETS.values()) {
      if (!a.active()) continue;
      int c = count(t, a.keywords());
      if (c > bestCount) {
        best = a;
        bestCount = c;
      }
    }
    return best;
  }

  /**
   * 첫 생성용 레시피 선택. 요청에 분위기·키워드가 분명히 나오면(점수 {@value #MOOD_SCORE} 이상) 그 레시피,
   * 아니면 이미 정해진 디자인 프리셋과 같은 프리셋의 레시피(동점은 학원 이름 해시), 그것도 없으면 null.
   * 이미 정한 프리셋(샘플 선택)을 뒤집는 레시피는 고르지 않는다.
   */
  static Asset recommendRecipe(String request, String academyName, String chosenPreset, boolean presetLocked) {
    List<Hit> hits = search(request, academyName, "recipe");
    if (!hits.isEmpty() && hits.get(0).score() >= MOOD_SCORE) {
      Asset top = hits.get(0).asset();
      if (!presetLocked || top.preset() == null || top.preset().equals(chosenPreset)) return top;
    }
    int seed = academyName == null ? 0 : academyName.hashCode();
    return ASSETS.values().stream()
        .filter(a -> a.active() && a.recipe() && a.preset() != null && a.preset().equals(chosenPreset))
        .min(Comparator.comparingInt(a -> Math.floorMod(a.id().hashCode() ^ seed, 9973)))
        .orElse(null);
  }

  /** 프롬프트용 목록 — 요청과 관련 높은 자산이 앞에 오도록 정렬한다. */
  static List<Asset> rankedActive(String text, String tieSeed) {
    List<Asset> ranked = new ArrayList<>();
    search(text, tieSeed, null).forEach(h -> ranked.add(h.asset()));
    active().forEach(a -> {
      if (!ranked.contains(a)) ranked.add(a);
    });
    return ranked;
  }

  static String normalize(String s) {
    return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
  }

  private static int count(String t, List<String> words) {
    int n = 0;
    for (String w : words) if (!w.isEmpty() && t.contains(normalize(w))) n++;
    return n;
  }

  private static String text(JsonNode n, String f) {
    JsonNode v = n.get(f);
    return v == null || v.isNull() ? null : v.asText();
  }

  private static List<String> strings(JsonNode arr) {
    List<String> out = new ArrayList<>();
    for (JsonNode x : arr) out.add(x.asText());
    return List.copyOf(out);
  }
}
