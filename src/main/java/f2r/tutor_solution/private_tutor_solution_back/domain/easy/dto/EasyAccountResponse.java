package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;

public record EasyAccountResponse(
    Long accountId,
    String code,
    String name,
    String direction,
    int sortOrder,
    boolean active
) {
  public static EasyAccountResponse from(EasyAccount account) {
    return new EasyAccountResponse(
        account.getAccountId(), account.getCode(), account.getName(),
        account.getDirection().name(), account.getSortOrder(), account.isActive());
  }
}
