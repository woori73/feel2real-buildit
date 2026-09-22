package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyMerchantAccountMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyMerchantAccountMappingRepository extends JpaRepository<EasyMerchantAccountMapping, Long> {

  /**
   * Priority 1: Explicit admin mapping by business + normalized merchant.
   * Most specific - exact normalized merchant match for this business.
   */
  Optional<EasyMerchantAccountMapping> findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(
      Long businessId, String normalizedMerchant);

  /**
   * Priority 1 fallback: Explicit admin mapping by business + raw merchant (before normalization).
   * Used when normalized merchant doesn't match but raw does.
   */
  Optional<EasyMerchantAccountMapping> findByBusiness_BusinessIdAndRawMerchantAndActiveTrue(
      Long businessId, String rawMerchant);

  /**
   * Find all active mappings for a business (for admin UI listing).
   */
  List<EasyMerchantAccountMapping> findByBusiness_BusinessIdAndActiveTrueOrderByCreatedAtDesc(Long businessId);

  /**
   * Find all active mappings by account (for admin UI filtering).
   */
  List<EasyMerchantAccountMapping> findByAccount_AccountIdAndActiveTrue(Long accountId);

  /**
   * Priority 2/3: Find by business + normalized merchant (includes inactive for historical reference).
   */
  Optional<EasyMerchantAccountMapping> findByBusiness_BusinessIdAndNormalizedMerchant(
      Long businessId, String normalizedMerchant);

  /**
   * Find mappings by source for auditing.
   */
  List<EasyMerchantAccountMapping> findBySource(EasyMerchantAccountMapping.Source source);
}