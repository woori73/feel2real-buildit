package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * BuildIt AI 채팅 컨트롤러 전용 예외 처리기 (D4).
 *
 * <p>범위: {@link AcademyAiChatController}에서 발생하는 예외만 처리한다.
 * {@code PageNotFoundRedirectController}의 전역 {@code /error} 처리는 변경하지 않으며,
 * 다른 API의 오류 응답 형식에도 영향을 주지 않는다.
 *
 * <p>{@code ResponseStatusException}을 JSON 본문으로 변환한다.
 * 프론트가 읽는 {@code message} 필드에 한국어 사유를 전달하고,
 * 예외에 지정된 HTTP 상태 코드는 그대로 유지한다.
 */
@RestControllerAdvice(assignableTypes = AcademyAiChatController.class)
public class AcademyAiChatExceptionHandler {

  /**
   * ResponseStatusException을 JSON 오류 응답으로 변환한다.
   *
   * <p>HTTP 400: 구체적인 한국어 검증 사유를 message에 담는다.
   * HTTP 409: 충돌 사유를 message에 담는다.
   * 상태 코드는 예외가 지정한 값을 그대로 사용한다.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
    HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    String message = e.getReason();
    if (message == null || message.isBlank()) {
      message = defaultMessageFor(status);
    }
    return ResponseEntity.status(status)
        .body(Map.of(
            "message", message,
            "status", status.value(),
            "error", status.getReasonPhrase()));
  }

  private String defaultMessageFor(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> "요청이 올바르지 않습니다.";
      case CONFLICT -> "요청이 기존 데이터와 충돌합니다.";
      case NOT_FOUND -> "요청한 리소스를 찾을 수 없습니다.";
      case FORBIDDEN -> "접근 권한이 없습니다.";
      default -> "요청 처리 중 오류가 발생했습니다.";
    };
  }
}
