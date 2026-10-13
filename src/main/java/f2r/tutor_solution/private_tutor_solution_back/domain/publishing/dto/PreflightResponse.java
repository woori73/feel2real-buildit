package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto;

import java.util.List;

/**
 * preflight 검수 결과 (Qoder API_INTEGRATION.md §2 스키마 확정).
 *
 * <p>프론트는 각 항목에서 {@code page}(숫자)와 {@code detail}(표시 문자열)을 읽는다.
 * {@code missingGlyphs}는 {@code glyph}, {@code lowPpi}는 {@code ppi}를 추가로 사용한다.
 */
public record PreflightResponse(
    boolean passed,
    List<Finding> overset,
    List<MissingGlyphFinding> missingGlyphs,
    List<LowPpiFinding> lowPpi
) {

  /** 일반 finding (overset 등). */
  public record Finding(
      Integer page,
      String detail
  ) {
  }

  /** 글리프 누락 finding. */
  public record MissingGlyphFinding(
      Integer page,
      String detail,
      String glyph
  ) {
  }

  /** 저해상도 이미지 finding. */
  public record LowPpiFinding(
      Integer page,
      String detail,
      Integer ppi
  ) {
  }

  public static PreflightResponse empty() {
    return new PreflightResponse(true, List.of(), List.of(), List.of());
  }
}
