package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ChartSiteToolsContractTest {

  @Test
  void adminChart_registersApprovedWebMcpTools() throws IOException {
    String script = read("static/js/admin_chart_site_tools.js");
    String template = read("templates/admin/chart.html");

    assertThat(script).contains("document.modelContext", "registerTool");
    assertThat(script).contains(
        "get_chart_context",
        "get_next_regions",
        "search_existing_educators",
        "save_research_drafts",
        "preview_buildit_scores",
        "submit_chart_review_batch",
        "inspect_chart_review_batch",
        "revert_chart_review_batch");
    assertThat(template).contains("/js/admin_chart_site_tools.js");
  }

  @Test
  void writeTools_preserveHumanPublicationBoundary() throws IOException {
    String script = read("static/js/admin_chart_site_tools.js");

    assertThat(script).contains("published: false");
    assertThat(script).doesNotContain("/api/admin/chart/tools/publications");
    assertThat(script).doesNotContain("publish_chart_review_batch");
    // 사고 복구 도구는 있어도 되지만, 직접 발행/재생성을 AI에 노출하지는 않는다.
    assertThat(script).doesNotContain("rebuild_nationwide_publication");
    assertThat(script).contains("revert_chart_review_batch");
  }

  private String read(String path) throws IOException {
    try (var input = new ClassPathResource(path).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
