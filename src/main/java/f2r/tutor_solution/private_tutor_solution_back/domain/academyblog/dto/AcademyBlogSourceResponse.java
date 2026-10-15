package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogSourceType;

public record AcademyBlogSourceResponse(
    Long sourceId,
    AcademyBlogSourceType sourceType,
    String originalFilename,
    String fileUrl,
    String contentType,
    long fileSize,
    int sortOrder
) {
  public static AcademyBlogSourceResponse from(AcademyBlogSource source) {
    return new AcademyBlogSourceResponse(
        source.getSourceId(), source.getSourceType(), source.getOriginalFilename(),
        source.getFileUrl(), source.getContentType(), source.getFileSize(), source.getSortOrder());
  }
}
