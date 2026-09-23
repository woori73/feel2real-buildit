package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account classification recommendation for EasyCardEvent (Phase 5A.2).
 *
 * Priority order:
 * 1. EXPLICIT ADMIN MAPPING (EasyMerchantAccountMapping with source=ADMIN_MAPPING)
 * 2. EXACT HISTORICAL CONFIRMED MATCH (same business, same merchant, consistent account in posted transactions)
 * 3. NORMALIZED MERCHANT MATCH (normalized merchant → existing mapping)
 * 4. RULE-BASED CATEGORY MATCH (EasyClassificationRule via EasyAccountService.classify)
 * 5. UNCLASSIFIED
 *
 * No auto-posting. Recommendation only. Admin must approve via existing approveAndPost.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyCardClassificationRecommendationService {

  private final EasyMerchantAccountMappingRepository merchantMappingRepository;
  private final EasyAccountService accountService;
  private final EasyTransactionRepository transactionRepository;
  private final EasyCardEventRepository cardEventRepository;

  /**
   * Recommendation result with full transparency.
   */
  public record Recommendation(
      Long recommendedAccountId,
      String recommendedAccountName,
      String recommendedAccountCode,
      Source source,
      Confidence confidence,
      String reason,
      String normalizedMerchant,
      String rawMerchant
  ) {
    public static Recommendation unclassified(String normalizedMerchant, String rawMerchant) {
      return new Recommendation(null, null, null, Source.UNCLASSIFIED, Confidence.LOW,
          "추천 가능한 매핑/규칙/이력이 없습니다.", normalizedMerchant, rawMerchant);
    }
  }

  public enum Source {
    ADMIN_MAPPING,          // Priority 1: Explicit admin mapping
    HISTORICAL_EXACT,       // Priority 2: Exact historical confirmed match
    HISTORICAL_NORMALIZED,  // Priority 3: Normalized merchant match
    RULE,                   // Priority 4: Rule-based category match
    UNCLASSIFIED            // No match found
  }

  public enum Confidence {
    HIGH, MEDIUM, LOW
  }

/**
    * Get recommendation for a card event (PENDING_REVIEW).
    * Uses issuer + providerExternalId as the raw merchant identifier.
    *
    * Cancellation/refund handling:
    * - If linked to original event with classification: inherit original account
    * - If linked to original event without classification: UNCLASSIFIED
    * - Never create/learn merchant mapping for cancellations
    */
  public Recommendation recommend(EasyCardEvent event) {
    // Handle cancellation/refund events - inherit from original if classified
    if (event.getEventType() == EasyCardEvent.Type.CANCELLATION ||
        event.getEventType() == EasyCardEvent.Type.CORRECTION) {
      return handleCancellationOrCorrection(event);
    }

    if (event.getEventType() != EasyCardEvent.Type.PURCHASE) {
      return Recommendation.unclassified("", "");
    }

    String rawMerchant = buildRawMerchant(event);
    String normalizedMerchant = normalizeMerchant(rawMerchant);

    // Priority 1: Explicit admin mapping (normalized merchant) - only ADMIN_MAPPING source
    Optional<EasyMerchantAccountMapping> adminMapping = merchantMappingRepository
        .findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(
            event.getBusiness().getBusinessId(), normalizedMerchant);
    if (adminMapping.isPresent() && adminMapping.get().getSource() == EasyMerchantAccountMapping.Source.ADMIN_MAPPING) {
      EasyMerchantAccountMapping mapping = adminMapping.get();
      return new Recommendation(
          mapping.getAccount().getAccountId(),
          mapping.getAccount().getName(),
          mapping.getAccount().getCode(),
          Source.ADMIN_MAPPING,
          confidenceFromEntity(mapping.getConfidence()),
          "관리자가 명시적으로 저장한 가맹점 매핑: " + mapping.getRawMerchant(),
          normalizedMerchant,
          rawMerchant
      );
    }

    // Priority 1 fallback: Admin mapping by raw merchant (before normalization) - only ADMIN_MAPPING source
    Optional<EasyMerchantAccountMapping> adminMappingRaw = merchantMappingRepository
        .findByBusiness_BusinessIdAndRawMerchantAndActiveTrue(
            event.getBusiness().getBusinessId(), rawMerchant);
    if (adminMappingRaw.isPresent() && adminMappingRaw.get().getSource() == EasyMerchantAccountMapping.Source.ADMIN_MAPPING) {
      EasyMerchantAccountMapping mapping = adminMappingRaw.get();
      return new Recommendation(
          mapping.getAccount().getAccountId(),
          mapping.getAccount().getName(),
          mapping.getAccount().getCode(),
          Source.ADMIN_MAPPING,
          confidenceFromEntity(mapping.getConfidence()),
          "관리자가 명시적으로 저장한 가맹점 매핑(원본): " + mapping.getRawMerchant(),
          normalizedMerchant,
          rawMerchant
      );
    }

    // Priority 2: Exact historical confirmed match
    // Look at posted transactions for this business + merchant
    Optional<Recommendation> historicalExact = findExactHistoricalMatch(
        event.getBusiness().getBusinessId(), rawMerchant, normalizedMerchant);
    if (historicalExact.isPresent()) {
      return historicalExact.get();
    }

    // Priority 3: Normalized merchant match (mapping exists but not admin explicit)
    Optional<EasyMerchantAccountMapping> normalizedMapping = merchantMappingRepository
        .findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(
            event.getBusiness().getBusinessId(), normalizedMerchant);
    if (normalizedMapping.isPresent()) {
      EasyMerchantAccountMapping mapping = normalizedMapping.get();
      // Only use if source is HISTORICAL_NORMALIZED or HISTORICAL_EXACT
      if (mapping.getSource() == EasyMerchantAccountMapping.Source.HISTORICAL_NORMALIZED ||
          mapping.getSource() == EasyMerchantAccountMapping.Source.HISTORICAL_EXACT) {
        return new Recommendation(
            mapping.getAccount().getAccountId(),
            mapping.getAccount().getName(),
            mapping.getAccount().getCode(),
            Source.HISTORICAL_NORMALIZED,
            confidenceFromEntity(mapping.getConfidence()),
            "정규화된 가맹점명으로 매칭된 이력 매핑: " + mapping.getRawMerchant(),
            normalizedMerchant,
            rawMerchant
        );
      }
    }

    // Priority 4: Rule-based category match (existing EasyClassificationRule)
    Optional<EasyAccountService.ClassificationMatch> ruleMatch = accountService.classify(
        event.getInstrument().getIssuer(),
        event.getProviderExternalId(),
        event.getAmount(),
        EasyTxnDirection.EXPENSE
    );
    if (ruleMatch.isPresent()) {
      EasyAccountService.ClassificationMatch match = ruleMatch.get();
      return new Recommendation(
          match.account().getAccountId(),
          match.account().getName(),
          match.account().getCode(),
          Source.RULE,
          confidenceFromScore(match.confidence()),
          "자동분류 규칙 매칭 (issuer: " + event.getInstrument().getIssuer() + ")",
          normalizedMerchant,
          rawMerchant
      );
    }

    // Priority 5: Unclassified
    return Recommendation.unclassified(normalizedMerchant, rawMerchant);
  }

  /**
   * Priority 2: Exact historical confirmed match.
   * Looks at posted EasyTransaction for this business where:
   * - counterparty matches the raw merchant (issuer + providerExternalId)
   * - direction is EXPENSE
   * - account is consistent across multiple transactions
   */
  private Optional<Recommendation> findExactHistoricalMatch(Long businessId, String rawMerchant,
      String normalizedMerchant) {
    // Find posted transactions for this business with matching counterparty
    // Counterparty in transaction is set to instrument issuer in approveAndPost
    // We need to check if there are multiple posted transactions with same account
    List<EasyTransaction> postedTransactions = transactionRepository
        .findByBusiness_BusinessIdAndDirectionAndCounterparty(
            businessId, EasyTxnDirection.EXPENSE, rawMerchant);

    if (postedTransactions.size() < 2) {
      // Need at least 2 transactions to establish consistency
      return Optional.empty();
    }

    // Check if all transactions use the same account
    Long firstAccountId = postedTransactions.get(0).getAccount() != null
        ? postedTransactions.get(0).getAccount().getAccountId() : null;
    if (firstAccountId == null) {
      return Optional.empty();
    }

    boolean allSameAccount = postedTransactions.stream()
        .allMatch(txn -> txn.getAccount() != null
            && txn.getAccount().getAccountId().equals(firstAccountId));

    if (!allSameAccount) {
      // Conflicting accounts - don't recommend HIGH confidence
      return Optional.empty();
    }

    EasyAccount account = postedTransactions.get(0).getAccount();
    return Optional.of(new Recommendation(
        account.getAccountId(),
        account.getName(),
        account.getCode(),
        Source.HISTORICAL_EXACT,
        Confidence.HIGH,
        "동일 사업자·동일 가맹점(" + rawMerchant + ")의 과거 확정 거래 " + postedTransactions.size()
            + "건이 일관되게 '" + account.getName() + "'로 처리됨",
        normalizedMerchant,
        rawMerchant
    ));
  }

  /**
   * Merchant normalization - conservative approach.
   * Only: trim, whitespace normalization, case normalization.
   * NO aggressive fuzzy matching, NO branch name removal.
   */
  public String normalizeMerchant(String rawMerchant) {
    if (rawMerchant == null || rawMerchant.isBlank()) {
      return "";
    }
    // Trim
    String normalized = rawMerchant.trim();
    // Whitespace normalization (multiple spaces -> single space)
    normalized = normalized.replaceAll("\\s+", " ");
    // Case normalization (uppercase for consistency)
    normalized = normalized.toUpperCase();
    // Remove obvious card statement noise (card issuer prefixes, etc.)
    // Conservative: only remove known prefix patterns
    normalized = removeKnownNoise(normalized);
    return normalized;
  }

  private String removeKnownNoise(String merchant) {
    // Remove common card statement prefixes (conservative list)
    // These are known patterns from Shinhan/CREFIA data
    String[] noisePatterns = {
        "^\\d{4}\\s*",           // Leading digits (date-like)
        "^\\*\\s*",              // Leading asterisk
        "^승인\\s*",             // "승인" prefix
        "^취소\\s*",             // "취소" prefix
        "^정정\\s*"              // "정정" prefix
    };
    String result = merchant;
    for (String pattern : noisePatterns) {
      result = result.replaceFirst(pattern, "");
    }
    return result.trim();
  }

  private String buildRawMerchant(EasyCardEvent event) {
    String issuer = event.getInstrument().getIssuer();
    String externalId = event.getProviderExternalId();
    if (externalId != null && !externalId.isBlank()) {
      return issuer + " " + externalId;
    }
    return issuer;
  }

  private Confidence confidenceFromScore(double score) {
    if (score >= 0.95) return Confidence.HIGH;
    if (score >= 0.8) return Confidence.MEDIUM;
    return Confidence.LOW;
  }

  private Confidence confidenceFromEntity(EasyMerchantAccountMapping.Confidence entityConfidence) {
    return switch (entityConfidence) {
      case HIGH -> Confidence.HIGH;
      case MEDIUM -> Confidence.MEDIUM;
      case LOW -> Confidence.LOW;
    };
  }

  /**
   * Handle cancellation/correction events by inheriting from original event.
   * Requirements:
   * - If original has posted transaction: inherit that account (HIGH confidence)
   * - If original has merchant mapping: inherit that mapping (MEDIUM confidence)
   * - If original unclassified: return UNCLASSIFIED
   * - Never create/learn merchant mapping for cancellations
   */
  private Recommendation handleCancellationOrCorrection(EasyCardEvent event) {
    EasyCardEvent original = event.getOriginalEvent();
    if (original == null) {
      return Recommendation.unclassified("", "취소/정정 이벤트이나 원본 이벤트 연결 없음");
    }

    // Check if original has a posted transaction (admin-approved classification)
    if (original.getPostedTxn() != null && original.getPostedTxn().getAccount() != null) {
      EasyAccount account = original.getPostedTxn().getAccount();
      String rawMerchant = buildRawMerchant(event);
      String normalizedMerchant = normalizeMerchant(rawMerchant);
      return new Recommendation(
          account.getAccountId(),
          account.getName(),
          account.getCode(),
          Source.HISTORICAL_EXACT,
          Confidence.HIGH,
          "원본 카드 이벤트 #" + original.getCardEventId() + "의 확정 계정(" + account.getName() + ")을 상속 (취소/정정 이벤트)",
          normalizedMerchant,
          rawMerchant
      );
    }

    // Check if original has a merchant mapping (admin-saved or historical)
    String rawMerchant = buildRawMerchant(original);
    String normalizedMerchant = normalizeMerchant(rawMerchant);
    Optional<EasyMerchantAccountMapping> adminMapping = merchantMappingRepository
        .findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(
            event.getBusiness().getBusinessId(), normalizedMerchant);
    if (adminMapping.isPresent() && adminMapping.get().getSource() == EasyMerchantAccountMapping.Source.ADMIN_MAPPING) {
      EasyMerchantAccountMapping mapping = adminMapping.get();
      return new Recommendation(
          mapping.getAccount().getAccountId(),
          mapping.getAccount().getName(),
          mapping.getAccount().getCode(),
          Source.ADMIN_MAPPING,
          confidenceFromEntity(mapping.getConfidence()),
          "원본 이벤트 #" + original.getCardEventId() + "의 관리자 매핑(" + mapping.getAccount().getName() + ")을 상속 (취소/정정 이벤트)",
          normalizedMerchant,
          rawMerchant
      );
    }

    // Original unclassified - cancellation remains unclassified
    // Do NOT create/learn merchant mapping for cancellations
    return Recommendation.unclassified(normalizedMerchant, rawMerchant);
  }
}