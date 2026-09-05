package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

/**
 * Academy Builder AI가 수행할 수 있는 명시적 작업 목록. 자유형 채팅이 아니라 Task 단위로
 * 제한한다 — Task마다 허용된 target(builderDraft key)이 정해져 있고, 그 밖의 target은
 * {@link f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiSuggestionService}가
 * 거부한다.
 */
public enum AcademyAiTaskType {
  /** 원장 소개문(builderDraft.principalDesc)을 다듬는다. */
  PRINCIPAL_INTRO,
  /** 학습 철학 항목(builderDraft.philosophyItem1~4) 중 하나를 다듬는다. */
  PHILOSOPHY_ITEM
}
