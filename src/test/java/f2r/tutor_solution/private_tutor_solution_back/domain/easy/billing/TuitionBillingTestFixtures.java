package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/** 단위 테스트용 가짜 데이터(이름은 모두 지어낸 것). */
final class TuitionBillingTestFixtures {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

  private TuitionBillingTestFixtures() {}

  static EasyTuitionPayment payment(long id, String studentName, long billed, long paid) {
    EasyBusiness business = EasyBusiness.builder().code("ACADEMY").name("시험학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    EasyStudent student = EasyStudent.builder().name(studentName).grade((byte) 1)
        .parentPhone("010-0000-0000").build();
    EasyTuitionPayment payment = EasyTuitionPayment.builder().student(student).business(business)
        .targetMonth("2026-11").billedAmount(billed).paidAmount(paid).build();
    ReflectionTestUtils.setField(payment, "paymentId", id);
    return payment;
  }

  static EasyTuitionBillLink link(long id, EasyTuitionPayment payment, String token, String code) {
    EasyTuitionBillLink link = EasyTuitionBillLink.builder().payment(payment)
        .tokenHash(TuitionBillTokens.hash(token)).depositorCode(code).bankName("시험은행")
        .bankAccountNo("110-123-456789").bankAccountHolder("시험학원").expiresAt(NOW.plusDays(14))
        .build();
    ReflectionTestUtils.setField(link, "linkId", id);
    ReflectionTestUtils.setField(link, "createdAt", NOW.minusDays(1));
    return link;
  }

  static EasyTransaction deposit(long id, long amount, LocalDate date, String counterparty,
      String payMethod) {
    EasyTransaction txn = EasyTransaction.builder().occurredOn(date)
        .direction(EasyTxnDirection.INCOME).amount(amount).counterparty(counterparty)
        .description("타행이체").payMethod(payMethod).build();
    ReflectionTestUtils.setField(txn, "txnId", id);
    return txn;
  }
}
