package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

/**
 * 동시 변환 상한에 걸렸을 때. 대기시키지 않고 즉시 실패시켜 CPU 폭주와 요청 스레드 점유를 막는다.
 */
public class VideoTranscoderBusyException extends RuntimeException {

  public VideoTranscoderBusyException(String message) {
    super(message);
  }
}
