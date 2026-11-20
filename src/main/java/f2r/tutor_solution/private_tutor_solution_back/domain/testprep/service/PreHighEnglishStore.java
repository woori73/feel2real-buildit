package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PreHighEnglishStore {
  public static final int ROUND_COUNT = 35;
  private static final int LEGACY_ROUND_COUNT = 25;
  public static final int READING_COUNT = 6;
  public static final int READING_SECONDS = 90 * READING_COUNT;
  public static final int WORD_SECONDS = 5;
  public static final int MAX_PACKAGE_BYTES = 32 * 1024 * 1024;

  public record Reading(String image, String passage, int answer, String explanation, List<Mark> marks) {}
  public record Mark(String part, String kind, int x, int y, int width, int height) {
    boolean valid(boolean hasPassage) {
      return ("question".equals(part) || "passage".equals(part) && hasPassage)
          && ("evidence".equals(kind) || "answer".equals(kind) && "question".equals(part))
          && x >= 0 && y >= 0 && width > 0 && height > 0
          && (long) x + width <= 10_000 && (long) y + height <= 10_000;
    }
  }
  public record Word(String text, List<String> choices, int answer) {}
  public record Round(int number, List<Reading> reading, List<Word> words) {}
  public record RoundInfo(int number, int words) {}

  private final ObjectMapper json;
  private final Path directory;
  private volatile List<Round> rounds = List.of();

  public PreHighEnglishStore(ObjectMapper json,
      @Value("${app.pre-high-english.dir:${user.home}/pre-high-english}") String directory) throws IOException {
    this.json = json;
    this.directory = Path.of(directory);
    Path current = this.directory.resolve("rounds.json");
    if (Files.isRegularFile(current)) rounds = readPackage(Files.readAllBytes(current));
  }

  public Round get(int number) {
    var current = rounds;
    return number >= 1 && number <= current.size() ? current.get(number - 1) : null;
  }

  public List<RoundInfo> list() {
    return rounds.stream().map(round -> new RoundInfo(round.number(), round.words().size())).toList();
  }

  public synchronized void install(byte[] contents) throws IOException {
    List<Round> replacement = readPackage(contents);
    Files.createDirectories(directory);
    Path incoming = Files.createTempFile(directory, "rounds-", ".json");
    try {
      Files.write(incoming, contents);
      Files.move(incoming, directory.resolve("rounds.json"), StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(incoming);
    }
    rounds = replacement;
  }

  private List<Round> readPackage(byte[] contents) throws IOException {
    if (contents.length > MAX_PACKAGE_BYTES) throw new IllegalArgumentException("자료 용량이 너무 큽니다.");
    List<Round> data = json.readerForListOf(Round.class).readValue(contents);
    if (data == null || (data.size() != LEGACY_ROUND_COUNT && data.size() != ROUND_COUNT)) {
      throw new IllegalArgumentException("회차 25개 또는 35개가 필요합니다.");
    }
    int number = 0;
    for (Round round : data) {
      if (round == null || round.number() != ++number || round.reading() == null
          || round.reading().size() != READING_COUNT || round.words() == null
          || round.words().size() < 5 || round.words().size() > 100) {
        throw new IllegalArgumentException("회차, 독해 문항 수 또는 어휘 문항 수를 확인해 주세요.");
      }
      for (Reading item : round.reading()) {
        if (item == null || !isWebp(item.image()) || (item.passage() != null && !isWebp(item.passage()))
            || !validAnswer(item.answer()) || item.explanation() == null || item.explanation().isBlank()
            || item.marks() != null && (item.marks().size() > 30 || item.marks().stream()
                .anyMatch(mark -> mark == null || !mark.valid(item.passage() != null)))) {
          throw new IllegalArgumentException("독해 문항 자료를 확인해 주세요.");
        }
      }
      for (Word item : round.words()) {
        if (item == null || item.text() == null || item.text().isBlank() || !validAnswer(item.answer())
            || item.choices() == null || item.choices().size() != 5
            || item.choices().stream().anyMatch(choice -> choice == null || choice.isBlank())
            || new HashSet<>(item.choices()).size() != 5) {
          throw new IllegalArgumentException("어휘 문항 자료를 확인해 주세요.");
        }
      }
    }
    return List.copyOf(data);
  }

  private static boolean validAnswer(int answer) { return answer >= 1 && answer <= 5; }

  private static boolean isWebp(String image) {
    if (image == null || image.length() > 300_000) return false;
    byte[] bytes = Base64.getDecoder().decode(image);
    return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
        && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
  }
}
