package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/easy/academy-links")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EasyAcademyLinkAdminController {

  private final EasyTutorAccessService accessService;

  @PostMapping
  public ResponseEntity<EasyBusinessResponse> link(@RequestBody LinkRequest request) {
    return ResponseEntity.ok(accessService.link(request.academyId(), request.businessId()));
  }

  @DeleteMapping("/academies/{academyId}/businesses/{businessId}")
  public ResponseEntity<Void> unlink(
      @PathVariable Long academyId, @PathVariable Long businessId) {
    accessService.unlink(academyId, businessId);
    return ResponseEntity.noContent().build();
  }

  public record LinkRequest(Long academyId, Long businessId) {
  }
}
