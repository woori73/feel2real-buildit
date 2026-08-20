package f2r.tutor_solution.private_tutor_solution_back.domain.consultation.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;

/**
 * 상담 피드백 자료 1건 요약 (업로드 응답용).
 * 조회는 conversation detail의 sources(sourceType=CONSULTATION 필터)를 재활용하므로 별도 list 엔드포인트는 두지 않는다.
 */
public record ConsultationMaterialResponse(
    Long sourceId,
    String fileName,
    String fileType,
    int pageCount) {

  public static ConsultationMaterialResponse of(Source source, int pageCount) {
    return new ConsultationMaterialResponse(
        source.getSourceId(),
        source.getOriginalFileName(),
        source.getFileType(),
        pageCount);
  }
}
