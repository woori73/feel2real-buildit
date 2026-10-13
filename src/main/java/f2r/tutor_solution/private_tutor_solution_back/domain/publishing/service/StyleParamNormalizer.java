package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 스타일 파라미터 정규화.
 *
 * <p>DesignCraft CLI 0.6.0의 para/char 속성은 InDesign식 명명을 사용한다 (실측):
 * <ul>
 *   <li>{@code align} enum 9종: left/center/right/leftJustified/centerJustified/
 *       rightJustified/fullyJustified/towardsSpine/awayFromSpine
 *   <li>치수는 f64 (예: {@code spaceAfter: 6.0})
 *   <li>{@code alignment}·{@code justification}·{@code leading}은 invalid parameter 오류
 * </ul>
 *
 * <p>Qoder 프론트에서 오는 일반 명칭을 CLI 명칭으로 변환한다.
 */
@Component
public class StyleParamNormalizer {

  /** 허용된 align 값. */
  private static final Set<String> ALIGN_VALUES = Set.of(
      "left", "center", "right",
      "leftJustified", "centerJustified", "rightJustified", "fullyJustified",
      "towardsSpine", "awayFromSpine"
  );

  /** 프론트 명칭 → CLI 명칭. */
  private static final Map<String, String> PARA_ALIASES = Map.of(
      "alignment", "align",
      "justification", "align",
      "leading", "lineHeight",
      "firstLineIndent", "firstLineIndent",
      "spaceAfter", "spaceAfter",
      "spaceBefore", "spaceBefore"
  );

  /**
   * 문단 스타일 생성 파라미터를 정규화한다.
   *
   * @param params 원본 파라미터 (name, chars, para)
   * @return CLI 호환 파라미터
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> normalizeParagraph(Map<String, Object> params) {
    if (params == null) {
      return Map.of();
    }
    Map<String, Object> out = new HashMap<>(params);
    Object paraObj = params.get("para");
    if (paraObj instanceof Map<?, ?> paraMap) {
      Map<String, Object> para = new HashMap<>();
      for (Map.Entry<?, ?> e : paraMap.entrySet()) {
        String key = String.valueOf(e.getKey());
        String mapped = PARA_ALIASES.getOrDefault(key, key);
        Object value = e.getValue();
        if ("align".equals(mapped) && value instanceof String s) {
          if (!ALIGN_VALUES.contains(s)) {
            throw new IllegalArgumentException("지원하지 않는 align 값: " + s);
          }
        }
        // 치수는 f64로 통일
        if (value instanceof Integer i
            && ("spaceAfter".equals(mapped) || "spaceBefore".equals(mapped)
                || "firstLineIndent".equals(mapped) || "lineHeight".equals(mapped))) {
          value = i.doubleValue();
        }
        para.put(mapped, value);
      }
      out.put("para", para);
    }
    // 한글 폰트 강제: chars.fontFamily 미지정 시 Noto Sans KR
    Object charsObj = out.get("chars");
    if (charsObj instanceof Map<?, ?> charsMap) {
      Map<String, Object> chars = new HashMap<>((Map<String, Object>) charsMap);
      chars.putIfAbsent("fontFamily", "Noto Sans KR");
      out.put("chars", chars);
    } else {
      out.put("chars", Map.of("fontFamily", "Noto Sans KR"));
    }
    return out;
  }
}
