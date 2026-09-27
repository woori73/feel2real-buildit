package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * NewStart 권한 거절을 API 응답으로 번역하는 단일 지점.
 *
 * <p>Body는 프론트가 이미 읽는 {@code {status:"error", errorMessage:"..."}} 계약이다
 * (newstart.js의 AI 답변, fss-helper의 Guided Agent 공통). 예외 stack/내부 권한명은
 * 브라우저로 내려보내지 않는다.
 *
 * <p>범위를 newstart 패키지로 한정하는 이유: 전역 GlobalExceptionHandler가 예외 type을
 * TEXT/plain으로 처리하는 경로와 충돌하지 않으면서, 다른 프로젝트 API의 error shape를
 * 바꾸지 않기 위해서다.
 */
@RestControllerAdvice(basePackages = "f2r.tutor_solution.private_tutor_solution_back.domain.newstart")
public class NewStartEntitlementExceptionHandler {

  @ExceptionHandler(NewStartEntitlementDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleDenied(NewStartEntitlementDeniedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("status", "error", "errorMessage", e.getMessage()));
  }
}
