package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCardClassificationRecommendationService;
import java.util.List;

/**
 * Account classification recommendation response for PENDING_REVIEW card events.
 */
public record EasyCardClassificationRecommendationResponse(
    Long recommendedAccountId,
    String recommendedAccountName,
    String recommendedAccountCode,
    EasyCardClassificationRecommendationService.Source source,
    EasyCardClassificationRecommendationService.Confidence confidence,
    String reason,
    String normalizedMerchant,
    String rawMerchant
) {
  public static EasyCardClassificationRecommendationResponse from(
      EasyCardClassificationRecommendationService.Recommendation recommendation) {
    return new EasyCardClassificationRecommendationResponse(
        recommendation.recommendedAccountId(),
        recommendation.recommendedAccountName(),
        recommendation.recommendedAccountCode(),
        recommendation.source(),
        recommendation.confidence(),
        recommendation.reason(),
        recommendation.normalizedMerchant(),
        recommendation.rawMerchant()
    );
  }
}