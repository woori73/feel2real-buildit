package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class PreHighGrammarBank {
  public static final int QUESTION_COUNT = 20;
  private static final List<Integer> CHAPTER_ORDER = List.of(11, 9, 8, 6, 3, 5, 4, 7, 10, 12);
  private static final int FINAL_ROUND_COUNT = 10;
  private static final String DIRECTORY = "pre-high-english/grammar/";
  public static final int QUESTION_SECONDS = 20;

  public record Chapter(int number, String title, List<Question> questions) {}
  public record ChapterInfo(int number, String title) {}
  public record Exam(String title, List<Question> questions) {}
  public record Question(String instruction, String text, List<String> choices, int answer, String completed,
      String translation, String topic, String principle, String strategy, String evidence, List<String> reasons,
      List<String> choiceTranslations) {}

  private final List<ChapterInfo> chapters;
  private final List<Integer> finalRounds;
  private final Map<String, Exam> exams;
  private final Map<String, String> previousChapterLinks;

  public PreHighGrammarBank(ObjectMapper json) throws IOException {
    List<ChapterInfo> loadedChapters = new ArrayList<>();
    List<Integer> loadedRounds = new ArrayList<>();
    Map<String, Exam> loadedExams = new HashMap<>();
    Map<String, String> oldLinks = new HashMap<>();
    for (int index = 0; index < CHAPTER_ORDER.size(); index++) {
      int number = index + 1;
      int originalNumber = CHAPTER_ORDER.get(index);
      Chapter chapter = load(json, "ch", originalNumber, 2 * QUESTION_COUNT);
      String title = switch (number) {
        case 1 -> "수일치·시제 일치";
        case 10 -> "특수구문 (기타구문)";
        default -> chapter.title();
      };
      String heading = "CH " + number + " · " + title;
      List<Question> questions = chapter.questions();
      loadedChapters.add(new ChapterInfo(number, title));
      String originalKey = String.valueOf(originalNumber);
      loadedExams.put(originalKey, new Exam(chapter.title(), questions));
      loadedExams.put(number + "-1", new Exam(heading + " · 1부", questions.subList(0, QUESTION_COUNT)));
      loadedExams.put(number + "-2", new Exam(heading + " · 2부", questions.subList(QUESTION_COUNT, questions.size())));
      oldLinks.put(originalKey, number + "-1");
    }
    for (int number = 1; number <= FINAL_ROUND_COUNT; number++) {
      Chapter round = load(json, "final", number, QUESTION_COUNT);
      loadedRounds.add(number);
      loadedExams.put("final-" + number, new Exam(round.title(), round.questions()));
    }
    chapters = List.copyOf(loadedChapters);
    finalRounds = List.copyOf(loadedRounds);
    exams = Map.copyOf(loadedExams);
    previousChapterLinks = Map.copyOf(oldLinks);
  }

  private static Chapter load(ObjectMapper json, String prefix, int number, int questionCount) throws IOException {
    String path = DIRECTORY + String.format(Locale.ROOT, "%s%02d.json", prefix, number);
    try (var input = new ClassPathResource(path).getInputStream()) {
      Chapter data = json.readValue(input, Chapter.class);
      if (data.number() != number || data.questions().size() != questionCount
          || data.questions().stream().anyMatch(question -> question.choices().size() != 5
              || question.choices().stream().distinct().count() != 5 || question.reasons().size() != 5
              || question.choiceTranslations().size() != 5 || question.answer() < 1 || question.answer() > 5)) {
        throw new IllegalStateException("예비고1 어법 문항 자료의 구성을 확인해 주세요. " + path);
      }
      return new Chapter(data.number(), data.title(), List.copyOf(data.questions()));
    }
  }

  public List<ChapterInfo> list() {
    return chapters;
  }

  public List<Integer> finalRounds() {
    return finalRounds;
  }

  public String firstPartForPreviousChapter(String key) {
    return previousChapterLinks.get(key);
  }

  public Exam get(String key) {
    return exams.get(key);
  }
}
