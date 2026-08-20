package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 학습자료 1건의 학생·강사·Admin 공통 응답 DTO.
 *
 * <p>학생 대시보드/대화별 목록/단건 조회에서 모두 같은 모양을 사용.
 * 학생이 풀이를 업로드하면 {@code answers}가 채워진다.
 *
 * <p>PDF 없이 부여된 숙제(V13)는 {@code problemSourceId}/{@code problemFileName}이 {@code null}이고
 * {@code hasProblemFile=false}다. 프론트는 이 플래그로 문제 PDF 뷰어/다운로드 버튼을 분기한다.
 */
public record LearningMaterialResponse(
    Long materialId,
    Long conversationId,
    Long problemSourceId,
    String problemFileName,
    boolean hasProblemFile,
    String title,
    String description,
    LearningMaterialStatus status,
    LocalDateTime assignedAt,
    LocalDateTime completedAt,
    Long assignedByUserId,
    String assignedByName,
    Long studentId,
    String studentName,
    boolean reviewed,
    LocalDateTime reviewedAt,
    String reviewedByName,
    List<MaterialAnswerInfo> answers,
    List<MaterialFeedbackInfo> feedbacks
) {

  /**
   * 학생이 업로드한 풀이 결과물 1건.
   *
   * @param answerId         learning_material_answers.answer_id
   * @param sourceId         실제 파일 source.id (preview/download 시 사용)
   * @param originalFileName 학생이 올린 원본 파일명
   * @param orderNum         업로드 순서 (1부터)
   */
  public record MaterialAnswerInfo(
      Long answerId,
      Long sourceId,
      String originalFileName,
      Integer orderNum
  ) {}

  /**
   * 강사/Admin이 등록한 피드백 1건. 첨부 없이 등록된 피드백이면 sourceId/originalFileName은 null.
   *
   * @param feedbackId       learning_material_feedbacks.feedback_id
   * @param sourceId         실제 파일 source.id (preview 시 사용, 첨부 없으면 null)
   * @param originalFileName 강사가 올린 원본 파일명 (첨부 없으면 null)
   * @param feedbackText     학생에게 표시할 피드백 설명/메모
   * @param orderNum         등록 순서 (1부터)
   */
  public record MaterialFeedbackInfo(
      Long feedbackId,
      Long sourceId,
      String originalFileName,
      String feedbackText,
      Integer orderNum
  ) {}
}
