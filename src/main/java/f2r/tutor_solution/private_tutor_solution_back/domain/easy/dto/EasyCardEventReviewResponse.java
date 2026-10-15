package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessCardAccountingService.PendingReviewItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCardClassificationRecommendationService;
import java.time.LocalDateTime;

/**
 * accountId/accountName is the rule-based suggestion while PENDING_REVIEW, and the actual
 * posted account once the event has a linked transaction.
 * recommendation is the Phase 5A.2 classification recommendation (if PENDING_REVIEW).
 */
public record EasyCardEventReviewResponse(
    Long cardEventId,
    Long businessId,
    Long instrumentId,
    String instrumentIssuer,
    String instrumentLast4,
    String eventType,
    String reviewStatus,
    long amount,
    String currency,
    LocalDateTime occurredAt,
    String providerExternalId,
    Long accountId,
    String accountName,
    Long postedTxnId,
    String reviewedBy,
    LocalDateTime reviewedAt,
    EasyCardClassificationRecommendationResponse recommendation
) {
  public static EasyCardEventReviewResponse of(EasyCardEvent event, Long suggestedAccountId,
      String suggestedAccountName, EasyCardClassificationRecommendationResponse recommendation) {
    EasyAccount posted = event.getPostedTxn() == null ? null : event.getPostedTxn().getAccount();
    return new EasyCardEventReviewResponse(
        event.getCardEventId(),
        event.getBusiness().getBusinessId(),
        event.getInstrument().getInstrumentId(),
        event.getInstrument().getIssuer(),
        event.getInstrument().getLast4(),
        event.getEventType().name(),
        event.getReviewStatus().name(),
        event.getAmount(),
        event.getCurrency(),
        event.getOccurredAt(),
        event.getProviderExternalId(),
        posted != null ? posted.getAccountId() : suggestedAccountId,
        posted != null ? posted.getName() : suggestedAccountName,
        event.getPostedTxn() == null ? null : event.getPostedTxn().getTxnId(),
        event.getReviewedBy(),
        event.getReviewedAt(),
        recommendation);
  }

  public static EasyCardEventReviewResponse from(PendingReviewItem item,
      EasyCardClassificationRecommendationResponse recommendation) {
    return of(item.event(), item.suggestedAccountId(), item.suggestedAccountName(), recommendation);
  }

  public static EasyCardEventReviewResponse from(EasyCardEvent event) {
    return of(event, null, null, null);
  }
}
