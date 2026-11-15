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
        new SectionScore("READING", 8, 6, 6, 5, 83.3, 5.0, null),
        new SectionScore("LISTENING", 6, 4, 4, 3, 75.0, 4.0, null));
    assertThat(TestPrepService.overallBand(sections)).isEqualTo(4.5);
  }

  @Test
  @DisplayName("given 자동채점 섹션이 없으면 when overallBand면 null — ETS 공식처럼 표시하지 않는다")
  void noAutoScoreMeansNoBand() {
    List<SectionScore> sections = List.of(new SectionScore("SPEAKING", 2, 0, 0, 0, null, null, null));
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
  @DisplayName("given SAT 영역 점수들, when totalScaled면 합계, 영역 점수가 없으면 null")
  void satTotal() {
    List<SectionScore> sections = List.of(
        new SectionScore("RW", 10, 10, 10, 8, 80.0, null, 680),
        new SectionScore("MATH", 8, 8, 8, 6, 75.0, null, 650));
    assertThat(TestPrepService.totalScaled(sections)).isEqualTo(1330);
    assertThat(TestPrepService.totalScaled(List.of(new SectionScore("READING", 8, 6, 6, 5, 83.3, 5.0, null)))).isNull();
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
}
