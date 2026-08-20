package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

/**
 * Academy 공개 홈과 블로그 footer에 표시하는 학원별 사업자 정보.
 * {@code businessInfoUrl}은 legacy JSON 호환 field이며 write 시 사업자등록번호에서 파생된다.
 */
public record AcademyBusinessInfo(
    String businessName,
    String representative,
    String address,
    String businessRegistrationNumber,
    String mailOrderRegistrationNumber,
    String businessInfoUrl,
    String phone,
    String email) {

  public AcademyBusinessInfo {
    businessName = trim(businessName);
    representative = trim(representative);
    address = trim(address);
    businessRegistrationNumber = trim(businessRegistrationNumber);
    mailOrderRegistrationNumber = trim(mailOrderRegistrationNumber);
    businessInfoUrl = trim(businessInfoUrl);
    phone = trim(phone);
    email = trim(email);
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }
}
