package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.NOW;
import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.link;
import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.payment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillLinkService.CreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TuitionBillLinkServiceTest {

  private final EasyTuitionBillLinkRepository links = mock(EasyTuitionBillLinkRepository.class);
  private final EasyTuitionPaymentRepository payments = mock(EasyTuitionPaymentRepository.class);
  private final TuitionBillLinkService service = new TuitionBillLinkService(links, payments);

  private CreateRequest request(Integer ttl) {
    return new CreateRequest(1L, "시험은행", "110-123-456789", "시험학원", ttl);
  }

  @Test
  @DisplayName("given 미납 수납과 이전 링크 when 링크 만들기 then 이전 링크는 회수되고 토큰은 해시로만 저장된다")
  void createStoresHashAndRevokesOld() {
    // given
    EasyTuitionPayment payment = payment(1L, "가나다", 300_000, 0);
    EasyTuitionBillLink old = link(5L, payment, TuitionBillTokens.newToken(), "1111");
    when(payments.findById(1L)).thenReturn(Optional.of(payment));
    when(links.findByPayment_PaymentIdAndStatus(1L, EasyTuitionBillLink.Status.ACTIVE))
        .thenReturn(List.of(old));
    when(links.save(any())).thenAnswer(inv -> inv.getArgument(0));
    // when
    TuitionBillLinkService.CreatedLink created = service.create(request(null), NOW);
    // then
    ArgumentCaptor<EasyTuitionBillLink> saved = ArgumentCaptor.forClass(EasyTuitionBillLink.class);
    verify(links).save(saved.capture());
    assertThat(old.getStatus()).isEqualTo(EasyTuitionBillLink.Status.REVOKED);
    assertThat(created.token()).hasSize(43);
    assertThat(saved.getValue().getTokenHash()).isEqualTo(TuitionBillTokens.hash(created.token()))
        .isNotEqualTo(created.token());
    assertThat(created.depositorCode()).matches("[1-9][0-9]{3}");
    assertThat(created.expiresAt()).isEqualTo(NOW.plusDays(TuitionBillLinkService.DEFAULT_TTL_DAYS));
  }

  @Test
  @DisplayName("given 완납 수납·잘못된 계좌·기한 when 링크 만들기 then 거절하고 저장하지 않는다")
  void createRejectsInvalid() {
    // given
    when(payments.findById(1L)).thenReturn(Optional.of(payment(1L, "가나다", 300_000, 300_000)));
    when(payments.findById(2L)).thenReturn(Optional.of(payment(2L, "가나다", 300_000, 0)));
    // when / then
    assertThatThrownBy(() -> service.create(request(null), NOW))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("남은 청구 금액");
    assertThatThrownBy(() -> service.create(
        new CreateRequest(2L, "시험은행", "계좌 abc", "시험학원", null), NOW))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("계좌번호");
    assertThatThrownBy(() -> service.create(
        new CreateRequest(2L, " ", "110-123-456789", "시험학원", null), NOW))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("은행");
    assertThatThrownBy(() -> service.create(
        new CreateRequest(2L, "시험은행", "110-123-456789", "시험학원", 61), NOW))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("기한");
    assertThatThrownBy(() -> service.create(new CreateRequest(null, null, null, null, null), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    verify(links, never()).save(any());
  }

  @Test
  @DisplayName("given 열린 링크 when 공개 화면 조회 then 이름은 가려지고 실명·연락처가 없다")
  void publicViewMasksName() {
    // given
    String token = TuitionBillTokens.newToken();
    EasyTuitionBillLink link = link(1L, payment(1L, "가나다", 300_000, 100_000), token, "4821");
    when(links.findByTokenHash(TuitionBillTokens.hash(token))).thenReturn(Optional.of(link));
    // when
    TuitionBillLinkService.PublicBill bill = service.publicView(token, NOW).orElseThrow();
    // then
    assertThat(bill.maskedStudentName()).isEqualTo("가*다");
    assertThat(bill.toString()).doesNotContain("가나다").doesNotContain("010-0000-0000");
    assertThat(bill.balance()).isEqualTo(200_000);
    assertThat(bill.paid()).isFalse();
    assertThat(bill.depositorCode()).isEqualTo("4821");
    assertThat(bill.academyName()).isEqualTo("시험학원");
  }

  @Test
  @DisplayName("given 없는·형식이 틀린·만료된·회수된 토큰 when 공개 화면 조회 then 모두 비어 있다")
  void publicViewHidesClosedLinks() {
    // given
    String expiredToken = TuitionBillTokens.newToken();
    String revokedToken = TuitionBillTokens.newToken();
    EasyTuitionBillLink expired = link(1L, payment(1L, "가나다", 300_000, 0), expiredToken, "4821");
    EasyTuitionBillLink revoked = link(2L, payment(2L, "가나다", 300_000, 0), revokedToken, "4822");
    revoked.revoke(NOW);
    when(links.findByTokenHash(TuitionBillTokens.hash(expiredToken))).thenReturn(Optional.of(expired));
    when(links.findByTokenHash(TuitionBillTokens.hash(revokedToken))).thenReturn(Optional.of(revoked));
    // when / then
    assertThat(service.publicView(expiredToken, NOW.plusDays(15))).isEmpty();
    assertThat(service.publicView(revokedToken, NOW)).isEmpty();
    assertThat(service.publicView(TuitionBillTokens.newToken(), NOW)).isEmpty();
    assertThat(service.publicView("../etc", NOW)).isEmpty();
  }

  @Test
  @DisplayName("given 대상 월 수납 when 목록 조회 then 가장 최근 링크 상태가 붙는다")
  void listForMonth() {
    // given
    EasyTuitionPayment withLink = payment(1L, "가나다", 300_000, 0);
    EasyTuitionPayment noLink = payment(2L, "라마바", 200_000, 0);
    when(payments.findByTargetMonthOrderByStudent_NameAsc("2026-11"))
        .thenReturn(List.of(withLink, noLink));
    when(links.findByPayment_PaymentIdInOrderByLinkIdDesc(List.of(1L, 2L)))
        .thenReturn(List.of(link(9L, withLink, TuitionBillTokens.newToken(), "4821")));
    // when
    List<TuitionBillLinkService.AdminRow> rows = service.listForMonth("2026-11", NOW);
    // then
    assertThat(rows).extracting(TuitionBillLinkService.AdminRow::linkState)
        .containsExactly("ACTIVE", "NONE");
    assertThat(rows.get(0).linkId()).isEqualTo(9L);
    assertThatThrownBy(() -> service.listForMonth("2026-13", NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 열린 링크 when 회수 then 상태만 REVOKED로 바뀌고 행은 남는다")
  void revoke() {
    // given
    EasyTuitionBillLink link = link(1L, payment(1L, "가나다", 300_000, 0),
        TuitionBillTokens.newToken(), "4821");
    when(links.findById(1L)).thenReturn(Optional.of(link));
    // when
    service.revoke(1L, NOW);
    // then
    assertThat(link.getStatus()).isEqualTo(EasyTuitionBillLink.Status.REVOKED);
    verify(links, never()).delete(any());
    assertThatThrownBy(() -> service.revoke(99L, NOW)).isInstanceOf(IllegalArgumentException.class);
  }
}
