package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatcher.Bill;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatcher.Decision;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatcher.Deposit;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatcher.Reason;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 이름은 모두 가짜다. */
class TuitionDepositMatcherTest {

  private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
  private static final LocalDate TO = LocalDate.of(2026, 11, 15);
  private static final LocalDate DAY = LocalDate.of(2026, 11, 5);

  private Bill bill(long id, long balance, String code, String name) {
    return new Bill(id, balance, code, name, FROM, TO);
  }

  @Test
  @DisplayName("given 금액이 같고 확인 번호가 적힌 입금 when 대조 then 자동 기록(CODE_AMOUNT)")
  void codeAndAmount() {
    // given
    List<Bill> bills = List.of(bill(1L, 300_000, "4821", "가나다"));
    List<Deposit> deposits = List.of(new Deposit(10L, 300_000, DAY, "홍길동4821 타행이체"));
    // when
    List<Decision> result = TuitionDepositMatcher.decide(bills, deposits);
    // then
    assertThat(result).containsExactly(new Decision(10L, 1L, true, Reason.CODE_AMOUNT));
  }

  @Test
  @DisplayName("given 금액이 같고 학생 이름이 적힌 입금 when 대조 then 자동 기록(NAME_AMOUNT)")
  void nameAndAmount() {
    // given
    List<Bill> bills = List.of(bill(1L, 300_000, "4821", "가나다"));
    List<Deposit> deposits = List.of(new Deposit(10L, 300_000, DAY, "가 나다 수강료"));
    // when
    List<Decision> result = TuitionDepositMatcher.decide(bills, deposits);
    // then
    assertThat(result).containsExactly(new Decision(10L, 1L, true, Reason.NAME_AMOUNT));
  }

  @Test
  @DisplayName("given 확인 번호는 맞지만 금액이 다른 입금 when 대조 then 자동 기록하지 않고 원장 확인")
  void codeButAmountDiffers() {
    // given
    List<Bill> bills = List.of(bill(1L, 300_000, "4821", "가나다"));
    List<Deposit> deposits = List.of(new Deposit(10L, 250_000, DAY, "홍길동4821"));
    // when
    List<Decision> result = TuitionDepositMatcher.decide(bills, deposits);
    // then
    assertThat(result).containsExactly(new Decision(10L, 1L, false, Reason.AMOUNT_MISMATCH));
  }

  @Test
  @DisplayName("given 금액만 같은 입금 when 대조 then 원장 확인(AMOUNT_ONLY), 청구가 여럿이면 후보 없음")
  void amountOnly() {
    // given
    List<Deposit> deposits = List.of(new Deposit(10L, 300_000, DAY, "홍길동"));
    // when
    List<Decision> one = TuitionDepositMatcher.decide(
        List.of(bill(1L, 300_000, "4821", "가나다")), deposits);
    List<Decision> many = TuitionDepositMatcher.decide(
        List.of(bill(1L, 300_000, "4821", "가나다"), bill(2L, 300_000, "7733", "라마바")), deposits);
    // then
    assertThat(one).containsExactly(new Decision(10L, 1L, false, Reason.AMOUNT_ONLY));
    assertThat(many).containsExactly(new Decision(10L, null, false, Reason.AMOUNT_ONLY));
  }

  @Test
  @DisplayName("given 한 입금에 맞는 청구가 둘 when 대조 then 자동 기록하지 않는다(MULTIPLE_BILLS)")
  void multipleBills() {
    // given: 같은 이름 학생 둘, 같은 금액
    List<Bill> bills = List.of(bill(1L, 300_000, "4821", "가나다"), bill(2L, 300_000, "7733", "가나다"));
    List<Deposit> deposits = List.of(new Deposit(10L, 300_000, DAY, "가나다"));
    // when
    List<Decision> result = TuitionDepositMatcher.decide(bills, deposits);
    // then
    assertThat(result).containsExactly(new Decision(10L, null, false, Reason.MULTIPLE_BILLS));
  }

  @Test
  @DisplayName("given 한 청구에 맞는 입금이 둘 when 대조 then 둘 다 원장 확인(MULTIPLE_DEPOSITS)")
  void multipleDeposits() {
    // given
    List<Bill> bills = List.of(bill(1L, 300_000, "4821", "가나다"));
    List<Deposit> deposits = List.of(new Deposit(10L, 300_000, DAY, "홍길동4821"),
        new Deposit(11L, 300_000, DAY.plusDays(1), "홍길동 4821"));
    // when
    List<Decision> result = TuitionDepositMatcher.decide(bills, deposits);
    // then
    assertThat(result).containsExactly(
        new Decision(10L, 1L, false, Reason.MULTIPLE_DEPOSITS),
        new Decision(11L, 1L, false, Reason.MULTIPLE_DEPOSITS));
  }

  @Test
  @DisplayName("given 번호가 다른 숫자에 붙어 있거나 기간 밖이거나 관련 없는 입금 when 대조 then 결과에 넣지 않는다")
  void unrelated() {
    // given
    List<Bill> bills = List.of(bill(1L, 300_000, "4821", "가나다"),
        new Bill(2L, 0, "5555", "사아자", FROM, TO));
    List<Deposit> deposits = List.of(
        new Deposit(10L, 120_000, DAY, "계좌끝자리148210"),
        new Deposit(11L, 300_000, TO.plusDays(1), "홍길동4821"),
        new Deposit(12L, 50_000, DAY, "교재비 5555"));
    // when
    List<Decision> result = TuitionDepositMatcher.decide(bills, deposits);
    // then
    assertThat(result).isEmpty();
  }
}
