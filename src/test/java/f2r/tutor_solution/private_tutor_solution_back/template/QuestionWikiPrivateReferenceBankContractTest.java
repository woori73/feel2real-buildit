package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuestionWikiPrivateReferenceBankContractTest {
  @Test
  void questionWikiUsesFullExactSourcePrivateReferenceOnQwenServer() throws Exception {
    String js=Files.readString(Path.of("src/main/resources/static/js/question_wiki_ai.js"),StandardCharsets.UTF_8);
    String router=Files.readString(Path.of("problem-generator/wiki-backend-src/backend/ai_router.py"),StandardCharsets.UTF_8);
    String bank=Files.readString(Path.of("problem-generator/wiki-backend-src/backend/private_reference_bank.py"),StandardCharsets.UTF_8);
    String g2=Files.readString(Path.of("problem-generator/wiki-backend-src/backend/private-reference/grade2-manifest.json"),StandardCharsets.UTF_8);
    assertThat(js).contains("QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST", "provider:AI_PROVIDER", "EXACT-SOURCE", "QWEN_3_7_FLASH");
    assertThat(js).doesNotContain("result = await generateChrome()", "provider:'auto'", "qwen/qwen3.7-flash", "gemini-3.7-flash");
    assertThat(router).contains("private_reference_bank.enrich_prompt(prompt)", "model=QWEN_MODEL", "provider_name=QWEN_PROVIDER");
    assertThat(bank).contains("references from any other sourceQuestionNo are forbidden", "exact requested type first",
      "resolve_shard(grade,qno)", "_load_question(grade,qno)");
    assertThat(bank).doesNotContain("_load_grade(grade)");
    assertThat(g2).contains("\"recordCount\": 243", "g2-q30.json.gz", "g2-q31.json.gz");
  }
}
