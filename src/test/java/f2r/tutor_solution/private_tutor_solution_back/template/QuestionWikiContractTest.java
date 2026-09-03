package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * 공개 BuildIt Wiki 문항별 THREAD 화면 계약.
 * - /wiki 문맥에서만 움직인다(/home·/community 로 이동하지 않음).
 * - 목록·상세가 layouts/public·fragment 치환까지 실제로 렌더된다.
 * - 프론트가 문항 Context API(/api/wiki/questions/{id}/context)와 THREAD write API 를 쓴다.
 */
class QuestionWikiContractTest {

  private static TemplateEngine engine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    return engine;
  }

  @Test
  @DisplayName("목록·상세 템플릿이 layout·fragment 치환까지 렌더된다")
  void templates_render() {
    TemplateEngine engine = engine();
    for (String view : new String[] {"public/question_wiki_list", "public/question_wiki"}) {
      String html = engine.process(view, new Context());
      assertThat(html).as(view)
          .contains("<!DOCTYPE html>")
          .contains("GTM-MMJ4MGNN")
          .contains("<body class=\"wiki-body\">")
          .doesNotContain("th:block", "th:replace", "layout:fragment");
    }
  }

  @Test
  @DisplayName("허브(/wiki)에 문항별 THREAD 진입점이 있다")
  void wikiHub_linksToQuestionWiki() throws IOException {
    String hub = read("templates/public/wiki.html");
    assertThat(hub).contains("href=\"/wiki/questions\"");
  }

  @Test
  @DisplayName("문항 상세 JS 는 Context API + THREAD write API 를 쓰고 /home 으로 가지 않는다")
  void detailJs_usesContextApi() throws IOException {
    String js = read("static/js/question_wiki.js");
    assertThat(js).contains("/api/wiki/questions/");
    assertThat(js).contains("/context");
    assertThat(js).contains("/posts");
    assertThat(js).contains("/replies");
    assertThat(js).doesNotContain("'/home'").doesNotContain("\"/home\"");
    assertThat(js).doesNotContain("/community/posts/");

    String listJs = read("static/js/question_wiki_list.js");
    assertThat(listJs).contains("/api/wiki/questions?grade=");
    assertThat(listJs).contains("/wiki/questions/");
  }

  @Test
  @DisplayName("상세 화면은 THREAD 필터(전체/질문/해설/출제포인트/변형문제/기출)와 5개 섹션을 갖는다")
  void detailTemplate_hasFiltersAndSections() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html).contains("data-filter=\"all\"", "data-filter=\"QUESTION\"",
        "data-filter=\"VARIANT\"", "data-filter=\"EXAM_EVIDENCE\"");
    assertThat(html).contains("학습자료", "THREAD", "변형문제", "기출 THREAD", "AI 종합 요약");
    assertThat(html).contains("id=\"qwVariantFields\"", "id=\"qwEvidenceFields\"");
  }

  private static String read(String classpath) throws IOException {
    try (var in = new ClassPathResource(classpath).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
