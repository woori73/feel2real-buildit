package f2r.tutor_solution.private_tutor_solution_back.domain.community.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommunityProperties — 기본값 보정")
class CommunityPropertiesTest {

  @Test
  @DisplayName("0 이하 값은 기본값(30/7)으로 보정된다")
  void correctsNonPositiveToDefaults() {
    // given / when
    CommunityProperties p = new CommunityProperties(0, -1);
    // then
    assertThat(p.likeAllowedDays()).isEqualTo(30);
    assertThat(p.recommendationWindowDays()).isEqualTo(7);
  }

  @Test
  @DisplayName("양수 값은 그대로 유지된다")
  void keepsPositiveValues() {
    // given / when
    CommunityProperties p = new CommunityProperties(14, 3);
    // then
    assertThat(p.likeAllowedDays()).isEqualTo(14);
    assertThat(p.recommendationWindowDays()).isEqualTo(3);
  }
}
