package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Mark;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Reading;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Round;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Word;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 예비고1 디지털 수능 영어 모의고사 2부 — 고1 실전 독해 모의고사(18~45번 28문항, 45분) 10회.
 * 자료는 웹에서 바로 열리지 않는 classpath:prehigh-part2/rNN/에 둔다(round.json, 문항 그림 webp, 문제지 paper.pdf).
 * 그림은 응시 중일 때만 API로 내려 주고, 정답·해설·근거 표시는 채점(REVIEW) 뒤에만 보인다.
 */
@Service
public class PreHighPart2Store {
  public static final int ROUND_COUNT = 10;
  public static final int FIRST_NUMBER = 18;
  public static final int READING_COUNT = 28;
  public static final int READING_SECONDS = 45 * 60;
  public static final int WORD_COUNT = 50;
  private static final String BASE = "prehigh-part2/";

  /** round.json 형식. 그림은 파일 이름(q18.webp, p41-42.webp)으로 적는다. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record ReadingFile(String image, String passage, int answer, String explanation, List<Mark> marks) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  record RoundFile(int number, String source, List<ReadingFile> reading, List<Word> words) {}

  /** 회차 목록 한 줄: 회차 번호와 원본 시험. */
  public record Info(int number, String source) {}

  private final ObjectMapper json;
  private final Map<Integer, Round> rounds = new ConcurrentHashMap<>();
  private volatile List<Info> infos;

  public PreHighPart2Store(ObjectMapper json) {
    this.json = json;
  }

  /** 시험지 번호 18~45. 화면 문항 번호와 답안지 번호에 쓴다. */
  public static List<Integer> numbers() {
    return IntStream.range(0, READING_COUNT).map(i -> FIRST_NUMBER + i).boxed().toList();
  }

  public List<Info> list() {
    var current = infos;
    if (current == null) {
      List<Info> loaded = new ArrayList<>();
      for (int n = 1; n <= ROUND_COUNT; n++) {
        RoundFile file = readFile(n);
        loaded.add(new Info(n, file.source()));
      }
      current = infos = List.copyOf(loaded);
    }
    return current;
  }

  /** 회차 자료(그림은 base64). 없는 회차면 null. */
  public Round get(int number) {
    if (number < 1 || number > ROUND_COUNT) return null;
    return rounds.computeIfAbsent(number, this::load);
  }

  /** 내려받기용 문제지 PDF. */
  public byte[] paper(int number) {
    if (number < 1 || number > ROUND_COUNT) return null;
    return bytes(String.format("r%02d/paper.pdf", number));
  }

  private Round load(int number) {
    RoundFile file = readFile(number);
    validate(number, file);
    String dir = String.format("r%02d/", number);
    List<Reading> reading = file.reading().stream().map(item -> new Reading(
        Base64.getEncoder().encodeToString(bytes(dir + item.image())),
        item.passage() == null ? null : Base64.getEncoder().encodeToString(bytes(dir + item.passage())),
        item.answer(), item.explanation(), item.marks())).toList();
    return new Round(number, reading, file.words());
  }

  private RoundFile readFile(int number) {
    try {
      return json.readValue(bytes(String.format("r%02d/round.json", number)), RoundFile.class);
    } catch (IOException error) {
      throw new UncheckedIOException(error);
    }
  }

  /** 자료가 잘못되면 시작부터 막는다(문항 수, 정답 범위, 근거 표시 좌표, 어휘 선택지). */
  static void validate(int number, RoundFile file) {
    if (file == null || file.number() != number || file.reading() == null || file.reading().size() != READING_COUNT
        || file.words() == null || file.words().size() != WORD_COUNT) {
      throw new IllegalStateException(number + "회 2부 자료의 문항 수를 확인해 주세요.");
    }
    for (ReadingFile item : file.reading()) {
      if (item == null || item.image() == null || item.answer() < 1 || item.answer() > 5
          || item.explanation() == null || item.explanation().isBlank()
          || item.marks() == null || item.marks().size() > 30
          || item.marks().stream().anyMatch(mark -> !validMark(mark, item.passage() != null))) {
        throw new IllegalStateException(number + "회 2부 독해 자료를 확인해 주세요.");
      }
    }
    for (Word word : file.words()) {
      if (word == null || word.text() == null || word.text().isBlank() || word.answer() < 1 || word.answer() > 5
          || word.choices() == null || word.choices().size() != 5 || new HashSet<>(word.choices()).size() != 5) {
        throw new IllegalStateException(number + "회 2부 어휘 자료를 확인해 주세요.");
      }
    }
  }

  /** 2부는 장문(41~45번)의 밑줄 낱말에도 정답 표시를 하므로 공통 지문(passage)의 answer 표시를 허용한다. */
  static boolean validMark(Mark mark, boolean hasPassage) {
    return mark != null
        && ("question".equals(mark.part()) || "passage".equals(mark.part()) && hasPassage)
        && ("evidence".equals(mark.kind()) || "answer".equals(mark.kind()))
        && mark.x() >= 0 && mark.y() >= 0 && mark.width() > 0 && mark.height() > 0
        && (long) mark.x() + mark.width() <= 10_000 && (long) mark.y() + mark.height() <= 10_000;
  }

  private static byte[] bytes(String path) {
    try (InputStream in = new ClassPathResource(BASE + path).getInputStream()) {
      return in.readAllBytes();
    } catch (IOException error) {
      throw new UncheckedIOException(error);
    }
  }
}
