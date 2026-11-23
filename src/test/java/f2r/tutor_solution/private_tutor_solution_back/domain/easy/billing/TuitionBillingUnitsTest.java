package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 토큰·이름 가리기·요청 제한·스위치·시험 대행 구현·엔티티 상태 변화의 단위 테스트. */
class TuitionBillingUnitsTest {

  @Test
  @DisplayName("given 새 토큰 둘 when 해시 then 43자 토큰·64자 해시이고 서로 다르며 같은 토큰은 같은 해시")
  void tokenAndHash() {
    // given
    String a = TuitionBillTokens.newToken();
    String b = TuitionBillTokens.newToken();
    // when
    String hashA = TuitionBillTokens.hash(a);
    // then
    assertThat(a).hasSize(43).isNotEqualTo(b);
    assertThat(hashA).hasSize(64).isEqualTo(TuitionBillTokens.hash(a))
        .isNotEqualTo(TuitionBillTokens.hash(b)).doesNotContain(a);
  }

  @Test
  @DisplayName("given 토큰 형식이 아닌 값 when 해시 then null(DB 조회 안 함)")
  void hashRejectsMalformed() {
    assertThat(TuitionBillTokens.hash(null)).isNull();
    assertThat(TuitionBillTokens.hash("short")).isNull();
    assertThat(TuitionBillTokens.hash("a".repeat(42) + "/")).isNull();
  }

  @Test
  @DisplayName("given 주문번호·확인 번호 생성 when 확인 then 32자 hex, 숫자 4자리")
  void orderIdAndDepositorCode() {
    assertThat(TuitionBillTokens.newOrderId()).matches("[0-9a-f]{32}");
    assertThat(TuitionBillTokens.newDepositorCode()).matches("[1-9][0-9]{3}");
  }

  @Test
  @DisplayName("given 여러 길이의 이름 when 가리기 then 첫·끝 글자만 남는다")
  void maskName() {
    assertThat(TuitionNameMasker.mask("가나다")).isEqualTo("가*다");
    assertThat(TuitionNameMasker.mask("가나")).isEqualTo("가*");
    assertThat(TuitionNameMasker.mask("가나다라")).isEqualTo("가**라");
    assertThat(TuitionNameMasker.mask("가")).isEqualTo("*");
    assertThat(TuitionNameMasker.mask(" ")).isEqualTo("학생");
    assertThat(TuitionNameMasker.mask(null)).isEqualTo("학생");
  }

  @Test
  @DisplayName("given 같은 IP가 1분에 30번 요청 when 31번째 then 거절, 1분 뒤 다시 허용, 다른 IP는 무관")
  void rateLimit() {
    // given
    TuitionPayRateLimiter limiter = new TuitionPayRateLimiter();
    for (int i = 0; i < TuitionPayRateLimiter.PER_MINUTE; i++) {
      assertThat(limiter.tryAcquire("203.0.113.5", 1_000L + i)).isTrue();
    }
    // when / then
    assertThat(limiter.tryAcquire("203.0.113.5", 2_000L)).isFalse();
    assertThat(limiter.tryAcquire("203.0.113.6", 2_000L)).isTrue();
    assertThat(limiter.tryAcquire("203.0.113.5", 62_000L)).isTrue();
    assertThat(limiter.tryAcquire(null, 62_000L)).isFalse();
  }

  @Test
  @DisplayName("given 스위치 OFF when requireEnabled then 404, card-test만 켜도 결제창은 닫힘")
  void switchOff() {
    // given
    TuitionBillingSwitch off = new TuitionBillingSwitch(false, true);
    TuitionBillingSwitch on = new TuitionBillingSwitch(true, false);
    // when / then
    assertThat(off.isEnabled()).isFalse();
    assertThat(off.isCardTestEnabled()).isFalse();
    assertThatThrownBy(off::requireEnabled).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
    on.requireEnabled();
    assertThatThrownBy(on::requireCardTestEnabled).isInstanceOf(ResponseStatusException.class);
    new TuitionBillingSwitch(true, true).requireCardTestEnabled();
  }

