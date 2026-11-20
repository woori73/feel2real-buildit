package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class PreHighVocabularyBank {
  public static final int ROUND_COUNT = 50;
  public static final int GRADE_TWO_ROUND_COUNT = 40;
  public static final int QUESTION_COUNT = 50;
  public static final int MEANING_COUNT = 30;

  public record Question(String term, String meaning, String sentence, String translation,
                         boolean idiom, List<String> choices, int answer) {
    public int seconds() { return sentence == null ? 3 : 5; }

    public String completed() {
      return sentence == null ? null : sentence.replace("____", choices.get(answer - 1));
    }
  }

  private final List<List<Question>> gradeOne;
  private final List<List<Question>> gradeTwo;

  public PreHighVocabularyBank(ObjectMapper json) throws IOException {
    gradeOne = load(json, "vocabulary", true);
    gradeTwo = load(json, "vocabulary-grade2", false);
  }

  private static List<List<Question>> load(ObjectMapper json, String directory, boolean gradeOne) throws IOException {
    int count = gradeOne ? ROUND_COUNT : GRADE_TWO_ROUND_COUNT;
    List<List<Question>> loaded = new ArrayList<>(count);
    for (int start = 1; start <= count; start += 5) {
      String path = String.format(Locale.ROOT, "pre-high-english/%s/rounds-%02d.json", directory, start);
      try (var input = new ClassPathResource(path).getInputStream()) {
        var type = json.getTypeFactory().constructCollectionType(List.class, Question.class);
        var groupType = json.getTypeFactory().constructCollectionType(List.class, type);
        List<List<Question>> block = json.readValue(input, groupType);
        if (block.size() != 5 || block.stream().anyMatch(round -> !validRound(round, gradeOne))) {
          throw new IllegalStateException("예비고1 어휘 자료의 문항 구성을 확인해 주세요. " + path);
        }
        block.forEach(round -> loaded.add(List.copyOf(round)));
      }
    }
    return List.copyOf(loaded);
  }

  private static boolean validRound(List<Question> round, boolean gradeOne) {
    if (round.size() != QUESTION_COUNT || round.stream().map(Question::term).distinct().count() != QUESTION_COUNT
        || (gradeOne && round.stream().filter(Question::idiom).count() != 3)) return false;
    for (int i = 0; i < round.size(); i++) {
      Question q = round.get(i);
      if (q.term().isBlank() || q.meaning().isBlank() || q.choices().size() != 5
          || q.choices().stream().distinct().count() != 5 || q.choices().stream().anyMatch(String::isBlank)
          || q.answer() < 1 || q.answer() > 5) return false;
      if (i < MEANING_COUNT) {
        if (q.sentence() != null || q.translation() != null || (gradeOne && q.idiom())
            || !q.meaning().equals(q.choices().get(q.answer() - 1))) return false;
      } else if (q.sentence() == null || q.sentence().indexOf("____") == -1
          || q.sentence().indexOf("____") != q.sentence().lastIndexOf("____")
          || q.translation() == null || q.translation().isBlank()) return false;
    }
    return true;
  }

  public List<Question> get(int round) {
    return get(4, round);
  }

  public List<Question> get(int part, int round) {
    if (round < 1 || round > ROUND_COUNT) return null;
    return switch (part) {
      case 4 -> gradeOne.get(round - 1);
      case 7 -> round <= gradeTwo.size() ? gradeTwo.get(round - 1) : null;
      default -> null;
    };
  }
}
