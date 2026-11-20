package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank.QUESTION_SECONDS;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank.Question;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public final class PreHighGrammarAttempt {
  public record Item(int number, String instruction, String text, List<String> choices) {}
  public record Result(int number, int chosen, Question question) {}
  public record State(UUID id, long remainingMillis, long elapsedMillis, int count, Item item, List<Result> results) {}

  private final UUID id = UUID.randomUUID();
  private final Exam exam;
  private final int[] answers;
  private Instant deadline;
  private int index;
  private long elapsedMillis;

  public PreHighGrammarAttempt(Exam exam, Instant now) {
    this.exam = exam;
    answers = new int[exam.questions().size()];
    deadline = now.plusSeconds(QUESTION_SECONDS);
  }

  public UUID id() { return id; }
  public boolean finished() { return index == answers.length; }

  public State act(String action, int number, int choice, Instant now) {
    boolean expired = !finished() && !now.isBefore(deadline);
    if (expired) advance(deadline, now);
    switch (action) {
      case "state" -> { }
      case "answer" -> {
        if (choice < 1 || choice > 5) throw new IllegalArgumentException("답안 번호는 1~5입니다.");
        if (!expired && !finished() && number == index + 1) {
          answers[index] = choice;
          advance(now, now);
        }
      }
      default -> throw new IllegalArgumentException("지원하지 않는 요청입니다.");
    }
    if (finished()) {
      List<Result> results = IntStream.range(0, answers.length)
          .mapToObj(i -> new Result(i + 1, answers[i], exam.questions().get(i))).toList();
      return new State(id, 0, elapsedMillis, answers.length, null, results);
    }
    Question question = exam.questions().get(index);
    return new State(id, deadline.toEpochMilli() - now.toEpochMilli(), elapsedMillis, answers.length,
        new Item(index + 1, question.instruction(), question.text(), question.choices()), null);
  }

  private void advance(Instant answeredAt, Instant nextStartsAt) {
    elapsedMillis += answeredAt.toEpochMilli() - deadline.minusSeconds(QUESTION_SECONDS).toEpochMilli();
    index++;
    deadline = nextStartsAt.plusSeconds(QUESTION_SECONDS);
  }
}
