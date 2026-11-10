package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EasyBankTransactionClassifierTest {

  @Test
  @DisplayName("given 카드대금 출금 when 판정 then CARD_PAYMENT(비용 재계상 금지)")
  void cardPayment() {
    // when
    var result = EasyBankTransactionClassifier.classify(false, 1_200_000, "신한카드", "자동이체 카드대금");
    // then
    assertThat(result.disposition()).isEqualTo("CARD_PAYMENT");
  }

  @Test
  @DisplayName("given 카드사 정산입금 when 판정 then CARD_SETTLEMENT(매출 재계상 금지)")
  void cardSettlement() {
    var result = EasyBankTransactionClassifier.classify(true, 980_000, "KB 국민카드", "가맹점 정산");
    assertThat(result.disposition()).isEqualTo("CARD_SETTLEMENT");
  }

  @Test
  @DisplayName("given 소상공인 정책자금 입금 when 판정 then LOAN_INFLOW(부채, 매출 아님)")
  void policyLoan() {
    var result = EasyBankTransactionClassifier.classify(true, 30_000_000, "소상공인시장진흥공단", "경영안정자금");
    assertThat(result.disposition()).isEqualTo("LOAN_INFLOW");
    assertThat(result.reviewNote()).contains("부채");
  }

  @Test
  @DisplayName("given 고용지원금 입금 when 판정 then GOV_SUBSIDY + 세무 확인 메모")
  void subsidy() {
    var result = EasyBankTransactionClassifier.classify(true, 600_000, "고용노동부", "청년일자리도약장려금");
    assertThat(result.disposition()).isEqualTo("GOV_SUBSIDY");
    assertThat(result.reviewNote()).contains("수입금액 산입");
  }

  @Test
  @DisplayName("given 10만원 이상 계좌이체 수입 when 판정 then 현금영수증 의무발행 확인 메모, 10만원 미만은 메모 없음")
  void cashReceiptThreshold() {
    assertThat(EasyBankTransactionClassifier.classify(true, 100_000, "김학부", "수강료").reviewNote())
        .contains("현금영수증");
    assertThat(EasyBankTransactionClassifier.classify(true, 99_999, "김학부", "수강료").reviewNote())
        .isNull();
  }

  @Test
  @DisplayName("given 권리금 거래 when 판정 then 8.8% 원천징수 확인 메모")
  void goodwill() {
    var result = EasyBankTransactionClassifier.classify(false, 20_000_000, "양도인", "학원 권리금");
    assertThat(result.disposition()).isNull();
    assertThat(result.reviewNote()).contains("8.8%");
  }

  @Test
  @DisplayName("given 개인 이름 '신보람' 입금 when 판정 then 신용보증 기관으로 오인하지 않는다")
  void personNameIsNotLoan() {
    var result = EasyBankTransactionClassifier.classify(true, 50_000, "신보람", "수강료");
    assertThat(result.disposition()).isNull();
  }
}
