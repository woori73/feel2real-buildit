package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class McpConsentTemplateTest {
  @Test
  void givenScopeDescriptions_whenConsentRendered_thenEveryCheckboxHasItsDescription()
      throws Exception {
    var engine = new SpringTemplateEngine();
    engine.setTemplateResolver(new StringTemplateResolver());
    var context = new Context();
    context.setVariable("scopes", List.of("studio:read", "originals:read"));
    context.setVariable(
        "scopeLabels", Map.of("studio:read", "AI AGENT 제작 자료", "originals:read", "원본 파일 외부 제공"));
    context.setVariable("clientName", "Synthetic client");
    context.setVariable("nonce", "synthetic");
    context.setVariable("adminAccount", true);
    String template =
        Files.readString(Path.of("src/main/resources/templates/studio/mcp-consent.html"));
    template =
        template.replace("<th:block th:replace=\"~{fragments/_head :: favicon}\"></th:block>", "");
    String result = engine.process(template, context);
    assertThat(result).contains("AI AGENT 제작 자료", "원본 파일 외부 제공", "Synthetic client");
  }
}
