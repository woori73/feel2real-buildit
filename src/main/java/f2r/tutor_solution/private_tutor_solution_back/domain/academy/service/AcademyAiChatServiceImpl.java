package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ChatProposal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.FieldChange;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ValidationResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * P1 AI 채팅 서비스 구현 (Muse 담당).
 *
 * <p>자연어 요청을 분석하여 수정안을 생성한다.
 * 1일차 구현: 키워드 기반 필드 매핑 + AiProvider를 통한 텍스트 생성.
 *
 * <p>보안: 이 서비스는 DB를 직접 수정하지 않는다. 제안 생성과 검증만 수행한다.
 */
@Service
@RequiredArgsConstructor
public class AcademyAiChatServiceImpl implements AcademyAiChatService {

  private final AiProvider aiProvider;
  private final AcademyAiContextService contextService;

  /**
   * 톤/스타일 수정 키워드 → 지시 매핑.
   * 예: "소개글을 좀 더 짧게" → intro 필드를 짧게
   */
  private static final Map<String, ToneInstruction> TONE_INSTRUCTIONS = Map.ofEntries(
      Map.entry("짧게", new ToneInstruction("더 간결하게", "핵심만 담아 1-2문장으로 줄여주세요")),
      Map.entry("길게", new ToneInstruction("더 자세하게", "구체적인 내용을 추가해 3-4문장으로 늘려주세요")),
      Map.entry("신뢰", new ToneInstruction("신뢰감 있게", "학부모가 신뢰할 수 있는 전문적이고 안정적인 표현으로")),
      Map.entry("친근", new ToneInstruction("친근하게", "따뜻하고 친근한 말투로")),
      Map.entry("전문적", new ToneInstruction("전문적으로", "전문적이고 권위 있는 표현으로")),
      Map.entry("부드럽", new ToneInstruction("부드럽게", "부드럽고 정중한 표현으로")),
      Map.entry("강조", new ToneInstruction("강조해서", "핵심 메시지를 더 강하게 어필하도록")));

  private record ToneInstruction(String label, String instruction) {}

  /**
   * 자연어 키워드 → builderDraft 필드 매핑.
   * 2일차: 톤 조절, 다중 필드, 문맥 참조 지원.
   */
  private static final Map<String, List<String>> KEYWORD_FIELD_MAP = Map.ofEntries(
      Map.entry("수강료", List.of("monthlyTuition")),
      Map.entry("월 수강료", List.of("monthlyTuition")),
      Map.entry("가격", List.of("monthlyTuition")),
      Map.entry("소개글", List.of("intro", "introHeading", "introMotto")),
      Map.entry("소개", List.of("intro", "introHeading", "introMotto")),
      Map.entry("학원 소개", List.of("intro", "introHeading", "introMotto", "introBodySecondary")),
      Map.entry("헤드라인", List.of("introHeading")),
      Map.entry("모토", List.of("introMotto")),
      Map.entry("원장", List.of("principalDesc")),
      Map.entry("원장 소개", List.of("principalDesc", "principalQuote")),
      Map.entry("약력", List.of("principalCareer")),
      Map.entry("철학", List.of("philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4")),
      Map.entry("교육 철학", List.of("philosophyItem1", "philosophyItem2")),
      Map.entry("성적", List.of("scoreImprovement")),
      Map.entry("향상률", List.of("scoreImprovement")),
      Map.entry("운영", List.of("operationYears")),
      Map.entry("경력", List.of("operationYears")),
      Map.entry("전화", List.of("ctaPhone")),
      Map.entry("연락처", List.of("ctaPhone")),
      Map.entry("카카오", List.of("ctaKakaoUrl")),
      Map.entry("카톡", List.of("ctaKakaoUrl")));

  /** AI가 수정할 수 있는 필드 (컨트롤러의 allowlist와 동일). */
  private static final Set<String> ALLOWED_FIELDS = Set.of(
      "principalDesc", "principalCareer",
      "principalQuote",
      "philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4",
      "monthlyTuition", "scoreImprovement", "operationYears",
      "ctaPhone", "ctaKakaoUrl",
      "introHeading", "introMotto", "introBodySecondary",
      "intro");

