package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

/**
 * 장부 거래와 계산형 처리 상태를 함께 반환한다. 상태는 기존 테이블을 읽어 계산하며
 * 영속 필드를 추가하지 않는다.
 */
public record EasyWorkspaceTransactionResponse(
    EasyTxnResponse transaction,
    String collectionStatus,
    String importStatus,
    String ledgerStatus,
    String journalStatus,
    String closingStatus,
    boolean periodClosed
) {
}
