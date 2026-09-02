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
 * 공개 BuildIt Wiki(/wiki) 화면 계약.
 * - 자료실(원본·분석)은 새 Wiki 자료실 시스템(/api/wiki/originals, /api/wiki/analysis-materials) — /analyzes 재사용 X.
 * - 게시판 작성/상세는 /wiki 전용 화면(/wiki/board/{key}/new, /wiki/posts/{id}) — /community·/home 로 이동하지 않음.
 * - 문자열 계약 + 실제 Thymeleaf 렌더로 파싱·fragment 오류를 함께 잡는다.
 */
class WikiPageContractTest {

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
  @DisplayName("허브·게시판·전용 화면·관리 템플릿이 layouts/public·fragment 치환까지 실제로 렌더된다")
  void templates_render_withLayoutAndFragmentsResolved() {
    TemplateEngine engine = engine();

    for (String view : new String[] {
        "public/wiki", "public/wiki_board", "public/wiki_post_new", "public/wiki_post",
        "public/wiki_originals", "public/wiki_analysis"}) {
      String html = engine.process(view, new Context());
      assertThat(html)
          .as(view)
          .contains("<!DOCTYPE html>")
          .contains("GTM-MMJ4MGNN")
          .contains("<body class=\"wiki-body\">")
          .doesNotContain("th:block", "th:replace", "layout:fragment");
    }

    String admin = engine.process("admin/wiki_analysis", new Context());
    assertThat(admin)
        .contains("<!DOCTYPE html>")
        .contains("admin-nav")
        .contains("href=\"/admin/wiki-analysis\"")
        .contains("id=\"materialForm\"", "id=\"materialModal\"")
        .doesNotContain("th:block", "th:replace");
  }

  @Test
  @DisplayName("허브: 새 Wiki 자료실 시스템 사용 + /analyzes 재사용 안 함 + /wiki 전용 네비")
  void wikiHub_usesWikiResourceSystem() throws IOException {
    String html = read("templates/public/wiki.html");
    String js = read("static/js/wiki.js");

    assertThat(html).contains("layout:decorate=\"~{layouts/public(bodyClass='wiki-body')}\"");
    assertThat(html).contains("id=\"gradeTabs\"", "data-grade=\"3\"", "data-grade=\"2\"", "data-grade=\"1\"");
    assertThat(countOccurrences(html, "/problem-generator")).isEqualTo(1);
    assertThat(html).contains("href=\"/wiki/originals\"", "href=\"/wiki/analysis\"");
    // 관리자 업로드 UI는 서버 principal 가드(WikiPageController가 wikiAdmin 주입). 렌더 검증은 WikiPageControllerRenderTest.
    assertThat(html).contains("th:if=\"${wikiAdmin}\"");

    assertThat(js).contains("/api/wiki/originals?grade=");
    assertThat(js).contains("/api/wiki/analysis-materials?grade=");
    assertThat(js).contains("/api/open-community/posts?scope=wiki");
    assertThat(js).contains("/wiki/board/", "/new");
    assertThat(js).contains("/wiki/posts/");
    assertThat(js).doesNotContain("/api/analyzes");
    assertThat(js).doesNotContain("/community/posts/");
  }

  @Test
  @DisplayName("게시판 목록·작성 화면은 /wiki 문맥에서만 움직인다")
  void wikiBoardAndCompose_stayInWikiContext() throws IOException {
    String boardJs = read("static/js/wiki_board.js");
    assertThat(boardJs).contains("/wiki/board/", "/wiki/posts/");
    assertThat(boardJs).doesNotContain("/community/posts/");

    String newHtml = read("templates/public/wiki_post_new.html");
    String newJs = read("static/js/wiki_post_new.js");
    assertThat(newHtml).contains("← BuildIt Wiki로 돌아가기");
    assertThat(newJs).contains("/wiki/posts/");                 // 저장 후 wiki 상세로
    assertThat(newJs).contains("/api/open-community/posts/", "/attachments");  // 첨부 업로드
    assertThat(newJs).doesNotContain("'/home'").doesNotContain("\"/home\"");

    String postHtml = read("templates/public/wiki_post.html");
    String postJs = read("static/js/wiki_post.js");
    assertThat(postHtml).contains("← BuildIt Wiki로 돌아가기");
    assertThat(postJs).contains("/api/open-community/attachments/");
    assertThat(postJs).doesNotContain("'/home'").doesNotContain("\"/home\"");
    assertThat(postJs).doesNotContain("/community/posts/new");
  }

  private static String read(String classpath) throws IOException {
    try (var in = new ClassPathResource(classpath).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
      count++;
    }
    return count;
  }
}
