package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.ImageEditFlowDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyImageAsset;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyWorkingCopy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.HeroMediaSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyImageAssetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyWorkingCopyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 이미지 편집 승인 워크플로우.
 *
 * <p>상태는 DB에서 계산한다(서버 재시작 후에도 유지). 별도 상태 컬럼 없이 기존 데이터로 결정된다:
 * <ul>
 *   <li>PUBLISHED — 공개 슬롯의 저장 키가 편집본 키와 같다</li>
 *   <li>STAGED — 작업본 예약({@code stagedMediaJson})이 편집본 키를 가리킨다 (승인됨, 게시 대기)</li>
 *   <li>PREVIEW_STAGED — 편집 직후 승인 대기 ({@code stagedForPreview=true})</li>
 *   <li>REJECTED — 그 밖 (거부·취소·대체됨)</li>
 * </ul>
 *
 * <p><b>보안 원칙:</b>
 * <ul>
 *   <li>승인 전에는 작업본/공개본 어디에도 편집본이 반영되지 않는다 (편집 단계는 저장소·자산 테이블만 건드린다)</li>
 *   <li>작업본 반영은 공개 URL이 아닌 실제 저장 키로 한다</li>
 *   <li>게시는 기존 작업본 게시 플로우로만 가능하다</li>
 *   <li>다른 학원의 자산은 승인·조회할 수 없다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageEditApprovalService {

  private final AcademyWorkingCopyService workingCopyService;
  private final AcademyWorkingCopyRepository workingCopyRepository;
  private final AcademyService academyService;
  private final AcademyImageAssetRepository assetRepository;

  /**
   * 이미지 편집 승인/거부.
   *
   * <p><b>승인:</b> 작업본 예약에 편집본 저장 키를 올린다. 홈페이지 게시는 별도의 게시 API로 한다.
   * <p><b>거부:</b> 반영하지 않는다. 이미 승인(작업본 예약)된 편집본이면 예약을 해제한다.
   */
  @Transactional
  public ImageEditFlowDtos.ApproveImageResponse approve(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      ImageEditFlowDtos.ApproveImageRequest request) {

    Academy academy = academyService.requireMyAcademy(principal);
    AcademyImageAsset asset = requireOwnAsset(academy, request.assetId());
    Map<String, String> staged = workingCopyService.stagedMedia(principal);
    String status = statusOf(academy, asset, staged);

    if (request.approved()) {
      if (!"PREVIEW_STAGED".equals(status)) {
        throw new IllegalStateException("승인 가능한 상태가 아닙니다: " + status);
      }
      long revision = request.expectedRevision() != null
          ? request.expectedRevision() : currentRevision(academy);
      try {
        // 공개 URL이 아니라 저장소 키 기준으로 작업본에 예약
        workingCopyService.stageMedia(principal, revision, asset.getSlotKey(), asset.getEffectivePath());
      } catch (Exception e) {
        log.error("WorkingCopy 스테이징 실패: assetId={}", asset.getId(), e);
        throw new IllegalStateException("WorkingCopy 스테이징 실패 (revision 충돌 가능): " + e.getMessage(), e);
      }
      asset.clearPreviewStage();
      assetRepository.save(asset);
      return new ImageEditFlowDtos.ApproveImageResponse(
          asset.getId(), "STAGED",
          "승인되어 미리보기에 반영되었습니다. 발행하면 홈페이지에 표시됩니다.");
    }

    if ("PUBLISHED".equals(status)) {
      throw new IllegalStateException("이미 게시된 이미지입니다. 버전 기록에서 복원하세요.");
    }
    if ("STAGED".equals(status)) {
      // 승인 후 취소/원본 복원: 작업본 예약 해제 (공개본은 그대로)
      workingCopyService.stageMedia(
          principal, request.expectedRevision() != null ? request.expectedRevision() : currentRevision(academy),
          asset.getSlotKey(), null);
    }
    asset.clearPreviewStage();
    assetRepository.save(asset);
    log.info("이미지 거부: assetId={}, reason={}", asset.getId(), request.reason());
    return new ImageEditFlowDtos.ApproveImageResponse(
        asset.getId(), "REJECTED", "거부되었습니다. 이미지가 홈페이지에 반영되지 않습니다.");
  }

  /** 현재 WorkingCopy의 revision (없으면 신규 작업본의 초기값 0). */
  @Transactional(readOnly = true)
  public long getCurrentRevision(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return currentRevision(academyService.requireMyAcademy(principal));
  }

  /** 로그인한 튜터의 학원 ID. */
  @Transactional(readOnly = true)
  public Long currentAcademyId(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academyService.requireMyAcademy(principal).getAcademyId();
  }

  /** 플로우 상태 조회 (소유 학원만). */
  @Transactional(readOnly = true)
  public ImageEditFlowDtos.ImageEditFlowStatus getFlowStatus(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long assetId) {
    Academy academy = academyService.requireMyAcademy(principal);
    AcademyImageAsset asset = requireOwnAsset(academy, assetId);
    String status = statusOf(academy, asset, workingCopyService.stagedMedia(principal));
    return new ImageEditFlowDtos.ImageEditFlowStatus(
        asset.getId(), asset.getSlotKey(), asset.getVersion(), status,
        asset.getEditDescription() == null ? "" : asset.getEditDescription());
  }

  private long currentRevision(Academy academy) {
    return workingCopyRepository.findByAcademyId(academy.getAcademyId())
        .map(AcademyWorkingCopy::getRevision)
        .orElse(0L);
  }

  private AcademyImageAsset requireOwnAsset(Academy academy, Long assetId) {
    AcademyImageAsset asset = assetRepository.findById(assetId)
        .orElseThrow(() -> new IllegalArgumentException("편집 이력을 찾을 수 없습니다: " + assetId));
    if (!asset.getAcademyId().equals(academy.getAcademyId())) {
      throw new SecurityException("다른 학원의 이미지에 접근할 수 없습니다.");
    }
    return asset;
  }

  /** DB 상태(공개 슬롯·작업본 예약·자산 플래그)로 플로우 상태를 계산한다. */
  static String statusOf(Academy academy, AcademyImageAsset asset, Map<String, String> staged) {
    String editedKey = asset.getEditedPath();
    if (editedKey == null) {
      return "ORIGINAL";
    }
    if (editedKey.equals(AcademyService.heroMediaKey(academy, HeroMediaSlot.from(asset.getSlotKey())))) {
      return "PUBLISHED";
    }
    if (editedKey.equals(staged.get(asset.getSlotKey()))) {
      return "STAGED";
    }
    return asset.getStagedForPreview() ? "PREVIEW_STAGED" : "REJECTED";
  }
}
