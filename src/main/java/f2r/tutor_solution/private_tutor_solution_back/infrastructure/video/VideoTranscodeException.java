package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

/** 변환 자체가 실패했을 때(비정상 exit, 타임아웃, 산출물 없음). */
public class VideoTranscodeException extends RuntimeException {

  public VideoTranscodeException(String message) {
    super(message);
  }

  public VideoTranscodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
