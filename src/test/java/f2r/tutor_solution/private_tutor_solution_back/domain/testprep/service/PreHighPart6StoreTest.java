package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Mark;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreHighPart6StoreTest {
  private final PreHighPart6Store store = new PreHighPart6Store(new ObjectMapper());

  @Test
  @DisplayName("given 6부 문제지 PDF, when 글자를 뽑으면, then 원본·출처·학평·학력평가·변형과 내부 기록(source)이 없다")
  void paperHasNoSourceWords() throws Exception {
    for (int n = 1; n <= PreHighPart6Store.ROUND_COUNT; n++) {
      // given
      byte[] pdf = store.paper(n);
      // when
      String text;
      try (var doc = Loader.loadPDF(pdf)) {
        text = new PDFTextStripper().getText(doc);
      }
      // then
      assertThat(text).as("%d회", n).contains("28문항", "고2").doesNotContain("원본").doesNotContain("출처")
          .doesNotContain("학평").doesNotContain("학력평가").doesNotContain("변형");
      assertThat(text).as("%d회", n).doesNotContain(store.list().get(n - 1).source());
    }
  }

  @Test
  @DisplayName("given 6부 자료, when list·get하면, then 회차마다 독해 28문항·어휘 50문항이 있다")
  void loadsAllRounds() {
    // given / when
    var infos = store.list();
    // then
    assertThat(infos).hasSize(PreHighPart6Store.ROUND_COUNT);
    for (int n = 1; n <= PreHighPart6Store.ROUND_COUNT; n++) {
      var round = store.get(n);
      assertThat(round.number()).isEqualTo(n);
      assertThat(round.reading()).hasSize(28);
      assertThat(round.words()).hasSize(50);
    }
  }

  @Test
  @DisplayName("given 6부 회차, when 독해 문항을 보면, then 그림은 모두 webp이고 41~45번만 공통 지문이 있으며 해설은 '정답'으로 시작한다")
  void readingItemsHaveImagesAndExplanations() {
    for (int n = 1; n <= PreHighPart6Store.ROUND_COUNT; n++) {
      // given
      var round = store.get(n);
      // when
      var reading = round.reading();
      // then
      assertThat(reading).allSatisfy(item -> {
        assertThat(isWebp(item.image())).isTrue();
        assertThat(item.explanation()).startsWith("정답 ");
      });
      assertThat(reading.subList(0, 23)).allMatch(item -> item.passage() == null);
      assertThat(reading.subList(23, 28)).allMatch(item -> isWebp(item.passage()));
      assertThat(reading.get(23).passage()).isEqualTo(reading.get(24).passage());
      assertThat(reading.get(25).passage()).isEqualTo(reading.get(27).passage()).isNotEqualTo(reading.get(24).passage());
    }
  }

  @Test
  @DisplayName("given 6부 전체, when 근거 표시를 보면, then 모든 문항에 노란 근거와 정답 표시가 있고 좌표는 그림 안에 있다")
  void everyItemHasEvidenceAndAnswerMarks() {
    for (int n = 1; n <= PreHighPart6Store.ROUND_COUNT; n++) {
      // given
      var round = store.get(n);
      for (var item : round.reading()) {
        // when
        List<Mark> marks = item.marks();
        // then
        assertThat(marks).anyMatch(mark -> "evidence".equals(mark.kind()));
        assertThat(marks).anyMatch(mark -> "answer".equals(mark.kind()));
        assertThat(marks).allMatch(mark -> PreHighPart2Store.validMark(mark, item.passage() != null));
      }
    }
  }

  @Test
  @DisplayName("given 6부 장문 어휘·지칭 문항(42·44번), when 정답 표시를 보면, then 지문 밑줄과 선택지 둘 다에 있다")
  void letterQuestionsMarkBothPassageAndChoice() {
    // given
    var reading = store.get(1).reading();
    for (int index : List.of(24, 26)) {
      // when
      var answers = reading.get(index).marks().stream().filter(mark -> "answer".equals(mark.kind())).map(Mark::part).toList();
      // then
      assertThat(answers).containsExactlyInAnyOrder("question", "passage");
    }
  }

  @Test
  @DisplayName("given 6부 어휘, when 선택지를 보면, then 5개가 서로 다르고 정답 번호의 뜻이 있다")
  void wordsHaveFiveDistinctChoices() {
    // given
    var words = store.get(1).words();
    // when / then
    assertThat(words).allMatch(word -> word.choices().stream().distinct().count() == 5
        && word.answer() >= 1 && word.answer() <= 5 && !word.text().isBlank());
  }

  @Test
  @DisplayName("given 회차 번호, when paper·get을 부르면, then 실린 회차는 PDF를 돌려주고 없는 회차는 null")
  void missingRoundIsNull() {
    // given
    int last = PreHighPart6Store.ROUND_COUNT;
    // when
    byte[] pdf = store.paper(last);
    // then
    assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    assertThat(store.paper(last + 1)).isNull();
    assertThat(store.get(last + 1)).isNull();
    assertThat(store.get(0)).isNull();
  }

  private static boolean isWebp(String encoded) {
    byte[] image = Base64.getDecoder().decode(encoded);
    return "RIFF".equals(new String(image, 0, 4, StandardCharsets.US_ASCII))
        && "WEBP".equals(new String(image, 8, 4, StandardCharsets.US_ASCII));
  }
}
