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
 * Easy staging 행 — 원본 1행. 검증·계정제안 후 확정 시 거래로 변환된다.
 */
@Entity
@Table(name = "easy_import_rows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyImportRow extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "row_id")
  private Long rowId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false)
  private EasyImportBatch batch;

  @Column(name = "row_no", nullable = false)
  private int rowNo;

  @Column(name = "occurred_on")
  private LocalDate occurredOn;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", length = 10)
  private EasyTxnDirection direction;

  @Column(name = "amount")
  private Long amount;

  @Column(name = "counterparty", nullable = false, length = 100)
  private String counterparty = "";

  @Column(name = "description", nullable = false, length = 200)
  private String description = "";

  @Column(name = "pay_method", nullable = false, length = 30)
  private String payMethod = "";

  @Column(name = "supply_amount")
  private Long supplyAmount;

  @Column(name = "vat_amount")
  private Long vatAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_type", length = 30)
  private EasyEvidenceType evidenceType;

  @Column(name = "raw_data", columnDefinition = "TEXT")
  private String rawData;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private EasyImportRowStatus status = EasyImportRowStatus.PENDING;

  @Column(name = "error", length = 500)
  private String error;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "matched_account_id")
  private EasyAccount matchedAccount;

  @Column(name = "confidence")
  private Double confidence;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_txn_id")
  private EasyTransaction createdTxn;

  @Builder
  public EasyImportRow(EasyImportBatch batch, int rowNo, LocalDate occurredOn,
      EasyTxnDirection direction, Long amount, String counterparty, String description,
      String payMethod, Long supplyAmount, Long vatAmount, EasyEvidenceType evidenceType,
      String rawData) {
    this.batch = batch;
    this.rowNo = rowNo;
    this.occurredOn = occurredOn;
    this.direction = direction;
    this.amount = amount;
    this.counterparty = counterparty == null ? "" : counterparty;
    this.description = description == null ? "" : description;
    this.payMethod = payMethod == null ? "" : payMethod;
    this.supplyAmount = supplyAmount;
    this.vatAmount = vatAmount;
    this.evidenceType = evidenceType;
    this.rawData = rawData;
  }

  public void markReady(EasyAccount matchedAccount, Double confidence) {
    this.status = EasyImportRowStatus.READY;
    this.error = null;
    this.matchedAccount = matchedAccount;
    this.confidence = confidence;
  }

  public void markError(String error) {
    this.status = EasyImportRowStatus.ERROR;
    this.error = error;
  }

  public void markImported(EasyTransaction createdTxn) {
    this.status = EasyImportRowStatus.IMPORTED;
    this.error = null;
    this.createdTxn = createdTxn;
  }
}
