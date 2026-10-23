package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StudioAccessGuardTest {

  @Mock UserRepository userRepository;

  private StudioAccessGuard guard;

  @BeforeEach
  void setUp() {
    guard = new StudioAccessGuard(userRepository);
    ReflectionTestUtils.setField(guard, "operatorLoginId", "modu");
  }

  private static User user(long id, String loginId, Role role) {
    User u = User.builder().loginId(loginId).password("x").name(loginId).nickname(loginId).role(role).build();
    ReflectionTestUtils.setField(u, "userId", id);
    return u;
  }

  private static AuthenticatedPrincipal principal(long userId, String loginId, Role role) {
    return new AuthenticatedPrincipal(userId, loginId, role.name());
  }

  @Test
  @DisplayName("ADMIN은 운영 계정 조회 없이 허용된다")
  void givenAdmin_whenChecked_thenAllowed() {
    // given
    var admin = principal(1L, "admin", Role.ADMIN);
    // when
    boolean allowed = guard.isAllowed(admin);
    // then
    assertThat(allowed).isTrue();
    verify(userRepository, never()).findByLoginId("modu");
  }

  @Test
  @DisplayName("modu는 저장소의 실제 user_id와 일치할 때 허용되고 운영 계정으로 판정된다")
  void givenModuById_whenChecked_thenAllowedAndOperator() {
    // given
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.of(user(42L, "modu", Role.TUTOR)));
    var modu = principal(42L, "modu", Role.TUTOR);
    // when / then
    assertThat(guard.isAllowed(modu)).isTrue();
    assertThat(guard.isOperator(modu)).isTrue();
    verify(userRepository, times(1)).findByLoginId("modu");
  }

  @Test
  @DisplayName("loginId만 modu로 위장한 다른 user_id는 차단된다")
  void givenSpoofedLoginId_whenChecked_thenDenied() {
    // given
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.of(user(42L, "modu", Role.TUTOR)));
    // when
    boolean allowed = guard.isAllowed(principal(77L, "modu", Role.TUTOR));
    // then
    assertThat(allowed).isFalse();
  }

  @Test
  @DisplayName("일반 회원·다른 학원 강사·CREATOR·비로그인은 차단된다")
  void givenOtherUsers_whenChecked_thenDenied() {
    // given
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.of(user(42L, "modu", Role.TUTOR)));
    // when / then
    assertThat(guard.isAllowed(principal(10L, "student", Role.STUDENT))).isFalse();
    assertThat(guard.isAllowed(principal(11L, "other-academy-tutor", Role.TUTOR))).isFalse();
    assertThat(guard.isAllowed(principal(12L, "creator", Role.CREATOR))).isFalse();
    assertThat(guard.isAllowed(null)).isFalse();
  }

  @Test
  @DisplayName("modu 계정이 없으면 ADMIN 외 전원 차단되고, 이후 생성되면 따라간다")
  void givenMissingOperator_whenCreatedLater_thenFollows() {
    // given
    when(userRepository.findByLoginId("modu"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(user(42L, "modu", Role.STUDENT)));
    var modu = principal(42L, "modu", Role.STUDENT);
    // when / then
    assertThat(guard.isAllowed(modu)).isFalse();
    assertThat(guard.isAllowed(modu)).isTrue();
  }

  @Test
  @DisplayName("격리 테스트용 operators-only=false면 기존 초대 회원 모델로 돌아간다")
  void givenOperatorsOnlyOff_whenChecked_thenLoggedInUsersPassGate() {
    // given
    ReflectionTestUtils.setField(guard, "operatorsOnly", false);
    // when / then
    assertThat(guard.isAllowed(principal(10L, "student", Role.STUDENT))).isTrue();
    assertThat(guard.isAllowed(null)).isFalse();
  }
}
