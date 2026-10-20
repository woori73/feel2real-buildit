package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** "---" front matter + markdown 본문을 HelpDoc으로 변환한다. 필수 메타가 없으면 거부한다. */
public final class HelpDocParser {

  private static final List<String> REQUIRED = List.of(
      "id", "slug", "title", "audience", "visibility", "status", "featureState", "appliesTo", "lastVerified", "source");

  private HelpDocParser() {}

  public static HelpDoc parse(String raw) {
    String text = raw.replace("\r\n", "\n");
    if (!text.startsWith("---\n")) {
      throw new IllegalArgumentException("front matter가 없습니다");
    }
    int end = text.indexOf("\n---\n", 4);
    if (end < 0) {
      throw new IllegalArgumentException("front matter 종료(---)가 없습니다");
    }
    Map<String, String> meta = new HashMap<>();
    for (String line : text.substring(4, end).split("\n")) {
      int colon = line.indexOf(':');
      if (colon > 0) {
        meta.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
      }
    }
    for (String key : REQUIRED) {
      if (meta.getOrDefault(key, "").isBlank()) {
        throw new IllegalArgumentException("필수 메타 누락: " + key);
      }
    }
    List<String> related = meta.containsKey("related")
        ? Arrays.stream(meta.get("related").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
        : List.of();
    return new HelpDoc(
        meta.get("id"), meta.get("slug"), meta.get("title"), meta.getOrDefault("category", ""),
        Integer.parseInt(meta.getOrDefault("order", "99")), meta.get("audience"), meta.get("visibility"),
        meta.get("status"), meta.get("featureState"), meta.get("appliesTo"),
        meta.getOrDefault("lastModified", ""), meta.get("lastVerified"), meta.get("source"),
        related, text.substring(end + 5).strip());
  }
}
