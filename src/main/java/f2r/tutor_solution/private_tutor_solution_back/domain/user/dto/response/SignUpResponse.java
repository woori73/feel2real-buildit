package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import lombok.Getter;

@Getter
public class SignUpResponse {

  private final Long userId;
  private final String loginId;
  private final String name;

  private SignUpResponse(Long userId, String loginId, String name) {
    this.userId = userId;
    this.loginId = loginId;
    this.name = name;
  }

  public static SignUpResponse from(User user) {
    return new SignUpResponse(
        user.getUserId(),
        user.getLoginId(),
        user.getName()
    );
  }
}
