package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("AdminCredentialService — 본인 ID·비밀번호 교체 규칙")
class AdminCredentialServiceTest {

  private static final String CURRENT = "current-pw-for-unit-test-0001";
  private static final String NEW_PW = "brand-new-pw-for-unit-0002";
  private static final String NEW_ID = "owner-console-7391";

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private UserRepository repo;
  private User admin;
  private AdminCredentialService service;

  @BeforeEach
  void setUp() {
    repo = mock(UserRepository.class);
    admin = User.builder().loginId("old-admin-id").password(encoder.encode(CURRENT)).name("관리자")
        .nickname("관리자").role(Role.ADMIN).build();
    when(repo.findById(1L)).thenReturn(Optional.of(admin));
    when(repo.existsByLoginId(any())).thenReturn(false);
    service = new AdminCredentialService(repo, encoder, Clock.systemDefaultZone(), 3, Duration.ofMinutes(10));
  }

  private void assertStatus(Runnable r, HttpStatus expected) {
    assertThatThrownBy(r::run).isInstanceOfSatisfying(ResponseStatusException.class,
        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
  }

  @Test
  @DisplayName("given 올바른 현재 비밀번호와 유효한 새 값 when 교체하면 then ID와 비밀번호가 바뀌고 새 비밀번호는 해시로만 저장된다")
  void rotate_success() {
    // when
    service.rotate(1L, CURRENT, NEW_ID, NEW_ID, NEW_PW, NEW_PW);
    // then
    assertThat(admin.getLoginId()).isEqualTo(NEW_ID);
    assertThat(admin.getPassword()).isNotEqualTo(NEW_PW).startsWith("$2");
    assertThat(encoder.matches(NEW_PW, admin.getPassword())).isTrue();
    assertThat(encoder.matches(CURRENT, admin.getPassword())).isFalse();
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    verify(repo).saveAndFlush(admin);
  }

  @Test
  @DisplayName("given 틀린 현재 비밀번호 when 교체하면 then 401이고 아무것도 바뀌지 않는다")
  void wrongCurrentPassword_unchanged() {
    assertStatus(() -> service.rotate(1L, "wrong-current-password-xyz", NEW_ID, NEW_ID, NEW_PW, NEW_PW), HttpStatus.UNAUTHORIZED);
    assertThat(admin.getLoginId()).isEqualTo("old-admin-id");
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("given 현재 비밀번호를 3번 틀리면 when 다시 시도하면 then 올바른 값이어도 429로 차단된다")
  void repeatedFailures_rateLimited() {
    for (int i = 0; i < 3; i++) {
      assertStatus(() -> service.rotate(1L, "wrong-current-password-xyz", NEW_ID, NEW_ID, NEW_PW, NEW_PW), HttpStatus.UNAUTHORIZED);
    }
    assertStatus(() -> service.rotate(1L, CURRENT, NEW_ID, NEW_ID, NEW_PW, NEW_PW), HttpStatus.TOO_MANY_REQUESTS);
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("given ADMIN이 아닌 계정 또는 없는 계정 when 교체하면 then 403이다")
  void nonAdmin_forbidden() {
    User student = User.builder().loginId("stu").password(encoder.encode(CURRENT)).name("학생").nickname("학생").role(Role.STUDENT).build();
    when(repo.findById(2L)).thenReturn(Optional.of(student));
    when(repo.findById(3L)).thenReturn(Optional.empty());
    assertStatus(() -> service.rotate(2L, CURRENT, NEW_ID, NEW_ID, NEW_PW, NEW_PW), HttpStatus.FORBIDDEN);
    assertStatus(() -> service.rotate(3L, CURRENT, NEW_ID, NEW_ID, NEW_PW, NEW_PW), HttpStatus.FORBIDDEN);
    assertThat(student.getLoginId()).isEqualTo("stu");
  }

  @Test
  @DisplayName("given 약하거나 잘못된 새 값 when 교체하면 then 400/409로 거부하고 바뀌지 않는다")
  void invalidNewValues_rejected() {
    assertStatus(() -> service.rotate(1L, CURRENT, "short", "short", NEW_PW, NEW_PW), HttpStatus.BAD_REQUEST); // ID 형식
    assertStatus(() -> service.rotate(1L, CURRENT, "bad id with space", "bad id with space", NEW_PW, NEW_PW), HttpStatus.BAD_REQUEST);
    assertStatus(() -> service.rotate(1L, CURRENT, "OLD-ADMIN-ID", "OLD-ADMIN-ID", NEW_PW, NEW_PW), HttpStatus.BAD_REQUEST); // 기존 ID와 동일
    assertStatus(() -> service.rotate(1L, CURRENT, NEW_ID, NEW_ID, NEW_PW, NEW_PW + "x"), HttpStatus.BAD_REQUEST); // 확인값 불일치
    assertStatus(() -> service.rotate(1L, CURRENT, NEW_ID, NEW_ID, "short-pw-15char", "short-pw-15char"), HttpStatus.BAD_REQUEST);
    assertStatus(() -> service.rotate(1L, CURRENT, NEW_ID, NEW_ID, "x".repeat(73), "x".repeat(73)), HttpStatus.BAD_REQUEST); // 72바이트 초과
    assertStatus(() -> service.rotate(1L, CURRENT, NEW_ID, NEW_ID, CURRENT, CURRENT), HttpStatus.BAD_REQUEST); // 현재와 동일
    String withId = "prefix-" + NEW_ID + "-suffix-pw";
    assertStatus(() -> service.rotate(1L, CURRENT, NEW_ID, NEW_ID, withId, withId), HttpStatus.BAD_REQUEST); // ID 포함
    assertStatus(() -> service.rotate(1L, CURRENT, NEW_ID, NEW_ID + "x", NEW_PW, NEW_PW), HttpStatus.BAD_REQUEST); // ID 확인값 불일치
    assertStatus(() -> service.rotate(1L, CURRENT, "Admin-F2R", "Admin-F2R", NEW_PW, NEW_PW), HttpStatus.BAD_REQUEST); // 예약(폐기) ID
    when(repo.existsByLoginId(NEW_ID)).thenReturn(true);
    assertStatus(() -> service.rotate(1L, CURRENT, NEW_ID, NEW_ID, NEW_PW, NEW_PW), HttpStatus.CONFLICT); // 이미 사용 중
    assertThat(admin.getLoginId()).isEqualTo("old-admin-id");
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("given 성공·실패 교체 when 로그를 확인하면 then 비밀번호·로그인 ID가 어떤 로그에도 남지 않는다")
  void logsNeverContainSecrets() {
    // given
    var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AdminCredentialService.class);
    var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      // when
      assertStatus(() -> service.rotate(1L, "wrong-current-password-xyz", NEW_ID, NEW_ID, NEW_PW, NEW_PW), HttpStatus.UNAUTHORIZED);
      service.rotate(1L, CURRENT, NEW_ID, NEW_ID, NEW_PW, NEW_PW);
      // then
      String all = appender.list.stream().map(e -> e.getFormattedMessage()).reduce("", String::concat);
      assertThat(all).isNotEmpty().doesNotContain(NEW_PW).doesNotContain(CURRENT).doesNotContain(NEW_ID)
          .doesNotContain("old-admin-id").doesNotContain("wrong-current-password-xyz");
    } finally {
      logger.detachAppender(appender);
    }
  }
}
