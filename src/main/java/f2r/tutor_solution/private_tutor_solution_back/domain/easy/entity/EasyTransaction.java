package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy 수입·지출 거래 — 반드시 사업체에 연결된다. 금액은 원 단위 정수.
 */
@Entity
@Table(name = "easy_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyTransaction extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "txn_id")
  private Long txnId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "occurred_on", nullable = false)
  private LocalDate occurredOn;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10)
  private EasyTxnDirection direction;

  @Column(name = "amount", nullable = false)
  private long amount;

  @Column(name = "category", nullable = false, length = 50)
  private String category = "기타";

  @Column(name = "counterparty", nullable = false, length = 100)
  private String counterparty = "";

  @Column(name = "description", nullable = false, length = 200)
  private String description = "";

  @Column(name = "pay_method", nullable = false, length = 30)
  private String payMethod = "";

  @Column(name = "memo", length = 500)
  private String memo;

  @Column(name = "receipt_ref", length = 200)
  private String receiptRef;

  @Builder
  public EasyTransaction(EasyBusiness business, LocalDate occurredOn, EasyTxnDirection direction,
      long amount, String category, String counterparty, String description,
      String payMethod, String memo, String receiptRef) {
    this.business = business;
    this.occurredOn = occurredOn;
    this.direction = direction;
    this.amount = amount;
    this.category = category == null ? "기타" : category;
    this.counterparty = counterparty == null ? "" : counterparty;
    this.description = description == null ? "" : description;
    this.payMethod = payMethod == null ? "" : payMethod;
    this.memo = memo;
    this.receiptRef = receiptRef;
  }

  public void update(EasyBusiness business, LocalDate occurredOn, EasyTxnDirection direction,
      long amount, String category, String counterparty, String description,
      String payMethod, String memo, String receiptRef) {
    this.business = business;
    this.occurredOn = occurredOn;
    this.direction = direction;
    this.amount = amount;
    this.category = category == null ? "기타" : category;
    this.counterparty = counterparty == null ? "" : counterparty;
    this.description = description == null ? "" : description;
    this.payMethod = payMethod == null ? "" : payMethod;
    this.memo = memo;
    this.receiptRef = receiptRef;
  }
}
