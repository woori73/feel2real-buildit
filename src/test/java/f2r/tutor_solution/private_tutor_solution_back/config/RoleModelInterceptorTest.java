package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleModelInterceptorTest {

  private final RoleModelInterceptor interceptor = new RoleModelInterceptor();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("인증 principal이 있으면 현재 role을 반환한다")
  void currentRoleReturnsAuthenticatedPrincipalRole() {
    // given
    var principal = new JwtTokenProvider.AuthenticatedPrincipal(1L, "admin", "ADMIN");
    var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);

    // when
    String role = interceptor.currentRole();

    // then
    assertThat(role).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("인증 정보가 없으면 현재 role은 null이다")
  void currentRoleReturnsNullWithoutAuthentication() {
    // given

    // when
    String role = interceptor.currentRole();

    // then
    assertThat(role).isNull();
  }
}
