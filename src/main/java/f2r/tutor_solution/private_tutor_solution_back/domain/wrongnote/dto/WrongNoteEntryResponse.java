package f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.entity.WrongNoteEntry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

public record WrongNoteEntryResponse(
    Long entryId,
    Long conversationId,
    Long sourceId,
    String title,
    String examName,
    String examScope,
    String memo,
    String tags,
    String originalFileName,
    String fileType,
    Long fileSize,
    List<FileInfo> files,
    String uploadedByName,
    String uploadedByRole,
    LocalDateTime createdAt,
    int pageCount
) {
  public static WrongNoteEntryResponse of(WrongNoteEntry entry, Function<Source, Integer> pageCountResolver) {
    List<FileInfo> files = entry.getFiles().stream()
        .map(file -> new FileInfo(
            file.getSource().getSourceId(),
            file.getSource().getOriginalFileName(),
            file.getSource().getFileType(),
            file.getSource().getFileSize(),
            file.getOrderNum(),
            pageCountResolver.apply(file.getSource())))
        .toList();
    if (files.isEmpty()) {
      files = List.of(new FileInfo(
          entry.getSource().getSourceId(),
          entry.getSource().getOriginalFileName(),
          entry.getSource().getFileType(),
          entry.getSource().getFileSize(),
          1,
          pageCountResolver.apply(entry.getSource())));
    }
    FileInfo first = files.getFirst();
    return new WrongNoteEntryResponse(
        entry.getEntryId(),
        entry.getConversation().getConversationId(),
        first.sourceId(),
        entry.getTitle(),
        entry.getExamName(),
        entry.getExamScope(),
        entry.getMemo(),
        entry.getTags(),
        first.originalFileName(),
        first.fileType(),
        first.fileSize(),
        files,
        entry.getUploadedBy().getName(),
        entry.getUploadedBy().getRole().name(),
        entry.getCreatedAt(),
        first.pageCount());
  }

  public record FileInfo(
      Long sourceId,
      String originalFileName,
      String fileType,
      Long fileSize,
      int orderNum,
      int pageCount
  ) {}
}
