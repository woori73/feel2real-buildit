package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 문서(이미지/PDF) 업로드의 업무 상한 판정.
 *
 * <p>{@code spring.servlet.multipart.max-file-size}는 서블릿 컨테이너 전역 설정이라 endpoint별로 나눌 수
 * 없다. 영상 업로드(100MB) 때문에 그 값을 열어 두면 이미지·PDF 경로도 같이 열리므로, 문서 상한은
 * 컨테이너가 아니라 여기서 판정한다.
 */
@Component
public class UploadSizeGuard {

  private final long maxDocumentBytes;

  public UploadSizeGuard(@Value("${app.upload.max-document-bytes:20971520}") long maxDocumentBytes) {
    this.maxDocumentBytes = maxDocumentBytes;
  }

  /** @throws ResponseStatusException 상한 초과 시 400 */
  public void requireWithinDocumentLimit(MultipartFile file) {
    if (file != null && file.getSize() > maxDocumentBytes) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "파일 용량은 " + (maxDocumentBytes / (1024 * 1024)) + "MB를 넘을 수 없습니다: "
              + file.getOriginalFilename());
    }
  }
}
