package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.NewStartPasswordChangeRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.NewStartPasswordStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("NewStart 회원 본인 비밀번호 변경 — 현재 비밀번호 검증이 곧 강제 해제 조건")
class NewStartMemberPasswordServiceTest {

  private static final String TEMP = "kq7m2xt9pbad";

  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private NewStartMemberPasswordService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    service = new NewStartMemberPasswordService(userRepository, passwordEncoder);
  }

  @Test
  @DisplayName("status — ADMIN 초기화 상태를 최소 길이(8)와 함께 알려준다")
  void statusExposesRequiredFlag() {
    User member = member(true);
    ReflectionTestUtils.setField(member, "lastLoginAt", LocalDateTime.of(2026, 10, 2, 9, 30));
    when(userRepository.findById(77L)).thenReturn(Optional.of(member));

    NewStartPasswordStatus status = service.status(77L);

    assertThat(status.changeRequired()).isTrue();
    assertThat(status.minPasswordLength()).isEqualTo(8);
    assertThat(status.lastLoginAt()).isEqualTo(LocalDateTime.of(2026, 10, 2, 9, 30));
  }

  @Test
  @DisplayName("pageView — 강제 진입 여부와 로그인 ID를 화면용으로만 노출(비밀번호 값은 없다)")
  void pageViewHasNoPasswordMaterial() {
    when(userRepository.findById(77L)).thenReturn(Optional.of(member(true)));

    var view = service.pageView(77L);

    assertThat(view.loginId()).isEqualTo("ns-member-01");
    assertThat(view.changeRequired()).isTrue();
    assertThat(java.util.Arrays.stream(
            NewStartMemberPasswordService.PasswordPageView.class.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName)
        .toList())
        .containsExactly("loginId", "changeRequired", "minPasswordLength");
    assertThat(view.toString()).doesNotContain("old-hash");
  }

  @Test
  @DisplayName("현재 비밀번호가 맞아야 변경되고 그때만 password_reset_required가 FALSE가 된다")
  void changeRequiresCurrentPasswordAndClearsFlag() {
    User member = member(true);
    when(userRepository.findById(77L)).thenReturn(Optional.of(member));
    when(passwordEncoder.matches(TEMP, "old-hash")).thenReturn(true);
    when(passwordEncoder.encode("newpass123")).thenReturn("bcrypt-new");

    service.change(77L, request(TEMP, "newpass123", "newpass123"));

    assertThat(member.getPassword()).isEqualTo("bcrypt-new");
    assertThat(member.isPasswordResetRequired()).isFalse();
    verify(userRepository).save(member);
  }

  @Test
  @DisplayName("현재 비밀번호 불일치 → 400, hash도 플래그도 그대로")
  void changeRejectsWrongCurrentPassword() {
    User member = member(true);
    when(userRepository.findById(77L)).thenReturn(Optional.of(member));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

    assertThatThrownBy(() -> service.change(77L, request("wrongpw12345", "newpass123", "newpass123")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("현재 비밀번호가 올바르지 않습니다.");

    assertThat(member.getPassword()).isEqualTo("old-hash");
    assertThat(member.isPasswordResetRequired()).isTrue();
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("새 비밀번호 7자(현행 가입 정책 최소 8자 미만)·확인 불일치·현재와 동일 → 모두 거부")
  void changeRejectsWeakOrMismatchedInput() {
    User member = member(false);
    when(userRepository.findById(77L)).thenReturn(Optional.of(member));
    when(passwordEncoder.matches(TEMP, "old-hash")).thenReturn(true);

    assertThatThrownBy(() -> service.change(77L, request(TEMP, "short12", "short12")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("새 비밀번호는 8자 이상으로 입력해 주세요.");
    assertThatThrownBy(() -> service.change(77L, request(TEMP, "newpass123", "newpass124")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("새 비밀번호가 서로 일치하지 않습니다.");
    assertThatThrownBy(() -> service.change(77L, request(TEMP, "", "")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("새 비밀번호는 8자 이상으로 입력해 주세요.");
    // 현재 비밀번호와 같은 값으로 재설정하는 것은 거부(의미 없는 변경 + 강제 해제 우회처럼 보인다).
    assertThatThrownBy(() -> service.change(77L, request(TEMP, TEMP, TEMP)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("새 비밀번호는 현재 비밀번호와 다르게 입력해 주세요.");

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("현재 비밀번호가 비어 있으면 hash 검증 조차 하지 않는다")
  void changeRejectsBlankCurrentPasswordBeforeVerification() {
    when(userRepository.findById(77L)).thenReturn(Optional.of(member(false)));

    assertThatThrownBy(() -> service.change(77L, request("  ", "newpass123", "newpass123")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("현재 비밀번호를 입력해 주세요.");
    verify(passwordEncoder, never()).matches(anyString(), anyString());
  }

  @Test
  @DisplayName("운영 ADMIN 계정은 이 경로로 바꾸지 못한다 — 403")
  void adminAccountIsOutOfScope() {
    User admin = member(false);
    ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
    when(userRepository.findById(77L)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.change(77L, request(TEMP, "newpass123", "newpass123")))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  @DisplayName("없는 회원 id → 404")
  void unknownMemberIsNotFound() {
    when(userRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.change(404L, request(TEMP, "newpass123", "newpass123")))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  private NewStartPasswordChangeRequest request(String current, String next, String confirm) {
    return new NewStartPasswordChangeRequest(current, next, confirm);
  }

  private User member(boolean resetRequired) {
    User user = User.builder()
        .loginId("ns-member-01")
        .password("old-hash")
        .name("새출발")
        .nickname("새출발러")
        .role(Role.STUDENT)
        .build();
    ReflectionTestUtils.setField(user, "userId", 77L);
    if (resetRequired) {
      user.markPasswordResetRequired();
    }
    return user;
  }
}
