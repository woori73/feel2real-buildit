package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 저장되는 GrapesJS projectData가 제안의 변경을 실제로 담고 있는지 검증한다.
 *
 * <p><b>fail-closed</b>: 성공을 기본값으로 반환하는 경로가 없다. 비교 대상 누락·JSON 파싱 실패·
 * {@code changes} 비어 있음·알 수 없는 type은 모두 실패 사유로 반환된다. 사유 문자열은
 * {@code "코드:componentId"} 형식이다.
 */
public final class ProposalVerifier {

  public static final String NO_CHANGES = "NO_CHANGES";
  public static final String PROJECT_DATA_MISSING = "PROJECT_DATA_MISSING";
  public static final String TARGET_NOT_FOUND = "TARGET_NOT_FOUND";
  public static final String TARGET_AMBIGUOUS = "TARGET_AMBIGUOUS";
  public static final String TEXT_MISMATCH = "TEXT_MISMATCH";
  public static final String IMAGE_MISMATCH = "IMAGE_MISMATCH";
  public static final String STYLE_MISMATCH = "STYLE_MISMATCH";
  public static final String UNKNOWN_CHANGE_TYPE = "UNKNOWN_CHANGE_TYPE";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ProposalVerifier() {
  }

  /** 전체 검증. 빈 목록이면 통과. */
  public static List<String> verify(String projectData, List<ProposalChange> changes) {
    return run(projectData, changes, true);
  }

  /** 대상 존재만 확인한다(STALE 판정용 dry-run). 같은 탐색 코드를 쓴다. */
  public static List<String> verifyTargetsOnly(String projectData, List<ProposalChange> changes) {
    return run(projectData, changes, false);
  }

  private static List<String> run(String projectData, List<ProposalChange> changes, boolean deep) {
    List<String> failures = new ArrayList<>();
    if (changes == null || changes.isEmpty()) {
      failures.add(NO_CHANGES);
      return failures;
    }
    JsonNode root = parse(projectData);
    if (root == null) {
      failures.add(PROJECT_DATA_MISSING);
      return failures;
    }
    JsonNode wrapper = wrapper(root);
    if (wrapper == null) {
      failures.add(PROJECT_DATA_MISSING);
      return failures;
    }
    for (ProposalChange c : changes) {
      String id = c == null ? null : c.componentId();
      if (c == null || id == null || c.type() == null) {
        failures.add(UNKNOWN_CHANGE_TYPE + ":" + id);
        continue;
      }
      List<JsonNode> found = new ArrayList<>();
      collect(wrapper, id, found);
      if (found.isEmpty()) {
        failures.add(TARGET_NOT_FOUND + ":" + id);
        continue;
      }
      if (found.size() > 1) {
        failures.add(TARGET_AMBIGUOUS + ":" + id);
        continue;
      }
      if (!deep) {
        continue;
      }
      switch (c.type()) {
        case ProposalChange.TEXT -> {
          if (c.after() == null || !normalize(textOf(found.get(0))).equals(normalize(c.after()))) {
            failures.add(TEXT_MISMATCH + ":" + id);
          }
        }
        case ProposalChange.IMAGE -> {
          JsonNode src = found.get(0).path("attributes").path("src");
          if (c.after() == null || !src.isTextual() || !src.asText().equals(c.after())) {
            failures.add(IMAGE_MISMATCH + ":" + id);
          }
        }
        case ProposalChange.STYLE -> {
          if (!styleMatches(root, id, c.styles(), c.mediaText())) {
            failures.add(STYLE_MISMATCH + ":" + id);
          }
        }
        default -> failures.add(UNKNOWN_CHANGE_TYPE + ":" + id);
      }
    }
    return failures;
  }

  private static JsonNode parse(String projectData) {
    if (projectData == null || projectData.isBlank()) {
      return null;
    }
    try {
      JsonNode n = MAPPER.readTree(projectData);
      return n != null && n.isObject() ? n : null;
    } catch (Exception e) {
      return null;
    }
  }

  private static JsonNode wrapper(JsonNode root) {
    JsonNode pages = root.path("pages");
    if (!pages.isArray() || pages.isEmpty()) {
      return null;
    }
    JsonNode frames = pages.get(0).path("frames");
    if (!frames.isArray() || frames.isEmpty()) {
      return null;
    }
    JsonNode component = frames.get(0).path("component");
    return component.isObject() ? component : null;
  }

  private static void collect(JsonNode node, String id, List<JsonNode> out) {
    if (node == null || !node.isObject()) {
      return;
    }
    JsonNode attrId = node.path("attributes").path("id");
    if (attrId.isTextual() && attrId.asText().equals(id)) {
      out.add(node);
    }
    JsonNode children = node.path("components");
    if (children.isArray()) {
      for (JsonNode child : children) {
        collect(child, id, out);
      }
    }
  }

  /** 하위 textnode의 content를 깊이 우선으로 이어 붙인다(중첩 인라인 요소 포함). */
  private static String textOf(JsonNode node) {
    StringBuilder sb = new StringBuilder();
    appendText(node, sb);
    return sb.toString();
  }

  private static void appendText(JsonNode node, StringBuilder sb) {
    if (node == null || !node.isObject()) {
      return;
    }
    JsonNode content = node.path("content");
    if (content.isTextual()) {
      sb.append(content.asText());
    }
    JsonNode children = node.path("components");
    if (children.isArray()) {
      for (JsonNode child : children) {
        appendText(child, sb);
      }
    }
  }

  private static boolean styleMatches(
      JsonNode root, String id, Map<String, String> expected, String mediaText) {
    if (expected == null || expected.isEmpty()) {
      return false;
    }
    JsonNode styles = root.path("styles");
    if (!styles.isArray()) {
      return false;
    }
    String selector = "#" + id;
    String wantMedia = mediaText == null ? "" : mediaText;
    for (JsonNode rule : styles) {
      if (!hasSelector(rule, selector)) {
        continue;
      }
      String ruleMedia = rule.path("mediaText").isTextual() ? rule.path("mediaText").asText() : "";
      if (!Objects.equals(ruleMedia, wantMedia)) {
        continue;
      }
      JsonNode style = rule.path("style");
      if (!style.isObject()) {
        continue;
      }
      boolean all = true;
      for (Map.Entry<String, String> e : expected.entrySet()) {
        JsonNode actual = style.path(e.getKey().toLowerCase());
        if (!actual.isTextual() || !normalize(actual.asText()).equalsIgnoreCase(normalize(e.getValue()))) {
          all = false;
          break;
        }
      }
      if (all) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasSelector(JsonNode rule, String selector) {
    JsonNode selectors = rule.path("selectors");
    if (selectors.isArray()) {
      for (JsonNode s : selectors) {
        if (s.isTextual() && s.asText().equals(selector)) {
          return true;
        }
      }
    }
    JsonNode add = rule.path("selectorsAdd");
    return add.isTextual() && add.asText().trim().equals(selector);
  }

  private static String normalize(String s) {
    return s == null ? "" : s.trim().replaceAll("\\s+", " ");
  }
}
