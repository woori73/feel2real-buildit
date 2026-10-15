package f2r.tutor_solution.private_tutor_solution_back.domain.source.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;

/**
 * Phase 4 검증 페이지에 보여줄 데이터. analysis_result JSON을 그대로 노출 + 파일명/sourceId 메타.
 */
public record ExamReviewResponse(
    Long sourceId,
    String originalFileName,
    String processingStatus,
    String contentKind,
    ExamAnalysisResult analysisResult
) {
  public static ExamReviewResponse of(Source source, ExamAnalysisResult result) {
    return new ExamReviewResponse(
        source.getSourceId(),
        source.getOriginalFileName(),
        source.getProcessingStatus().name(),
        source.getContentKind().name(),
        result);
  }
}
