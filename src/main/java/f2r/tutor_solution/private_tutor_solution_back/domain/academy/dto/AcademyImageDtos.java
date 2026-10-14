package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;
import java.util.Map;

/**
 * 학원 홈페이지 이미지 편집 DTO.
 */
public class AcademyImageDtos {

  /** 이미지 편집 요청. */
  public record EditImageRequest(
      /** 대상 이미지 자산 ID (null이면 슬롯의 최신 버전 사용). */
      Long assetId,
      /** 이미지 슬롯 (HERO_BANNER, SECTION_BG 등). */
      String slotKey,
      /** 편집 작업 목록. */
      List<EditOperation> operations,
      /** 미리보기에 즉시 적용 여부. */
      boolean applyToPreview) {}

  /** 편집 작업. */
  public record EditOperation(
      /** 작업 종류: brightness, contrast, exposure, vibrance, resize. */
      String op,
      /** 작업 파라미터. */
      Map<String, Object> params) {}

  /** 이미지 편집 응답. */
  public record EditImageResponse(
      Long assetId,
      Integer version,
      String imageUrl,
      String editDescription,
      boolean stagedForPreview) {}

  /** 이미지 자산 정보. */
  public record ImageAssetInfo(
      Long id,
      String slotKey,
      Integer version,
      String imageUrl,
      String editDescription,
      boolean stagedForPreview,
      boolean published,
      Long previousAssetId) {}
}
