package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiGuideSuggestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.EducatorReference;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Phase 5B — AI Guided Academy Builder의 문구 추천 파이프라인.
 *
 * <p>{@link AcademyAiSuggestionService}(단일 target 다듬기) · {@link AcademyAiProposalService}
 * (전체 진단)와 <b>완전히 같은 파이프라인</b>(Context → Prompt → Provider)을 재사용한다. 다른
 * 점은 딱 하나 — 응답이 "후보 문자열 배열"이라, 이 서비스가 JSON 배열을 파싱하고 후보별
 * target allowlist·길이를 검증한다는 것뿐이다.
 *
 * <p>절대 원칙(Phase 4/5A와 동일): 이 서비스는 어떤 경우에도 {@code Academy}/{@code builderDraft}
 * 를 수정하지 않는다. "적용"은 사용자가 Guide 패널에서 눌러야 하고, 그때도 기존
 * {@code PUT /api/tutor/academy} 경로로만 반영된다 — 여기엔 "적용" 엔드포인트가 없다.
 * Publish는 어떤 경로로도 자동 실행되지 않는다.
 */
@Service
public class AcademyAiGuideService {

  /**
   * Guide가 문구 추천을 만들 수 있는 target. {@link AcademyAiContextService#EDITABLE_TEXT_TARGETS}
   * (Phase 5A에서 "잘못 바뀌면 위험한" 값 — 학원명/원장 이름·직함/가격/연락처 — 을 이미 제외한
   * 목록)를 그대로 재사용하고, 히어로 소개 문장(top-level {@code intro}) 하나만 추가한다.
   * 여기 없는 target은 어떤 답변으로도 추천 요청을 만들 수 없다.
   */
  static final Set<String> ALLOWED_TARGETS;

  static {
    Set<String> targets = new LinkedHashSet<>(AcademyAiContextService.EDITABLE_TEXT_TARGETS);
    targets.add("intro");
    ALLOWED_TARGETS = Set.copyOf(targets);
  }

  /** 문단형(길게 허용) target — 나머지는 카드/제목이라 짧게 강제한다. */
  private static final Set<String> LONG_TARGETS = Set.of(
      "intro", "principalDesc", "principalQuote", "introMotto", "introBodySecondary",
      "featuredDescription", "philosophyTitle");

  private static final int LONG_MAX_CHARS = 220;
  private static final int SHORT_MAX_CHARS = 70;
  private static final int MAX_ANSWER_LENGTH = 400;
  private static final int MAX_STYLE_LENGTH = 200;
  private static final int MAX_CANDIDATES = 3;
  private static final int MAX_OUTPUT_CHARS = 1200;

  private final AcademyAiContextService contextService;
  private final AcademyAiPromptBuilder promptBuilder;
  private final AiProvider aiProvider;
  private final EducatorKnowledgeService educatorKnowledge;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AcademyAiGuideService(
      AcademyAiContextService contextService,
      AcademyAiPromptBuilder promptBuilder,
      AiProvider aiProvider,
      EducatorKnowledgeService educatorKnowledge) {
    this.contextService = contextService;
    this.promptBuilder = promptBuilder;
    this.aiProvider = aiProvider;
    this.educatorKnowledge = educatorKnowledge;
  }

