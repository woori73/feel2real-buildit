package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.ImageEditFlowDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미지 편집 승인 워크플로우.
 *
 * <p>플로우:
 * <ol>
 *   <li>PhotoCraft 편집 → 새 버전 생성 (DRAFT)</li>
 *   <li>미리보기에 스테이징 (PREVIEW_STAGED)</li>
 *   <li>사용자가 원본 vs 수정본 비교 후 승인/거부</li>
 *   <li>승인 → AcademyWorkingCopy.stageMedia로 스테이징 (APPROVED)</li>
 *   <li>발행 → 홈페이지 반영 (PUBLISHED)</li>
 *   <li>거부 → 이전 버전으로 되돌리기 (REJECTED)</li>
 * </ol>
 *
 * <p>의존성: #662의 AcademyImageService, AcademyWorkingCopyService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageEditApprovalService {

  // TODO(#662): PR #662 병합 후 주입 활성화
  // private final AcademyImageService imageService;
  // private final AcademyWorkingCopyService workingCopyService;
  // private final AcademyImageAssetRepository assetRepository;

  /**
   * 이미지 편집 승인/거부.
   *
   * @param principal 인증 사용자
   * @param request 승인 요청
   * @return 처리 결과
   */
  @Transactional
  public ImageEditFlowDtos.ApproveImageResponse approve(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      ImageEditFlowDtos.ApproveImageRequest request) {

    // TODO(#662): 실제 구현
    // 1. assetRepository로 이미지 조회 및 권한 검증
    // 2. approved=true이면:
    //    - workingCopyService.stageMedia(principal, revision, slotKey, storageKey) 호출
    //    - 상태를 APPROVED로 변경
    // 3. approved=false이면:
    //    - imageService.revertImage(principal, assetId) 호출
    //    - 상태를 REJECTED로 변경

    throw new UnsupportedOperationException(
        "PR #662 병합 후 구현 예정: AcademyImageService, AcademyWorkingCopyService 연동 필요");
  }

  /**
   * 플로우 상태 조회.
   */
  @Transactional(readOnly = true)
  public ImageEditFlowDtos.ImageEditFlowStatus getFlowStatus(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long assetId) {

    // TODO(#662): 실제 구현
    throw new UnsupportedOperationException(
        "PR #662 병합 후 구현 예정");
  }
}
