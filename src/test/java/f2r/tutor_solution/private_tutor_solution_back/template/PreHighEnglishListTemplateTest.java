package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller.PreHighEnglishController.PdfGroup;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

class PreHighEnglishListTemplateTest {
  private Document render(String part) {
    return render("prehigh/list", part, Map.of(
        "rounds", List.of(Map.of("number", 1, "words", 15)),
        "grammarChapters", List.of(Map.of("number", 3, "title", "수동태")),
        "canInstall", true));
  }

  private Document render(String template, String part, Map<String, Object> variables) {
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setCharacterEncoding("UTF-8");
    var engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);

    var request = new MockHttpServletRequest("GET", "/naesintest/pre-high");
    if (part != null) request.setParameter("part", part);
    var application = JakartaServletWebApplication.buildApplication(request.getServletContext());
    var exchange = application.buildExchange(request, new MockHttpServletResponse());
    var context = new WebContext(exchange);
    context.setVariables(variables);
    return Jsoup.parse(engine.process(template, context));
  }

  @Test
  void chooserShowsAllThreeParts() {
    // given / when
    Document chooser = render(null);

    // then
    assertThat(chooser.select(".nt-table.nt-schools tbody a").eachAttr("href")).containsExactly(
        "/naesintest/pre-high?part=1", "/naesintest/pre-high?part=2", "/naesintest/pre-high?part=3");
    assertThat(chooser.select(".nt-table.nt-schools tbody tr").eachText()).anyMatch(row -> row.contains("10회 준비 중"));
    assertThat(chooser.select("#ph-install")).hasSize(1);
    assertThat(render("unknown").select("#ph-parts-title")).hasSize(1);
  }

  @Test
  void selectedPartShowsOnlyItsOwnContent() {
    // given / when
    Document reading = render("1");
    Document practice = render("2");
    Document grammar = render("3");

    // then
    assertThat(reading.select("table tbody a").eachAttr("href")).containsExactly("/naesintest/pre-high/1");
    assertThat(practice.select(".nt-group").text()).contains("2부", "18~45번", "준비 중");
    assertThat(practice.select("table tbody a")).isEmpty();
    assertThat(grammar.select("table tbody a").eachAttr("href")).containsExactly("/naesintest/pre-high/grammar/3");
    for (Document part : List.of(reading, practice, grammar)) {
      assertThat(part.select("#ph-parts-title")).isEmpty();
      assertThat(part.select(".nt-top a[href=/naesintest/pre-high]").text()).isEqualTo("구성 목록");
    }
  }

  @Test
  void downloadPanelOnlyRendersForAnAdminWithQuestionGroups() {
    // given / when
    Document student = render("prehigh/question-pdf", null, Map.of());
    Document admin = render("prehigh/question-pdf", null, Map.of("pdfGroups", List.of(
        new PdfGroup("독해", "/api/admin/pre-high-english/rounds/1/reading", 2))));

    // then
    assertThat(student.select("details")).isEmpty();
    assertThat(admin.select("a[download]").eachAttr("href")).containsExactly(
        "/api/admin/pre-high-english/rounds/1/reading/1.pdf",
        "/api/admin/pre-high-english/rounds/1/reading/2.pdf");
  }
}
