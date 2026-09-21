package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.time.LocalDateTime;
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
  private EasyBusinessCardAccountingService service;
  private EasyBusiness business;
  private EasyCardInstrument instrument;

  @BeforeEach
  void setUp() {
    service = new EasyBusinessCardAccountingService(businesses, connections, instruments, events,
        payables, billings, settlements, allocations);
    business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    instrument = EasyCardInstrument.builder().business(business).providerCode("TEST")
        .issuer("TEST").providerInstrumentTokenHash("a".repeat(64)).last4("1234").build();
    ReflectionTestUtils.setField(instrument, "instrumentId", 10L);
    when(businesses.findById(1L)).thenReturn(Optional.of(business));
    when(instruments.findById(10L)).thenReturn(Optional.of(instrument));
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

  private EasyBusinessCardAccountingService.PurchaseCommand command() {
    return new EasyBusinessCardAccountingService.PurchaseCommand(1L, null, 10L, "TEST",
        "purchase-1", LocalDateTime.of(2026, 9, 1, 12, 0), 50_000L, "KRW", "approval-1");
  }
  private EasyCardEvent purchaseEvent() {
    return EasyCardEvent.builder().business(business).instrument(instrument)
        .eventType(EasyCardEvent.Type.PURCHASE).fingerprint("b".repeat(64))
        .occurredAt(LocalDateTime.of(2026, 9, 1, 12, 0)).amount(50_000L).currency("KRW").build();
  }
}
