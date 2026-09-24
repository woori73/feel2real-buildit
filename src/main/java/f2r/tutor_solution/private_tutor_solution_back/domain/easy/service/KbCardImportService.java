package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.KbCardNormalizedTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.KbCardRawRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardInstrument;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KB "개인사업자용 이용내역" end-to-end: RAW SOURCE -> PARSE -> NORMALIZE -> MATCH/DEDUPE ->
 * EasyCardEvent (PENDING_REVIEW). Never writes easy_transactions/journal entries directly — that
 * boundary stays {@link EasyBusinessCardAccountingService#approveAndPost} only, after admin
 * review, unchanged from the existing Phase 5A.1 contract.
 *
 * <p>Business ownership is caller-supplied configuration ({@code businessId} parameter), never
 * inferred from row content.
 *
 * <p>Negative rows (should KB ever report cancellations/refunds that way — currently UNKNOWN,
 * zero observed) are matched to an earlier positive row in the same import batch by (cardName,
 * merchant, paymentMethod, abs(amount)), FIFO by usage date. A negative that cannot be matched
 * is never dropped and never sign-flipped — it is reported in {@link ImportResult#unmatchedNegatives()}
 * for manual reconciliation, and is deliberately NOT turned into an {@code EasyCardEvent}.
 *
 * <p>Foreign (USD) rows DO become PENDING_REVIEW events with currency USD and exact US-cent
 * amounts — no FX rate is invented, and {@code approveAndPost} explicitly rejects non-KRW, so
 * they can be reviewed but never wrongly posted.
 *
 * <p><b>Boundary dedupe</b>: a positive row whose (business, occurredOn, amount) already matches
 * an existing {@code easy_transactions} row is skipped entirely — no {@code EasyCardEvent} is
 * created for it, independent of whatever date range was requested.
 *
 * <p>Dedupe identity is stronger than Shinhan's: KB provides a real per-row 승인번호, which is
 * part of the stable external id and passed as the approval reference (hashed into the event
 * fingerprint).
 */
@Service
@RequiredArgsConstructor
public class KbCardImportService {

  private static final String PROVIDER_CODE = "KB";
  private static final String ISSUER = "KB국민카드";

  private final KbCardExcelParser parser;
  private final KbCardNormalizer normalizer;
  private final EasyBusinessCardAccountingService accountingService;
  private final EasyTransactionRepository transactionRepository;

  public record ImportResult(
      int sourceRows,
      int positiveRows,
      int negativeRows,
      int foreignRows,
      long netSourceAmount,
      int matchedCancellationPairs,
      int unmatchedNegativeCount,
      List<KbCardNormalizedTransaction> unmatchedNegatives,
      int boundaryDuplicateSkipped,
      int newPurchaseEvents,
      int duplicatePurchaseEvents,
      int newCancellationEvents,
      int duplicateCancellationEvents) {
  }

  @Transactional
  public ImportResult importFor(Long businessId, InputStream xls) {
    if (businessId == null) throw new IllegalArgumentException("businessId is required (configuration, never inferred).");

    List<KbCardRawRow> rawRows = parser.parse(xls);
    List<KbCardNormalizedTransaction> normalized = normalizer.normalize(rawRows, businessId);

    long netAmount = normalized.stream().mapToLong(KbCardNormalizedTransaction::eventAmount).sum();
    int positiveRows = (int) normalized.stream().filter(t -> t.eventAmount() > 0).count();
    int negativeRows = (int) normalized.stream().filter(t -> t.eventAmount() < 0).count();
    int foreignRows = (int) normalized.stream().filter(KbCardNormalizedTransaction::isForeign).count();

    Map<String, Deque<KbCardNormalizedTransaction>> unmatchedPositivesByGroup = new HashMap<>();
    List<KbCardNormalizedTransaction> unmatchedNegatives = new java.util.ArrayList<>();
    record Pair(KbCardNormalizedTransaction positive, KbCardNormalizedTransaction negative) {}
    List<Pair> matchedPairs = new java.util.ArrayList<>();

    List<KbCardNormalizedTransaction> positives = normalized.stream()
        .filter(t -> t.eventAmount() > 0)
        .sorted(Comparator.comparing(KbCardNormalizedTransaction::usageDate)
            .thenComparing(t -> t.confirmationDate() != null ? t.confirmationDate() : t.usageDate())
            .thenComparing(KbCardNormalizedTransaction::occurrenceIndex))
        .toList();

    for (KbCardNormalizedTransaction t : positives) {
      unmatchedPositivesByGroup
          .computeIfAbsent(matchGroupKey(t, t.eventAmount()), k -> new ArrayDeque<>())
          .addLast(t);
    }
    for (KbCardNormalizedTransaction t : normalized) {
      if (t.eventAmount() >= 0) continue;
      Deque<KbCardNormalizedTransaction> candidates =
          unmatchedPositivesByGroup.get(matchGroupKey(t, -t.eventAmount()));
      KbCardNormalizedTransaction match = candidates == null ? null : candidates.pollFirst();
      if (match == null) {
        unmatchedNegatives.add(t);
      } else {
        matchedPairs.add(new Pair(match, t));
      }
    }

    int newPurchases = 0, dupPurchases = 0, newCancellations = 0, dupCancellations = 0, boundarySkipped = 0;
    Map<String, EasyCardInstrument> instrumentCache = new HashMap<>();

    Map<KbCardNormalizedTransaction, Long> purchaseEventIdByTxn = new HashMap<>();
    for (KbCardNormalizedTransaction t : normalized) {
      if (t.eventAmount() <= 0) continue;
      if (isBoundaryDuplicate(t)) {
        boundarySkipped++;
        continue;
      }
      EasyCardInstrument instrument = instrumentCache.computeIfAbsent(t.cardName(),
          card -> accountingService.registerInstrument(businessId, null, PROVIDER_CODE, ISSUER,
              instrumentToken(businessId, card), card, null));
      var result = accountingService.observePurchase(new EasyBusinessCardAccountingService.PurchaseCommand(
          businessId, null, instrument.getInstrumentId(), PROVIDER_CODE, rowExternalId(t),
          t.usageDate().atStartOfDay(), t.confirmationDate(), t.eventAmount(), t.currency(),
          t.approvalNumber()));
      if (result.duplicate()) dupPurchases++; else newPurchases++;
      purchaseEventIdByTxn.put(t, result.event().getCardEventId());
    }

    for (Pair pair : matchedPairs) {
      Long originalEventId = purchaseEventIdByTxn.get(pair.positive());
      if (originalEventId == null) {
        unmatchedNegatives.add(pair.negative());
        continue;
      }
      var result = accountingService.observeCancellation(originalEventId, PROVIDER_CODE,
          rowExternalId(pair.negative()), pair.negative().usageDate().atStartOfDay(),
          pair.negative().confirmationDate(), -pair.negative().eventAmount());
      if (result.duplicate()) dupCancellations++; else newCancellations++;
    }

    return new ImportResult(normalized.size(), positiveRows, negativeRows, foreignRows, netAmount,
        matchedPairs.size(), unmatchedNegatives.size(), unmatchedNegatives, boundarySkipped,
        newPurchases, dupPurchases, newCancellations, dupCancellations);
  }

  /** True if an existing easy_transactions row already accounts for this exact (business, date, amount). */
  private boolean isBoundaryDuplicate(KbCardNormalizedTransaction t) {
    List<EasyTransaction> sameDay = transactionRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            t.businessId(), t.usageDate(), t.usageDate());
    return sameDay.stream().anyMatch(existing -> existing.getAmount() == t.eventAmount());
  }

  private String matchGroupKey(KbCardNormalizedTransaction t, long absAmount) {
    return String.join("|", t.cardName(), t.merchantName(), t.paymentMethod(), String.valueOf(absAmount));
  }

  /** Stable per-source-row identity: same file re-parsed -> same id -> observePurchase/observeCancellation dedupe by fingerprint. */
  private String rowExternalId(KbCardNormalizedTransaction t) {
    return String.join("|", PROVIDER_CODE, String.valueOf(t.businessId()), t.cardName(),
        t.usageDate().toString(), t.merchantName(), t.paymentMethod(), t.currency(),
        String.valueOf(t.eventAmount()), t.approvalNumber(), String.valueOf(t.occurrenceIndex()));
  }

  private String instrumentToken(Long businessId, String cardName) {
    return String.join("|", PROVIDER_CODE, String.valueOf(businessId), cardName);
  }
}
