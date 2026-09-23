package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyMerchantAccountMapping;

/**
 * Request to save explicit admin merchant → account mapping.
 * Called when admin clicks "Save as mapping for future" in review UI.
 */
public record EasyMerchantMappingSaveRequest(
    Long businessId,
    String rawMerchant,
    Long accountId,
    String adminId
) {
  public void validate() {
    if (businessId == null) throw new IllegalArgumentException("businessId is required.");
    if (rawMerchant == null || rawMerchant.isBlank()) throw new IllegalArgumentException("rawMerchant is required.");
    if (accountId == null) throw new IllegalArgumentException("accountId is required.");
    if (adminId == null || adminId.isBlank()) throw new IllegalArgumentException("adminId is required.");
  }
}