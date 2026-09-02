package f2r.tutor_solution.private_tutor_solution_back.domain.community.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPostAttachment;

/** 게시글 첨부 1건. */
public record CommunityAttachmentResponse(
    Long attachmentId,
    String originalFileName,
    String contentType,
    long fileSize,
    String downloadUrl) {

  public static CommunityAttachmentResponse of(CommunityPostAttachment a) {
    return new CommunityAttachmentResponse(
        a.getAttachmentId(),
        a.getOriginalFileName(),
        a.getContentType(),
        a.getFileSize(),
        "/api/open-community/attachments/" + a.getAttachmentId() + "/file");
  }
}
