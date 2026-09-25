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
 * Easy cut-over — 사업체별 실계정 복식 전환 기준일 + 개시잔액 분개 링크.
 *
 * 개시잔액은 반드시 복식부기 분개로 표현한다 (AI 추정·수기 숫자 저장 금지).
 * 기존 543 historical shadow journal은 수정·삭제·재분개하지 않는다.
 */
@Entity
@Table(name = "easy_cutovers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyCutover extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "cutover_id")
  private Long cutoverId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "cutover_date", nullable = false)
  private LocalDate cutoverDate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "opening_entry_id")
  private EasyJournalEntry openingEntry;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private EasyCutoverStatus status = EasyCutoverStatus.DRAFT;

  @Builder
  public EasyCutover(EasyBusiness business, LocalDate cutoverDate) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (cutoverDate == null) {
      throw new IllegalArgumentException("전환 기준일을 입력해 주세요.");
    }
    this.business = business;
    this.cutoverDate = cutoverDate;
  }

  public void linkOpeningEntry(EasyJournalEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("개시분개를 선택해 주세요.");
    }
    requireDraft();
    this.openingEntry = entry;
  }

  public void approve() {
    requireDraft();
    if (openingEntry == null) {
      throw new IllegalArgumentException("개시분개 없이 전환을 확정할 수 없습니다.");
    }
    this.status = EasyCutoverStatus.APPROVED;
  }

  private void requireDraft() {
    if (this.status != EasyCutoverStatus.DRAFT) {
      throw new IllegalArgumentException("확정된 전환은 변경할 수 없습니다.");
    }
  }
}
