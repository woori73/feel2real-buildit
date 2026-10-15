package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 저장된 초안 projectData에서 "제안이 가리킬 수 있는" 컴포넌트 목록(id가 있는 것)을 뽑는다.
 * Agent의 context 응답과 AI 수정 요청의 프롬프트 입력이 이 목록 하나를 공유한다.
 */
public final class EditorOutline {

  public static final int MAX_ITEMS = 200;
  public static final int MAX_TEXT = 120;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 컴포넌트 요약. text/src는 해당 시에만 값이 있다. */
  public record Item(String id, String tag, String type, String text, String src) {
  }

  private EditorOutline() {
  }

  public static List<Item> of(String projectData) {
    List<Item> out = new ArrayList<>();
    if (projectData == null || projectData.isBlank()) {
      return out;
    }
    try {
      JsonNode root = MAPPER.readTree(projectData);
      JsonNode pages = root.path("pages");
      if (pages.isArray() && !pages.isEmpty()) {
        JsonNode frames = pages.get(0).path("frames");
        if (frames.isArray() && !frames.isEmpty()) {
          walk(frames.get(0).path("component"), out);
        }
      }
    } catch (Exception e) {
      return new ArrayList<>();
    }
    return out;
  }

  private static void walk(JsonNode node, List<Item> out) {
    if (node == null || !node.isObject() || out.size() >= MAX_ITEMS) {
      return;
    }
    JsonNode id = node.path("attributes").path("id");
    if (id.isTextual() && !id.asText().isBlank()) {
      String tag = node.path("tagName").isTextual() ? node.path("tagName").asText() : "div";
      String type = node.path("type").isTextual() ? node.path("type").asText() : "";
      String text = shortText(node);
      JsonNode src = node.path("attributes").path("src");
      out.add(new Item(id.asText(), tag, type, text.isEmpty() ? null : text,
          src.isTextual() ? src.asText() : null));
    }
    JsonNode children = node.path("components");
    if (children.isArray()) {
      for (JsonNode child : children) {
        walk(child, out);
      }
    }
  }

  private static String shortText(JsonNode node) {
    StringBuilder sb = new StringBuilder();
    collectText(node, sb);
    String t = sb.toString().trim().replaceAll("\\s+", " ");
    return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
  }

  private static void collectText(JsonNode node, StringBuilder sb) {
    if (node == null || !node.isObject() || sb.length() > MAX_TEXT * 2) {
      return;
    }
    if (node.path("content").isTextual()) {
      sb.append(node.path("content").asText());
    }
    JsonNode children = node.path("components");
    if (children.isArray()) {
      for (JsonNode c : children) {
        collectText(c, sb);
      }
    }
  }
}
