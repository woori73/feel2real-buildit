package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PreHighVocabularyAttemptTest {
  private final PreHighVocabularyBank bank = new PreHighVocabularyBank(new ObjectMapper());
  private final Instant start = Instant.parse("2026-11-20T01:00:00Z");

  PreHighVocabularyAttemptTest() throws IOException {}

  @Test
  void allFiftyRoundsHaveThirtyMeaningAndTwentyClozeQuestions() {
    for (int round = 1; round <= 50; round++) {
      var questions = bank.get(round);
      assertThat(questions).hasSize(50);
      assertThat(questions.subList(0, 30)).allMatch(question -> question.seconds() == 3);
      assertThat(questions.subList(30, 50)).allMatch(question -> question.seconds() == 5);
      assertThat(questions.stream().filter(PreHighVocabularyBank.Question::idiom)).hasSize(3);
    }
    assertThat(bank.get(0)).isNull();
    assertThat(bank.get(51)).isNull();
  }

  @Test
  void boundaryChangesToFiveSecondsAndRejectsExpiredAnswer() {
    var questions = bank.get(1);
    var attempt = new PreHighVocabularyAttempt(questions, start);
    assertThat(attempt.state(start).remainingMillis()).isEqualTo(3000);
    assertThat(attempt.state(start).results()).isNull();

    for (int i = 1; i <= 29; i++) {
      attempt.answer(i, questions.get(i - 1).answer(), start.plusMillis(i));
    }
    var next = attempt.answer(30, 1, start.plusMillis(3029));
    assertThat(next.item().number()).isEqualTo(31);
    assertThat(next.item().cloze()).isTrue();
    assertThat(next.remainingMillis()).isEqualTo(5000);
    assertThat(next.score()).isNull();
    var duplicate = attempt.answer(30, 1, start.plusMillis(3030));
    assertThat(duplicate.item().number()).isEqualTo(31);

    for (int i = 31; i <= 50; i++) {
      attempt.answer(i, questions.get(i - 1).answer(), start.plusMillis(3000 + i));
    }
    var result = attempt.state(start.plusMillis(4000));
    assertThat(result.item()).isNull();
    assertThat(result.score()).isEqualTo(98);
    assertThat(result.results().get(29).chosen()).isZero();
    assertThat(result.results().get(30).completed()).doesNotContain("____");
    assertThat(result.results().get(30).question().translation()).isNotBlank();
  }
}
