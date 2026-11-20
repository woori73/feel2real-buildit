package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class PreHighGrammarBank {
  public static final int QUESTION_COUNT = 40;
  public static final int QUESTION_SECONDS = 20;

  public record Chapter(int number, String title, List<Question> questions) {}
  public record ChapterInfo(int number, String title) {}
  public record Question(String instruction, String text, List<String> choices, int answer, String completed,
      String translation, String topic, String principle, String strategy, String evidence, List<String> reasons,
      List<String> choiceTranslations) {}

  private final List<Chapter> chapters;

  public PreHighGrammarBank(ObjectMapper json) throws IOException {
    List<Chapter> loaded = new ArrayList<>();
    for (int number = 3; number <= 12; number++) {
      String path = String.format(Locale.ROOT, "pre-high-english/grammar/ch%02d.json", number);
      try (var input = new ClassPathResource(path).getInputStream()) {
        Chapter chapter = json.readValue(input, Chapter.class);
        if (chapter.number() != number || chapter.questions().size() != QUESTION_COUNT
            || chapter.questions().stream().anyMatch(question -> question.choices().size() != 5
                || question.choices().stream().distinct().count() != 5 || question.reasons().size() != 5
                || question.choiceTranslations().size() != 5 || question.answer() < 1 || question.answer() > 5)) {
          throw new IllegalStateException("예비고1 어법 문항 자료의 구성을 확인해 주세요. " + path);
        }
        loaded.add(chapter);
      }
    }
    chapters = List.copyOf(loaded);
  }

  public List<ChapterInfo> list() {
    return chapters.stream().map(chapter -> new ChapterInfo(chapter.number(), chapter.title())).toList();
  }

  public Chapter get(int number) {
    return chapters.stream().filter(chapter -> chapter.number() == number).findFirst().orElse(null);
  }
}
