package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiCandidateAdminView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiExposureUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermWikiSourceAdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기존 TUTOR owner 보안 경계 아래의 최소 Wiki exposure API. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tutor/lms/{handle}/midterm-campaign/wiki-candidates")
public class MidtermWikiSourceAdminController {

  private final MidtermWikiSourceAdminService service;

  @GetMapping
  public ResponseEntity<List<WikiCandidateAdminView>> list(@PathVariable String handle) {
    return ResponseEntity.ok(service.listCandidates(handle));
  }

  @PatchMapping("/{sourceId}/exposure")
  public ResponseEntity<List<WikiCandidateAdminView>> updateExposure(
      @PathVariable String handle,
      @PathVariable String sourceId,
      @RequestBody WikiExposureUpdate request) {
    return ResponseEntity.ok(service.updateExposure(handle, sourceId, request));
  }
}
