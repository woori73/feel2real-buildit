package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.PasswordResetTokenResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.PasswordResetToken;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.PasswordResetTokenRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

  private UserRepository userRepository;
  private PasswordResetTokenRepository tokenRepository;
  private PasswordEncoder passwordEncoder;
  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    tokenRepository = mock(PasswordResetTokenRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder);
  }

  @Test
  @DisplayName("requestReset — 가입 이메일이면 기존 미사용 토큰을 폐기하고 해시 토큰을 저장한다")
  void requestResetSavesHashedToken() throws Exception {
    // given
    User user = user("student", "student@example.com", Role.STUDENT);
    when(userRepository.findByLoginIdAndEmail("student", "student@example.com")).thenReturn(Optional.of(user));

    // when
    PasswordResetTokenResult result = service.requestReset(" student ", " Student@Example.com ", "http://localhost:8389");

    // then
    assertThat(result.email()).isEqualTo("student@example.com");
    String rawToken = UriComponentsBuilder.fromUriString(result.resetUrl())
        .build()
        .getQueryParams()
        .getFirst("token");
    assertThat(rawToken).isNotBlank();
    verify(tokenRepository).markUnusedTokensUsed(eq(user), any(LocalDateTime.class));
    ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
    verify(tokenRepository).save(captor.capture());
    PasswordResetToken saved = captor.getValue();
    assertThat(saved.getUser()).isSameAs(user);
    assertThat(saved.getTokenHash()).isEqualTo(sha256(rawToken));
    assertThat(saved.getTokenHash()).hasSize(64);
    assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(50));
    // 링크를 발급만 받고 비밀번호는 아직 그대로여야 한다 — 고정값으로 덮어쓰는 경로는 없다.
    assertThat(user.getPassword()).isEqualTo("old-password");
    verify(userRepository, never()).save(any());
    verify(passwordEncoder, never()).encode(anyString());
  }

  @Test
  @DisplayName("requestReset — 응답에는 1회용 링크만 담기고 비밀번호 값은 어디에도 없다")
  void requestResetReturnsOnlyExpiringLink() {
    // given
    User user = user("student", "student@example.com", Role.STUDENT);
    when(userRepository.findByLoginIdAndEmail("student", "student@example.com")).thenReturn(Optional.of(user));

    // when
    PasswordResetTokenResult result = service.requestReset("student", "student@example.com", "http://localhost:8389");

    // then
    assertThat(result.resetUrl()).startsWith("http://localhost:8389/signin/reset/password/confirm?token=");
    assertThat(result.toString().toLowerCase()).doesNotContain("reset1234");
    assertThat(user.getPassword()).isEqualTo("old-password");
  }

  @Test
  @DisplayName("requestReset — 이메일이 비어 있으면 누락 메시지로 예외")
  void requestResetRejectsBlankEmailAsMissingEmail() {
    // given

    // when & then
    assertThatThrownBy(() -> service.requestReset("student", "   ", "http://localhost:8389"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("이메일을 입력해 주세요.");
    verifyNoInteractions(userRepository, tokenRepository);
  }

  @Test
  @DisplayName("requestReset — 이메일 형식이 아니면 저장하지 않고 예외")
  void requestResetRejectsInvalidEmail() {
    // given

    // when & then
    assertThatThrownBy(() -> service.requestReset("student", "ssuw22", "http://localhost:8389"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("이메일 형식이 올바르지 않습니다.");
    verifyNoInteractions(userRepository, tokenRepository);
  }

  @Test
  @DisplayName("requestReset — 가입되지 않은 이메일이면 존재하지 않는 계정 예외")
  void requestResetRejectsUnknownEmail() {
    // given
    when(userRepository.findByLoginIdAndEmail("missing", "missing@example.com")).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.requestReset("missing", "missing@example.com", "http://localhost:8389"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("아이디와 이메일이 일치하는 계정이 없습니다.");
    verify(tokenRepository, never()).save(any());
  }


  @Test
  @DisplayName("findLoginIds — 같은 이메일의 여러 아이디와 역할을 반환한다")
  void findLoginIdsReturnsMultipleAccountsWithRoleLabels() {
    // given
    when(userRepository.findAllByEmail("shared@example.com"))
        .thenReturn(List.of(
            user("tutor01", "shared@example.com", Role.TUTOR),
            user("student01", "shared@example.com", Role.STUDENT)));

    // when
    var result = service.findLoginIds(" Shared@Example.com ");

    // then
    assertThat(result.email()).isEqualTo("shared@example.com");
    assertThat(result.accounts()).extracting("loginId")
        .containsExactly("student01", "tutor01");
    assertThat(result.accounts()).extracting("roleLabel")
        .containsExactly("학생", "강사");
  }

  @Test
  @DisplayName("requestReset — 아이디가 비어 있으면 누락 메시지로 예외")
  void requestResetRejectsBlankLoginId() {
    // when & then
    assertThatThrownBy(() -> service.requestReset("   ", "student@example.com", "http://localhost:8389"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("아이디를 입력해 주세요.");
    verifyNoInteractions(userRepository, tokenRepository);
  }

  @Test
  @DisplayName("confirmReset — 유효한 토큰이면 새 비밀번호를 인코딩해 저장하고 토큰을 사용 처리한다")
  void confirmResetUpdatesPasswordAndMarksTokenUsed() {
    // given
    User user = user("student", "student@example.com", Role.STUDENT);
    PasswordResetToken token = new PasswordResetToken(user, sha256("raw-token"), LocalDateTime.now().plusMinutes(30));
    when(tokenRepository.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));
    when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

    // when
    service.confirmReset("raw-token", "new-password");

    // then
    assertThat(user.getPassword()).isEqualTo("encoded-new-password");
    assertThat(token.isUsed()).isTrue();
  }

  @Test
  @DisplayName("confirmReset — 만료된 토큰이면 비밀번호를 바꾸지 않는다")
  void confirmResetRejectsExpiredToken() {
    // given
    User user = user("student", "student@example.com", Role.STUDENT);
    PasswordResetToken token = new PasswordResetToken(user, sha256("expired-token"), LocalDateTime.now().minusMinutes(1));
    when(tokenRepository.findByTokenHash(sha256("expired-token"))).thenReturn(Optional.of(token));

    // when & then
    assertThatThrownBy(() -> service.confirmReset("expired-token", "new-password"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("비밀번호 재설정 링크가 만료되었습니다.");
    verify(passwordEncoder, never()).encode(anyString());
  }

  @Test
  @DisplayName("confirmReset — 이미 사용된 토큰이면 비밀번호를 바꾸지 않는다")
  void confirmResetRejectsUsedToken() {
    // given
    User user = user("student", "student@example.com", Role.STUDENT);
    PasswordResetToken token = new PasswordResetToken(user, sha256("used-token"), LocalDateTime.now().plusMinutes(30));
    token.markUsed(LocalDateTime.now().minusMinutes(1));
    when(tokenRepository.findByTokenHash(sha256("used-token"))).thenReturn(Optional.of(token));

    // when & then
    assertThatThrownBy(() -> service.confirmReset("used-token", "new-password"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("이미 사용된 비밀번호 재설정 링크입니다.");
    verify(passwordEncoder, never()).encode(anyString());
  }

  private User user(String loginId, String email, Role role) {
    return User.builder()
        .loginId(loginId)
        .password("old-password")
        .name("학생")
        .nickname("학생")
        .email(email)
        .role(role)
        .build();
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
