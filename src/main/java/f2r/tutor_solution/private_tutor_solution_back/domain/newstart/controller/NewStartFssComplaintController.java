package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent.FssGuidedWorkflow;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent.NewStartFssAgentController;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.FssComplaintHelperService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 금감원 민원 도우미 API.
 *
 * <p>인증은 기존 {@code /api/**} catch-all(인증 필요)을 따른다. 설계 §7 경계에 따라
 * {@code GET /companion}(설명·예시·공식 안내)은 로그인 회원이면 열람하고,
 * {@code POST /compose}(AI 실행)는 THANKS_MEMBER 권한이 있을 때만 동작한다 — 거절은
 * 권한 예외 핸들러가 403 + 안내 문구로 번역한다.
 *
 * <p>V1은 무상태: 민원 원문·정리문 어디도 DB에 저장하지 않고, 로그에도 남기지 않는다.
 * 자동 제출은 존재하지 않는다(응답은 텍스트일 뿐이고, 제출은 사용자가 공식 사이트에서 직접 한다).
 */
@RestController
@RequestMapping("/api/newstart/fss")
public class NewStartFssComplaintController {

  private final FssComplaintHelperService fssComplaintHelperService;
  private final NewStartEntitlementService entitlementService;

  public NewStartFssComplaintController(FssComplaintHelperService fssComplaintHelperService,
      NewStartEntitlementService entitlementService) {
    this.fssComplaintHelperService = fssComplaintHelperService;
    this.entitlementService = entitlementService;
  }

  @GetMapping("/companion")
  public CompanionResponse companion() {
    return new CompanionResponse("ok",
        fssComplaintHelperService.samples(), fssComplaintHelperService.manual(),
        fssComplaintHelperService.officialGuide(), agentWorkflow());
  }

  /** Guided Agent 정적 안내 — {@link NewStartFssAgentController}/와 동일한 14단계 payload. */
  private static NewStartFssAgentController.WorkflowResponse agentWorkflow() {
    return new NewStartFssAgentController.WorkflowResponse("ok",
        FssGuidedWorkflow.OFFICIAL_ENTRY_URL, FssGuidedWorkflow.PC_CHROME_NOTICE,
        FssGuidedWorkflow.EXPLANATION, FssGuidedWorkflow.steps());
  }

  @PostMapping("/compose")
  public ResponseEntity<ComposeResponse> compose(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody ComposeRequest request) {
    entitlementService.require(principal, NewStartCapability.THANKS_MEMBER);
    FssComplaintHelperService.ComposeResult result;
    if (request != null && request.interview() != null) {
      result = fssComplaintHelperService.composeInterview(request.interview());
    } else {
      result = fssComplaintHelperService.compose(
          request == null ? null : request.sampleId(),
          request == null ? null : request.situationText());
    }
    if (!result.ok()) {
      return ResponseEntity.badRequest().body(
          ComposeResponse.failure(result.errorMessage()));
    }
    return ResponseEntity.ok(ComposeResponse.success(
        result.composedText(), result.subject(), result.body(), result.model()));
  }

  /**
   * @param sampleId 카드에서 선택한 전자책 예시 ID(FS-1..FS-3). @param situationText 사용자 자유 서술.
   * @param interview §10 guided interview 응답. 존재하면 자유서술 경로보다 우선한다(예시 불필요).
   */
  public record ComposeRequest(String sampleId, String situationText,
      FssComplaintHelperService.Interview interview) {}

  /**
   * @param status "ok" | "error". composedText 성공 시 AI 정리문 전문, subject/body는 복사용
   *               분리 값(§19; 파싱 실패 시 subject 빈 문자열, body=전문). errorMessage는
   *               사용자용 안내뿐이며 민원 원문·내부 상세를 담지 않는다.
   */
  public record ComposeResponse(String status, String composedText, String subject, String body,
      String errorMessage, String model) {
    public static ComposeResponse success(String composedText, String model) {
      return success(composedText, "", composedText, model);
    }

    public static ComposeResponse success(String composedText, String subject, String body,
        String model) {
      return new ComposeResponse("ok", composedText, subject, body, null, model);
    }

    public static ComposeResponse failure(String errorMessage) {
      return new ComposeResponse("error", "", "", "", errorMessage, null);
    }
  }

  /** 전자책 출처 식별자(ebookRef·sourceLines)가 각 샘플에 포함된 그대로 노출한다. */
  /** @param agent Phase 1 Guided Agent 14단계 정적 안내(개인정보 없음) — 화면 1회 로딩으로 함께 전달. */
  /** @param official §7 금감원 공식 소관 안내(2026-09-25 조회) — 개인정보 없음. */
  public record CompanionResponse(String status,
      List<FssComplaintHelperService.ComplaintSample> samples,
      FssComplaintHelperService.ManualGuide manual,
      FssComplaintHelperService.OfficialGuide official,
      NewStartFssAgentController.WorkflowResponse agent) {}
}
