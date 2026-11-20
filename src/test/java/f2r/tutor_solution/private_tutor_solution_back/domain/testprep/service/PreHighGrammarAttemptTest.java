package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PreHighGrammarAttemptTest {
  private static final Instant START = Instant.parse("2026-11-20T09:00:00Z");
  private final PreHighGrammarBank bank = new PreHighGrammarBank(new ObjectMapper());

  PreHighGrammarAttemptTest() throws IOException {}

  @Test
  void loadsFortyQuestionsWithBalancedAnswersInTheTenChapters() {
    assertThat(bank.list()).hasSize(10);
    assertThat(bank.get(1)).isNull();
    assertThat(bank.get(2)).isNull();
    assertThat(bank.get(11).questions()).allSatisfy(item -> assertThat(item.topic()).doesNotContain("화법"));
    for (var info : bank.list()) {
      var questions = bank.get(info.number()).questions();
      assertThat(questions).hasSize(40);
      for (int option = 1; option <= 5; option++) {
        int answer = option;
        assertThat(questions.stream().filter(item -> item.answer() == answer).count()).isEqualTo(8);
      }
    }
  }

  @Test
  void expiresAtTwentySecondsAndDoesNotApplyAStaleAnswerToTheNextQuestion() {
    var attempt = new PreHighGrammarAttempt(bank.get(3), START);
    var first = attempt.act("state", 0, 0, START);

    assertThat(first.remainingMillis()).isEqualTo(20_000);
    assertThat(first.item().number()).isEqualTo(1);
    assertThat(first.results()).isNull();

    var second = attempt.act("answer", 1, 3, START.plusSeconds(20));

    assertThat(second.item().number()).isEqualTo(2);
    assertThat(second.elapsedMillis()).isEqualTo(20_000);
    assertThat(second.remainingMillis()).isEqualTo(20_000);
    assertThat(attempt.act("answer", 1, 3, START.plusSeconds(21)).item().number()).isEqualTo(2);
  }

  @Test
  void includesAllAnswersAndExplanationsOnlyAfterTheLastQuestion() {
    var chapter = bank.get(12);
    var attempt = new PreHighGrammarAttempt(chapter, START);
    attempt.act("state", 0, 0, START.plusSeconds(20));
    var now = START.plusSeconds(21);
    for (int number = 2; number < 40; number++) {
      var next = attempt.act("answer", number, chapter.questions().get(number - 1).answer(), now);
      assertThat(next.results()).isNull();
      now = now.plusSeconds(1);
    }

    var review = attempt.act("answer", 40, chapter.questions().getLast().answer(), now);

    assertThat(review.item()).isNull();
    assertThat(review.results()).hasSize(40);
    assertThat(review.results().getFirst().chosen()).isZero();
    assertThat(review.results().getLast().question().principle()).isNotBlank();
    assertThat(review.results().stream().filter(result -> result.chosen() == result.question().answer()).count())
        .isEqualTo(39);
    assertThat(review.elapsedMillis()).isEqualTo(59_000);
  }
}
