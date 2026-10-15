package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import java.util.List;

/**
 * 한 문항(Question Wiki)에 연결된 전체 Context 를 한 번에 담는다.
 *
 * <p>문항 상세 화면과 (향후) AI 종합 분석이 같은 payload 를 쓴다. 데이터는 성격별로 나뉘어 있다:
 * <ul>
 *   <li>{@code threads} — DISCUSSION 최상위 글 + 1단계 답글(replies 중첩)
 *   <li>{@code variants} — 사용자 제작 변형/예상 문제(GENERATED_PRACTICE)
 *   <li>{@code examEvidence} — 실제 학교 기출 증거(EXAM_EVIDENCE)
 *   <li>{@code aiSummary} — 최신 승인된 AI 종합(없으면 null)
 * </ul>
 */
public record QuestionWikiContextResponse(
    Question question,
    List<QuestionWikiMaterialLinkResponse> materials,
    List<QuestionThreadPostResponse> threads,
    List<QuestionThreadPostResponse> variants,
    List<QuestionThreadPostResponse> examEvidence,
    QuestionAiSummaryResponse aiSummary,
    Counts counts) {

  public record Question(
      Long questionWikiId,
      String subject,
      int grade,
      int examYear,
      String examLabel,
      int questionNumber,
      String passageGroupKey,
      List<Integer> passageGroupQuestionNumbers,
      String title,
      String displayContext) {}

  public record Counts(
      long threadCount,
      long discussionCount,
      long variantCount,
      long examEvidenceCount) {}
}
