package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionWikiMaterialLinkRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionWikiMaterialLinkResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service.QuestionWikiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 문항 ↔ 기존 Wiki 자료 링크 관리(ADMIN 전용). {@code /api/admin/**} → Spring Security {@code hasRole('ADMIN')}.
 * 서비스에서도 principal 기준으로 한 번 더 검증한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/wiki/questions")
public class QuestionWikiAdminController {

  private final QuestionWikiService questionWikiService;

  @PostMapping("/{questionWikiId}/material-links")
  public ResponseEntity<QuestionWikiMaterialLinkResponse> addMaterialLink(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long questionWikiId,
      @RequestBody QuestionWikiMaterialLinkRequest request) {
    return ResponseEntity.ok(questionWikiService.addMaterialLink(principal, questionWikiId, request));
  }

  @DeleteMapping("/material-links/{linkId}")
  public ResponseEntity<Void> removeMaterialLink(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long linkId) {
    questionWikiService.removeMaterialLink(principal, linkId);
    return ResponseEntity.noContent().build();
  }
}
