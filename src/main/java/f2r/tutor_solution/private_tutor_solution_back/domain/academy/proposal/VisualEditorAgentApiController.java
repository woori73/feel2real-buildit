package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.VisualEditorDraftRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 시각 편집기 AI Agent 전용 API(Bearer 토큰, 학원 1개에 바인딩).
 *
 * <p>Agent가 할 수 있는 것은 <b>읽기(컨텍스트·제안 상태)와 제안 생성뿐</b>이다. 초안·게시본·게시·renderMode는
 * 이 경로에 없다. 학원은 토큰에서만 도출하며 요청의 어떤 값도 학원 식별에 쓰지 않는다.
 * 제안이 반영되려면 교육자가 편집기에서 미리보기 후 승인(저장)해야 한다.
 */
@RestController
@RequestMapping("/api/agent/academy/visual-editor")
@RequiredArgsConstructor
public class VisualEditorAgentApiController {

  private final VisualEditorDraftRepository draftRepository;
  private final VisualEditorProposalService proposalService;

  /** 제안 생성 요청 본문. */
  public record CreateProposalRequest(Long baseRevision, String summary, List<ProposalChange> changes) {
  }

  @GetMapping("/context")
  @Transactional(readOnly = true)
  public Map<String, Object> context(@AuthenticationPrincipal AgentTokenService.AgentPrincipal agent) {
    requireScope(agent, AgentToken.SCOPE_READ);
    Map<String, Object> out = new HashMap<>();
    var draft = draftRepository.findByAcademyId(agent.academyId());
    out.put("revision", draft.map(d -> d.getRevision()).orElse(0L));
    out.put("components", draft.map(d -> EditorOutline.of(d.getProjectData())).orElse(List.of()));
    out.put("editableTypes", List.of("text", "image", "style"));
    out.put("note", "제안은 사용자가 편집기에서 승인해야 반영됩니다. components는 저장된 초안 기준입니다.");
    return out;
  }

  @PostMapping("/proposals")
  public ResponseEntity<VisualEditorProposalService.ProposalView> propose(
      @AuthenticationPrincipal AgentTokenService.AgentPrincipal agent,
      @RequestBody CreateProposalRequest request) {
    requireScope(agent, AgentToken.SCOPE_PROPOSE);
    var view = proposalService.create(agent.academyId(), "AGENT", "token:" + agent.tokenId(),
        request.baseRevision(), request.summary(), request.changes());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/proposals/{id}")
  public VisualEditorProposalService.ProposalView get(
      @AuthenticationPrincipal AgentTokenService.AgentPrincipal agent, @PathVariable("id") Long id) {
    requireScope(agent, AgentToken.SCOPE_READ);
    return proposalService.get(agent.academyId(), id);
  }

  private static void requireScope(AgentTokenService.AgentPrincipal agent, String scope) {
    if (agent == null || !agent.hasScope(scope)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "토큰에 필요한 권한(" + scope + ")이 없습니다.");
    }
  }
}
