package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class PublicAnalyticsLayoutContractTest {

  private static final Path TEMPLATE_ROOT = Path.of("src/main/resources/templates");
  private static final String GTM_CONTAINER_ID = "GTM-MMJ4MGNN";
  private static final List<String> TRACKED_PUBLIC_TEMPLATES = List.of(
      "academies.html",
      "academy_blog_post.html",
      "academy_blog_posts.html",
      "academy_posts.html",
      "academy_public.html",
      "analyze_detail.html",
      "analyzes.html",
      "community.html",
      "community_post.html",
      "home.html",
      "landing.html",
      "lesson_search.html");

  @Test
  void publicLayout_placesOfficialGtmSnippetsAtDocumentBoundaries() throws IOException {
    // given
    String layout = Files.readString(TEMPLATE_ROOT.resolve("layouts/public.html"));

    // when
    int headStart = layout.indexOf("<head>");
    int headSnippet = layout.indexOf("<!-- Google Tag Manager -->");
    int bodyStart = layout.indexOf("<body");
    int bodySnippet = layout.indexOf("<!-- Google Tag Manager (noscript) -->");
    int content = layout.indexOf("layout:fragment=\"content\"");

    // then
    assertThat(layout).contains(GTM_CONTAINER_ID);
    assertThat(layout).contains("fragments/_head :: favicon");
    assertThat(layout).contains("https://www.googletagmanager.com/gtm.js?id=");
    assertThat(layout).contains("https://www.googletagmanager.com/ns.html?id=" + GTM_CONTAINER_ID);
    assertThat(headSnippet).isGreaterThan(headStart).isLessThan(layout.indexOf("</head>"));
    assertThat(bodySnippet).isGreaterThan(bodyStart).isLessThan(content);
  }

  @Test
  void publicReadPages_decorateSharedLayoutAndExposeOneContentFragment() throws IOException {
    // given / when
    List<String> templates = TRACKED_PUBLIC_TEMPLATES.stream()
        .map(name -> TEMPLATE_ROOT.resolve("public").resolve(name))
        .map(PublicAnalyticsLayoutContractTest::readString)
        .toList();

    // then
    assertThat(templates).allSatisfy(template -> {
      assertThat(template).contains("layout:decorate=\"~{layouts/public");
      assertThat(template).contains("layout:fragment=\"content\"");
      assertThat(template).doesNotContain(GTM_CONTAINER_ID);
    });
  }

  @Test
  void privateAuthoringAndPreviewPages_doNotLoadPublicAnalyticsLayout() throws IOException {
    // given / when
    String authoring = readString(TEMPLATE_ROOT.resolve("public/community_new.html"));
    String preview = readString(TEMPLATE_ROOT.resolve("public/academy_layout_preview.html"));

    // then
    assertThat(authoring).doesNotContain("layouts/public").doesNotContain(GTM_CONTAINER_ID);
    assertThat(preview).doesNotContain("layouts/public").doesNotContain(GTM_CONTAINER_ID);
  }

  @Test
  void layoutDialect_rendersGtmBeforePageHeadAndBodyContent() {
    // given
    TemplateEngine engine = templateEngine();

    // when
    String rendered = engine.process("public/home", new Context());

    // then
    assertThat(rendered.indexOf("<!-- Google Tag Manager -->"))
        .isLessThan(rendered.indexOf("<title>F2R"));
    assertThat(rendered.indexOf("<!-- Google Tag Manager (noscript) -->"))
        .isLessThan(rendered.indexOf("<div class=\"site\""));
    assertThat(rendered).contains("gtm.js?id=", "ns.html?id=" + GTM_CONTAINER_ID);
    assertThat(count(rendered, "<head>")).isOne();
    assertThat(count(rendered, "<body")).isOne();
  }

  @Test
  void layoutDialect_preservesPublicPageBodyClassParameter() {
    // given
    TemplateEngine engine = templateEngine();

    // when
    String rendered = engine.process("public/community", new Context());

    // then
    assertThat(rendered).contains("<body class=\"community-body\">");
    assertThat(rendered.indexOf("<!-- Google Tag Manager (noscript) -->"))
        .isLessThan(rendered.indexOf("<header class=\"community-topbar\""));
  }

  private static String readString(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read template: " + path, e);
    }
  }

  private static int count(String text, String needle) {
    int count = 0;
    int from = 0;
    while ((from = text.indexOf(needle, from)) >= 0) {
      count++;
      from += needle.length();
    }
    return count;
  }

  private static TemplateEngine templateEngine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode("HTML");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    return engine;
  }
}
