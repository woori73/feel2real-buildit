package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;

public record SourceAttachResponse(
    Long sourceId,
    String fileName,
    String fileType,
    Long fileSize,
    String processingStatus
) {
  public static SourceAttachResponse from(Source source) {
    return new SourceAttachResponse(
        source.getSourceId(),
        source.getOriginalFileName(),
        source.getFileType(),
        source.getFileSize(),
        source.getProcessingStatus() != null ? source.getProcessingStatus().name() : null
    );
  }
}
