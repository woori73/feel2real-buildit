package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import org.springframework.http.HttpStatus;

/**
 * 기출분석 서비스의 사용자 안내용 오류. {@code code}는 화면이 분기에 쓰고, 메시지는 그대로 사용자에게 보여 준다.
 * 상세 원인(파일 내용·모델 응답)은 메시지에 싣지 않는다.
 */
public class ExamInsightException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ExamInsightException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public static ExamInsightException badRequest(String code, String message) {
    return new ExamInsightException(HttpStatus.BAD_REQUEST, code, message);
  }
}
