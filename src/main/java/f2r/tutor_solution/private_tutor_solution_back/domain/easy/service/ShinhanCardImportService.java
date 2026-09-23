package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.ShinhanCardNormalizedTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.ShinhanCardRawRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardInstrument;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shinhan "개인사업자 이용내역" end-to-end: RAW SOURCE -> PARSE -> NORMALIZE -> MATCH/DEDUPE ->
 * EasyCardEvent (PENDING_REVIEW). Never writes easy_transactions/journal entries directly — that
 * boundary stays {@link EasyBusinessCardAccountingService#approveAndPost} only, after admin
 * review, unchanged from the existing Phase 5A.1 contract.
 *
 * <p>Business ownership is caller-supplied configuration ({@code businessId} parameter), never
 * inferred from row content.
 *
 * <p>Negative rows (cancellation/refund) are matched to an earlier positive row in the same
 * import batch by (maskedCard, merchant, productType, abs(amount)), FIFO by <b>transaction
 * date</b> (earliest first) — this reproduces the real source's actual pairing exactly
 * (verified by hand against a real download: 09/19 쿠팡 +34,500/-34,500 pairs; 09/20 쿠팡
 * 할부3개월 -34,500 has no matching positive in-window and is correctly left unmatched). A negative that cannot be matched is
 * never dropped and never sign-flipped — it is reported in {@link ImportResult#unmatchedNegatives()}
 * for manual reconciliation, and is deliberately NOT turned into an {@code EasyCardEvent}: the
 * entity's own invariant requires a positive amount and, for CANCELLATION, a real original event
 * id (see {@code EasyCardEvent} constructor) — inventing either for an unmatched row would be a
 * bigger, riskier change than this phase needs.
 *
 * <p><b>Boundary dedupe</b>: a positive row whose (business, occurredOn, amount) already matches
 * an existing {@code easy_transactions} row is skipped entirely — no {@code EasyCardEvent} is
 * created for it. This exists specifically for the EasyShop-historical/Shinhan-collector overlap
 * day (e.g. 2026-09-12): trusting a date cutoff alone ("collect from 09-13") is not a safe
 * boundary control on its own — if a collection run is ever re-run with a wider or mis-set range,
 * this check is what actually prevents the same real transaction from being double-counted once
 * an admin approves it, independent of whatever date range was requested.
 */
@Service
@RequiredArgsConstructor
public class ShinhanCardImportService {

  private static final String PROVIDER_CODE = "SHINHAN";
  private static final String ISSUER = "신한카드";

  private final ShinhanCardExcelParser parser;
  private final ShinhanCardNormalizer normalizer;
  private final EasyBusinessCardAccountingService accountingService;
  private final EasyTransactionRepository transactionRepository;

  public record ImportResult(
      int sourceRows,
      int positiveRows,
      int negativeRows,
      long netSourceAmount,
      int matchedCancellationPairs,
      int unmatchedNegativeCount,
      List<ShinhanCardNormalizedTransaction> unmatchedNegatives,
      int boundaryDuplicateSkipped,
      int newPurchaseEvents,
      int duplicatePurchaseEvents,
      int newCancellationEvents,
      int duplicateCancellationEvents) {
  }

  @Transactional
  public ImportResult importFor(Long businessId, InputStream xls) {
    if (businessId == null) throw new IllegalArgumentException("businessId is required (configuration, never inferred).");

    List<ShinhanCardRawRow> rawRows = parser.parse(xls);
    List<ShinhanCardNormalizedTransaction> normalized = normalizer.normalize(rawRows, businessId);

    long netAmount = normalized.stream().mapToLong(ShinhanCardNormalizedTransaction::amount).sum();
    int positiveRows = (int) normalized.stream().filter(t -> t.amount() > 0).count();
    int negativeRows = (int) normalized.stream().filter(t -> t.amount() < 0).count();

    // FIFO match within (maskedCard, merchant, productType, |amount|) groups.
    // Sort positives by transactionDate ASC (earliest first) so matching respects actual
    // transaction chronology, not Excel row order (which is confirmation date DESC).
    Map<String, Deque<ShinhanCardNormalizedTransaction>> unmatchedPositivesByGroup = new HashMap<>();
    List<ShinhanCardNormalizedTransaction> unmatchedNegatives = new ArrayList<>();
    record Pair(ShinhanCardNormalizedTransaction positive, ShinhanCardNormalizedTransaction negative) {}
    List<Pair> matchedPairs = new ArrayList<>();

    List<ShinhanCardNormalizedTransaction> positives = normalized.stream()
        .filter(t -> t.amount() > 0)
        .sorted(Comparator.comparing(ShinhanCardNormalizedTransaction::transactionDate)
            .thenComparing(ShinhanCardNormalizedTransaction::confirmationDate)
            .thenComparing(ShinhanCardNormalizedTransaction::occurrenceIndex))
        .toList();

    for (ShinhanCardNormalizedTransaction t : positives) {
      unmatchedPositivesByGroup
          .computeIfAbsent(matchGroupKey(t, t.amount()), k -> new ArrayDeque<>())
          .addLast(t);
    }
    for (ShinhanCardNormalizedTransaction t : normalized) {
      if (t.amount() >= 0) continue;
      Deque<ShinhanCardNormalizedTransaction> candidates =
          unmatchedPositivesByGroup.get(matchGroupKey(t, -t.amount()));
      ShinhanCardNormalizedTransaction match = candidates == null ? null : candidates.pollFirst();
      if (match == null) {
        unmatchedNegatives.add(t);
      } else {
        matchedPairs.add(new Pair(match, t));
      }
    }

    int newPurchases = 0, dupPurchases = 0, newCancellations = 0, dupCancellations = 0, boundarySkipped = 0;
    Map<String, EasyCardInstrument> instrumentCache = new HashMap<>();

    // Register every distinct card, then observe every positive row as PURCHASE — unless it's a
    // boundary duplicate of an already-existing easy_transactions row (see class javadoc).
    // Otherwise dedupe happens inside observePurchase via fingerprint, not here.
    Map<ShinhanCardNormalizedTransaction, Long> purchaseEventIdByTxn = new HashMap<>();
    for (ShinhanCardNormalizedTransaction t : normalized) {
      if (t.amount() <= 0) continue;
      if (isBoundaryDuplicate(t)) {
        boundarySkipped++;
        continue;
      }
      EasyCardInstrument instrument = instrumentCache.computeIfAbsent(t.maskedCard(),
          masked -> accountingService.registerInstrument(businessId, null, PROVIDER_CODE, ISSUER,
              instrumentToken(businessId, masked), masked, null));
      var result = accountingService.observePurchase(new EasyBusinessCardAccountingService.PurchaseCommand(
          businessId, null, instrument.getInstrumentId(), PROVIDER_CODE, rowExternalId(t),
          t.transactionDate().atStartOfDay(), t.confirmationDate(), t.amount(), "KRW", null));
      if (result.duplicate()) dupPurchases++; else newPurchases++;
      purchaseEventIdByTxn.put(t, result.event().getCardEventId());
    }

    for (Pair pair : matchedPairs) {
      Long originalEventId = purchaseEventIdByTxn.get(pair.positive());
      if (originalEventId == null) {
        // Positive half of this pair was itself a boundary duplicate — the cancellation has
        // nothing to attach to here; treat it like an unmatched negative rather than guess.
        unmatchedNegatives.add(pair.negative());
        continue;
      }
      var result = accountingService.observeCancellation(originalEventId, PROVIDER_CODE,
          rowExternalId(pair.negative()), pair.negative().transactionDate().atStartOfDay(),
          pair.negative().confirmationDate(), -pair.negative().amount());
      if (result.duplicate()) dupCancellations++; else newCancellations++;
    }

    return new ImportResult(normalized.size(), positiveRows, negativeRows, netAmount,
        matchedPairs.size(), unmatchedNegatives.size(), unmatchedNegatives, boundarySkipped,
        newPurchases, dupPurchases, newCancellations, dupCancellations);
  }

  /** True if an existing easy_transactions row already accounts for this exact (business, date, amount). */
  private boolean isBoundaryDuplicate(ShinhanCardNormalizedTransaction t) {
    List<EasyTransaction> sameDay = transactionRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            t.businessId(), t.transactionDate(), t.transactionDate());
    return sameDay.stream().anyMatch(existing -> existing.getAmount() == t.amount());
  }

  private String matchGroupKey(ShinhanCardNormalizedTransaction t, long absAmount) {
    return String.join("|", t.maskedCard(), t.merchantName(), t.productType(), String.valueOf(absAmount));
  }

  /** Stable per-source-row identity: same file re-parsed -> same id -> observePurchase/observeCancellation dedupe by fingerprint. */
  private String rowExternalId(ShinhanCardNormalizedTransaction t) {
    return String.join("|", PROVIDER_CODE, String.valueOf(t.businessId()), t.maskedCard(),
        t.transactionDate().toString(), t.confirmationDate().toString(), t.merchantName(),
        t.productType(), String.valueOf(t.amount()), String.valueOf(t.occurrenceIndex()));
  }

  private String instrumentToken(Long businessId, String maskedCard) {
    return String.join("|", PROVIDER_CODE, String.valueOf(businessId), maskedCard);
  }
}
