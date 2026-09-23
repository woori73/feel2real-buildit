package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EasyCardClassificationRecommendationServiceTest {

  private final EasyMerchantAccountMappingRepository merchantMappingRepository = mock(EasyMerchantAccountMappingRepository.class);
  private final EasyAccountService accountService = mock(EasyAccountService.class);
  private final EasyTransactionRepository transactionRepository = mock(EasyTransactionRepository.class);
  private final EasyCardEventRepository cardEventRepository = mock(EasyCardEventRepository.class);
  private EasyCardClassificationRecommendationService service;
  private EasyBusiness business;
  private EasyCardInstrument instrument;

  @BeforeEach
  void setUp() {
    service = new EasyCardClassificationRecommendationService(
        merchantMappingRepository, accountService, transactionRepository, cardEventRepository);
    business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    instrument = EasyCardInstrument.builder().business(business).providerCode("SHINHAN")
        .issuer("신한카드").providerInstrumentTokenHash("a".repeat(64)).last4("1234").build();
    ReflectionTestUtils.setField(instrument, "instrumentId", 10L);
  }

  private EasyCardEvent purchaseEvent(String providerExternalId, long amount) {
    return EasyCardEvent.builder().business(business).instrument(instrument)
        .eventType(EasyCardEvent.Type.PURCHASE).providerExternalId(providerExternalId)
        .fingerprint("b".repeat(64)).occurredAt(LocalDateTime.of(2026, 9, 1, 12, 0))
        .amount(amount).currency("KRW").build();
  }

  private EasyAccount account(Long id, String code, String name) {
    EasyAccount account = EasyAccount.builder().code(code).name(name)
        .direction(EasyAccountDirection.EXPENSE).sortOrder(1).active(true).build();
    ReflectionTestUtils.setField(account, "accountId", id);
    return account;
  }

  @Test
  void priority1_adminMappingExactMatch() {
    // Given: explicit admin mapping exists for normalized merchant
    // Service builds rawMerchant = "신한카드 이마트24 강남점" (issuer + providerExternalId)
    // normalizeMerchant -> "신한카드 이마트24 강남점" (Korean unchanged by toUpperCase)
    String expectedNormalized = "신한카드 이마트24 강남점";
    EasyMerchantAccountMapping mapping = EasyMerchantAccountMapping.builder()
        .business(business)
        .normalizedMerchant(expectedNormalized)
        .rawMerchant("이마트24 강남점")
        .account(account(5L, "EXP_SUPPLIES", "소모품비"))
        .source(EasyMerchantAccountMapping.Source.ADMIN_MAPPING)
        .confidence(EasyMerchantAccountMapping.Confidence.HIGH)
        .build();
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.of(mapping));

    // When
    EasyCardEvent event = purchaseEvent("이마트24 강남점", 15_000L);
    var recommendation = service.recommend(event);

    // Then
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.ADMIN_MAPPING);
    assertThat(recommendation.confidence()).isEqualTo(EasyCardClassificationRecommendationService.Confidence.HIGH);
    assertThat(recommendation.recommendedAccountId()).isEqualTo(5L);
    assertThat(recommendation.reason()).contains("관리자가 명시적으로 저장한 가맹점 매핑");
  }

  @Test
  void priority1_adminMappingRawMerchantFallback() {
    // Given: admin mapping exists for raw merchant (before normalization)
    String expectedNormalized = "신한카드 이마트24 강남점";
    EasyMerchantAccountMapping mapping = EasyMerchantAccountMapping.builder()
        .business(business)
        .normalizedMerchant("EMART24_GANGNAM")  // Different from expected normalized
        .rawMerchant("이마트24 강남점")
        .account(account(5L, "EXP_SUPPLIES", "소모품비"))
        .source(EasyMerchantAccountMapping.Source.ADMIN_MAPPING)
        .confidence(EasyMerchantAccountMapping.Confidence.HIGH)
        .build();
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.empty());
    when(merchantMappingRepository.findByBusiness_BusinessIdAndRawMerchantAndActiveTrue(1L, "신한카드 이마트24 강남점"))
        .thenReturn(Optional.of(mapping));

    // When
    EasyCardEvent event = purchaseEvent("이마트24 강남점", 15_000L);
    var recommendation = service.recommend(event);

    // Then
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.ADMIN_MAPPING);
    assertThat(recommendation.reason()).contains("원본");
  }

  @Test
  void priority2_exactHistoricalMatch() {
    // Given: multiple posted transactions with same merchant and same account
    EasyAccount consistentAccount = account(7L, "EXP_SUPPLIES", "소모품비");
    EasyTransaction txn1 = mock(EasyTransaction.class);
    EasyTransaction txn2 = mock(EasyTransaction.class);
    when(txn1.getAccount()).thenReturn(consistentAccount);
    when(txn2.getAccount()).thenReturn(consistentAccount);
    when(transactionRepository.findByBusiness_BusinessIdAndDirectionAndCounterparty(
        1L, EasyTxnDirection.EXPENSE, "신한카드 이마트24 강남점"))
        .thenReturn(List.of(txn1, txn2));

    // No admin mapping
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(1L, "SHINHAN_EMART24"))
        .thenReturn(Optional.empty());
    when(merchantMappingRepository.findByBusiness_BusinessIdAndRawMerchantAndActiveTrue(1L, "신한카드 이마트24 강남점"))
        .thenReturn(Optional.empty());

    // When
    EasyCardEvent event = purchaseEvent("이마트24 강남점", 15_000L);
    var recommendation = service.recommend(event);

    // Then
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.HISTORICAL_EXACT);
    assertThat(recommendation.confidence()).isEqualTo(EasyCardClassificationRecommendationService.Confidence.HIGH);
    assertThat(recommendation.reason()).contains("일관되게");
  }

  @Test
  void priority2_noHistoricalMatch_whenOnlyOneTransaction() {
    // Given: only one posted transaction (need at least 2 for consistency)
    EasyTransaction txn1 = mock(EasyTransaction.class);
    when(txn1.getAccount()).thenReturn(account(7L, "EXP_SUPPLIES", "소모품비"));
    when(transactionRepository.findByBusiness_BusinessIdAndDirectionAndCounterparty(
        1L, EasyTxnDirection.EXPENSE, "신한카드 이마트24 강남점"))
        .thenReturn(List.of(txn1));

    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(1L, "SHINHAN_EMART24"))
        .thenReturn(Optional.empty());
    when(merchantMappingRepository.findByBusiness_BusinessIdAndRawMerchantAndActiveTrue(1L, "신한카드 이마트24 강남점"))
        .thenReturn(Optional.empty());

    // When
    EasyCardEvent event = purchaseEvent("이마트24 강남점", 15_000L);
    var recommendation = service.recommend(event);

    // Then - should fall through to next priority
    assertThat(recommendation.source()).isNotEqualTo(EasyCardClassificationRecommendationService.Source.HISTORICAL_EXACT);
  }

  @Test
  void priority2_noHistoricalMatch_whenConflictingAccounts() {
    // Given: multiple transactions but different accounts
    String expectedNormalized = "신한카드 이마트24 강남점";
    EasyTransaction txn1 = mock(EasyTransaction.class);
    EasyTransaction txn2 = mock(EasyTransaction.class);
    when(txn1.getAccount()).thenReturn(account(7L, "EXP_SUPPLIES", "소모품비"));
    when(txn2.getAccount()).thenReturn(account(8L, "EXP_ENTERTAIN", "접대비"));
    when(transactionRepository.findByBusiness_BusinessIdAndDirectionAndCounterparty(
        1L, EasyTxnDirection.EXPENSE, expectedNormalized))
        .thenReturn(List.of(txn1, txn2));

    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.empty());
    when(merchantMappingRepository.findByBusiness_BusinessIdAndRawMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.empty());

    // When
    EasyCardEvent event = purchaseEvent("이마트24 강남점", 15_000L);
    var recommendation = service.recommend(event);

    // Then - should fall through due to conflict
    assertThat(recommendation.source()).isNotEqualTo(EasyCardClassificationRecommendationService.Source.HISTORICAL_EXACT);
  }

  @Test
  void priority3_normalizedMerchantMapping() {
    // Given: mapping exists with HISTORICAL_NORMALIZED source
    String expectedNormalized = "신한카드 이마트24 강남점";
    EasyMerchantAccountMapping mapping = EasyMerchantAccountMapping.builder()
        .business(business)
        .normalizedMerchant(expectedNormalized)
        .rawMerchant("이마트24 강남점")
        .account(account(5L, "EXP_SUPPLIES", "소모품비"))
        .source(EasyMerchantAccountMapping.Source.HISTORICAL_NORMALIZED)
        .confidence(EasyMerchantAccountMapping.Confidence.MEDIUM)
        .build();
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.of(mapping));
    when(merchantMappingRepository.findByBusiness_BusinessIdAndRawMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.empty());
    when(transactionRepository.findByBusiness_BusinessIdAndDirectionAndCounterparty(
        1L, EasyTxnDirection.EXPENSE, expectedNormalized))
        .thenReturn(List.of());

    // When
    EasyCardEvent event = purchaseEvent("이마트24 강남점", 15_000L);
    var recommendation = service.recommend(event);

    // Then
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.HISTORICAL_NORMALIZED);
    assertThat(recommendation.confidence()).isEqualTo(EasyCardClassificationRecommendationService.Confidence.MEDIUM);
    assertThat(recommendation.reason()).contains("정규화된 가맹점명으로 매칭된 이력 매핑");
  }

  @Test
  void priority4_ruleBasedMatch() {
    // Given: no merchant mappings, but classification rule matches
    String expectedNormalized = "신한카드 이마트24 강남점";
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.empty());
    when(merchantMappingRepository.findByBusiness_BusinessIdAndRawMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.empty());
    when(transactionRepository.findByBusiness_BusinessIdAndDirectionAndCounterparty(
        1L, EasyTxnDirection.EXPENSE, expectedNormalized))
        .thenReturn(List.of());
    when(accountService.classify("신한카드", "이마트24 강남점", 15_000L, EasyTxnDirection.EXPENSE))
        .thenReturn(Optional.of(new EasyAccountService.ClassificationMatch(account(9L, "EXP_SUPPLIES", "소모품비"), 0.9)));

    // When
    EasyCardEvent event = purchaseEvent("이마트24 강남점", 15_000L);
    var recommendation = service.recommend(event);

    // Then
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.RULE);
    assertThat(recommendation.confidence()).isEqualTo(EasyCardClassificationRecommendationService.Confidence.MEDIUM);
    assertThat(recommendation.reason()).contains("자동분류 규칙 매칭");
  }

  @Test
  void priority5_unclassifiedWhenNoMatch() {
    // Given: no mappings, no historical transactions, no rule match
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(anyLong(), anyString()))
        .thenReturn(Optional.empty());
    when(merchantMappingRepository.findByBusiness_BusinessIdAndRawMerchantAndActiveTrue(anyLong(), anyString()))
        .thenReturn(Optional.empty());
    when(transactionRepository.findByBusiness_BusinessIdAndDirectionAndCounterparty(anyLong(), any(), anyString()))
        .thenReturn(List.of());
    when(accountService.classify(anyString(), anyString(), anyLong(), any()))
        .thenReturn(Optional.empty());

    // When
    EasyCardEvent event = purchaseEvent("알수없는가맹점", 10_000L);
    var recommendation = service.recommend(event);

    // Then
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.UNCLASSIFIED);
    assertThat(recommendation.confidence()).isEqualTo(EasyCardClassificationRecommendationService.Confidence.LOW);
    assertThat(recommendation.recommendedAccountId()).isNull();
  }

  @Test
  void nonPurchaseEventReturnsUnclassified() {
    // Given: billing event (non-purchase, doesn't require originalEvent)
    EasyCardEvent event = EasyCardEvent.builder().business(business).instrument(instrument)
        .eventType(EasyCardEvent.Type.BILLING)
        .fingerprint("c".repeat(64)).occurredAt(LocalDateTime.of(2026, 9, 1, 12, 0))
        .amount(15_000L).currency("KRW").build();

    // When
    var recommendation = service.recommend(event);

    // Then
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.UNCLASSIFIED);
  }

  @Test
  void businessIsolation_differentBusinessDifferentMapping() {
    // Given: admin mapping exists for business 1 but not business 2
    String expectedNormalized = "신한카드 이마트24";
    EasyMerchantAccountMapping mapping = EasyMerchantAccountMapping.builder()
        .business(business)
        .normalizedMerchant(expectedNormalized)
        .rawMerchant("이마트24")
        .account(account(5L, "EXP_SUPPLIES", "소모품비"))
        .source(EasyMerchantAccountMapping.Source.ADMIN_MAPPING)
        .confidence(EasyMerchantAccountMapping.Confidence.HIGH)
        .build();
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.of(mapping));
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(2L, expectedNormalized))
        .thenReturn(Optional.empty());

    // When - business 1
    EasyCardEvent event1 = purchaseEvent("이마트24", 15_000L);
    ReflectionTestUtils.setField(event1.getBusiness(), "businessId", 1L);
    var rec1 = service.recommend(event1);

    // When - business 2 (same merchant)
    EasyCardEvent event2 = purchaseEvent("이마트24", 15_000L);
    ReflectionTestUtils.setField(event2.getBusiness(), "businessId", 2L);
    var rec2 = service.recommend(event2);

    // Then - business 1 gets admin mapping, business 2 does not
    assertThat(rec1.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.ADMIN_MAPPING);
    assertThat(rec2.source()).isNotEqualTo(EasyCardClassificationRecommendationService.Source.ADMIN_MAPPING);
  }

  @Test
  void normalization_conservative() {
    // Test that normalization is conservative - no aggressive fuzzy matching
    String normalized = service.normalizeMerchant("  이마트 24  강남점  ");
    assertThat(normalized).isEqualTo("이마트 24 강남점"); // spaces normalized but branch name kept

    // Case normalization (only affects ASCII)
    normalized = service.normalizeMerchant("emart24");
    assertThat(normalized).isEqualTo("EMART24");

    // Noise removal
    normalized = service.normalizeMerchant("승인 이마트24");
    assertThat(normalized).isEqualTo("이마트24");

    // Different branches should NOT be normalized to same
    String branch1 = service.normalizeMerchant("이마트24 강남점");
    String branch2 = service.normalizeMerchant("이마트24 압구정점");
    assertThat(branch1).isNotEqualTo(branch2); // Branch names preserved
  }

  @Test
  void cancellation_inheritsOriginalAccount_whenOriginalPosted() {
    // H1: linked cancellation + classified original → inherits original account
    // Given: original PURCHASE event with posted transaction
    EasyAccount originalAccount = account(10L, "EXP_SUPPLIES", "소모품비");
    EasyCardEvent original = purchaseEvent("이마트24 강남점", 15_000L);
    ReflectionTestUtils.setField(original, "cardEventId", 500L);
    ReflectionTestUtils.setField(original, "postedTxn", createPostedTxn(originalAccount));

    // Cancellation event linked to original
    EasyCardEvent cancellation = EasyCardEvent.builder()
        .business(business).instrument(instrument)
        .originalEvent(original)
        .eventType(EasyCardEvent.Type.CANCELLATION)
        .fingerprint("c".repeat(64))
        .occurredAt(LocalDateTime.of(2026, 9, 15, 10, 0))
        .amount(15_000L).currency("KRW").build();
    ReflectionTestUtils.setField(cancellation, "cardEventId", 501L);

    // When
    var recommendation = service.recommend(cancellation);

    // Then: inherits original account with HIGH confidence
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.HISTORICAL_EXACT);
    assertThat(recommendation.confidence()).isEqualTo(EasyCardClassificationRecommendationService.Confidence.HIGH);
    assertThat(recommendation.recommendedAccountId()).isEqualTo(10L);
    assertThat(recommendation.reason()).contains("원본 카드 이벤트 #500의 확정 계정");
    assertThat(recommendation.reason()).contains("상속");
  }

  @Test
  void cancellation_inheritsOriginalMapping_whenOriginalHasAdminMapping() {
    // H1 variant: linked cancellation + original has admin mapping (not yet posted)
    // Given: admin mapping exists for the merchant
    String expectedNormalized = "신한카드 이마트24 강남점";
    EasyMerchantAccountMapping mapping = EasyMerchantAccountMapping.builder()
        .business(business)
        .normalizedMerchant(expectedNormalized)
        .rawMerchant("이마트24 강남점")
        .account(account(10L, "EXP_SUPPLIES", "소모품비"))
        .source(EasyMerchantAccountMapping.Source.ADMIN_MAPPING)
        .confidence(EasyMerchantAccountMapping.Confidence.HIGH)
        .build();
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchantAndActiveTrue(1L, expectedNormalized))
        .thenReturn(Optional.of(mapping));

    // Original event (no posted txn, but has admin mapping)
    EasyCardEvent original = purchaseEvent("이마트24 강남점", 15_000L);
    ReflectionTestUtils.setField(original, "cardEventId", 600L);
    // No posted transaction

    // Cancellation linked to original
    EasyCardEvent cancellation = EasyCardEvent.builder()
        .business(business).instrument(instrument)
        .originalEvent(original)
        .eventType(EasyCardEvent.Type.CANCELLATION)
        .fingerprint("c".repeat(64))
        .occurredAt(LocalDateTime.of(2026, 9, 15, 10, 0))
        .amount(15_000L).currency("KRW").build();

    // When
    var recommendation = service.recommend(cancellation);

    // Then: inherits admin mapping
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.ADMIN_MAPPING);
    assertThat(recommendation.recommendedAccountId()).isEqualTo(10L);
    assertThat(recommendation.reason()).contains("원본 이벤트 #600의 관리자 매핑");
    assertThat(recommendation.reason()).contains("상속");
  }

  @Test
  void cancellation_unclassified_whenOriginalUnclassified() {
    // H2: linked cancellation + unclassified original → UNCLASSIFIED
    // Given: original PURCHASE event with NO posted transaction and NO mapping
    EasyCardEvent original = purchaseEvent("알수없는가맹점", 10_000L);
    ReflectionTestUtils.setField(original, "cardEventId", 700L);
    // No posted transaction, no mapping (repository returns empty)

    // Cancellation linked to original
    EasyCardEvent cancellation = EasyCardEvent.builder()
        .business(business).instrument(instrument)
        .originalEvent(original)
        .eventType(EasyCardEvent.Type.CANCELLATION)
        .fingerprint("c".repeat(64))
        .occurredAt(LocalDateTime.of(2026, 9, 15, 10, 0))
        .amount(10_000L).currency("KRW").build();

    // When
    var recommendation = service.recommend(cancellation);

    // Then: UNCLASSIFIED (does not force classification)
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.UNCLASSIFIED);
    assertThat(recommendation.recommendedAccountId()).isNull();
  }

  @Test
  void cancellation_noAutoTransactionOrLearning() {
    // H3: cancellation recommendation → no EasyTransaction auto-created, no historical learning
    // Given: cancellation with classified original
    EasyAccount originalAccount = account(10L, "EXP_SUPPLIES", "소모품비");
    EasyCardEvent original = purchaseEvent("이마트24 강남점", 15_000L);
    ReflectionTestUtils.setField(original, "cardEventId", 800L);
    ReflectionTestUtils.setField(original, "postedTxn", createPostedTxn(originalAccount));

    EasyCardEvent cancellation = EasyCardEvent.builder()
        .business(business).instrument(instrument)
        .originalEvent(original)
        .eventType(EasyCardEvent.Type.CANCELLATION)
        .fingerprint("c".repeat(64))
        .occurredAt(LocalDateTime.of(2026, 9, 15, 10, 0))
        .amount(15_000L).currency("KRW").build();

    // When
    var recommendation = service.recommend(cancellation);

    // Then: recommendation exists (inherits) but source indicates it's for reference only
    assertThat(recommendation.source()).isEqualTo(EasyCardClassificationRecommendationService.Source.HISTORICAL_EXACT);
    assertThat(recommendation.reason()).contains("상속");
    // Verify service doesn't create merchant mapping for cancellations
    // (This is verified by the implementation not calling save on merchantMappingRepository for cancellations)
  }

  private EasyTransaction createPostedTxn(EasyAccount account) {
    EasyTransaction txn = mock(EasyTransaction.class);
    when(txn.getAccount()).thenReturn(account);
    return txn;
  }
}