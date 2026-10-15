package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionCheckpoint;
import java.time.LocalDateTime;

/** Safe checkpoint view. Cursor and checkpoint values are deliberately absent. */
public record EasyCollectionCheckpointResponse(
    Long connectionId,
    String datasetType,
    boolean checkpointExists,
    boolean hasCursor,
    LocalDateTime lastSuccessAt,
    LocalDateTime updatedAt
) {
  public static EasyCollectionCheckpointResponse from(EasyCollectionCheckpoint checkpoint) {
    return new EasyCollectionCheckpointResponse(
        checkpoint.getConnection().getConnectionId(),
        checkpoint.getDatasetType().name(),
        true,
        checkpoint.getCursorValue() != null && !checkpoint.getCursorValue().isBlank(),
        checkpoint.getLastSuccessAt(),
        checkpoint.getUpdatedAt());
  }
}
