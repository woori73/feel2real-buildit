package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;

import java.util.List;

public record LoginIdFindResponse(String email, List<Account> accounts) {

  public record Account(String loginId, Role role, String roleLabel) {
  }
}
