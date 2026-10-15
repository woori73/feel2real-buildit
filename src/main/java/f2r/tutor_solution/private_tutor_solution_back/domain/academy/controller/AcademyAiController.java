package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiGuideSuggestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiProposal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiSuggestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyCompletionResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiContextService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiGuideService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiProposalService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiSuggestionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyCompletionCheckService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Academy Builder AI Context/Suggestion/Completion API.
 *
 * <p>전부 {@code /api/tutor/academy/ai/**}이고, 어떤 요청도 {@code academyId}를 body/param으로
 * 받지 않는다 — 항상 인증된 principal 기준으로 본인 Academy만 조회/생성한다(다른 Academy를
 * 건드릴 방법이 API 계약 자체에 없다). WRITE(=Builder에 실제로 반영)는 이 컨트롤러에 없다 —
 * 여기는 Suggestion을 "만들어서 보여주기"까지만 하고, 실제 반영은 기존
 * {@code PUT /api/tutor/academy}를 그대로 쓴다(§ AcademyAiSuggestionService 클래스 주석 참고).
 */
@RestController
@RequestMapping("/api/tutor/academy/ai")
@RequiredArgsConstructor
public class AcademyAiController {

  private final AcademyAiContextService contextService;
  private final AcademyAiSuggestionService suggestionService;
  private final AcademyAiProposalService proposalService;
  private final AcademyAiGuideService guideService;
  private final AcademyCompletionCheckService completionCheckService;
  private final AiProvider aiProvider;

  /** 디버그/향후 WebMCP 노출용. 민감 필드 없이 A급 필드만 담은 context를 그대로 보여준다. */
  @GetMapping("/context")
  public ResponseEntity<AcademyAiContext> context(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(contextService.buildContext(principal));
  }

  /** provider 준비 상태만 노출한다 — API key 등 실제 자격 증명 값은 절대 포함하지 않는다. */
  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> status() {
    return ResponseEntity.ok(Map.of(
        "provider", aiProvider.name(),
        "ready", aiProvider.isReady()));
  }

  @PostMapping("/suggestions")
  public ResponseEntity<AcademyAiSuggestion> generateSuggestion(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody AiSuggestionRequest request) {
    if (request == null || request.task() == null) {
      throw new IllegalArgumentException("task가 필요합니다.");
    }
    AcademyAiTaskType task = parseTask(request.task());
    return ResponseEntity.ok(suggestionService.generate(
        principal, task, request.target(), request.currentValue(), request.instructions()));
  }

  /**
   * Phase 5A — Academy 전체를 분석하고 안전한 개선안 묶음을 돌려준다. READ 전용이다:
   * 이 호출만으로는 {@code builderDraft}도 Production도 바뀌지 않는다. 실제 반영은 사용자가
   * Builder에서 proposal별 "적용"을 눌렀을 때 기존 {@code PUT /api/tutor/academy} 경로로만
   * 이뤄진다. academyId를 받지 않는다.
   */
  @PostMapping("/academy-proposal")
  public ResponseEntity<AcademyAiProposal> academyProposal(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody(required = false) AcademyProposalRequest request) {
    String instructions = request == null ? null : request.instructions();
    return ResponseEntity.ok(proposalService.generate(principal, instructions));
  }

  /**
   * Phase 5B — AI Guided Builder 한 단계의 문구 후보 2~3개를 돌려준다. READ 전용이다: 이 호출
   * 만으로는 {@code builderDraft}도 Production도 바뀌지 않는다. 실제 반영은 사용자가 Guide
   * 패널에서 후보의 "적용"을 눌렀을 때 기존 인라인 편집과 같은 경로로만 이뤄진다.
   * academyId를 받지 않는다.
   */
  @PostMapping("/guide-suggestions")
  public ResponseEntity<AcademyAiGuideSuggestion> guideSuggestions(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody GuideSuggestionRequest request) {
    if (request == null || request.target() == null) {
      throw new IllegalArgumentException("target이 필요합니다.");
    }
    return ResponseEntity.ok(guideService.generate(
        principal, request.target(), request.answer(), request.style()));
  }

  @GetMapping("/completion")
  public ResponseEntity<AcademyCompletionResult> completion(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(completionCheckService.evaluate(principal));
  }

  private static AcademyAiTaskType parseTask(String raw) {
    try {
      return AcademyAiTaskType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("알 수 없는 AI task입니다: " + raw);
    }
  }

  /**
   * @param task {@link AcademyAiTaskType} 이름. @param target builderDraft key.
   * @param instructions 사용자가 AI에게 추가로 요청한 내용(선택, 예: "더 짧게"). null이면 없음으로
   *     취급한다 — 길이 제한 자체는 AcademyAiSuggestionService가 검증한다.
   */
  public record AiSuggestionRequest(String task, String target, String currentValue, String instructions) {}

  /** @param instructions 사용자가 "어떤 Academy로 만들고 싶은지" 자유 입력(선택, 200자 제한은 서비스가 검증). */
  public record AcademyProposalRequest(String instructions) {}

  /**
   * @param target 추천 대상 builderDraft key(또는 top-level {@code intro}). allowlist 밖이면 서비스가 거부한다.
   * @param answer 사용자가 이 Guide 단계에서 답한 강조점(선택).
   * @param style Guide 세션에서 정한 홈페이지 스타일/톤(선택).
   */
  public record GuideSuggestionRequest(String target, String answer, String style) {}
}
