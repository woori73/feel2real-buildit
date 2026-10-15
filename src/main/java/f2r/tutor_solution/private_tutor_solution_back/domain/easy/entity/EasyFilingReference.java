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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy 과거 신고 기준자료 — Library 확보 자료를 손으로 등록하는 reference다.
 * 장부 금액에 맞추기 위한 역산에 쓰지 않는다. 독립 비교용이다.
 */
@Entity
@Table(name = "easy_filing_references")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyFilingReference extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ref_id")
  private Long refId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "fiscal_year", nullable = false)
  private int fiscalYear;

  @Column(name = "revenue")
  private Long revenue;

  @Column(name = "expense")
  private Long expense;

  @Column(name = "income_amount")
  private Long incomeAmount;

  @Column(name = "source", nullable = false, length = 100)
  private String source = "";

  @Column(name = "note", length = 500)
  private String note;

  @Builder
  public EasyFilingReference(EasyBusiness business, int fiscalYear, Long revenue, Long expense,
      Long incomeAmount, String source, String note) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (fiscalYear < 2000 || fiscalYear > 2100) {
      throw new IllegalArgumentException("귀속연도가 올바르지 않습니다.");
    }
    if (revenue != null && revenue < 0) {
      throw new IllegalArgumentException("수입금액은 0 이상이어야 합니다.");
    }
    if (expense != null && expense < 0) {
      throw new IllegalArgumentException("필요경비는 0 이상이어야 합니다.");
    }
    this.business = business;
    this.fiscalYear = fiscalYear;
    this.revenue = revenue;
    this.expense = expense;
    this.incomeAmount = incomeAmount;
    this.source = source == null ? "" : source;
    this.note = note;
  }

  public void update(Long revenue, Long expense, Long incomeAmount, String source, String note) {
    if (revenue != null && revenue < 0) {
      throw new IllegalArgumentException("수입금액은 0 이상이어야 합니다.");
    }
    if (expense != null && expense < 0) {
      throw new IllegalArgumentException("필요경비는 0 이상이어야 합니다.");
    }
    this.revenue = revenue;
    this.expense = expense;
    this.incomeAmount = incomeAmount;
    if (source != null) {
      this.source = source;
    }
    this.note = note;
  }
}
