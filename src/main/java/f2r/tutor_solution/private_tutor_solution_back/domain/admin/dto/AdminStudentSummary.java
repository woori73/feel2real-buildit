package f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;

import java.time.LocalDateTime;

/**
 * Phase 10 — 어드민 대시보드 학생 카드용 요약 DTO.
 *
 * <p>학생 기본 정보(이름·학교·학년·loginId) + 활동 요약(자료 개수·대화 개수).
 * 어드민이 학생 카드를 클릭하면 임퍼소네이트로 학생 대시보드에 진입한다.
 */
public record AdminStudentSummary(
    Long studentId,
    Long userId,
    String loginId,
    String name,
    String schoolName,
    Byte grade,
    long sourceCount,
    long conversationCount,
    LocalDateTime createdAt
) {
  public static AdminStudentSummary of(Student student, long sourceCount, long conversationCount) {
    return new AdminStudentSummary(
        student.getStudentId(),
        student.getUser().getUserId(),
        student.getUser().getLoginId(),
        student.getUser().getName(),
        student.getSchoolName(),
        student.getGrade(),
        sourceCount,
        conversationCount,
        student.getCreatedAt());
  }
}
