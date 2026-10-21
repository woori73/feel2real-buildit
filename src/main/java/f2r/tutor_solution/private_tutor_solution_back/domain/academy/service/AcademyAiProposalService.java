package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiProposal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiProposal.Item;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiProposal.Type;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.EducatorReference;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Phase 5A — Academy 전체 진단 + 안전한 개선안(proposal) 묶음 생성.
 *
 * <p>{@link AcademyAiSuggestionService}(단일 target 다듬기)와 완전히 같은 파이프라인
 * (Context → Prompt → Provider)을 재사용하되, 응답이 <b>구조화된 JSON</b>이라는 점만 다르다.
 * 이 서비스가 하는 일:
 * <ol>
 *   <li>본인 Academy context 조립(academyId 파라미터 없음 — principal 기준).</li>
 *   <li>{@code ACADEMY_COMPOSE} 프롬프트로 기존 Wiki AI Router(provider=auto) 호출.</li>
 *   <li>응답 JSON 파싱 — 실패하면 {@link AcademyAiGenerationException}(Academy는 안 건드림).</li>
 *   <li>proposal별 allowlist 검증 — 통과한 것만 담고, 실패한 것은 조용히 drop(전체 실패 아님).</li>
 * </ol>
 *
 * <p>절대 원칙(Phase 4와 동일): 이 서비스는 어떤 경우에도 {@code Academy}/{@code builderDraft}를
 * 수정하지 않는다. "적용"은 사용자가 Builder에서 눌러야 하고, 그때도 기존
 * {@code PUT /api/tutor/academy} 경로로만 반영된다 — 여기엔 "적용" 엔드포인트가 없다.
 */
@Service
public class AcademyAiProposalService {

  /**
   * TEXT proposal이 건드릴 수 있는 builderDraft key. {@link AcademyAiContextService#EDITABLE_TEXT_TARGETS}와
   * 동일하게 유지한다(프롬프트에 노출되는 목록과 실제 검증 목록이 갈라지면 안 된다).
   */
  static final Set<String> ALLOWED_TEXT_TARGETS =
      Set.copyOf(AcademyAiContextService.EDITABLE_TEXT_TARGETS);

  /** 문단형(길게 허용) target — 나머지는 카드/슬로건이라 짧게 강제한다. */
  private static final Set<String> LONG_TEXT_TARGETS = Set.of(
      "principalDesc", "principalQuote", "introMotto", "introBodySecondary",
      "featuredDescription", "philosophyTitle", "ctaSubtitle");

  private static final int LONG_MAX_CHARS = 240;
  private static final int SHORT_MAX_CHARS = 90;
  private static final int MAX_INSTRUCTIONS_LENGTH = 200;
  private static final int MAX_PROPOSALS = 6;
  private static final int MAX_OUTPUT_CHARS = 4000;

  private final AcademyAiContextService contextService;
  private final AcademyAiPromptBuilder promptBuilder;
  private final AiProvider aiProvider;
  private final EducatorKnowledgeService educatorKnowledge;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AcademyAiProposalService(
      AcademyAiContextService contextService,
      AcademyAiPromptBuilder promptBuilder,
      AiProvider aiProvider,
      EducatorKnowledgeService educatorKnowledge) {
    this.contextService = contextService;
    this.promptBuilder = promptBuilder;
    this.aiProvider = aiProvider;
    this.educatorKnowledge = educatorKnowledge;
  }

  public AcademyAiProposal generate(
      JwtTokenProvider.AuthenticatedPrincipal principal, String instructions) {
    String safeInstructions = instructions == null ? "" : instructions.trim();
    if (safeInstructions.length() > MAX_INSTRUCTIONS_LENGTH) {
      throw new IllegalArgumentException(
          "요구사항이 너무 깁니다(" + MAX_INSTRUCTIONS_LENGTH + "자 제한).");
    }

    AcademyAiContext context = contextService.buildContext(principal);
    // Chart 교육자 참고 사례(다른 교육자 원문 — 의뢰 학원 사실이 아님). 없으면 빈 목록 = 기존 프롬프트 그대로.
    List<EducatorReference> references = educatorKnowledge.search(context, safeInstructions);
    String prompt = promptBuilder.build(
        AcademyAiTaskType.ACADEMY_COMPOSE, context, null, null, safeInstructions, references);

    AiCompletion completion = aiProvider.complete(prompt, new AiTaskLimits(MAX_OUTPUT_CHARS));
    if (!completion.ok()) {
      throw new AcademyAiGenerationException(
          completion.errorMessage() == null
              ? "AI 분석을 생성하지 못했습니다."
              : completion.errorMessage());
    }

    JsonNode root = parseJson(completion.text());
    String diagnosis = root.path("diagnosis").asText("").trim();
    if (diagnosis.isBlank()) {
      throw new AcademyAiGenerationException("AI 분석 결과가 비어 있습니다.");
    }

    List<String> canonicalOrder = AcademyService.DEFAULT_SECTION_ORDER;
    Set<String> nonEditableSections = context.composition() == null
        ? Set.of()
        : context.composition().sections().stream()
            .filter(s -> !s.editable())
            .map(AcademyAiContext.Composition.SectionState::key)
            .collect(java.util.stream.Collectors.toSet());

    List<Item> items = new ArrayList<>();
    JsonNode proposals = root.path("proposals");
    if (proposals.isArray()) {
      int index = 0;
      for (JsonNode node : proposals) {
        if (items.size() >= MAX_PROPOSALS) break;
        Item item = parseProposal(node, ++index, canonicalOrder, nonEditableSections);
        if (item != null) {
          items.add(item);
        }
      }
    }

    return new AcademyAiProposal(diagnosis, List.copyOf(items));
  }

