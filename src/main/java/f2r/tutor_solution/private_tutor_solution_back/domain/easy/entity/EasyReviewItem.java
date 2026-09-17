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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy 장부 할 일 — "AI가 확인할 장부" 검토 작업대.
 * AI 판정이 아니라 확인 필요 목록이며, 사용자 확인으로만 닫힌다.
 */
@Entity
@Table(name = "easy_review_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyReviewItem extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "review_id")
  private Long reviewId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20)
  private EasyReviewKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private EasyReviewStatus status = EasyReviewStatus.OPEN;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "txn_id")
  private EasyTransaction txn;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "import_row_id")
  private EasyImportRow importRow;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id")
  private EasyImportBatch batch;

  @Column(name = "title", nullable = false, length = 200)
  private String title = "";

  @Column(name = "detail", length = 500)
  private String detail;

  @Column(name = "amount")
  private Long amount;

  @Column(name = "occurred_on")
  private LocalDate occurredOn;

  @Column(name = "resolution", length = 500)
  private String resolution;

  @Column(name = "resolved_by", length = 50)
  private String resolvedBy;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @Builder
  public EasyReviewItem(EasyBusiness business, EasyReviewKind kind, EasyTransaction txn,
      EasyImportRow importRow, EasyImportBatch batch,
      String title, String detail, Long amount, LocalDate occurredOn) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (kind == null) {
      throw new IllegalArgumentException("검토 종류를 선택해 주세요.");
    }
    if (txn == null && importRow == null && batch == null) {
      throw new IllegalArgumentException("거래·staging행·배치 중 하나는 연결되어야 합니다.");
    }
    this.business = business;
    this.kind = kind;
    this.txn = txn;
    this.importRow = importRow;
    this.batch = batch;
    this.title = title == null ? "" : title;
    this.detail = detail;
    this.amount = amount;
    this.occurredOn = occurredOn;
  }

  public void resolve(String resolution, String resolvedBy) {
    this.status = EasyReviewStatus.RESOLVED;
    this.resolution = resolution;
    this.resolvedBy = resolvedBy;
    this.resolvedAt = LocalDateTime.now();
  }

  public void ignore(String resolution, String resolvedBy) {
    this.status = EasyReviewStatus.IGNORED;
    this.resolution = resolution;
    this.resolvedBy = resolvedBy;
    this.resolvedAt = LocalDateTime.now();
  }

  public void reopen() {
    this.status = EasyReviewStatus.OPEN;
    this.resolution = null;
    this.resolvedBy = null;
    this.resolvedAt = null;
  }
}
