package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.CampaignView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.MaterialCard;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.PopupView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermCampaignPublicService;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermImageService;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermMaterialAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService.DownloadFile;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.web.FileDownloadResponses;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 학부모/학생용 공개 캠페인 API(작업지시서 §10·§11·§13·§14).
 *
 * <p>경로가 {@code /api/academies/**} 라 {@code SecurityConfig} 의 GET permitAll 을 탄다 —
 * BuildIt Wiki workspace 로 보내지 않고 <b>이 계층</b>이 프레젠테이션을 담당한다. 공개 범위
 * (PRIVATE/PREVIEW/PUBLIC/MEMBER)는 필터가 아니라 서비스가 판정한다
 * ({@link MidtermMaterialAccessService}) — permitAll 은 필터 차단만 해제할 뿐이다.
 */
@RestController
@RequiredArgsConstructor
public class MidtermCampaignPublicController {

  private final MidtermCampaignPublicService publicService;
  private final MidtermMaterialAccessService materialAccessService;
  private final AuthenticatedUserResolver authResolver;

  @GetMapping("/api/academies/{handle}/midterm-campaign")
  public ResponseEntity<CampaignView> campaign(@PathVariable String handle) {
    return publicService.findPublishedByHandle(handle)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개된 캠페인이 없습니다."));
  }

  @GetMapping("/api/academies/{handle}/midterm-campaign/popup")
  public ResponseEntity<PopupView> popup(@PathVariable String handle) {
    return publicService.findPopupByHandle(handle)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "노출할 팝업이 없습니다."));
  }

  @GetMapping("/api/academies/{handle}/midterm-campaign/materials/{materialId}")
  public ResponseEntity<MaterialCard> material(
      @PathVariable String handle, @PathVariable Long materialId) {
    return ResponseEntity.ok(publicService.requireListableCard(handle, materialId));
  }

  @GetMapping("/api/academies/{handle}/midterm-campaign/materials/{materialId}/file")
  public ResponseEntity<byte[]> materialFile(
      @PathVariable String handle, @PathVariable Long materialId) {
    MidtermMaterial material = publicService.requirePublishedMaterial(handle, materialId);
    DownloadFile file = materialAccessService.readFile(material, authResolver.currentPrincipal());
    return FileDownloadResponses.serve(file.bytes(), file.contentType(), file.fileName());
  }

  @GetMapping(value = "/api/academies/{handle}/midterm-campaign/materials/{materialId}/thumbnail")
  public ResponseEntity<byte[]> materialThumbnail(
      @PathVariable String handle, @PathVariable Long materialId) {
    MidtermImageService.ServedImage image =
        publicService.readMaterialThumbnail(handle, materialId);
    return image(image);
  }

  @GetMapping(value = "/api/academies/{handle}/midterm-campaign/popup-visual")
  public ResponseEntity<byte[]> popupVisual(@PathVariable String handle) {
    return image(publicService.readPopupVisual(handle));
  }

  private static ResponseEntity<byte[]> image(MidtermImageService.ServedImage image) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }
}
