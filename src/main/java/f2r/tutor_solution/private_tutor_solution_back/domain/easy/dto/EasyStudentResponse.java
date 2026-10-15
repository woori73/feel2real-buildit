package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import java.time.LocalDate;

public record EasyStudentResponse(
    Long studentId,
    String name,
    String school,
    Byte grade,
    String phone,
    String parentPhone,
    String status,
    LocalDate enrolledOn,
    String memo,
    long billedTotal,
    long paidTotal,
    long balance
) {
  public static EasyStudentResponse of(EasyStudent student, long billedTotal, long paidTotal) {
    return new EasyStudentResponse(
        student.getStudentId(),
        student.getName(),
        student.getSchool(),
        student.getGrade(),
        student.getPhone(),
        student.getParentPhone(),
        student.getStatus().name(),
        student.getEnrolledOn(),
        student.getMemo(),
        billedTotal,
        paidTotal,
        Math.max(0L, billedTotal - paidTotal));
  }
}
