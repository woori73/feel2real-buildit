package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy 월 수납 — 학생별 대상월 청구/납부. 상태는 저장하지 않고 자동 계산한다.
 */
@Entity
@Table(name = "easy_tuition_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyTuitionPayment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "payment_id")
  private Long paymentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private EasyStudent student;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "target_month", nullable = false, length = 7)
  private String targetMonth;

  @Column(name = "billed_amount", nullable = false)
  private long billedAmount;

  @Column(name = "paid_amount", nullable = false)
  private long paidAmount;

  @Column(name = "paid_on")
  private LocalDate paidOn;

  @Column(name = "pay_method", nullable = false, length = 30)
  private String payMethod = "";

  @Column(name = "memo", length = 500)
  private String memo;

  @Builder
  public EasyTuitionPayment(EasyStudent student, EasyBusiness business, String targetMonth,
      long billedAmount, long paidAmount, LocalDate paidOn, String payMethod, String memo) {
    this.student = student;
    this.business = business;
    this.targetMonth = targetMonth;
    this.billedAmount = billedAmount;
    this.paidAmount = paidAmount;
    this.paidOn = paidOn;
    this.payMethod = payMethod == null ? "" : payMethod;
    this.memo = memo;
  }

  public void update(EasyBusiness business, String targetMonth, long billedAmount,
      long paidAmount, LocalDate paidOn, String payMethod, String memo) {
    this.business = business;
    this.targetMonth = targetMonth;
    this.billedAmount = billedAmount;
    this.paidAmount = paidAmount;
    this.paidOn = paidOn;
    this.payMethod = payMethod == null ? "" : payMethod;
    this.memo = memo;
  }

  /** 입금·결제 확인분을 납부 기록에 더한다(청구 대조·결제창 확인에서만 호출). */
  public void applyDeposit(long amount, LocalDate paidOn, String payMethod) {
    if (amount <= 0) {
      throw new IllegalArgumentException("납부 금액은 0보다 커야 합니다.");
    }
    this.paidAmount += amount;
    this.paidOn = paidOn;
    this.payMethod = payMethod == null ? "" : payMethod;
  }

  /** applyDeposit로 더한 금액을 되돌린다(잘못 대조된 입금 취소). 0 미만으로 내려가지 않는다. */
  public void revertDeposit(long amount) {
    this.paidAmount = Math.max(0L, this.paidAmount - amount);
    if (this.paidAmount == 0L) {
      this.paidOn = null;
    }
  }

  /** 청구/납부 기준 자동 상태 — 미납/일부납/완납. */
  @Transient
  public EasyPaymentStatus getStatus() {
    return EasyPaymentStatus.of(billedAmount, paidAmount);
  }

  /** 미수금 = 청구 - 납부 (0 미만 방지). */
  @Transient
  public long getBalance() {
    return Math.max(0L, billedAmount - paidAmount);
  }
}
