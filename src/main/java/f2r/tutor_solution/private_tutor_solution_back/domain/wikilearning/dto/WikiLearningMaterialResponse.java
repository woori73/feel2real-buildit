package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterial;
import java.time.LocalDateTime;
import java.util.Set;

/** 모의고사 학습 자료 항목(목록/상세 공용). */
public record WikiLearningMaterialResponse(
    Long wikiLearningMaterialId,
    String dataType,
    String dataTypeLabel,
    Integer grade,
    String title,
    String originalFileName,
    Long fileSize,
    String uploaderName,
    LocalDateTime createdAt,
    boolean previewable,
    String previewUrl,
    String downloadUrl
) {
  public static final String PDF_CONTENT_TYPE = "application/pdf";
  public static final String HTML_CONTENT_TYPE = "text/html";
  private static final Set<String> PREVIEWABLE_CONTENT_TYPES = Set.of(PDF_CONTENT_TYPE, HTML_CONTENT_TYPE);

  public static boolean isPreviewableContentType(String contentType) {
    return PREVIEWABLE_CONTENT_TYPES.contains(contentType);
  }

  public static WikiLearningMaterialResponse of(WikiLearningMaterial m) {
    Long id = m.getWikiLearningMaterialId();
    boolean previewable = isPreviewableContentType(m.getContentType());
    return new WikiLearningMaterialResponse(
        id, m.getDataType().name(), m.getDataType().label(), m.getGrade(), m.getTitle(),
        m.getOriginalFileName(), m.getFileSize(), m.getUploaderName(), m.getCreatedAt(),
        previewable,
        previewable ? "/api/wiki/learning-materials/" + id + "/preview" : null,
        "/api/wiki/learning-materials/" + id + "/download");
  }
}
