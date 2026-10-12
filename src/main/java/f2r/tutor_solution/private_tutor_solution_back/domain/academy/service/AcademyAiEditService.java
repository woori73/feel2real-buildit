package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * AI 대화형 수정 서비스 (뮤즈 담당, 2026-10-12).
 *
 * <p>사용자의 자연어 수정 요청을 해석하여 기존 Proposal API에 전달할
 * {@code changes[]} 배열을 생성한다.
 *
 * <p><b>작업 경계</b>:
 * <ul>
 *   <li>이 서비스는 changes[] 생성까지만 담당
 *   <li>Proposal 승인·저장 로직은 수정하지 않음 (클로드 담당)
 *   <li>클로드의 편집기 파일·Proposal 저장 코드는 건드리지 않음
 * </ul>
 *
 * <p>changes[] 형식 (클로드 계약 {@code docs/BUILDIT_AI_AGENT_PROPOSAL_PORT_CONTRACT.md} 기준):
 * <pre>
 * {
 *   "componentId": "ve5",
 *   "type": "text" | "image" | "style",
 *   "after": "새 텍스트",              // text/image용
 *   "styles": {"color": "red"},        // style용
 *   "mediaText": "(max-width: 768px)"  // style용 (선택)
 * }
 * </pre>
 */
@Service
public class AcademyAiEditService {

  private final AiProvider aiProvider;

  // 컴포넌트 ID 패턴 (ve1, ve5 등) — 클로드 계약과 동일
  private static final Pattern COMPONENT_ID_PATTERN =
      Pattern.compile("ve\\d+");

  public AcademyAiEditService(AiProvider aiProvider) {
    this.aiProvider = aiProvider;
  }

  /**
   * 자연어 수정 요청을 해석하여 changes[]를 생성한다.
   *
   * @param command 사용자 명령 (예: "제목을 '수능 영어 1등급'으로 바꿔줘")
   * @param projectData 현재 projectData JSON (컴포넌트 탐색용)
   * @return Proposal changes[] 배열
   * @throws IllegalArgumentException 명령을 해석할 수 없거나 검증 실패 시
   */
  public List<Map<String, Object>> parseCommandToChanges(
      String command,
      String projectData) {

    if (command == null || command.isBlank()) {
      throw new IllegalArgumentException("수정 명령을 입력해주세요");
    }

    List<Map<String, Object>> changes = new ArrayList<>();

    // 1. 명령 유형 파악
    CommandType type = detectCommandType(command);

    switch (type) {
      case TEXT_CHANGE -> changes.addAll(parseTextChange(command, projectData));
      case IMAGE_CHANGE -> changes.addAll(parseImageChange(command, projectData));
      case STYLE_CHANGE -> changes.addAll(parseStyleChange(command, projectData));
      case UNKNOWN -> throw new IllegalArgumentException(
          "명령을 이해하지 못했습니다. 예: 제목을 '새 제목'으로 바꿔줘");
    }

    // 2. 변경 대상·내용 검증
    validateChanges(changes);

    return changes;
  }

  private CommandType detectCommandType(String command) {
    String lower = command.toLowerCase();

    boolean isChange = lower.contains("바꿔") || lower.contains("수정")
        || lower.contains("변경") || lower.contains("고쳐");
    if (!isChange) {
      return CommandType.UNKNOWN;
    }
    if (lower.contains("색") || lower.contains("스타일")
        || lower.contains("크기") || lower.contains("폰트")) {
      return CommandType.STYLE_CHANGE;
    }
    if (lower.contains("이미지") || lower.contains("사진")
        || lower.contains("그림")) {
      return CommandType.IMAGE_CHANGE;
    }
    return CommandType.TEXT_CHANGE;
  }

  /**
   * 텍스트 변경 명령 파싱.
   * 예: "제목을 '수능 영어 1등급'으로 바꿔줘"
   */
  private List<Map<String, Object>> parseTextChange(
      String command,
      String projectData) {

    List<Map<String, Object>> changes = new ArrayList<>();

    // 따옴표 안의 새 텍스트 추출
    Pattern quotePattern = Pattern.compile("'([^']+)'|\"([^\"]+)\"");
    Matcher m = quotePattern.matcher(command);
    String newText = null;
    if (m.find()) {
      newText = m.group(1) != null ? m.group(1) : m.group(2);
    }

    if (newText == null || newText.isBlank()) {
      throw new IllegalArgumentException(
          "변경할 텍스트를 따옴표로 지정해주세요. 예: 제목을 '새 제목'으로 바꿔줘");
    }

    // 대상 컴포넌트 찾기 (키워드 기반)
    String componentId = findComponentByKeyword(command, projectData);
    if (componentId == null) {
      throw new IllegalArgumentException(
          "수정할 대상을 찾지 못했습니다. 더 구체적으로 지정해주세요.");
    }

    Map<String, Object> change = new HashMap<>();
    change.put("componentId", componentId);
    change.put("type", "text");
    change.put("after", newText);
    changes.add(change);

    return changes;
  }

