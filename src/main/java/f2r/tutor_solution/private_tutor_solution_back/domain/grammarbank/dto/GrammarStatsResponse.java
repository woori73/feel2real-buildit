package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.util.List;

/** 문법 문제은행 대시보드 통계. */
public record GrammarStatsResponse(
    long total,
    long needsReview,
    long approved,
    long rejected,
    List<LevelCount> bySchoolLevel,
    List<TagCount> byGrammarTag
) {
  public record LevelCount(String level, String labelKo, long count) {
  }

  public record TagCount(Long tagId, String nameKo, long count) {
  }
}
