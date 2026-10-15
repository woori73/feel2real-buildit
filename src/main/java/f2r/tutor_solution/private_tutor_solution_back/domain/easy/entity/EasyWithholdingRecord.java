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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy 원천징수 검토 기록 — 최소 기반 (신고 기능 아님).
 * 인적용역 같은 거래를 일반 비용과 구분해 검토 상태로 관리한다.
 */
@Entity
@Table(name = "easy_withholding_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyWithholdingRecord extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "record_id")
  private Long recordId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "payee", nullable = false, length = 100)
  private String payee;

  @Column(name = "gross_amount", nullable = false)
  private long grossAmount;

  @Column(name = "net_amount")
  private Long netAmount;

  @Column(name = "withholding_amount")
  private Long withholdingAmount;

  @Column(name = "paid_on")
  private LocalDate paidOn;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "txn_id")
  private EasyTransaction txn;

  @Column(name = "filing_checked", nullable = false)
  private boolean filingChecked;

  @Column(name = "statement_checked", nullable = false)
  private boolean statementChecked;

  @Column(name = "memo", length = 500)
  private String memo;

  @Builder
  public EasyWithholdingRecord(EasyBusiness business, String payee, long grossAmount,
      Long netAmount, Long withholdingAmount, LocalDate paidOn, EasyTransaction txn,
      boolean filingChecked, boolean statementChecked, String memo) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (payee == null || payee.isBlank()) {
      throw new IllegalArgumentException("지급 대상을 입력해 주세요.");
    }
    if (grossAmount < 0) {
      throw new IllegalArgumentException("지급총액은 0 이상이어야 합니다.");
    }
    if (netAmount != null && netAmount < 0) {
      throw new IllegalArgumentException("실제 지급액은 0 이상이어야 합니다.");
    }
    if (withholdingAmount != null && withholdingAmount < 0) {
      throw new IllegalArgumentException("원천징수액은 0 이상이어야 합니다.");
    }
    this.business = business;
    this.payee = payee.trim();
    this.grossAmount = grossAmount;
    this.netAmount = netAmount;
    this.withholdingAmount = withholdingAmount;
    this.paidOn = paidOn;
    this.txn = txn;
    this.filingChecked = filingChecked;
    this.statementChecked = statementChecked;
    this.memo = memo;
  }

  public void update(String payee, long grossAmount, Long netAmount, Long withholdingAmount,
      LocalDate paidOn, EasyTransaction txn, boolean filingChecked, boolean statementChecked,
      String memo) {
    if (payee == null || payee.isBlank()) {
      throw new IllegalArgumentException("지급 대상을 입력해 주세요.");
    }
    if (grossAmount < 0) {
      throw new IllegalArgumentException("지급총액은 0 이상이어야 합니다.");
    }
    if (netAmount != null && netAmount < 0) {
      throw new IllegalArgumentException("실제 지급액은 0 이상이어야 합니다.");
    }
    if (withholdingAmount != null && withholdingAmount < 0) {
      throw new IllegalArgumentException("원천징수액은 0 이상이어야 합니다.");
    }
    this.payee = payee.trim();
    this.grossAmount = grossAmount;
    this.netAmount = netAmount;
    this.withholdingAmount = withholdingAmount;
    this.paidOn = paidOn;
    this.txn = txn;
    this.filingChecked = filingChecked;
    this.statementChecked = statementChecked;
    this.memo = memo;
  }
}
