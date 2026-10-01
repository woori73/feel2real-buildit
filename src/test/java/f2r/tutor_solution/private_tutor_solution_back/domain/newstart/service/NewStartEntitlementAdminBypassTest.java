package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 권한 원칙 고정: ADMIN → 모든 기능 즉시 허용(감사회원 인증·roster 연결·grant 불필요) / THANKS_MEMBER → 감사회원 전용 기능 /
 * 일반회원 → 일반 기능만. 모든 {@link NewStartCapability}(현재·향후 추가분 포함)에 대해 ADMIN 우회를 순회로 검증한다.
 */
@DisplayName("NewStartEntitlementService — ADMIN 최상위 우회(감사회원 인증 불필요)")
class NewStartEntitlementAdminBypassTest {

  private static AuthenticatedPrincipal principal(String role) {
    return new AuthenticatedPrincipal(1L, "login", role);
  }

  @Test
  @DisplayName("ADMIN은 모든 capability를 grant 조회 없이 통과한다 — 향후 추가되는 capability도 동일")
  void admin_passesEveryCapability_withoutTouchingGrants() {
    NewStartMemberGrantRepository grants = mock(NewStartMemberGrantRepository.class);
    NewStartEntitlementService service = new NewStartEntitlementService(grants);

    for (NewStartCapability capability : NewStartCapability.values()) {
      assertThat(service.allows(principal("ADMIN"), capability)).as("ADMIN %s", capability).isTrue();
      assertThatCode(() -> service.require(principal("ADMIN"), capability)).doesNotThrowAnyException();
    }
    assertThat(service.isThanksMember(principal("ADMIN"))).isTrue();
    assertThatCode(() -> service.requireThanksMember(principal("ADMIN"))).doesNotThrowAnyException();
    verifyNoInteractions(grants);   // roster 연결·grant row·인증 기록이 없어도 통과한다
  }

  @Test
  @DisplayName("일반회원은 grant가 없으면 모든 감사회원 전용 capability가 거절되고, 비로그인은 항상 거절")
  void ordinaryMember_isDeniedWithoutGrant() {
    NewStartMemberGrantRepository grants = mock(NewStartMemberGrantRepository.class);
    NewStartEntitlementService service = new NewStartEntitlementService(grants);

    for (NewStartCapability capability : NewStartCapability.values()) {
      assertThat(service.allows(principal("STUDENT"), capability)).isFalse();
      assertThatThrownBy(() -> service.require(principal("STUDENT"), capability)).isInstanceOf(NewStartEntitlementDeniedException.class);
      assertThat(service.allows(null, capability)).isFalse();
    }
  }
}
