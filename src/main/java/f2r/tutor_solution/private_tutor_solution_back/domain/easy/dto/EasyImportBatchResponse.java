package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import java.util.List;

public record EasyImportBatchResponse(
    Long batchId,
    Long businessId,
    String businessName,
    String sourceName,
    String sourceKind,
    String status,
    int totalRows,
    int confirmedRows,
    List<EasyImportRowResponse> rows
) {
  public static EasyImportBatchResponse of(EasyImportBatch batch, List<EasyImportRowResponse> rows) {
    return new EasyImportBatchResponse(
        batch.getBatchId(),
        batch.getBusiness().getBusinessId(),
        batch.getBusiness().getName(),
        batch.getSourceName(),
        batch.getSourceKind(),
        batch.getStatus().name(),
        batch.getTotalRows(),
        batch.getConfirmedRows(),
        rows);
  }
}
