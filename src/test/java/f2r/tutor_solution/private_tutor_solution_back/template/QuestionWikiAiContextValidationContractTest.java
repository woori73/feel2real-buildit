package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuestionWikiAiContextValidationContractTest {

  private static String source(String file) throws Exception {
    return Files.readString(Path.of("src/main/resources/static/js/" + file), StandardCharsets.UTF_8);
  }

  @Test
  void chromeAndFreeFallbackShareCanonicalCurrentQuestionContext() throws Exception {
    String engine = source("chrome_built_in_ai.js");
    String ui = source("question_wiki_ai.js");

    assertThat(engine).contains("function buildCanonicalContext(rawContext, sourceQuestionId)");
    assertThat(engine).contains("currentQuestion:");
    assertThat(engine).contains("passage: sourceText");
    assertThat(ui).contains("ai.buildCanonicalContext(event.detail.context, questionWikiId)");
    assertThat(ui).contains("contextPack: contextPack");
    assertThat(ui).contains("cloudMode: true");
  }

  @Test
  void missingPassageStopsBeforeEitherAiCallAndChangingQuestionClearsPreview() throws Exception {
    String engine = source("chrome_built_in_ai.js");
    String ui = source("question_wiki_ai.js");

    assertThat(engine).contains("function assertUsableContext(context)");
    assertThat(engine).contains("현재 문항의 원문 지문을 불러오지 못했습니다.");
    assertThat(ui.indexOf("ai.assertUsableContext(contextPack)"))
        .isLessThan(ui.indexOf("if (ai && canUseChrome())"));
    assertThat(ui).contains("preview = null;", "els.preview.hidden = true;", "els.save.disabled = true;");
  }

  @Test
  void validatesSelectedTypeSourceIdStructureFailureTextAndSourceOverlap() throws Exception {
    String engine = source("chrome_built_in_ai.js");

    assertThat(engine).contains("Number(question.sourceQuestionId) !== Number(request.sourceQuestionId)");
    assertThat(engine).contains("String(question.type || '') !== String(request.preset)");
    assertThat(engine).contains("question.choices.length !== 5");
    assertThat(engine).contains("answerInRange(question.answer, question.choices)");
    assertThat(engine).contains("request.preset === 'blank'");
    assertThat(engine).contains("FAILURE_TEXT.test(combined)");
    assertThat(engine).contains("hasSourceOverlap(source, passage)");
    assertThat(engine).contains("PATTERN REFERENCE ONLY");
  }

  @Test
  void freeFallbackIsExplicitAndHasNoPaidRetry() throws Exception {
    String ui = source("question_wiki_ai.js");

    assertThat(ui).contains("var FREE_PROVIDER = 'openrouter-free';", "provider: FREE_PROVIDER");
    assertThat(ui).contains("if (response.status === 429)");
    assertThat(ui).contains("write the explanation in Korean");
    assertThat(ui).doesNotContain("provider: 'auto'", "provider: 'qwen'", "provider: 'gemini'");
    assertThat(ui).doesNotContain("qwen3.7", "Gemini");
  }

  @Test
  void saveIsOnlyEnabledAfterValidatedPreview() throws Exception {
    String ui = source("question_wiki_ai.js");
    int validation = ui.indexOf("ai.validateQuestion(question, request)");
    int preview = ui.indexOf("els.save.disabled = false;");

    assertThat(validation).isGreaterThan(0);
    assertThat(preview).isGreaterThan(validation);
    assertThat(ui).contains("dataKind: 'GENERATED_PRACTICE'", "category: 'VARIANT'");
  }
}
