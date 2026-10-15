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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy 연도 마감 — 단순 상태 전이. 마감 후에도 해제→수정→재마감이 가능하다.
 * 사업장별 연간 결산 → 개인 귀속연도 합산(종소세 준비)으로 확장 가능한 구조다.
 * 장부 DB를 합치지 않는다 — 합산은 조회 시점에만 한다.
 */
@Entity
@Table(name = "easy_annual_closings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyAnnualClosing extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "closing_id")
  private Long closingId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "fiscal_year", nullable = false)
  private int fiscalYear;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private EasyClosingStatus status = EasyClosingStatus.OPEN;

  @Column(name = "checklist_snapshot", columnDefinition = "TEXT")
  private String checklistSnapshot;

  @Column(name = "note", length = 500)
  private String note;

  @Column(name = "closed_at")
  private LocalDateTime closedAt;

  @Builder
  public EasyAnnualClosing(EasyBusiness business, int fiscalYear) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (fiscalYear < 2000 || fiscalYear > 2100) {
      throw new IllegalArgumentException("귀속연도가 올바르지 않습니다.");
    }
    this.business = business;
    this.fiscalYear = fiscalYear;
  }

  public void markReview() {
    requireNotClosed();
    this.status = EasyClosingStatus.REVIEW;
  }

  public void close(String checklistSnapshot, String note) {
    requireNotClosed();
    this.status = EasyClosingStatus.CLOSED;
    this.checklistSnapshot = checklistSnapshot;
    this.note = note;
    this.closedAt = LocalDateTime.now();
  }

  /** 마감 해제 — 수정 후 다시 검토·마감할 수 있다. */
  public void reopen() {
    this.status = EasyClosingStatus.OPEN;
    this.closedAt = null;
  }

  private void requireNotClosed() {
    if (this.status == EasyClosingStatus.CLOSED) {
      throw new IllegalArgumentException("마감된 연도는 먼저 마감을 해제해 주세요.");
    }
  }
}
