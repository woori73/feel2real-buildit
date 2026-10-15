package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyImageDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyImageAsset;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.HeroMediaSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyImageAssetRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 학원 홈페이지 이미지 편집 서비스 (PhotoCraft).
 *
 * <p>홈페이지 편집기가 이미 쓰는 이미지 슬롯({@link HeroMediaSlot})과 저장소({@link FileStorage})를 재사용한다.
 * 편집 대상은 사용자가 그 슬롯에 올려 둔(작업본 예약 우선, 없으면 공개) 이미지이며,
 * 편집본은 새 저장 키로 저장한다. 원본 파일은 건드리지 않는다.
 *
 * <p>편집은 작업본/공개본을 바꾸지 않는다. 반영은 승인({@link ImageEditApprovalService})에서만 일어난다.
 *
 * <p>보안: 학원별 격리(소유 학원 불일치 시 SecurityException), 저장 키는 응답으로 내보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademyImageService {

  private static final String IMAGE_FILE_TYPE = "IMAGE";
  private static final String PREVIEW_URL = "/api/tutor/academy/images/flow/%d/%s";

  private final AcademyImageAssetRepository assetRepository;
  private final AcademyService academyService;
  private final AcademyWorkingCopyService workingCopyService;
  private final FileStorage fileStorage;
  private final PhotoCraftImageService photoCraftService;

  /** 사진 + 읽기 결과 (바이트와 Content-Type). */
  public record ServedImage(byte[] bytes, String contentType) {}

  /** 슬롯에 편집할 이미지가 있는지 (작업본 예약 우선, 비우기 예약이면 없음). */
  @Transactional(readOnly = true)
  public boolean hasSourceImage(AuthenticatedPrincipal principal, String slotKey) {
    Academy academy = academyService.requireMyAcademy(principal);
    return currentSourceKey(principal, academy, parseSlot(slotKey)) != null;
  }

  /**
   * 이미지 편집.
   *
   * @param principal 인증 사용자
   * @param request 편집 요청 (slotKey는 {@link HeroMediaSlot} 키: principal/textbook1~3/schedule)
   * @return 편집 결과 (미리보기 승인 대기 상태)
   */
  @Transactional
  public AcademyImageDtos.EditImageResponse editImage(
      AuthenticatedPrincipal principal, AcademyImageDtos.EditImageRequest request) {

    Academy academy = academyService.requireMyAcademy(principal);
    Long academyId = academy.getAcademyId();

    // 편집 대상: 지정 자산(소유 검증) 또는 슬롯에 현재 올려 둔 이미지
    HeroMediaSlot slot;
    String sourceKey;
    AcademyImageAsset target = null;
    if (request.assetId() != null) {
      target = requireOwnAsset(academyId, request.assetId());
      slot = parseSlot(target.getSlotKey());
      sourceKey = target.getEffectivePath();
    } else {
      slot = parseSlot(request.slotKey());
      sourceKey = currentSourceKey(principal, academy, slot);
      if (sourceKey == null) {
        throw new IllegalArgumentException("슬롯에 이미지가 없습니다: " + slot.key() + ". 먼저 이미지를 등록하세요.");
      }
    }

    // 움직이는 이미지(GIF/WebP)는 편집하면 프레임이 사라지므로 편집하지 않는다
    if (isAnimatedFormat(sourceKey)) {
      throw new IllegalArgumentException("움직이는 이미지(GIF/WebP)는 편집할 수 없습니다.");
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

    // 편집 실행 (임시 디렉터리에서만 파일을 만든다. 실패하면 저장소/DB는 그대로)
    byte[] editedBytes = runPhotoCraft(sourceKey, operations);

    String editedKey;
    try {
      editedKey = fileStorage.saveImage(
          academy.getTutor().getUserId(), slot.filenameHint() + "-edit", editedBytes, ImageSanitizer.JPEG_EXTENSION);
    } catch (IOException e) {
      throw new IllegalStateException("편집본 저장에 실패했습니다.", e);
    }

    try {
      if (target == null) {
        target = findOrRegisterSource(academyId, slot, sourceKey);
      }
      AcademyImageAsset edited = target.createEditedVersion(editedKey, descBuilder.toString());
      edited.markStagedForPreview();
      edited = assetRepository.save(edited);
      log.info("이미지 편집 완료: academy={}, slot={}, version={}", academyId, slot.key(), edited.getVersion());
      return new AcademyImageDtos.EditImageResponse(
          edited.getId(), edited.getVersion(), previewUrl(edited.getId(), "edited"),
          edited.getEditDescription(), edited.getStagedForPreview());
    } catch (RuntimeException e) {
      fileStorage.delete(editedKey, IMAGE_FILE_TYPE); // DB 실패 시 고아 파일 방지
      throw e;
    }
  }

  /** 편집 전/후 이미지 바이트 (소유 학원만). */
  @Transactional(readOnly = true)
  public ServedImage readAssetImage(AuthenticatedPrincipal principal, Long assetId, boolean edited) {
    Academy academy = academyService.requireMyAcademy(principal);
    AcademyImageAsset asset = requireOwnAsset(academy.getAcademyId(), assetId);
    String key = edited ? asset.getEffectivePath() : asset.getOriginalPath();
    try {
      return new ServedImage(
          fileStorage.readPage(key, IMAGE_FILE_TYPE, 1), ImageSanitizer.contentTypeForStorageKey(key));
    } catch (IOException e) {
      throw new IllegalStateException("이미지를 읽을 수 없습니다.", e);
    }
  }

  /** 이미지 되돌리기 (이전 버전으로). */
  @Transactional
  public AcademyImageDtos.ImageAssetInfo revertImage(AuthenticatedPrincipal principal, Long assetId) {
    Academy academy = academyService.requireMyAcademy(principal);
    AcademyImageAsset current = requireOwnAsset(academy.getAcademyId(), assetId);

    if (current.getPreviousAssetId() == null) {
      throw new IllegalStateException("되돌릴 이전 버전이 없습니다.");
    }
    AcademyImageAsset previous = assetRepository.findById(current.getPreviousAssetId())
        .orElseThrow(() -> new IllegalStateException("이전 버전을 찾을 수 없습니다."));

    // 이전 버전을 새 버전으로 복원 (이력 유지)
    AcademyImageAsset restored = previous.createEditedVersion(
        previous.getEffectivePath(), "버전 " + current.getVersion() + "에서 되돌림");
    return toInfo(assetRepository.save(restored));
  }

  /** 학원의 이미지 목록 조회. */
  @Transactional(readOnly = true)
  public List<AcademyImageDtos.ImageAssetInfo> listImages(AuthenticatedPrincipal principal) {
    Academy academy = academyService.requireMyAcademy(principal);
    return assetRepository.findByAcademyIdOrderBySlotKeyAscVersionDesc(academy.getAcademyId())
        .stream().map(this::toInfo).toList();
  }

  /** 미리보기 비교 URL (인증 필요, 저장 키는 노출하지 않는다). */
  public static String previewUrl(Long assetId, String which) {
    return PREVIEW_URL.formatted(assetId, which);
  }

  // ───────────────────────── 내부 ─────────────────────────

  private AcademyImageAsset requireOwnAsset(Long academyId, Long assetId) {
    AcademyImageAsset asset = assetRepository.findById(assetId)
        .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다: " + assetId));
    if (!asset.getAcademyId().equals(academyId)) {
      throw new SecurityException("다른 학원의 이미지에 접근할 수 없습니다.");
    }
    return asset;
  }

  private static HeroMediaSlot parseSlot(String slotKey) {
    return HeroMediaSlot.from(slotKey == null ? null : slotKey.toLowerCase());
  }

  /** 작업본 예약이 있으면 그 키(비우기 예약이면 null), 없으면 공개 슬롯 키. */
  private String currentSourceKey(AuthenticatedPrincipal principal, Academy academy, HeroMediaSlot slot) {
    Map<String, String> staged = workingCopyService.stagedMedia(principal);
    if (staged.containsKey(slot.key())) {
      String key = staged.get(slot.key());
      return key == null || key.isEmpty() ? null : key;
    }
    return AcademyService.heroMediaKey(academy, slot);
  }

  /** 현재 이미지가 이전 편집본이면 그 이력을 잇고, 아니면 원본으로 등록한다. */
  private AcademyImageAsset findOrRegisterSource(Long academyId, HeroMediaSlot slot, String sourceKey) {
    return assetRepository
        .findFirstByAcademyIdAndSlotKeyAndEditedPathOrderByVersionDesc(academyId, slot.key(), sourceKey)
        .or(() -> assetRepository
            .findFirstByAcademyIdAndSlotKeyAndOriginalPathAndEditedPathIsNullOrderByVersionAsc(
                academyId, slot.key(), sourceKey))
        .orElseGet(() -> assetRepository.save(
            AcademyImageAsset.createOriginal(academyId, slot.key(), sourceKey)));
  }

  private byte[] runPhotoCraft(String sourceKey, List<PhotoCraftImageService.ImageOperation> operations) {
    Path workDir = null;
    try {
      workDir = Files.createTempDirectory("f2r-photocraft-");
      Path in = workDir.resolve("in" + extensionOf(sourceKey));
      Path out = workDir.resolve("result/out.jpg");
      Files.write(in, fileStorage.readPage(sourceKey, IMAGE_FILE_TYPE, 1));
      PhotoCraftImageService.EditResult result =
          photoCraftService.editImage(in.toString(), out.toString(), operations);
      if (!result.success()) {
        throw new RuntimeException("이미지 편집 실패: " + result.errorMessage());
      }
      return Files.readAllBytes(out);
    } catch (IOException e) {
      throw new RuntimeException("이미지 편집 실패: " + e.getMessage(), e);
    } finally {
      deleteQuietly(workDir);
    }
  }

  static boolean isAnimatedFormat(String key) {
    String k = key.toLowerCase();
    return k.endsWith("." + ImageSanitizer.GIF_EXTENSION) || k.endsWith("." + ImageSanitizer.WEBP_EXTENSION);
  }

  private static String extensionOf(String key) {
    int dot = key.lastIndexOf('.');
    return dot >= 0 ? key.substring(dot) : ".png";
  }

  private static void deleteQuietly(Path dir) {
    if (dir == null) return;
    try (var walk = Files.walk(dir)) {
      walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException e) {
      log.warn("임시 디렉터리 정리 실패: {}", dir);
    }
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

  private AcademyImageDtos.ImageAssetInfo toInfo(AcademyImageAsset asset) {
    return new AcademyImageDtos.ImageAssetInfo(
        asset.getId(),
        asset.getSlotKey(),
        asset.getVersion(),
        previewUrl(asset.getId(), "edited"),
        asset.getEditDescription(),
        asset.getStagedForPreview(),
        asset.getPublished(),
        asset.getPreviousAssetId());
  }
}