  private JsonNode parseJson(String raw) {
    String text = raw == null ? "" : raw.trim();
    // provider가 코드펜스로 감싸는 경우 방어적으로 제거한다.
    text = text.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
    // 앞뒤에 설명 문장이 붙어도 첫 '{'부터 마지막 '}'까지만 시도한다.
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start >= 0 && end > start) {
      text = text.substring(start, end + 1);
    }
    try {
      JsonNode node = objectMapper.readTree(text);
      if (node == null || !node.isObject()) {
        throw new AcademyAiGenerationException("AI 응답을 이해하지 못했습니다.");
      }
      return node;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new AcademyAiGenerationException("AI 응답을 이해하지 못했습니다.");
    }
  }

  /** 한 proposal을 검증한다. 규칙을 하나라도 어기면 {@code null}(그 항목만 drop). */
  private Item parseProposal(
      JsonNode node, int index, List<String> canonicalOrder, Set<String> nonEditableSections) {
    if (node == null || !node.isObject()) return null;
    Type type = parseType(node.path("type").asText(""));
    if (type == null) return null;

    String id = blankTo(node.path("id").asText(""), "p" + index);
    String title = node.path("title").asText("").trim();
    String current = node.path("current").asText("").trim();
    String suggestion = node.path("suggestion").asText("").trim();
    if (title.isBlank() || suggestion.isBlank()) return null;
    boolean aiSafe = node.path("safeToApply").asBoolean(false);

    return switch (type) {
      case TEXT -> {
        String target = node.path("target").asText("").trim();
        if (!ALLOWED_TEXT_TARGETS.contains(target)) yield null;
        String value = node.path("suggestedValue").asText("").trim();
        // provider가 따옴표로 감싸는 경우 제거.
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
          value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isBlank()) yield null;
        int max = LONG_TEXT_TARGETS.contains(target) ? LONG_MAX_CHARS : SHORT_MAX_CHARS;
        if (value.length() > max) yield null; // 자르지 않고 통째로 거부(어중간한 문장 방지)
        yield new Item(id, Type.TEXT, title, current, suggestion, target, value, null, aiSafe);
      }
      case SECTION_ORDER -> {
        List<String> order = readStringArray(node.path("suggestedValue"));
        if (!isValidSectionOrder(order, canonicalOrder)) yield null;
        // [AI 변경 금지] 섹션의 위치가 바뀌면 거부한다.
        for (String locked : nonEditableSections) {
          if (canonicalOrder.indexOf(locked) != order.indexOf(locked)) yield null;
        }
        if (order.equals(canonicalOrder)) yield null; // 순서가 그대로면 제안 의미 없음
        yield new Item(id, Type.SECTION_ORDER, title, current, suggestion, null, null,
            List.copyOf(order), aiSafe);
      }
    };
  }

  private static Type parseType(String raw) {
    try {
      return Type.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private List<String> readStringArray(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<String> out = new ArrayList<>();
    for (JsonNode element : node) {
      if (!element.isTextual()) return List.of();
      out.add(element.asText());
    }
    return out;
  }

  /** 기존 15개 섹션의 재배열인지 — 개수 일치, 집합 동일, sec-hero 0번. */
  private static boolean isValidSectionOrder(List<String> order, List<String> canonicalOrder) {
    if (order.size() != canonicalOrder.size()) return false;
    if (order.isEmpty() || !"sec-hero".equals(order.get(0))) return false;
    LinkedHashSet<String> unique = new LinkedHashSet<>(order);
    if (unique.size() != order.size()) return false;
    return unique.containsAll(canonicalOrder);
  }

  private static String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
