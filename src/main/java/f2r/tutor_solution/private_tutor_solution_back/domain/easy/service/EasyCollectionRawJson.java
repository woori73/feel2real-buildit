package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 수집 원문 JSON에서 표식 필드를 읽는다. 원문이 JSON이 아니거나 값이 없으면 null. */
final class EasyCollectionRawJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private EasyCollectionRawJson() {}

  static String text(String rawJson, String field) {
    if (rawJson == null || rawJson.isBlank() || field == null) {
      return null;
    }
    try {
      JsonNode node = MAPPER.readTree(rawJson);
      if (node == null || !node.isObject()) {
        return null;
      }
      JsonNode value = node.get(field);
      if (value == null || value.isNull()) {
        return null;
      }
      String text = value.asText().trim();
      return text.isEmpty() ? null : text;
    } catch (Exception e) {
      return null;
    }
  }
}
