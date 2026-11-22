package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.NOW;
import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.deposit;
import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.link;
import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.payment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatchService.RunSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class TuitionDepositMatchServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
  private static final LocalDate TO = LocalDate.of(2026, 11, 10);

  private final EasyTransactionRepository txns = mock(EasyTransactionRepository.class);
  private final EasyTuitionPaymentRepository payments = mock(EasyTuitionPaymentRepository.class);
  private final EasyTuitionBillLinkRepository links = mock(EasyTuitionBillLinkRepository.class);
  private final EasyTuitionDepositMatchRepository matches =
      mock(EasyTuitionDepositMatchRepository.class);
  private final TuitionDepositMatchService service =
      new TuitionDepositMatchService(txns, payments, links, matches);

  private EasyTuitionPayment billed(long id, String name, long balance) {
    EasyTuitionPayment payment = payment(id, name, balance, 0);
    when(payments.findLockedByPaymentId(id)).thenReturn(Optional.of(payment));
    return payment;
  }

  private void givenDeposits(EasyTransaction... deposits) {
    when(txns.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        1L, FROM, TO)).thenReturn(List.of(deposits));
  }

  private EasyTuitionDepositMatch match(EasyTuitionDepositMatch.Status status,
      EasyTuitionPayment payment, long amount) {
    EasyTuitionDepositMatch match = EasyTuitionDepositMatch.builder().txnId(10L).payment(payment)
        .amount(amount).occurredOn(FROM).status(status)
        .reason(TuitionDepositMatcher.Reason.AMOUNT_ONLY).build();
    ReflectionTestUtils.setField(match, "matchId", 7L);
    when(matches.findById(7L)).thenReturn(Optional.of(match));
    return match;
  }

  @Test
  @DisplayName("given 번호·금액이 맞는 입금 하나와 금액만 같은 입금 하나 when 대조 then 하나는 납부 기록, 하나는 원장 확인")
  void runRecordsOnlyCertainDeposits() {
    // given
    EasyTuitionPayment certain = billed(1L, "가나다", 300_000);
    EasyTuitionPayment unsure = billed(2L, "라마바", 250_000);
    when(links.findByPayment_Business_BusinessIdAndStatus(1L, EasyTuitionBillLink.Status.ACTIVE))
        .thenReturn(List.of(link(1L, certain, TuitionBillTokens.newToken(), "4821"),
            link(2L, unsure, TuitionBillTokens.newToken(), "7733")));
    givenDeposits(deposit(10L, 300_000, FROM.plusDays(3), "홍길동4821", "계좌"),
        deposit(11L, 250_000, FROM.plusDays(3), "김아무개", "계좌"),
        deposit(12L, 300_000, FROM.plusDays(3), "카드매출 4821", "카드"));
    // when
    RunSummary summary = service.run(1L, FROM, TO, NOW);
    // then
    assertThat(summary).isEqualTo(new RunSummary(2, 0, 1, 1));
    assertThat(certain.getPaidAmount()).isEqualTo(300_000);
    assertThat(certain.getPaidOn()).isEqualTo(FROM.plusDays(3));
    assertThat(certain.getPayMethod()).isEqualTo("계좌이체");
    assertThat(unsure.getPaidAmount()).isZero();
    ArgumentCaptor<EasyTuitionDepositMatch> saved =
        ArgumentCaptor.forClass(EasyTuitionDepositMatch.class);
    verify(matches, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues()).extracting(EasyTuitionDepositMatch::getStatus)
        .containsExactlyInAnyOrder(EasyTuitionDepositMatch.Status.AUTO_CONFIRMED,
            EasyTuitionDepositMatch.Status.REVIEW);
  }

  @Test
  @DisplayName("given 이미 대조한 입금 when 다시 대조 then 납부 기록을 다시 더하지 않는다")
  void runIsIdempotent() {
    // given
    EasyTuitionPayment payment = billed(1L, "가나다", 300_000);
    when(links.findByPayment_Business_BusinessIdAndStatus(1L, EasyTuitionBillLink.Status.ACTIVE))
        .thenReturn(List.of(link(1L, payment, TuitionBillTokens.newToken(), "4821")));
    givenDeposits(deposit(10L, 300_000, FROM.plusDays(3), "홍길동4821", "계좌"));
    when(matches.findByTxnIdIn(List.of(10L))).thenReturn(List.of(
        EasyTuitionDepositMatch.builder().txnId(10L).build()));
    // when
    RunSummary summary = service.run(1L, FROM, TO, NOW);
    // then
    assertThat(summary).isEqualTo(new RunSummary(1, 1, 0, 0));
    assertThat(payment.getPaidAmount()).isZero();
    verify(matches, never()).save(any());
  }

  @Test
  @DisplayName("given 기간이 없거나 너무 긴 요청 when 대조 then 거절")
  void runValidatesRange() {
    assertThatThrownBy(() -> service.run(null, FROM, TO, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.run(1L, TO, FROM, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.run(1L, FROM, FROM.plusDays(200), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 확인 대기 입금 when 원장이 확정 then 납부 기록에 더해지고, 두 번 확정은 거절")
  void confirm() {
    // given
    EasyTuitionPayment payment = billed(1L, "가나다", 300_000);
    EasyTuitionDepositMatch match = match(EasyTuitionDepositMatch.Status.REVIEW, payment, 250_000);
    // when
    service.confirm(7L, null, NOW);
    // then
    assertThat(payment.getPaidAmount()).isEqualTo(250_000);
    assertThat(match.getStatus()).isEqualTo(EasyTuitionDepositMatch.Status.CONFIRMED);
    assertThatThrownBy(() -> service.confirm(7L, null, NOW))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이미 처리");
    assertThat(payment.getPaidAmount()).isEqualTo(250_000);
  }

  @Test
  @DisplayName("given 남은 청구액보다 큰 입금·후보 없는 입금 when 확정 then 거절하고 납부 기록은 그대로")
  void confirmRejectsOverpayAndMissingTarget() {
    // given
    EasyTuitionPayment payment = billed(1L, "가나다", 300_000);
    match(EasyTuitionDepositMatch.Status.REVIEW, null, 350_000);
    // when / then
    assertThatThrownBy(() -> service.confirm(7L, null, NOW))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("골라");
    assertThatThrownBy(() -> service.confirm(7L, 1L, NOW))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("큽니다");
    assertThat(payment.getPaidAmount()).isZero();
  }

  @Test
  @DisplayName("given 확인 대기 입금 when 수강료 아님 처리 then 납부 기록은 그대로이고 상태만 바뀐다")
  void dismiss() {
    // given
    EasyTuitionPayment payment = billed(1L, "가나다", 300_000);
    EasyTuitionDepositMatch match = match(EasyTuitionDepositMatch.Status.REVIEW, payment, 300_000);
    // when
    service.dismiss(7L, NOW);
    // then
    assertThat(match.getStatus()).isEqualTo(EasyTuitionDepositMatch.Status.DISMISSED);
    assertThat(payment.getPaidAmount()).isZero();
    assertThatThrownBy(() -> service.dismiss(7L, NOW)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 자동 기록된 입금 when 취소 then 더한 금액을 빼고 확인 목록으로 돌아간다")
  void cancel() {
    // given
    EasyTuitionPayment payment = billed(1L, "가나다", 300_000);
    payment.applyDeposit(300_000, FROM, "계좌이체");
    EasyTuitionDepositMatch match =
        match(EasyTuitionDepositMatch.Status.AUTO_CONFIRMED, payment, 300_000);
    // when
    service.cancel(7L, NOW);
    // then
    assertThat(payment.getPaidAmount()).isZero();
    assertThat(match.getStatus()).isEqualTo(EasyTuitionDepositMatch.Status.REVIEW);
    assertThatThrownBy(() -> service.cancel(7L, NOW)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 확인 대기 대조 when 목록 조회 then 입금 내용과 후보 수납이 함께 나온다")
  void list() {
    // given
    EasyTuitionPayment payment = billed(1L, "가나다", 300_000);
    EasyTuitionDepositMatch match = match(EasyTuitionDepositMatch.Status.REVIEW, payment, 300_000);
    when(matches.findByStatusInOrderByOccurredOnDescMatchIdDesc(
        List.of(EasyTuitionDepositMatch.Status.REVIEW))).thenReturn(List.of(match));
    when(txns.findAllById(List.of(10L)))
        .thenReturn(List.of(deposit(10L, 300_000, FROM, "김아무개", "계좌")));
    // when
    List<TuitionDepositMatchService.MatchRow> rows = service.list(false);
    // then
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).counterparty()).isEqualTo("김아무개");
    assertThat(rows.get(0).studentName()).isEqualTo("가나다");
    assertThat(rows.get(0).balance()).isEqualTo(300_000);
  }
}
