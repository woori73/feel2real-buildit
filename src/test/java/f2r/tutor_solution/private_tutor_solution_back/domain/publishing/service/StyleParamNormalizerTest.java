package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StyleParamNormalizerTest {

  private StyleParamNormalizer normalizer;

  @BeforeEach
  void setUp() {
    normalizer = new StyleParamNormalizer();
  }

  @Test
  @DisplayName("alignment 별칭이 align으로 변환된다")
  void alignmentAliasConverted() {
    Map<String, Object> params = Map.of(
        "name", "ko-body",
        "para", Map.of("alignment", "fullyJustified"));
    Map<String, Object> out = normalizer.normalizeParagraph(params);
    @SuppressWarnings("unchecked")
    Map<String, Object> para = (Map<String, Object>) out.get("para");
    assertEquals("fullyJustified", para.get("align"));
  }

  @Test
  @DisplayName("정수 치수가 f64로 변환된다")
  void intToDouble() {
    Map<String, Object> params = Map.of(
        "name", "ko-body",
        "para", Map.of("spaceAfter", 6));
    Map<String, Object> out = normalizer.normalizeParagraph(params);
    @SuppressWarnings("unchecked")
    Map<String, Object> para = (Map<String, Object>) out.get("para");
    assertEquals(6.0, para.get("spaceAfter"));
  }

  @Test
  @DisplayName("잘못된 align 값은 거부된다")
  void invalidAlignRejected() {
    Map<String, Object> params = Map.of(
        "name", "ko-body",
        "para", Map.of("align", "diagonal"));
    assertThrows(IllegalArgumentException.class,
        () -> normalizer.normalizeParagraph(params));
  }

  @Test
  @DisplayName("fontFamily 미지정 시 Noto Sans KR이 기본값이다")
  void defaultFontApplied() {
    Map<String, Object> params = new HashMap<>();
    params.put("name", "ko-body");
    Map<String, Object> out = normalizer.normalizeParagraph(params);
    @SuppressWarnings("unchecked")
    Map<String, Object> chars = (Map<String, Object>) out.get("chars");
    assertEquals("Noto Sans KR", chars.get("fontFamily"));
  }
}
