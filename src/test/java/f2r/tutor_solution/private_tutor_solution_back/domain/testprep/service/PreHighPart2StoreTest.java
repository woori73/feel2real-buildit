package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Mark;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreHighPart2StoreTest {

  @Test
  @DisplayName("given 2부 22회 문제지 PDF, when 글자를 뽑으면, then 원본(출처) 표기가 없다")
  void paperHasNoSourceLine() throws Exception {
    for (int n = 1; n <= PreHighPart2Store.ROUND_COUNT; n++) {
      // given
      byte[] pdf = store.paper(n);
      // when
      String text;
      try (var doc = Loader.loadPDF(pdf)) {
        text = new PDFTextStripper().getText(doc);
      }
      // then
      assertThat(text).as("%d회", n).contains("28문항").doesNotContain("원본").doesNotContain("출처").doesNotContain("학평").doesNotContain("학력평가");
      assertThat(text).as("%d회", n).doesNotContain(store.list().get(n - 1).source());
    }
  }

  private final PreHighPart2Store store = new PreHighPart2Store(new ObjectMapper());

  @Test
  @DisplayName("given 2부 자료 22회, when list·get하면, then 회차마다 독해 28문항·어휘 50문항과 원본 이름이 있다")
  void loadsAllRounds() {
    // given / when
    var infos = store.list();
    // then
    assertThat(infos).hasSize(22);
    assertThat(infos.get(0).source()).isNotBlank();
    for (int n = 1; n <= PreHighPart2Store.ROUND_COUNT; n++) {
      var round = store.get(n);
      assertThat(round.number()).isEqualTo(n);
      assertThat(round.reading()).hasSize(28);
      assertThat(round.words()).hasSize(50);
    }
  }

  @Test
  @DisplayName("given 2부 회차, when 독해 문항을 보면, then 그림은 webp이고 41~45번만 공통 지문이 있으며 해설은 '정답'으로 시작한다")
  void readingItemsHaveImagesAndExplanations() {
    // given
    var round = store.get(1);
    // when
    var first = round.reading().get(0);
    var long45 = round.reading().get(27);
    // then
    byte[] image = Base64.getDecoder().decode(first.image());
    assertThat(new String(image, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
    assertThat(new String(image, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
    assertThat(first.passage()).isNull();
    assertThat(long45.passage()).isNotNull();
    assertThat(round.reading().subList(0, 23)).allMatch(item -> item.passage() == null);
    assertThat(round.reading()).allMatch(item -> item.explanation().startsWith("정답 "));
  }

  @Test
  @DisplayName("given 2부 22회 전체, when 근거 표시를 보면, then 모든 문항에 노란 근거와 정답 표시가 있고 좌표는 그림 안에 있다")
  void everyItemHasEvidenceAndAnswerMarks() {
    for (int n = 1; n <= PreHighPart2Store.ROUND_COUNT; n++) {
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
  @DisplayName("given 2부 어휘, when 선택지를 보면, then 5개가 서로 다르고 정답 번호의 뜻이 있다")
  void wordsHaveFiveDistinctChoices() {
    // given
    var words = store.get(3).words();
    // when / then
    assertThat(words).allMatch(word -> word.choices().stream().distinct().count() == 5
        && word.answer() >= 1 && word.answer() <= 5 && !word.text().isBlank());
  }

  @Test
  @DisplayName("given 회차 번호, when paper·numbers를 부르면, then PDF와 시험지 번호 18~45를 돌려주고 없는 회차는 null")
  void paperAndNumbers() {
    // given / when
    byte[] pdf = store.paper(22);
    // then
    assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    assertThat(store.paper(23)).isNull();
    assertThat(store.get(0)).isNull();
    assertThat(PreHighPart2Store.numbers()).hasSize(28).startsWith(18).endsWith(45);
  }

  @Test
  @DisplayName("given 문항 수가 모자란 자료, when validate하면, then 시작부터 막는다")
  void validateRejectsShortRound() {
    // given
    var file = new PreHighPart2Store.RoundFile(1, "x", List.of(), List.of());
    // when / then
    assertThatThrownBy(() -> PreHighPart2Store.validate(1, file)).isInstanceOf(IllegalStateException.class);
  }
}
