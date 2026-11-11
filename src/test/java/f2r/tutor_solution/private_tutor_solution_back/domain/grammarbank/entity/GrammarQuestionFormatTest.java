package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

class GrammarQuestionFormatTest {
  @ParameterizedTest
  @EnumSource(GrammarQuestionFormat.class)
  void givenCanonicalName_whenParsed_thenRetainsItsFormat(GrammarQuestionFormat format) {
    assertThat(GrammarQuestionFormat.fromRaw("  " + format.name().toLowerCase(java.util.Locale.ROOT) + "  "))
        .isEqualTo(format);
  }

  @ParameterizedTest
  @CsvSource({
      "incorrect sentence, INCORRECT_SENTENCE",
      "error correction, ERROR_CORRECTION",
      "correct sentence, CORRECT_SENTENCE",
      "어법상 틀린 문장, INCORRECT_SENTENCE",
      "오류 수정, ERROR_CORRECTION",
      "어법상 옳은 문장, CORRECT_SENTENCE",
      "단어 배열, SENTENCE_ORDER"
  })
  void givenLegacyLabel_whenParsed_thenRetainsItsMeaning(String input, GrammarQuestionFormat expected) {
    assertThat(GrammarQuestionFormat.fromRaw(input)).isEqualTo(expected);
  }

  @Test
  void givenMissingOrUnknownLabel_whenParsed_thenPreservesFallbacks() {
    assertThat(GrammarQuestionFormat.fromRaw(null)).isNull();
    assertThat(GrammarQuestionFormat.fromRaw(" ")).isNull();
    assertThat(GrammarQuestionFormat.fromRaw("unrecognized-format")).isEqualTo(GrammarQuestionFormat.OTHER);
  }
}
