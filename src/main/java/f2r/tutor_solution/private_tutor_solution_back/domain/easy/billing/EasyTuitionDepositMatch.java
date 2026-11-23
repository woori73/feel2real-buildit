package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 은행 입금 거래 1건과 수납의 대조 결과. txn_id UNIQUE라 같은 입금은 한 번만 기록된다.
 * 장부 거래(easy_transactions)는 읽기만 하고 고치지 않는다.
 */
@Entity
@Table(name = "easy_tuition_deposit_matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyTuitionDepositMatch extends BaseEntity {

  /** REVIEW=원장 확인 필요, AUTO_CONFIRMED=자동 기록, CONFIRMED=원장이 확정, DISMISSED=수강료 아님. */
  public enum Status { REVIEW, AUTO_CONFIRMED, CONFIRMED, DISMISSED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "match_id")
  private Long matchId;

  @Column(name = "txn_id", nullable = false, unique = true)
  private Long txnId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id")
  private EasyTuitionPayment payment;

  @Column(name = "amount", nullable = false)
  private long amount;

  @Column(name = "occurred_on", nullable = false)
  private LocalDate occurredOn;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false, length = 30)
  private TuitionDepositMatcher.Reason reason;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Builder
  public EasyTuitionDepositMatch(Long txnId, EasyTuitionPayment payment, long amount,
      LocalDate occurredOn, Status status, TuitionDepositMatcher.Reason reason,
      LocalDateTime decidedAt) {
    this.txnId = txnId;
    this.payment = payment;
    this.amount = amount;
    this.occurredOn = occurredOn;
    this.status = status;
    this.reason = reason;
    this.decidedAt = decidedAt;
  }

  /** 납부 기록에 반영된 상태인지. */
  public boolean isApplied() {
    return status == Status.AUTO_CONFIRMED || status == Status.CONFIRMED;
  }

  public void confirm(EasyTuitionPayment payment, LocalDateTime now) {
    this.payment = payment;
    this.status = Status.CONFIRMED;
    this.decidedAt = now;
  }

  public void dismiss(LocalDateTime now) {
    this.status = Status.DISMISSED;
    this.decidedAt = now;
  }

  /** 기록 취소 — 다시 원장 확인 목록으로 돌린다. */
  public void backToReview(LocalDateTime now) {
    this.status = Status.REVIEW;
    this.decidedAt = now;
  }
}
