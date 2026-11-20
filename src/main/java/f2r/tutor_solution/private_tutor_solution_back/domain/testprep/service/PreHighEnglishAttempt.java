package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.READING_SECONDS;
import static f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.WORD_SECONDS;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Mark;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Round;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class PreHighEnglishAttempt {
  public enum Phase { READING, WORD_READY, WORDS, REVIEW }
  public record Item(int number, String text, List<String> choices) {}
  public record Result(int number, int chosen, int answer, String explanation, String word, String meaning, List<Mark> marks) {}
  public record State(UUID id, Phase phase, long remainingMillis, long elapsedMillis, int[] readingAnswers, int wordCount,
      Item word, List<Result> readingResults, List<Result> wordResults) {}

  private final UUID id = UUID.randomUUID();
  private final Round round;
  private final int[] readingAnswers;
  private final int[] wordAnswers;
  private Phase phase = Phase.READING;
  private Instant deadline;
  private int wordIndex;
  private long elapsedMillis;

  public PreHighEnglishAttempt(Round round, Instant now) {
    this.round = round;
    this.readingAnswers = new int[round.reading().size()];
    this.wordAnswers = new int[round.words().size()];
    deadline = now.plusSeconds(READING_SECONDS);
  }

  public UUID id() { return id; }
  public Round round() { return round; }

  public synchronized State act(String action, int number, int choice, Instant now) {
    expire(now);
    switch (action) {
      case "state" -> { }
      case "read-answer" -> {
        if (phase == Phase.READING && choice >= 1 && choice <= 5 && number >= 1 && number <= readingAnswers.length) {
          readingAnswers[number - 1] = choice;
        }
      }
      case "reading-done" -> {
        if (phase == Phase.READING) finishReading(now);
      }
      case "words-start" -> {
        if (phase == Phase.WORD_READY) {
          phase = Phase.WORDS;
          deadline = now.plusSeconds(WORD_SECONDS);
        }
      }
      case "word-answer" -> {
        if (phase == Phase.WORDS && number == wordIndex + 1 && choice >= 1 && choice <= 5) {
          wordAnswers[wordIndex] = choice;
          advanceWord(now);
        }
      }
      default -> throw new IllegalArgumentException("지원하지 않는 요청입니다.");
    }
    return state(now);
  }

  private void expire(Instant now) {
    if (now.isBefore(deadline)) return;
    if (phase == Phase.READING) finishReading(now);
    else if (phase == Phase.WORDS) advanceWord(now);
  }

  private void finishReading(Instant now) {
    elapsedMillis += elapsedInPeriod(now, READING_SECONDS);
    phase = Phase.WORD_READY;
  }

  private long elapsedInPeriod(Instant now, int periodSeconds) {
    long duration = periodSeconds * 1000L;
    long remaining = deadline.toEpochMilli() - now.toEpochMilli();
    return Math.clamp(duration - remaining, 0, duration);
  }

  private void advanceWord(Instant now) {
    elapsedMillis += elapsedInPeriod(now, WORD_SECONDS);
    if (++wordIndex == wordAnswers.length) phase = Phase.REVIEW;
    else deadline = now.plusSeconds(WORD_SECONDS);
  }

  private State state(Instant now) {
    Item word = null;
    List<Result> readingResults = null;
    List<Result> wordResults = null;
    if (phase == Phase.WORDS) {
      var current = round.words().get(wordIndex);
      word = new Item(wordIndex + 1, current.text(), current.choices());
    }
    if (phase == Phase.REVIEW) {
      readingResults = java.util.stream.IntStream.range(0, readingAnswers.length).mapToObj(i -> {
        var item = round.reading().get(i);
        return new Result(i + 1, readingAnswers[i], item.answer(), item.explanation(), null, null, item.marks());
      }).toList();
      wordResults = java.util.stream.IntStream.range(0, wordAnswers.length).mapToObj(i -> {
        var item = round.words().get(i);
        return new Result(i + 1, wordAnswers[i], item.answer(), null, item.text(), item.choices().get(item.answer() - 1), null);
      }).toList();
    }
    long remaining = (phase == Phase.READING || phase == Phase.WORDS) ? deadline.toEpochMilli() - now.toEpochMilli() : 0;
    return new State(id, phase, remaining, elapsedMillis, Arrays.copyOf(readingAnswers, readingAnswers.length),
        wordAnswers.length, word, readingResults, wordResults);
  }
}
