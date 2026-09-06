package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuestionWikiPrivateReferenceCorpusContractTest {
  @Test
  void fullCorpusIsServerOnlyExactSourceAndFreeProviderRemainsExplicit() throws Exception {
    Path root=Path.of("problem-generator/wiki-backend-src/backend");
    String bank=Files.readString(root.resolve("private_reference_bank.py"),StandardCharsets.UTF_8);
    String js=Files.readString(Path.of("src/main/resources/static/js/question_wiki_ai.js"),StandardCharsets.UTF_8);
    String g1=Files.readString(root.resolve("private-reference/grade1-manifest.json"),StandardCharsets.UTF_8);
    String g2=Files.readString(root.resolve("private-reference/grade2-manifest.json"),StandardCharsets.UTF_8);
    assertThat(g1).contains("\"recordCount\": 255", "\"PASS\": 255");
    assertThat(g2).contains("\"recordCount\": 243", "\"PASS\": 243");
    assertThat(bank).contains("exact requested type first", "_same_question(grade, qno)",
      "resolve_shard(grade,qno)", "_load_question(grade,qno)", "[:max(1, min(int(limit), 2))]");
    assertThat(bank).doesNotContain("_load_grade(grade)");
    assertThat(bank).doesNotContain("private_reference_exact_g2", "private_reference_corpus_g1");
    assertThat(js).contains("FREE_PROVIDER='openrouter-free'", "provider:FREE_PROVIDER");
    assertThat(js).doesNotContain("provider:'auto'", "qwen/qwen3.7-flash", "gemini-3.7-flash");
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
