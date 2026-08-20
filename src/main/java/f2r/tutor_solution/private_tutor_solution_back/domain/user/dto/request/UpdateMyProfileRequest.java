package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request;

public record UpdateMyProfileRequest(
    String name,
    String nickname,
    String email,
    String phone,
    String currentPassword,
    String newPassword,
    String schoolName,
    Byte grade,
    String parentName,
    String parentPhone
) {
  public UpdateMyProfileRequest(
      String name,
      String email,
      String phone,
      String currentPassword,
      String newPassword,
      String schoolName,
      Byte grade,
      String parentName,
      String parentPhone) {
    this(name, null, email, phone, currentPassword, newPassword, schoolName, grade, parentName, parentPhone);
  }
}
