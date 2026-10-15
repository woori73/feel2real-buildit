package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * NewStart "본인채무 확인 상태창" 저장 — {@code newstart_member_debt_status}.
 *
 * <p>{@code /newstart} HOME의 {@code #ns-status-panel}을 서버 저장으로 승격시킨 1:1 상태 row다.
 * 확인 플래그 6종은 Credit4U 공식 조회 항목 대응 체크리스트이고, 채무 세부 원본(금액·기관 목록
 * 자체)이 아니라 사용자가 직접 정리한 메모와 진행 단계만 저장한다. 삭제 API는 없다 — 초기화는
 * 값을 비운 upsert가 전부다.
 */
@Entity
@Table(name = "newstart_member_debt_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NewStartMemberDebtStatus extends BaseEntity {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "credit4u_checked", nullable = false)
  private boolean credit4uChecked;

  @Column(name = "creditor_confirmed", nullable = false)
  private boolean creditorConfirmed;

  @Column(name = "personal_loan_confirmed", nullable = false)
  private boolean personalLoanConfirmed;

  @Column(name = "overdue_registered_confirmed", nullable = false)
  private boolean overdueRegisteredConfirmed;

  @Column(name = "debt_guarantee_confirmed", nullable = false)
  private boolean debtGuaranteeConfirmed;

  @Column(name = "creditor_change_confirmed", nullable = false)
  private boolean creditorChangeConfirmed;

  @Column(name = "debt_summary", columnDefinition = "TEXT")
  private String debtSummary;

  @Column(name = "stage", length = 30)
  private String stage;

  @Builder
  public NewStartMemberDebtStatus(Long userId) {
    this.userId = userId;
  }

  public void updateProgress(
      boolean credit4uChecked,
      boolean creditorConfirmed,
      boolean personalLoanConfirmed,
      boolean overdueRegisteredConfirmed,
      boolean debtGuaranteeConfirmed,
      boolean creditorChangeConfirmed,
      String debtSummary,
      String stage) {
    this.credit4uChecked = credit4uChecked;
    this.creditorConfirmed = creditorConfirmed;
    this.personalLoanConfirmed = personalLoanConfirmed;
    this.overdueRegisteredConfirmed = overdueRegisteredConfirmed;
    this.debtGuaranteeConfirmed = debtGuaranteeConfirmed;
    this.creditorChangeConfirmed = creditorChangeConfirmed;
    this.debtSummary = debtSummary;
    this.stage = stage;
  }

  /** 6개 확인 항목 중 체크된 개수(Credit4U 완료 플래그 제외, 항목 확인 자체 5개 기준이 아니라
   * 화면 체크리스트 전체 6개 기준으로 카운트한다). */
  public int checkedCount() {
    int count = 0;
    if (credit4uChecked) count++;
    if (creditorConfirmed) count++;
    if (personalLoanConfirmed) count++;
    if (overdueRegisteredConfirmed) count++;
    if (debtGuaranteeConfirmed) count++;
    if (creditorChangeConfirmed) count++;
    return count;
  }

  public boolean allChecked() {
    return checkedCount() == 6;
  }
}
