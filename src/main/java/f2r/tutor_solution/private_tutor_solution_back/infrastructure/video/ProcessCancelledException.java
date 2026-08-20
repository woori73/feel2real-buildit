package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

public class ProcessCancelledException extends VideoTranscodeException {
  public ProcessCancelledException(String message) {
    super(message);
  }

  public ProcessCancelledException(String message, Throwable cause) {
    super(message, cause);
  }
}
