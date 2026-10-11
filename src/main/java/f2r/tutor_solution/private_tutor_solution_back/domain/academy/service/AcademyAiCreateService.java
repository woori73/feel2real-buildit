package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiCreateDtos.CreateDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * P1 AI 홈페이지 최초 생성 서비스 (Muse 담당).
 *
 * <p>자연어 요청을 분석하여 P0의 5종 템플릿 중 하나를 선택하고,
 * 초기 builderDraft를 생성한다.
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

  private static final Set<String> VALID_PRESETS =
      Set.of("EXAM", "ENGLISH", "ARTS", "STUDY", "ONLINE");

  /** 키워드 → 프리셋 매핑. */
  private static final Map<String, String> KEYWORD_PRESET_MAP = Map.ofEntries(
      Map.entry("입시", "EXAM"),
      Map.entry("내신", "EXAM"),
      Map.entry("수능", "EXAM"),
      Map.entry("영어회화", "ENGLISH"),
      Map.entry("회화", "ENGLISH"),
      Map.entry("영어", "ENGLISH"),
      Map.entry("미술", "ARTS"),
      Map.entry("음악", "ARTS"),
      Map.entry("예체능", "ARTS"),
      Map.entry("체육", "ARTS"),
      Map.entry("공부방", "STUDY"),
      Map.entry("독서실", "STUDY"),
      Map.entry("소규모", "STUDY"),
      Map.entry("온라인", "ONLINE"),
      Map.entry("인강", "ONLINE"),
      Map.entry("인터넷강의", "ONLINE"));

  /** 색상 키워드 → 브랜드 색상 매핑. */
  private static final Map<String, String> COLOR_MAP = Map.ofEntries(
      Map.entry("파란색", "#2b3a9e"),
      Map.entry("파랑", "#2b3a9e"),
      Map.entry("블루", "#2b3a9e"),
      Map.entry("빨간색", "#c0392b"),
      Map.entry("빨강", "#c0392b"),
      Map.entry("레드", "#c0392b"),
      Map.entry("초록색", "#27ae60"),
      Map.entry("초록", "#27ae60"),
      Map.entry("그린", "#27ae60"),
      Map.entry("노란색", "#f39c12"),
      Map.entry("노랑", "#f39c12"),
      Map.entry("보라색", "#8b5cf6"),
      Map.entry("보라", "#8b5cf6"),
      Map.entry("주황색", "#e67e22"),
      Map.entry("주황", "#e67e22"),
      Map.entry("분홍색", "#e91e63"),
      Map.entry("분홍", "#e91e63"));

  /**
   * 자연어 요청으로부터 홈페이지 초안을 생성한다.
   * DB를 수정하지 않는다.
   */
  public CreateDraft generateDraft(String message, String academyName) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("요청 내용을 입력해주세요.");
    }

    String preset = detectPreset(message);
    Map<String, String> draft = new HashMap<>();
    List<String> warnings = new ArrayList<>();

    // 1. 프리셋 설정
    draft.put("designPreset", preset);

    // 2. 색상 설정
    String color = detectColor(message);
    if (color != null) {
      if (isValidHexColor(color)) {
        draft.put("brandPrimary", color);
      } else {
        warnings.add("지원하지 않는 색상 형식입니다: " + color);
      }
    }

    // 3. 학원명 설정
    if (academyName != null && !academyName.isBlank()) {
      draft.put("academyName", academyName.trim());
    }

    // 4. 대상 연령대 감지
    String target = detectTarget(message);
    if (target != null) {
      draft.put("targetGroups", target);
    }

    // 5. AI로 소개문 생성 (선택적)
    String intro = generateIntro(message, academyName, preset);
    if (intro != null && !intro.isBlank()) {
      draft.put("intro", intro);
    }

    // 6. CTA 설정
    if (message.contains("상담") || message.contains("신청") || message.contains("버튼")) {
      draft.put("ctaEnabled", "true");
    }

    String summary = String.format(
        "'%s' 템플릿으로 홈페이지를 생성합니다. %d개 항목이 설정되었습니다.",
        getPresetDisplayName(preset), draft.size());

    return new CreateDraft(preset, draft, summary, warnings);
  }

  private String detectPreset(String message) {
    String lower = message.toLowerCase();
    for (Map.Entry<String, String> entry : KEYWORD_PRESET_MAP.entrySet()) {
      if (lower.contains(entry.getKey().toLowerCase())) {
        return entry.getValue();
      }
    }
    // 기본값: EXAM (가장 범용적)
    return "EXAM";
  }

  private String detectColor(String message) {
    String lower = message.toLowerCase();
    for (Map.Entry<String, String> entry : COLOR_MAP.entrySet()) {
      if (lower.contains(entry.getKey().toLowerCase())) {
        return entry.getValue();
      }
    }
    return null;
  }

  private String detectTarget(String message) {
    String lower = message.toLowerCase();
    if (lower.contains("중학생") || lower.contains("중등")) {
      return "중학생";
    }
    if (lower.contains("고등학생") || lower.contains("고등")) {
      return "고등학생";
    }
    if (lower.contains("초등학생") || lower.contains("초등")) {
      return "초등학생";
    }
    if (lower.contains("성인")) {
      return "성인";
    }
    return null;
  }

  private String generateIntro(String message, String academyName, String preset) {
    if (!aiProvider.isReady()) {
      return null;
    }

    String prompt = String.format(
        "다음 정보를 바탕으로 학원 홈페이지의 소개문을 2-3문장으로 작성해주세요.\n"
            + "학원명: %s\n"
            + "템플릿: %s\n"
            + "고객 요청: %s\n"
            + "자연스럽고 신뢰감 있는 문체로 작성해주세요. "
            + "과장 광고나 근거 없는 표현은 사용하지 마세요.",
        academyName != null ? academyName : "학원",
        getPresetDisplayName(preset),
        message);

    AiCompletion result = aiProvider.complete(prompt, new AiTaskLimits(300));
    if (!result.ok() || result.text().isBlank()) {
      return null;
    }

    return sanitize(result.text());
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

  private boolean isValidHexColor(String color) {
    return color.matches("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
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
