package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiSuggestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyCompletionResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiContextService;
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
    return ResponseEntity.ok(
        suggestionService.generate(principal, task, request.target(), request.currentValue()));
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

  /** @param task {@link AcademyAiTaskType} 이름. @param target builderDraft key. */
  public record AiSuggestionRequest(String task, String target, String currentValue) {}
}
