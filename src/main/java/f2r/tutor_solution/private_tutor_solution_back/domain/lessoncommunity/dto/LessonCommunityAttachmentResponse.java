package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityAttachment;

public record LessonCommunityAttachmentResponse(
    Long attachmentId,
    String originalFileName,
    String contentType,
    long fileSize,
    String fileUrl
) {
  public static LessonCommunityAttachmentResponse from(LessonCommunityAttachment a) {
    return new LessonCommunityAttachmentResponse(
        a.getAttachmentId(),
        a.getOriginalFileName(),
        a.getContentType(),
        a.getFileSize(),
        "/api/community/attachments/" + a.getAttachmentId() + "/file");
  }
}
