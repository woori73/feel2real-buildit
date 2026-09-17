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
 * Easy 분개 — 확정 거래 1건당 활성 분개 1건.
 * 같은 경제거래를 이중 계상하지 않는다. 수정은 void + 재분개로만 한다.
 */
@Entity
@Table(name = "easy_journal_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyJournalEntry extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entry_id")
  private Long entryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "occurred_on", nullable = false)
  private LocalDate occurredOn;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "txn_id")
  private EasyTransaction txn;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private EasyJournalStatus status = EasyJournalStatus.POSTED;

  @Column(name = "memo", length = 500)
  private String memo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reversed_entry_id")
  private EasyJournalEntry reversedEntry;

  @Builder
  public EasyJournalEntry(EasyBusiness business, LocalDate occurredOn, EasyTransaction txn,
      String memo, EasyJournalEntry reversedEntry) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (occurredOn == null) {
      throw new IllegalArgumentException("거래일을 입력해 주세요.");
    }
    this.business = business;
    this.occurredOn = occurredOn;
    this.txn = txn;
    this.memo = memo;
    this.reversedEntry = reversedEntry;
  }

  public void voidEntry() {
    this.status = EasyJournalStatus.VOIDED;
  }
}
