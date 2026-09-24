package f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생의 강좌(LessonProduct) 수강권(작업지시서 §6). 지금은 재원생 무료 복습수업 배정이지만,
 * 향후 유료 영문법 강좌 수강권까지 같은 모델로 확장한다({@link LmsGrantSource#PURCHASE}는
 * 값만 미리 열어 두고 이번 Phase에서는 쓰지 않는다).
 *
 * <p>기존 {@code LessonApplication}(신청)을 확장하지 않는다 — "신청"과 "수강 권한"은 서로 다른
 * 개념이라 하나를 억지로 겸용하면 결제/승인 도메인이 붙을 때 다시 갈라내야 한다.
 */
@Entity
@Table(name = "lms_enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LmsEnrollment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "lms_enrollment_id")
  private Long lmsEnrollmentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private Student student;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lesson_id", nullable = false)
  private LessonProduct lessonProduct;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private LmsEnrollmentStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "grant_source", nullable = false, length = 20)
  private LmsGrantSource grantSource;

  @Column(name = "granted_at", nullable = false)
  private LocalDateTime grantedAt;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Builder
  private LmsEnrollment(
      Student student, Academy academy, LessonProduct lessonProduct,
      LmsEnrollmentStatus status, LmsGrantSource grantSource,
      LocalDateTime grantedAt, LocalDateTime expiresAt) {
    if (student == null) throw new IllegalArgumentException("student is required");
    if (academy == null) throw new IllegalArgumentException("academy is required");
    if (lessonProduct == null) throw new IllegalArgumentException("lessonProduct is required");
    if (grantSource == null) throw new IllegalArgumentException("grantSource is required");
    this.student = student;
    this.academy = academy;
    this.lessonProduct = lessonProduct;
    this.status = status != null ? status : LmsEnrollmentStatus.ACTIVE;
    this.grantSource = grantSource;
    this.grantedAt = grantedAt != null ? grantedAt : LocalDateTime.now();
    this.expiresAt = expiresAt;
  }

  public boolean isActive(LocalDateTime asOf) {
    if (status != LmsEnrollmentStatus.ACTIVE) return false;
    return expiresAt == null || expiresAt.isAfter(asOf);
  }

  /** 취소된 수강권을 같은 학교/시험 재배정 때 다시 살린다(새 행을 만들지 않는다 — 유니크 제약). */
  public void reactivate(LmsGrantSource grantSource, LocalDateTime grantedAt, LocalDateTime expiresAt) {
    this.status = LmsEnrollmentStatus.ACTIVE;
    this.grantSource = grantSource != null ? grantSource : this.grantSource;
    this.grantedAt = grantedAt != null ? grantedAt : LocalDateTime.now();
    this.expiresAt = expiresAt;
  }

  public void revoke() {
    this.status = LmsEnrollmentStatus.REVOKED;
  }
}
