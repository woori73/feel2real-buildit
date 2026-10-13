package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/** 관리자 전용 학원 장부 월 대시보드. 각 수치의 원천을 API 계약에 함께 노출한다. */
public record EasyWorkspaceMonthlyResponse(
    String month,
    Long businessId,
    String businessName,
    long income,
    long expense,
    long profit,
    long receivable,
    long studentCount,
    long collectedCount,
    long importStagedCount,
    long ledgerSavedCount,
    long journalPostedCount,
    long openReviewCount,
    long evidenceReviewCount,
    String closingStatus,
    boolean periodClosed,
    Sources sources,
    List<EasyWorkspaceTransactionResponse> recentTransactions,
    List<EasyDashboardResponse.EasyOverdueStudent> overdueStudents
) {
  public record Sources(
      String amounts,
      String collection,
      String importStage,
      String ledger,
      String journal,
      String review,
      String closing
  ) {
    public static Sources contract() {
      return new Sources(
          "easy_transactions (세무구분 및 비용환불 정책 적용)",
          "easy_collection_raw_records.source_occurred_on",
          "easy_import_batches.status=STAGED (현재 대기 건수)",
          "easy_transactions (기간 내 저장 건수)",
          "easy_journal_entries.status=POSTED (거래별 활성 분개)",
          "easy_review_items.status=OPEN",
          "easy_annual_closings (미생성 연도는 OPEN)");
    }
  }
}
