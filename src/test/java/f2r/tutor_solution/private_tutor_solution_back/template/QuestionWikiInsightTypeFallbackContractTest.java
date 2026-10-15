package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuestionWikiInsightTypeFallbackContractTest {
  @Test void exactDbThenSameTypeInsightThenContract() throws Exception {
    String bank=Files.readString(Path.of("problem-generator/wiki-backend-src/backend/private_reference_bank.py"));
    String insight=Files.readString(Path.of("problem-generator/wiki-backend-src/backend/insight_type_reference.py"));
    assertThat(bank).contains("insight=\"\" if refs else insight_type_reference.extract(prompt,wanted)",
      "REQUESTED-TYPE INSIGHT (PATTERN ONLY)", "A DB variant of a different type is forbidden as fallback",
      "no requested-type Insight section was found", "CURRENT QUESTION is the ONLY content/material source");
    assertThat(insight).contains("sourceAnalysis", "변형문제 인사이트", "def extract(prompt, requested_type)", "글의 순서", "서술형");
  }
  @Test void orderUsesItsOwnLabelContract() throws Exception {
    String bank=Files.readString(Path.of("problem-generator/wiki-backend-src/backend/private_reference_bank.py"));
    assertThat(bank).contains("This rule does NOT apply to paragraph-order choices",
      "if wanted!='order' and len(labels)>=2", "ORDER_CHOICE_PERMUTATION_MISSING",
      "all three labels exactly once in a valid permutation");
  }
}
