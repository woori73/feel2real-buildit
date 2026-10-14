package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyImageDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyImageAsset;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyImageAssetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 학원 홈페이지 이미지 관리 서비스.
 *
 * <p>HeroMediaSlot(원장사진·교재표지·시간표)과 구분되는 홈페이지용 이미지 자산 관리.
 * PhotoCraft를 이용한 편집, 버전 관리, 미리보기 스테이징, 되돌리기를 제공한다.
 *
 * <p>보안:
 * <ul>
 *   <li>학원별 격리: principal의 academyId로 접근 제어</li>
 *   <li>원본 보존: 편집 실패 시 원본 untouched</li>
 *   <li>명시적 요청: AI 채팅에서 사용자 확인 후에만 실행</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademyImageService {

  private final AcademyImageAssetRepository assetRepository;
  private final AcademyRepository academyRepository;
  private final PhotoCraftImageService photoCraftService;

  /** 이미지 저장 루트. */
  @Value("${academy.image.storage-root:/var/lib/f2r/academy-images}")
  private String storageRoot;

  /**
   * 이미지 편집.
   *
   * @param principal 인증 사용자
   * @param request 편집 요청
   * @return 편집 결과
   */
  @Transactional
  public AcademyImageDtos.EditImageResponse editImage(
      AuthenticatedPrincipal principal, AcademyImageDtos.EditImageRequest request) {

    Long academyId = resolveAcademyId(principal);

    // 대상 자산 조회
    AcademyImageAsset target;
    if (request.assetId() != null) {
      target = assetRepository.findById(request.assetId())
          .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다: " + request.assetId()));
      // 학원별 격리 검증
      if (!target.getAcademyId().equals(academyId)) {
        throw new SecurityException("다른 학원의 이미지에 접근할 수 없습니다.");
      }
    } else {
      target = assetRepository
          .findFirstByAcademyIdAndSlotKeyOrderByVersionDesc(academyId, request.slotKey())
          .orElseThrow(() -> new IllegalArgumentException(
              "슬롯에 이미지가 없습니다: " + request.slotKey() + ". 먼저 이미지를 업로드하세요."));
    }

    // PhotoCraft 작업으로 변환
    List<PhotoCraftImageService.ImageOperation> operations = new ArrayList<>();
    StringBuilder descBuilder = new StringBuilder();
    for (AcademyImageDtos.EditOperation op : request.operations()) {
      PhotoCraftImageService.ImageOperation mapped = mapOperation(op);
      if (mapped != null) {
        operations.add(mapped);
        if (descBuilder.length() > 0) descBuilder.append(", ");
        descBuilder.append(describeOperation(op));
      }
    }

    if (operations.isEmpty()) {
      throw new IllegalArgumentException("지원하는 편집 작업이 없습니다.");
    }

    // 출력 경로 생성
    String outputPath = buildOutputPath(academyId, request.slotKey(), target.getVersion() + 1);

    // PhotoCraft 실행
    String inputPath = resolveStoragePath(target.getEffectivePath());
    PhotoCraftImageService.EditResult result =
        photoCraftService.editImage(inputPath, outputPath, operations);

    if (!result.success()) {
      throw new RuntimeException("이미지 편집 실패: " + result.errorMessage());
    }

    // 새 버전 생성
    AcademyImageAsset edited = target.createEditedVersion(
        toStorageKey(outputPath), descBuilder.toString());

    if (request.applyToPreview()) {
      edited.markStagedForPreview();
    }

    edited = assetRepository.save(edited);
    log.info("이미지 편집 완료: academy={}, slot={}, version={}",
        academyId, request.slotKey(), edited.getVersion());

    return new AcademyImageDtos.EditImageResponse(
        edited.getId(),
        edited.getVersion(),
        toPublicUrl(edited.getEffectivePath()),
        edited.getEditDescription(),
        edited.getStagedForPreview());
  }

  /**
   * 이미지 되돌리기 (이전 버전으로).
   */
  @Transactional
  public AcademyImageDtos.ImageAssetInfo revertImage(
      AuthenticatedPrincipal principal, Long assetId) {

    Long academyId = resolveAcademyId(principal);

    AcademyImageAsset current = assetRepository.findById(assetId)
        .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다: " + assetId));

    if (!current.getAcademyId().equals(academyId)) {
      throw new SecurityException("다른 학원의 이미지에 접근할 수 없습니다.");
    }

    if (current.getPreviousAssetId() == null) {
      throw new IllegalStateException("되돌릴 이전 버전이 없습니다.");
    }

    AcademyImageAsset previous = assetRepository.findById(current.getPreviousAssetId())
        .orElseThrow(() -> new IllegalStateException("이전 버전을 찾을 수 없습니다."));

    // 이전 버전을 새 버전으로 복원 (이력 유지)
    AcademyImageAsset restored = previous.createEditedVersion(
        previous.getEffectivePath(), "버전 " + current.getVersion() + "에서 되돌림");
    restored = assetRepository.save(restored);

    return toInfo(restored);
  }

  /**
   * 학원의 이미지 목록 조회.
   */
  @Transactional(readOnly = true)
  public List<AcademyImageDtos.ImageAssetInfo> listImages(AuthenticatedPrincipal principal) {
    Long academyId = resolveAcademyId(principal);
    return assetRepository.findByAcademyIdOrderBySlotKeyAscVersionDesc(academyId)
        .stream()
        .map(this::toInfo)
        .toList();
  }

  private Long resolveAcademyId(AuthenticatedPrincipal principal) {
    return academyRepository.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new IllegalStateException("학원을 찾을 수 없습니다."))
        .getAcademyId();
  }

  private PhotoCraftImageService.ImageOperation mapOperation(AcademyImageDtos.EditOperation op) {
    String opName = op.op().toLowerCase();
    Object v = op.params().get("value");
    if ("brightness".equals(opName)) {
      int value = v instanceof Number n ? n.intValue() : 0;
      return PhotoCraftImageService.ImageOperation.brightness(value);
    } else if ("exposure".equals(opName)) {
      double value = v instanceof Number n ? n.doubleValue() : 0.0;
      return PhotoCraftImageService.ImageOperation.exposure(value);
    } else if ("vibrance".equals(opName) || "saturation".equals(opName)) {
      int value = v instanceof Number n ? n.intValue() : 0;
      return PhotoCraftImageService.ImageOperation.vibrance(value);
    } else {
      log.warn("지원하지 않는 이미지 작업: {}", op.op());
      return null;
    }
  }

  private String describeOperation(AcademyImageDtos.EditOperation op) {
    Object v = op.params().get("value");
    String opName = op.op().toLowerCase();
    if ("brightness".equals(opName)) {
      String sign = "";
      if (v != null) {
        try {
          if (Integer.parseInt(v.toString()) > 0) sign = "+";
        } catch (NumberFormatException e) {
          // ignore
        }
      }
      return "밝기 " + sign + v;
    } else if ("exposure".equals(opName)) {
      return "노출 " + v;
    } else if ("vibrance".equals(opName) || "saturation".equals(opName)) {
      return "채도 " + v;
    } else {
      return op.op();
    }
  }

  private String buildOutputPath(Long academyId, String slotKey, int version) {
    String filename = String.format("academy-%d-%s-v%d-%s.png",
        academyId, slotKey.toLowerCase().replace("_", "-"), version,
        UUID.randomUUID().toString().substring(0, 8));
    return Path.of(storageRoot, String.valueOf(academyId), filename).toString();
  }

  private String resolveStoragePath(String storageKey) {
    // storageKey를 실제 파일 경로로 변환
    return Path.of(storageRoot, storageKey).toString();
  }

  private String toStorageKey(String absolutePath) {
    // 절대 경로를 storageKey로 변환 (storageRoot 기준 상대 경로)
    Path root = Path.of(storageRoot);
    Path path = Path.of(absolutePath);
    return root.relativize(path).toString();
  }

  private String toPublicUrl(String storageKey) {
    // 공개 URL 생성 (실제 서빙 경로에 맞게 조정)
    return "/api/tutor/academy/images/" + storageKey.replace("/", "_");
  }

  private AcademyImageDtos.ImageAssetInfo toInfo(AcademyImageAsset asset) {
    return new AcademyImageDtos.ImageAssetInfo(
        asset.getId(),
        asset.getSlotKey(),
        asset.getVersion(),
        toPublicUrl(asset.getEffectivePath()),
        asset.getEditDescription(),
        asset.getStagedForPreview(),
        asset.getPublished(),
        asset.getPreviousAssetId());
  }
}
