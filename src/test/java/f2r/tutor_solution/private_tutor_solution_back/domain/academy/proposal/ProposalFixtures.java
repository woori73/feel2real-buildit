package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;

/** 실제 GrapesJS 0.23.6 출력 fixture(src/test/resources/visual-editor)를 읽어 projectData를 만든다. */
final class ProposalFixtures {

  static final ObjectMapper M = new ObjectMapper();
  /** fixture의 h1(제목) id와 원래 텍스트. */
  static final String TITLE_ID = "ii5p";
  static final String TITLE_TEXT = "학원 이름";
  static final String PARA_ID = "i5fj";
  static final String IMAGE_ID = "ipvb4";

  private ProposalFixtures() {
  }

  static JsonNode fixture() {
    try (InputStream in = ProposalFixtures.class.getResourceAsStream(
        "/visual-editor/grapesjs-project.json")) {
      return M.readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String html() {
    return fixture().get("html").asText();
  }

  static String css() {
    return fixture().get("css").asText();
  }

  /** fixture projectData(문자열 또는 객체) 그대로. */
  static ObjectNode projectData() {
    try {
      JsonNode pd = fixture().get("projectData");
      return (ObjectNode) (pd.isTextual() ? M.readTree(pd.asText()) : pd.deepCopy());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String json(JsonNode n) {
    try {
      return M.writeValueAsString(n);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** id가 같은 컴포넌트 1개를 찾는다. */
  static ObjectNode component(JsonNode root, String id) {
    ObjectNode[] found = new ObjectNode[1];
    walk(root.get("pages").get(0).get("frames").get(0).get("component"), id, found);
    return found[0];
  }

  private static void walk(JsonNode n, String id, ObjectNode[] out) {
    if (n == null || !n.isObject()) {
      return;
    }
    if (n.path("attributes").path("id").asText("").equals(id)) {
      out[0] = (ObjectNode) n;
    }
    if (n.path("components").isArray()) {
      n.get("components").forEach(c -> walk(c, id, out));
    }
  }

  /** 제목(h1) 아래 첫 textnode의 content를 바꾼다. */
  static void setTitleText(ObjectNode root, String text) {
    ObjectNode h1 = component(root, TITLE_ID);
    ((ObjectNode) h1.get("components").get(0)).put("content", text);
  }

  /** `#id` 규칙(기본 화면)에 속성 하나를 넣는다(없으면 규칙을 만든다). */
  static void setStyle(ObjectNode root, String id, String mediaText, String prop, String value) {
    ArrayNode styles = (ArrayNode) root.get("styles");
    for (JsonNode r : styles) {
      boolean sel = r.path("selectors").isArray() && r.get("selectors").size() == 1
          && r.get("selectors").get(0).asText().equals("#" + id);
      String media = r.path("mediaText").asText("");
      if (sel && media.equals(mediaText == null ? "" : mediaText)) {
        ((ObjectNode) r.get("style")).put(prop, value);
        return;
      }
    }
    ObjectNode rule = M.createObjectNode();
    rule.putArray("selectors").add("#" + id);
    rule.putObject("style").put(prop, value);
    if (mediaText != null) {
      rule.put("mediaText", mediaText);
      rule.put("atRuleType", "media");
    }
    styles.add(rule);
  }
}
