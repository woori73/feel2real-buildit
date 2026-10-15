package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.AcademyEasyBusinessLink;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.AcademyEasyBusinessLinkRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyTutorAccessService {

  private final AuthenticatedUserResolver authResolver;
  private final AcademyRepository academyRepository;
  private final EasyBusinessRepository businessRepository;
  private final AcademyEasyBusinessLinkRepository linkRepository;

  public List<EasyBusinessResponse> allowedBusinesses(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = requireActiveOwnedAcademy(principal);
    return linkRepository
        .findAllByAcademy_AcademyIdOrderByBusiness_SortOrderAsc(academy.getAcademyId())
        .stream()
        .map(AcademyEasyBusinessLink::getBusiness)
        .filter(EasyBusiness::isActive)
        .map(EasyBusinessResponse::from)
        .toList();
  }

  /**
   * requestedBusinessId is only a selector. Authority comes from the authenticated tutor's
   * Academy and the server-side link row; no client-provided owner or academy identifier is used.
   */
  public EasyBusiness requireAllowedBusiness(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long requestedBusinessId) {
    if (requestedBusinessId == null) {
      throw new IllegalArgumentException("businessId는 필수입니다.");
    }
    Academy academy = requireActiveOwnedAcademy(principal);
    AcademyEasyBusinessLink link = linkRepository
        .findByAcademy_AcademyIdAndBusiness_BusinessId(
            academy.getAcademyId(), requestedBusinessId)
        .orElseThrow(() -> new AccessDeniedException("연결되지 않은 사업체입니다."));
    EasyBusiness business = link.getBusiness();
    if (!business.isActive()) {
      throw new AccessDeniedException("비활성 사업체에는 접근할 수 없습니다.");
    }
    return business;
  }

  @Transactional
  public EasyBusinessResponse link(Long academyId, Long businessId) {
    Academy academy = academyRepository.findById(academyId)
        .orElseThrow(() -> new IllegalArgumentException("Academy를 찾을 수 없습니다."));
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다."));
    if (!linkRepository.existsByAcademy_AcademyIdAndBusiness_BusinessId(
        academyId, businessId)) {
      linkRepository.save(new AcademyEasyBusinessLink(academy, business));
    }
    return EasyBusinessResponse.from(business);
  }

  @Transactional
  public void unlink(Long academyId, Long businessId) {
    linkRepository.deleteByAcademy_AcademyIdAndBusiness_BusinessId(academyId, businessId);
  }

  private Academy requireActiveOwnedAcademy(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    authResolver.requireTutor(principal);
    Academy academy = academyRepository.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new AccessDeniedException("연결된 Academy가 없습니다."));
    authResolver.verifyOwnsAcademy(academy);
    if (academy.getStatus() != AcademyStatus.PUBLISHED || !academy.isVisible()) {
      throw new AccessDeniedException("비활성 Academy에는 Easy 장부 접근이 허용되지 않습니다.");
    }
    return academy;
  }
}
