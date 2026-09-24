package f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto;

import java.time.LocalDateTime;
import java.util.List;

/** LMS 수강권 관리(`/api/tutor/lms/{handle}/enrollments`) DTO 모음. */
public final class LmsEnrollmentDtos {

  private LmsEnrollmentDtos() {}

  /**
   * 배정 후보 학생. {@code lesson_applications}(기존 신청/승인)에서 후보를 뽑아 보여줄 뿐
   * 그 레코드를 수강권으로 바꾸지 않는다 — 운영자가 후보 중 고르면 별도 {@code LmsEnrollment}
   * 행이 새로 생긴다.
   */
  public record CandidateStudent(
      Long studentId, String studentName, String schoolName, String gradeLabel, boolean enrolled) {}

  public record EnrollmentView(
      Long lmsEnrollmentId,
      Long studentId,
      String studentName,
      String status,
      String grantSource,
      LocalDateTime grantedAt,
      LocalDateTime expiresAt) {}

  public record LessonEnrollmentView(
      Long lessonId, List<CandidateStudent> candidates, List<EnrollmentView> enrollments) {}

  public record GrantRequest(Long studentId, LocalDateTime expiresAt) {}
}
