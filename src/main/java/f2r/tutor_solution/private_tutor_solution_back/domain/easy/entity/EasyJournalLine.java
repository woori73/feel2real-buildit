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
 * Easy 분개 행 — 차변/대변 중 한쪽만 값을 가진다 (DB CHECK 보증).
 */
@Entity
@Table(name = "easy_journal_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyJournalLine extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "line_id")
  private Long lineId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "entry_id", nullable = false)
  private EasyJournalEntry entry;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private EasyAccount account;

  @Column(name = "debit", nullable = false)
  private long debit;

  @Column(name = "credit", nullable = false)
  private long credit;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  public EasyJournalLine(EasyJournalEntry entry, EasyAccount account, long debit, long credit,
      int sortOrder) {
    if (entry == null) {
      throw new IllegalArgumentException("분개를 선택해 주세요.");
    }
    if (account == null) {
      throw new IllegalArgumentException("계정을 선택해 주세요.");
    }
    if (debit < 0 || credit < 0) {
      throw new IllegalArgumentException("차변/대변은 0 이상이어야 합니다.");
    }
    if (!((debit > 0 && credit == 0) || (credit > 0 && debit == 0))) {
      throw new IllegalArgumentException("분개 행은 차변/대변 중 한쪽만 값을 가집니다.");
    }
    this.entry = entry;
    this.account = account;
    this.debit = debit;
    this.credit = credit;
    this.sortOrder = sortOrder;
  }
}
