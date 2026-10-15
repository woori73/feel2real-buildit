package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;

/**
 * AI가 만든 "Academy 전체 개선안" 묶음(Phase 5A). Phase 4의 {@link AcademyAiSuggestion}과 같은
 * 철학을 따른다:
 * <ul>
 *   <li>서버는 완전히 stateless — DB에 저장하지 않는다. 생명주기는 "지금 열린 Builder 편집
 *       세션" 하나다.</li>
 *   <li>이 값을 만드는 것만으로는 {@code builderDraft}도 Production도 절대 바뀌지 않는다 —
 *       사용자가 Builder 화면에서 proposal별 "적용"을 눌러야만 기존 인라인 편집과 같은
 *       경로(builderDraft 갱신 → 발행/미리보기)로 반영된다.</li>
 *   <li>Publish는 AI가 절대 실행하지 않는다.</li>
 * </ul>
 *
 * @param diagnosis 전체 진단(2~4문장). AI 원문을 그대로 전달한다.
 * @param proposals allowlist 검증을 통과한 개별 개선안. 검증 실패한 항목은 여기 담기지 않는다.
 */
public record AcademyAiProposal(String diagnosis, List<Item> proposals) {

  /** proposal이 건드릴 수 있는 것의 종류. 이 두 가지 외에는 V1에서 지원하지 않는다. */
  public enum Type {
    /** 허용된 builderDraft key 하나의 문구를 바꾼다. */
    TEXT,
    /** 기존 섹션의 순서만 재배열한다(추가/삭제 없음). */
    SECTION_ORDER
  }

  /**
   * @param id AI가 붙인 식별자(프론트가 카드를 구분하는 용도, 서버 신뢰 대상 아님)
   * @param type {@link Type}
   * @param title 카드 제목
   * @param current "현재 →" 설명
   * @param suggestion "제안 →" 설명
   * @param target TEXT면 builderDraft key, SECTION_ORDER면 null
   * @param textValue TEXT면 적용할 새 문구, 아니면 null
   * @param sectionOrder SECTION_ORDER면 적용할 섹션 키 전체 목록, 아니면 null
   * @param safeToApply "추천안 전체 적용"에 포함해도 되는지(AI 판단 + 서버 검증 통과 시에만 true)
   */
  public record Item(
      String id,
      Type type,
      String title,
      String current,
      String suggestion,
      String target,
      String textValue,
      List<String> sectionOrder,
      boolean safeToApply) {}
}
