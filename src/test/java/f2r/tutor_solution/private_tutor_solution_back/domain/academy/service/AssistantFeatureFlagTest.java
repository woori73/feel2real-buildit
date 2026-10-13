package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssistantFeatureFlag — 역할과 무관하게 계정 ID 허용 목록으로 동작")
class AssistantFeatureFlagTest {

  @Test
  @DisplayName("given 스위치 off, when ADMIN/TUTOR/null, then 모두 꺼짐")
  void off() {
    AssistantFeatureFlag flag = new AssistantFeatureFlag(false, "admin1");
    assertThat(flag.isOn(new AuthenticatedPrincipal(1L, "admin1", "ADMIN"))).isFalse();
    assertThat(flag.isOn(null)).isFalse();
  }

  @Test
  @DisplayName("given 허용 목록에 ADMIN 계정, when ADMIN/다른 TUTOR, then ADMIN만 켜짐")
  void allowlistAppliesToAdmin() {
    AssistantFeatureFlag flag = new AssistantFeatureFlag(true, "admin1, admin2");
    assertThat(flag.isOn(new AuthenticatedPrincipal(1L, "admin1", "ADMIN"))).isTrue();
    assertThat(flag.isOn(new AuthenticatedPrincipal(2L, "tutorX", "TUTOR"))).isFalse();
  }

  @Test
  @DisplayName("given 허용 목록 비어 있음, when 임의 계정, then 모두 켜짐(전체 공개 모드)")
  void emptyAllowlistMeansEveryone() {
    AssistantFeatureFlag flag = new AssistantFeatureFlag(true, "");
    assertThat(flag.isOn(new AuthenticatedPrincipal(3L, "anyone", "TUTOR"))).isTrue();
  }
}
