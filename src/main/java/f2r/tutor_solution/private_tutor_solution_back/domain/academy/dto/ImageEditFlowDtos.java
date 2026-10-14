package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;
import java.util.Map;

/**
 * PhotoCraft 이미지 편집 플로우 DTO.
 *
 * <p>AI 채팅 → 이미지 편집 → 미리보기 → 승인 → 홈페이지 반영 흐름을 지원한다.
 * #662의 AcademyImageDtos를 확장하는 플로우 전용 DTO.
 */
public class ImageEditFlowDtos {

  /** 채팅 메시지에서 이미지 편집 요청을 감지한 결과. */
  public record ChatImageIntentResponse(
      /** 이미지 편집 의도 감지 여부. */
      boolean imageEditDetected,
      /** 감지된 슬롯 (HERO_BANNER, SECTION_BG 등). */
      String slotKey,
      /** 추천 편집 작업 설명 (사용자 확인용). */
      String suggestedEditDescription,
      /** 편집 작업 목록. */
      List<Map<String, Object>> operations,
      /** 기존 이미지가 있는지 여부. */
      boolean hasExistingImage,
      /** 기존 이미지가 없을 때 안내 메시지. */
      String noImageGuidance) {}

  /** 미리보기 데이터 (원본 vs 수정본 비교). */
  public record ImagePreviewData(
      /** 이미지 자산 ID. */
      Long assetId,
      /** 슬롯. */
      String slotKey,
      /** 버전. */
      Integer version,
      /** 원본 이미지 URL. */
      String originalImageUrl,
      /** 수정본 이미지 URL. */
      String editedImageUrl,
      /** 편집 설명. */
      String editDescription,
      /** 미리보기 스테이징 여부. */
      boolean stagedForPreview) {}

  /** 승인 요청. */
  public record ApproveImageRequest(
      /** 이미지 자산 ID. */
      Long assetId,
      /** 승인 여부 (true=승인, false=거부). */
      boolean approved,
      /** 거부/재수정 요청 시 사유. */
      String reason,
      /** WorkingCopy expected revision (승인 시에만 사용). */
      Long expectedRevision) {

    /** 하위 호환 생성자 (revision 없음). */
    public ApproveImageRequest(Long assetId, boolean approved, String reason) {
      this(assetId, approved, reason, null);
    }
  }

  /** 승인 응답. */
  public record ApproveImageResponse(
      Long assetId,
      /** 처리 결과: APPROVED, REJECTED, PUBLISHED. */
      String status,
      String message) {}

  /** 플로우 상태. */
  public record ImageEditFlowStatus(
      Long assetId,
      String slotKey,
      Integer version,
      /** DRAFT, PREVIEW_STAGED, APPROVED, PUBLISHED, REJECTED. */
      String flowStatus,
      String editDescription) {}
}
