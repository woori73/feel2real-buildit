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
        new SectionScore("READING", 8, 6, 6, 5, 83.3, 5.0),
        new SectionScore("LISTENING", 6, 4, 4, 3, 75.0, 4.0));
    assertThat(TestPrepService.overallBand(sections)).isEqualTo(4.5);
  }

  @Test
  @DisplayName("given 자동채점 섹션이 없으면 when overallBand면 null — ETS 공식처럼 표시하지 않는다")
  void noAutoScoreMeansNoBand() {
    List<SectionScore> sections = List.of(new SectionScore("SPEAKING", 2, 0, 0, 0, null, null));
    assertThat(TestPrepService.overallBand(sections)).isNull();
  }
}
