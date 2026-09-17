package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

public record EasyCorrectionLogResponse(
    Long logId,
    Long businessId,
    String businessName,
    Long txnId,
    String action,
    String beforeJson,
    String afterJson,
    String requestedBy,
    String confirmedBy
) {
  public static EasyCorrectionLogResponse from(
      f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCorrectionLog log) {
    return new EasyCorrectionLogResponse(
        log.getLogId(),
        log.getBusiness().getBusinessId(),
        log.getBusiness().getName(),
        log.getTxn() == null ? null : log.getTxn().getTxnId(),
        log.getAction(),
        log.getBeforeJson(),
        log.getAfterJson(),
        log.getRequestedBy(),
        log.getConfirmedBy());
  }
}
