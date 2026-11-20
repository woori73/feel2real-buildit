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
      assertThat(questions.subList(0, 30)).allMatch(question -> question.seconds() == 5);
      assertThat(questions.subList(30, 50)).allMatch(question -> question.seconds() == 10);
      assertThat(questions.stream().filter(PreHighVocabularyBank.Question::idiom)).hasSize(3);
    }
    assertThat(bank.get(0)).isNull();
    assertThat(bank.get(51)).isNull();
  }

  @Test
  void gradeTwoRoundsProvideIndependentTimedQuestionsAndReview() {
    for (int round = 1; round <= 40; round++) {
      var questions = bank.get(7, round);
      assertThat(questions).isNotEqualTo(bank.get(round));
      var attempt = new PreHighVocabularyAttempt(questions, start);
      for (int i = 0; i < 50; i++) {
        var now = start.plusMillis(i * 100L);
        assertThat(attempt.state(now).item().seconds()).isEqualTo(i < 30 ? 5 : 10);
        attempt.answer(i + 1, questions.get(i).answer(), now);
      }
      var result = attempt.state(start.plusSeconds(5));
      assertThat(result.score()).isEqualTo(100);
      assertThat(result.results().subList(30, 50)).allMatch(r ->
          !r.completed().contains("____") && !r.question().translation().isBlank());
    }
    assertThat(bank.get(7, 0)).isNull();
    assertThat(bank.get(7, 41)).isNull();
  }

  @Test
  void earlyAnswersAdvanceImmediatelyAndExpiredAnswerIsOmittedAtSectionBoundary() {
    var questions = bank.get(1);
    var attempt = new PreHighVocabularyAttempt(questions, start);
    assertThat(attempt.state(start).remainingMillis()).isEqualTo(5000);
    assertThat(attempt.state(start).results()).isNull();

    for (int i = 1; i <= 29; i++) {
      var early = attempt.answer(i, questions.get(i - 1).answer(), start.plusMillis(i));
      assertThat(early.item().number()).isEqualTo(i + 1);
      assertThat(early.remainingMillis()).isEqualTo(5000);
    }
    var next = attempt.answer(30, 1, start.plusMillis(5029));
    assertThat(next.item().number()).isEqualTo(31);
    assertThat(next.item().cloze()).isTrue();
    assertThat(next.remainingMillis()).isEqualTo(10000);
    assertThat(next.score()).isNull();
    var duplicate = attempt.answer(30, 1, start.plusMillis(5030));
    assertThat(duplicate.item().number()).isEqualTo(31);

    for (int i = 31; i <= 50; i++) {
      attempt.answer(i, questions.get(i - 1).answer(), start.plusMillis(5000 + i));
    }
    var result = attempt.state(start.plusMillis(6000));
    assertThat(result.item()).isNull();
    assertThat(result.score()).isEqualTo(98);
    assertThat(result.results().get(29).chosen()).isZero();
    assertThat(result.results().get(30).completed()).doesNotContain("____");
    assertThat(result.results().get(30).question().translation()).isNotBlank();
  }
}
