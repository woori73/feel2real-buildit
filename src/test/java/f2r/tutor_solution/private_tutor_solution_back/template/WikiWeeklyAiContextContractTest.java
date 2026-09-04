package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class WikiWeeklyAiContextContractTest {

  @Test
  void weeklyAiContext_isAlwaysLoadedWithStandardAndReference() throws IOException {
    String js = read("static/js/wiki_learning.js");
    String standard = read("static/wiki/weekly/WEEKLY_GENERATION_STANDARD.md");
    String reference = read("static/wiki/weekly/WEEKLY_REFERENCE.html");

    assertThat(js)
        .contains("/wiki/weekly/WEEKLY_GENERATION_STANDARD.md")
        .contains("/wiki/weekly/WEEKLY_REFERENCE.html")
        .contains("buildit_weekly_ai_context")
        .contains("weeklyAiContext")
        .contains("generation_standard", "visual_reference_html", "current_half_source")
        .contains("loadWeeklyAiContext();");

    assertThat(standard)
        .contains("14문항")
        .contains("수능 21번형 함축 의미")
        .contains("수능 29번형 — 어법")
        .contains("수능 30번형 — 문맥상 어휘")
        .contains("Fixed Typography + Fixed Margin + Flexible Pagination")
        .contains("WEEKLY_REFERENCE.html");

    assertThat(reference)
        .contains("BUILD IT")
        .contains("WEEKLY 01 · 고3")
        .contains("9.8pt")
        .contains("Flexible Pagination")
        .contains("class=\"target\"")
        .contains("font-weight:400");
  }

  private static String read(String classpath) throws IOException {
    try (var in = new ClassPathResource(classpath).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
