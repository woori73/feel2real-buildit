package f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto;

import java.util.List;

/**
 * 한 대화(conversation)에 첨부된 시험지(들)의 카테고리별 정답률 — radar chart 데이터.
 *
 * <p>카테고리는 시드된 5종(vocabulary/grammar/memorization/reading/other)을 빠짐없이 반환한다.
 * 답안이 없는 카테고리는 total=0, correct=0, accuracyPercent=0으로 채워서 radar 축 5개가 항상 그려지게 한다.
 */
public record WeaknessRadarResponse(List<CategoryStat> categories) {

  public record CategoryStat(
      String categoryNameEn,
      String categoryNameKo,
      int total,
      int correct,
      double accuracyPercent) {
  }
}