  @Override
  public ChatProposal proposeChanges(AcademyAiContext context, String userMessage) {
    if (userMessage == null || userMessage.isBlank()) {
      return new ChatProposal(List.of(), "요청 내용을 이해하지 못했습니다.", newSessionId());
    }

    // 1. 키워드 기반 필드 매핑 (여러 항목 동시 지원)
    List<String> targetFields = mapKeywordsToFields(userMessage);
    if (targetFields.isEmpty()) {
      return new ChatProposal(
          List.of(),
          buildUnsupportedGuidance(userMessage),
          newSessionId());
    }

    // 2. 톤/스타일 지시 감지
    ToneInstruction toneInstruction = detectToneInstruction(userMessage);

    // 3. 각 필드에 대해 수정안 생성
    List<FieldChange> changes = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    for (String field : targetFields) {
      String currentValue = getCurrentValue(context, field);
      String newValue = generateNewValue(field, currentValue, userMessage, toneInstruction);
      if (newValue != null && !newValue.equals(currentValue)) {
        // 값 형식 검증
        String validationError = validateFieldValue(field, newValue);
        if (validationError != null) {
          skipped.add(field + ": " + validationError);
        } else {
          changes.add(new FieldChange(field, currentValue, newValue));
        }
      }
    }

    String summary = buildSummary(changes, skipped, toneInstruction);

    return new ChatProposal(changes, summary, newSessionId());
  }

