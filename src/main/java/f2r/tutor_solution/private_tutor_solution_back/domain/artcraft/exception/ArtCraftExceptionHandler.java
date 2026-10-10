package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ArtCraft 편집기 도메인 예외 → HTTP 상태 매핑.
 *
 * <p>기존 {@code GlobalExceptionHandler}와 충돌하지 않는다:
 * {@code IllegalArgumentException} → 400, {@code AccessDeniedException} → 403은
 * 전역 핸들러가 그대로 처리한다.
 */
@RestControllerAdvice
public class ArtCraftExceptionHandler {

  /** 존재하지 않는 프로젝트/스냅샷/PDF — 404. */
  @ExceptionHandler(ArtCraftNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(ArtCraftNotFoundException e) {
    return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
  }

  /** 낙관적 잠금 충돌 — 409 + 현재 버전. 호출자는 최신 버전을 읽고 재시도한다. */
  @ExceptionHandler(ArtCraftVersionConflictException.class)
  public ResponseEntity<Map<String, Object>> handleVersionConflict(
      ArtCraftVersionConflictException e) {
    return ResponseEntity.status(409).body(Map.of(
        "message", e.getMessage(),
        "currentVersion", e.getCurrentVersion()));
  }

  /** 편집 소스 미등록 — 409. 호출자는 소스를 먼저 업로드해야 한다. */
  @ExceptionHandler(ArtCraftSourceMissingException.class)
  public ResponseEntity<Map<String, Object>> handleSourceMissing(
      ArtCraftSourceMissingException e) {
    return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
  }

  /** CLI 실행 실패 — 예외가 지정한 상태(502/503/504). */
  @ExceptionHandler(ArtCraftCliException.class)
  public ResponseEntity<Map<String, Object>> handleCliFailure(ArtCraftCliException e) {
    return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
  }
}

