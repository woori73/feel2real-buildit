package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoUploadApiException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoStorageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = AcademyVideoUploadController.class)
public class AcademyVideoUploadExceptionHandler {

  @ExceptionHandler(AcademyVideoUploadApiException.class)
  ResponseEntity<Map<String, Object>> handle(AcademyVideoUploadApiException exception) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", exception.code());
    body.put("message", exception.getMessage());
    if (exception.retryAfterSeconds() != null) {
      body.put("retryAfterSeconds", exception.retryAfterSeconds());
    }
    ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.status());
    if (exception.retryAfterSeconds() != null) {
      response.header(HttpHeaders.RETRY_AFTER,
          Integer.toString(exception.retryAfterSeconds()));
    }
    return response.body(body);
  }

  @ExceptionHandler(AcademyVideoStorageException.class)
  ResponseEntity<Map<String, Object>> handleStorage() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of(
            "code", "UPLOAD_STORAGE_UNAVAILABLE",
            "message", "업로드 저장소를 사용할 수 없습니다."));
  }
}
