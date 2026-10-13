package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Easy Workspace의 명시적 사업체 경계. HTTP 권한은 ROLE_ADMIN으로 제한하고,
 * 서비스 계층에서는 누락·비활성 사업체를 거부한다.
 */
@Component
@RequiredArgsConstructor
public class EasyBusinessScopeGuard {

  private final EasyBusinessRepository businessRepository;

  public EasyBusiness requireActive(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("businessId는 필수입니다.");
    }
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException(
            "사업체를 찾을 수 없습니다: businessId=" + businessId));
    if (!business.isActive()) {
      throw new IllegalArgumentException("비활성 사업체는 조회할 수 없습니다.");
    }
    return business;
  }
}
