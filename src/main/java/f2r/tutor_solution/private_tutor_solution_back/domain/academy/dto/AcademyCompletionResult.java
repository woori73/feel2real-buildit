package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;

/**
 * Academy 콘텐츠 완성도 검사 결과. AI가 아니라 실제 schema에서 신뢰성 있게 확인 가능한
 * 항목만으로 만든 deterministic 결과다 — 같은 Academy 상태에서는 항상 같은 점수가 나온다.
 */
public record AcademyCompletionResult(int scorePercent, List<CompletionItem> items) {

  /** 완성도 항목 하나. {@code key}는 AI 보완이 가능한 target(builderDraft key)과 같을 수 있다. */
  public record CompletionItem(String key, String label, boolean done) {}
}
