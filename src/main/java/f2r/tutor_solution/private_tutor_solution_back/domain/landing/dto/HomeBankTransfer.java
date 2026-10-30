package f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto;

/**
 * 홈페이지 무통장입금 안내 계좌(2026-10-28 사용자 확정). 공개 여부는 {@code app.home.bank-transfer-public}
 * 한 설정으로만 정하며, 맞춤 문제 신청 기능({@code app.exam-intake.*})과 독립적이다. 입금 확인은 관리자 수동 확인이다.
 */
public record HomeBankTransfer(String bank, String accountNumber, String holder) {
  public static final HomeBankTransfer CURRENT = new HomeBankTransfer("신한은행", "110-498-250866", "박정민");
}
