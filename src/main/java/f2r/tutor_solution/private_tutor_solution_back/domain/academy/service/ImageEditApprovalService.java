package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.ImageEditFlowDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyWorkingCopy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyWorkingCopyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이미지 편집 승인 워크플로우.
 *
 * <p>플로우:
 * <ol>
 *   <li>PhotoCraft 편집 → 새 버전 생성 (DRAFT)</li>
 *   <li>미리보기에 스테이징 (PREVIEW_STAGED)</li>
 *   <li>사용자가 원본 vs 수정본 비교 후 승인/거부</li>
 *   <li>승인 → AcademyWorkingCopy.stageMedia로 스테이징 (APPROVED)</li>
 *   <li>발행 → 홈페이지 반영 (PUBLISHED) - WorkingCopy publish API 사용</li>
 *   <li>거부 → 이전 버전으로 되돌리기 (REJECTED)</li>
 * </ol>
 *
 * <p><b>보안 원칙:</b>
 * <ul>
 *   <li>승인되지 않은 이미지는 WorkingCopy에 스테이징되지 않음</li>
 *   <li>WorkingCopy에 스테이징되지 않은 이미지는 미리보기/홈페이지에 노출되지 않음</li>
 *   <li>발행은 WorkingCopy의 publish 플로우를 통해서만 가능</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageEditApprovalService {

  private final AcademyWorkingCopyService workingCopyService;
  private final AcademyWorkingCopyRepository workingCopyRepository;
  private final AcademyRepository academyRepository;

  /**
   * 승인 상태 저장소 (메모리 기반, #662 병합 후 DB로 이전 예정).
   * key: assetId, value: 플로우 상태
   */
  private final Map<Long, String> flowStatusStore = new ConcurrentHashMap<>();
  private final Map<Long, String> assetSlotStore = new ConcurrentHashMap<>();
  private final Map<Long, String> assetStorageKeyStore = new ConcurrentHashMap<>();

  /**
   * 편집 완료를 등록 (PhotoCraft 편집 후 호출).
   * 이 단계에서는 WorkingCopy에 반영하지 않음.
   *
   * @param assetId 이미지 자산 ID
   * @param slotKey 슬롯 키
   * @param storageKey 스토리지 키
   */
  public void registerEditedImage(Long assetId, String slotKey, String storageKey) {
    flowStatusStore.put(assetId, "PREVIEW_STAGED");
    assetSlotStore.put(assetId, slotKey);
    assetStorageKeyStore.put(assetId, storageKey);
    log.info("이미지 편집 등록: assetId={}, slot={}, status=PREVIEW_STAGED", assetId, slotKey);
  }

  /**
   * 이미지 편집 승인/거부.
   *
   * <p><b>승인 시:</b> AcademyWorkingCopy.stageMedia를 호출하여
   * 미리보기에 반영한다. 홈페이지 발행은 별도의 publish API를 통해 수행.
   *
   * <p><b>거부 시:</b> 상태를 REJECTED로 변경하고 WorkingCopy에 반영하지 않는다.
   * 이전 버전 복원은 #662의 revert 기능 사용 예정.
   *
   * @param principal 인증 사용자
   * @param request 승인 요청
   * @return 처리 결과
   */
  @Transactional
  public ImageEditFlowDtos.ApproveImageResponse approve(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      ImageEditFlowDtos.ApproveImageRequest request) {

    Long assetId = request.assetId();
    String currentStatus = flowStatusStore.get(assetId);

    if (currentStatus == null) {
      throw new IllegalArgumentException("편집 이력을 찾을 수 없습니다: " + assetId);
    }

    if (!"PREVIEW_STAGED".equals(currentStatus)) {
      throw new IllegalStateException("승인 가능한 상태가 아닙니다: " + currentStatus);
    }

    if (request.approved()) {
      // 승인: WorkingCopy에 스테이징
      String slotKey = assetSlotStore.get(assetId);
      String storageKey = assetStorageKeyStore.get(assetId);

      if (slotKey == null || storageKey == null) {
        throw new IllegalStateException("이미지 정보를 찾을 수 없습니다: " + assetId);
      }

      // expectedRevision 결정: 요청에 있으면 사용, 없으면 서버에서 조회
      long revision;
      if (request.expectedRevision() != null) {
        revision = request.expectedRevision();
        log.info("프론트엔드에서 전달된 revision 사용: {}", revision);
      } else {
        revision = getCurrentRevision(principal);
        log.info("서버에서 조회한 revision 사용: {}", revision);
      }

      // WorkingCopy에 스테이징 (승인된 이미지만)
      try {
        workingCopyService.stageMedia(principal, revision, slotKey, storageKey);
        log.info("이미지 승인 및 WorkingCopy 스테이징 완료: assetId={}, slot={}, revision={}",
            assetId, slotKey, revision);
      } catch (Exception e) {
        log.error("WorkingCopy 스테이징 실패: assetId={}", assetId, e);
        throw new IllegalStateException("WorkingCopy 스테이징 실패 (revision 충돌 가능): " + e.getMessage(), e);
      }

      flowStatusStore.put(assetId, "STAGED");

      return new ImageEditFlowDtos.ApproveImageResponse(
          assetId, "STAGED",
          "승인되어 미리보기에 반영되었습니다. 발행하면 홈페이지에 표시됩니다.");
    } else {
      // 거부: WorkingCopy에 반영하지 않음
      flowStatusStore.put(assetId, "REJECTED");
      log.info("이미지 거부: assetId={}, reason={}", assetId, request.reason());

      return new ImageEditFlowDtos.ApproveImageResponse(
          assetId, "REJECTED",
          "거부되었습니다. 이미지가 홈페이지에 반영되지 않습니다.");
    }
  }

  /**
   * 승인된 이미지를 WorkingCopy에 스테이징.
   *
   * <p>승인된 이미지만 이 메서드를 통해 WorkingCopy에 반영된다.
   * 승인되지 않은 이미지는 절대 이 메서드를 호출하지 않는다.
   *
   * @param principal 인증 사용자
   * @param assetId 이미지 자산 ID
   * @param expectedRevision WorkingCopy revision
   */
  @Transactional
  public void stageApprovedImage(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long assetId,
      long expectedRevision) {

    String status = flowStatusStore.get(assetId);
    if (!"APPROVED".equals(status)) {
      throw new SecurityException(
          "승인되지 않은 이미지는 WorkingCopy에 반영할 수 없습니다: " + assetId + " (상태: " + status + ")");
    }

    String slotKey = assetSlotStore.get(assetId);
    String storageKey = assetStorageKeyStore.get(assetId);

    // WorkingCopy에 스테이징
    workingCopyService.stageMedia(principal, expectedRevision, slotKey, storageKey);

    flowStatusStore.put(assetId, "STAGED");
    log.info("WorkingCopy 스테이징 완료: assetId={}, slot={}", assetId, slotKey);
  }

  /**
   * 현재 WorkingCopy의 revision을 조회.
   *
   * <p>서버 상태와 일치하는 revision을 반환하여
   * 낙관적 락 충돌을 방지한다.
   *
   * @param principal 인증 사용자
   * @return 현재 revision
   */
  public long getCurrentRevision(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Long academyId = academyRepository.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new IllegalStateException("학원을 찾을 수 없습니다."))
        .getAcademyId();

    AcademyWorkingCopy wc = workingCopyRepository.findByAcademyId(academyId)
        .orElseThrow(() -> new IllegalStateException("WorkingCopy를 찾을 수 없습니다."));

    return wc.getRevision();
  }

  /**
   * 승인된 이미지를 WorkingCopy에 스테이징 (revision 자동 조회).
   *
   * @param principal 인증 사용자
   * @param assetId 이미지 자산 ID
   */
  @Transactional
  public void stageApprovedImage(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long assetId) {
    long revision = getCurrentRevision(principal);
    stageApprovedImage(principal, assetId, revision);
  }

  /**
   * 플로우 상태 조회.
   */
  @Transactional(readOnly = true)
  public ImageEditFlowDtos.ImageEditFlowStatus getFlowStatus(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long assetId) {

    String status = flowStatusStore.getOrDefault(assetId, "UNKNOWN");
    String slotKey = assetSlotStore.getOrDefault(assetId, "");

    return new ImageEditFlowDtos.ImageEditFlowStatus(
        assetId, slotKey, 1, status, "");
  }

  /**
   * 승인되지 않은 이미지가 공개 홈페이지에 노출되지 않는지 검증.
   *
   * <p>검증 원칙: WorkingCopy의 stagedMediaJson에 없는 이미지는
   * 미리보기/홈페이지에 표시되지 않아야 한다.
   *
   * @param assetId 이미지 자산 ID
   * @return 승인되어 스테이징된 경우 true
   */
  public boolean isExposedToHomepage(Long assetId) {
    String status = flowStatusStore.get(assetId);
    // STAGED 또는 PUBLISHED 상태일 때만 홈페이지에 노출 가능
    return "STAGED".equals(status) || "PUBLISHED".equals(status);
  }
}
