package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * pdfcraft-cli 실행 실패. 상태 코드로 변환된다.
 *
 * <ul>
 *   <li>503 — CLI 바이너리가 설치되지 않음 (배포 문제, 관리자가 {@code cli-path}에 배치 필요)
 *   <li>502 — CLI 비정상 종료 (스크립트/입력 문제)
 *   <li>504 — CLI 실행 타임아웃
 * </ul>
 */
@Getter
public class ArtCraftCliException extends RuntimeException {

  private final HttpStatus status;

  public ArtCraftCliException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public ArtCraftCliException(HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }
}
