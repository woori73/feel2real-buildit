package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;import jakarta.persistence.FetchType;
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

  /** 계정 master 연결 — 없으면 category 텍스트를 그대로 쓴다 (v1 호환). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id")
  private EasyAccount account;

  /** 공급가액 — 미분리면 NULL. 세율 자동계산은 하지 않는다. */
  @Column(name = "supply_amount")
  private Long supplyAmount;

  /** 부가세 — 미분리면 NULL. */
  @Column(name = "vat_amount")
  private Long vatAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_type", length = 30)
  private EasyEvidenceType evidenceType;

  /** 거래 출처 — 여러 source에서 들어오는 동일 경제거래 추적용. */
  @Column(name = "source_kind", length = 20)
  private String sourceKind;

  /** 원본 참조 — 파일명+행번호 등. */
  @Column(name = "source_ref", length = 200)
  private String sourceRef;

  /**
   * 중복 의심 탐지용 해시 — 자동합치기에 쓰지 않는다.
   * 의심만 탐지하고 판단·합치기는 사용자가 한다.
   */
  @Column(name = "fingerprint", length = 64)
  private String fingerprint;

  /** 증빙 검토 상태 — 세법 판정이 아니라 확인 표시다. NULL이면 UNKNOWN. */
  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_review", length = 20)
  private EasyEvidenceReview evidenceReview;

  /** 원천징수 검토 상태 — 신고 기능이 아니라 검토 표시다. NULL이면 NONE. */
  @Enumerated(EnumType.STRING)
  @Column(name = "withholding_review", length = 20)
  private EasyWithholdingReview withholdingReview;

  /**
   * 비용 환불 역분개 — 실데이터의 음수 비용(예: -330,000원 환불)을
   * 누락·절댓값 변환 없이 보존한다. 방향은 INCOME, P&L에서는 비용 차감으로 집계한다.
   */
  @Column(name = "is_expense_reversal")
  private Boolean expenseReversal;

  @Builder
  public EasyTransaction(EasyBusiness business, LocalDate occurredOn, EasyTxnDirection direction,
      long amount, String category, String counterparty, String description,
      String payMethod, String memo, String receiptRef,
      EasyAccount account, Long supplyAmount, Long vatAmount, EasyEvidenceType evidenceType,
      String sourceKind, String sourceRef, String fingerprint,
      EasyEvidenceReview evidenceReview, EasyWithholdingReview withholdingReview,
      Boolean expenseReversal) {
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
    this.account = account;
    this.supplyAmount = supplyAmount;
    this.vatAmount = vatAmount;
    this.evidenceType = evidenceType;
    this.sourceKind = sourceKind;
    this.sourceRef = sourceRef;
    this.fingerprint = fingerprint;
    this.evidenceReview = evidenceReview;
    this.withholdingReview = withholdingReview;
    this.expenseReversal = expenseReversal;
  }

  public void update(EasyBusiness business, LocalDate occurredOn, EasyTxnDirection direction,
      long amount, String category, String counterparty, String description,
      String payMethod, String memo, String receiptRef,
      EasyAccount account, Long supplyAmount, Long vatAmount, EasyEvidenceType evidenceType,
      String sourceKind, String sourceRef, String fingerprint,
      EasyEvidenceReview evidenceReview, EasyWithholdingReview withholdingReview,
      Boolean expenseReversal) {
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
    this.account = account;
    this.supplyAmount = supplyAmount;
    this.vatAmount = vatAmount;
    this.evidenceType = evidenceType;
    this.sourceKind = sourceKind;
    this.sourceRef = sourceRef;
    this.fingerprint = fingerprint;
    this.evidenceReview = evidenceReview;
    this.withholdingReview = withholdingReview;
    this.expenseReversal = expenseReversal;
  }

  /** 환불 역분개 여부 — NULL은 일반 거래로 읽는다. */
  public boolean isReversal() {
    return Boolean.TRUE.equals(expenseReversal);
  }
}
