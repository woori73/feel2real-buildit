package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D4 예외 핸들러 단위 테스트.
 *
 * <p>{@link AcademyAiChatExceptionHandler}가 ResponseStatusException을
 * 프론트가 읽는 {@code message} 필드를 포함한 JSON으로 변환하는지 검증한다.
 * HTTP 상태 코드는 예외가 지정한 값을 그대로 유지해야 한다.
 */
class AcademyAiChatExceptionHandlerTest {

  private final AcademyAiChatExceptionHandler handler =
      new AcademyAiChatExceptionHandler();

  @Test
  void badRequest_returns400_withKoreanMessage() {
    ResponseStatusException ex = new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "학원 이름은 필수입니다.");

    ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("message")).isEqualTo("학원 이름은 필수입니다.");
    assertThat(response.getBody().get("status")).isEqualTo(400);
  }

  @Test
  void conflict_returns409_withConflictReason() {
    ResponseStatusException ex = new ResponseStatusException(
        HttpStatus.CONFLICT, "이미 처리 중인 요청입니다: test-key");

    ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("message"))
        .isEqualTo("이미 처리 중인 요청입니다: test-key");
    assertThat(response.getBody().get("status")).isEqualTo(409);
  }

  @Test
  void emptyReason_usesDefaultKoreanMessage() {
    ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST);

    ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("message")).isEqualTo("요청이 올바르지 않습니다.");
  }

  @Test
  void notFound_returns404_withDefaultMessage() {
    ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND);

    ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("message"))
        .isEqualTo("요청한 리소스를 찾을 수 없습니다.");
  }
}
