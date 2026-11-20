package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishAttempt.Phase;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Reading;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Round;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Word;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreHighEnglishAttemptTest {

  private static final Instant START = Instant.parse("2026-11-20T05:00:00Z");
  private static final List<String> CHOICES = List.of("고르다", "읽다", "보다", "듣다", "쓰다");
  private static final Round ROUND = new Round(1,
      Collections.nCopies(6, new Reading("image", null, 2, "읽기 해설",
          List.of(new PreHighEnglishStore.Mark("question", "evidence", 100, 200, 300, 400)))),
      List.of(new Word("read", CHOICES, 2), new Word("write", CHOICES, 5)));

  @Test
  void readingDeadlineLocksAnswersAndKeepsExplanationsHidden() {
    var attempt = new PreHighEnglishAttempt(ROUND, START);
    assertEquals(540_000, attempt.act("state", 0, 0, START).remainingMillis());
    var saved = attempt.act("read-answer", 1, 2, START.plusSeconds(539));
    assertEquals(2, saved.readingAnswers()[0]);
    assertNull(saved.readingResults());

    var expired = attempt.act("read-answer", 2, 2, START.plusSeconds(540));
    assertEquals(Phase.WORD_READY, expired.phase());
    assertEquals(0, expired.readingAnswers()[1]);
    assertEquals(540_000, expired.elapsedMillis());
    assertNull(expired.readingResults());
  }

  @Test
  void lateWordAnswerIsBlankAndNextWordHasItsOwnFiveSeconds() {
    var attempt = new PreHighEnglishAttempt(ROUND, START);
    attempt.act("reading-done", 0, 0, START);
    var first = attempt.act("words-start", 0, 0, START);
    assertEquals(1, first.word().number());
    assertEquals(5_000, first.remainingMillis());
    assertNull(first.wordResults());

    var second = attempt.act("word-answer", 1, 2, START.plusSeconds(5));
    assertEquals(2, second.word().number());
    assertEquals(5_000, second.remainingMillis());

    var review = attempt.act("word-answer", 2, 5, START.plusSeconds(9));
    assertEquals(Phase.REVIEW, review.phase());
    assertEquals(0, review.wordResults().get(0).chosen());
    assertEquals(5, review.wordResults().get(1).chosen());
    assertEquals("쓰다", review.wordResults().get(1).meaning());
    assertEquals("읽기 해설", review.readingResults().get(0).explanation());
    assertEquals(ROUND.reading().get(0).marks(), review.readingResults().get(0).marks());
  }

  @Test
  void reviewTimeAddsActivePeriodsAndExcludesBreakBeforeWords() {
    var attempt = new PreHighEnglishAttempt(ROUND, START);
    attempt.act("reading-done", 0, 0, START.plusMillis(12_500));
    attempt.act("words-start", 0, 0, START.plusSeconds(30));
    attempt.act("word-answer", 1, 2, START.plusMillis(31_500));
    var afterTimeout = attempt.act("state", 0, 0, START.plusSeconds(40));

    assertEquals(Phase.REVIEW, afterTimeout.phase());
    assertEquals(19_000, afterTimeout.elapsedMillis());
    assertEquals(19_000, attempt.act("state", 0, 0, START.plusSeconds(99)).elapsedMillis());
  }

}
