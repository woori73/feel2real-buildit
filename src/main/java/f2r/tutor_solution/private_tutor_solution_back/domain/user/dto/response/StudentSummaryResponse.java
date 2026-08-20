package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;

public record StudentSummaryResponse(
        Long studentId,
        Long userId,
        String name,
        String loginId,
        String phone,
        String schoolName,
        Byte grade,
        String parentName,
        String parentPhone
) {
    public static StudentSummaryResponse from(Student student) {
        return new StudentSummaryResponse(
                student.getStudentId(),
                student.getUser().getUserId(),
                student.getUser().getName(),
                student.getUser().getLoginId(),
                student.getUser().getPhone(),
                student.getSchoolName(),
                student.getGrade(),
                student.getParentName(),
                student.getParentPhone()
        );
    }
}
