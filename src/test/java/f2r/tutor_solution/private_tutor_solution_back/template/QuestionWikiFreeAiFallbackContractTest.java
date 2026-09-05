package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuestionWikiFreeAiFallbackContractTest {
    @Test
    void questionWikiFallbackUsesOnlyExplicitOpenRouterFreeProvider() throws Exception {
        String source = Files.readString(
                Path.of("src/main/resources/static/js/question_wiki_ai.js"), StandardCharsets.UTF_8);

        assertThat(source).contains("var FREE_PROVIDER = 'openrouter-free';");
        assertThat(source).contains("provider: FREE_PROVIDER");
        assertThat(source).contains("generationMode = 'OPENROUTER_FREE'");
        assertThat(source).contains("if (response.status === 429)");
        assertThat(source).doesNotContain("provider: 'auto'");
        assertThat(source).doesNotContain("provider: 'qwen'");
        assertThat(source).doesNotContain("provider: 'gemini'");
        assertThat(source).doesNotContain("qwen/qwen3.7-flash");
        assertThat(source).doesNotContain("google/gemini-3.7-flash");
    }
}
