package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

public record EasyDashboardResponse(
    String month,
    Long businessId,
    String businessName,
    long income,
    long expense,
    long profit,
    long receivable,
    long studentCount,
    List<EasyTxnResponse> recentTxns,
    List<EasyOverdueStudent> overdueStudents
) {
  public record EasyOverdueStudent(
      Long studentId,
      String name,
      String school,
      long balance
  ) {
  }
}
