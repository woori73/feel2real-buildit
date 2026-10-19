package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.SchoolLevel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Existing JSON column: immutable imported snapshot plus explicit classification history. */
public final class GrammarReviewMetadata {
  private static final ObjectMapper JSON = new ObjectMapper();
  private GrammarReviewMetadata() {}

  public static Map<String, Object> root(String json) {
    if (json == null || json.isBlank()) return new LinkedHashMap<>();
    try { return JSON.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {}); }
    catch (Exception e) { throw new IllegalArgumentException("검토 메타데이터 JSON을 읽을 수 없습니다.", e); }
  }

  public static Map<String, Object> snapshot(String json) {
    Object value = root(json).get("review_metadata");
    if (!(value instanceof Map<?, ?> map)) return new LinkedHashMap<>();
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((k, v) -> result.put(String.valueOf(k), v));
    return result;
  }

  public static SchoolLevel recommendedGrade(String json) {
    Object value = root(json).get("recommended_grade");
    if (value == null) value = snapshot(json).get("recommended_grade");
    if (value == null) return null;
    String raw = String.valueOf(value);
    for (SchoolLevel level : SchoolLevel.values()) {
      if (level.name().equals(raw) || level.labelKo().equals(raw)) return level;
    }
    throw new IllegalArgumentException("권장 학년 값이 올바르지 않습니다: " + raw);
  }

  public static String imported(Map<String, Object> inferred, Map<String, Object> snapshot,
      String externalRef) {
    Map<String, Object> root = inferred == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inferred);
    if (snapshot != null) {
      if (externalRef == null || !externalRef.equals(snapshot.get("external_id")))
        throw new IllegalArgumentException("external_ref와 검토본 external_id가 일치해야 합니다.");
      root.put("review_metadata", snapshot);
      String text = write(root);
      SchoolLevel level = recommendedGrade(text);
      if (level != null && (level == SchoolLevel.COMMON || level == SchoolLevel.UNKNOWN))
        throw new IllegalArgumentException("권장 학년은 중1~고3 또는 null이어야 합니다.");
      root.put("recommended_grade", level == null ? null : level.name());
      root.put("metadata_schema", "grammar-review/1");
    }
    return root.isEmpty() ? null : write(root);
  }

  public static String updateGrade(String json, SchoolLevel grade, Long actorId) {
    if (grade == SchoolLevel.COMMON) throw new IllegalArgumentException("권장 학년은 중1~고3 또는 미확정입니다.");
    Map<String, Object> root = root(json);
    List<Object> history = root.get("review_history") instanceof List<?> list
        ? new ArrayList<>(list) : new ArrayList<>();
    Map<String, Object> event = new LinkedHashMap<>();
    SchoolLevel previous = recommendedGrade(json);
    event.put("field", "recommended_grade");
    event.put("before", previous == null ? null : previous.name());
    event.put("after", grade == null || grade == SchoolLevel.UNKNOWN ? null : grade.name());
    event.put("actorUserId", actorId);
    event.put("at", Instant.now().toString());
    history.add(event);
    root.put("review_history", history);
    // Imported snapshot is never edited. UNKNOWN is an explicit clear, not a missing value.
    root.put("recommended_grade", grade == null ? SchoolLevel.UNKNOWN.name() : grade.name());
    return write(root);
  }

  public static String write(Object value) {
    try { return JSON.writeValueAsString(value); }
    catch (Exception e) { throw new IllegalArgumentException("검토 메타데이터 직렬화 실패", e); }
  }
}
