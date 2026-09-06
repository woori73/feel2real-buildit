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

/** 공개 BuildIt Wiki 문항 상세 화면 계약. */
class QuestionWikiContractTest {
  private static TemplateEngine engine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode(TemplateMode.HTML); resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine(); engine.setTemplateResolver(resolver); engine.addDialect(new LayoutDialect()); return engine;
  }

  @Test @DisplayName("목록·상세 템플릿이 layout·fragment 치환까지 렌더된다")
  void templates_render() {
    TemplateEngine engine = engine();
    for (String view : new String[] {"public/question_wiki_list", "public/question_wiki"}) {
      String html = engine.process(view, new Context());
      assertThat(html).as(view).contains("<!DOCTYPE html>").contains("GTM-MMJ4MGNN").contains("<body class=\"wiki-body\">").doesNotContain("th:block", "th:replace", "layout:fragment");
    }
  }

  @Test @DisplayName("허브(/wiki)에 문항별 THREAD 진입점이 있다")
  void wikiHub_linksToQuestionWiki() throws IOException { assertThat(read("templates/public/wiki.html")).contains("href=\"/wiki/questions\""); }

  @Test @DisplayName("상세 화면은 [학습자료]/[AI 문제제작]/[이 문항에 대한 의견] 흐름이며 작성폼은 모달(기본 숨김)이다")
  void detailTemplate_twoAreasAndNoInlineForm() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html).contains("id=\"qwMaterialSection\"", "id=\"qwOpinionSection\"");
    assertThat(html).contains(">학습자료<", "이 문항에 대한 의견");
    assertThat(html).contains("id=\"qwWriteBtn\"").contains("+ 의견 남기기");
    assertThat(html).contains("Wiki 자료에서 찾기", "직접 올리기");
    assertThat(html).contains("id=\"qwComposerModal\"", "id=\"qwComposer\"");
    assertThat(html.indexOf("id=\"qwComposerModal\"")).isLessThan(html.indexOf("id=\"qwComposer\""));
    assertThat(html).doesNotContain("데이터 성격").doesNotContain("GENERATED_PRACTICE").doesNotContain("DISCUSSION").doesNotContain("id=\"qwDataKind\"").doesNotContain("id=\"qwVariantFields\"").doesNotContain("id=\"qwEvidenceFields\"");
  }

  @Test @DisplayName("분류 필터는 보조(details 안)로 우선순위가 낮다")
  void detailTemplate_filterIsSecondary() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html).contains("<details class=\"qw-filter\"");
    assertThat(html).contains("data-filter=\"all\"", "data-filter=\"VARIANT\"", "data-filter=\"EXAM_EVIDENCE\"");
  }

  @Test @DisplayName("상세 JS 는 Context API + 의견/자료 API 를 쓰고 /home·/community 로 가지 않는다")
  void detailJs_usesContextApi() throws IOException {
    String js = read("static/js/question_wiki.js");
    assertThat(js).contains("/api/wiki/questions/").contains("/context").contains("/posts").contains("/replies").contains("/api/admin/wiki/questions/materials/search").contains("/materials/upload");
    assertThat(js).doesNotContain("'/home'").doesNotContain("\"/home\"").doesNotContain("/community/posts/").contains("JSON.stringify({ content: content })");
    String listJs = read("static/js/question_wiki_list.js");
    assertThat(listJs).contains("/api/wiki/questions?grade=").contains("/wiki/questions/");
  }

  @Test @DisplayName("HTML 직접 올리기: accept 에 .html + 학습자료 카드는 [HTML] 미리보기를 격리 iframe 으로 연다")
  void htmlUpload_acceptAndSandboxedPreview() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html).contains(".html,.htm").contains("id=\"qwHtmlPreviewModal\"", "id=\"qwHtmlFrame\"").contains("<iframe id=\"qwHtmlFrame\"").contains("sandbox");
    String js = read("static/js/question_wiki.js");
    assertThat(js).contains("previewKind === 'HTML'").contains("data-html-preview", "openHtmlPreview").contains("els.htmlFrame.setAttribute('src'");
    assertThat(js).doesNotContain("htmlFrame.srcdoc").doesNotContain("innerHTML = html");
  }

  @Test @DisplayName("AI 문제제작은 exact-source 비공개 Reference를 서버 무료 AI로 생성하고 기존 Variant로 저장한다")
  void detailTemplate_hasExactSourceServerFirstAiEntrypoint() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html).contains("id=\"qwAiSection\"", "id=\"qwAiToggle\"", "✨ AI 문제제작").contains("data-ai-preset=\"grammar\"", "id=\"qwAiRequest\"", "id=\"qwAiPattern\"").contains("/js/chrome_built_in_ai.js", "/js/question_wiki_ai.js");
    String detailJs = read("static/js/question_wiki.js");
    assertThat(detailJs).contains("questionwiki:context", "questionWikiId: questionWikiId", "context: cache").contains("questionwiki:reload");
    String aiJs = read("static/js/question_wiki_ai.js");
    assertThat(aiJs).contains("window.ChromeBuiltInQuestionAi").contains("FREE_PROVIDER='openrouter-free'").contains("QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST").contains("EXACT-SOURCE REFERENCE").contains("generateFreeServer()").contains("sourceQuestionId:Number(questionWikiId)").contains("dataKind:'GENERATED_PRACTICE'", "category:'VARIANT'", "variant:").contains("questionwiki:reload").doesNotContain("ai.generate(request)").doesNotContain("provider:'auto'", "provider: 'auto'", "qwen", "gemini", "claude", "openai");
    String engineJs = read("static/js/chrome_built_in_ai.js");
    assertThat(engineJs).contains("LanguageModel.availability", "LanguageModel.create").contains("sourceLanguage: 'ko', targetLanguage: 'en'").contains("sourceLanguage: 'en', targetLanguage: 'ko'").contains("PATTERN REFERENCE ONLY", "CURRENT_QUESTION.passage is the only content source").contains("REFERENCE_CONTEXT (MANDATORY supporting analysis/strategy; never a replacement passage)").doesNotContain("fetch(");
  }

  private static String read(String classpath) throws IOException {
    try (var in = new ClassPathResource(classpath).getInputStream()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
  }
}
