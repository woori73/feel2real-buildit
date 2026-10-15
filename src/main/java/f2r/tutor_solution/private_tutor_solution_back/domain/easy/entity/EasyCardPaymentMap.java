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
 * Easy 카드 결제수단 → 미지급금 계정 → 결제 은행 매핑.
 *
 * 카드 issuer와 결제은행이 같다고 가정하지 않는다
 * ("신한카드이므로 신한은행" 같은 추론 금지 — 설정값이 정답).
 */
@Entity
@Table(name = "easy_card_payment_maps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyCardPaymentMap extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "map_id")
  private Long mapId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "instrument_id", nullable = false)
  private EasyCardInstrument instrument;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "card_payable_account_id", nullable = false)
  private EasyAccount cardPayableAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_bank_map_id", nullable = false)
  private EasyBankAccountMap paymentBankMap;

  @Column(name = "active_from", nullable = false)
  private LocalDate activeFrom;

  @Column(name = "active_to")
  private LocalDate activeTo;

  @Column(name = "status", nullable = false, length = 10)
  private String status = "ACTIVE";

  @Builder
  public EasyCardPaymentMap(EasyBusiness business, EasyCardInstrument instrument,
      EasyAccount cardPayableAccount, EasyBankAccountMap paymentBankMap,
      LocalDate activeFrom, LocalDate activeTo, String status) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (instrument == null) {
      throw new IllegalArgumentException("카드 결제수단을 선택해 주세요.");
    }
    if (cardPayableAccount == null) {
      throw new IllegalArgumentException("미지급금 계정을 선택해 주세요.");
    }
    if (paymentBankMap == null) {
      throw new IllegalArgumentException("결제 은행을 선택해 주세요.");
    }
    if (activeFrom == null) {
      throw new IllegalArgumentException("사용 시작일을 입력해 주세요.");
    }
    this.business = business;
    this.instrument = instrument;
    this.cardPayableAccount = cardPayableAccount;
    this.paymentBankMap = paymentBankMap;
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
