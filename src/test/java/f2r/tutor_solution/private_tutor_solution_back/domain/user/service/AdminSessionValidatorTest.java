package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdminSessionValidator — ADMIN 세션만 자격 증명 stamp로 검사")
class AdminSessionValidatorTest {

  private UserRepository repo;
  private User admin;
  private AdminSessionValidator validator;

  @BeforeEach
  void setUp() {
    repo = mock(UserRepository.class);
    admin = User.builder().loginId("admin-a").password("$2y$10$hash-one").name("관리자").nickname("관리자").role(Role.ADMIN).build();
    when(repo.findById(1L)).thenReturn(Optional.of(admin));
    validator = new AdminSessionValidator(repo, true);
  }

  private AuthenticatedPrincipal adminPrincipal(String stamp) {
    return new AuthenticatedPrincipal(1L, "admin-a", "ADMIN", stamp);
  }

  @Test
  @DisplayName("given 현재 계정의 stamp를 가진 ADMIN 토큰 when 검사하면 then 유효하다")
  void currentStamp_valid() {
    assertThat(validator.isValid(adminPrincipal(validator.stampFor(1L)))).isTrue();
  }

  @Test
  @DisplayName("given 비밀번호가 바뀐 뒤 when 이전 stamp로 검사하면 then 무효다")
  void passwordChanged_invalid() {
    String old = validator.stampFor(1L);
    admin.updatePassword("$2y$10$hash-two");
    assertThat(validator.isValid(adminPrincipal(old))).isFalse();
  }

  @Test
  @DisplayName("given 로그인 ID가 바뀐 뒤 when 이전 stamp로 검사하면 then 무효다")
  void loginIdChanged_invalid() {
    String old = validator.stampFor(1L);
    admin.updateLoginId("admin-b");
    assertThat(validator.isValid(adminPrincipal(old))).isFalse();
  }

  @Test
  @DisplayName("given stamp 없는 ADMIN 토큰(기능 이전 발급)·위조 stamp·없는 사용자 when 검사하면 then 무효다")
  void missingForgedOrUnknown_invalid() {
    assertThat(validator.isValid(adminPrincipal(null))).isFalse();
    assertThat(validator.isValid(adminPrincipal("forged-stamp-value-000"))).isFalse();
    assertThat(validator.isValid(new AuthenticatedPrincipal(99L, "ghost", "ADMIN", validator.stampFor(1L)))).isFalse();
  }

  @Test
  @DisplayName("given ADMIN 토큰의 사용자가 이제 ADMIN이 아니면 when 검사하면 then 무효다")
  void demoted_invalid() {
    String stamp = validator.stampFor(1L);
    User student = User.builder().loginId("admin-a").password("$2y$10$hash-one").name("x").nickname("x").role(Role.STUDENT).build();
    when(repo.findById(1L)).thenReturn(Optional.of(student));
    assertThat(validator.isValid(adminPrincipal(stamp))).isFalse();
  }

  @Test
  @DisplayName("given 일반 회원·TUTOR 토큰 when 검사하면 then DB를 조회하지 않고 항상 유효하다(영향 없음)")
  void nonAdmin_untouched() {
    UserRepository untouched = mock(UserRepository.class);
    AdminSessionValidator v = new AdminSessionValidator(untouched, true);
    assertThat(v.isValid(new AuthenticatedPrincipal(5L, "stu", "STUDENT", null))).isTrue();
    assertThat(v.isValid(new AuthenticatedPrincipal(6L, "tut", "TUTOR", null))).isTrue();
    verifyNoInteractions(untouched);
  }

  @Test
  @DisplayName("given 기능이 꺼진 설정 when 검사하면 then ADMIN도 통과한다(테스트·롤백용 스위치)")
  void disabled_passesThrough() {
    assertThat(new AdminSessionValidator(repo, false).isValid(adminPrincipal(null))).isTrue();
  }

  @Test
  @DisplayName("given stamp when 내용을 보면 then 비밀번호 해시나 ID를 그대로 담지 않는다")
  void stamp_doesNotLeakHash() {
    String stamp = validator.stampFor(1L);
    assertThat(stamp).hasSize(22).doesNotContain("hash-one").doesNotContain("admin-a");
  }
}
