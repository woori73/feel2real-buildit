package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyMerchantAccountMapping;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCardClassificationRecommendationService;

/**
 * Admin merchant account mapping response.
 */
public record EasyMerchantMappingResponse(
    Long mappingId,
    Long businessId,
    String businessCode,
    String rawMerchant,
    String normalizedMerchant,
    Long accountId,
    String accountName,
    String accountCode,
    EasyMerchantAccountMapping.Source source,
    EasyMerchantAccountMapping.Confidence confidence,
    boolean active,
    String createdBy,
    String updatedBy
) {
  public static EasyMerchantMappingResponse from(EasyMerchantAccountMapping mapping) {
    return new EasyMerchantMappingResponse(
        mapping.getMappingId(),
        mapping.getBusiness().getBusinessId(),
        mapping.getBusiness().getCode(),
        mapping.getRawMerchant(),
        mapping.getNormalizedMerchant(),
        mapping.getAccount().getAccountId(),
        mapping.getAccount().getName(),
        mapping.getAccount().getCode(),
        mapping.getSource(),
        mapping.getConfidence(),
        mapping.isActive(),
        mapping.getCreatedBy(),
        mapping.getUpdatedBy()
    );
  }
}