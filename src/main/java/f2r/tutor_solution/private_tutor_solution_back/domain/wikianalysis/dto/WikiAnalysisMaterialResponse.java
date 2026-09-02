package f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity.WikiAnalysisMaterial;
import java.time.LocalDateTime;

public record WikiAnalysisMaterialResponse(
    Long wikiAnalysisMaterialId,
    String title,
    String description,
    String schoolName,
    Integer grade,
    Integer year,
    String semester,
    String examType,
    String subject,
    String contentType,
    String originalFileName,
    Long fileSize,
    boolean isVisible,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String downloadUrl
) {
  public static WikiAnalysisMaterialResponse of(WikiAnalysisMaterial material) {
    Long id = material.getWikiAnalysisMaterialId();
    return new WikiAnalysisMaterialResponse(
        id,
        material.getTitle(),
        material.getDescription(),
        material.getSchoolName(),
        material.getGrade(),
        material.getYear(),
        material.getSemester(),
        material.getExamType(),
        material.getSubject(),
        material.getContentType(),
        material.getOriginalFileName(),
        material.getFileSize(),
        material.isVisible(),
        material.getCreatedAt(),
        material.getUpdatedAt(),
        "/api/wiki/analysis-materials/" + id + "/download"
    );
  }
}
