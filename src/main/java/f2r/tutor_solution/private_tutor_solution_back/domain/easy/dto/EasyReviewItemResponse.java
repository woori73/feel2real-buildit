package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewItem;
import java.time.LocalDate;

public record EasyReviewItemResponse(
    Long reviewId,
    Long businessId,
    String businessName,
    String kind,
    String kindLabel,
    String status,
    Long txnId,
    Long importRowId,
    Long batchId,
    String title,
    String detail,
    Long amount,
    LocalDate occurredOn,
    String resolution
) {
  public static EasyReviewItemResponse from(EasyReviewItem item) {
    return new EasyReviewItemResponse(
        item.getReviewId(),
        item.getBusiness().getBusinessId(),
        item.getBusiness().getName(),
        item.getKind().name(),
        item.getKind().label(),
        item.getStatus().name(),
        item.getTxn() == null ? null : item.getTxn().getTxnId(),
        item.getImportRow() == null ? null : item.getImportRow().getRowId(),
        item.getBatch() == null ? null : item.getBatch().getBatchId(),
        item.getTitle(),
        item.getDetail(),
        item.getAmount(),
        item.getOccurredOn(),
        item.getResolution());
  }
}
