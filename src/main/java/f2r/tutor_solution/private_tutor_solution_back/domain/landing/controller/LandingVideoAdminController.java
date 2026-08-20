package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoAdminResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoVisibilityRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.service.LandingVideoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/admin/landing/videos")
@RequiredArgsConstructor
public class LandingVideoAdminController {

  private final LandingVideoService service;

  @GetMapping
  public List<LandingVideoAdminResponse> list() {
    return service.listAdmin();
  }

  @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, path = "/{slot}")
  public LandingVideoAdminResponse upload(
      @PathVariable LandingVideoSlotKey slot,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart("video") MultipartFile video) {
    return service.upload(slot, principal, video);
  }

  @PatchMapping("/{slot}/visibility")
  public LandingVideoAdminResponse updateVisibility(
      @PathVariable LandingVideoSlotKey slot,
      @RequestBody LandingVideoVisibilityRequest request) {
    return service.updateVisibility(slot, request.visible());
  }

  @GetMapping(value = "/{slot}/stream", produces = "video/mp4")
  public ResponseEntity<StreamingResponseBody> stream(@PathVariable LandingVideoSlotKey slot) {
    return response(service.readAdminVideo(slot));
  }

  @GetMapping(value = "/{slot}/thumbnail", produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<StreamingResponseBody> thumbnail(@PathVariable LandingVideoSlotKey slot) {
    return response(service.readAdminThumbnail(slot));
  }

  private ResponseEntity<StreamingResponseBody> response(
      LandingVideoService.ManagedMedia media) {
    try {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.noStore())
          .contentType(media.contentType())
          .contentLength(media.contentLength())
          .body(media.body());
    } catch (RuntimeException e) {
      media.close();
      throw e;
    }
  }
}
