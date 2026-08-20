package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

/**
 * ffmpeg/ffprobe 바이너리를 실행할 수 없을 때.
 *
 * <p>배포 환경에 바이너리가 아직 없어도 앱은 부팅되어야 하므로(변환 기능만 못 씀) 부팅 시점이 아니라
 * 호출 시점에 던지고, 핸들러가 503 + 안내로 변환한다.
 */
public class VideoToolUnavailableException extends RuntimeException {

  public VideoToolUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
