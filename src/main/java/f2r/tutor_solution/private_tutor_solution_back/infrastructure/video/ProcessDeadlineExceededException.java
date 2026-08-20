package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

public class ProcessDeadlineExceededException extends VideoTranscodeException {
  public ProcessDeadlineExceededException(String message) {
    super(message);
  }
}