  /**
   * @param target builderDraft key(또는 {@code intro}). allowlist 밖이면 {@link IllegalArgumentException}.
   * @param answer 사용자가 이 단계에서 답한 내용(강조점). 비어 있어도 된다.
   * @param style Guide 세션 초반에 정한 홈페이지 스타일/톤(선택).
   */
  public AcademyAiGuideSuggestion generate(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String target,
      String answer,
      String style) {
    String safeTarget = target == null ? "" : target.trim();
    if (!ALLOWED_TARGETS.contains(safeTarget)) {
      throw new IllegalArgumentException("Guide가 추천할 수 없는 영역입니다: " + target);
    }
    String safeAnswer = answer == null ? "" : answer.trim();
    if (safeAnswer.length() > MAX_ANSWER_LENGTH) {
      throw new IllegalArgumentException("답변이 너무 깁니다(" + MAX_ANSWER_LENGTH + "자 제한).");
    }
    String safeStyle = style == null ? "" : style.trim();
    if (safeStyle.length() > MAX_STYLE_LENGTH) {
      throw new IllegalArgumentException("스타일 설명이 너무 깁니다(" + MAX_STYLE_LENGTH + "자 제한).");
    }

    // academyId를 절대 파라미터로 받지 않는다 — principal 기준으로만 본인 Academy를 조회한다.
    AcademyAiContext context = contextService.buildContext(principal);
    String currentValue = currentValueFor(context, safeTarget);
    String instructions = composeInstructions(safeAnswer, safeStyle);
    // Chart 교육자 참고 사례(다른 교육자 원문 — 의뢰 학원 사실이 아님). 없으면 빈 목록 = 기존 프롬프트 그대로.
    List<EducatorReference> references = educatorKnowledge.search(context, instructions);
    String prompt = promptBuilder.build(
        AcademyAiTaskType.GUIDE_TEXT, context, safeTarget, currentValue, instructions, references);

    AiCompletion completion = aiProvider.complete(prompt, new AiTaskLimits(MAX_OUTPUT_CHARS));
    if (!completion.ok()) {
      throw new AcademyAiGenerationException(
          completion.errorMessage() == null
              ? "AI 추천을 생성하지 못했습니다."
              : completion.errorMessage());
    }

    List<String> candidates = parseCandidates(completion.text(), safeTarget);
    if (candidates.isEmpty()) {
      throw new AcademyAiGenerationException("AI가 사용할 만한 추천 문구를 만들지 못했습니다.");
    }
    return new AcademyAiGuideSuggestion(safeTarget, candidates);
  }

  private static String currentValueFor(AcademyAiContext context, String target) {
    if ("intro".equals(target)) {
      return context.intro() == null ? "" : context.intro();
    }
    // builderDraft 상세는 context에 담지 않으므로(§AcademyAiContext) 프롬프트에는 "현재 문구"를
    // 비워서 넘긴다 — Guide는 "새로 작성" 관점이라 현재 값이 필수는 아니다.
    return "";
  }

  private static String composeInstructions(String answer, String style) {
    StringBuilder sb = new StringBuilder();
    if (!style.isBlank()) {
      sb.append("홈페이지 스타일/톤: ").append(style);
    }
    if (!answer.isBlank()) {
      if (sb.length() > 0) {
        sb.append(" / ");
      }
      sb.append("이 영역에서 강조하려는 것: ").append(answer);
    }
    return sb.toString();
  }

  /** provider 응답에서 후보 배열을 뽑아 검증한다. 규칙을 어긴 후보만 drop(전체 실패 아님). */
  private List<String> parseCandidates(String raw, String target) {
    int max = LONG_TARGETS.contains(target) ? LONG_MAX_CHARS : SHORT_MAX_CHARS;
    JsonNode node = tryReadArray(raw);
    List<String> out = new ArrayList<>();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    if (node != null && node.isArray()) {
      for (JsonNode element : node) {
        if (out.size() >= MAX_CANDIDATES) break;
        if (!element.isTextual()) continue;
        String value = sanitize(element.asText());
        if (value.isBlank() || value.length() > max) continue;
        if (seen.add(value.toLowerCase(Locale.ROOT))) {
          out.add(value);
        }
      }
    }
    return List.copyOf(out);
  }

  private JsonNode tryReadArray(String raw) {
    String text = raw == null ? "" : raw.trim();
    text = text.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
    int start = text.indexOf('[');
    int end = text.lastIndexOf(']');
    if (start >= 0 && end > start) {
      text = text.substring(start, end + 1);
    }
    try {
      JsonNode node = objectMapper.readTree(text);
      return node != null && node.isArray() ? node : null;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return null;
    }
  }

  private static String sanitize(String text) {
    String trimmed = text == null ? "" : text.trim();
    if (trimmed.length() >= 2
        && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
            || (trimmed.startsWith("“") && trimmed.endsWith("”")))) {
      trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
    }
    return trimmed.replace("\r", " ").replace("\n", " ").replaceAll("\\s{2,}", " ").trim();
  }
}
