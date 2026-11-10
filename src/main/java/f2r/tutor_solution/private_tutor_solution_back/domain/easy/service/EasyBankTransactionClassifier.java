package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 은행 입출금 1건의 "원천 판정"(primary_disposition)과 검토 메모를 만든다. 계정과목을 정하지 않는다 —
 * 중복 회계를 막기 위한 손익 제외 판정과, 학원 세무 체크리스트에 따른 검토 표시만 한다.
 *
 * <p>근거: EASY_LEDGER_OPERATING_PRINCIPLES §8(중복 회계 금지),
 * docs/easy/ACADEMY_TAX_CHECKLIST.md(학원사업자 세무 안내 — 현금영수증·정책자금·지원금·권리금).
 * 확실하지 않으면 판정하지 않고(null) 검토로 넘긴다.
 */
public final class EasyBankTransactionClassifier {

  /** 현금영수증 의무발행 기준(학원 = 의무발행업종, 계좌이체 포함 현금 수입 10만원 이상). */
  public static final long CASH_RECEIPT_THRESHOLD = 100_000L;

  public static final String CARD_PAYMENT = "CARD_PAYMENT";
  public static final String CARD_SETTLEMENT = "CARD_SETTLEMENT";
  public static final String LOAN_INFLOW = "LOAN_INFLOW";
  public static final String LOAN_INTEREST = "LOAN_INTEREST";
  public static final String LOAN_REPAYMENT = "LOAN_REPAYMENT";
  public static final String GOV_SUBSIDY = "GOV_SUBSIDY";
  /** 결제선생(페이민트) 등 수납 서비스의 정산 입금 — 이미 수납 시점에 수입으로 잡힌 돈. */
  public static final String PG_SETTLEMENT = "PG_SETTLEMENT";
  private static final List<String> TUITION_PAY_SERVICES = List.of("페이민트", "결제선생", "paymint", "payssam");

  private static final List<String> CARD_COMPANIES = List.of("신한카드", "국민카드", "kb카드",
      "kb국민카드", "비씨카드", "bc카드", "삼성카드", "현대카드", "롯데카드", "하나카드", "우리카드",
      "농협카드", "nh카드", "nh농협카드");
  private static final Pattern CARD_PAYMENT_HINT = Pattern.compile("결제|대금|카드");
  private static final Pattern POLICY_LOAN = Pattern.compile(
      "소상공인시장진흥공단|소상공인진흥공단|소진공|중소벤처기업진흥공단|중진공|신용보증재단|신용보증기금|대출");
  private static final Pattern LOAN_INTEREST_HINT = Pattern.compile("대출이자|이자출금|이자납입");
  private static final Pattern LOAN_REPAY_HINT = Pattern.compile("대출상환|원금상환|원리금");
  private static final Pattern SUBSIDY = Pattern.compile(
      "고용노동부|고용24|근로복지공단|두루누리|장려금|지원금|보조금");
  private static final Pattern GOODWILL = Pattern.compile("권리금");

  private EasyBankTransactionClassifier() {}

  /** 판정 결과. disposition·reviewNote 둘 다 없을 수 있다. */
  public record Result(String disposition, String reviewNote) {}

  public static Result classify(boolean income, long amount, String counterparty,
      String description) {
    String text = normalize(counterparty) + " " + normalize(description);
    boolean cardCompany = CARD_COMPANIES.stream().anyMatch(text::contains);

    if (!income && cardCompany && CARD_PAYMENT_HINT.matcher(text).find()) {
      return new Result(CARD_PAYMENT,
          "[검토] 카드대금 출금 — 카드 사용 시점에 비용 처리되므로 비용으로 다시 넣지 않았습니다.");
    }
    if (income && TUITION_PAY_SERVICES.stream().anyMatch(text::contains)) {
      return new Result(PG_SETTLEMENT,
          "[검토] 결제선생(페이민트) 정산입금 — 결제선생 수납 자료와 대사하세요. 수강료 수입으로 다시 넣지 않았습니다.");
    }
    if (income && cardCompany) {
      return new Result(CARD_SETTLEMENT,
          "[검토] 카드사 정산입금 — 카드매출과 대사하세요. 매출로 다시 넣지 않았습니다.");
    }
    if (income && POLICY_LOAN.matcher(text).find()) {
      return new Result(LOAN_INFLOW, "[검토] 정책자금·대출 입금 — 부채(차입금)이며 매출이 아닙니다.");
    }
    if (!income && LOAN_INTEREST_HINT.matcher(text).find()) {
      return new Result(LOAN_INTEREST, null);
    }
    if (!income && LOAN_REPAY_HINT.matcher(text).find()) {
      return new Result(LOAN_REPAYMENT, "[검토] 대출 상환 — 원금은 비용이 아닙니다. 원금·이자를 나눠 확인하세요.");
    }
    if (income && SUBSIDY.matcher(text).find()) {
      return new Result(GOV_SUBSIDY,
          "[검토] 고용지원금·장려금 입금 — 수입금액 산입 여부를 세무사와 확인하세요.");
    }
    if (GOODWILL.matcher(text).find()) {
      return new Result(null, "[검토] 권리금 — 양수자는 8.8% 원천징수 후 91.2% 지급 대상입니다.");
    }
    if (income && amount >= CASH_RECEIPT_THRESHOLD) {
      return new Result(null,
          "[검토] 10만원 이상 현금(계좌이체) 수입 — 현금영수증 의무발행 대상인지 확인하세요(미발행 가산세 20%).");
    }
    return new Result(null, null);
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .replaceAll("\\s+", "")
        .toLowerCase(Locale.ROOT);
  }
}
