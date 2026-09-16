package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영 ADMIN 비밀번호 변경 보안 계약 — Spring context 없이 service 단위로 검증한다.
 * 평문·해시 상수를 두지 않고 행위(상태코드·저장 호출 여부)로만 판정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPasswordChangeService 보안 테스트")
class AdminPasswordChangeServiceTest {

  private static final String ADMIN_ID = "admin-f2r";
  private static final String CURRENT = "current-password-000";
  private static final String NEXT = "next-password-000000";

  @Mock UserRepository userRepository;
  @Mock PasswordEncoder passwordEncoder;

  private AdminPasswordChangeService service;

  @BeforeEach
  void setUp() {
    service = new AdminPasswordChangeService(
        userRepository, passwordEncoder, 100, Duration.ofMinutes(10),
        Clock.fixed(Instant.now(), ZoneId.systemDefault()));
  }

  @Test
  @DisplayName("현재 비밀번호 오류 → 401, 저장 호출 없음")
  void wrongCurrent_unauthorized_noSave() {
    givenAdmin("STORED-HASH");
    when(passwordEncoder.matches(CURRENT, "STORED-HASH")).thenReturn(false);

    assertThatThrownBy(() -> service.changePassword(ADMIN_ID, CURRENT, NEXT, NEXT))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED));
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("새 비밀번호 불일치 → 400, 저장 호출 없음")
  void mismatch_badRequest_noSave() {
    givenAdmin("STORED-HASH");
    when(passwordEncoder.matches(CURRENT, "STORED-HASH")).thenReturn(true);

    assertThatThrownBy(() -> service.changePassword(ADMIN_ID, CURRENT, NEXT, NEXT + "x"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("16자 미만 → 400, 저장 호출 없음")
  void tooShort_badRequest_noSave() {
    givenAdmin("STORED-HASH");
    when(passwordEncoder.matches(CURRENT, "STORED-HASH")).thenReturn(true);

    assertThatThrownBy(() -> service.changePassword(ADMIN_ID, CURRENT, "short-15-xxxxxx", "short-15-xxxxxx"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("존재하지 않는 ID → 403")
  void unknown_forbidden() {
    when(userRepository.findByLoginId("no-such-admin")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changePassword("no-such-admin", CURRENT, NEXT, NEXT))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  @DisplayName("STUDENT 계정으로는 변경 불가 → 403, 저장 호출 없음")
  void student_forbidden_noSave() {
    User student = User.builder()
        .loginId("student01").password("STORED-HASH").name("학생")
        .nickname("학생").role(Role.STUDENT).build();
    when(userRepository.findByLoginId("student01")).thenReturn(Optional.of(student));

    assertThatThrownBy(() -> service.changePassword("student01", CURRENT, NEXT, NEXT))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN));
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("정상 ADMIN → 변경 성공, BCrypt encode + 1건 저장")
  void admin_success_encodedAndSaved() {
    User admin = givenAdmin("STORED-HASH");
    when(passwordEncoder.matches(CURRENT, "STORED-HASH")).thenReturn(true);
    when(passwordEncoder.encode(NEXT)).thenReturn("NEW-HASH");

    service.changePassword(ADMIN_ID, CURRENT, NEXT, NEXT);

    assertThat(admin.getPassword()).isEqualTo("NEW-HASH");
    verify(userRepository).save(admin);
  }

  @Test
  @DisplayName("인증 실패 반복 → 상한 초과 시 429")
  void repeatedFailures_tooManyRequests() {
    AdminPasswordChangeService limited = new AdminPasswordChangeService(
        userRepository, passwordEncoder, 2, Duration.ofMinutes(10),
        Clock.fixed(Instant.now(), ZoneId.systemDefault()));
    when(userRepository.findByLoginId(ADMIN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> limited.changePassword(ADMIN_ID, "wrong-1-00000000", NEXT, NEXT))
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> limited.changePassword(ADMIN_ID, "wrong-2-00000000", NEXT, NEXT))
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> limited.changePassword(ADMIN_ID, "wrong-3-00000000", NEXT, NEXT))
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
  }

  private User givenAdmin(String storedHash) {
    User admin = User.builder()
        .loginId(ADMIN_ID).password(storedHash).name("관리자")
        .nickname("관리자").role(Role.ADMIN).build();
    when(userRepository.findByLoginId(ADMIN_ID)).thenReturn(Optional.of(admin));
    return admin;
  }
}
