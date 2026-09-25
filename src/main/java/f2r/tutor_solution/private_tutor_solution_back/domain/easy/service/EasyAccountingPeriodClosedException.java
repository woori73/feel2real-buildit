package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/**
 * Easy 회계기간 마감 위반 — CLOSED 연도의 신규 전기 시 발생한다.
 * 컨트롤러 UI가 아닌 중앙 전기 경로(postLegs/repost)에서 강제한다.
 */
public class EasyAccountingPeriodClosedException extends IllegalArgumentException {
  public EasyAccountingPeriodClosedException(Long businessId, int fiscalYear) {
    super("ACCOUNTING_PERIOD_CLOSED: 사업체 " + businessId + "의 " + fiscalYear
        + "년은 마감되어 전표할 수 없습니다. 먼저 마감을 해제해 주세요.");
  }
}
