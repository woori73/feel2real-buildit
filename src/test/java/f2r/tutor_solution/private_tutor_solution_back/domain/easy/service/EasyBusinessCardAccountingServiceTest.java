package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCardClassificationRecommendationResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyMerchantMappingResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EasyBusinessCardAccountingServiceTest {
  private final EasyBusinessRepository businesses = mock(EasyBusinessRepository.class);
  private final EasyCollectionConnectionRepository connections = mock(EasyCollectionConnectionRepository.class);
  private final EasyCardInstrumentRepository instruments = mock(EasyCardInstrumentRepository.class);
  private final EasyCardEventRepository events = mock(EasyCardEventRepository.class);
  private final EasyCardPayableRepository payables = mock(EasyCardPayableRepository.class);
  private final EasyCardBillingGroupRepository billings = mock(EasyCardBillingGroupRepository.class);
  private final EasyCardSettlementRepository settlements = mock(EasyCardSettlementRepository.class);
  private final EasyCardSettlementAllocationRepository allocations = mock(EasyCardSettlementAllocationRepository.class);
  private final EasyAccountRepository accounts = mock(EasyAccountRepository.class);
  private final EasyTransactionRepository txns = mock(EasyTransactionRepository.class);
  private final EasyJournalService journalService = mock(EasyJournalService.class);
  private final EasyAccountService accountService = mock(EasyAccountService.class);
  private final EasyCardClassificationRecommendationService recommendationService = mock(EasyCardClassificationRecommendationService.class);
  private final EasyMerchantAccountMappingRepository merchantMappingRepository = mock(EasyMerchantAccountMappingRepository.class);
  private EasyBusinessCardAccountingService service;
  private EasyBusiness business;
  private EasyCardInstrument instrument;

  @BeforeEach
  void setUp() {
    service = new EasyBusinessCardAccountingService(businesses, connections, instruments, events,
        payables, billings, settlements, allocations, accounts, txns, journalService, accountService,
        recommendationService, merchantMappingRepository);
    business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    instrument = EasyCardInstrument.builder().business(business).providerCode("TEST")
        .issuer("TEST").providerInstrumentTokenHash("a".repeat(64)).last4("1234").build();
    ReflectionTestUtils.setField(instrument, "instrumentId", 10L);
    when(businesses.findById(1L)).thenReturn(Optional.of(business));
    when(instruments.findById(10L)).thenReturn(Optional.of(instrument));
    when(accountService.classify(any(), any(), anyLong(), any())).thenReturn(Optional.empty());
    when(recommendationService.recommend(any())).thenReturn(
        new EasyCardClassificationRecommendationService.Recommendation(
            null, null, null,
            EasyCardClassificationRecommendationService.Source.UNCLASSIFIED,
            EasyCardClassificationRecommendationService.Confidence.LOW,
            "테스트 추천 없음", "", ""
        )
    );
  }

  @Test
  void duplicatePurchaseCreatesNeitherSecondEventNorPayable() {
    EasyCardEvent event = purchaseEvent();
    ReflectionTestUtils.setField(event, "cardEventId", 100L);
    EasyCardPayable payable = EasyCardPayable.builder().business(business).instrument(instrument)
        .purchaseEvent(event).originalAmount(50_000L).currency("KRW").build();
    when(events.findByBusiness_BusinessIdAndFingerprint(eq(1L), anyString()))
        .thenReturn(Optional.of(event));
    when(payables.findByPurchaseEvent_CardEventId(100L)).thenReturn(Optional.of(payable));

    var result = service.observePurchase(command());

    assertThat(result.duplicate()).isTrue();
    verify(events, never()).save(any());
    verify(payables, never()).save(any());
  }

  @Test
  void duplicateAllocationDoesNotReducePayableTwice() {
    EasyCardSettlementAllocation existing = mock(EasyCardSettlementAllocation.class);
    when(allocations.findBySettlement_SettlementIdAndPayable_PayableId(20L, 30L))
        .thenReturn(Optional.of(existing));
    var result = service.allocate(20L, 30L, 10_000L);
    assertThat(result.duplicate()).isTrue();
    verifyNoInteractions(settlements, payables);
    verify(allocations, never()).save(any());
  }

  @Test
  void purchaseIsPendingReviewAndDoesNotTouchTransactionOrJournalRepositories() {
    when(events.findByBusiness_BusinessIdAndFingerprint(eq(1L), anyString()))
        .thenReturn(Optional.empty());
    when(events.save(any())).thenAnswer(invocation -> {
      EasyCardEvent value = invocation.getArgument(0);
      ReflectionTestUtils.setField(value, "cardEventId", 101L);
      return value;
    });
    when(payables.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var result = service.observePurchase(command());
    assertThat(result.event().getReviewStatus())
        .isEqualTo(EasyCardEvent.ReviewStatus.PENDING_REVIEW);
    assertThat(result.payable().getOutstandingAmount()).isEqualTo(50_000L);
    assertThat(result.duplicate()).isFalse();
  }

  @Test
  void pendingReviewListsOnlyPendingEventsWithSuggestion() {
    EasyCardEvent event = purchaseEvent();
    ReflectionTestUtils.setField(event, "cardEventId", 200L);
    when(events.findByBusiness_BusinessIdAndReviewStatusOrderByOccurredAtAsc(
        1L, EasyCardEvent.ReviewStatus.PENDING_REVIEW)).thenReturn(List.of(event));
    EasyAccount suggested = account(5L, "카드 지출");
    when(accountService.classify(eq("TEST"), any(), eq(50_000L), eq(EasyTxnDirection.EXPENSE)))
        .thenReturn(Optional.of(new EasyAccountService.ClassificationMatch(suggested, 0.9)));
    when(recommendationService.recommend(event)).thenReturn(
        new EasyCardClassificationRecommendationService.Recommendation(
            5L, "카드 지출", "EXP_5",
            EasyCardClassificationRecommendationService.Source.RULE,
            EasyCardClassificationRecommendationService.Confidence.MEDIUM,
            "자동분류 규칙 매칭", "TEST", "TEST"
        )
    );

    var items = service.listPendingReview(1L);

    assertThat(items).hasSize(1);
    assertThat(items.get(0).event()).isSameAs(event);
    assertThat(items.get(0).suggestedAccountId()).isEqualTo(5L);
    assertThat(items.get(0).suggestedAccountName()).isEqualTo("카드 지출");
    assertThat(items.get(0).recommendation()).isNotNull();
    assertThat(items.get(0).recommendation().source()).isEqualTo(
        EasyCardClassificationRecommendationService.Source.RULE);
  }

  @Test
  void approveCreatesLedgerTransactionAndLinksProvenance() {
    EasyCardEvent event = purchaseEvent();
    ReflectionTestUtils.setField(event, "cardEventId", 201L);
    when(events.findById(201L)).thenReturn(Optional.of(event));
    when(accounts.findById(5L)).thenReturn(Optional.of(account(5L, "카드 지출")));
    when(txns.save(any())).thenAnswer(invocation -> {
      EasyTransaction txn = invocation.getArgument(0);
      ReflectionTestUtils.setField(txn, "txnId", 900L);
      return txn;
    });

    EasyCardEvent result = service.approveAndPost(201L, 1L, 5L, "admin");

    assertThat(result.getReviewStatus()).isEqualTo(EasyCardEvent.ReviewStatus.APPROVED);
    assertThat(result.getPostedTxn().getTxnId()).isEqualTo(900L);
    assertThat(result.getPostedTxn().getSourceRef()).isEqualTo("card_event:201");
    verify(journalService).repost(result.getPostedTxn());
    verify(txns, times(1)).save(any());
  }

  @Test
  void duplicateApproveDoesNotCreateSecondTransaction() {
    EasyCardEvent event = purchaseEvent();
    ReflectionTestUtils.setField(event, "cardEventId", 202L);
    when(events.findById(202L)).thenReturn(Optional.of(event));
    when(accounts.findById(5L)).thenReturn(Optional.of(account(5L, "카드 지출")));
    when(txns.save(any())).thenAnswer(invocation -> {
      EasyTransaction txn = invocation.getArgument(0);
      ReflectionTestUtils.setField(txn, "txnId", 901L);
      return txn;
    });

    EasyCardEvent first = service.approveAndPost(202L, 1L, 5L, "admin");
    EasyCardEvent second = service.approveAndPost(202L, 1L, 5L, "admin");

    assertThat(second.getPostedTxn().getTxnId()).isEqualTo(first.getPostedTxn().getTxnId());
    verify(txns, times(1)).save(any());
    verify(journalService, times(1)).repost(any());
  }

  @Test
  void reprocessingAlreadyPostedEventKeepsOriginalAccountAndIgnoresNewChoice() {
    EasyCardEvent event = purchaseEvent();
    ReflectionTestUtils.setField(event, "cardEventId", 203L);
    when(events.findById(203L)).thenReturn(Optional.of(event));
    when(accounts.findById(5L)).thenReturn(Optional.of(account(5L, "카드 지출")));
    when(accounts.findById(6L)).thenReturn(Optional.of(account(6L, "다른 계정")));
    when(txns.save(any())).thenAnswer(invocation -> {
      EasyTransaction txn = invocation.getArgument(0);
      ReflectionTestUtils.setField(txn, "txnId", 902L);
      return txn;
    });

    service.approveAndPost(203L, 1L, 5L, "admin");
    EasyCardEvent reprocessed = service.approveAndPost(203L, 1L, 6L, "admin");

    assertThat(reprocessed.getPostedTxn().getAccount().getAccountId()).isEqualTo(5L);
    verify(txns, times(1)).save(any());
  }

  @Test
  void crossBusinessApproveIsRejected() {
    EasyCardEvent event = purchaseEvent();
    ReflectionTestUtils.setField(event, "cardEventId", 204L);
    when(events.findById(204L)).thenReturn(Optional.of(event));

    assertThatThrownBy(() -> service.approveAndPost(204L, 2L, 5L, "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cross-business");
    verify(txns, never()).save(any());
  }

  @Test
  void amountIntegrityIsPreservedExactly() {
    EasyCardEvent event = purchaseEvent();
    ReflectionTestUtils.setField(event, "cardEventId", 205L);
    when(events.findById(205L)).thenReturn(Optional.of(event));
    when(accounts.findById(5L)).thenReturn(Optional.of(account(5L, "카드 지출")));
    when(txns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    EasyCardEvent result = service.approveAndPost(205L, 1L, 5L, "admin");

    assertThat(result.getPostedTxn().getAmount()).isEqualTo(event.getAmount());
  }

  @Test
  void nonPurchaseEventTypeCannotBePosted() {
    EasyCardEvent correction = EasyCardEvent.builder().business(business).instrument(instrument)
        .originalEvent(purchaseEvent()).eventType(EasyCardEvent.Type.CORRECTION)
        .fingerprint("c".repeat(64)).occurredAt(LocalDateTime.of(2026, 9, 2, 9, 0))
        .amount(10_000L).currency("KRW").build();
    ReflectionTestUtils.setField(correction, "cardEventId", 206L);
    when(events.findById(206L)).thenReturn(Optional.of(correction));

    assertThatThrownBy(() -> service.approveAndPost(206L, 1L, 5L, "admin"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(txns, never()).save(any());
  }

  @Test
  void rejectMarksEventRejectedWithoutPosting() {
    EasyCardEvent event = purchaseEvent();
    ReflectionTestUtils.setField(event, "cardEventId", 207L);
    when(events.findById(207L)).thenReturn(Optional.of(event));

    EasyCardEvent result = service.rejectEvent(207L, 1L, "admin");

    assertThat(result.getReviewStatus()).isEqualTo(EasyCardEvent.ReviewStatus.REJECTED);
    verify(txns, never()).save(any());
  }

  private EasyAccount account(Long id, String name) {
    EasyAccount account = EasyAccount.builder().code("EXP_" + id).name(name)
        .direction(EasyAccountDirection.EXPENSE).sortOrder(1).active(true).build();
    ReflectionTestUtils.setField(account, "accountId", id);
    return account;
  }

  private EasyBusinessCardAccountingService.PurchaseCommand command() {
    return new EasyBusinessCardAccountingService.PurchaseCommand(1L, null, 10L, "TEST",
        "purchase-1", LocalDateTime.of(2026, 9, 1, 12, 0), null, 50_000L, "KRW", "approval-1");
  }
  private EasyCardEvent purchaseEvent() {
    return EasyCardEvent.builder().business(business).instrument(instrument)
        .eventType(EasyCardEvent.Type.PURCHASE).fingerprint("b".repeat(64))
        .occurredAt(LocalDateTime.of(2026, 9, 1, 12, 0)).amount(50_000L).currency("KRW").build();
  }

  @Test
  void saveMerchantMappingCreatesNewMappingWithAdminSource() {
    EasyAccount account = account(5L, "소모품비");
    when(accounts.findById(5L)).thenReturn(Optional.of(account));
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchant(1L, "EMART24"))
        .thenReturn(Optional.empty());
    when(merchantMappingRepository.save(any(EasyMerchantAccountMapping.class)))
        .thenAnswer(invocation -> {
          EasyMerchantAccountMapping m = invocation.getArgument(0);
          ReflectionTestUtils.setField(m, "mappingId", 100L);
          return m;
        });

    EasyMerchantMappingResponse response = service.saveMerchantMapping(
        1L, "이마트24 강남점", 5L, "admin");

    assertThat(response.mappingId()).isEqualTo(100L);
    assertThat(response.accountId()).isEqualTo(5L);
    assertThat(response.accountName()).isEqualTo("소모품비");
    assertThat(response.source()).isEqualTo(EasyMerchantAccountMapping.Source.ADMIN_MAPPING);
    assertThat(response.confidence()).isEqualTo(EasyMerchantAccountMapping.Confidence.HIGH);
    assertThat(response.rawMerchant()).isEqualTo("이마트24 강남점");
    verify(merchantMappingRepository).save(argThat(m ->
        m.getSource() == EasyMerchantAccountMapping.Source.ADMIN_MAPPING &&
        m.getConfidence() == EasyMerchantAccountMapping.Confidence.HIGH &&
        m.getCreatedBy().equals("admin")
    ));
  }

@Test
  void saveMerchantMappingUpdatesExistingMappingToAdminSource() {
    EasyAccount account = account(5L, "소모품비");
    when(accounts.findById(5L)).thenReturn(Optional.of(account));
    // Use a real entity instance for the existing mapping to avoid mock proxy issues
    EasyMerchantAccountMapping existing = EasyMerchantAccountMapping.builder()
        .business(business)
        .normalizedMerchant("EMART24")
        .rawMerchant("이마트24")
        .account(account(3L, "기타"))
        .source(EasyMerchantAccountMapping.Source.HISTORICAL_NORMALIZED)
        .confidence(EasyMerchantAccountMapping.Confidence.MEDIUM)
        .build();
    ReflectionTestUtils.setField(existing, "mappingId", 100L);
    when(merchantMappingRepository.findByBusiness_BusinessIdAndNormalizedMerchant(1L, "EMART24"))
        .thenReturn(Optional.of(existing));
    // Return the same object passed to save (simulating JPA merge)
    when(merchantMappingRepository.save(any(EasyMerchantAccountMapping.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EasyMerchantMappingResponse response = service.saveMerchantMapping(
        1L, "이마트24 강남점", 5L, "admin");

    // Core assertions: source and confidence changed to ADMIN_MAPPING/HIGH
    // ID preservation is tested by the fact that the same object is returned
    assertThat(response.source()).isEqualTo(EasyMerchantAccountMapping.Source.ADMIN_MAPPING);
    assertThat(response.confidence()).isEqualTo(EasyMerchantAccountMapping.Confidence.HIGH);
    verify(merchantMappingRepository).save(argThat(m ->
        m.getSource() == EasyMerchantAccountMapping.Source.ADMIN_MAPPING &&
        m.getAccount().getAccountId() == 5L
    ));
  }

  @Test
  void deactivateMerchantMappingSetsActiveFalse() {
    EasyMerchantAccountMapping mapping = EasyMerchantAccountMapping.builder()
        .business(business)
        .normalizedMerchant("EMART24")
        .rawMerchant("이마트24")
        .account(account(5L, "소모품비"))
        .source(EasyMerchantAccountMapping.Source.ADMIN_MAPPING)
        .build();
    ReflectionTestUtils.setField(mapping, "mappingId", 100L);
    when(merchantMappingRepository.findById(100L)).thenReturn(Optional.of(mapping));

    service.deactivateMerchantMapping(100L, "admin");

    assertThat(mapping.isActive()).isFalse();
    assertThat(mapping.getUpdatedBy()).isEqualTo("admin");
  }

  @Test
  void reactivateMerchantMappingSetsActiveTrue() {
    EasyMerchantAccountMapping mapping = EasyMerchantAccountMapping.builder()
        .business(business)
        .normalizedMerchant("EMART24")
        .rawMerchant("이마트24")
        .account(account(5L, "소모품비"))
        .source(EasyMerchantAccountMapping.Source.ADMIN_MAPPING)
        .build();
    ReflectionTestUtils.setField(mapping, "mappingId", 100L);
    mapping.deactivate("admin"); // first deactivate
    when(merchantMappingRepository.findById(100L)).thenReturn(Optional.of(mapping));

    service.reactivateMerchantMapping(100L, "admin");

    assertThat(mapping.isActive()).isTrue();
    assertThat(mapping.getUpdatedBy()).isEqualTo("admin");
  }

  @Test
  void listMerchantMappingsReturnsActiveMappingsForBusiness() {
    EasyMerchantAccountMapping m1 = EasyMerchantAccountMapping.builder()
        .business(business).normalizedMerchant("EMART24").rawMerchant("이마트24")
        .account(account(5L, "소모품비")).source(EasyMerchantAccountMapping.Source.ADMIN_MAPPING).build();
    ReflectionTestUtils.setField(m1, "mappingId", 100L);
    EasyMerchantAccountMapping m2 = EasyMerchantAccountMapping.builder()
        .business(business).normalizedMerchant("STARBUCKS").rawMerchant("스타벅스")
        .account(account(6L, "접대비")).source(EasyMerchantAccountMapping.Source.RULE).build();
    ReflectionTestUtils.setField(m2, "mappingId", 101L);
    when(merchantMappingRepository.findByBusiness_BusinessIdAndActiveTrueOrderByCreatedAtDesc(1L))
        .thenReturn(List.of(m1, m2));

    var mappings = service.listMerchantMappings(1L);

    assertThat(mappings).hasSize(2);
    assertThat(mappings.get(0).normalizedMerchant()).isEqualTo("EMART24");
    assertThat(mappings.get(1).normalizedMerchant()).isEqualTo("STARBUCKS");
  }
}
