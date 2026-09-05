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
 * 공개 BuildIt Wiki 문항 상세 화면 계약.
 *
 * <p>단순화 원칙(스펙): 화면은 [학습자료] / [AI 문제제작] / [이 문항에 대한 의견] 흐름으로 보인다.
 * 작성폼은 기본으로 펼쳐져 있지 않고, 내부 개념(data_kind / DISCUSSION 등)을 사용자에게 노출하지 않는다.
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
    assertThat(read("templates/public/wiki.html")).contains("href=\"/wiki/questions\"");
  }

  @Test
  @DisplayName("상세 화면은 [학습자료]/[AI 문제제작]/[이 문항에 대한 의견] 흐름이며 작성폼은 모달(기본 숨김)이다")
  void detailTemplate_twoAreasAndNoInlineForm() throws IOException {
    String html = read("templates/public/question_wiki.html");

    assertThat(html).contains("id=\"qwMaterialSection\"", "id=\"qwOpinionSection\"");
    assertThat(html).contains(">학습자료<", "이 문항에 대한 의견");
    assertThat(html).contains("id=\"qwWriteBtn\"").contains("+ 의견 남기기");
    // 학습자료 등록 두 갈래
    assertThat(html).contains("Wiki 자료에서 찾기", "직접 올리기");
    // 작성폼(qwComposer)은 모달(qwComposerModal) 안에 있다 — 전면 노출 금지
    assertThat(html).contains("id=\"qwComposerModal\"", "id=\"qwComposer\"");
    assertThat(html.indexOf("id=\"qwComposerModal\"")).isLessThan(html.indexOf("id=\"qwComposer\""));
    // 내부 분류 개념을 사용자에게 노출하지 않는다
    assertThat(html)
        .doesNotContain("데이터 성격")
        .doesNotContain("GENERATED_PRACTICE")
        .doesNotContain("DISCUSSION")
        .doesNotContain("id=\"qwDataKind\"")
        .doesNotContain("id=\"qwVariantFields\"")
        .doesNotContain("id=\"qwEvidenceFields\"");
  }

  @Test
  @DisplayName("분류 필터는 보조(details 안)로 우선순위가 낮다")
  void detailTemplate_filterIsSecondary() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html).contains("<details class=\"qw-filter\"");
    assertThat(html).contains("data-filter=\"all\"", "data-filter=\"VARIANT\"", "data-filter=\"EXAM_EVIDENCE\"");
  }

  @Test
  @DisplayName("상세 JS 는 Context API + 의견/자료 API 를 쓰고 /home·/community 로 가지 않는다")
  void detailJs_usesContextApi() throws IOException {
    String js = read("static/js/question_wiki.js");
    assertThat(js).contains("/api/wiki/questions/");
    assertThat(js).contains("/context");
    assertThat(js).contains("/posts");
    assertThat(js).contains("/replies");
    assertThat(js).contains("/api/admin/wiki/questions/materials/search");
    assertThat(js).contains("/materials/upload");
    assertThat(js).doesNotContain("'/home'").doesNotContain("\"/home\"");
    assertThat(js).doesNotContain("/community/posts/");
    // 새 글은 분류를 보내지 않는다(서버 기본값 ETC + 향후 AI 자동분류)
    assertThat(js).contains("JSON.stringify({ content: content })");

    String listJs = read("static/js/question_wiki_list.js");
    assertThat(listJs).contains("/api/wiki/questions?grade=");
    assertThat(listJs).contains("/wiki/questions/");
  }

  @Test
  @DisplayName("HTML 직접 올리기: accept 에 .html + 학습자료 카드는 [HTML] 미리보기를 격리 iframe 으로 연다")
  void htmlUpload_acceptAndSandboxedPreview() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html).contains(".html,.htm");           // 파일 선택 accept
    assertThat(html).contains("id=\"qwHtmlPreviewModal\"", "id=\"qwHtmlFrame\"");
    // 부모 DOM 삽입이 아니라 sandbox iframe
    assertThat(html).contains("<iframe id=\"qwHtmlFrame\"").contains("sandbox");

    String js = read("static/js/question_wiki.js");
    assertThat(js).contains("previewKind === 'HTML'");
    assertThat(js).contains("data-html-preview", "openHtmlPreview");
    assertThat(js).contains("els.htmlFrame.setAttribute('src'");
    // iframe src 로만 연다(srcdoc/innerHTML 로 부모에 삽입하지 않음)
    assertThat(js).doesNotContain("htmlFrame.srcdoc").doesNotContain("innerHTML = html");
  }

  @Test
  @DisplayName("모든 문항 상세에 공통 Chrome AI 진입점이 있고 현재 Context만 사용해 기존 Variant로 저장한다")
  void detailTemplate_hasSharedChromeAiEntrypoint() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html)
        .contains("id=\"qwAiSection\"", "id=\"qwAiToggle\"", "✨ AI 문제제작")
        .contains("data-ai-preset=\"grammar\"", "id=\"qwAiRequest\"", "id=\"qwAiPattern\"")
        .contains("/js/chrome_built_in_ai.js", "/js/question_wiki_ai.js");

    String detailJs = read("static/js/question_wiki.js");
    assertThat(detailJs)
        .contains("questionwiki:context", "questionWikiId: questionWikiId", "context: cache")
        .contains("questionwiki:reload");

    String aiJs = read("static/js/question_wiki_ai.js");
    assertThat(aiJs)
        .contains("window.ChromeBuiltInQuestionAi")
        .contains("sourceQuestionId: Number(questionWikiId)")
        .contains("dataKind: 'GENERATED_PRACTICE'", "category: 'VARIANT'", "variant:")
        .contains("questionwiki:reload")
        .doesNotContain("/context")
        .doesNotContain("openai", "claude", "gemini", "googleapis");

    String engineJs = read("static/js/chrome_built_in_ai.js");
    assertThat(engineJs)
        .contains("LanguageModel.availability", "LanguageModel.create")
        .contains("sourceLanguage: 'ko', targetLanguage: 'en'")
        .contains("sourceLanguage: 'en', targetLanguage: 'ko'")
        .contains("PATTERN REFERENCE ONLY", "CURRENT_QUESTION.passage is the only content source")
        .contains("REFERENCE_CONTEXT (supporting analysis only; never a replacement passage)")
        .doesNotContain("fetch(");
  }

  private static String read(String classpath) throws IOException {
    try (var in = new ClassPathResource(classpath).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
