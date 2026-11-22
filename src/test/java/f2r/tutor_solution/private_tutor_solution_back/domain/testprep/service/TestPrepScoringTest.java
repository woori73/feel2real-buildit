package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.SectionScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestPrepScoringTest {

  @Test
  @DisplayName("given 정답률 95% 이상일 때 when bandFor then 6.0")
  void bandTop() {
    assertThat(TestPrepService.bandFor(96.7)).isEqualTo(6.0);
    assertThat(TestPrepService.bandFor(95.0)).isEqualTo(6.0);
  }

  @Test
  @DisplayName("given 경계값 70%일 때 when bandFor then 4.5")
  void bandBoundary() {
    assertThat(TestPrepService.bandFor(70.0)).isEqualTo(4.5);
    assertThat(TestPrepService.bandFor(69.9)).isEqualTo(4.0);
    assertThat(TestPrepService.bandFor(0.0)).isEqualTo(1.5);
  }

  @Test
  @DisplayName("given 섹션 밴드 5.0과 4.0일 때 when overallBand면 0.5 단위로 평균 반올림(ETS 방식)")
  void overallRoundsToHalfBand() {
    List<SectionScore> sections = List.of(
        new SectionScore("READING", 8, 6, 6, 5, 83.3, 5.0, null, null),
        new SectionScore("LISTENING", 6, 4, 4, 3, 75.0, 4.0, null, null));
    assertThat(TestPrepService.overallBand(sections)).isEqualTo(4.5);
  }

  @Test
  @DisplayName("given 자동채점 섹션이 없으면 when overallBand면 null — ETS 공식처럼 표시하지 않는다")
  void noAutoScoreMeansNoBand() {
    List<SectionScore> sections = List.of(new SectionScore("SPEAKING", 2, 0, 0, 0, null, null, null, null));
    assertThat(TestPrepService.overallBand(sections)).isNull();
  }
  @Test
  @DisplayName("given SAT 정답 수, when satScaledFor면 200~800 범위의 10점 단위 추정 점수")
  void satScaledRange() {
    assertThat(TestPrepService.satScaledFor(10, 10)).isEqualTo(800);
    assertThat(TestPrepService.satScaledFor(0, 10)).isEqualTo(200);
    assertThat(TestPrepService.satScaledFor(5, 10)).isEqualTo(500);
    assertThat(TestPrepService.satScaledFor(7, 8)).isEqualTo(730); // 0.875 × 60 = 52.5 → 53 → 730
    assertThat(TestPrepService.satScaledFor(0, 0)).isNull();
  }

  @Test
  @DisplayName("given TOEIC 정답 수, when toeicScaledFor면 5~495 범위의 5점 단위 추정 점수")
  void toeicScaledRange() {
    // given / when / then
    assertThat(TestPrepService.toeicScaledFor(100, 100)).isEqualTo(495);
    assertThat(TestPrepService.toeicScaledFor(0, 100)).isEqualTo(5);
    assertThat(TestPrepService.toeicScaledFor(50, 100)).isEqualTo(250);
    assertThat(TestPrepService.toeicScaledFor(73, 100)).isEqualTo(365); // 0.73 × 98 = 71.54 → 72 → 365
    assertThat(TestPrepService.toeicScaledFor(47, 94)).isEqualTo(250);  // 듣기 94문항(Part 1 제외)도 같은 범위
    assertThat(TestPrepService.toeicScaledFor(0, 0)).isNull();
  }

  @Test
  @DisplayName("given TOEIC 듣기·읽기 영역 점수, when totalScaled·totalMax면 합계와 만점 990, 읽기만이면 만점 495")
  void toeicTotal() {
    // given
    List<SectionScore> full = List.of(
        new SectionScore("LISTENING", 94, 94, 94, 70, 74.5, null, 370, 495),
        new SectionScore("READING", 100, 100, 100, 80, 80.0, null, 395, 495));
    List<SectionScore> readingOnly = List.of(new SectionScore("READING", 100, 100, 100, 80, 80.0, null, 395, 495));
    // when / then
    assertThat(TestPrepService.totalScaled(full)).isEqualTo(765);
    assertThat(TestPrepService.totalMax(full)).isEqualTo(990);
    assertThat(TestPrepService.totalMax(readingOnly)).isEqualTo(495);
  }

  @Test
  @DisplayName("given 시험 종류, when disclaimerFor면 TOEIC은 ETS TOEIC 공식 점수가 아니라는 문구")
  void toeicDisclaimer() {
    // given
    String toeicKind = "TOEIC";
    String satKind = "SAT";
    String ibtKind = "PRACTICE";
    // when
    String toeic = TestPrepService.disclaimerFor(toeicKind);
    String sat = TestPrepService.disclaimerFor(satKind);
    String ibt = TestPrepService.disclaimerFor(ibtKind);
    // then
    assertThat(toeic).isEqualTo(TestPrepService.TOEIC_DISCLAIMER);
    assertThat(sat).isEqualTo(TestPrepService.SAT_DISCLAIMER);
    assertThat(ibt).isEqualTo(TestPrepService.BAND_DISCLAIMER);
  }

  @Test
  @DisplayName("given TOEIC 오답만 다시 풀기(부분 응시), when toeicSectionScaled면 추정 점수를 내지 않고(null) 전체 응시는 그대로 낸다")
  void toeicPartialAttemptHasNoEstimate() {
    // given — 틀린 3문항만 다시 풀어 모두 맞힌 경우
    int correct = 3;
    int total = 3;
    // when
    Integer partial = TestPrepService.toeicSectionScaled(true, correct, total);
    Integer full = TestPrepService.toeicSectionScaled(false, correct, total);
    // then — 부분 응시에서 495가 나오면 안 된다
    assertThat(partial).isNull();
    assertThat(full).isEqualTo(495);
  }

  @Test
  @DisplayName("given 부분 응시라 점수가 빈 TOEIC 영역, when totalScaled·totalMax면 합계와 만점도 null")
  void toeicPartialAttemptHasNoTotal() {
    // given
    List<SectionScore> partial = List.of(new SectionScore("READING", 3, 3, 3, 3, 100.0, null, null, null));
    // when / then
    assertThat(TestPrepService.totalScaled(partial)).isNull();
    assertThat(TestPrepService.totalMax(partial)).isNull();
  }

  @Test
  @DisplayName("given 시험 종류, when sectionRank면 TOEIC은 듣기가 읽기보다 앞이고 iBT·SAT는 기존 순서 그대로다")
  void toeicSectionOrderIsListeningFirst() {
    // given
    String toeic = "TOEIC";
    String ibt = "PRACTICE";
    // when / then
    assertThat(TestPrepService.sectionRank(toeic, "LISTENING")).isLessThan(TestPrepService.sectionRank(toeic, "READING"));
    assertThat(TestPrepService.sectionRank(ibt, "READING")).isLessThan(TestPrepService.sectionRank(ibt, "LISTENING"));
    assertThat(TestPrepService.sectionRank("SAT", "RW")).isLessThan(TestPrepService.sectionRank("SAT", "MATH"));
  }

  @Test
  @DisplayName("given SAT 영역 점수들, when totalScaled면 합계, 영역 점수가 없으면 null")
  void satTotal() {
    List<SectionScore> sections = List.of(
        new SectionScore("RW", 10, 10, 10, 8, 80.0, null, 680, null),
        new SectionScore("MATH", 8, 8, 8, 6, 75.0, null, 650, null));
    assertThat(TestPrepService.totalScaled(sections)).isEqualTo(1330);
    assertThat(TestPrepService.totalScaled(List.of(new SectionScore("READING", 8, 6, 6, 5, 83.3, 5.0, null, null)))).isNull();
  }

  @Test
  @DisplayName("given 주관식 허용 답 '21/2|10.5', when 같은 값을 다른 꼴로 입력하면 정답")
  void satSprEquivalentForms() {
    String key = "21/2|10.5";
    assertThat(TestPrepService.isSatSprCorrect("21/2", key)).isTrue();
    assertThat(TestPrepService.isSatSprCorrect("10.5", key)).isTrue();
    assertThat(TestPrepService.isSatSprCorrect("42/4", key)).isTrue();
    assertThat(TestPrepService.isSatSprCorrect(" 10.50 ", key)).isTrue();
    assertThat(TestPrepService.isSatSprCorrect("10", key)).isFalse();
  }

  @Test
  @DisplayName("given 주관식 입력이 비었거나 형식 밖이거나 너무 길면, when 채점하면 오답")
  void satSprRejectsInvalid() {
    assertThat(TestPrepService.isSatSprCorrect("", "5")).isFalse();
    assertThat(TestPrepService.isSatSprCorrect(null, "5")).isFalse();
    assertThat(TestPrepService.isSatSprCorrect("5$", "5")).isFalse();
    assertThat(TestPrepService.isSatSprCorrect("5.00000", "5")).isFalse(); // 7자 — 입력 한도 밖
    assertThat(TestPrepService.isSatSprCorrect("-1/4", "-.25")).isTrue();
    assertThat(TestPrepService.isSatSprCorrect("1/0", "5")).isFalse();
  }

  @Test
  @DisplayName("given 답이 2/3처럼 긴 소수일 때, when 칸을 다 채워 자르거나 반올림하면 정답, 덜 채우면 오답")
  void satSprLongDecimalRule() {
    assertThat(TestPrepService.isSatSprCorrect(".6666", "2/3")).isTrue();
    assertThat(TestPrepService.isSatSprCorrect(".6667", "2/3")).isTrue();
    assertThat(TestPrepService.isSatSprCorrect("0.666", "2/3")).isTrue();
    assertThat(TestPrepService.isSatSprCorrect("0.667", "2/3")).isTrue();
    assertThat(TestPrepService.isSatSprCorrect("0.67", "2/3")).isFalse();
    assertThat(TestPrepService.isSatSprCorrect(".66", "2/3")).isFalse();
    assertThat(TestPrepService.isSatSprCorrect("-.6666", "-2/3")).isTrue();
    assertThat(TestPrepService.isSatSprCorrect("0.668", "2/3")).isFalse();
  }

  @Test
  @DisplayName("given 시험 종류, when disclaimerFor면 SAT은 College Board 안내, 그 밖은 ETS 안내")
  void disclaimerByKind() {
    assertThat(TestPrepService.disclaimerFor("SAT")).isEqualTo(TestPrepService.SAT_DISCLAIMER);
    assertThat(TestPrepService.disclaimerFor("PRACTICE")).isEqualTo(TestPrepService.BAND_DISCLAIMER);
  }

  @Test
  @DisplayName("given 섹션 만점, when totalMax면 합계, 만점이 없는 섹션이 있으면 null")
  void totalMaxSumsScaleMax() {
    assertThat(TestPrepService.totalMax(List.of(
        new SectionScore("EN", 40, 40, 40, 31, 77.5, null, 103, 134)))).isEqualTo(134);
    assertThat(TestPrepService.totalMax(List.of(
        new SectionScore("RW", 10, 10, 10, 8, 80.0, null, 680, 800),
        new SectionScore("MATH", 8, 8, 8, 6, 75.0, null, 650, null)))).isNull();
    assertThat(TestPrepService.disclaimerFor("NAESIN")).isEqualTo(TestPrepService.NAESIN_DISCLAIMER);
  }

  @Test
  @DisplayName("given 내신 모두 고르기 정답 'B,D', when 같은 기호를 순서만 바꿔 고르면 then 정답")
  void naesinMultiSameSetInAnyOrder() {
    // given
    String key = "B,D";
    // when · then
    assertThat(TestPrepService.isMultiChoiceCorrect("B,D", key)).isTrue();
    assertThat(TestPrepService.isMultiChoiceCorrect("D,B", key)).isTrue();
    assertThat(TestPrepService.isMultiChoiceCorrect(" d , b ", key)).isTrue();
  }

  @Test
  @DisplayName("given 내신 모두 고르기 정답 'B,D', when 하나만 고르거나 더 고르거나 비우면 then 오답")
  void naesinMultiPartialOrExtraIsWrong() {
    // given
    String key = "B,D";
    // when · then
    assertThat(TestPrepService.isMultiChoiceCorrect("B", key)).isFalse();
    assertThat(TestPrepService.isMultiChoiceCorrect("B,C,D", key)).isFalse();
    assertThat(TestPrepService.isMultiChoiceCorrect("", key)).isFalse();
    assertThat(TestPrepService.isMultiChoiceCorrect(null, key)).isFalse();
  }
}