  private List<Map<String, Object>> parseImageChange(
      String command,
      String projectData) {
    // 이미지 변경은 2단계에서 구현 (Asset 업로드 연동 필요)
    throw new UnsupportedOperationException(
        "이미지 변경은 2단계에서 구현 예정입니다");
  }

  private List<Map<String, Object>> parseStyleChange(
      String command,
      String projectData) {
    List<Map<String, Object>> changes = new ArrayList<>();

    String componentId = findComponentByKeyword(command, projectData);
    if (componentId == null) {
      throw new IllegalArgumentException("수정할 대상을 찾지 못했습니다.");
    }

    Map<String, String> styles = new HashMap<>();
    String lower = command.toLowerCase();

    // 색상 추출
    if (lower.contains("빨간") || lower.contains("빨강")) {
      styles.put("color", "#ff0000");
    } else if (lower.contains("파란") || lower.contains("파랑")) {
      styles.put("color", "#0000ff");
    } else if (lower.contains("검정") || lower.contains("검은")) {
      styles.put("color", "#000000");
    }

    // 크기 추출
    Pattern sizePattern = Pattern.compile("(\\d+)px");
    Matcher m = sizePattern.matcher(command);
    if (m.find()) {
      styles.put("font-size", m.group(1) + "px");
    }

    if (styles.isEmpty()) {
      throw new IllegalArgumentException(
          "변경할 스타일을 지정해주세요. 예: 제목 색을 빨간색으로 바꿔줘");
    }

    Map<String, Object> change = new HashMap<>();
    change.put("componentId", componentId);
    change.put("type", "style");
    change.put("styles", styles);
    // mediaText는 기본 null (데스크톱) — 클로드 계약의 선택 필드
    changes.add(change);

    return changes;
  }

  /**
   * 키워드로 컴포넌트를 찾는다.
   *
   * <p>현재는 키워드→컴포넌트 매핑 플레이스홀더. 2단계에서 projectData JSON을
   * 파싱하여 실제 컴포넌트 트리를 탐색하도록 확장한다.
   */
  private String findComponentByKeyword(String command, String projectData) {
    String lower = command.toLowerCase();

    if (lower.contains("제목")) {
      return "ve5";
    }
    if (lower.contains("부제목") || lower.contains("소제목")) {
      return "ve6";
    }

    return null;
  }

  /**
   * changes[] 검증 (클로드 계약 기준).
   */
  private void validateChanges(List<Map<String, Object>> changes) {
    if (changes.isEmpty()) {
      throw new IllegalArgumentException("변경 사항이 없습니다");
    }

    for (Map<String, Object> change : changes) {
      Object componentIdObj = change.get("componentId");
      Object typeObj = change.get("type");

      if (!(componentIdObj instanceof String componentId)
          || !COMPONENT_ID_PATTERN.matcher(componentId).matches()) {
        throw new IllegalArgumentException(
            "잘못된 컴포넌트 ID: " + componentIdObj);
      }

      if (!(typeObj instanceof String type)) {
        throw new IllegalArgumentException("변경 타입이 없습니다: " + componentId);
      }

      // 타입별 필수 필드 검증
      switch (type) {
        case "text", "image" -> {
          if (change.get("after") == null) {
            throw new IllegalArgumentException(
                "after 필드가 필요합니다: " + componentId);
          }
        }
        case "style" -> {
          if (!(change.get("styles") instanceof Map)) {
            throw new IllegalArgumentException(
                "styles 필드가 필요합니다: " + componentId);
          }
        }
        default -> throw new IllegalArgumentException(
            "알 수 없는 변경 타입: " + type);
      }
    }
  }

  private enum CommandType {
    TEXT_CHANGE,
    IMAGE_CHANGE,
    STYLE_CHANGE,
    UNKNOWN
  }
}
