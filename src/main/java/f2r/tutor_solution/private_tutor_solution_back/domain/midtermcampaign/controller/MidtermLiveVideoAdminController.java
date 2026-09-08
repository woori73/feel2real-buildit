package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoAdminPayload;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoLinkCreate;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoLinkUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermLiveVideoAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 "LIVE 수업 영상" 연결 API(Phase 4c). 경로가 {@code /api/tutor/lms/{handle}/...} 라
 * {@code SecurityConfig} 의 {@code hasRole("TUTOR")} 를 그대로 타고, 타 학원 TUTOR·ADMIN 우회는
 * {@link MidtermLiveVideoAdminService} 의 {@code requireOwnedAcademyByHandle} 가 막는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tutor/lms/{handle}/midterm-campaign/live-videos")
public class MidtermLiveVideoAdminController {

  private final MidtermLiveVideoAdminService service;

  @GetMapping
  public ResponseEntity<LiveVideoAdminPayload> list(@PathVariable String handle) {
    return ResponseEntity.ok(service.list(handle));
  }

  @PostMapping
  public ResponseEntity<LiveVideoAdminPayload> create(
      @PathVariable String handle, @RequestBody LiveVideoLinkCreate req) {
    return ResponseEntity.ok(service.create(handle, req));
  }

  @PatchMapping("/{linkId}")
  public ResponseEntity<LiveVideoAdminPayload> update(
      @PathVariable String handle, @PathVariable Long linkId,
      @RequestBody LiveVideoLinkUpdate req) {
    return ResponseEntity.ok(service.update(handle, linkId, req));
  }

  @DeleteMapping("/{linkId}")
  public ResponseEntity<LiveVideoAdminPayload> delete(
      @PathVariable String handle, @PathVariable Long linkId) {
    return ResponseEntity.ok(service.delete(handle, linkId));
  }
}