  /**
   * 톤/스타일 수정 지시를 감지한다.
   * 예: "소개글을 좀 더 짧게" → ToneInstruction("더 간결하게", ...)
   */
  private ToneInstruction detectToneInstruction(String userMessage) {
    String lower = userMessage.toLowerCase();
    for (Map.Entry<String, ToneInstruction> entry : TONE_INSTRUCTIONS.entrySet()) {
      if (lower.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * 지원하지 않는 요청에 대한 명확한 안내.
   */
  private String buildUnsupportedGuidance(String userMessage) {
    String lower = userMessage.toLowerCase();
    if (lower.contains("삭제") || lower.contains("없애")) {
      return "삭제 기능은 현재 지원하지 않습니다. 수정이 필요한 항목을 알려주세요.";
    }
    if (lower.contains("섹션") || lower.contains("추가") && lower.contains("페이지")) {
      return "새 섹션 추가는 본부 승인이 필요합니다. 기존 항목의 수정을 요청해주세요.";
    }
    if (lower.contains("디자인") || lower.contains("템플릿") || lower.contains("프리셋")) {
      return "디자인 프리셋 변경은 Builder의 브랜드 탭에서 직접 선택해주세요. "
          + "색상 조정은 '브랜드 색상을 #FF0000으로 바꿔줘'와 같이 요청할 수 있습니다.";
    }
    return "어떤 항목을 수정할지 파악하지 못했습니다.\n"
        + "예시:\n"
        + "- '수강료를 15만원으로 바꿔줘'\n"
        + "- '원장 소개를 더 친근하게 다듬어줘'\n"
        + "- '교육 철학 1번을 바꿔줘'\n"
        + "- '전화번호를 02-1234-5678로 바꿔줘'";
  }

  /**
   * 필드별 값 형식을 검증한다.
   */
  private String validateFieldValue(String field, String value) {
    if (value == null || value.isBlank()) {
      return "값이 비어 있습니다.";
    }
    // 전화번호 필드: 기본 형식 검증
    if (field.equals("ctaPhone")) {
      if (!value.matches("^[0-9\\-\\(\\)\\s+]+$")) {
        return "전화번호 형식이 올바르지 않습니다.";
      }
    }
    // URL 필드: 기본 형식 검증
    if (field.equals("ctaKakaoUrl")) {
      if (!value.startsWith("http://") && !value.startsWith("https://")) {
        return "URL은 http:// 또는 https://로 시작해야 합니다.";
      }
    }
    // 숫자 필드: 숫자 포함 확인
    if (field.equals("monthlyTuition") || field.equals("scoreImprovement")
        || field.equals("operationYears")) {
      if (!value.matches(".*\\d.*")) {
        return "숫자가 포함되어야 합니다.";
      }
    }
    return null;
  }

  private String buildSummary(
      List<FieldChange> changes, List<String> skipped, ToneInstruction tone) {
    if (changes.isEmpty() && skipped.isEmpty()) {
      return "변경할 내용을 생성하지 못했습니다.";
    }
    StringBuilder sb = new StringBuilder();
    if (!changes.isEmpty()) {
      sb.append(changes.size()).append("개 항목의 수정을 제안합니다.");
      if (tone != null) {
        sb.append(" (").append(tone.label()).append(")");
      }
    }
    if (!skipped.isEmpty()) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append("다음 항목은 건너뜀: ").append(String.join(", ", skipped));
    }
    return sb.toString();
  }

  private String buildSummary(List<FieldChange> changes, List<String> skipped) {
    return buildSummary(changes, skipped, null);
  }

  @Override
  public ValidationResult validateProposal(ChatProposal proposal) {
    if (proposal == null || proposal.changes() == null || proposal.changes().isEmpty()) {
      return new ValidationResult(false, List.of("수정안이 비어 있습니다."));
    }

    List<String> errors = new ArrayList<>();
    for (FieldChange change : proposal.changes()) {
      if (!ALLOWED_FIELDS.contains(change.key())) {
        errors.add("허용되지 않은 필드: " + change.key());
      }
      if (change.after() != null && containsDangerousContent(change.after())) {
        errors.add("위험한 콘텐츠가 포함된 필드: " + change.key());
      }
      if (change.after() != null && change.after().length() > 2000) {
        errors.add("값이 너무 깁니다: " + change.key());
      }
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  private List<String> mapKeywordsToFields(String message) {
    List<String> fields = new ArrayList<>();
    String lower = message.toLowerCase();
    for (Map.Entry<String, List<String>> entry : KEYWORD_FIELD_MAP.entrySet()) {
      if (lower.contains(entry.getKey().toLowerCase())) {
        for (String field : entry.getValue()) {
          if (!fields.contains(field)) {
            fields.add(field);
          }
        }
      }
    }
    return fields;
  }

  private String getCurrentValue(AcademyAiContext context, String field) {
    return switch (field) {
      case "intro" -> nullToEmpty(context.intro());
      case "principalDesc" -> nullToEmpty(context.principalDesc());
      case "philosophyItem1" -> getPhilosophyItem(context, 0);
      case "philosophyItem2" -> getPhilosophyItem(context, 1);
      case "philosophyItem3" -> getPhilosophyItem(context, 2);
      case "philosophyItem4" -> getPhilosophyItem(context, 3);
      // builderDraft 필드는 context에 없으므로 빈 값으로 시작
      // (실제 값은 프론트엔드가 제공하거나, 2일차에 context 확장)
      default -> "";
    };
  }

  private String getPhilosophyItem(AcademyAiContext context, int index) {
    if (context.philosophyItems() != null && index < context.philosophyItems().size()) {
      return context.philosophyItems().get(index);
    }
    return "";
  }

  private String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  /**
   * AI Provider를 통해 수정안을 생성한다.
   * Provider가 준비되지 않았으면 원본 메시지를 파싱하여 직접 값을 추출한다.
   *
   * @param toneInstruction 톤/스타일 지시 (예: "짧게", "신뢰감 있게"). null 가능.
   */
  private String generateNewValue(
      String field, String currentValue, String userMessage, ToneInstruction toneInstruction) {
    // 숫자 필드는 메시지에서 직접 추출 시도
    if (isNumericField(field)) {
      String extracted = extractNumericValue(userMessage);
      if (extracted != null) {
        return extracted;
      }
    }

    // 톤 조절만 요청된 경우 (예: "소개글을 좀 더 짧게")
    // 현재 값이 있으면 AI로 스타일 변환
    if (toneInstruction != null && !currentValue.isBlank()) {
      return generateToneAdjustedValue(field, currentValue, toneInstruction);
    }

    // AI Provider로 텍스트 생성
    if (!aiProvider.isReady()) {
      return null;
    }

    String prompt = buildPrompt(field, currentValue, userMessage, toneInstruction);
    AiCompletion result = aiProvider.complete(prompt, new AiTaskLimits(500));

    if (!result.ok() || result.text().isBlank()) {
      return null;
    }

    return sanitize(result.text());
  }

  private String generateNewValue(String field, String currentValue, String userMessage) {
    return generateNewValue(field, currentValue, userMessage, null);
  }

  /**
   * 톤/스타일 지시에 따라 기존 값을 변환한다.
   */
  private String generateToneAdjustedValue(
      String field, String currentValue, ToneInstruction tone) {
    if (!aiProvider.isReady()) {
      return null;
    }

    String prompt = String.format(
        "다음 학원 홈페이지 문구를 '%s' 스타일로 다듬어주세요.\n"
            + "원본: %s\n"
            + "%s\n"
            + "수정된 문구만 출력하세요. 과장 광고는 사용하지 마세요.",
        tone.label(), currentValue, tone.instruction());

    AiCompletion result = aiProvider.complete(prompt, new AiTaskLimits(500));
    if (!result.ok() || result.text().isBlank()) {
      return null;
    }
    return sanitize(result.text());
  }

  private boolean isNumericField(String field) {
    return field.equals("monthlyTuition")
        || field.equals("scoreImprovement")
        || field.equals("operationYears");
  }

  private String extractNumericValue(String message) {
    // "15만원", "15만", "150,000원" 등의 패턴에서 숫자 추출
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("(\\d[\\d,]*)(만)?원?").matcher(message);
    if (m.find()) {
      String num = m.group(1).replace(",", "");
      String man = m.group(2);
      if ("만".equals(man)) {
        return num + "만원";
      }
      return num;
    }
    return null;
  }

  private String buildPrompt(
      String field, String currentValue, String userMessage, ToneInstruction tone) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format(
        "학원 홈페이지의 '%s' 항목을 수정해주세요.\n"
            + "현재 값: %s\n"
            + "고객 요청: %s\n",
        field, currentValue, userMessage));
    if (tone != null) {
      sb.append("스타일 지시: ").append(tone.instruction()).append("\n");
    }
    sb.append("자연스럽고 전문적인 문체로 수정안을 제시해주세요. "
        + "과장 광고나 근거 없는 표현은 사용하지 마세요. "
        + "수정된 값만 출력하세요.");
    return sb.toString();
  }

  private String buildPrompt(String field, String currentValue, String userMessage) {
    return buildPrompt(field, currentValue, userMessage, null);
  }

  private String sanitize(String text) {
    // 코드펜스, 따옴표 제거
    String result = text.trim();
    if (result.startsWith("```")) {
      int end = result.indexOf("\n");
      if (end > 0) {
        result = result.substring(end + 1);
      }
      if (result.endsWith("```")) {
        result = result.substring(0, result.length() - 3);
      }
      result = result.trim();
    }
    if ((result.startsWith("\"") && result.endsWith("\""))
        || (result.startsWith("'") && result.endsWith("'"))) {
      result = result.substring(1, result.length() - 1);
    }
    return result.trim();
  }

  private boolean containsDangerousContent(String value) {
    String lower = value.toLowerCase();
    return lower.contains("<script")
        || lower.contains("javascript:")
        || lower.contains("onerror=")
        || lower.contains("onload=")
        || lower.contains("onclick=");
  }

  private String newSessionId() {
    return UUID.randomUUID().toString();
  }
}
