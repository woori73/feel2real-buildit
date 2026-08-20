package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

/**
 * 업로드 제한(길이·용량·개수·코덱) 위반.
 *
 * <p>{@code ResponseStatusException}을 쓰지 않는 이유: Spring 기본 설정
 * ({@code server.error.include-message=never})에서는 reason이 응답 본문에 실리지 않아
 * 화면이 "왜 실패했는지"를 표시할 수 없다. 위반 사유를 그대로 사용자에게 보여줘야 하므로
 * 핸들러에서 {@code {"message": ...}} 형태로 내보낸다.
 */
public class VideoUploadRejectedException extends RuntimeException {

  public VideoUploadRejectedException(String message) {
    super(message);
  }
}
