package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EasyBusinessCardAccountingDomainTest {
  private static final String HASH_A = "a".repeat(64);
  private static final String HASH_B = "b".repeat(64);

  @Test
  void purchaseCreatesExpenseCandidateAndEqualPayable() {
    EasyCardPayable payable = payable(50_000L);
    assertThat(payable.recognizedExpenseCandidate()).isEqualTo(50_000L);
    assertThat(payable.getOutstandingAmount()).isEqualTo(50_000L);
    assertThat(payable.getStatus()).isEqualTo(EasyCardPayable.Status.OPEN);
  }

  @Test
  void bankSettlementReducesPayableWithoutAdditionalExpense() {
    EasyCardPayable payable = payable(50_000L);
    payable.settle(50_000L);
    assertThat(payable.getOutstandingAmount()).isZero();
    assertThat(payable.recognizedExpenseCandidate()).isEqualTo(50_000L);
    assertThat(payable.getStatus()).isEqualTo(EasyCardPayable.Status.SETTLED);
  }

  @Test
  void doubleExpenseInvariantRemainsFiftyThousand() {
    EasyCardPayable payable = payable(50_000L);
    long expenseAfterPurchase = payable.recognizedExpenseCandidate();
    payable.settle(50_000L);
    long expenseAfterBankPayment = payable.recognizedExpenseCandidate();
    assertThat(expenseAfterPurchase).isEqualTo(50_000L);
    assertThat(expenseAfterBankPayment).isEqualTo(50_000L);
  }

  @Test
  void partialSettlementLeavesOutstandingAndThenSettles() {
    EasyCardPayable payable = payable(100_000L);
    payable.settle(60_000L);
    assertThat(payable.getOutstandingAmount()).isEqualTo(40_000L);
    assertThat(payable.getStatus()).isEqualTo(EasyCardPayable.Status.PARTIALLY_SETTLED);
    payable.settle(40_000L);
    assertThat(payable.getOutstandingAmount()).isZero();
    assertThat(payable.getStatus()).isEqualTo(EasyCardPayable.Status.SETTLED);
  }

  @Test
  void fullAndPartialCancellationAdjustExpenseAndPayable() {
    EasyCardPayable full = payable(100_000L);
    full.cancel(100_000L);
    assertThat(full.recognizedExpenseCandidate()).isZero();
    assertThat(full.getOutstandingAmount()).isZero();
    assertThat(full.getStatus()).isEqualTo(EasyCardPayable.Status.CANCELLED);

    EasyCardPayable partial = payable(100_000L);
    partial.cancel(30_000L);
    assertThat(partial.recognizedExpenseCandidate()).isEqualTo(70_000L);
    assertThat(partial.getOutstandingAmount()).isEqualTo(70_000L);
    assertThat(partial.getStatus()).isEqualTo(EasyCardPayable.Status.OPEN);
  }

  @Test
  void cancellationCannotMakeOutstandingNegative() {
    EasyCardPayable payable = payable(100_000L);
    payable.settle(60_000L);
    assertThatThrownBy(() -> payable.cancel(50_000L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outstanding");
  }

  @Test
  void correctionPreservesOriginalProvenanceAndReviewBoundary() {
    Fixture f = fixture();
    EasyCardEvent original = purchaseEvent(f, 100_000L, HASH_A);
    EasyCardEvent correction = EasyCardEvent.builder()
        .business(f.business).instrument(f.instrument).originalEvent(original)
        .eventType(EasyCardEvent.Type.CORRECTION).fingerprint(HASH_B)
        .occurredAt(LocalDateTime.of(2026, 9, 2, 9, 0)).amount(70_000L).currency("KRW")
        .build();
    assertThat(correction.getOriginalEvent()).isSameAs(original);
    assertThat(correction.getReviewStatus()).isEqualTo(EasyCardEvent.ReviewStatus.PENDING_REVIEW);
    correction.approve("admin");
    assertThat(correction.getReviewStatus()).isEqualTo(EasyCardEvent.ReviewStatus.APPROVED);
  }

  @Test
  void markPostedApprovesAndLinksTheTransaction() {
    Fixture f = fixture();
    EasyCardEvent event = purchaseEvent(f, 50_000L, HASH_A);
    EasyTransaction txn = EasyTransaction.builder().business(f.business)
        .occurredOn(LocalDate.of(2026, 9, 1)).direction(EasyTxnDirection.EXPENSE)
        .amount(50_000L).build();
    event.markPosted(txn, "admin");
    assertThat(event.getReviewStatus()).isEqualTo(EasyCardEvent.ReviewStatus.APPROVED);
    assertThat(event.getPostedTxn()).isSameAs(txn);
  }

  @Test
  void rejectRefusesAnAlreadyPostedEvent() {
    Fixture f = fixture();
    EasyCardEvent event = purchaseEvent(f, 50_000L, HASH_A);
    EasyTransaction txn = EasyTransaction.builder().business(f.business)
        .occurredOn(LocalDate.of(2026, 9, 1)).direction(EasyTxnDirection.EXPENSE)
        .amount(50_000L).build();
    event.markPosted(txn, "admin");
    assertThatThrownBy(() -> event.reject("admin"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void settlementAndPayableSupportManyToManyPartialAllocation() {
    EasyCardPayable first = payable(70_000L);
    EasyCardPayable second = payable(30_000L);
    EasyCardSettlement paymentA = settlement(60_000L, HASH_A);
    EasyCardSettlement paymentB = settlement(40_000L, HASH_B);

    first.settle(60_000L); paymentA.allocate(60_000L);
    first.settle(10_000L); paymentB.allocate(10_000L);
    second.settle(30_000L); paymentB.allocate(30_000L);

    assertThat(first.getOutstandingAmount()).isZero();
    assertThat(second.getOutstandingAmount()).isZero();
    assertThat(paymentA.getStatus()).isEqualTo(EasyCardSettlement.Status.ALLOCATED);
    assertThat(paymentB.getStatus()).isEqualTo(EasyCardSettlement.Status.ALLOCATED);
    assertThat(first.recognizedExpenseCandidate() + second.recognizedExpenseCandidate())
        .isEqualTo(100_000L);
  }

  private EasyCardPayable payable(long amount) {
    Fixture f = fixture();
    return EasyCardPayable.builder().business(f.business).instrument(f.instrument)
        .purchaseEvent(purchaseEvent(f, amount, HASH_A)).originalAmount(amount).currency("KRW").build();
  }
  private EasyCardSettlement settlement(long amount, String hash) {
    Fixture f = fixture();
    EasyCardEvent event = EasyCardEvent.builder().business(f.business).instrument(f.instrument)
        .eventType(EasyCardEvent.Type.PAYMENT_SETTLEMENT).fingerprint(hash)
        .occurredAt(LocalDateTime.of(2026, 10, 15, 0, 0)).amount(amount).currency("KRW").build();
    return EasyCardSettlement.builder().business(f.business).instrument(f.instrument)
        .settlementEvent(event).fingerprint(hash).paidOn(LocalDate.of(2026, 10, 15))
        .amount(amount).currency("KRW").build();
  }
  private EasyCardEvent purchaseEvent(Fixture f, long amount, String hash) {
    return EasyCardEvent.builder().business(f.business).instrument(f.instrument)
        .eventType(EasyCardEvent.Type.PURCHASE).fingerprint(hash)
        .occurredAt(LocalDateTime.of(2026, 9, 1, 12, 0)).amount(amount).currency("KRW").build();
  }
  private Fixture fixture() {
    EasyBusiness business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    EasyCardInstrument instrument = EasyCardInstrument.builder().business(business)
        .providerCode("TEST").issuer("TEST ISSUER").providerInstrumentTokenHash(HASH_A)
        .maskedIdentifier("****-1234").last4("1234").build();
    return new Fixture(business, instrument);
  }
  private record Fixture(EasyBusiness business, EasyCardInstrument instrument) {}
}
