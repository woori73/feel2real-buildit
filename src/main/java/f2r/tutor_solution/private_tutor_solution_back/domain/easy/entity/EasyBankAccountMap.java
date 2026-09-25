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
 * Easy 실제 은행계좌 → 회계계정 매핑.
 *
 * 한 사업체가 여러 은행계좌(동일 은행 복수계좌, 기간별 주거래 변경)를 각각 별도
 * 회계계정에 연결한다. 은행별 사용기간을 코드에 hard-code하지 않으며,
 * 실제 귀속은 원본 은행거래의 거래일자 + 계좌 식별자로 판정한다.
 * 실계좌번호 전체를 저장하지 않는다 (safe 식별자 + masked만).
 * 과거 계좌는 삭제하지 않고 INACTIVE + activeTo로 보존한다.
 */
@Entity
@Table(name = "easy_bank_account_maps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyBankAccountMap extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "map_id")
  private Long mapId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "bank_provider", nullable = false, length = 20)
  private String bankProvider;

  @Column(name = "safe_account_identifier", nullable = false, length = 64)
  private String safeAccountIdentifier;

  @Column(name = "masked_account", length = 20)
  private String maskedAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "accounting_account_id", nullable = false)
  private EasyAccount accountingAccount;

  @Column(name = "active_from", nullable = false)
  private LocalDate activeFrom;

  @Column(name = "active_to")
  private LocalDate activeTo;

  @Column(name = "status", nullable = false, length = 10)
  private String status = "ACTIVE";

  @Builder
  public EasyBankAccountMap(EasyBusiness business, String bankProvider,
      String safeAccountIdentifier, String maskedAccount, EasyAccount accountingAccount,
      LocalDate activeFrom, LocalDate activeTo, String status) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (bankProvider == null || bankProvider.isBlank()) {
      throw new IllegalArgumentException("은행 구분을 입력해 주세요.");
    }
    if (safeAccountIdentifier == null || safeAccountIdentifier.isBlank()) {
      throw new IllegalArgumentException("계좌 안전 식별자를 입력해 주세요.");
    }
    if (accountingAccount == null) {
      throw new IllegalArgumentException("회계계정을 선택해 주세요.");
    }
    if (activeFrom == null) {
      throw new IllegalArgumentException("사용 시작일을 입력해 주세요.");
    }
    this.business = business;
    this.bankProvider = bankProvider.trim();
    this.safeAccountIdentifier = safeAccountIdentifier.trim();
    this.maskedAccount = maskedAccount;
    this.accountingAccount = accountingAccount;
    this.activeFrom = activeFrom;
    this.activeTo = activeTo;
    this.status = status == null ? "ACTIVE" : status;
  }

  public boolean isActiveOn(LocalDate date) {
    if (!"ACTIVE".equals(status)) {
      return false;
    }
    if (date.isBefore(activeFrom)) {
      return false;
    }
    return activeTo == null || !date.isAfter(activeTo);
  }
}
