package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto;

import java.util.Map;

/**
 * DesignCraft CLI 명령 1건.
 *
 * <p>예: {@code id="style.paragraph.apply", params={"name":"ko-heading1"}}
 * CLI 호출 형태: {@code designcraft-cli run --cmd 'style.paragraph.apply={"name":"ko-heading1"}'}
 */
public record PublishingCommand(
    String id,
    Map<String, Object> params
) {

  public PublishingCommand {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("command id is required");
    }
  }

  /** CLI --cmd 인자 형태로 직렬화: {@code id={json}} */
  public String toCliArg(com.fasterxml.jackson.databind.ObjectMapper om) {
    try {
      String json = om.writeValueAsString(params == null ? Map.of() : params);
      return id + "=" + json;
    } catch (Exception e) {
      throw new IllegalArgumentException("cannot serialize command params: " + id, e);
    }
  }
}
