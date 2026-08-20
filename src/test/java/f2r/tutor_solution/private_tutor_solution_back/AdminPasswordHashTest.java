package f2r.tutor_solution.private_tutor_solution_back;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 admin(admin-f2r) 계정의 비밀번호 해시가 평문과 매칭되는지 검증한다.
 *
 * <p>V2 마이그레이션에 박혀 있는 해시는 임의의 BCrypt salt로 생성된 한 번뿐인 값이라,
 * 매번 재생성 없이 이 테스트로 회귀를 막는다. 비밀번호를 바꿀 일이 생기면 새 해시를 생성해
 * 마이그레이션과 본 테스트 양쪽을 동시에 갱신한다.
 */
class AdminPasswordHashTest {

  /** {@code db/migration/V2__add_admin_account.sql}에 박힌 해시와 동일해야 한다. */
  private static final String ADMIN_HASH_IN_MIGRATION =
      "$2y$10$b0GANweD8ce2KtqHWbV1Cuvgs6S8Ks6kqhLVTOyNLl9ivR/7Iq4.a";
  private static final String ADMIN_PASSWORD = "admin-mochi-96";

  @Test
  void givenMigrationHash_whenMatchesAdminPassword_thenTrue() {
    // given
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // when
    boolean matches = encoder.matches(ADMIN_PASSWORD, ADMIN_HASH_IN_MIGRATION);

    // then
    assertThat(matches)
        .as("V2 마이그레이션의 admin-f2r 해시가 평문 admin-mochi-96과 일치해야 한다")
        .isTrue();
  }
}
