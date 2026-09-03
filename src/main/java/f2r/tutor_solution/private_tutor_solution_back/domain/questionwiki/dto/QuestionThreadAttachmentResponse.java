package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadAttachment;

public record QuestionThreadAttachmentResponse(
    Long attachmentId,
    String originalFileName,
    String contentType,
    long fileSize,
    String downloadUrl) {

  public static QuestionThreadAttachmentResponse from(QuestionThreadAttachment a) {
    return new QuestionThreadAttachmentResponse(
        a.getAttachmentId(),
        a.getOriginalFileName(),
        a.getContentType(),
        a.getFileSize(),
        "/api/wiki/questions/attachments/" + a.getAttachmentId() + "/file");
  }
}
