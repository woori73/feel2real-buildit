package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiCreateDtos.CreateDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * P1 AI 홈페이지 최초 생성 서비스 (Muse 담당).
 *
 * <p>자연어 요청을 분석하여 P0의 5종 템플릿 중 하나를 선택하고,
 * 초기 builderDraft를 생성한다.
 *
 * <p>고도화 (2일차):
 * <ul>
 *   <li>{@link AcademyRequirementAnalyzer}로 요구사항 구조화
 *   <li>학원 소개·교육 철학·강점 문구 생성
 *   <li>허위 정보 방지: 요청에 없는 경력·성적·실적은 생성하지 않음
 * </ul>
 *
 * <p>보안:
 * <ul>
 *   <li>생성된 초안은 DB에 저장하지 않는다 (승인 전).
 *   <li>기존 학원 홈페이지 및 데이터를 덮어쓰지 않는다.
 *   <li>designPreset은 allowlist의 5종만 허용한다.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AcademyAiCreateService {

  private final AiProvider aiProvider;
  private final AcademyRequirementAnalyzer analyzer;

  private static final Set<String> VALID_PRESETS =
      Set.of("EXAM", "ENGLISH", "ARTS", "STUDY", "ONLINE");

  /**
   * 자연어 요청으로부터 홈페이지 초안을 생성한다.
   * DB를 수정하지 않는다.
   */
  public CreateDraft generateDraft(String message, String academyName) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("요청 내용을 입력해주세요.");
    }

    // 1. 요구사항 분석
    AcademyRequirementAnalyzer.AcademyRequirements req = analyzer.analyze(message);
    String preset = req.preset();

    Map<String, String> draft = new HashMap<>();
    List<String> warnings = new ArrayList<>();

    // 2. 프리셋 설정
    draft.put("designPreset", preset);

    // 3. 학원명 설정
    if (academyName != null && !academyName.isBlank()) {
      draft.put("academyName", academyName.trim());
    }

    // 4. 대상 설정
    if (req.target() != null) {
      draft.put("targetGroups", req.target());
    }

    // 5. AI로 홈페이지 콘텐츠 생성
    Map<String, String> content = generateHomepageContent(req, academyName, message);
    draft.putAll(content);

    // 6. 명시적 값 반영 (허위 생성 방지: 요청에 있는 값만)
    for (Map.Entry<String, String> entry : req.explicitValues().entrySet()) {
      // allowlist에 있는 필드만
      if (isAllowedDraftField(entry.getKey())) {
        draft.put(entry.getKey(), entry.getValue());
      }
    }

    // 7. CTA 설정
    if (req.emphases().stream().anyMatch(e -> e.contains("상담"))
        || message.contains("상담") || message.contains("신청") || message.contains("버튼")) {
      draft.put("ctaEnabled", "true");
    }

    String summary = String.format(
        "'%s' 템플릿으로 홈페이지를 생성합니다. %d개 항목이 설정되었습니다.",
        getPresetDisplayName(preset), draft.size());

    // 색상 요청이 있었다면 안내 추가
    if (detectColorRequest(message)) {
      warnings.add("요청하신 색상은 '" + getPresetDisplayName(preset)
          + "' 프리셋의 기본 색상을 사용합니다. "
          + "색상 변경은 Builder의 브랜드 탭에서 직접 조정해주세요.");
    }

    return new CreateDraft(preset, draft, summary, warnings);
  }

  /**
   * AI로 홈페이지 콘텐츠를 생성한다.
   * 허위 정보 방지 프롬프트를 사용한다.
   */
  private Map<String, String> generateHomepageContent(
      AcademyRequirementAnalyzer.AcademyRequirements req,
      String academyName,
      String originalMessage) {
    Map<String, String> content = new HashMap<>();

    if (!aiProvider.isReady()) {
      // AI 미준비 시 기본 템플릿 콘텐츠
      return generateFallbackContent(req, academyName);
    }

    String prompt = buildContentPrompt(req, academyName, originalMessage);
    AiCompletion result = aiProvider.complete(prompt, new AiTaskLimits(2000));

    if (!result.ok() || result.text().isBlank()) {
      return generateFallbackContent(req, academyName);
    }

    // JSON 파싱 시도
    Map<String, String> parsed = parseContentJson(result.text());
    if (parsed.isEmpty()) {
      return generateFallbackContent(req, academyName);
    }

    // allowlist 검증
    for (Map.Entry<String, String> entry : parsed.entrySet()) {
      if (isAllowedDraftField(entry.getKey()) && isSafeContent(entry.getValue())) {
        content.put(entry.getKey(), sanitize(entry.getValue()));
      }
    }

    return content;
  }

  /**
   * 허위 정보 방지 프롬프트를 구성한다.
   */
  private String buildContentPrompt(
      AcademyRequirementAnalyzer.AcademyRequirements req,
      String academyName,
      String originalMessage) {
    StringBuilder sb = new StringBuilder();
    sb.append("학원 홈페이지의 초기 콘텐츠를 JSON 형식으로 생성해주세요.\n\n");
    sb.append("## 학원 정보\n");
    sb.append("- 학원명: ").append(academyName != null ? academyName : "미정").append("\n");
    if (req.hasLocation()) {
      sb.append("- 지역: ").append(req.location()).append("\n");
    }
    if (req.target() != null) {
      sb.append("- 대상: ").append(req.target()).append("\n");
    }
    if (req.subject() != null) {
      sb.append("- 과목/분야: ").append(req.subject()).append("\n");
    }
    if (req.hasStrengths()) {
      sb.append("- 강점: ").append(String.join(", ", req.strengths())).append("\n");
    }
    if (!req.emphases().isEmpty()) {
      sb.append("- 강조점: ").append(String.join(", ", req.emphases())).append("\n");
    }
    sb.append("- 톤: ").append(req.tone()).append("\n");
    sb.append("- 원본 요청: ").append(originalMessage).append("\n\n");

    sb.append("## 절대 규칙 (허위 정보 방지)\n");
    sb.append("1. 요청에 없는 구체적 숫자는 절대 생성하지 마세요 "
        + "(예: '10년 경력', '95% 향상', '100명 합격').\n");
    sb.append("2. 요청에 없는 수상 실적·합격 실적·강사 경력을 지어내지 마세요.\n");
    sb.append("3. 일반적인 표현만 사용하세요 "
        + "(예: '체계적인 커리큘럼', '개별 맞춤 관리').\n\n");

    sb.append("## 출력 형식 (JSON만 출력)\n");
    sb.append("{\n");
    sb.append("  \"intro\": \"학원 소개 2-3문장\",\n");
    sb.append("  \"introHeading\": \"메인 헤드라인 (15자 이내)\",\n");
    sb.append("  \"introMotto\": \"학원 모토 (20자 이내)\",\n");
    sb.append("  \"philosophyItem1\": \"교육 철학 1\",\n");
    sb.append("  \"philosophyItem2\": \"교육 철학 2\"\n");
    sb.append("}\n");
    sb.append("JSON만 출력하고 다른 설명은 붙이지 마세요.");

    return sb.toString();
  }

  /**
   * AI 미준비 시 기본 콘텐츠를 생성한다.
   */
  private Map<String, String> generateFallbackContent(
      AcademyRequirementAnalyzer.AcademyRequirements req,
      String academyName) {
    Map<String, String> content = new HashMap<>();
    String name = academyName != null ? academyName : "학원";

    StringBuilder intro = new StringBuilder();
    if (req.hasLocation()) {
      intro.append(req.location()).append("에 위치한 ");
    }
    if (req.target() != null) {
      intro.append(req.target()).append(" 전문 ");
    }
    if (req.subject() != null) {
      intro.append(req.subject()).append(" ");
    }
    intro.append(name).append("입니다. ");
    if (req.hasStrengths()) {
      intro.append(String.join(", ", req.strengths())).append("에 강점을 가지고 있습니다.");
    } else {
      intro.append("체계적인 커리큘럼으로 학생들을 지도합니다.");
    }
    content.put("intro", intro.toString());

    if (req.target() != null && req.subject() != null) {
      content.put("introHeading",
          req.target() + " " + req.subject() + " 전문");
    }
    content.put("introMotto", "믿고 맡길 수 있는 " + name);

    if (req.hasStrengths()) {
      content.put("philosophyItem1", req.strengths().get(0) + " 중심의 체계적인 학습 관리");
      if (req.strengths().size() > 1) {
        content.put("philosophyItem2", req.strengths().get(1) + "을 통한 실력 향상");
      }
    }

    return content;
  }

  /**
   * AI 응답 JSON을 파싱한다.
   */
  private Map<String, String> parseContentJson(String text) {
    Map<String, String> result = new HashMap<>();
    try {
      String json = extractJson(text);
      // 간단한 JSON 파싱 (Jackson 없이)
      Pattern pattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");
      Matcher m = pattern.matcher(json);
      while (m.find()) {
        result.put(m.group(1), m.group(2));
      }
    } catch (Exception e) {
      // 파싱 실패 시 빈 맵
    }
    return result;
  }

  private String extractJson(String text) {
    String trimmed = text.trim();
    int start = trimmed.indexOf("{");
    int end = trimmed.lastIndexOf("}");
    if (start >= 0 && end > start) {
      return trimmed.substring(start, end + 1);
    }
    return trimmed;
  }

  /**
   * 생성된 콘텐츠가 안전한지 검증한다.
   * 허위 정보 패턴을 감지한다.
   */
  private boolean isSafeContent(String value) {
    if (value == null || value.isBlank()) return false;
    String lower = value.toLowerCase();

    // 허위 정보 패턴 감지
    // 구체적 숫자 + 성과 표현 (요청에 없는 경우)
    if (Pattern.compile("\\d+(년|%|점|명)\\s*(경력|향상|합격|수상)").matcher(value).find()) {
      // 명시적 값이 아닌 경우 거부 (보수적 접근)
      return false;
    }

    // 위험한 콘텐츠
    return !containsDangerousContent(value);
  }

  private boolean isAllowedDraftField(String key) {
    return Set.of(
        "intro", "introHeading", "introMotto", "introBodySecondary",
        "principalDesc", "principalCareer", "principalQuote",
        "philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4",
        "monthlyTuition", "scoreImprovement", "operationYears",
        "ctaPhone", "ctaKakaoUrl"
    ).contains(key);
  }

  private boolean detectColorRequest(String message) {
    String lower = message.toLowerCase();
    return lower.contains("파란색") || lower.contains("빨간색")
        || lower.contains("초록색") || lower.contains("색상")
        || lower.contains("블루") || lower.contains("컬러");
  }

  private boolean containsDangerousContent(String value) {
    String lower = value.toLowerCase();
    return lower.contains("<script")
        || lower.contains("javascript:")
        || lower.contains("onerror=")
        || lower.contains("onload=");
  }

  private String getPresetDisplayName(String preset) {
    return switch (preset) {
      case "EXAM" -> "입시학원형";
      case "ENGLISH" -> "영어회화형";
      case "ARTS" -> "예체능형";
      case "STUDY" -> "공부방형";
      case "ONLINE" -> "온라인학원형";
      default -> preset;
    };
  }

  private String sanitize(String text) {
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
    return result.trim();
  }
}
