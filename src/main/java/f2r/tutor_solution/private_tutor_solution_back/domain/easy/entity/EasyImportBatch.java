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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy import 배치 — 원본 묶음. 확정된 행만 장부로 옮긴다.
 */
@Entity
@Table(name = "easy_import_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyImportBatch extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "batch_id")
  private Long batchId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "source_name", nullable = false, length = 200)
  private String sourceName = "";

  @Column(name = "source_kind", nullable = false, length = 20)
  private String sourceKind = "CSV";

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private EasyImportStatus status = EasyImportStatus.STAGED;

  @Column(name = "total_rows", nullable = false)
  private int totalRows;

  @Column(name = "confirmed_rows", nullable = false)
  private int confirmedRows;

  @Builder
  public EasyImportBatch(EasyBusiness business, String sourceName, String sourceKind, int totalRows) {
    this.business = business;
    this.sourceName = sourceName == null ? "" : sourceName;
    this.sourceKind = sourceKind == null ? "CSV" : sourceKind;
    this.totalRows = totalRows;
  }

  public void markConfirmed(int confirmedRows) {
    this.status = EasyImportStatus.CONFIRMED;
    this.confirmedRows = confirmedRows;
  }

  public void markDiscarded() {
    this.status = EasyImportStatus.DISCARDED;
  }
}
