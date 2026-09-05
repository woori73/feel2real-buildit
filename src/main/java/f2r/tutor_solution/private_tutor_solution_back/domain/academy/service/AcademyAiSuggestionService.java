package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiSuggestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Context → Task → Provider → Suggestion 파이프라인의 조립점.
 *
 * <p>절대 원칙: 이 서비스는 어떤 경우에도 {@code Academy}/{@code builderDraft}를 수정하지
 * 않는다. 순수 조회(context 조립) + 외부 provider 호출 + 결과 검증만 하고, 만든
 * {@link AcademyAiSuggestion}을 그대로 돌려준다. 실제 반영은 사용자가 Builder에서 "적용"을
 * 눌렀을 때 프론트엔드가 기존 인라인 편집과 같은 경로(PUT /api/tutor/academy)로 수행한다 —
 * 이 서비스에는 "적용" 엔드포인트 자체가 없다.
 *
 * <p>Suggestion을 DB에 저장하지 않기로 한 이유: Suggestion의 생명주기는 정확히 "지금 열린
 * Builder 편집 세션" 하나다. reload 후에도 유지해야 한다거나, 여러 suggestion을 감사(audit)
 * 목적으로 보관해야 한다는 요구가 없다 — 있다면 그때 별도 테이블을 추가하면 되고, 지금은
 * "가장 작은 안전한 구조"가 stateless 응답이다. 상태(PENDING/APPLIED/DISMISSED)는 브라우저의
 * 모달 하나가 들고 있는 것으로 충분하다.
 */
@Service
public class AcademyAiSuggestionService {

  /**
   * Task별로 AI가 건드릴 수 있는 target(builderDraft key)을 명시적으로 제한한다. 여기 없는
   * target은 어떤 Task로도 절대 생성 요청을 만들 수 없다 — allowlist 밖의 값(academyId,
   * videoPurpose, published 등)은애초에 이 서비스의 관심사에 들어오지 않는다.
   */
  private static final Map<AcademyAiTaskType, Set<String>> ALLOWED_TARGETS = Map.of(
      AcademyAiTaskType.PRINCIPAL_INTRO, Set.of("principalDesc"),
      AcademyAiTaskType.PHILOSOPHY_ITEM,
          Set.of("philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4"));

  private static final int MAX_CURRENT_VALUE_LENGTH = 2000;
  private static final Map<AcademyAiTaskType, Integer> MAX_OUTPUT_CHARS = Map.of(
      AcademyAiTaskType.PRINCIPAL_INTRO, 220,
      AcademyAiTaskType.PHILOSOPHY_ITEM, 80);

  private final AcademyAiContextService contextService;
  private final AcademyAiPromptBuilder promptBuilder;
  private final AiProvider aiProvider;

  public AcademyAiSuggestionService(
      AcademyAiContextService contextService,
      AcademyAiPromptBuilder promptBuilder,
      AiProvider aiProvider) {
    this.contextService = contextService;
    this.promptBuilder = promptBuilder;
    this.aiProvider = aiProvider;
  }

  public AcademyAiSuggestion generate(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      AcademyAiTaskType task,
      String target,
      String currentValue) {
    if (task == null) {
      throw new IllegalArgumentException("task가 필요합니다.");
    }
    Set<String> allowed = ALLOWED_TARGETS.get(task);
    if (target == null || allowed == null || !allowed.contains(target)) {
      throw new IllegalArgumentException("이 작업(" + task + ")에는 허용되지 않는 target입니다: " + target);
    }
    String safeCurrentValue = currentValue == null ? "" : currentValue;
    if (safeCurrentValue.length() > MAX_CURRENT_VALUE_LENGTH) {
      throw new IllegalArgumentException(
          "currentValue가 너무 깁니다(" + MAX_CURRENT_VALUE_LENGTH + "자 제한).");
    }

    // academyId를 절대 파라미터로 받지 않는다 — principal 기준으로만 본인 Academy를 조회한다.
    AcademyAiContext context = contextService.buildContext(principal);
    String prompt = promptBuilder.build(task, context, target, safeCurrentValue);

    int maxOutputChars = MAX_OUTPUT_CHARS.getOrDefault(task, 200);
    AiCompletion completion = aiProvider.complete(prompt, new AiTaskLimits(maxOutputChars));
    if (!completion.ok()) {
      throw new AcademyAiGenerationException(
          completion.errorMessage() == null
              ? "AI 제안을 생성하지 못했습니다."
              : completion.errorMessage());
    }

    String suggestedValue = sanitize(completion.text());
    if (suggestedValue.isBlank()) {
      throw new AcademyAiGenerationException("AI가 빈 응답을 반환했습니다.");
    }
    if (suggestedValue.length() > maxOutputChars) {
      throw new AcademyAiGenerationException(
          "AI 응답이 허용 길이(" + maxOutputChars + "자)를 초과했습니다.");
    }

    return new AcademyAiSuggestion(task, target, safeCurrentValue, suggestedValue);
  }

  /** provider가 마크다운 코드펜스나 감싸는 따옴표를 덧붙이는 경우를 방어적으로 제거한다. */
  private static String sanitize(String text) {
    String trimmed = text == null ? "" : text.trim();
    trimmed = trimmed.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
    if (trimmed.length() >= 2
        && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
            || (trimmed.startsWith("“") && trimmed.endsWith("”")))) {
      trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
    }
    return trimmed;
  }
}
