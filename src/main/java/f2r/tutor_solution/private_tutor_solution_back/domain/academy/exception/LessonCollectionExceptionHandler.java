package f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = AcademyController.class)
public class LessonCollectionExceptionHandler {

  @ExceptionHandler(LessonCollectionException.class)
  public ResponseEntity<LessonCollectionErrorResponse> handleCollectionConflict(
      LessonCollectionException exception) {
    log.warn("lesson_collection_outcome code={}", exception.getCode());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new LessonCollectionErrorResponse(
        exception.getCode(),
        exception.getMessage(),
        exception.isRetryable()));
  }
}
