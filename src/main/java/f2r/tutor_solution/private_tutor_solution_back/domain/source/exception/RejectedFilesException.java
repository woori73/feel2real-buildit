package f2r.tutor_solution.private_tutor_solution_back.domain.source.exception;

import java.util.List;

public class RejectedFilesException extends RuntimeException {

  public record RejectedFile(String fileName, String reason) {}

  private final List<RejectedFile> rejectedFiles;

  public RejectedFilesException(List<RejectedFile> rejectedFiles) {
    super("업로드 파일 중 일부가 거부되었습니다.");
    this.rejectedFiles = rejectedFiles;
  }

  public List<RejectedFile> getRejectedFiles() {
    return rejectedFiles;
  }
}
