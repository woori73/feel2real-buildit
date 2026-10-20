package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.support;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWiki;

/**
 * 문항 AI 문제제작 제공 정책.
 *
 * <p>2026년 9월 모의고사는 아카이브 전용 회차라 그 문항들에는 AI 문제제작을 제공하지 않는다
 * (2026-10-20 운영 결정). 판정은 이 클래스 한 곳에서만 하며, 다른 회차의 문항과 다른 서비스의
 * AI 기능에는 영향을 주지 않는다. 이미 생성·저장된 변형문제 데이터는 이 정책으로 삭제하지 않는다.
 */
public final class QuestionAiGenerationPolicy {

  /** AI 문제제작을 제공하지 않는 회차: 2026년 9월 모의고사. */
  public static final int DISABLED_EXAM_YEAR = 2026;
  public static final String DISABLED_EXAM_MONTH_LABEL = "9월";

  private QuestionAiGenerationPolicy() {
  }

  public static boolean isAiGenerationDisabled(QuestionWiki question) {
    return question != null
        && isAiGenerationDisabled(question.getExamYear(), question.getExamLabel());
  }

  public static boolean isAiGenerationDisabled(int examYear, String examLabel) {
    return examYear == DISABLED_EXAM_YEAR
        && examLabel != null
        && examLabel.contains(DISABLED_EXAM_MONTH_LABEL);
  }
}
