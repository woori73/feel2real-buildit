package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionWikiContractTest {

  private String read(String relativePath) throws IOException {
    return Files.readString(Path.of("src/main/resources").resolve(relativePath), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("문항별 Wiki 화면은 공통 Context와 토론/변형문제 영역을 제공한다")
  void detailTemplate_hasQuestionWikiContracts() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html)
        .contains("question_wiki.css")
        .contains("question_wiki.js")
        .contains("id=\"qwThreadList\"")
        .contains("id=\"qwVariantList\"");
  }

  @Test
  @DisplayName("문항 상세 JS는 현재 문항 Context를 공유하고 갱신 이벤트를 지원한다")
  void detailJs_dispatchesCurrentQuestionContext() throws IOException {
    String js = read("static/js/question_wiki.js");
    assertThat(js)
        .contains("questionwiki:context")
        .contains("questionWikiId: questionWikiId")
        .contains("context: cache")
        .contains("questionwiki:reload");
  }

  @Test
  @DisplayName("AI 문제제작은 exact-source 비공개 Reference를 서버 무료 AI로 생성하고 기존 Variant로 저장한다")
  void detailTemplate_hasExactSourceServerFirstAiEntrypoint() throws IOException {
    String html = read("templates/public/question_wiki.html");
    assertThat(html)
        .contains("id=\"qwAiSection\"", "id=\"qwAiToggle\"", "✨ AI 문제제작")
        .contains("data-ai-preset=\"grammar\"", "id=\"qwAiRequest\"", "id=\"qwAiPattern\"")
        .contains("/js/chrome_built_in_ai.js", "/js/question_wiki_ai.js");

    String aiJs = read("static/js/question_wiki_ai.js");
    assertThat(aiJs)
        .contains("window.ChromeBuiltInQuestionAi")
        .contains("FREE_PROVIDER='openrouter-free'")
        .contains("QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST")
        .contains("EXACT-SOURCE REFERENCE")
        .contains("generateFreeServer()")
        .contains("sourceQuestionId:Number(questionWikiId)")
        .contains("dataKind:'GENERATED_PRACTICE'", "category:'VARIANT'", "variant:")
        .contains("questionwiki:reload")
        .doesNotContain("ai.generate(request)")
        .doesNotContain("provider:'auto'", "provider: 'auto'", "qwen", "gemini", "claude", "openai");

    String engineJs = read("static/js/chrome_built_in_ai.js");
    assertThat(engineJs)
        .contains("LanguageModel.availability", "LanguageModel.create")
        .contains("sourceLanguage: 'ko', targetLanguage: 'en'")
        .contains("sourceLanguage: 'en', targetLanguage: 'ko'")
        .contains("PATTERN REFERENCE ONLY", "CURRENT_QUESTION.passage is the only content source")
        .contains("REFERENCE_CONTEXT (MANDATORY supporting analysis/strategy; never a replacement passage)")
        .doesNotContain("fetch(");
  }

  @Test
  @DisplayName("문항 Wiki HTML 미리보기는 raw HTML을 직접 주입하지 않는다")
  void preview_doesNotInjectRawHtml() throws IOException {
    String js = read("static/js/question_wiki.js");
    assertThat(js).doesNotContain("htmlFrame.srcdoc").doesNotContain("innerHTML = html");
  }
}
