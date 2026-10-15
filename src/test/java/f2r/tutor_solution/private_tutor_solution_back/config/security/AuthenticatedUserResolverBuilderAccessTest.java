package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("빌더 계열 권한 — TUTOR/ADMIN 허용, 소유권과 TUTOR 전용 검사는 유지")
class AuthenticatedUserResolverBuilderAccessTest {

  private final AuthenticatedUserResolver resolver = new AuthenticatedUserResolver(
      mock(f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository.class),
      mock(f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository.class),
      mock(f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository.class),
      mock(f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository.class));

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static JwtTokenProvider.AuthenticatedPrincipal principal(long id, Role role) {
    return new JwtTokenProvider.AuthenticatedPrincipal(id, "u" + id, role.name());
  }

  private static void login(JwtTokenProvider.AuthenticatedPrincipal p) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(p, null, List.of()));
  }

  private static Academy academyOwnedBy(long ownerId) {
    User owner = mock(User.class);
    org.mockito.Mockito.when(owner.getUserId()).thenReturn(ownerId);
    return Academy.builder().tutor(owner).build();
  }

  @Test
  @DisplayName("given TUTOR/ADMIN, when 빌더 역할 검사, then 통과한다")
  void builderRole_allowsTutorAndAdmin() {
    assertThatCode(() -> resolver.requireAcademyBuilderRole(principal(1, Role.TUTOR))).doesNotThrowAnyException();
    assertThatCode(() -> resolver.requireAcademyBuilderRole(principal(2, Role.ADMIN))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("given STUDENT/비로그인, when 빌더 역할 검사, then 403이다")
  void builderRole_rejectsStudentAndAnonymous() {
    assertThatThrownBy(() -> resolver.requireAcademyBuilderRole(principal(3, Role.STUDENT)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> resolver.requireAcademyBuilderRole(null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given ADMIN 본인 학원, when 빌더 소유자 검증, then 통과한다")
  void builderOwnership_allowsAdminOwnAcademy() {
    login(principal(2, Role.ADMIN));
    assertThatCode(() -> resolver.verifyOwnsAcademyForBuilder(academyOwnedBy(2))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("given ADMIN이 타인 학원, when 빌더 소유자 검증, then 403이다 (소유권 보호)")
  void builderOwnership_rejectsAdminOnOthersAcademy() {
    login(principal(2, Role.ADMIN));
    assertThatThrownBy(() -> resolver.verifyOwnsAcademyForBuilder(academyOwnedBy(99)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given TUTOR, when 본인/타인 학원 검증, then 본인만 통과한다 (기존 동작 유지)")
  void builderOwnership_tutorUnchanged() {
    login(principal(1, Role.TUTOR));
    assertThatCode(() -> resolver.verifyOwnsAcademyForBuilder(academyOwnedBy(1))).doesNotThrowAnyException();
    assertThatThrownBy(() -> resolver.verifyOwnsAcademyForBuilder(academyOwnedBy(99)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given STUDENT, when 빌더 소유자 검증, then 본인 학원이라도 403이다")
  void builderOwnership_rejectsStudent() {
    login(principal(3, Role.STUDENT));
    assertThatThrownBy(() -> resolver.verifyOwnsAcademyForBuilder(academyOwnedBy(3)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given ADMIN, when TUTOR 전용 검사(requireTutor/verifyOwnsAcademy), then 여전히 403이다")
  void tutorOnlyChecksAreUnchanged() {
    login(principal(2, Role.ADMIN));
    assertThatThrownBy(() -> resolver.requireTutor(principal(2, Role.ADMIN)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> resolver.verifyOwnsAcademy(academyOwnedBy(2)))
        .isInstanceOf(AccessDeniedException.class);
  }
}
