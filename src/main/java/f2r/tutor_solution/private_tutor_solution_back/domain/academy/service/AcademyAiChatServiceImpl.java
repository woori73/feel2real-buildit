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
   * 자연어 키워드 → builderDraft 필드 매핑.
   * 1일차는 키워드 기반으로 동작하고, 점진적으로 개선한다.
   */
  private static final Map<String, List<String>> KEYWORD_FIELD_MAP = Map.ofEntries(
      Map.entry("수강료", List.of("monthlyTuition")),
      Map.entry("월 수강료", List.of("monthlyTuition")),
      Map.entry("가격", List.of("monthlyTuition")),
      Map.entry("소개", List.of("intro")),
      Map.entry("학원 소개", List.of("intro", "heroAccent")),
      Map.entry("원장", List.of("principalDesc")),
      Map.entry("원장 소개", List.of("principalDesc", "principalQuote")),
      Map.entry("약력", List.of("principalCareer1", "principalCareer2", "principalCareer3")),
      Map.entry("철학", List.of("philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4")),
      Map.entry("교육 철학", List.of("philosophyItem1", "philosophyItem2")),
      Map.entry("성적", List.of("scoreImprovement")),
      Map.entry("향상률", List.of("scoreImprovement")),
      Map.entry("운영", List.of("operationYears")),
      Map.entry("경력", List.of("operationYears")),
      Map.entry("전화", List.of("ctaPhone")),
      Map.entry("연락처", List.of("ctaPhone")),
      Map.entry("카카오", List.of("ctaKakaoUrl")),
      Map.entry("카톡", List.of("ctaKakaoUrl")),
      Map.entry("색상", List.of("brandPrimary", "brandAccent")),
      Map.entry("브랜드", List.of("brandPrimary", "brandAccent")));

  /** AI가 수정할 수 있는 필드 (컨트롤러의 allowlist와 동일). */
  private static final Set<String> ALLOWED_FIELDS = Set.of(
      "intro", "heroAccent",
      "principalDesc", "principalCareer1", "principalCareer2", "principalCareer3",
      "principalQuote",
      "philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4",
      "monthlyTuition", "scoreImprovement", "operationYears",
      "ctaPhone", "ctaKakaoUrl",
      "brandPrimary", "brandAccent");

  @Override
  public ChatProposal proposeChanges(AcademyAiContext context, String userMessage) {
    if (userMessage == null || userMessage.isBlank()) {
      return new ChatProposal(List.of(), "요청 내용을 이해하지 못했습니다.", newSessionId());
    }

    // 1. 키워드 기반 필드 매핑
    List<String> targetFields = mapKeywordsToFields(userMessage);
    if (targetFields.isEmpty()) {
      return new ChatProposal(
          List.of(),
          "어떤 항목을 수정할지 파악하지 못했습니다. 예: '수강료를 15만원으로 바꿔줘', '원장 소개를 다듬어줘'",
          newSessionId());
    }

    // 2. 각 필드에 대해 AI로 수정안 생성
    List<FieldChange> changes = new ArrayList<>();
    for (String field : targetFields) {
      String currentValue = getCurrentValue(context, field);
      String newValue = generateNewValue(field, currentValue, userMessage);
      if (newValue != null && !newValue.equals(currentValue)) {
        changes.add(new FieldChange(field, currentValue, newValue));
      }
    }

    String summary = changes.isEmpty()
        ? "변경할 내용을 생성하지 못했습니다."
        : changes.size() + "개 항목의 수정을 제안합니다.";

    return new ChatProposal(changes, summary, newSessionId());
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
   */
  private String generateNewValue(String field, String currentValue, String userMessage) {
    // 숫자 필드는 메시지에서 직접 추출 시도
    if (isNumericField(field)) {
      String extracted = extractNumericValue(userMessage);
      if (extracted != null) {
        return extracted;
      }
    }

    // AI Provider로 텍스트 생성
    if (!aiProvider.isReady()) {
      return null;
    }

    String prompt = buildPrompt(field, currentValue, userMessage);
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

  private String buildPrompt(String field, String currentValue, String userMessage) {
    return String.format(
        "학원 홈페이지의 '%s' 항목을 수정해주세요.\n"
            + "현재 값: %s\n"
            + "고객 요청: %s\n"
            + "자연스럽고 전문적인 문체로 수정안을 제시해주세요. "
            + "과장 광고나 근거 없는 표현은 사용하지 마세요. "
            + "수정된 값만 출력하세요.",
        field, currentValue, userMessage);
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
