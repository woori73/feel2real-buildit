package f2r.tutor_solution.private_tutor_solution_back.domain.source.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;

import java.time.LocalDateTime;

/**
 * 학생 대시보드 "검증 대기" 카드 항목 — Source 중 PENDING_REVIEW + EXAM_GRADED 상태인 항목 요약.
 *
 * <p>학생이 시험지 N장을 한 묶음으로 올린 경우 본 응답은 source N개로 반환되며, 프론트가
 * {@code examId} 기준으로 묶어 1개 카드로 표시한다 (conversation.js의 groupSources 패턴 동일).
 * 검증 페이지 진입 시 묶음의 첫 sourceId만 사용해도 충분 — 모든 source는 같은 analysis_result를 공유한다.
 */
public record PendingReviewSourceResponse(
    Long sourceId,
    String originalFileName,
    Long examId,
    String examDisplayName,
    LocalDateTime createdAt
) {
  public static PendingReviewSourceResponse from(Source source) {
    Exam exam = source.getExam();
    return new PendingReviewSourceResponse(
        source.getSourceId(),
        source.getOriginalFileName(),
        exam != null ? exam.getExamId() : null,
        exam != null ? exam.getDisplayName() : null,
        source.getCreatedAt());
  }
}
