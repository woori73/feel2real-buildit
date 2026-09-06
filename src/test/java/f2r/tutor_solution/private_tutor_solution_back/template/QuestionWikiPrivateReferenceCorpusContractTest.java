package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuestionWikiPrivateReferenceCorpusContractTest {
  @Test
  void fullCorpusIsServerOnlyExactSourceAndQwenProviderRemainsExplicit() throws Exception {
    Path root=Path.of("problem-generator/wiki-backend-src/backend");
    String bank=Files.readString(root.resolve("private_reference_bank.py"),StandardCharsets.UTF_8);
    String js=Files.readString(Path.of("src/main/resources/static/js/question_wiki_ai.js"),StandardCharsets.UTF_8);
    String router=Files.readString(root.resolve("ai_router.py"),StandardCharsets.UTF_8);
    String proxy=Files.readString(Path.of("src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/problemgenerator/controller/BuildItWikiProxyController.java"),StandardCharsets.UTF_8);
    String g1=Files.readString(root.resolve("private-reference/grade1-manifest.json"),StandardCharsets.UTF_8);
    String g2=Files.readString(root.resolve("private-reference/grade2-manifest.json"),StandardCharsets.UTF_8);
    assertThat(g1).contains("\"recordCount\": 255", "\"PASS\": 255");
    assertThat(g2).contains("\"recordCount\": 243", "\"PASS\": 243");
    assertThat(bank).contains("exact requested type first", "_same_question(grade,qno)",
      "resolve_shard(grade,qno)", "_load_question(grade,qno)", "refs=select(prompt,limit=1)");
    assertThat(bank).doesNotContain("_load_grade(grade)");
    assertThat(bank).doesNotContain("private_reference_exact_g2", "private_reference_corpus_g1");
    assertThat(js).contains("AI_PROVIDER='openrouter-free'", "provider:AI_PROVIDER", "QWEN_3_7_FLASH");
    assertThat(js).doesNotContain("provider:'auto'", "qwen/qwen3.7-flash", "gemini-3.7-flash", "d.slice(0,120)");
    assertThat(router).contains("DEFAULT_PROVIDER_TIMEOUT_SECONDS=45.0", "QUESTION_WIKI_REFERENCE_NOT_FOUND", "QUESTION_WIKI_FREE_AI_TIMEOUT", "model=QWEN_MODEL", "provider_name=QWEN_PROVIDER");
    assertThat(proxy).contains("QUESTION_WIKI_AI_TIMEOUT = Duration.ofSeconds(52)", "isQuestionWikiAi(targetPath)");
  }

  @Test
  void pythonCorpusValidationAndSelectorTestsPass() throws Exception {
    Process process=new ProcessBuilder("python3","-m","unittest",
      "problem-generator/wiki-backend-src/backend/test_private_reference_bank.py")
      .redirectErrorStream(true).start();
    String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
    assertThat(process.waitFor()).as(output).isZero();
  }
}
