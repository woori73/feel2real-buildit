package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GrammarBankAccessGuardTest {

  @Mock UserRepository userRepository;

  private GrammarBankAccessGuard guard;

  @BeforeEach
  void setUp() {
    guard = new GrammarBankAccessGuard(userRepository);
    ReflectionTestUtils.setField(guard, "operatorLoginId", "modu");
  }

  private static User user(long id, String loginId, Role role) {
    User u = User.builder().loginId(loginId).password("x").name(loginId).nickname(loginId).role(role).build();
    // @GeneratedValue user_id는 빌더로 채워지지 않아 테스트에서만 리플렉션으로 넣는다.
    ReflectionTestUtils.setField(u, "userId", id);
    return u;
  }

  private static AuthenticatedPrincipal principal(long userId, String loginId, String role) {
    return new AuthenticatedPrincipal(userId, loginId, role);
  }

  @Test
  @DisplayName("ADMIN 역할은 modu 조회 없이도 허용된다")
  void adminAlwaysAllowed() {
    assertThat(guard.isAllowed(principal(999L, "anyadmin", Role.ADMIN.name()))).isTrue();
  }

  @Test
  @DisplayName("운영 계정 modu는 고유 식별자(user_id)로 판정해 허용한다 — 문자열만 신뢰하지 않는다")
  void moduAllowedById() {
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.of(user(42L, "modu", Role.STUDENT)));
    assertThat(guard.isAllowed(principal(42L, "modu", Role.STUDENT.name()))).isTrue();
  }

  @Test
  @DisplayName("loginId가 modu가 아니면(user_id 불일치) TUTOR·STUDENT는 차단된다")
  void otherUsersBlocked() {
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.of(user(42L, "modu", Role.STUDENT)));
    assertThat(guard.isAllowed(principal(7L, "tutor01", Role.TUTOR.name()))).isFalse();
    assertThat(guard.isAllowed(principal(8L, "student01", Role.STUDENT.name()))).isFalse();
  }

  @Test
  @DisplayName("modu를 사칭한 다른 user_id는 차단된다(문자열 loginId만 맞으면 부족)")
  void spoofedLoginIdBlocked() {
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.of(user(42L, "modu", Role.STUDENT)));
    // JWT principal의 loginId가 "modu"라도 고유 식별자가 다르면 거부.
    assertThat(guard.isAllowed(principal(123L, "modu", Role.STUDENT.name()))).isFalse();
  }

  @Test
  @DisplayName("미로그인(principal=null)은 401, 권한 없으면 403")
  void requireStatusCodes() {
    assertThatThrownBy(() -> guard.require(null))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

    when(userRepository.findByLoginId("modu")).thenReturn(Optional.of(user(42L, "modu", Role.STUDENT)));
    assertThatThrownBy(() -> guard.require(principal(7L, "tutor01", Role.TUTOR.name())))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  @DisplayName("운영 계정이 아직 DB에 없으면 modu 판정은 실패하고 ADMIN만 허용된다")
  void missingOperatorFallsBackToAdminOnly() {
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.empty());
    assertThat(guard.isAllowed(principal(42L, "modu", Role.STUDENT.name()))).isFalse();
    assertThat(guard.isAllowed(principal(1L, "admin", Role.ADMIN.name()))).isTrue();
  }
}
