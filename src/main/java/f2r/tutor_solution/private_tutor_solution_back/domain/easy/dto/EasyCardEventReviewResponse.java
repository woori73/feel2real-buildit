package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessCardAccountingService.PendingReviewItem;
import java.time.LocalDateTime;

/**
 * accountId/accountName is the rule-based suggestion while PENDING_REVIEW, and the actual
 * posted account once the event has a linked transaction.
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
    LocalDateTime reviewedAt
) {
  public static EasyCardEventReviewResponse of(EasyCardEvent event, Long suggestedAccountId,
      String suggestedAccountName) {
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
        event.getReviewedAt());
  }

  public static EasyCardEventReviewResponse from(PendingReviewItem item) {
    return of(item.event(), item.suggestedAccountId(), item.suggestedAccountName());
  }

  public static EasyCardEventReviewResponse from(EasyCardEvent event) {
    return of(event, null, null);
  }
}
