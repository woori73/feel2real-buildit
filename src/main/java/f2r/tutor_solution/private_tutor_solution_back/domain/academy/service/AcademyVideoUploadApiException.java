package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import org.springframework.http.HttpStatus;

public class AcademyVideoUploadApiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final Integer retryAfterSeconds;

  public AcademyVideoUploadApiException(
      HttpStatus status, String code, String message, Integer retryAfterSeconds) {
    super(message, null, false, false);
    this.status = status;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public HttpStatus status() { return status; }
  public String code() { return code; }
  public Integer retryAfterSeconds() { return retryAfterSeconds; }
}
