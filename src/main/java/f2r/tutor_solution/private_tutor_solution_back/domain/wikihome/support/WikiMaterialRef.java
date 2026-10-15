package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.support;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;

/**
 * 공개 Wiki 메인에서 자료 카테고리 → 사람이 읽는 라벨 / 클릭 시 이동 경로 매핑.
 * 원본·분석은 전체보기 목록으로, 학습 자료는 상세/미리보기 화면으로 보낸다(기존 라우팅과 일치).
 */
public final class WikiMaterialRef {

  private WikiMaterialRef() {}

  public static String label(WikiMaterialCategory category) {
    return switch (category) {
      case ORIGINAL -> "모의고사 원본";
      case ANALYSIS -> "분석 자료";
      case LEARNING -> "모의고사 학습 자료";
    };
  }

  public static String label(String category) {
    return label(WikiMaterialCategory.valueOf(category));
  }

  public static String href(WikiMaterialCategory category, Long materialId) {
    return switch (category) {
      case ORIGINAL -> "/wiki/originals";
      case ANALYSIS -> "/wiki/analysis";
      case LEARNING -> "/wiki/learning/" + materialId;
    };
  }

  public static String href(String category, Long materialId) {
    return href(WikiMaterialCategory.valueOf(category), materialId);
  }
}