  @Test
  @DisplayName("given 시험 대행 구현 when 승인 요청 then test_ok_ 로 시작하는 값만 승인, 결제창은 내부 시험 주소")
  void fakeGateway() {
    // given
    FakeTuitionPaymentGateway gateway = new FakeTuitionPaymentGateway();
    // when / then
    assertThat(gateway.code()).isEqualTo("FAKE");
    assertThat(gateway.approve("o1", "test_ok_o1", 1000).approved()).isTrue();
    assertThat(gateway.approve("o1", "anything", 1000).approved()).isFalse();
    assertThat(gateway.approve("o1", null, 1000).failureCode()).isEqualTo("TEST_DECLINED");
    assertThat(gateway.checkoutPath("tok", "o1")).isEqualTo("/pay/tok/test-checkout?orderId=o1");
  }

  @Test
  @DisplayName("given 미납 수납 when 입금 반영 후 되돌리기 then 납부액·납부일이 원래대로")
  void applyAndRevertDeposit() {
    // given
    EasyTuitionPayment payment = EasyTuitionPayment.builder()
        .targetMonth("2026-11").billedAmount(300_000).paidAmount(0).build();
    // when
    payment.applyDeposit(300_000, LocalDate.of(2026, 11, 5), "계좌이체");
    // then
    assertThat(payment.getBalance()).isZero();
    assertThat(payment.getPaidOn()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(payment.getPayMethod()).isEqualTo("계좌이체");
    assertThatThrownBy(() -> payment.applyDeposit(0, LocalDate.now(), "계좌이체"))
        .isInstanceOf(IllegalArgumentException.class);
    // when
    payment.revertDeposit(300_000);
    // then
    assertThat(payment.getPaidAmount()).isZero();
    assertThat(payment.getPaidOn()).isNull();
  }

  @Test
  @DisplayName("given 청구 링크 when 만료·회수 then 열 수 없고, 대조 기간은 만료일까지")
  void linkLifecycle() {
    // given
    LocalDateTime now = LocalDateTime.of(2026, 11, 1, 9, 0);
    EasyTuitionBillLink link = EasyTuitionBillLink.builder().expiresAt(now.plusDays(14)).build();
    // when / then
    assertThat(link.isOpen(now)).isTrue();
    assertThat(link.isOpen(now.plusDays(14))).isFalse();
    assertThat(link.coversDepositDate(LocalDate.of(2026, 11, 15))).isTrue();
    assertThat(link.coversDepositDate(LocalDate.of(2026, 11, 16))).isFalse();
    link.revoke(now);
    assertThat(link.isOpen(now)).isFalse();
    assertThat(link.getRevokedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("given 확인 대기 대조 when 확정·취소·넘기기 then 상태와 반영 여부가 바뀐다")
  void matchLifecycle() {
    // given
    LocalDateTime now = LocalDateTime.of(2026, 11, 5, 9, 0);
    EasyTuitionDepositMatch match = EasyTuitionDepositMatch.builder().txnId(1L).amount(1000)
        .occurredOn(now.toLocalDate()).status(EasyTuitionDepositMatch.Status.REVIEW)
        .reason(TuitionDepositMatcher.Reason.AMOUNT_ONLY).build();
    // when / then
    assertThat(match.isApplied()).isFalse();
    match.confirm(null, now);
    assertThat(match.isApplied()).isTrue();
    match.backToReview(now);
    assertThat(match.getStatus()).isEqualTo(EasyTuitionDepositMatch.Status.REVIEW);
    match.dismiss(now);
    assertThat(match.getStatus()).isEqualTo(EasyTuitionDepositMatch.Status.DISMISSED);
    assertThat(match.isApplied()).isFalse();
  }

  @Test
  @DisplayName("given 준비 주문 when 결제 완료·실패 표시 then 상태와 결제 식별값이 기록된다")
  void orderLifecycle() {
    // given
    LocalDateTime now = LocalDateTime.of(2026, 11, 5, 9, 0);
    EasyTuitionPgOrder order = EasyTuitionPgOrder.builder().orderId("o1").amount(1000)
        .gateway("FAKE").build();
    // when / then
    assertThat(order.getStatus()).isEqualTo(EasyTuitionPgOrder.Status.READY);
    order.markPaid("test_ok_o1", now);
    assertThat(order.getStatus()).isEqualTo(EasyTuitionPgOrder.Status.PAID);
    assertThat(order.getPaymentKey()).isEqualTo("test_ok_o1");
    assertThat(order.getPaidAt()).isEqualTo(now);
    EasyTuitionPgOrder failed = EasyTuitionPgOrder.builder().orderId("o2").amount(1000)
        .gateway("FAKE").build();
    failed.markFailed();
    assertThat(failed.getStatus()).isEqualTo(EasyTuitionPgOrder.Status.FAILED);
  }
}
