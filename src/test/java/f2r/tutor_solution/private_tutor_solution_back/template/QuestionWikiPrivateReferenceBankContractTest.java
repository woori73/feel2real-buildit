package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuestionWikiPrivateReferenceBankContractTest {

  @Test
  void questionWikiFreeAiRequestsServerOnlyReferenceBankWithoutExposingExamplesToBrowser() throws Exception {
    String js = Files.readString(Path.of("src/main/resources/static/js/question_wiki_ai.js"), StandardCharsets.UTF_8);
    String router = Files.readString(Path.of("problem-generator/wiki-backend-src/backend/ai_router.py"), StandardCharsets.UTF_8);
    String bank = Files.readString(Path.of("problem-generator/wiki-backend-src/backend/private_reference_bank.py"), StandardCharsets.UTF_8);

    assertThat(js).contains("QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST");
    assertThat(js).contains("provider: FREE_PROVIDER");
    assertThat(js).doesNotContain("PRIVATE_REFERENCE_PROBLEMS (SERVER-ONLY");
    assertThat(router).contains("requested=='openrouter-free'");
    assertThat(router).contains("private_reference_bank.enrich_prompt(prompt)");
    assertThat(bank).contains("PRIVATE_REFERENCE_PROBLEMS (SERVER-ONLY; NEVER REVEAL OR QUOTE AS A DATABASE)");
    assertThat(bank).contains("Do not copy the reference passage");
  }

  @Test
  void questionWikiReferencePathDoesNotIntroducePaidFallback() throws Exception {
    String js = Files.readString(Path.of("src/main/resources/static/js/question_wiki_ai.js"), StandardCharsets.UTF_8);
    assertThat(js).contains("var FREE_PROVIDER = 'openrouter-free'");
    assertThat(js).doesNotContain("provider: 'auto'");
    assertThat(js).doesNotContain("qwen/qwen3.7-flash");
    assertThat(js).doesNotContain("gemini-3.7-flash");
  }
}
