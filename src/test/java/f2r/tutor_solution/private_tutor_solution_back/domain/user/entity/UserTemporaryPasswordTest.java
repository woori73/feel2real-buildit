package f2r.tutor_solution.private_tutor_solution_back.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("User — 임시 비밀번호 상태")
class UserTemporaryPasswordTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 12, 0);

  private User user() {
    return User.builder().loginId("stu").password("h0").name("학생").nickname("도도").role(Role.STUDENT).build();
  }

  @Test
  @DisplayName("given 회원 when 임시 비밀번호 발급 then 해시·변경 강제·유효기간이 들어간다")
  void issueTemporaryPassword_setsState() {
    // given
    User u = user();

    // when
    u.issueTemporaryPassword("h1", NOW.plusHours(72));

    // then
    assertThat(u.getPassword()).isEqualTo("h1");
    assertThat(u.isPasswordChangeRequired()).isTrue();
    assertThat(u.getTempPasswordExpiresAt()).isEqualTo(NOW.plusHours(72));
  }

  @Test
  @DisplayName("given 임시 비밀번호 상태 when 유효기간 전·당일·후 then 만료 여부가 맞다")
  void isTemporaryPasswordExpired() {
    // given
    User u = user();
    u.issueTemporaryPassword("h1", NOW);

    // when & then
    assertThat(u.isTemporaryPasswordExpired(NOW.minusSeconds(1))).isFalse();
    assertThat(u.isTemporaryPasswordExpired(NOW)).isTrue();
    assertThat(u.isTemporaryPasswordExpired(NOW.plusDays(1))).isTrue();
  }

  @Test
  @DisplayName("given 일반 회원 when 만료 확인 then 항상 false")
  void notTemporary_neverExpired() {
    // given
    User u = user();

    // when & then
    assertThat(u.isTemporaryPasswordExpired(NOW)).isFalse();
  }

  @Test
  @DisplayName("given 임시 비밀번호 상태 when 회원이 비밀번호를 바꾸면 then 임시 상태가 끝난다")
  void updatePassword_clearsTemporaryState() {
    // given
    User u = user();
    u.issueTemporaryPassword("h1", NOW.plusHours(72));

    // when
    u.updatePassword("h2");

    // then
    assertThat(u.getPassword()).isEqualTo("h2");
    assertThat(u.isPasswordChangeRequired()).isFalse();
    assertThat(u.getTempPasswordExpiresAt()).isNull();
  }
}
