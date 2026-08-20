package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.service.LandingVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/landing/videos")
@RequiredArgsConstructor
public class LandingVideoPublicController {

  private final LandingVideoService service;

  @GetMapping(value = "/{slot}/stream", produces = "video/mp4")
  public ResponseEntity<StreamingResponseBody> stream(@PathVariable LandingVideoSlotKey slot) {
    LandingVideoService.ManagedMedia media = service.readPublicVideo(slot);
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
