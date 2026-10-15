package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadPost;

import java.time.LocalDateTime;
import java.util.List;

/**
 * THREAD 글 1건(+ 1단계 답글). AI 가 category / dataKind 로 필터할 수 있도록 두 축을 모두 노출한다.
 */
public record QuestionThreadPostResponse(
    Long postId,
    Long parentPostId,
    String category,
    String categoryLabel,
    String dataKind,
    String dataKindLabel,
    String title,
    String content,
    Long authorUserId,
    String authorName,
    String authorRole,
    String participantType,
    boolean mine,
    boolean canManage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<QuestionThreadAttachmentResponse> attachments,
    QuestionThreadVariantResponse variantDetail,
    QuestionThreadExamEvidenceResponse examEvidence,
    List<QuestionThreadPostResponse> replies) {

  public static QuestionThreadPostResponse from(
      QuestionThreadPost p,
      Long currentUserId,
      boolean canManage,
      List<QuestionThreadAttachmentResponse> attachments,
      QuestionThreadVariantResponse variantDetail,
      QuestionThreadExamEvidenceResponse examEvidence,
      List<QuestionThreadPostResponse> replies) {
    Long authorUserId = p.getAuthor().getUserId();
    return new QuestionThreadPostResponse(
        p.getPostId(),
        p.getParentPost() == null ? null : p.getParentPost().getPostId(),
        p.getCategory().name(),
        p.getCategory().label(),
        p.getDataKind().name(),
        p.getDataKind().label(),
        p.getTitle(),
        p.getContent(),
        authorUserId,
        p.getAuthor().getDisplayName(),
        p.getAuthorRole() == null ? null : p.getAuthorRole().name(),
        ParticipantType.label(p.getAuthorRole()),
        currentUserId != null && authorUserId.equals(currentUserId),
        canManage,
        p.getCreatedAt(),
        p.getUpdatedAt(),
        attachments,
        variantDetail,
        examEvidence,
        replies);
  }
}
