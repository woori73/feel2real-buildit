package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import org.springframework.stereotype.Component;

/**
 * Explicit provider verification allow-list. It is intentionally empty until an official provider
 * contract, endpoint, credentials, and lifecycle mapping are approved.
 */
@Component
public class EasyCollectionProviderReadinessPolicy {

  public boolean isVerified(EasyCollectionSource source) {
    return false;
  }
}
