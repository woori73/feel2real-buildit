package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;

public record MyProfileResponse(
    Long userId,
    String loginId,
    String role,
    String name,
    String nickname,
    String displayName,
    String email,
    String phone,
    Long studentId,
    String schoolName,
    Byte grade,
    String parentName,
    String parentPhone
) {

  public static MyProfileResponse from(User user) {
    return new MyProfileResponse(
        user.getUserId(),
        user.getLoginId(),
        user.getRole().name(),
        user.getName(),
        user.getNickname(),
        user.getDisplayName(),
        user.getEmail(),
        user.getPhone(),
        null,
        null,
        null,
        null,
        null);
  }

  public static MyProfileResponse from(User user, Student student) {
    return new MyProfileResponse(
        user.getUserId(),
        user.getLoginId(),
        user.getRole().name(),
        user.getName(),
        user.getNickname(),
        user.getDisplayName(),
        user.getEmail(),
        user.getPhone(),
        student.getStudentId(),
        student.getSchoolName(),
        student.getGrade(),
        student.getParentName(),
        student.getParentPhone());
  }
}
