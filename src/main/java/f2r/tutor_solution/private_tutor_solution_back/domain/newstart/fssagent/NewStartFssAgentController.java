package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 금감원 민원 Guided Agent API (Phase 1 = C: 사용자 PC Chrome + 단계 안내).
 *
 * <p>이 서버는 사용자의 금감원 탭을 보지 못하고, 보려고 하지도 않는다 — 브라우저 자동화
 * gateway(Playwright 등)는 존재하지 않는다. 엔드포인트는 전부 <b>요청 body가 없다</b>:
 * 서버가 아는 것은 userId와 단계 번호뿐이다.
 *
 * <p>설계 §7의 경계를 여기에서 지킨다: <b>14단계 안내 문구({@code /workflow})는 누구에게나 공개</b>
 * 이지만, 세션을 만들어 진행하는 <b>Agent 실행</b>은 THANKS_MEMBER 전용이다. 따라서
 * {@code /session}, {@code /advance}, {@code /back}, {@code /restart}는
 * {@link NewStartEntitlementService}를 통과한다(비로그인은 {@code /api/**} catch-all이 401,
 * 권한 없는 회원은 403 + 안내 문구).
 */
@RestController
@RequestMapping("/api/newstart/fss/agent")
public class NewStartFssAgentController {

  private final FssGuidedAgentService guidedAgentService;
  private final NewStartEntitlementService entitlementService;

  public NewStartFssAgentController(FssGuidedAgentService guidedAgentService,
      NewStartEntitlementService entitlementService) {
    this.guidedAgentService = guidedAgentService;
    this.entitlementService = entitlementService;
  }

  /** 14단계 안내 전문(정적 문구, 개인정보 없음) + 진입 URL + 고지 문구 — 회원/비로그인 모두 공개. */
  @GetMapping("/workflow")
  public WorkflowResponse workflow() {
    return new WorkflowResponse("ok", FssGuidedWorkflow.OFFICIAL_ENTRY_URL,
        FssGuidedWorkflow.PC_CHROME_NOTICE, FssGuidedWorkflow.EXPLANATION, FssGuidedWorkflow.steps());
  }

  /** 세션 조회(없으면 1단계로 생성). */
  @GetMapping("/session")
  public SessionResponse session(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireAgentRun(principal);
    return toResponse(guidedAgentService.session(principal.userId()));
  }

  /** "[이 단계를 완료했습니다]" — 화면 감지가 아닌 사용자 자기 선언이다. */
  @PostMapping("/advance")
  public SessionResponse advance(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireAgentRun(principal);
    return toResponse(guidedAgentService.completeCurrentStep(principal.userId()));
  }

  @PostMapping("/back")
  public SessionResponse back(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireAgentRun(principal);
    return toResponse(guidedAgentService.previousStep(principal.userId()));
  }

  @PostMapping("/restart")
  public SessionResponse restart(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireAgentRun(principal);
    return toResponse(guidedAgentService.restart(principal.userId()));
  }

  private void requireAgentRun(JwtTokenProvider.AuthenticatedPrincipal principal) {
    entitlementService.require(principal, NewStartCapability.THANKS_MEMBER);
  }

  private static SessionResponse toResponse(FssGuidedSession session) {
    return new SessionResponse("ok", session.currentStep(), session.finished(),
        FssGuidedWorkflow.total(), FssGuidedWorkflow.step(session.currentStep()).title());
  }

  public record WorkflowResponse(String status, String officialEntryUrl, String pcChromeNotice,
      String explanation, List<FssGuidedStep> steps) {}

  public record SessionResponse(String status, int currentStep, boolean finished, int totalSteps,
      String stepTitle) {}
}
