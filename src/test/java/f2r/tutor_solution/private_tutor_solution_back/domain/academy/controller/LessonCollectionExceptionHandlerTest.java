package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception.LessonCollectionErrorResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception.LessonCollectionException;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception.LessonCollectionExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LessonCollectionExceptionHandlerTest {

  private final LessonCollectionExceptionHandler handler =
      new LessonCollectionExceptionHandler();

  @Test
  void lockConflict_returnsStableRetryableResponseAndLogsOnlySafeCode(
      CapturedOutput output) {
    // given
    LessonCollectionException conflict =
        LessonCollectionException.authoringLockConflict();

    // when
    ResponseEntity<LessonCollectionErrorResponse> response =
        handler.handleCollectionConflict(conflict);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody()).isNotNull().satisfies(body -> {
      assertThat(body.code())
          .isEqualTo(LessonCollectionException.AUTHORING_LOCK_CONFLICT);
      assertThat(body.message())
          .isEqualTo("수업 상품이 다른 요청에서 변경 중입니다. 잠시 후 다시 시도해 주세요.");
      assertThat(body.retryable()).isTrue();
    });
    assertThat(output)
        .contains("lesson_collection_outcome code=LESSON_AUTHORING_LOCK_CONFLICT")
        .doesNotContain(conflict.getMessage())
        .doesNotContain("payload=")
        .doesNotContain("select ")
        .doesNotContain("insert ")
        .doesNotContain("update ");
  }
}
