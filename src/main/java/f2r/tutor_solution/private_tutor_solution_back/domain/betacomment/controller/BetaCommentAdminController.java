package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentAdminResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentHighlightUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentModerationView;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentStatusUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.service.BetaCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 베타 의견 모더레이션.
 *
 * <p>ROLE_ADMIN은 {@code SecurityConfig}의 {@code /api/admin/**} 규칙이 담당한다.
 */
@RestController
@RequestMapping("/api/admin/beta-comments")
@RequiredArgsConstructor
public class BetaCommentAdminController {

  private final BetaCommentService service;

  @GetMapping
  public BetaCommentModerationView list() {
    return service.loadModerationView();
  }

  @PatchMapping("/{id}/status")
  public BetaCommentAdminResponse updateStatus(
      @PathVariable Long id, @RequestBody BetaCommentStatusUpdateRequest request) {
    return service.updateStatus(id, request.status());
  }

  /** {@code highlightLabel}을 null로 보내면 뱃지를 해제한다. */
  @PatchMapping("/{id}/highlight")
  public BetaCommentAdminResponse updateHighlight(
      @PathVariable Long id, @RequestBody BetaCommentHighlightUpdateRequest request) {
    return service.updateHighlight(id, request.highlightLabel());
  }
}
