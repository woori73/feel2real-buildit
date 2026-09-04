package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterial;
import java.time.LocalDateTime;

/**
 * 모의고사 학습 자료 항목(목록/상세 공용). 비회원도 받는 응답이라 storage_key 등 내부 값은 담지 않는다.
 *
 * <p>클릭 흐름: 목록 → 상세({@code /wiki/learning/{id}}) → [다운로드]. 카드 클릭이 바로 다운로드를 시작하지 않는다.
 * <ul>
 *   <li>{@code previewable} — Wiki 안에서 안전하게 인라인 미리보기 가능한 형식인가(현재 PDF 만).
 *       미리보기는 비회원도 볼 수 있다({@code previewUrl}, permitAll).
 *   <li>{@code downloadUrl} — 실제 파일. 회원 전용 엔드포인트(익명 401). 프런트 버튼만이 아니라 서버가 막는다.
 * </ul>
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
    boolean previewable,
    String previewUrl,
    String downloadUrl
) {
  /** 현재 Wiki 안에서 안전하게 인라인 미리보기 가능한 형식 = PDF 뿐. (회원이 HTML/JS 를 올릴 수 없음) */
  public static final String PREVIEWABLE_CONTENT_TYPE = "application/pdf";

  public static WikiLearningMaterialResponse of(WikiLearningMaterial m) {
    Long id = m.getWikiLearningMaterialId();
    boolean previewable = PREVIEWABLE_CONTENT_TYPE.equals(m.getContentType());
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
        previewable,
        previewable ? "/api/wiki/learning-materials/" + id + "/preview" : null,
        "/api/wiki/learning-materials/" + id + "/download"
    );
  }
}
