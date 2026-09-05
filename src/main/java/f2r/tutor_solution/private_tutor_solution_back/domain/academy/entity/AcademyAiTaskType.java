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
  PHILOSOPHY_ITEM,
  /**
   * Academy 전체를 진단하고 안전한 개선안(문구 개선 · 섹션 순서 변경)을 구조화된 proposal
   * 묶음으로 제안한다(Phase 5A). 단일 target을 다듬는 위 두 Task와 달리
   * {@code AcademyAiProposalService}가 JSON 응답을 파싱하고 proposal별 allowlist를 검증한다.
   */
  ACADEMY_COMPOSE
}
