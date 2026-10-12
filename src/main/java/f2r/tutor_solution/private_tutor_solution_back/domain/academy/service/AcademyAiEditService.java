package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * AI 대화형 수정 서비스 (뮤즈 담당, 2026-10-12, 2단계 보완).
 *
 * <p>사용자의 자연어 수정 요청을 해석하여 기존 Proposal API에 전달할
 * {@code changes[]} 배열을 생성한다.
 *
 * <p><b>2단계 보완</b>:
 * <ul>
 *   <li>실제 projectData JSON을 파싱하여 컴포넌트 트리를 탐색
 *   <li>HTML id 기준으로 대상을 정확히 찾음 (편집기의 {@code planChanges}와 동일한 기준)
 *   <li>없는 컴포넌트는 절대 수정하지 않음 (TARGET_NOT_FOUND로 거부)
 * </ul>
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

  // 컴포넌트 ID 패턴 — 편집기 planChanges와 동일: /^[A-Za-z][\w-]*$/
  private static final Pattern COMPONENT_ID_PATTERN =
      Pattern.compile("[A-Za-z][\\w-]*");

  // projectData에서 컴포넌트 id 추출용 (GrapesJS 형식)
  // "attributes":{"id":"ve5"} 또는 "attributes": {"id": "hero-title"}
  private static final Pattern ATTR_ID_PATTERN =
      Pattern.compile("\"attributes\"\\s*:\\s*\\{[^}]*\"id\"\\s*:\\s*\"([A-Za-z][\\w-]*)\"");

  // 텍스트노드 content 추출용
  private static final Pattern TEXTNODE_PATTERN =
      Pattern.compile("\"type\"\\s*:\\s*\"textnode\"[^}]*\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

  // 이미지 src 추출용
  private static final Pattern IMG_SRC_PATTERN =
      Pattern.compile("\"tagName\"\\s*:\\s*\"img\"[^}]*\"src\"\\s*:\\s*\"([^\"]*)\"");

  public AcademyAiEditService(AiProvider aiProvider) {
    this.aiProvider = aiProvider;
  }

  /**
   * 자연어 수정 요청을 해석하여 changes[]를 생성한다.
   *
   * @param command 사용자 명령 (예: "제목을 '수능 영어 1등급'으로 바꿔줘")
   * @param projectData 현재 projectData JSON (컴포넌트 탐색용, 필수)
   * @return Proposal changes[] 배열
   * @throws IllegalArgumentException 명령을 해석할 수 없거나 대상이 없을 때
   */
  public List<Map<String, Object>> parseCommandToChanges(
      String command,
      String projectData) {

    if (command == null || command.isBlank()) {
      throw new IllegalArgumentException("수정 명령을 입력해주세요");
    }
    if (projectData == null || projectData.isBlank()) {
      throw new IllegalArgumentException(
          "편집기 데이터가 없습니다. 편집기를 먼저 열어주세요.");
    }

    // 1. projectData에서 실제 컴포넌트 인덱스 구축
    ComponentIndex index = buildComponentIndex(projectData);
    if (index.isEmpty()) {
      throw new IllegalArgumentException(
          "편집기에서 수정 가능한 요소를 찾지 못했습니다.");
    }

    // 2. 명령 유형 파악
    CommandType type = detectCommandType(command);

    List<Map<String, Object>> changes = new ArrayList<>();
    switch (type) {
      case TEXT_CHANGE -> changes.addAll(parseTextChange(command, index));
      case IMAGE_CHANGE -> changes.addAll(parseImageChange(command, index));
      case STYLE_CHANGE -> changes.addAll(parseStyleChange(command, index));
      case UNKNOWN -> throw new IllegalArgumentException(
          "명령을 이해하지 못했습니다. 예: 제목을 '새 제목'으로 바꿔줘");
    }

    // 3. 변경 대상·내용 검증 (계약 기준)
    validateChanges(changes, index);

    return changes;
  }

  /**
   * projectData JSON을 파싱하여 컴포넌트 인덱스를 구축한다.
   *
   * <p>GrapesJS projectData 형식:
   * <pre>
   * {"pages":[{"frames":[{"component":{
   *   "type":"wrapper","components":[
   *     {"tagName":"h1","attributes":{"id":"ve5"},
   *      "components":[{"type":"textnode","content":"제목"}]}
   *   ]}}]}]}
   * </pre>
   */
  private ComponentIndex buildComponentIndex(String projectData) {
    ComponentIndex index = new ComponentIndex();

    // attributes.id를 가진 컴포넌트 추출
    // 각 컴포넌트 블록을 찾아 id와 주변 컨텍스트 파악
    Matcher idMatcher = ATTR_ID_PATTERN.matcher(projectData);
    while (idMatcher.find()) {
      String id = idMatcher.group(1);
      int pos = idMatcher.start();

      // 해당 위치 주변에서 tagName 추출 (앞 500자 내에서 가장 가까운 tagName)
      String before = projectData.substring(Math.max(0, pos - 800), pos);
      String tagName = extractLastTagName(before);

      // 해당 컴포넌트의 텍스트 콘텐츠 추출 (뒤 2000자 내에서 textnode)
      String after = projectData.substring(pos,
          Math.min(projectData.length(), pos + 3000));
      String textContent = extractFirstTextContent(after);
      String imgSrc = extractImgSrc(before, after);

      index.add(id, tagName, textContent, imgSrc);
    }

    return index;
  }

  private String extractLastTagName(String before) {
    Pattern p = Pattern.compile("\"tagName\"\\s*:\\s*\"(\\w+)\"");
    Matcher m = p.matcher(before);
    String last = null;
    while (m.find()) {
      last = m.group(1);
    }
    return last;
  }

  private String extractFirstTextContent(String after) {
    Matcher m = TEXTNODE_PATTERN.matcher(after);
    // 첫 번째 중첩 깊이의 textnode만 (간단히 첫 매치)
    if (m.find()) {
      return unescapeJson(m.group(1));
    }
    return null;
  }

  private String extractImgSrc(String before, String after) {
    // before에서 img tagName 확인
    if (before.contains("\"img\"")) {
      Matcher m = IMG_SRC_PATTERN.matcher(before + after);
      if (m.find()) {
        return m.group(1);
      }
    }
    return null;
  }

  private String unescapeJson(String s) {
    return s.replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\u0026", "&");
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
   * 실제 컴포넌트의 텍스트 내용과 대조하여 대상을 찾는다.
   */
  private List<Map<String, Object>> parseTextChange(
      String command,
      ComponentIndex index) {

    List<Map<String, Object>> changes = new ArrayList<>();

    // 따옴표 안의 텍스트 추출 — "'A'를 'B'로 바꿔" 패턴이면 A=기존, B=신규
    Pattern quotePattern = Pattern.compile("'([^']+)'|\"([^\"]+)\"");
    Matcher m = quotePattern.matcher(command);
    List<String> quoted = new ArrayList<>();
    while (m.find()) {
      quoted.add(m.group(1) != null ? m.group(1) : m.group(2));
    }

    String newText;
    String oldTextHint = null;
    if (quoted.size() >= 2) {
      // "'A'를 'B'로" → A는 기존 텍스트 힌트, B가 신규 텍스트
      oldTextHint = quoted.get(0);
      newText = quoted.get(1);
    } else if (quoted.size() == 1) {
      newText = quoted.get(0);
      oldTextHint = extractOldTextHintFallback(command, newText);
    } else {
      throw new IllegalArgumentException(
          "변경할 텍스트를 따옴표로 지정해주세요. 예: 제목을 '새 제목'으로 바꿔줘");
    }

    if (newText.isBlank()) {
      throw new IllegalArgumentException(
          "변경할 텍스트를 따옴표로 지정해주세요. 예: 제목을 '새 제목'으로 바꿔줘");
    }

    // 실제 컴포넌트에서 대상 찾기
    String componentId = index.findTextComponent(command, oldTextHint);
    if (componentId == null) {
      throw new IllegalArgumentException(
          "수정할 대상을 찾지 못했습니다 (TARGET_NOT_FOUND). "
              + "편집기에 해당 요소가 있는지 확인해주세요.");
    }

    Map<String, Object> change = new HashMap<>();
    change.put("componentId", componentId);
    change.put("type", "text");
    change.put("after", newText);
    changes.add(change);

    return changes;
  }

  private String extractOldTextHintFallback(String command, String newText) {
    // 단일 따옴표 패턴에서 기존 텍스트 힌트 추출 시도 (보조)
    return null;
  }

  private List<Map<String, Object>> parseImageChange(
      String command,
      ComponentIndex index) {
    // 이미지 URL 추출
    Pattern urlPattern = Pattern.compile("https?://[^\\s'\"<>]+");
    Matcher m = urlPattern.matcher(command);
    String newSrc = null;
    if (m.find()) {
      newSrc = m.group(0);
    }
    if (newSrc == null) {
      throw new IllegalArgumentException(
          "변경할 이미지 URL을 지정해주세요. 예: 메인 이미지를 https://... 로 바꿔줘");
    }

    String componentId = index.findImageComponent(command);
    if (componentId == null) {
      throw new IllegalArgumentException(
          "수정할 이미지를 찾지 못했습니다 (TARGET_NOT_FOUND).");
    }

    Map<String, Object> change = new HashMap<>();
    change.put("componentId", componentId);
    change.put("type", "image");
    change.put("after", newSrc);
    return List.of(change);
  }

  private List<Map<String, Object>> parseStyleChange(
      String command,
      ComponentIndex index) {
    List<Map<String, Object>> changes = new ArrayList<>();

    String componentId = index.findTextComponent(command, null);
    if (componentId == null) {
      componentId = index.findAnyComponent(command);
    }
    if (componentId == null) {
      throw new IllegalArgumentException(
          "수정할 대상을 찾지 못했습니다 (TARGET_NOT_FOUND).");
    }

    Map<String, String> styles = new HashMap<>();
    String lower = command.toLowerCase();

    if (lower.contains("빨간") || lower.contains("빨강")) {
      styles.put("color", "#ff0000");
    } else if (lower.contains("파란") || lower.contains("파랑")) {
      styles.put("color", "#0000ff");
    } else if (lower.contains("검정") || lower.contains("검은")) {
      styles.put("color", "#000000");
    } else if (lower.contains("흰") || lower.contains("화이트")) {
      styles.put("color", "#ffffff");
    }

    Pattern sizePattern = Pattern.compile("(\\d+)px");
    Matcher m = sizePattern.matcher(command);
    if (m.find()) {
      styles.put("font-size", m.group(1) + "px");
    }

    if (lower.contains("굵게") || lower.contains("볼드")) {
      styles.put("font-weight", "bold");
    }

    if (styles.isEmpty()) {
      throw new IllegalArgumentException(
          "변경할 스타일을 지정해주세요. 예: 제목 색을 빨간색으로 바꿔줘");
    }

    // 모바일 지정 여부
    String mediaText = null;
    if (lower.contains("모바일")) {
      mediaText = "(max-width: 768px)";
    }

    Map<String, Object> change = new HashMap<>();
    change.put("componentId", componentId);
    change.put("type", "style");
    change.put("styles", styles);
    if (mediaText != null) {
      change.put("mediaText", mediaText);
    }
    changes.add(change);

    return changes;
  }

  /**
   * changes[] 검증 — 클로드 계약 기준.
   * 존재하지 않는 컴포넌트는 절대 포함하지 않는다.
   */
  private void validateChanges(
      List<Map<String, Object>> changes,
      ComponentIndex index) {

    if (changes.isEmpty()) {
      throw new IllegalArgumentException("변경 사항이 없습니다 (NO_CHANGES)");
    }

    for (Map<String, Object> change : changes) {
      Object componentIdObj = change.get("componentId");
      Object typeObj = change.get("type");

      if (!(componentIdObj instanceof String componentId)
          || !COMPONENT_ID_PATTERN.matcher(componentId).matches()) {
        throw new IllegalArgumentException(
            "잘못된 컴포넌트 ID: " + componentIdObj + " (INVALID_CHANGE)");
      }

      // 실제 존재 확인 — 없으면 거부 (날조 방지)
      if (!index.contains(componentId)) {
        throw new IllegalArgumentException(
            "존재하지 않는 컴포넌트입니다: " + componentId + " (TARGET_NOT_FOUND)");
      }

      if (!(typeObj instanceof String type)) {
        throw new IllegalArgumentException("변경 타입이 없습니다: " + componentId);
      }

      switch (type) {
        case "text", "image" -> {
          if (!(change.get("after") instanceof String after) || after.isBlank()) {
            throw new IllegalArgumentException(
                "after 필드가 필요합니다: " + componentId + " (INVALID_CHANGE)");
          }
        }
        case "style" -> {
          if (!(change.get("styles") instanceof Map)) {
            throw new IllegalArgumentException(
                "styles 필드가 필요합니다: " + componentId + " (INVALID_CHANGE)");
          }
        }
        default -> throw new IllegalArgumentException(
            "알 수 없는 변경 타입: " + type + " (UNKNOWN_CHANGE_TYPE)");
      }
    }
  }

  /**
   * projectData에서 추출한 컴포넌트 인덱스.
   */
  static class ComponentIndex {
    private final Map<String, ComponentInfo> components = new HashMap<>();

    void add(String id, String tagName, String textContent, String imgSrc) {
      components.put(id, new ComponentInfo(id, tagName, textContent, imgSrc));
    }

    boolean isEmpty() {
      return components.isEmpty();
    }

    boolean contains(String id) {
      return components.containsKey(id);
    }

    /**
     * 텍스트 변경 대상 찾기.
     * 1순위: oldTextHint와 일치하는 텍스트를 가진 컴포넌트
     * 2순위: 키워드(제목/부제목/본문 등)와 태그·텍스트가 매칭되는 컴포넌트
     */
    String findTextComponent(String command, String oldTextHint) {
      String lower = command.toLowerCase();

      // 1순위: 기존 텍스트 힌트 매칭
      if (oldTextHint != null && !oldTextHint.isBlank()) {
        for (ComponentInfo c : components.values()) {
          if (c.textContent != null && c.textContent.contains(oldTextHint)) {
            return c.id;
          }
        }
      }

      // 2순위: 키워드 기반 매칭
      List<ComponentInfo> candidates = new ArrayList<>();
      if (lower.contains("제목") && !lower.contains("부제목") && !lower.contains("소제목")) {
        // h1, h2 우선
        for (ComponentInfo c : components.values()) {
          if ("h1".equals(c.tagName) || "h2".equals(c.tagName)) {
            candidates.add(c);
          }
        }
      } else if (lower.contains("부제목") || lower.contains("소제목")) {
        for (ComponentInfo c : components.values()) {
          if ("h3".equals(c.tagName) || "h4".equals(c.tagName)) {
            candidates.add(c);
          }
        }
      } else if (lower.contains("본문") || lower.contains("문단") || lower.contains("설명")) {
        for (ComponentInfo c : components.values()) {
          if ("p".equals(c.tagName)) {
            candidates.add(c);
          }
        }
      } else if (lower.contains("버튼")) {
        for (ComponentInfo c : components.values()) {
          if ("button".equals(c.tagName) || "a".equals(c.tagName)) {
            candidates.add(c);
          }
        }
      }

      // 텍스트가 있는 후보 중 첫 번째
      for (ComponentInfo c : candidates) {
        if (c.textContent != null && !c.textContent.isBlank()) {
          return c.id;
        }
      }
      // 후보는 있지만 텍스트가 없으면 첫 번째 후보
      if (!candidates.isEmpty()) {
        return candidates.get(0).id;
      }

      return null;
    }

    String findImageComponent(String command) {
      for (ComponentInfo c : components.values()) {
        if ("img".equals(c.tagName)) {
          return c.id;
        }
      }
      return null;
    }

    String findAnyComponent(String command) {
      String lower = command.toLowerCase();
      // ID 직접 지정 (예: "ve5를 바꿔줘")
      Matcher m = Pattern.compile("[A-Za-z][\\w-]*").matcher(command);
      while (m.find()) {
        String id = m.group(0);
        if (components.containsKey(id)) {
          return id;
        }
      }
      return null;
    }

    record ComponentInfo(String id, String tagName, String textContent, String imgSrc) {}
  }

  private enum CommandType {
    TEXT_CHANGE,
    IMAGE_CHANGE,
    STYLE_CHANGE,
    UNKNOWN
  }
}
