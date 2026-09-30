package f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LmsEnrollmentEntityTest {

  private static LmsEnrollment.LmsEnrollmentBuilder validBuilder() {
    return LmsEnrollment.builder()
        .student(mock(Student.class))
        .academy(mock(Academy.class))
        .lessonProduct(mock(LessonProduct.class))
        .grantSource(LmsGrantSource.ACADEMY);
  }

  @Test
  void builder_throwsWhenStudentMissing() {
    assertThatThrownBy(() -> LmsEnrollment.builder()
        .academy(mock(Academy.class))
        .lessonProduct(mock(LessonProduct.class))
        .grantSource(LmsGrantSource.ACADEMY)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("student");
  }

  @Test
  void builder_defaultsStatusActiveWhenOmitted() {
    LmsEnrollment enrollment = validBuilder().build();

    assertThat(enrollment.getStatus()).isEqualTo(LmsEnrollmentStatus.ACTIVE);
  }

  @Test
  void isActive_trueWhenActiveAndNoExpiry() {
    LmsEnrollment enrollment = validBuilder().build();

    assertThat(enrollment.isActive(LocalDateTime.now())).isTrue();
  }

  @Test
  void isActive_falseAfterExpiry() {
    LocalDateTime now = LocalDateTime.of(2026, 9, 24, 12, 0);
    LmsEnrollment enrollment = validBuilder().expiresAt(now.minusDays(1)).build();

    assertThat(enrollment.isActive(now)).isFalse();
  }

  @Test
  void isActive_falseWhenRevoked() {
    LmsEnrollment enrollment = validBuilder().build();
    enrollment.revoke();

    assertThat(enrollment.isActive(LocalDateTime.now())).isFalse();
    assertThat(enrollment.getStatus()).isEqualTo(LmsEnrollmentStatus.REVOKED);
  }

  @Test
  void reactivate_restoresActiveStatusWithNewGrantMetadata() {
    LmsEnrollment enrollment = validBuilder().build();
    enrollment.revoke();
    LocalDateTime grantedAt = LocalDateTime.of(2026, 9, 24, 9, 0);
    LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 31, 0, 0);

    enrollment.reactivate(LmsGrantSource.ADMIN, grantedAt, expiresAt);

    assertThat(enrollment.getStatus()).isEqualTo(LmsEnrollmentStatus.ACTIVE);
    assertThat(enrollment.getGrantSource()).isEqualTo(LmsGrantSource.ADMIN);
    assertThat(enrollment.getGrantedAt()).isEqualTo(grantedAt);
    assertThat(enrollment.getExpiresAt()).isEqualTo(expiresAt);
    assertThat(enrollment.isActive(grantedAt)).isTrue();
  }
}
