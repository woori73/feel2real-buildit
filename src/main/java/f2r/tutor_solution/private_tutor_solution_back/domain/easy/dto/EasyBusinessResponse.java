package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;

public record EasyBusinessResponse(Long businessId, String code, String name) {
  public static EasyBusinessResponse from(EasyBusiness business) {
    return new EasyBusinessResponse(business.getBusinessId(), business.getCode(), business.getName());
  }
}
