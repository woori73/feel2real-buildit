package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.TemporaryPasswordIssue;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.TemporaryPasswordIssueRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("TemporaryPasswordService — 관리자 발급·계정 찾기 재설정·변경 강제")
class TemporaryPasswordServiceTest {

  private static final ZoneId ZONE = ZoneId.systemDefault();
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 12, 0);
  private static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "admin", "ADMIN");

  private UserRepository users;
  private TemporaryPasswordIssueRepository issues;
  private PasswordEncoder encoder;
  private TemporaryPasswordService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    issues = mock(TemporaryPasswordIssueRepository.class);
    encoder = mock(PasswordEncoder.class);
    when(encoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
    Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    service = new TemporaryPasswordService(users, issues, encoder, new SecureRandom(), clock);
  }

  private User user(Long id, Role role) {
    User u = User.builder().loginId("u" + id).password("old").name("n").nickname("도도").role(role).build();
    ReflectionTestUtils.setField(u, "userId", id);
    when(users.findById(id)).thenReturn(Optional.of(u));
    return u;
  }

  @Test
  @DisplayName("given 학생 when 관리자가 발급 then 무작위 비밀번호를 해시로 저장·72시간·기록(비밀번호 없음)")
  void issueByAdmin_success() {
    // given
    User stu = user(10L, Role.STUDENT);

    // when
    TemporaryPasswordService.AdminIssueResult r = service.issueByAdmin(ADMIN, 10L);

    // then
    assertThat(r.temporaryPassword()).hasSize(10);
    assertThat(r.loginId()).isEqualTo("u10");
    assertThat(stu.getPassword()).isEqualTo("hash:" + r.temporaryPassword());
    assertThat(stu.isPasswordChangeRequired()).isTrue();
    assertThat(stu.getTempPasswordExpiresAt()).isEqualTo(NOW.plusHours(72));
    ArgumentCaptor<TemporaryPasswordIssue> cap = ArgumentCaptor.forClass(TemporaryPasswordIssue.class);
    verify(issues).save(cap.capture());
    assertThat(cap.getValue().getTargetUserId()).isEqualTo(10L);
    assertThat(cap.getValue().getIssuedByLoginId()).isEqualTo("admin");
    assertThat(cap.getValue().getIssuedAt()).isEqualTo(NOW);
    assertThat(cap.getValue().getExpiresAt()).isEqualTo(NOW.plusHours(72));
  }

  @Test
  @DisplayName("given 같은 회원 when 두 번 발급 then 매번 다른 비밀번호")
  void issueByAdmin_differentEachTime() {
    // given
    user(10L, Role.STUDENT);

    // when
    String a = service.issueByAdmin(ADMIN, 10L).temporaryPassword();
    String b = service.issueByAdmin(ADMIN, 10L).temporaryPassword();

    // then
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  @DisplayName("given 관리자가 아닌 사람·관리자 계정·없는 회원 when 발급 then 거부하고 저장하지 않는다")
  void issueByAdmin_rejections() {
    // given
    user(10L, Role.STUDENT);
    user(2L, Role.ADMIN);
    when(users.findById(99L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.issueByAdmin(new AuthenticatedPrincipal(5L, "t", "TUTOR"), 10L))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.issueByAdmin(ADMIN, 2L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.issueByAdmin(ADMIN, 99L)).isInstanceOf(IllegalArgumentException.class);
    verify(issues, never()).save(any());
  }

  @Test
  @DisplayName("given 회원 when 계정 찾기 재설정 then 고정값이 아닌 무작위 비밀번호·변경 강제")
  void issueForSelfReset() {
    // given
    User stu = user(10L, Role.STUDENT);

    // when
    String raw = service.issueForSelfReset(stu);

    // then
    assertThat(raw).hasSize(10).isNotEqualTo("reset1234");
    assertThat(stu.isPasswordChangeRequired()).isTrue();
    assertThat(stu.getTempPasswordExpiresAt()).isEqualTo(NOW.plusHours(72));
  }

  @Test
  @DisplayName("given 임시 비밀번호 상태 when 올바른 새 비밀번호 then 바뀌고 임시 상태가 끝난다")
  void change_success() {
    // given
    User stu = user(10L, Role.STUDENT);
    stu.issueTemporaryPassword("hash:temp", NOW.plusHours(1));
    when(encoder.matches("newpass12", "hash:temp")).thenReturn(false);

    // when
    User changed = service.changeTemporaryPassword(10L, "newpass12", "newpass12");

    // then
    assertThat(changed.getPassword()).isEqualTo("hash:newpass12");
    assertThat(changed.isPasswordChangeRequired()).isFalse();
  }

  @Test
  @DisplayName("given 여러 잘못된 경우 when 변경 then 각각 안내와 함께 거부")
  void change_rejections() {
    // given
    User stu = user(10L, Role.STUDENT);
    User normal = user(11L, Role.STUDENT);
    User expired = user(12L, Role.STUDENT);
    stu.issueTemporaryPassword("hash:temp", NOW.plusHours(1));
    expired.issueTemporaryPassword("hash:temp", NOW);
    when(encoder.matches("temp-same1", "hash:temp")).thenReturn(true);

    // when & then
    assertThatThrownBy(() -> service.changeTemporaryPassword(11L, "newpass12", "newpass12")).hasMessageContaining("이미");
    assertThatThrownBy(() -> service.changeTemporaryPassword(12L, "newpass12", "newpass12")).hasMessageContaining("유효기간");
    assertThatThrownBy(() -> service.changeTemporaryPassword(10L, "short", "short")).hasMessageContaining("8자");
    assertThatThrownBy(() -> service.changeTemporaryPassword(10L, "newpass12", "newpass13")).hasMessageContaining("일치");
    assertThatThrownBy(() -> service.changeTemporaryPassword(10L, "temp-same1", "temp-same1")).hasMessageContaining("다른");
    assertThat(stu.isPasswordChangeRequired()).isTrue();
    assertThat(normal.getPassword()).isEqualTo("old");
  }
}
