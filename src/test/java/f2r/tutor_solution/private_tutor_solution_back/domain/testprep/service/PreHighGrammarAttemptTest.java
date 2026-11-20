package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreHighGrammarAttemptTest {
  private static final Instant START = Instant.parse("2026-11-20T09:00:00Z");
  private final PreHighGrammarBank bank = new PreHighGrammarBank(new ObjectMapper());

  PreHighGrammarAttemptTest() throws IOException {}

  @Test
  void listsChaptersInTeachingOrderAndSeparatesTheTwoParts() {
    assertThat(bank.list().stream().map(PreHighGrammarBank.ChapterInfo::title)).containsExactly(
        "수일치·시제 일치", "관계사", "접속사", "분사", "수동태", "동명사", "부정사", "비교", "가정법", "특수구문 (기타구문)");
    assertThat(bank.firstPartForPreviousChapter("11")).isEqualTo("1-1");
    assertThat(bank.firstPartForPreviousChapter("3")).isEqualTo("5-1");
    assertThat(bank.get("1-1").questions()).isEqualTo(bank.get("11").questions().subList(0, 20));
    assertThat(bank.get("1-2").questions()).isEqualTo(bank.get("11").questions().subList(20, 40));
    assertThat(bank.get("1-2").questions()).allSatisfy(item -> assertThat(item.topic()).doesNotContain("화법"));
    assertThat(bank.get("1-3")).isNull();
    assertThat(bank.get("11-1")).isNull();
  }

  @Test
  void allThirtyExamsHaveTwentyQuestionsAndFinalsUseDistinctQuestions() {
    var used = new HashSet<String>();
    for (var info : bank.list()) {
      for (int part : List.of(1, 2)) {
        var questions = bank.get(info.number() + "-" + part).questions();
        assertThat(questions).hasSize(20);
        assertThat(questions).allSatisfy(item -> assertThat(used.add(item.completed())).isTrue());
      }
    }

    assertThat(bank.finalRounds()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    for (int round : bank.finalRounds()) {
      var questions = bank.get("final-" + round).questions();
      assertThat(questions).hasSize(20);
      assertThat(questions).allSatisfy(item -> assertThat(used.add(item.completed())).isTrue());
      for (int option = 1; option <= 5; option++) {
        int answer = option;
        assertThat(questions.stream().filter(item -> item.answer() == answer).count()).isEqualTo(4);
      }
    }
    assertThat(used).hasSize(600);
  }

  @Test
  void expiresAtTwentySecondsAndDoesNotApplyAStaleAnswerToTheNextQuestion() {
    var attempt = new PreHighGrammarAttempt(bank.get("5-1"), START);
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
    var exam = bank.get("final-10");
    var attempt = new PreHighGrammarAttempt(exam, START);
    attempt.act("state", 0, 0, START.plusSeconds(20));
    var now = START.plusSeconds(21);
    for (int number = 2; number < 20; number++) {
      var next = attempt.act("answer", number, exam.questions().get(number - 1).answer(), now);
      assertThat(next.results()).isNull();
      now = now.plusSeconds(1);
    }

    var review = attempt.act("answer", 20, exam.questions().getLast().answer(), now);

    assertThat(review.item()).isNull();
    assertThat(review.results()).hasSize(20);
    assertThat(review.results().getFirst().chosen()).isZero();
    assertThat(review.results().getLast().question().principle()).isNotBlank();
    assertThat(review.results().stream().filter(result -> result.chosen() == result.question().answer()).count())
        .isEqualTo(19);
    assertThat(review.elapsedMillis()).isEqualTo(39_000);
  }
}
