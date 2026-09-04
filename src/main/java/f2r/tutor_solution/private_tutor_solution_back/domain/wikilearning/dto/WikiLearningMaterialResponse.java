package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterial;
import java.time.LocalDateTime;

/**
 * 모의고사 학습 자료 목록 항목. 비회원도 받는 응답이라 storage_key 등 내부 값은 담지 않는다.
 * 실제 파일은 {@code downloadUrl}(회원 전용 엔드포인트)로만 접근한다.
 */
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
    String downloadUrl
) {
  public static WikiLearningMaterialResponse of(WikiLearningMaterial m) {
    Long id = m.getWikiLearningMaterialId();
    return new WikiLearningMaterialResponse(
        id,
        m.getDataType().name(),
        m.getDataType().label(),
        m.getGrade(),
        m.getTitle(),
        m.getOriginalFileName(),
        m.getFileSize(),
        m.getUploaderName(),
        m.getCreatedAt(),
        "/api/wiki/learning-materials/" + id + "/download"
    );
  }
}
