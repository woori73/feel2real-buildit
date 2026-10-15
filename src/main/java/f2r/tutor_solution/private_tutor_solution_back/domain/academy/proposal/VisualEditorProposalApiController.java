package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시각 편집기 교육자용 AI 제안 API(쿠키 JWT, TUTOR, 본인 학원).
 *
 * <p>승인은 별도 엔드포인트가 아니다 — 편집기가 제안을 적용해 {@code PUT …/draft}에
 * {@code appliedProposalId}를 실어 저장하는 것이 승인이며, 그 저장 트랜잭션 안에서 APPLIED가 된다.
 * 학원은 항상 로그인 사용자의 학원이며 요청값이 아니다.
 */
@RestController
@RequestMapping("/api/tutor/academy/page-settings/visual-editor")
@RequiredArgsConstructor
public class VisualEditorProposalApiController {

  private final AcademyService academyService;
  private final VisualEditorProposalService proposalService;
  private final ProposalAiService proposalAiService;
  private final AgentTokenService agentTokenService;

  public record AiRequest(String instruction) {
  }

  public record TokenRequest(String label) {
  }

  @GetMapping("/proposals")
  public List<VisualEditorProposalService.ProposalView> list(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return proposalService.listForEditor(academyId(principal));
  }

  @PostMapping("/proposals/{id}/reject")
  public VisualEditorProposalService.ProposalView reject(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable("id") Long id) {
    return proposalService.reject(academyId(principal), id);
  }

  /** AI에게 수정 요청 → 제안(PENDING) 생성. 초안·공개 페이지는 바뀌지 않는다. */
  @PostMapping("/ai-requests")
  public ResponseEntity<VisualEditorProposalService.ProposalView> aiRequest(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody AiRequest request) {
    var view = proposalAiService.requestChange(academyId(principal), request.instruction());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/agent-tokens")
  public List<AgentTokenService.TokenView> tokens(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return agentTokenService.list(academyId(principal));
  }

  /** 평문 토큰은 이 응답에서 한 번만 보인다. */
  @PostMapping("/agent-tokens")
  public ResponseEntity<AgentTokenService.IssuedToken> issueToken(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody TokenRequest request) {
    var issued = agentTokenService.issue(academyId(principal), principal.userId(), request.label());
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Cache-Control", "no-store").body(issued);
  }

  @DeleteMapping("/agent-tokens/{id}")
  public Map<String, Object> revokeToken(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable("id") Long id) {
    agentTokenService.revoke(academyId(principal), id);
    return Map.of("status", "REVOKED");
  }

  private Long academyId(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academyService.getMine(principal)
        .orElseThrow(() -> new AccessDeniedException("편집할 홈페이지가 없습니다."))
        .academyId();
  }
}
