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
  ACADEMY_COMPOSE,

  /**
   * AI Guided Academy Builder(Phase 5B) — Guide가 진행하는 한 단계에서, 사용자가 방금 답한
   * 내용을 바탕으로 해당 영역의 문구 후보 2~3개를 제안한다. {@code PRINCIPAL_INTRO}/
   * {@code PHILOSOPHY_ITEM}과 같은 "단일 target 다듬기" 계약을 공유하되, 응답이 <b>후보 배열</b>
   * 이라는 점만 다르다({@code AcademyAiGuideService}가 JSON 배열을 파싱하고 target allowlist·
   * 길이를 검증한다). 이 Task도 절대 Academy/builderDraft를 수정하지 않는다 — "적용"은 기존
   * 인라인 편집과 같은 경로로만 이뤄진다.
   */
  GUIDE_TEXT
}
