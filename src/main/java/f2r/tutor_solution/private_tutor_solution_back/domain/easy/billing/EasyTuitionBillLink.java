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
 * 수강료 청구 링크 — 수납 1건을 학부모가 로그인 없이 볼 수 있게 하는 일회용 주소.
 * 토큰 원문은 저장하지 않고 SHA-256 해시만 둔다. 삭제 대신 회수(REVOKED)한다.
 */
@Entity
@Table(name = "easy_tuition_bill_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyTuitionBillLink extends BaseEntity {

  public enum Status { ACTIVE, REVOKED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "link_id")
  private Long linkId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id", nullable = false)
  private EasyTuitionPayment payment;

  @Column(name = "token_hash", nullable = false, length = 64, unique = true)
  private String tokenHash;

  @Column(name = "depositor_code", nullable = false, length = 8)
  private String depositorCode;

  @Column(name = "bank_name", nullable = false, length = 40)
  private String bankName;

  @Column(name = "bank_account_no", nullable = false, length = 40)
  private String bankAccountNo;

  @Column(name = "bank_account_holder", nullable = false, length = 40)
  private String bankAccountHolder;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Builder
  public EasyTuitionBillLink(EasyTuitionPayment payment, String tokenHash, String depositorCode,
      String bankName, String bankAccountNo, String bankAccountHolder, LocalDateTime expiresAt) {
    this.payment = payment;
    this.tokenHash = tokenHash;
    this.depositorCode = depositorCode;
    this.bankName = bankName;
    this.bankAccountNo = bankAccountNo;
    this.bankAccountHolder = bankAccountHolder;
    this.expiresAt = expiresAt;
    this.status = Status.ACTIVE;
  }

  public void revoke(LocalDateTime now) {
    if (status == Status.ACTIVE) {
      this.status = Status.REVOKED;
      this.revokedAt = now;
    }
  }

  /** 학부모가 지금 열 수 있는 링크인지(회수되지 않았고 만료 전). */
  public boolean isOpen(LocalDateTime now) {
    return status == Status.ACTIVE && now.isBefore(expiresAt);
  }

  /** 입금 대조 기간(링크를 만든 날 ~ 만료일)에 든 입금인지. 만든 날을 모르면(저장 전) 만료일만 본다. */
  public boolean coversDepositDate(LocalDate date) {
    LocalDate from = getCreatedAt() == null ? LocalDate.MIN : getCreatedAt().toLocalDate();
    return !date.isBefore(from) && !date.isAfter(expiresAt.toLocalDate());
  }
}
