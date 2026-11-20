package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighVocabularyBank.Question;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public final class PreHighVocabularyAttempt {
  public record Item(int number, boolean cloze, String prompt, List<String> choices, int seconds, int selected) {}
  public record Result(int number, int chosen, Question question, String completed) {
    boolean correct() { return chosen == question.answer(); }
  }
  public record State(UUID id, long remainingMillis, int count, Item item, Integer score, List<Result> results) {}

  private final UUID id = UUID.randomUUID();
  private final List<Question> questions;
  private final int[] answers;
  private final boolean confirmChoice;
  private int index;
  private Instant deadline;

  public PreHighVocabularyAttempt(List<Question> questions, Instant now) {
    this(questions, now, false);
  }

  public PreHighVocabularyAttempt(List<Question> questions, Instant now, boolean confirmChoice) {
    this.confirmChoice = confirmChoice;
    this.questions = List.copyOf(questions);
    this.answers = new int[questions.size()];
    deadline = now.plusSeconds(seconds(questions.getFirst()));
  }

  public UUID id() { return id; }
  public boolean finished() { return index == answers.length; }

  public State state(Instant now) {
    if (!finished() && !now.isBefore(deadline)) advance(now);
    if (finished()) {
      var results = IntStream.range(0, answers.length)
          .mapToObj(i -> new Result(i + 1, answers[i], questions.get(i), questions.get(i).completed()))
          .toList();
      int correct = (int) results.stream().filter(Result::correct).count();
      return new State(id, 0, answers.length, null, correct * 2, results);
    }
    Question question = questions.get(index);
    boolean cloze = question.sentence() != null;
    var item = new Item(index + 1, cloze, cloze ? question.sentence() : question.term(),
        question.choices(), seconds(question), answers[index]);
    return new State(id, Duration.between(now, deadline).toMillis(), answers.length, item, null, null);
  }

  public State answer(int number, int choice, Instant now) {
    if (choice < 1 || choice > 5) throw new IllegalArgumentException("답안 번호는 1~5입니다.");
    if (!finished() && number == index + 1 && now.isBefore(deadline)) {
      answers[index] = choice;
      if (!confirmChoice) advance(now);
    }
    return state(now);
  }

  public State next(int number, Instant now) {
    if (confirmChoice && !finished() && number == index + 1 && answers[index] != 0) advance(now);
    return state(now);
  }

  private int seconds(Question question) {
    if (confirmChoice) return question.sentence() == null ? 5 : 10;
    return question.seconds();
  }

  private void advance(Instant now) {
    index++;
    if (!finished()) deadline = now.plusSeconds(seconds(questions.get(index)));
  }
}
